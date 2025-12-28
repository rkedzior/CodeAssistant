package app.platform.delivery.web;

import app.core.search.InvalidRegexException;
import app.core.search.SemanticSearchPort;
import app.core.search.SemanticSearchResponse;
import app.core.search.TextSearchPort;
import app.core.search.TextSearchResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchApiController {
  private final TextSearchPort textSearchPort;
  private final SemanticSearchPort semanticSearchPort;

  public SearchApiController(TextSearchPort textSearchPort, SemanticSearchPort semanticSearchPort) {
    this.textSearchPort = textSearchPort;
    this.semanticSearchPort = semanticSearchPort;
  }

  @GetMapping(path = "/api/search/text", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TextSearchResponse> searchText(
      @RequestParam(name = "query", required = false) String query,
      @RequestParam(name = "regex", required = false, defaultValue = "false") boolean regex) {
    try {
      return ResponseEntity.ok(textSearchPort.search(query, regex));
    } catch (InvalidRegexException e) {
      return ResponseEntity.badRequest().body(new TextSearchResponse(query, List.of(), e.getMessage()));
    }
  }

  @GetMapping(path = "/api/search/semantic", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SemanticSearchResponse> searchSemantic(
      @RequestParam(name = "query", required = false) String query,
      @RequestParam(name = "k", required = false, defaultValue = "10") int k) {
    SemanticSearchResponse response = semanticSearchPort.search(query, k, Map.of());
    if (response.error() != null) {
      return ResponseEntity.badRequest().body(response);
    }
    return ResponseEntity.ok(response);
  }
}
