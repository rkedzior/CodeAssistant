package app.platform.adapters.vectorstore;

import app.core.vectorstore.VectorStoreFile;
import app.core.vectorstore.VectorStoreFileSummary;
import app.core.vectorstore.VectorStorePort;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonString;
import com.openai.core.JsonValue;
import com.openai.core.MultipartField;
import com.openai.core.http.HttpResponse;
import com.openai.errors.NotFoundException;
import com.openai.models.files.FileContentParams;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileDeleteParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;
import com.openai.models.vectorstores.files.FileListParams;
import com.openai.models.vectorstores.files.FileRetrieveParams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class OpenAIVectorStoreAdapter implements VectorStorePort {
  private final OpenAIClient client;
  private final String vectorStoreId;

  public OpenAIVectorStoreAdapter(OpenAIClient client, String vectorStoreId) {
    this.client = Objects.requireNonNull(client, "client must not be null.");
    if (vectorStoreId == null || vectorStoreId.trim().isBlank()) {
      throw new IllegalArgumentException("vectorStoreId must be non-blank.");
    }
    this.vectorStoreId = vectorStoreId.trim();
  }

  @Override
  public Optional<String> findByAttributes(Map<String, String> requiredAttributes) {
    Map<String, String> required = requiredAttributes == null ? Map.of() : requiredAttributes;
    return streamVectorStoreFiles()
        .filter(f -> matchesRequired(parseAttributes(f), required))
        .map(com.openai.models.vectorstores.files.VectorStoreFile::id)
        .findFirst();
  }

  @Override
  public String createFile(String fileId, byte[] content, Map<String, String> attributes) {
    if (content == null) {
      throw new IllegalArgumentException("content must not be null.");
    }

    Map<String, String> safeAttributes = attributes == null ? Map.of() : attributes;
    String path = normalizeOptional(safeAttributes.get("path"));
    if (path != null) {
      deleteAllWithPath(path);
    }

    String filename = normalizeOptional(fileId);
    if (filename == null) {
      filename = path == null ? "file" : path;
    }

    FileObject uploaded;
    try {
      MultipartField<InputStream> fileField =
          MultipartField.<InputStream>builder()
              .value(new ByteArrayInputStream(content))
              .filename(filename)
              .contentType("application/octet-stream")
              .build();
      uploaded =
          client
              .files()
              .create(FileCreateParams.builder().file(fileField).purpose(FilePurpose.ASSISTANTS).build());
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to upload file to OpenAI.", e);
    }

    String openAiFileId = uploaded.id();
    try {
      com.openai.models.vectorstores.files.FileCreateParams.Builder attachParams =
          com.openai.models.vectorstores.files.FileCreateParams.builder()
              .vectorStoreId(vectorStoreId)
              .fileId(openAiFileId);
      if (!safeAttributes.isEmpty()) {
        attachParams.attributes(toVectorStoreFileAttributes(safeAttributes));
      }
      client.vectorStores().files().create(attachParams.build());
      return openAiFileId;
    } catch (RuntimeException e) {
      try {
        client.files().delete(FileDeleteParams.builder().fileId(openAiFileId).build());
      } catch (RuntimeException ignored) {
      }
      throw new IllegalStateException("Failed to attach file to OpenAI vector store.", e);
    }
  }

  @Override
  public VectorStoreFile readFile(String fileId) {
    if (fileId == null || fileId.isBlank()) {
      throw new IllegalArgumentException("fileId must be non-blank.");
    }
    String trimmedId = fileId.trim();

    com.openai.models.vectorstores.files.VectorStoreFile vectorStoreFile;
    try {
      vectorStoreFile =
          client
              .vectorStores()
              .files()
              .retrieve(
                  FileRetrieveParams.builder()
                      .vectorStoreId(vectorStoreId)
                      .fileId(trimmedId)
                      .build());
    } catch (NotFoundException e) {
      throw new IllegalStateException("Vector store file not found: " + trimmedId);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to retrieve vector store file: " + trimmedId, e);
    }

    byte[] content;
    try (HttpResponse response = client.files().content(FileContentParams.builder().fileId(trimmedId).build())) {
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "Failed to download file content for " + trimmedId + " (status " + response.statusCode() + ").");
      }
      try (InputStream body = response.body()) {
        content = body.readAllBytes();
      }
    } catch (NotFoundException e) {
      throw new IllegalStateException("Vector store file not found: " + trimmedId);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read downloaded content for " + trimmedId, e);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to download file content for " + trimmedId, e);
    }

    return new VectorStoreFile(trimmedId, content, parseAttributes(vectorStoreFile));
  }

  @Override
  public List<VectorStoreFileSummary> listFiles() {
    return streamVectorStoreFiles()
        .map(f -> new VectorStoreFileSummary(f.id(), f.usageBytes(), parseAttributes(f)))
        .sorted(Comparator.comparing(VectorStoreFileSummary::fileId))
        .toList();
  }

  @Override
  public void deleteFile(String fileId) {
    if (fileId == null || fileId.isBlank()) {
      throw new IllegalArgumentException("fileId must be non-blank.");
    }
    String trimmedId = fileId.trim();
    try {
      client
          .vectorStores()
          .files()
          .delete(
              com.openai.models.vectorstores.files.FileDeleteParams.builder()
                  .vectorStoreId(vectorStoreId)
                  .fileId(trimmedId)
                  .build());
    } catch (NotFoundException ignored) {
      return;
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to delete from OpenAI vector store: " + trimmedId, e);
    }

    try {
      client.files().delete(FileDeleteParams.builder().fileId(trimmedId).build());
    } catch (NotFoundException ignored) {
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to delete underlying OpenAI file: " + trimmedId, e);
    }
  }

  private Stream<com.openai.models.vectorstores.files.VectorStoreFile> streamVectorStoreFiles() {
    return client
        .vectorStores()
        .files()
        .list(FileListParams.builder().vectorStoreId(vectorStoreId).limit(100L).build())
        .autoPager()
        .stream();
  }

  private void deleteAllWithPath(String path) {
    streamVectorStoreFiles()
        .filter(f -> path.equals(parseAttributes(f).get("path")))
        .map(com.openai.models.vectorstores.files.VectorStoreFile::id)
        .forEach(this::deleteFile);
  }

  private static boolean matchesRequired(Map<String, String> attributes, Map<String, String> requiredAttributes) {
    if (requiredAttributes == null || requiredAttributes.isEmpty()) return true;
    if (attributes == null || attributes.isEmpty()) return false;
    for (Map.Entry<String, String> entry : requiredAttributes.entrySet()) {
      if (!Objects.equals(entry.getValue(), attributes.get(entry.getKey()))) {
        return false;
      }
    }
    return true;
  }

  private static Map<String, String> parseAttributes(
      com.openai.models.vectorstores.files.VectorStoreFile file) {        
    if (file == null) return Map.of();

    Optional<com.openai.models.vectorstores.files.VectorStoreFile.Attributes> maybeAttributes =
        file.attributes();
    if (maybeAttributes.isEmpty()) {
      return Map.of();
    }

    Map<String, JsonValue> attributes = maybeAttributes.get()._additionalProperties();
    if (attributes == null || attributes.isEmpty()) return Map.of();

    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, JsonValue> entry : attributes.entrySet()) {
      String key = entry.getKey();
      JsonValue value = entry.getValue();
      if (key == null || key.isBlank() || value == null) continue;
      if (value instanceof JsonString jsonString) {
        result.put(key, jsonString.value());
        continue;
      }
      try {
        String converted = value.convert(String.class);
        if (converted != null) {
          result.put(key, converted);
        }
      } catch (RuntimeException ignored) {
      }
    }

    return result.isEmpty() ? Map.of() : Map.copyOf(result);
  }

  private static com.openai.models.vectorstores.files.FileCreateParams.Attributes
      toVectorStoreFileAttributes(Map<String, String> attributes) {
    com.openai.models.vectorstores.files.FileCreateParams.Attributes.Builder builder =
        com.openai.models.vectorstores.files.FileCreateParams.Attributes.builder();
    if (attributes == null || attributes.isEmpty()) {
      return builder.build();
    }
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      String key = normalizeOptional(entry.getKey());
      String value = entry.getValue();
      if (key == null || value == null) continue;
      builder.putAdditionalProperty(key, JsonValue.from(value));
    }
    return builder.build();
  }

  private static String normalizeOptional(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }
}

