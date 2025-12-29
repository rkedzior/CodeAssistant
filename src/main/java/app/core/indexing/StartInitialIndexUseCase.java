package app.core.indexing;

import app.core.git.GitDiffEntry;
import app.core.git.GitPort;
import app.core.projectstate.ProjectMetadata;
import app.core.projectstate.ProjectMetadataState;
import app.core.projectstate.ProjectStatePort;
import app.core.vectorstore.VectorStoreFileSummary;
import app.core.vectorstore.VectorStorePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.task.TaskExecutor;

public class StartInitialIndexUseCase {
  private static final Duration PROGRESS_STEP_DELAY = Duration.ofMillis(250);

  private final GitPort gitPort;
  private final ProjectStatePort projectStatePort;
  private final VectorStorePort vectorStorePort;
  private final TrackedFileClassifier trackedFileClassifier;
  private final TaskExecutor taskExecutor;

  private final AtomicReference<IndexJobState> state = new AtomicReference<>(IndexJobState.idle());
  private final AtomicReference<CompletableFuture<Void>> running = new AtomicReference<>();

  public StartInitialIndexUseCase(
      GitPort gitPort,
      ProjectStatePort projectStatePort,
      VectorStorePort vectorStorePort,
      TrackedFileClassifier trackedFileClassifier,
      TaskExecutor taskExecutor) {
    this.gitPort = gitPort;
    this.projectStatePort = projectStatePort;
    this.vectorStorePort = vectorStorePort;
    this.trackedFileClassifier = trackedFileClassifier;
    this.taskExecutor = taskExecutor;
  }

  public IndexJobState startInitialIndex() {
    return startJob(
        "Starting initial index…",
        "Completed.",
        () -> {
          updateProgress("Reading repository HEAD…");
          sleep(PROGRESS_STEP_DELAY);

          String headCommit = gitPort.getHeadCommit();

          updateProgress("Enumerating tracked files…");
          sleep(PROGRESS_STEP_DELAY);

          List<String> trackedFiles = gitPort.listTrackedFiles();
          UploadResult uploadResult =
              uploadTrackedFiles(
                  trackedFiles, (path) -> gitPort.readWorkingTreeFile(path), "");

          updateProgress("Updating metadata…");
          sleep(PROGRESS_STEP_DELAY);

          ProjectMetadataState existingMetadata = projectStatePort.getOrCreateMetadata();
          ProjectMetadata updated =
              existingMetadata.metadata().withIndexingUpdate(headCommit, uploadResult.pathToFileIds());
          projectStatePort.saveMetadata(updated);
        });
  }

  public IndexJobState startUpdateIndex(String targetCommit) {
    if (targetCommit == null || targetCommit.isBlank()) {
      throw new IllegalArgumentException("targetCommit must be non-blank.");    
    }

    String trimmedTarget = targetCommit.trim();
    String fromCommit = readLastIndexedCommit();
    String startingProgress =
        fromCommit == null
            ? "Starting index update to " + trimmedTarget + "…"
            : "Starting index update from " + fromCommit + " to " + trimmedTarget + "…";

    return startJob(
        startingProgress,
        "Completed update.",
        () -> {
          if (fromCommit == null) {
            updateProgress("Update: no prior index, running full reload...");
            sleep(PROGRESS_STEP_DELAY);
            runFullReloadIndex(trimmedTarget, "Update: ");
            return;
          }

          updateProgress("Update: reading changes from " + fromCommit + " to " + trimmedTarget + "...");
          sleep(PROGRESS_STEP_DELAY);

          List<GitDiffEntry> diffEntries = gitPort.listChangedFiles(fromCommit, trimmedTarget);
          DiffPlan diffPlan = buildDiffPlan(diffEntries);

          updateProgress(
              "Update: "
                  + diffPlan.toUpload().size()
                  + " file(s) to upload, "
                  + diffPlan.toDelete().size()
                  + " file(s) to delete...");
          sleep(PROGRESS_STEP_DELAY);

          ProjectMetadataState existingMetadata = projectStatePort.getOrCreateMetadata();
          Map<String, List<String>> pathToFileIds =
              new HashMap<>(existingMetadata.metadata().pathToFileIdsOrEmpty());

          deletePaths(diffPlan.toDelete(), pathToFileIds, "Update: ");

          UploadResult uploadResult =
              uploadTrackedFiles(
                  new ArrayList<>(diffPlan.toUpload()),
                  (path) -> gitPort.readFileAtCommit(trimmedTarget, path),
                  "Update: ");
          pathToFileIds.putAll(uploadResult.pathToFileIds());

          updateProgress("Update: updating metadata...");
          sleep(PROGRESS_STEP_DELAY);

          ProjectMetadata updated =
              existingMetadata.metadata().withIndexingUpdate(trimmedTarget, Map.copyOf(pathToFileIds));
          projectStatePort.saveMetadata(updated);
        });
  }

