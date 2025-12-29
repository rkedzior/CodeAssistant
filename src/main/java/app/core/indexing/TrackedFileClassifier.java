package app.core.indexing;

import app.core.projectstate.ProjectMetadata;
import app.core.projectstate.ProjectStatePort;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TrackedFileClassifier {
  private static final Set<String> KNOWN_CODE_EXTENSIONS = knownCodeExtensions();

  private final ProjectStatePort projectStatePort;

  public TrackedFileClassifier(ProjectStatePort projectStatePort) {
    this.projectStatePort = projectStatePort;
  }

  public Optional<Map<String, String>> classify(String repoRelativePath) {
    if (repoRelativePath == null || repoRelativePath.isBlank()) {
      return Optional.empty();
    }

    String normalized = normalize(repoRelativePath);
    for (ProjectMetadata.ClassificationRule rule : resolveRules()) {
      if (rule == null) {
        continue;
      }
      String prefix = normalize(rule.pathPrefix());
      if (prefix != null && !prefix.isBlank() && normalized.startsWith(prefix)) {
        return Optional.of(
            Map.of("type", rule.type(), "subtype", rule.subtype(), "path", normalized));
      }
    }

    if (isKnownCodeExtension(normalized)) {
      return Optional.of(Map.of("type", "code", "subtype", "other", "path", normalized));
    }

    return Optional.empty();
  }

  private List<ProjectMetadata.ClassificationRule> resolveRules() {
    ProjectMetadata metadata = projectStatePort.getOrCreateMetadata().metadata();
    if (metadata == null) {
      return ProjectMetadata.defaultClassificationRules();
    }
    return metadata.classificationRulesOrDefault();
  }

  private static String normalize(String repoRelativePath) {
    if (repoRelativePath == null) {
      return null;
    }
    String normalized = repoRelativePath.replace('\\', '/');
    if (normalized.startsWith("./")) {
      normalized = normalized.substring(2);
    }
    return normalized;
  }

  private static boolean isKnownCodeExtension(String normalizedPath) {
    int lastDot = normalizedPath.lastIndexOf('.');
    if (lastDot < 0 || lastDot == normalizedPath.length() - 1) {
      return false;
    }

    String extension = normalizedPath.substring(lastDot + 1).toLowerCase();
    return KNOWN_CODE_EXTENSIONS.contains(extension);
  }

  private static Set<String> knownCodeExtensions() {
    Set<String> extensions = new HashSet<>();
    extensions.add("java");
    extensions.add("kt");
    extensions.add("kts");
    extensions.add("xml");
    extensions.add("yml");
    extensions.add("yaml");
    extensions.add("properties");
    extensions.add("json");
    extensions.add("js");
    extensions.add("ts");
    extensions.add("tsx");
    extensions.add("jsx");
    extensions.add("css");
    extensions.add("scss");
    extensions.add("html");
    extensions.add("sql");
    extensions.add("sh");
    extensions.add("ps1");
    extensions.add("bat");
    return Set.copyOf(extensions);
  }
}
