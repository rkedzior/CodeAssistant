package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigPort;
import app.core.spec.SpecFile;
import app.core.spec.SpecStoragePort;
import app.core.specupdates.ProposeSpecUpdatesUseCase;
import app.core.specupdates.SpecUpdateProposal;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpecApiController {
  private final ProjectConfigPort projectConfigPort;
  private final SpecStoragePort specStoragePort;
  private final ProposeSpecUpdatesUseCase proposeSpecUpdatesUseCase;

  public SpecApiController(
      ProjectConfigPort projectConfigPort,
      SpecStoragePort specStoragePort,
      ProposeSpecUpdatesUseCase proposeSpecUpdatesUseCase) {
    this.projectConfigPort = projectConfigPort;
    this.specStoragePort = specStoragePort;
    this.proposeSpecUpdatesUseCase = proposeSpecUpdatesUseCase;
  }

  @GetMapping(path = "/api/spec/files", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> listFiles() {
    if (projectConfigPort.load().isEmpty()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Project is not configured yet."));
    }

    List<String> files = specStoragePort.listSpecFiles();
    return ResponseEntity.ok(files);
  }

  @GetMapping(path = "/api/spec/file", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> getFile(@RequestParam(name = "path", required = false) String path) {
    if (projectConfigPort.load().isEmpty()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Project is not configured yet."));
    }
    if (path == null || path.isBlank()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Missing required query parameter: path"));
    }

    Optional<SpecFile> file;
    try {
      file = specStoragePort.readSpecFile(path);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    if (file.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new ErrorResponse("Spec file not found: " + path.trim()));
    }

    return ResponseEntity.ok(file.get());
  }

  @PostMapping(path = "/api/spec/propose-updates", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> proposeUpdates() {
    if (projectConfigPort.load().isEmpty()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Project is not configured yet."));
    }

    List<SpecUpdateProposal> proposals;
    try {
      proposals = proposeSpecUpdatesUseCase.propose();
    } catch (RuntimeException e) {
      return ResponseEntity.internalServerError()
          .body(new ErrorResponse("Failed to propose spec updates."));
    }
    return ResponseEntity.ok(proposals);
  }

  public record ErrorResponse(String error) {}
}
