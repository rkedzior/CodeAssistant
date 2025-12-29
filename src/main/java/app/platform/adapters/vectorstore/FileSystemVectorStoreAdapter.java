package app.platform.adapters.vectorstore;

import app.core.vectorstore.VectorStoreFile;
import app.core.vectorstore.VectorStoreFileSummary;
import app.core.vectorstore.VectorStorePort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FileSystemVectorStoreAdapter implements VectorStorePort {
  private static final TypeReference<Map<String, String>> STRING_MAP =
      new TypeReference<>() {};

  private final ObjectMapper objectMapper;
  private final Path root;

  public FileSystemVectorStoreAdapter(
      ObjectMapper objectMapper,
      String rootPath) {
    this.objectMapper = objectMapper;
    this.root = Path.of(rootPath);
  }

  @Override
  public Optional<String> findByAttributes(Map<String, String> requiredAttributes) {
    if (!Files.exists(root)) {
      return Optional.empty();
    }

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.attrs.json")) {
      for (Path attrsPath : stream) {
        Map<String, String> attributes = readAttributes(attrsPath);
        if (matchesRequired(attributes, requiredAttributes)) {
          String attrsFileName = attrsPath.getFileName().toString();
          String fileId = attrsFileName.substring(0, attrsFileName.length() - ".attrs.json".length());
          return Optional.of(fileId);
        }
      }
      return Optional.empty();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to search vector store at " + root, e);
    }
  }

  @Override
  public String createFile(String fileId, byte[] content, Map<String, String> attributes) {
    validateFileId(fileId);

    try {
      Files.createDirectories(root);

      Path contentPath = root.resolve(fileId);
      Path attrsPath = root.resolve(fileId + ".attrs.json");

      Path tmpContent = Files.createTempFile(root, fileId.replace('.', '_'), ".tmp");
      Files.write(tmpContent, content);
      Files.move(tmpContent, contentPath, StandardCopyOption.REPLACE_EXISTING);

      Path tmpAttrs = Files.createTempFile(root, fileId.replace('.', '_'), ".attrs.tmp");
      Files.writeString(tmpAttrs, objectMapper.writeValueAsString(attributes));
      Files.move(tmpAttrs, attrsPath, StandardCopyOption.REPLACE_EXISTING);

      return fileId;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write vector store file " + fileId + " to " + root, e);
    }
  }

  @Override
  public VectorStoreFile readFile(String fileId) {
    validateFileId(fileId);

    Path contentPath = root.resolve(fileId);
    Path attrsPath = root.resolve(fileId + ".attrs.json");
    if (!Files.exists(contentPath) || !Files.exists(attrsPath)) {
      throw new IllegalStateException("Vector store file not found: " + fileId);
    }

    try {
      byte[] content = Files.readAllBytes(contentPath);
      Map<String, String> attributes = readAttributes(attrsPath);
      return new VectorStoreFile(fileId, content, attributes);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read vector store file " + fileId + " from " + root, e);
    }
  }

  @Override
  public List<VectorStoreFileSummary> listFiles() {
    if (!Files.exists(root)) {
      return List.of();
    }

    List<VectorStoreFileSummary> results = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.attrs.json")) {
      for (Path attrsPath : stream) {
        String attrsFileName = attrsPath.getFileName().toString();
        String fileId = attrsFileName.substring(0, attrsFileName.length() - ".attrs.json".length());
        validateFileId(fileId);

        Path contentPath = root.resolve(fileId);
        if (!Files.exists(contentPath)) {
          continue;
        }

        Map<String, String> attributes = readAttributes(attrsPath);
        long sizeBytes = Files.size(contentPath);
        results.add(new VectorStoreFileSummary(fileId, sizeBytes, attributes, "ready"));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to list vector store files at " + root, e);
    }

    results.sort(Comparator.comparing(VectorStoreFileSummary::fileId));
    return results;
  }

  @Override
  public void deleteFile(String fileId) {
    validateFileId(fileId);

    Path contentPath = root.resolve(fileId);
    Path attrsPath = root.resolve(fileId + ".attrs.json");
    try {
      Files.deleteIfExists(contentPath);
      Files.deleteIfExists(attrsPath);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to delete vector store file " + fileId + " from " + root, e);
    }
  }

  private Map<String, String> readAttributes(Path attrsPath) throws IOException {
    return objectMapper.readValue(Files.readAllBytes(attrsPath), STRING_MAP);
  }

  private static boolean matchesRequired(
      Map<String, String> attributes, Map<String, String> requiredAttributes) {
    for (Map.Entry<String, String> entry : requiredAttributes.entrySet()) {
      if (!entry.getValue().equals(attributes.get(entry.getKey()))) {
        return false;
      }
    }
    return true;
  }

  private static void validateFileId(String fileId) {
    if (!fileId.matches("[a-zA-Z0-9._-]+")) {
      throw new IllegalArgumentException("Invalid fileId: " + fileId);
    }
  }
}
