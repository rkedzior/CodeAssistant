package app.platform.adapters.projectconfig;

import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class FileProjectConfigAdapter implements ProjectConfigPort {
  private final ObjectMapper objectMapper;
  private final Path configFile;

  public FileProjectConfigAdapter(
      ObjectMapper objectMapper,
      @Value("${codeassistant.config.path:.codeassistant/config.json}") String configPath) {
    this.objectMapper = objectMapper;
    this.configFile = Path.of(configPath);
  }

  @Override
  public Optional<ProjectConfig> load() {
    if (!Files.exists(configFile)) {
      return Optional.empty();
    }

    try {
      return Optional.of(objectMapper.readValue(Files.readAllBytes(configFile), ProjectConfig.class));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read config from " + configFile, e);
    }
  }

  @Override
  public void save(ProjectConfig config) {
    try {
      Path parent = configFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }

      Path tmp = Files.createTempFile(parent, "config", ".json");
      Files.writeString(tmp, objectMapper.writeValueAsString(config));
      Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write config to " + configFile, e);
    }
  }
}

