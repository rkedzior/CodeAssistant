package app.core.vectorstore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface VectorStorePort {
  Optional<String> findByAttributes(Map<String, String> requiredAttributes);

  String createFile(String fileId, byte[] content, Map<String, String> attributes);

  VectorStoreFile readFile(String fileId);

  List<VectorStoreFileSummary> listFiles();
}
