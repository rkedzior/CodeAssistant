package app.platform.adapters.git;

import app.core.git.GitDiffEntry;
import app.core.git.GitPort;
import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
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
    return runGitText(repoPath, DEFAULT_TIMEOUT, "rev-parse", "HEAD").trim();
  }

  @Override
  public List<String> listTrackedFiles() {
    Path repoPath = resolveLocalRepoPath();
    String stdout = runGitText(repoPath, DEFAULT_TIMEOUT, "ls-files", "-z");
    return parseNullSeparatedList(stdout);
  }

  @Override
  public byte[] readWorkingTreeFile(String repoRelativePath) {
    if (repoRelativePath == null || repoRelativePath.isBlank()) {
      throw new IllegalArgumentException("repoRelativePath must be non-blank.");
    }

    Path repoPath = resolveLocalRepoPath();
    Path filePath = repoPath.resolve(repoRelativePath);
    try {
      return Files.readAllBytes(filePath);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read repo file: " + repoRelativePath, e);
    }
  }

  @Override
  public List<String> listTrackedFilesAtCommit(String commit) {
    if (commit == null || commit.isBlank()) {
      throw new IllegalArgumentException("commit must be non-blank.");
    }

    Path repoPath = resolveLocalRepoPath();
    String stdout =
        runGitText(repoPath, DEFAULT_TIMEOUT, "ls-tree", "-r", "-z", "--name-only", commit.trim());
    return parseNullSeparatedList(stdout);
  }

  @Override
  public byte[] readFileAtCommit(String commit, String repoRelativePath) {
    if (commit == null || commit.isBlank()) {
      throw new IllegalArgumentException("commit must be non-blank.");
    }
    if (repoRelativePath == null || repoRelativePath.isBlank()) {
      throw new IllegalArgumentException("repoRelativePath must be non-blank.");
    }

    Path repoPath = resolveLocalRepoPath();
    return runGitBytes(repoPath, DEFAULT_TIMEOUT, "show", commit.trim() + ":" + repoRelativePath);
  }

  @Override
  public List<GitDiffEntry> listChangedFiles(String fromCommit, String toCommit) {
    if (fromCommit == null || fromCommit.isBlank()) {
      throw new IllegalArgumentException("fromCommit must be non-blank.");
    }
    if (toCommit == null || toCommit.isBlank()) {
      throw new IllegalArgumentException("toCommit must be non-blank.");
    }

    Path repoPath = resolveLocalRepoPath();
    String stdout =
        runGitText(
            repoPath,
            DEFAULT_TIMEOUT,
            "diff",
            "--name-status",
            "-M",
            "-z",
            fromCommit.trim() + ".." + toCommit.trim());
    return parseDiffNameStatus(stdout);
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

  private static List<GitDiffEntry> parseDiffNameStatus(String stdout) {
    if (stdout == null || stdout.isEmpty()) {
      return List.of();
    }

    String[] tokens = stdout.split("\u0000", -1);
    List<GitDiffEntry> results = new ArrayList<>();
    int index = 0;
    while (index < tokens.length) {
      String status = tokens[index++];
      if (status == null || status.isBlank()) {
        continue;
      }

      char code = status.charAt(0);
      if (code == 'R') {
        if (index + 1 >= tokens.length) {
          break;
        }
        String fromPath = tokens[index++];
        String toPath = tokens[index++];
        if (fromPath != null && !fromPath.isBlank() && toPath != null && !toPath.isBlank()) {
          results.add(new GitDiffEntry(GitDiffEntry.Type.RENAMED, toPath, fromPath));
        }
        continue;
      }

      if (code == 'C') {
        if (index + 1 >= tokens.length) {
          break;
        }
        String fromPath = tokens[index++];
        String toPath = tokens[index++];
        if (toPath != null && !toPath.isBlank()) {
          results.add(new GitDiffEntry(GitDiffEntry.Type.ADDED, toPath, fromPath));
        }
        continue;
      }

      if (index >= tokens.length) {
        break;
      }
      String path = tokens[index++];
      if (path == null || path.isBlank()) {
        continue;
      }

      GitDiffEntry.Type type =
          switch (code) {
            case 'A' -> GitDiffEntry.Type.ADDED;
            case 'M' -> GitDiffEntry.Type.MODIFIED;
            case 'D' -> GitDiffEntry.Type.DELETED;
            default -> GitDiffEntry.Type.MODIFIED;
          };
      results.add(new GitDiffEntry(type, path, null));
    }

    return results;
  }

  private static String runGitText(Path repoPath, Duration timeout, String... args) {
    GitOutput output = runGitRaw(repoPath, timeout, args);
    return new String(output.stdout(), StandardCharsets.UTF_8);
  }

  private static byte[] runGitBytes(Path repoPath, Duration timeout, String... args) {
    GitOutput output = runGitRaw(repoPath, timeout, args);
    return output.stdout();
  }

  private static GitOutput runGitRaw(Path repoPath, Duration timeout, String... args) {
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

    StreamReader stdoutReader = new StreamReader(process.getInputStream());
    StreamReader stderrReader = new StreamReader(process.getErrorStream());

    Thread stdoutThread = new Thread(stdoutReader, "git-stdout-reader");
    Thread stderrThread = new Thread(stderrReader, "git-stderr-reader");
    stdoutThread.setDaemon(true);
    stderrThread.setDaemon(true);
    stdoutThread.start();
    stderrThread.start();

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

    try {
      stdoutThread.join();
      stderrThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while reading git output.", e);
    }

    stdoutReader.throwIfFailed();
    stderrReader.throwIfFailed();

    byte[] stdout = stdoutReader.bytes();
    byte[] stderr = stderrReader.bytes();

    if (process.exitValue() != 0) {
      String stderrText = new String(stderr, StandardCharsets.UTF_8);
      String message =
          "git failed (exit=" + process.exitValue() + ") in " + repoPath + ": " + stderrText.trim();
      throw new IllegalStateException(message);
    }

    return new GitOutput(stdout, stderr);
  }

  private record GitOutput(byte[] stdout, byte[] stderr) {}

  private static final class StreamReader implements Runnable {
    private final java.io.InputStream inputStream;
    private volatile byte[] bytes;
    private volatile RuntimeException failure;

    private StreamReader(java.io.InputStream inputStream) {
      this.inputStream = inputStream;
    }

    @Override
    public void run() {
      try (inputStream) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        inputStream.transferTo(output);
        bytes = output.toByteArray();
      } catch (IOException e) {
        failure = new UncheckedIOException(e);
      }
    }

    public byte[] bytes() {
      return bytes == null ? new byte[0] : bytes;
    }

    public void throwIfFailed() {
      if (failure != null) {
        throw failure;
      }
    }
  }
}
