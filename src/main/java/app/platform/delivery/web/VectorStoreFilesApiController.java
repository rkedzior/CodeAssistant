package app.platform.delivery.web;

import app.core.vectorstore.VectorStoreFileSummary;
import app.core.vectorstore.VectorStorePort;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VectorStoreFilesApiController {
  private final VectorStorePort vectorStorePort;

  public VectorStoreFilesApiController(VectorStorePort vectorStorePort) {
    this.vectorStorePort = vectorStorePort;
  }

  @GetMapping(path = "/api/vectorstore/files", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<VectorStoreFileSummary>> listFiles() {
    return ResponseEntity.ok(vectorStorePort.listFiles());
  }
}
