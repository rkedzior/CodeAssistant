package app.core.projectstate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public record ProjectMetadataV1(
    int schemaVersion, String lastIndexedCommit, Map<String, List<String>> pathToFileIds) {}
