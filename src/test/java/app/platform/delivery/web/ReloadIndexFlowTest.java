package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.core.indexing.IndexJobState;
import app.core.indexing.IndexJobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReloadIndexFlowTest {
  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(10);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @TempDir Path tempDir;

  @Test
  void reloadIndex_apiFlow_updatesLastIndexedCommitFromAtoX() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    RepoCommits commits = initTempGitRepoWithCommitsAandX(repoDir);
    runGit(repoDir, "checkout", commits.commitA());

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString()))
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(post("/api/index/initial"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("RUNNING"));

    IndexJobState initialFinalState = pollUntilFinished();
    assertTrue(
        initialFinalState.status() == IndexJobStatus.SUCCESS,
        "Expected SUCCESS, got " + initialFinalState);

    mockMvc
        .perform(get("/api/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.indexing.lastIndexedCommit").value(commits.commitA()));

    mockMvc
        .perform(
            post("/api/index/reload")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("commit", commits.commitX()))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("RUNNING"));

    IndexJobState reloadFinalState = pollUntilFinished();
    assertTrue(
        reloadFinalState.status() == IndexJobStatus.SUCCESS,
        "Expected SUCCESS, got " + reloadFinalState);

    mockMvc
        .perform(get("/api/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.indexing.lastIndexedCommit").value(commits.commitX()));
  }

  @Test
  void indexPage_rendersReloadControl() throws Exception {
    mockMvc
        .perform(get("/index"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("id=\"reloadBtn\"")))
        .andExpect(content().string(containsString("Full reload")));
  }

  private IndexJobState pollUntilFinished() throws Exception {
    long deadlineNanos = System.nanoTime() + POLL_TIMEOUT.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      MvcResult result = mockMvc.perform(get("/api/index/status")).andReturn();
      IndexJobState state =
          objectMapper.readValue(result.getResponse().getContentAsByteArray(), IndexJobState.class);

      if (state.status() == IndexJobStatus.SUCCESS || state.status() == IndexJobStatus.FAILED) {
        return state;
      }

      Thread.sleep(100);
    }

    throw new AssertionError("Timed out while waiting for indexing job to finish");
  }

  private record RepoCommits(String commitA, String commitX) {}

  private static RepoCommits initTempGitRepoWithCommitsAandX(Path repoDir) throws Exception {
    runGit(repoDir, "init");
    runGit(repoDir, "config", "user.email", "test@example.com");
    runGit(repoDir, "config", "user.name", "Test User");

    Files.writeString(repoDir.resolve("README.md"), "A", StandardCharsets.UTF_8);
    runGit(repoDir, "add", ".");
    runGit(repoDir, "commit", "-m", "Commit A");
    String commitA = runGit(repoDir, "rev-parse", "HEAD").trim();

    Files.writeString(repoDir.resolve("README.md"), "X", StandardCharsets.UTF_8);
    runGit(repoDir, "add", "README.md");
    runGit(repoDir, "commit", "-m", "Commit X");
    String commitX = runGit(repoDir, "rev-parse", "HEAD").trim();

    assertTrue(!commitA.isBlank(), "Expected commit A hash");
    assertTrue(!commitX.isBlank(), "Expected commit X hash");
    assertTrue(!commitA.equals(commitX), "Expected distinct commits");
    assertEquals("X", Files.readString(repoDir.resolve("README.md"), StandardCharsets.UTF_8));

    return new RepoCommits(commitA, commitX);
  }

  private static String runGit(Path repoDir, String... args) throws Exception {
    Process process;
    try {
      ProcessBuilder pb = new ProcessBuilder();
      pb.command(buildGitCommand(args));
      pb.directory(repoDir.toFile());
      process = pb.start();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start git in " + repoDir, e);
    }

    boolean finished = process.waitFor(10, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new IllegalStateException("Timed out while running git in " + repoDir);
    }

    String stdout = readAll(process.getInputStream());
    String stderr = readAll(process.getErrorStream());
    if (process.exitValue() != 0) {
      throw new IllegalStateException("git failed in " + repoDir + ": " + stderr.trim());
    }
    return stdout;
  }

  private static String[] buildGitCommand(String... args) {
    String[] command = new String[args.length + 1];
    command[0] = "git";
    System.arraycopy(args, 0, command, 1, args.length);
    return command;
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
