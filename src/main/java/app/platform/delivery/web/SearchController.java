package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigPort;
import app.core.search.InvalidRegexException;
import app.core.search.SemanticSearchPort;
import app.core.search.SemanticSearchResponse;
import app.core.search.TextSearchPort;
import app.core.search.TextSearchResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SearchController {
  private final ProjectConfigPort projectConfigPort;
  private final TextSearchPort textSearchPort;
  private final SemanticSearchPort semanticSearchPort;

  public SearchController(
      ProjectConfigPort projectConfigPort,
      TextSearchPort textSearchPort,
      SemanticSearchPort semanticSearchPort) {
    this.projectConfigPort = projectConfigPort;
    this.textSearchPort = textSearchPort;
    this.semanticSearchPort = semanticSearchPort;
  }

  @GetMapping("/search")
  public String search(
      @RequestParam(name = "query", required = false) String query,
      @RequestParam(name = "regex", required = false, defaultValue = "false") boolean regex,
      @RequestParam(name = "mode", required = false, defaultValue = "text") String mode,
      @RequestParam(name = "k", required = false, defaultValue = "10") int k,   
      @RequestParam(name = "type", required = false) String type,
      @RequestParam(name = "subtype", required = false) String subtype,
      Model model) {
    boolean configured = projectConfigPort.load().isPresent();
    String normalizedMode = "semantic".equalsIgnoreCase(mode) ? "semantic" : "text";
    String normalizedType = type == null ? "" : type.trim();
    String normalizedSubtype = subtype == null ? "" : subtype.trim();

    model.addAttribute("configured", configured);
    model.addAttribute("query", query == null ? "" : query);
    model.addAttribute("regex", regex);
    model.addAttribute("mode", normalizedMode);
    model.addAttribute("k", k);
    model.addAttribute("type", normalizedType);
    model.addAttribute("subtype", normalizedSubtype);
    model.addAttribute("error", null);
    model.addAttribute("textResults", List.of());
    model.addAttribute("semanticResults", List.of());

    if (configured && query != null && !query.isBlank()) {
      if ("semantic".equals(normalizedMode)) {
        Map<String, String> filters = new HashMap<>();
        if (!normalizedType.isBlank()) filters.put("type", normalizedType);
        if (!normalizedSubtype.isBlank()) filters.put("subtype", normalizedSubtype);

        SemanticSearchResponse response = semanticSearchPort.search(query, k, filters);
        model.addAttribute("semanticResults", response.results());
        model.addAttribute("error", response.error());
      } else {
        try {
          TextSearchResponse response = textSearchPort.search(query, regex);    
          model.addAttribute("textResults", response.files());
        } catch (InvalidRegexException e) {
          model.addAttribute("error", e.getMessage());
        }
      }
    }

    return "search";
  }
}
