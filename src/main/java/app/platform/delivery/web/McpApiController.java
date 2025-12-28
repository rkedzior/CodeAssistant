package app.platform.delivery.web;

import app.core.search.InvalidRegexException;
import app.core.search.SemanticSearchPort;
import app.core.search.SemanticSearchResponse;
import app.core.search.TextSearchPort;
import app.core.search.TextSearchResponse;
import app.core.observations.Observation;
import app.core.observations.ObservationSubtype;
import app.core.observations.ObservationsPort;
import app.platform.mcp.api.McpSearchRequest;
import app.platform.mcp.api.McpSearchResponse;
import app.platform.mcp.api.McpWriteObservationRequest;
import app.platform.mcp.McpToolRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class McpApiController {
  private final McpToolRegistry mcpToolRegistry;
  private final TextSearchPort textSearchPort;
  private final SemanticSearchPort semanticSearchPort;
  private final ObservationsPort observationsPort;

  public McpApiController(
      McpToolRegistry mcpToolRegistry,
      TextSearchPort textSearchPort,
      SemanticSearchPort semanticSearchPort,
      ObservationsPort observationsPort) {
    this.mcpToolRegistry = mcpToolRegistry;
    this.textSearchPort = textSearchPort;
    this.semanticSearchPort = semanticSearchPort;
    this.observationsPort = observationsPort;
  }

  @GetMapping(path = "/api/mcp/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<McpStatusResponse> status() {
    return ResponseEntity.ok(new McpStatusResponse(true, mcpToolRegistry.listTools()));
  }

  @PostMapping(
      path = "/api/mcp/search",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> search(@RequestBody McpSearchRequest request) {
    if (request == null) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Request body is required."));
    }
    if (request.mode() == null || request.mode().isBlank()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Field `mode` is required."));
    }
    if (request.query() == null || request.query().isBlank()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Field `query` must not be blank."));
    }

    String mode = request.mode().trim().toLowerCase(java.util.Locale.ROOT);
    if ("text".equals(mode)) {
      boolean regex = Boolean.TRUE.equals(request.regex());
      try {
        TextSearchResponse response = textSearchPort.search(request.query(), regex);
        return ResponseEntity.ok(new McpSearchResponse("text", response, null));
      } catch (InvalidRegexException e) {
        TextSearchResponse response = new TextSearchResponse(request.query(), List.of(), e.getMessage());
        return ResponseEntity.badRequest().body(new McpSearchResponse("text", response, null));
      }
    }

    if ("semantic".equals(mode)) {
      int k = request.k() == null ? 10 : request.k();
      Map<String, String> filters = new HashMap<>();
      if (request.type() != null && !request.type().isBlank()) filters.put("type", request.type().trim());
      if (request.subtype() != null && !request.subtype().isBlank())
        filters.put("subtype", request.subtype().trim());

      SemanticSearchResponse response = semanticSearchPort.search(request.query(), k, filters);
      McpSearchResponse mcpResponse = new McpSearchResponse("semantic", null, response);
      if (response.error() != null) {
        return ResponseEntity.badRequest().body(mcpResponse);
      }
      return ResponseEntity.ok(mcpResponse);
    }

    return ResponseEntity.badRequest().body(new ErrorResponse("Field `mode` must be one of: text, semantic."));
  }

  @PostMapping(
      path = "/api/mcp/write_observation",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> writeObservation(@RequestBody McpWriteObservationRequest request) {
    if (request == null) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Request body is required."));
    }
    if (request.text() == null || request.text().isBlank()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Field `text` must not be blank."));
    }

    ObservationSubtype parsedSubtype;
    try {
      String rawSubtype = request.subtype();
      if (rawSubtype == null || rawSubtype.isBlank()) {
        parsedSubtype = ObservationSubtype.NOTE;
      } else {
        parsedSubtype = ObservationSubtype.fromJson(rawSubtype.trim().toLowerCase(Locale.ROOT));
      }
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    try {
      Observation saved = observationsPort.save(request.text(), parsedSubtype);
      return ResponseEntity.ok(saved);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }
  }

  public record McpStatusResponse(boolean running, List<String> tools) {}

  public record ErrorResponse(String error) {}
}
