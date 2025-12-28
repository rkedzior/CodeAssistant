package app.core.spec;

import java.util.List;
import java.util.Optional;

public interface SpecStoragePort {
  List<String> listSpecFiles();

  Optional<SpecFile> readSpecFile(String repoRelativePath);

  void writeSpecFile(String repoRelativePath, String content);
}
