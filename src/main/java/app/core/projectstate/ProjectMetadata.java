package app.core.projectstate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public record ProjectMetadata(
    int schemaVersion, String lastIndexedCommit, Map<String, List<String>> pathToFileIds) {
  public static ProjectMetadata initial() {
    return new ProjectMetadata(1, null, Map.of());
  }

  public Map<String, List<String>> pathToFileIdsOrEmpty() {
    return pathToFileIds == null ? Map.of() : pathToFileIds;
  }
}
