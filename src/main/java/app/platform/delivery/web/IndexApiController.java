package app.platform.delivery.web;

import app.core.git.GitPort;
import app.core.indexing.IndexIngestionStatus;
import app.core.indexing.IndexJobState;
import app.core.indexing.IndexJobStatus;
import app.core.indexing.StartInitialIndexUseCase;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  @PostMapping(
      path = "/api/index/update",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IndexJobState> startUpdateIndex(@RequestBody UpdateIndexRequest request) {
    if (request == null || request.commit() == null || request.commit().isBlank()) {
      return ResponseEntity.badRequest()
          .body(
              new IndexJobState(
                  IndexJobStatus.FAILED,
                  "Failed.",
                  Instant.now(),
                  Instant.now(),
                  "Field `commit` must not be blank.",
                  IndexIngestionStatus.empty()));
    }

    try {
      return ResponseEntity.accepted()
          .body(startInitialIndexUseCase.startUpdateIndex(request.commit().trim()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .body(
              new IndexJobState(
                  IndexJobStatus.FAILED,
                  "Failed.",
                  Instant.now(),
                  Instant.now(),
                  e.getMessage(),
                  IndexIngestionStatus.empty()));
    }
  }

  @PostMapping(
      path = "/api/index/reload",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IndexJobState> startReloadIndex(@RequestBody UpdateIndexRequest request) {
    if (request == null || request.commit() == null || request.commit().isBlank()) {
      return ResponseEntity.badRequest()
          .body(
              new IndexJobState(
                  IndexJobStatus.FAILED,
                  "Failed.",
                  Instant.now(),
                  Instant.now(),
                  "Field `commit` must not be blank.",
                  IndexIngestionStatus.empty()));
    }

    try {
      return ResponseEntity.accepted()
          .body(startInitialIndexUseCase.startFullReloadIndex(request.commit().trim()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .body(
              new IndexJobState(
                  IndexJobStatus.FAILED,
                  "Failed.",
                  Instant.now(),
                  Instant.now(),
                  e.getMessage(),
                  IndexIngestionStatus.empty()));
    }
  }

  @GetMapping(path = "/api/index/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IndexJobState> getIndexStatus() {
    return ResponseEntity.ok(startInitialIndexUseCase.getStatus());
  }

  @GetMapping(path = "/api/index/tracked-files", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<String>> getTrackedFiles() {
    return ResponseEntity.ok(gitPort.listTrackedFiles());
  }

  public record UpdateIndexRequest(String commit) {}
}