  public IndexJobState startFullReloadIndex(String targetCommit) {
    if (targetCommit == null || targetCommit.isBlank()) {
      throw new IllegalArgumentException("targetCommit must be non-blank.");
    }

    String trimmedTarget = targetCommit.trim();
    String startingProgress = "Starting full reload at " + trimmedTarget + "...";

    return startJob(
        startingProgress,
        "Completed reload.",
        () -> {
          runFullReloadIndex(trimmedTarget, "Reload: ");
        });
  }

  public IndexJobState getStatus() {
    return state.get();
  }

  private String readLastIndexedCommit() {
    return projectStatePort
        .readMetadata()
        .map(m -> m == null || m.metadata() == null ? null : m.metadata().lastIndexedCommit())
        .map(c -> c == null || c.isBlank() ? null : c.trim())
        .orElse(null);
  }

  private IndexJobState startJob(String startingProgress, String successProgress, JobRunner job) {
    CompletableFuture<Void> existing = running.get();
    if (existing != null && !existing.isDone()) {
      return state.get();
    }

    IndexJobState runningState =
        new IndexJobState(IndexJobStatus.RUNNING, startingProgress, Instant.now(), null, null);
    state.set(runningState);

    CompletableFuture<Void> future = new CompletableFuture<>();
    running.set(future);

    taskExecutor.execute(
        () -> {
          try {
            job.run();
            state.set(
                new IndexJobState(
                    IndexJobStatus.SUCCESS,
                    successProgress,
                    state.get().startedAt(),
                    Instant.now(),
                    null));
            future.complete(null);
          } catch (Exception e) {
            state.set(
                new IndexJobState(
                    IndexJobStatus.FAILED,
                    "Failed.",
                    state.get().startedAt(),
                    Instant.now(),
                    e.getMessage()));
            future.completeExceptionally(e);
          } finally {
            running.set(null);
          }
        });

    return state.get();
  }

  private UploadResult uploadTrackedFiles(
      List<String> trackedFiles, FileReader fileReader, String prefix) {
    String safePrefix = prefix == null ? "" : prefix;

    updateProgress(safePrefix + "Found " + trackedFiles.size() + " tracked files…");
    sleep(PROGRESS_STEP_DELAY);

    updateProgress(safePrefix + "Uploading tracked files…");
    sleep(PROGRESS_STEP_DELAY);

    int uploadedCount = 0;
    int skippedCount = 0;
    Map<String, List<String>> pathToFileIds = new HashMap<>();
    for (int i = 0; i < trackedFiles.size(); i++) {
      String repoRelativePath = trackedFiles.get(i);
      Optional<Map<String, String>> attributes = trackedFileClassifier.classify(repoRelativePath);
      if (attributes.isEmpty()) {
        skippedCount++;
        continue;
      }

      updateProgress(
          safePrefix + "Uploading " + (uploadedCount + 1) + " / " + trackedFiles.size() + "…");
      byte[] content = fileReader.read(repoRelativePath);
      String path = normalizePath(attributes.get().get("path"));
      String fileId = computeFileId(path == null ? repoRelativePath : path);
      String storedFileId = vectorStorePort.createFile(fileId, content, attributes.get());
      if (path != null && !path.isBlank()) {
        pathToFileIds.put(path, List.of(storedFileId));
      }
      uploadedCount++;
    }

    updateProgress(
        safePrefix
            + "Uploaded "
            + uploadedCount
            + " file(s); skipped "
            + skippedCount
            + " file(s)…");
    sleep(PROGRESS_STEP_DELAY);
    return new UploadResult(Map.copyOf(pathToFileIds), uploadedCount, skippedCount);
  }

