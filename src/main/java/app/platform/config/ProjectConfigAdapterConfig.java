package app.platform.config;

import app.core.projectconfig.ProjectConfigPort;
import app.platform.adapters.projectconfig.FileProjectConfigAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("!test & !e2etest")
public class ProjectConfigAdapterConfig {
  @Bean
  public ProjectConfigPort fileProjectConfigAdapter(
      ObjectMapper objectMapper,
      Environment environment) {
    String configPath =
        environment.getProperty("codeassistant.config.path", ".codeassistant/config.json");
    return new FileProjectConfigAdapter(objectMapper, configPath);
  }
}
