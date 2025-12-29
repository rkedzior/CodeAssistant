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
  private static final Duration INGESTION_POLL_INTERVAL = Duration.ofSeconds(1);
  private static final Duration INGESTION_TIMEOUT = Duration.ofSeconds(60);
  private static final int MAX_INGESTION_FAILURES = 10;

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

          waitForIngestion(uploadResult, "");

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

          waitForIngestion(uploadResult, "Update: ");

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
        new IndexJobState(
            IndexJobStatus.RUNNING,
            startingProgress,
            Instant.now(),
            null,
            null,
            IndexIngestionStatus.empty());
    state.set(runningState);

    CompletableFuture<Void> future = new CompletableFuture<>();
    running.set(future);

    taskExecutor.execute(
        () -> {
          try {
            job.run();
            IndexJobState current = state.get();
            state.set(
                new IndexJobState(
                    IndexJobStatus.SUCCESS,
                    successProgress,
                    current.startedAt(),
                    Instant.now(),
                    current.error(),
                    current.ingestion()));
            future.complete(null);
          } catch (Exception e) {
            IndexJobState current = state.get();
            state.set(
                new IndexJobState(
                    IndexJobStatus.FAILED,
                    "Failed.",
                    current.startedAt(),
                    Instant.now(),
                    e.getMessage(),
                    current.ingestion()));
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
    Map<String, String> fileIdToPath = new HashMap<>();
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
      if (storedFileId != null && !storedFileId.isBlank()) {
        String pathForStatus =
            path == null || path.isBlank() ? normalizePath(repoRelativePath) : path;
        fileIdToPath.put(storedFileId, pathForStatus);
      }
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
    return new UploadResult(
        Map.copyOf(pathToFileIds),
        Map.copyOf(fileIdToPath),
        uploadedCount,
        skippedCount);
  }

  private void waitForIngestion(UploadResult uploadResult, String progressPrefix) {
    if (uploadResult == null || uploadResult.uploadedCount() == 0) {
      updateIngestionStatus(IndexIngestionStatus.empty(), null);
      return;
    }

    String safePrefix = progressPrefix == null ? "" : progressPrefix;
    IndexIngestionStatus status = buildIngestionStatus(uploadResult);
    updateIngestionStatus(status, formatIngestionProgress(safePrefix, status));

    if (status.processing() == 0) {
      recordIngestionError(status);
      return;
    }

    Instant deadline = Instant.now().plus(INGESTION_TIMEOUT);
    while (Instant.now().isBefore(deadline) && status.processing() > 0) {
      sleep(INGESTION_POLL_INTERVAL);
      status = buildIngestionStatus(uploadResult);
      updateIngestionStatus(status, formatIngestionProgress(safePrefix, status));
    }

    if (status.processing() > 0) {
      updateError("Timed out waiting for vector store ingestion.");
      return;
    }

    recordIngestionError(status);
  }

  private void recordIngestionError(IndexIngestionStatus status) {
    if (status != null && status.failed() > 0) {
      updateError("Vector store ingestion failed for " + status.failed() + " file(s).");
    }
  }

  private IndexIngestionStatus buildIngestionStatus(UploadResult uploadResult) {
    Map<String, String> fileIdToPath = uploadResult.fileIdToPath();
    if (fileIdToPath == null || fileIdToPath.isEmpty()) {
      return IndexIngestionStatus.empty();
    }

    Map<String, VectorStoreFileSummary> summaryById = new HashMap<>();
    for (VectorStoreFileSummary summary : vectorStorePort.listFiles()) {
      if (summary != null && summary.fileId() != null) {
        summaryById.put(summary.fileId(), summary);
      }
    }

    int uploaded = fileIdToPath.size();
    int processing = 0;
    int ready = 0;
    int failed = 0;
    List<IndexIngestionStatus.IngestionFailure> failures = new ArrayList<>();

    for (Map.Entry<String, String> entry : fileIdToPath.entrySet()) {
      String fileId = entry.getKey();
      if (fileId == null) {
        continue;
      }

      VectorStoreFileSummary summary = summaryById.get(fileId);
      String path = entry.getValue();
      if ((path == null || path.isBlank()) && summary != null && summary.attributes() != null) {
        path = normalizePath(summary.attributes().get("path"));
      }

      if (summary == null) {
        failed++;
        if (failures.size() < MAX_INGESTION_FAILURES) {
          failures.add(new IndexIngestionStatus.IngestionFailure(fileId, path, "missing"));
        }
        continue;
      }

      String status = normalizeStatus(summary.status());
      IngestionState state = toIngestionState(status);
      switch (state) {
        case READY -> ready++;
        case PROCESSING -> processing++;
        case FAILED -> {
          failed++;
          if (failures.size() < MAX_INGESTION_FAILURES) {
            failures.add(new IndexIngestionStatus.IngestionFailure(fileId, path, status));
          }
        }
      }
    }

    String lastError =
        failed > 0 ? "Vector store ingestion failed for " + failed + " file(s)." : null;
    return new IndexIngestionStatus(uploaded, processing, ready, failed, failures, lastError);
  }

  private static String formatIngestionProgress(String prefix, IndexIngestionStatus status) {
    String safePrefix = prefix == null ? "" : prefix;
    return safePrefix
        + "Ingestion: ready "
        + status.ready()
        + " / "
        + status.uploaded()
        + ", processing "
        + status.processing()
        + ", failed "
        + status.failed()
        + "...";
  }

  private static String normalizeStatus(String status) {
    if (status == null) {
      return null;
    }
    String trimmed = status.trim().toLowerCase();
    return trimmed.isBlank() ? null : trimmed;
  }

  private static IngestionState toIngestionState(String status) {
    if (status == null) {
      return IngestionState.PROCESSING;
    }
    if ("completed".equals(status) || "ready".equals(status) || "succeeded".equals(status)) {
      return IngestionState.READY;
    }
    if ("failed".equals(status)
        || "error".equals(status)
        || "cancelled".equals(status)
        || "canceled".equals(status)) {
      return IngestionState.FAILED;
    }
    if (status.contains("progress")
        || status.contains("process")
        || status.contains("queued")
        || status.contains("pending")) {
      return IngestionState.PROCESSING;
    }
    return IngestionState.PROCESSING;
  }

  private void updateProgress(String progress) {
    IndexJobState current = state.get();
    state.set(
        new IndexJobState(
            current.status(),
            progress,
            current.startedAt(),
            current.finishedAt(),
            current.error(),
            current.ingestion()));
  }

  private void updateIngestionStatus(IndexIngestionStatus ingestion, String progress) {
    IndexJobState current = state.get();
    String nextProgress = progress == null ? current.progress() : progress;
    state.set(
        new IndexJobState(
            current.status(),
            nextProgress,
            current.startedAt(),
            current.finishedAt(),
            current.error(),
            ingestion));
  }

  private void updateError(String error) {
    IndexJobState current = state.get();
    state.set(
        new IndexJobState(
            current.status(),
            current.progress(),
            current.startedAt(),
            current.finishedAt(),
            error,
            current.ingestion()));
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

    waitForIngestion(uploadResult, safePrefix);

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

  private enum IngestionState {
    READY,
    PROCESSING,
    FAILED
  }

  private record UploadResult(
      Map<String, List<String>> pathToFileIds,
      Map<String, String> fileIdToPath,
      int uploadedCount,
      int skippedCount) {}

  private record DiffPlan(Set<String> toUpload, Set<String> toDelete) {}
}
