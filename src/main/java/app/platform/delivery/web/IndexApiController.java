package app.platform.delivery.web;

import app.core.indexing.IndexJobState;
import app.core.indexing.StartInitialIndexUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IndexApiController {
  private final StartInitialIndexUseCase startInitialIndexUseCase;

  public IndexApiController(StartInitialIndexUseCase startInitialIndexUseCase) {
    this.startInitialIndexUseCase = startInitialIndexUseCase;
  }

  @PostMapping(path = "/api/index/initial", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IndexJobState> startInitialIndex() {
    return ResponseEntity.accepted().body(startInitialIndexUseCase.startInitialIndex());
  }

  @GetMapping(path = "/api/index/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IndexJobState> getIndexStatus() {
    return ResponseEntity.ok(startInitialIndexUseCase.getStatus());
  }
}

