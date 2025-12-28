package app.platform.delivery.web;

import app.platform.mcp.McpToolRegistry;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class McpApiController {
  private final McpToolRegistry mcpToolRegistry;

  public McpApiController(McpToolRegistry mcpToolRegistry) {
    this.mcpToolRegistry = mcpToolRegistry;
  }

  @GetMapping(path = "/api/mcp/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<McpStatusResponse> status() {
    return ResponseEntity.ok(new McpStatusResponse(true, mcpToolRegistry.listTools()));
  }

  public record McpStatusResponse(boolean running, List<String> tools) {}
}

