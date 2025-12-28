package app.platform.delivery.web;

import app.core.search.TextSearchPort;
import app.core.search.TextSearchResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchApiController {
  private final TextSearchPort textSearchPort;

  public SearchApiController(TextSearchPort textSearchPort) {
    this.textSearchPort = textSearchPort;
  }

  @GetMapping(path = "/api/search/text", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TextSearchResponse> searchText(@RequestParam(name = "query", required = false) String query) {
    return ResponseEntity.ok(textSearchPort.searchExact(query));
  }
}

