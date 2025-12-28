package app.platform.delivery.web;

import app.core.analysis.AnalysisRequest;
import app.core.analysis.AnalysisResponse;
import app.core.analysis.RunAnalysisUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalysisApiController {
  private final RunAnalysisUseCase runAnalysisUseCase;

  public AnalysisApiController(RunAnalysisUseCase runAnalysisUseCase) {
    this.runAnalysisUseCase = runAnalysisUseCase;
  }

  @PostMapping(
      path = "/api/analysis",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AnalysisResponse> analyze(@RequestBody AnalysisRequest request) {
    AnalysisResponse response = runAnalysisUseCase.analyze(request);
    if (response.error() != null) {
      return ResponseEntity.badRequest().body(response);
    }
    return ResponseEntity.ok(response);
  }
}

