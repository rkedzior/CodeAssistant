package app.platform.adapters.spec;

import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigPort;
import app.core.spec.SpecFile;
import app.core.spec.SpecStoragePort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class RepoSpecFolderAdapter implements SpecStoragePort {
  private final ProjectConfigPort projectConfigPort;

  public RepoSpecFolderAdapter(ProjectConfigPort projectConfigPort) {
    this.projectConfigPort = projectConfigPort;
  }

  @Override
  public List<String> listSpecFiles() {
    Path repoPath = resolveLocalRepoPath();
    Path specDir = repoPath.resolve("spec");
    if (!Files.isDirectory(specDir)) {
      return List.of();
    }

    try (Stream<Path> stream = Files.walk(specDir)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(
              p ->
                  p.getFileName() != null
                      && p.getFileName()
                          .toString()
                          .toLowerCase(Locale.ROOT)
                          .endsWith(".md"))
          .map(p -> repoPath.relativize(p).toString().replace('\\', '/'))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to list spec files.", e);
    }
  }

  @Override
  public Optional<SpecFile> readSpecFile(String repoRelativePath) {
    String normalized = normalizeRepoRelativeSpecPath(repoRelativePath);
    Path repoPath = resolveLocalRepoPath();
    Path specDir = repoPath.resolve("spec").normalize();
    Path filePath = repoPath.resolve(normalized).normalize();
    if (!filePath.startsWith(specDir)) {
      throw new IllegalArgumentException("Path must be under spec/.");
    }
    if (!Files.isRegularFile(filePath)) {
      return Optional.empty();
    }

    String content;
    try {
      content = Files.readString(filePath, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read spec file: " + normalized, e);
    }

    return Optional.of(new SpecFile(normalized, content));
  }

  @Override
  public void writeSpecFile(String repoRelativePath, String content) {
    String normalized = normalizeRepoRelativeSpecPath(repoRelativePath);
    Path repoPath = resolveLocalRepoPath();
    Path specDir = repoPath.resolve("spec").normalize();
    Path filePath = repoPath.resolve(normalized).normalize();
    if (!filePath.startsWith(specDir)) {
      throw new IllegalArgumentException("Path must be under spec/.");
    }

    Path parent = filePath.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("Path must be under spec/.");
    }

    try {
      Files.createDirectories(parent);
      Files.writeString(filePath, content == null ? "" : content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write spec file: " + normalized, e);
    }
  }

  private Path resolveLocalRepoPath() {
    ProjectConfig config =
        projectConfigPort.load().orElseThrow(() -> new IllegalStateException("Project is not configured."));

    if (config.localRepoPath() == null || config.localRepoPath().isBlank()) {
      throw new IllegalStateException("Local repo path is not configured.");
    }

    Path repoPath = Path.of(config.localRepoPath());
    if (!Files.exists(repoPath)) {
      throw new IllegalStateException("Local repo path does not exist: " + repoPath);
    }
    return repoPath;
  }

  private static String normalizeRepoRelativeSpecPath(String rawPath) {
    if (rawPath == null) {
      throw new IllegalArgumentException("Path must be non-blank.");
    }

    String path = rawPath.trim().replace('\\', '/');
    while (path.startsWith("./")) {
      path = path.substring(2);
    }

    if (path.isBlank()) {
      throw new IllegalArgumentException("Path must be non-blank.");
    }
    if (path.startsWith("/") || path.startsWith("~") || path.startsWith("\\\\")) {
      throw new IllegalArgumentException("Path must be repo-relative.");
    }
    if (path.contains(":")) {
      throw new IllegalArgumentException("Path must be repo-relative.");
    }
    if (path.indexOf('\u0000') >= 0) {
      throw new IllegalArgumentException("Path contains invalid characters.");
    }
    if (!path.startsWith("spec/")) {
      throw new IllegalArgumentException("Path must be under spec/.");
    }

    String[] parts = path.split("/", -1);
    for (String part : parts) {
      if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
        throw new IllegalArgumentException("Path is not allowed: " + rawPath);
      }
    }

    return path;
  }
}
