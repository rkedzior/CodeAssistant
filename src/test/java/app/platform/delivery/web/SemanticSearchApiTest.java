package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import java.util.HashSet;
import java.util.Set;
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
class SemanticSearchApiTest {
  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @TempDir Path tempDir;

  @Test
  void semanticSearch_returnsResults_andRanksMoreRelevantFileFirst() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepoWithTwoSemanticFiles(repoDir);

    configureLocalRepo(repoDir);
    startIndexingAndWaitUntilFinished();

    String query = "authorization token refresh session";
    mockMvc
        .perform(get("/api/search/semantic").param("query", query).param("k", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.query").value(query))
        .andExpect(jsonPath("$.results[0].path").value("src/main/java/app/core/SemanticAlpha.java"))
        .andExpect(jsonPath("$.results[1].path").value("src/main/java/app/core/SemanticBeta.java"));
  }

  @Test
  void semanticSearchHtml_rendersResults_withExpectedPath() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepoWithTwoSemanticFiles(repoDir);

    configureLocalRepo(repoDir);
    startIndexingAndWaitUntilFinished();

    String query = "authorization token refresh session";
    mockMvc
        .perform(get("/search").param("mode", "semantic").param("query", query).param("k", "10"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("src/main/java/app/core/SemanticAlpha.java")));
  }

  @Test
  void semanticSearch_filtersByTypeAndSubtype() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepoWithSpecAndNoiseFiles(repoDir);

    configureLocalRepo(repoDir);
    startIndexingAndWaitUntilFinished();

    String query = "non functional requirements";
    mockMvc
        .perform(
            get("/api/search/semantic")
                .param("query", query)
                .param("type", "documentation")
                .param("subtype", "spec")
                .param("k", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(1))
        .andExpect(jsonPath("$.results[0].path").value("spec/US_9998_NFR.md"));
  }

  @Test
  void semanticSearchHtml_filtersByTypeAndSubtype() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepoWithSpecAndNoiseFiles(repoDir);

    configureLocalRepo(repoDir);
    startIndexingAndWaitUntilFinished();

    String query = "non functional requirements";
    mockMvc
        .perform(
            get("/search")
                .param("mode", "semantic")
                .param("type", "documentation")
                .param("subtype", "spec")
                .param("query", query)
                .param("k", "10"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("spec/US_9998_NFR.md")))
        .andExpect(content().string(not(containsString("Noise.java"))));
  }

  private void configureLocalRepo(Path repoDir) throws Exception {
    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString()))
        .andExpect(status().is3xxRedirection());
  }

  private void startIndexingAndWaitUntilFinished() throws Exception {
    mockMvc.perform(post("/api/index/initial")).andExpect(status().isAccepted());
    IndexJobState finalState = pollUntilFinished(new HashSet<>());
    assertTrue(
        finalState.status() == IndexJobStatus.SUCCESS,
        "Expected SUCCESS, got " + finalState.status() + " (" + finalState.error() + ")");
  }

  private IndexJobState pollUntilFinished(Set<String> observedProgressWhileRunning) throws Exception {
    long deadlineNanos = System.nanoTime() + POLL_TIMEOUT.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      MvcResult result = mockMvc.perform(get("/api/index/status")).andReturn();
      IndexJobState state =
          objectMapper.readValue(result.getResponse().getContentAsByteArray(), IndexJobState.class);

      if (state.status() == IndexJobStatus.RUNNING && state.progress() != null) {
        observedProgressWhileRunning.add(state.progress());
      }

      if (state.status() == IndexJobStatus.SUCCESS || state.status() == IndexJobStatus.FAILED) {
        return state;
      }

      Thread.sleep(100);
    }

    throw new AssertionError("Timed out while waiting for indexing job to finish");
  }

  private static void initTempGitRepo(Path repoDir) throws Exception {
    runGit(repoDir, "init");
    runGit(repoDir, "config", "user.email", "test@example.com");
    runGit(repoDir, "config", "user.name", "Test User");
  }

  private static void initTempGitRepoWithTwoSemanticFiles(Path repoDir) throws Exception {
    initTempGitRepo(repoDir);

    Files.createDirectories(repoDir.resolve("src/main/java/app/core"));
    Files.writeString(
        repoDir.resolve("src/main/java/app/core/SemanticAlpha.java"),
        String.join(
            "\n",
            "package app.core;",
            "",
            "public class SemanticAlpha {",
            "  // authorization token refresh session",
            "  // authorization token refresh session",
            "  // authorization token refresh session",
            "  // authorization token refresh session",
            "  // authorization token refresh session",
            "}"),
        StandardCharsets.UTF_8);

    Files.writeString(
        repoDir.resolve("src/main/java/app/core/SemanticBeta.java"),
        String.join(
            "\n",
            "package app.core;",
            "",
            "public class SemanticBeta {",
            "  // token",
            "  // banana carrot dolphin elephant fig grape hotel igloo jacket kettle lemon mango",
            "  // nebula oxygen proton quantum rocket satellite telescope",
            "}"),
        StandardCharsets.UTF_8);

    runGit(repoDir, "add", ".");
    runGit(repoDir, "commit", "-m", "initial");
  }

  private static void initTempGitRepoWithSpecAndNoiseFiles(Path repoDir) throws Exception {
    initTempGitRepo(repoDir);

    Files.createDirectories(repoDir.resolve("spec"));
    Files.writeString(
        repoDir.resolve("spec/US_9998_NFR.md"),
        String.join("\n", "# US_9998", "", "Non-functional requirements: latency"),
        StandardCharsets.UTF_8);

    Files.createDirectories(repoDir.resolve("src/main/java/app/core"));
    Files.writeString(
        repoDir.resolve("src/main/java/app/core/Noise.java"),
        String.join(
            "\n",
            "package app.core;",
            "",
            "public class Noise {",
            "  // unrelated content",
            "  // banana carrot dolphin elephant fig grape hotel igloo jacket kettle lemon mango",
            "}"),
        StandardCharsets.UTF_8);

    runGit(repoDir, "add", ".");
    runGit(repoDir, "commit", "-m", "initial");
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
