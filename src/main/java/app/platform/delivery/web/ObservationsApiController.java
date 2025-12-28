package app.platform.delivery.web;

import app.core.observations.Observation;
import app.core.observations.ObservationSubtype;
import app.core.observations.ObservationsPort;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ObservationsApiController {
  private final ObservationsPort observationsPort;

  public ObservationsApiController(ObservationsPort observationsPort) {
    this.observationsPort = observationsPort;
  }

  @PostMapping(
      path = "/api/observations",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> create(@RequestBody CreateObservationRequest request) {
    if (request == null) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Request body is required."));
    }
    if (request.text() == null || request.text().isBlank()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Field `text` must not be blank."));
    }
    if (request.subtype() == null) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Field `subtype` is required."));
    }

    try {
      Observation saved = observationsPort.save(request.text(), request.subtype());
      return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }
  }

  @GetMapping(path = "/api/observations", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Observation>> list() {
    return ResponseEntity.ok(observationsPort.list());
  }

  public record CreateObservationRequest(String text, ObservationSubtype subtype) {}

  public record ErrorResponse(String error) {}
}

