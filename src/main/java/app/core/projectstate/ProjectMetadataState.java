package app.core.projectstate;

import java.util.Map;

public record ProjectMetadataState(
    String fileId, ProjectMetadata metadata, Map<String, String> attributes) {}

