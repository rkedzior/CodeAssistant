package app.core.indexing;

import app.core.git.GitPort;
import app.core.projectstate.ProjectMetadata;
import app.core.projectstate.ProjectMetadataState;
import app.core.projectstate.ProjectStatePort;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.task.TaskExecutor;

public class StartInitialIndexUseCase {
  private static final Duration PROGRESS_STEP_DELAY = Duration.ofMillis(250);

  private final GitPort gitPort;
  private final ProjectStatePort projectStatePort;
  private final TaskExecutor taskExecutor;

  private final AtomicReference<IndexJobState> state = new AtomicReference<>(IndexJobState.idle());
  private final AtomicReference<CompletableFuture<Void>> running = new AtomicReference<>();

  public StartInitialIndexUseCase(
      GitPort gitPort, ProjectStatePort projectStatePort, TaskExecutor taskExecutor) {
    this.gitPort = gitPort;
    this.projectStatePort = projectStatePort;
    this.taskExecutor = taskExecutor;
  }

  public IndexJobState startInitialIndex() {
    CompletableFuture<Void> existing = running.get();
    if (existing != null && !existing.isDone()) {
      return state.get();
    }

    IndexJobState runningState =
        new IndexJobState(
            IndexJobStatus.RUNNING, "Starting initial index…", Instant.now(), null, null);
    state.set(runningState);

    CompletableFuture<Void> future = new CompletableFuture<>();
    running.set(future);

    taskExecutor.execute(
        () -> {
          try {
            updateProgress("Reading repository HEAD…");
            sleep(PROGRESS_STEP_DELAY);

            String headCommit = gitPort.getHeadCommit();

            updateProgress("Updating metadata…");
            sleep(PROGRESS_STEP_DELAY);

            ProjectMetadataState existingMetadata = projectStatePort.getOrCreateMetadata();
            ProjectMetadata updated =
                new ProjectMetadata(existingMetadata.metadata().schemaVersion(), headCommit);
            projectStatePort.saveMetadata(updated);

            state.set(
                new IndexJobState(
                    IndexJobStatus.SUCCESS,
                    "Completed.",
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

  public IndexJobState getStatus() {
    return state.get();
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
}
