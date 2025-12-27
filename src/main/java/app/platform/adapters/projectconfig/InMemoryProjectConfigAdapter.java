package app.platform.adapters.projectconfig;

import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigPort;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemoryProjectConfigAdapter implements ProjectConfigPort {
  private final AtomicReference<ProjectConfig> config = new AtomicReference<>();

  @Override
  public Optional<ProjectConfig> load() {
    return Optional.ofNullable(config.get());
  }

  @Override
  public void save(ProjectConfig config) {
    this.config.set(config);
  }
}

