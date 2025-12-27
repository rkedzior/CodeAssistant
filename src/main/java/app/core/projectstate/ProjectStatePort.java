package app.core.projectstate;

import java.util.Optional;

public interface ProjectStatePort {
  ProjectMetadataState getOrCreateMetadata();

  Optional<ProjectMetadataState> readMetadata();
}