  private void updateProgress(String progress) {
    IndexJobState current = state.get();
    state.set(
        new IndexJobState(
            current.status(), progress, current.startedAt(), current.finishedAt(), current.error()));
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String computeFileId(String repoRelativePath) {
    String toHash = repoRelativePath == null ? "" : repoRelativePath;
    byte[] digest;
    try {
      digest = MessageDigest.getInstance("SHA-256").digest(toHash.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Missing SHA-256 MessageDigest.", e);
    }
    return "repo_" + HexFormat.of().formatHex(digest);
  }

  private void runFullReloadIndex(String targetCommit, String progressPrefix) {
    String safePrefix = progressPrefix == null ? "" : progressPrefix;

    updateProgress(safePrefix + "Enumerating tracked files at " + targetCommit + "...");
    sleep(PROGRESS_STEP_DELAY);

    List<String> trackedFiles = gitPort.listTrackedFilesAtCommit(targetCommit);
    UploadResult uploadResult =
        uploadTrackedFiles(
            trackedFiles, (path) -> gitPort.readFileAtCommit(targetCommit, path), safePrefix);

    ProjectMetadataState existingMetadata = projectStatePort.getOrCreateMetadata();
    Map<String, List<String>> existingPathToFileIds =
        new HashMap<>(existingMetadata.metadata().pathToFileIdsOrEmpty());
    Set<String> removedPaths = new HashSet<>(existingPathToFileIds.keySet());
    removedPaths.removeAll(uploadResult.pathToFileIds().keySet());
    if (!removedPaths.isEmpty()) {
      deletePaths(removedPaths, existingPathToFileIds, safePrefix);
    }

    updateProgress(safePrefix + "Updating metadata...");
    sleep(PROGRESS_STEP_DELAY);

    ProjectMetadata updated =
        existingMetadata.metadata().withIndexingUpdate(targetCommit, uploadResult.pathToFileIds());
    projectStatePort.saveMetadata(updated);
  }

  private DiffPlan buildDiffPlan(List<GitDiffEntry> diffEntries) {
    Set<String> toUpload = new LinkedHashSet<>();
    Set<String> toDelete = new LinkedHashSet<>();
    if (diffEntries != null) {
      for (GitDiffEntry entry : diffEntries) {
        if (entry == null || entry.type() == null) {
          continue;
        }
        switch (entry.type()) {
          case ADDED, MODIFIED -> addNormalizedPath(entry.path(), toUpload);
          case DELETED -> addNormalizedPath(entry.path(), toDelete);
          case RENAMED -> {
            addNormalizedPath(entry.previousPath(), toDelete);
            addNormalizedPath(entry.path(), toUpload);
          }
        }
      }
    }
    return new DiffPlan(toUpload, toDelete);
  }

  private void addNormalizedPath(String path, Set<String> destination) {
    String normalized = normalizePath(path);
    if (normalized == null || normalized.isBlank()) {
      return;
    }
    destination.add(normalized);
  }

  private void deletePaths(
      Set<String> pathsToDelete, Map<String, List<String>> pathToFileIds, String progressPrefix) {
    if (pathsToDelete == null || pathsToDelete.isEmpty()) {
      return;
    }

    String safePrefix = progressPrefix == null ? "" : progressPrefix;
    updateProgress(safePrefix + "Deleting " + pathsToDelete.size() + " file(s)...");
    sleep(PROGRESS_STEP_DELAY);

    Map<String, List<String>> fallbackPathToFileIds =
        buildPathToFileIds(vectorStorePort.listFiles());

    int deletedCount = 0;
    for (String path : pathsToDelete) {
      String normalized = normalizePath(path);
      if (normalized == null || normalized.isBlank()) {
        continue;
      }
      List<String> fileIds = pathToFileIds.get(normalized);
      if (fileIds == null || fileIds.isEmpty()) {
        fileIds = fallbackPathToFileIds.get(normalized);
      }
      if (fileIds != null) {
        for (String fileId : fileIds) {
          if (fileId == null || fileId.isBlank()) {
            continue;
          }
          vectorStorePort.deleteFile(fileId);
          deletedCount++;
        }
      }
      pathToFileIds.remove(normalized);
    }

    updateProgress(safePrefix + "Deleted " + deletedCount + " file(s)...");
    sleep(PROGRESS_STEP_DELAY);
  }

  private static Map<String, List<String>> buildPathToFileIds(
      List<VectorStoreFileSummary> files) {
    if (files == null || files.isEmpty()) {
      return Map.of();
    }
    Map<String, List<String>> results = new HashMap<>();
    for (VectorStoreFileSummary file : files) {
      if (file == null || file.attributes() == null) {
        continue;
      }
      String path = normalizePath(file.attributes().get("path"));
      if (path == null || path.isBlank()) {
        continue;
      }
      results.computeIfAbsent(path, ignored -> new ArrayList<>()).add(file.fileId());
    }
    return results.isEmpty() ? Map.of() : Map.copyOf(results);
  }

  private static String normalizePath(String path) {
    if (path == null) {
      return null;
    }
    String normalized = path.replace('\\', '/');
    if (normalized.startsWith("./")) {
      normalized = normalized.substring(2);
    }
    return normalized;
  }

  @FunctionalInterface
  private interface JobRunner {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface FileReader {
    byte[] read(String repoRelativePath);
  }

  private record UploadResult(
      Map<String, List<String>> pathToFileIds, int uploadedCount, int skippedCount) {}

  private record DiffPlan(Set<String> toUpload, Set<String> toDelete) {}
}
