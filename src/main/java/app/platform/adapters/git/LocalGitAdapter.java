package app.platform.adapters.git;

import app.core.git.GitPort;
import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class LocalGitAdapter implements GitPort {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

  private final ProjectConfigPort projectConfigPort;

  public LocalGitAdapter(ProjectConfigPort projectConfigPort) {
    this.projectConfigPort = projectConfigPort;
  }

  @Override
  public String getHeadCommit() {
    Path repoPath = resolveLocalRepoPath();
    return runGit(repoPath, DEFAULT_TIMEOUT, "rev-parse", "HEAD").trim();
  }

  @Override
  public List<String> listTrackedFiles() {
    Path repoPath = resolveLocalRepoPath();
    String stdout = runGit(repoPath, DEFAULT_TIMEOUT, "ls-files", "-z");
    return parseNullSeparatedList(stdout);
  }

  private Path resolveLocalRepoPath() {
    ProjectConfig config =
        projectConfigPort
            .load()
            .orElseThrow(() -> new IllegalStateException("Project is not configured."));

    if (config.localRepoPath() == null || config.localRepoPath().isBlank()) {
      throw new IllegalStateException("Local repo path is not configured.");
    }

    Path repoPath = Path.of(config.localRepoPath());
    if (!Files.exists(repoPath)) {
      throw new IllegalStateException("Local repo path does not exist: " + repoPath);
    }
    return repoPath;
  }

  private static List<String> parseNullSeparatedList(String stdout) {
    if (stdout == null || stdout.isEmpty()) {
      return List.of();
    }

    String[] entries = stdout.split("\u0000", -1);
    List<String> results = new ArrayList<>(entries.length);
    for (String entry : entries) {
      if (entry != null && !entry.isBlank()) {
        results.add(entry);
      }
    }
    return results;
  }

  private static String runGit(Path repoPath, Duration timeout, String... args) {
    List<String> command = new ArrayList<>();
    command.add("git");
    for (String arg : args) {
      command.add(arg);
    }

    Process process;
    try {
      process = new ProcessBuilder(command).directory(repoPath.toFile()).start();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start git process.", e);
    }

    boolean finished;
    try {
      finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for git.", e);
    }

    if (!finished) {
      process.destroyForcibly();
      throw new IllegalStateException("Timed out while running git in " + repoPath);
    }

    String stdout = readAll(process.getInputStream());
    String stderr = readAll(process.getErrorStream());

    if (process.exitValue() != 0) {
      String message =
          "git failed (exit=" + process.exitValue() + ") in " + repoPath + ": " + stderr.trim();
      throw new IllegalStateException(message);
    }

    return stdout;
  }

  private static String readAll(java.io.InputStream inputStream) {
    try (inputStream) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      inputStream.transferTo(output);
      return output.toString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read process output.", e);
    }
  }
}
