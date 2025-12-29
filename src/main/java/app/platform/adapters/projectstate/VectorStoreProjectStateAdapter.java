package app.platform.adapters.projectstate;

import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigPort;
import app.core.projectstate.ProjectMetadata;
import app.core.projectstate.ProjectMetadataState;
import app.core.projectstate.ProjectMetadataV1;
import app.core.projectstate.ProjectStatePort;
import app.core.vectorstore.VectorStoreFile;
import app.core.vectorstore.VectorStorePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class VectorStoreProjectStateAdapter implements ProjectStatePort {
  private static final String METADATA_FILE_ID = "metadata.json";
  private static final Map<String, String> LEGACY_METADATA_ATTRIBUTES =
      Map.of("type", "documentation", "subtype", "metadata");
  private static final Map<String, String> METADATA_ATTRIBUTES =
      Map.of("type", "documentation", "subtype", "metadata", "path", METADATA_FILE_ID);

  private final VectorStorePort vectorStorePort;
  private final ObjectMapper objectMapper;
  private final ProjectConfigPort projectConfigPort;

  public VectorStoreProjectStateAdapter(
      VectorStorePort vectorStorePort,
      ObjectMapper objectMapper,
      ProjectConfigPort projectConfigPort) {
    this.vectorStorePort = vectorStorePort;
    this.objectMapper = objectMapper;
    this.projectConfigPort = projectConfigPort;
  }

  @Override
  public ProjectMetadataState getOrCreateMetadata() {
    return readMetadata()
        .orElseGet(
            () -> {
              ProjectMetadata metadata =
                  ProjectMetadata.initial(projectConfigPort.load().orElse(null));
              try {
                String storedFileId =
                    vectorStorePort.createFile(
                        METADATA_FILE_ID,
                        objectMapper.writeValueAsString(metadata)
                            .getBytes(StandardCharsets.UTF_8),
                        METADATA_ATTRIBUTES);
                return new ProjectMetadataState(storedFileId, metadata, METADATA_ATTRIBUTES);
              } catch (IOException e) {
                throw new IllegalStateException("Failed to create " + METADATA_FILE_ID, e);
              }
            });
  }

  @Override
  public Optional<ProjectMetadataState> readMetadata() {
    Optional<String> fileId = vectorStorePort.findByAttributes(METADATA_ATTRIBUTES);
    if (fileId.isEmpty()) {
      fileId = vectorStorePort.findByAttributes(LEGACY_METADATA_ATTRIBUTES);
    }
    if (fileId.isEmpty()) {
      return Optional.empty();
    }

    VectorStoreFile file;
    try {
      file = vectorStorePort.readFile(fileId.get());
    } catch (IllegalStateException e) {
      String message = e.getMessage();
      if (message != null && message.startsWith("Vector store file not found:")) {
        return Optional.empty();
      }
      throw e;
    }
    try {
      MetadataReadResult result = parseMetadata(file.content());
      if (result.migrated()) {
        return Optional.of(saveMetadata(result.metadata()));
      }
      return Optional.of(new ProjectMetadataState(file.fileId(), result.metadata(), file.attributes()));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read " + METADATA_FILE_ID, e);
    }
  }

  @Override
  public ProjectMetadataState saveMetadata(ProjectMetadata metadata) {
    try {
      String storedFileId =
          vectorStorePort.createFile(
              METADATA_FILE_ID,
              objectMapper.writeValueAsBytes(metadata),
              METADATA_ATTRIBUTES);
      return new ProjectMetadataState(storedFileId, metadata, METADATA_ATTRIBUTES);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write " + METADATA_FILE_ID, e);
    }
  }

  private MetadataReadResult parseMetadata(byte[] content) throws IOException {
    JsonNode root = objectMapper.readTree(content);
    int schemaVersion = root.path("schemaVersion").asInt(1);
    ProjectConfig config = projectConfigPort.load().orElse(null);
    if (schemaVersion < ProjectMetadata.CURRENT_SCHEMA_VERSION) {
      ProjectMetadataV1 v1 = objectMapper.treeToValue(root, ProjectMetadataV1.class);
      return new MetadataReadResult(ProjectMetadata.fromV1(v1, config), true);
    }
    ProjectMetadata metadata = objectMapper.treeToValue(root, ProjectMetadata.class);
    return new MetadataReadResult(metadata, false);
  }

  private record MetadataReadResult(ProjectMetadata metadata, boolean migrated) {}
}
