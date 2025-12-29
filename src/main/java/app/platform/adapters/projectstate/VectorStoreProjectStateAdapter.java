package app.platform.adapters.projectstate;

import app.core.projectstate.ProjectMetadata;
import app.core.projectstate.ProjectMetadataState;
import app.core.projectstate.ProjectStatePort;
import app.core.vectorstore.VectorStoreFile;
import app.core.vectorstore.VectorStorePort;
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

  public VectorStoreProjectStateAdapter(VectorStorePort vectorStorePort, ObjectMapper objectMapper) {
    this.vectorStorePort = vectorStorePort;
    this.objectMapper = objectMapper;
  }

  @Override
  public ProjectMetadataState getOrCreateMetadata() {
    return readMetadata()
        .orElseGet(
            () -> {
              ProjectMetadata metadata = ProjectMetadata.initial();
              try {
                String storedFileId =
                    vectorStorePort.createFile(
                    METADATA_FILE_ID,
                    objectMapper.writeValueAsString(metadata).getBytes(StandardCharsets.UTF_8),
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
      ProjectMetadata metadata = objectMapper.readValue(file.content(), ProjectMetadata.class);
      return Optional.of(new ProjectMetadataState(file.fileId(), metadata, file.attributes()));
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
}
