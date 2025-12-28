package app.platform.delivery.web;

import app.platform.openai.OpenAISettingsResolver;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenAIStatusApiController {
  private final OpenAISettingsResolver openAISettingsResolver;

  public OpenAIStatusApiController(OpenAISettingsResolver openAISettingsResolver) {
    this.openAISettingsResolver = openAISettingsResolver;
  }

  @GetMapping(path = "/api/openai/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OpenAIStatusResponse> status() {
    OpenAISettingsResolver.ResolvedOpenAISettings settings = openAISettingsResolver.resolve();
    return ResponseEntity.ok(
        new OpenAIStatusResponse(
            settings.configured(),
            settings.model(),
            settings.vectorStoreId(),
            settings.apiKeySource() == null ? null : settings.apiKeySource().jsonValue()));
  }

  public record OpenAIStatusResponse(
      boolean configured, String model, String vectorStoreId, String apiKeySource) {}
}

