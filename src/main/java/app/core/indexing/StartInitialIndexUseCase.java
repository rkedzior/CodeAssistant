package app.core.indexing;

import app.core.git.GitPort;
import app.core.projectstate.ProjectMetadata;
import app.core.projectstate.ProjectMetadataState;
import app.core.projectstate.ProjectStatePort;
import app.core.vectorstore.VectorStorePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
          uploadTrackedFiles(trackedFiles, (path) -> gitPort.readWorkingTreeFile(path), "");

          updateProgress("Updating metadata…");
          sleep(PROGRESS_STEP_DELAY);

          ProjectMetadataState existingMetadata = projectStatePort.getOrCreateMetadata();
          ProjectMetadata updated =
              new ProjectMetadata(existingMetadata.metadata().schemaVersion(), headCommit);
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
          updateProgress("Update: enumerating tracked files at " + trimmedTarget + "…");
          sleep(PROGRESS_STEP_DELAY);

          List<String> trackedFiles = gitPort.listTrackedFilesAtCommit(trimmedTarget);
          uploadTrackedFiles(
              trackedFiles,
              (path) -> gitPort.readFileAtCommit(trimmedTarget, path),
              "Update: ");

          updateProgress("Update: updating metadata…");
          sleep(PROGRESS_STEP_DELAY);

          ProjectMetadataState existingMetadata = projectStatePort.getOrCreateMetadata();
          ProjectMetadata updated =
              new ProjectMetadata(existingMetadata.metadata().schemaVersion(), trimmedTarget);
          projectStatePort.saveMetadata(updated);
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

  private void uploadTrackedFiles(List<String> trackedFiles, FileReader fileReader, String prefix) {
    String safePrefix = prefix == null ? "" : prefix;

    updateProgress(safePrefix + "Found " + trackedFiles.size() + " tracked files…");
    sleep(PROGRESS_STEP_DELAY);

    updateProgress(safePrefix + "Uploading tracked files…");
    sleep(PROGRESS_STEP_DELAY);

    int uploadedCount = 0;
    int skippedCount = 0;
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
      String fileId = computeFileId(attributes.get().get("path"));
      vectorStorePort.createFile(fileId, content, attributes.get());
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

  @FunctionalInterface
  private interface JobRunner {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface FileReader {
    byte[] read(String repoRelativePath);
  }
}
