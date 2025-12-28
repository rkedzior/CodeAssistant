package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigPort;
import app.core.search.TextSearchPort;
import app.core.search.TextSearchResponse;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SearchController {
  private final ProjectConfigPort projectConfigPort;
  private final TextSearchPort textSearchPort;

  public SearchController(ProjectConfigPort projectConfigPort, TextSearchPort textSearchPort) {
    this.projectConfigPort = projectConfigPort;
    this.textSearchPort = textSearchPort;
  }

  @GetMapping("/search")
  public String search(@RequestParam(name = "query", required = false) String query, Model model) {
    boolean configured = projectConfigPort.load().isPresent();
    model.addAttribute("configured", configured);
    model.addAttribute("query", query == null ? "" : query);

    if (configured && query != null && !query.isBlank()) {
      TextSearchResponse response = textSearchPort.searchExact(query);
      model.addAttribute("results", response.files());
    } else {
      model.addAttribute("results", List.of());
    }

    return "search";
  }
}

