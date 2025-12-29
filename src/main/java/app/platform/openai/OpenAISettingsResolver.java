package app.platform.openai;

import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigPort;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class OpenAISettingsResolver {
  public static final String DEFAULT_OPENAI_MODEL = "gpt-4.1-mini";

  private final Environment environment;
  private final ProjectConfigPort projectConfigPort;
  private final boolean allowKeyInUi;

  public OpenAISettingsResolver(
      Environment environment,
      ProjectConfigPort projectConfigPort,
      @Value("${codeassistant.allowKeyInUi:false}") boolean allowKeyInUi) {
    this.environment = environment;
    this.projectConfigPort = projectConfigPort;
    this.allowKeyInUi = allowKeyInUi;
  }

  public ResolvedOpenAISettings resolve() {
    Optional<ProjectConfig> config = projectConfigPort.load();

    ResolvedApiKey apiKey = resolveApiKey(environment, config, allowKeyInUi);
    String model = resolveModel(config);
    String vectorStoreId = resolveVectorStoreId(config);

    return new ResolvedOpenAISettings(apiKey.apiKey() != null, model, vectorStoreId, apiKey.source());
  }

  public ResolvedApiKey resolveApiKey() {
    return resolveApiKey(environment, projectConfigPort.load(), allowKeyInUi);
  }

  public static ResolvedApiKey resolveApiKey(
      Environment environment, Optional<ProjectConfig> config, boolean allowKeyInUi) {
    String envKey = normalizeOptional(environment.getProperty("OPENAI_API_KEY"));
    if (envKey != null) return new ResolvedApiKey(envKey, ApiKeySource.ENV);

    String propertyKey = normalizeOptional(environment.getProperty("openai.api.key"));
    if (propertyKey != null) return new ResolvedApiKey(propertyKey, ApiKeySource.PROPERTY);

    if (allowKeyInUi) {
      String configKey =
          config.map(ProjectConfig::openaiApiKey).map(OpenAISettingsResolver::normalizeOptional).orElse(null);
      if (configKey != null) return new ResolvedApiKey(configKey, ApiKeySource.PROJECT_CONFIG);
    }

    return new ResolvedApiKey(null, ApiKeySource.MISSING);
  }

  public static ResolvedApiKey resolveApiKey(Environment environment, Optional<ProjectConfig> config) {
    return resolveApiKey(environment, config, false);
  }

  static String resolveModel(Optional<ProjectConfig> config) {
    return config.map(ProjectConfig::openaiModel)
        .map(OpenAISettingsResolver::normalizeOptional)
        .orElse(DEFAULT_OPENAI_MODEL);
  }

  static String resolveVectorStoreId(Optional<ProjectConfig> config) {
    return config.map(ProjectConfig::openaiVectorStoreId)
        .map(OpenAISettingsResolver::normalizeOptional)
        .orElse(null);
  }

  public static String normalizeOptional(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }

  public record ResolvedApiKey(String apiKey, ApiKeySource source) {}

  public enum ApiKeySource {
    ENV("env"),
    PROPERTY("property"),
    PROJECT_CONFIG("projectConfig"),
    MISSING("missing");

    private final String jsonValue;

    ApiKeySource(String jsonValue) {
      this.jsonValue = jsonValue;
    }

    public String jsonValue() {
      return jsonValue;
    }
  }

  public record ResolvedOpenAISettings(
      boolean configured, String model, String vectorStoreId, ApiKeySource apiKeySource) {}
}
