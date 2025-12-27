package app.core.projectstate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record ProjectMetadata(int schemaVersion, String lastIndexedCommit) {
  public static ProjectMetadata initial() {
    return new ProjectMetadata(1, null);
  }
}

