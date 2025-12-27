package app.platform.delivery.web;

import app.core.git.GitPort;
import app.core.indexing.IndexJobState;
import app.core.indexing.StartInitialIndexUseCase;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IndexApiController {
  private final StartInitialIndexUseCase startInitialIndexUseCase;
  private final GitPort gitPort;

  public IndexApiController(StartInitialIndexUseCase startInitialIndexUseCase, GitPort gitPort) {
    this.startInitialIndexUseCase = startInitialIndexUseCase;
    this.gitPort = gitPort;
  }

  @PostMapping(path = "/api/index/initial", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IndexJobState> startInitialIndex() {
    return ResponseEntity.accepted().body(startInitialIndexUseCase.startInitialIndex());
  }

  @GetMapping(path = "/api/index/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IndexJobState> getIndexStatus() {
    return ResponseEntity.ok(startInitialIndexUseCase.getStatus());
  }

  @GetMapping(path = "/api/index/tracked-files", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<String>> getTrackedFiles() {
    return ResponseEntity.ok(gitPort.listTrackedFiles());
  }
}
