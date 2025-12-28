package app.platform.delivery.web;

import app.platform.mcp.McpToolRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class McpStatusController {
  private final McpToolRegistry mcpToolRegistry;

  public McpStatusController(McpToolRegistry mcpToolRegistry) {
    this.mcpToolRegistry = mcpToolRegistry;
  }

  @GetMapping("/mcp")
  public String status(Model model) {
    model.addAttribute("running", true);
    model.addAttribute("tools", mcpToolRegistry.listTools());
    return "mcp";
  }
}

