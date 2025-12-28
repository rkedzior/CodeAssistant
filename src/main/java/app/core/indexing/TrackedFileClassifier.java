package app.core.indexing;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TrackedFileClassifier {
  private static final Set<String> KNOWN_CODE_EXTENSIONS = knownCodeExtensions();

  public Optional<Map<String, String>> classify(String repoRelativePath) {
    if (repoRelativePath == null || repoRelativePath.isBlank()) {
      return Optional.empty();
    }

    String normalized = normalize(repoRelativePath);

    if (isSpecMarkdown(normalized)) {
      return Optional.of(Map.of("type", "documentation", "subtype", "spec", "path", normalized));
    }

    if (normalized.equals("README.md")) {
      return Optional.of(Map.of("type", "documentation", "subtype", "readme", "path", normalized));
    }

    if (normalized.startsWith("src/main/java/app/platform/delivery/")) {
      return Optional.of(Map.of("type", "code", "subtype", "ui", "path", normalized));
    }

    if (normalized.startsWith("src/main/java/app/core/")) {
      return Optional.of(Map.of("type", "code", "subtype", "business_logic", "path", normalized));
    }

    if (normalized.startsWith("src/main/java/app/platform/config/")) {
      return Optional.of(Map.of("type", "code", "subtype", "configuration", "path", normalized));
    }

    if (normalized.startsWith("src/main/java/app/platform/adapters/")) {
      return Optional.of(Map.of("type", "code", "subtype", "infrastructure", "path", normalized));
    }

    if (normalized.startsWith("src/test/")) {
      return Optional.of(Map.of("type", "code", "subtype", "test", "path", normalized));
    }

    if (isKnownCodeExtension(normalized)) {
      return Optional.of(Map.of("type", "code", "subtype", "other", "path", normalized));
    }

    return Optional.empty();
  }

  private static boolean isSpecMarkdown(String normalizedPath) {
    return normalizedPath.startsWith("spec/") && normalizedPath.endsWith(".md");
  }

  private static String normalize(String repoRelativePath) {
    String normalized = repoRelativePath.replace('\\', '/');
    if (normalized.startsWith("./")) {
      return normalized.substring(2);
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
