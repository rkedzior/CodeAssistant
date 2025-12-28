package app.platform.adapters.search;

import app.core.git.GitPort;
import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigPort;
import app.core.search.TextSearchFileResult;
import app.core.search.TextSearchMatchLine;
import app.core.search.TextSearchPort;
import app.core.search.TextSearchResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RipgrepTextSearchAdapter implements TextSearchPort {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
  private static final long MAX_FALLBACK_FILE_BYTES = 2_000_000; // 2MB
  private static final int MAX_MATCH_LINES_PER_FILE = 20;

  private final ProjectConfigPort projectConfigPort;
  private final GitPort gitPort;
  private final boolean forceJavaFallback;
  private final AtomicReference<Boolean> rgAvailable = new AtomicReference<>();

  public RipgrepTextSearchAdapter(
      ProjectConfigPort projectConfigPort,
      GitPort gitPort,
      @Value("${codeassistant.textsearch.forceJavaFallback:false}") boolean forceJavaFallback) {
    this.projectConfigPort = projectConfigPort;
    this.gitPort = gitPort;
    this.forceJavaFallback = forceJavaFallback;
  }

  @Override
  public TextSearchResponse searchExact(String query) {
    if (query == null || query.isBlank()) {
      return new TextSearchResponse(query, List.of());
    }

    List<String> trackedFiles = gitPort.listTrackedFiles();
    if (trackedFiles.isEmpty()) {
      return new TextSearchResponse(query, List.of());
    }

    if (!forceJavaFallback && isRipgrepAvailable()) {
      try {
        return runRipgrep(query, trackedFiles);
      } catch (Exception ignored) {
        // Fall back to Java search for environments without rg or when rg errors.
      }
    }

    return runJavaFallback(query, trackedFiles);
  }

  private TextSearchResponse runRipgrep(String query, List<String> trackedFiles) {
    Path repoPath = resolveLocalRepoPath();

    Path fileList;
    try {
      fileList = Files.createTempFile("codeassistant-tracked-files", ".txt");
      Files.writeString(fileList, String.join("\n", trackedFiles) + "\n", StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write ripgrep file list.", e);
    }

    try {
      Process process;
      try {
        process =
            new ProcessBuilder(
                    "rg",
                    "--fixed-strings",
                    "--vimgrep",
                    "--color",
                    "never",
                    "--no-messages",
                    "--files-from",
                    fileList.toString(),
                    "--",
                    query)
                .directory(repoPath.toFile())
                .start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start rg process.", e);
      }

      boolean finished;
      try {
        finished = process.waitFor(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for rg.", e);
      }

      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("Timed out while running rg in " + repoPath);
      }

      String stdout = readAll(process.getInputStream());
      String stderr = readAll(process.getErrorStream());
      int exit = process.exitValue();

      if (exit == 1) {
        return new TextSearchResponse(query, List.of());
      }
      if (exit != 0) {
        throw new IllegalStateException("rg failed (exit=" + exit + "): " + stderr.trim());
      }

      Map<String, Map<Integer, String>> grouped = new LinkedHashMap<>();
      for (String line : stdout.split("\r?\n")) {
        if (line == null || line.isBlank()) continue;
        String[] parts = line.split(":", 4);
        if (parts.length < 4) continue;
        String path = parts[0];
        int lineNumber;
        try {
          lineNumber = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
          continue;
        }
        String lineText = parts[3];

        Map<Integer, String> fileMatches = grouped.computeIfAbsent(path, ignored -> new TreeMap<>());
        if (fileMatches.size() < MAX_MATCH_LINES_PER_FILE) {
          fileMatches.putIfAbsent(lineNumber, lineText);
        }
      }

      List<TextSearchFileResult> results = new ArrayList<>(grouped.size());
      for (Map.Entry<String, Map<Integer, String>> entry : grouped.entrySet()) {
        List<TextSearchMatchLine> matches =
            entry.getValue().entrySet().stream()
                .map(e -> new TextSearchMatchLine(e.getKey(), e.getValue()))
                .toList();
        results.add(new TextSearchFileResult(entry.getKey(), matches));
      }
      results.sort(Comparator.comparing(TextSearchFileResult::path));
      return new TextSearchResponse(query, results);
    } finally {
      try {
        Files.deleteIfExists(fileList);
      } catch (IOException ignored) {
        // best-effort cleanup
      }
    }
  }

  private TextSearchResponse runJavaFallback(String query, List<String> trackedFiles) {
    List<TextSearchFileResult> results = new ArrayList<>();
    for (String repoRelativePath : trackedFiles) {
      byte[] contentBytes = gitPort.readWorkingTreeFile(repoRelativePath);
      if (contentBytes.length > MAX_FALLBACK_FILE_BYTES) continue;
      if (looksBinary(contentBytes)) continue;

      List<TextSearchMatchLine> matches = new ArrayList<>();
      try (java.io.BufferedReader reader =
          new java.io.BufferedReader(
              new InputStreamReader(new ByteArrayInputStream(contentBytes), StandardCharsets.UTF_8))) {
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
          lineNumber++;
          if (line.contains(query)) {
            matches.add(new TextSearchMatchLine(lineNumber, line));
            if (matches.size() >= MAX_MATCH_LINES_PER_FILE) break;
          }
        }
      } catch (IOException ignored) {
        continue;
      }

      if (!matches.isEmpty()) {
        results.add(new TextSearchFileResult(repoRelativePath, matches));
      }
    }

    results.sort(Comparator.comparing(TextSearchFileResult::path));
    return new TextSearchResponse(query, results);
  }

  private boolean isRipgrepAvailable() {
    Boolean cached = rgAvailable.get();
    if (cached != null) return cached;

    boolean available;
    try {
      Process process = new ProcessBuilder("rg", "--version").start();
      boolean finished = process.waitFor(1, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        available = false;
      } else {
        available = process.exitValue() == 0;
      }
    } catch (Exception e) {
      available = false;
    }

    rgAvailable.compareAndSet(null, available);
    return available;
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

  private static boolean looksBinary(byte[] bytes) {
    int max = Math.min(bytes.length, 8192);
    for (int i = 0; i < max; i++) {
      if (bytes[i] == 0) return true;
    }
    return false;
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
