package app.core.projectstate;

import app.core.projectconfig.ProjectConfig;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public record ProjectMetadata(
    int schemaVersion,
    ProjectInfo project,
    OpenAiSettings openai,
    IndexingSettings indexing,
    List<ClassificationRule> classificationRules,
    Map<String, List<String>> pathToOpenAiFileIds) {
  public static final int CURRENT_SCHEMA_VERSION = 2;
  private static final int DEFAULT_MAX_CHUNK_CHARS = 12000;
  private static final int DEFAULT_CHUNK_OVERLAP_CHARS = 800;

  public static ProjectMetadata initial() {
    return initial(null);
  }

  public static ProjectMetadata initial(ProjectConfig config) {
    return new ProjectMetadata(
        CURRENT_SCHEMA_VERSION,
        defaultProjectInfo(config),
        defaultOpenAiSettings(config),
        defaultIndexingSettings(null),
        defaultClassificationRules(),
        Map.of());
  }

  public static ProjectMetadata fromV1(ProjectMetadataV1 v1, ProjectConfig config) {
    String lastIndexedCommit = v1 == null ? null : normalizeOptional(v1.lastIndexedCommit());
    Map<String, List<String>> pathMap =
        v1 == null || v1.pathToFileIds() == null ? Map.of() : Map.copyOf(v1.pathToFileIds());
    return new ProjectMetadata(
        CURRENT_SCHEMA_VERSION,
        defaultProjectInfo(config),
        defaultOpenAiSettings(config),
        defaultIndexingSettings(lastIndexedCommit),
        defaultClassificationRules(),
        pathMap);
  }

  public ProjectMetadata withIndexingUpdate(
      String lastIndexedCommit, Map<String, List<String>> pathToFileIds) {
    IndexingSettings updatedIndexing =
        new IndexingSettings(
            normalizeOptional(lastIndexedCommit),
            indexing == null ? DEFAULT_MAX_CHUNK_CHARS : indexing.maxChunkChars(),
            indexing == null ? DEFAULT_CHUNK_OVERLAP_CHARS : indexing.chunkOverlapChars());
    List<ClassificationRule> rules =
        classificationRules == null || classificationRules.isEmpty()
            ? defaultClassificationRules()
            : classificationRules;
    Map<String, List<String>> pathMap =
        pathToFileIds == null ? Map.of() : Map.copyOf(pathToFileIds);
    return new ProjectMetadata(
        CURRENT_SCHEMA_VERSION, project, openai, updatedIndexing, rules, pathMap);
  }

  @JsonIgnore
  public String lastIndexedCommit() {
    return indexing == null ? null : indexing.lastIndexedCommit();
  }

  @JsonIgnore
  public Map<String, List<String>> pathToFileIdsOrEmpty() {
    return pathToOpenAiFileIds == null ? Map.of() : pathToOpenAiFileIds;
  }

  @JsonIgnore
  public List<ClassificationRule> classificationRulesOrDefault() {
    return classificationRules == null || classificationRules.isEmpty()
        ? defaultClassificationRules()
        : classificationRules;
  }

  private static ProjectInfo defaultProjectInfo(ProjectConfig config) {
    if (config == null) {
      return null;
    }
    String repoRoot = normalizeOptional(config.localRepoPath());
    String name = null;
    if (repoRoot != null) {
      try {
        Path repoPath = Path.of(repoRoot);
        Path fileName = repoPath.getFileName();
        name = fileName == null ? null : fileName.toString();
      } catch (RuntimeException ignored) {
      }
    }
    if (name == null) {
      String githubRepo = normalizeOptional(config.githubRepo());
      if (githubRepo != null) {
        int slash = githubRepo.lastIndexOf('/');
        name = slash >= 0 && slash < githubRepo.length() - 1 ? githubRepo.substring(slash + 1) : githubRepo;
      }
    }
    if (name == null && repoRoot == null) {
      return null;
    }
    return new ProjectInfo(name, repoRoot);
  }

  private static OpenAiSettings defaultOpenAiSettings(ProjectConfig config) {
    if (config == null) {
      return null;
    }
    String vectorStoreId = normalizeOptional(config.openaiVectorStoreId());
    String model = normalizeOptional(config.openaiModel());
    if (vectorStoreId == null && model == null) {
      return null;
    }
    return new OpenAiSettings(vectorStoreId, model);
  }

  private static IndexingSettings defaultIndexingSettings(String lastIndexedCommit) {
    return new IndexingSettings(
        normalizeOptional(lastIndexedCommit), DEFAULT_MAX_CHUNK_CHARS, DEFAULT_CHUNK_OVERLAP_CHARS);
  }

  public static List<ClassificationRule> defaultClassificationRules() {
    return List.of(
        new ClassificationRule("spec/", "documentation", "spec"),
        new ClassificationRule("README.md", "documentation", "readme"),
        new ClassificationRule("src/main/java/app/platform/delivery/", "code", "ui"),
        new ClassificationRule("src/main/java/app/core/", "code", "business_logic"),
        new ClassificationRule("src/main/java/app/platform/config/", "code", "configuration"),
        new ClassificationRule("src/main/java/app/platform/adapters/", "code", "infrastructure"),
        new ClassificationRule("src/test/", "code", "test"));
  }

  private static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }

  @JsonInclude(Include.NON_NULL)
  public record ProjectInfo(String name, String repoRoot) {}

  @JsonInclude(Include.NON_NULL)
  public record OpenAiSettings(String vectorStoreId, String model) {}

  @JsonInclude(Include.NON_NULL)
  public record IndexingSettings(String lastIndexedCommit, int maxChunkChars, int chunkOverlapChars) {}

  @JsonInclude(Include.NON_NULL)
  public record ClassificationRule(String pathPrefix, String type, String subtype) {}
}
