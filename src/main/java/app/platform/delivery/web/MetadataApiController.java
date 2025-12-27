package app.platform.delivery.web;

import app.core.projectstate.ProjectMetadataState;
import app.core.projectstate.ProjectStatePort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetadataApiController {
  private final ProjectStatePort projectStatePort;

  public MetadataApiController(ProjectStatePort projectStatePort) {
    this.projectStatePort = projectStatePort;
  }

  @GetMapping(path = "/api/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ProjectMetadataState> getMetadata() {
    return ResponseEntity.ok(projectStatePort.getOrCreateMetadata());
  }
}

