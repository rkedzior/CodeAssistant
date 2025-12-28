package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigPort;
import app.core.search.InvalidRegexException;
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
  public String search(
      @RequestParam(name = "query", required = false) String query,
      @RequestParam(name = "regex", required = false, defaultValue = "false") boolean regex,
      Model model) {
    boolean configured = projectConfigPort.load().isPresent();
    model.addAttribute("configured", configured);
    model.addAttribute("query", query == null ? "" : query);
    model.addAttribute("regex", regex);
    model.addAttribute("error", null);

    if (configured && query != null && !query.isBlank()) {
      try {
        TextSearchResponse response = textSearchPort.search(query, regex);
        model.addAttribute("results", response.files());
      } catch (InvalidRegexException e) {
        model.addAttribute("error", e.getMessage());
        model.addAttribute("results", List.of());
      }
    } else {
      model.addAttribute("results", List.of());
    }

    return "search";
  }
}
