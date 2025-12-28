package app.platform.delivery.web;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.core.indexing.IndexJobState;
import app.core.indexing.IndexJobStatus;
import app.platform.mcp.api.McpSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
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
class McpSearchApiTest {
  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @TempDir Path tempDir;

  @Test
  void mcpTextSearch_returnsTrackedFileContainingToken() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    String token = "mcp-token-" + UUID.randomUUID();
    initTempGitRepoWithTrackedFile(repoDir, token);

    configureLocalRepo(repoDir);

    mockMvc
        .perform(
            post("/api/mcp/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new McpSearchRequest("text", token, false, null, null, null))))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.mode").value("text"))
        .andExpect(jsonPath("$.text.query").value(token))
        .andExpect(jsonPath("$.text.files[*].path", hasItem("tracked.txt")));
  }

  @Test
  void mcpSemanticSearch_returnsAtLeastOneResult() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepoWithSemanticFile(repoDir);

    configureLocalRepo(repoDir);
    startIndexingAndWaitUntilFinished();

    String query = "authorization token refresh session";
    mockMvc
        .perform(
            post("/api/mcp/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new McpSearchRequest("semantic", query, null, 5, null, null))))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.mode").value("semantic"))
        .andExpect(jsonPath("$.semantic.query").value(query))
        .andExpect(jsonPath("$.semantic.results.length()").value(greaterThan(0)));
  }

  private void configureLocalRepo(Path repoDir) throws Exception {
    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString())
                .param("openaiApiKey", "sk-test"))
        .andExpect(status().is3xxRedirection());
  }

  private void startIndexingAndWaitUntilFinished() throws Exception {
    mockMvc.perform(post("/api/index/initial")).andExpect(status().isAccepted());
    IndexJobState finalState = pollUntilFinished();
    assertTrue(
        finalState.status() == IndexJobStatus.SUCCESS,
        "Expected SUCCESS, got " + finalState.status() + " (" + finalState.error() + ")");
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

  private static void initTempGitRepoWithTrackedFile(Path repoDir, String token) throws Exception {
    initTempGitRepo(repoDir);
    Files.writeString(repoDir.resolve("tracked.txt"), "Unique token: " + token + "\n", StandardCharsets.UTF_8);
    runGit(repoDir, "add", ".");
    runGit(repoDir, "commit", "-m", "initial");
  }

  private static void initTempGitRepoWithSemanticFile(Path repoDir) throws Exception {
    initTempGitRepo(repoDir);

    Files.createDirectories(repoDir.resolve("src/main/java/app/core"));
    Files.writeString(
        repoDir.resolve("src/main/java/app/core/SemanticMcp.java"),
        String.join(
            "\n",
            "package app.core;",
            "",
            "public class SemanticMcp {",
            "  // authorization token refresh session",
            "  // authorization token refresh session",
            "  // authorization token refresh session",
            "  // authorization token refresh session",
            "}"),
        StandardCharsets.UTF_8);

    runGit(repoDir, "add", ".");
    runGit(repoDir, "commit", "-m", "initial");
  }

  private static void initTempGitRepo(Path repoDir) throws Exception {
    runGit(repoDir, "init");
    runGit(repoDir, "config", "user.email", "test@example.com");
    runGit(repoDir, "config", "user.name", "Test User");
    Files.writeString(repoDir.resolve("README.md"), "hello", StandardCharsets.UTF_8);
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
