package app.platform.adapters.vectorstore;

import app.core.vectorstore.VectorStoreFile;
import app.core.vectorstore.VectorStoreFileSummary;
import app.core.vectorstore.VectorStorePort;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"test", "e2etest"})
public class InMemoryVectorStoreAdapter implements VectorStorePort {
  private final ConcurrentHashMap<String, VectorStoreFile> files = new ConcurrentHashMap<>();

  @Override
  public Optional<String> findByAttributes(Map<String, String> requiredAttributes) {
    return files.values().stream()
        .filter(file -> matchesRequired(file.attributes(), requiredAttributes))
        .map(VectorStoreFile::fileId)
        .findFirst();
  }

  @Override
  public String createFile(String fileId, byte[] content, Map<String, String> attributes) {
    files.put(fileId, new VectorStoreFile(fileId, content, attributes));
    return fileId;
  }

  @Override
  public VectorStoreFile readFile(String fileId) {
    VectorStoreFile file = files.get(fileId);
    if (file == null) {
      throw new IllegalStateException("Vector store file not found: " + fileId);
    }
    return file;
  }

  @Override
  public List<VectorStoreFileSummary> listFiles() {
    return files.values().stream()
        .map(file -> new VectorStoreFileSummary(file.fileId(), file.content().length, file.attributes()))
        .sorted(Comparator.comparing(VectorStoreFileSummary::fileId))
        .toList();
  }

  @Override
  public void deleteFile(String fileId) {
    files.remove(fileId);
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
}
