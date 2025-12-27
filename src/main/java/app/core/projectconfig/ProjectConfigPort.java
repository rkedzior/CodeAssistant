package app.core.projectconfig;

import java.util.Optional;

public interface ProjectConfigPort {
  Optional<ProjectConfig> load();

  void save(ProjectConfig config);
}

