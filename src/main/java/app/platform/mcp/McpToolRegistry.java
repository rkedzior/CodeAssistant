package app.platform.mcp;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class McpToolRegistry {
  public List<String> listTools() {
    return List.of("search", "write_observation");
  }
}

