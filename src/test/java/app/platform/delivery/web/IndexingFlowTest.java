package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
class IndexingFlowTest {
  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @TempDir Path tempDir;

  @Test
  void initialIndexJob_updatesProgress_andWritesLastIndexedCommit() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepo(repoDir);
    String expectedHead = runGit(repoDir, "rev-parse", "HEAD").trim();

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString()))
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(post("/api/index/initial"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("RUNNING"))
        .andExpect(jsonPath("$.progress").exists());

    Set<String> observedProgressWhileRunning = new HashSet<>();
    IndexJobState finalState = pollUntilFinished(observedProgressWhileRunning);

    assertTrue(
        observedProgressWhileRunning.size() >= 2,
        "Expected progress to change while RUNNING, got: " + observedProgressWhileRunning);
    assertTrue(finalState.status() == IndexJobStatus.SUCCESS, "Expected SUCCESS, got " + finalState);
    assertTrue(finalState.ingestion() != null, "Expected ingestion status to be present.");
    assertTrue(
        finalState.ingestion().uploaded() > 0, "Expected ingestion uploaded count to be > 0.");
    assertTrue(
        finalState.ingestion().processing() == 0,
        "Expected ingestion processing to be 0, got " + finalState.ingestion().processing());
    assertTrue(
        finalState.ingestion().failed() == 0,
        "Expected ingestion failed to be 0, got " + finalState.ingestion().failed());

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString(expectedHead)))
        .andExpect(content().string(not(containsString("Not indexed"))));
  }

  @Test
  void initialIndexJob_uploadsCodeAndSpecFilesToVectorStore_withExpectedAttributes() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepoWithCodeAndSpec(repoDir);
    String expectedHead = runGit(repoDir, "rev-parse", "HEAD").trim();

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString()))
        .andExpect(status().is3xxRedirection());

    mockMvc.perform(post("/api/index/initial")).andExpect(status().isAccepted());
    pollUntilFinished(new HashSet<>());

    mockMvc
        .perform(get("/api/vectorstore/files"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$..attributes.path", hasItem("src/main/java/app/core/Foo.java")))
        .andExpect(jsonPath("$..attributes.type", hasItem("code")))
        .andExpect(jsonPath("$..attributes.subtype", hasItem("business_logic")))
        .andExpect(jsonPath("$..attributes.path", hasItem("spec/US_9999_Test.md")))
        .andExpect(jsonPath("$..attributes.type", hasItem("documentation")))
        .andExpect(jsonPath("$..attributes.subtype", hasItem("spec")));

    mockMvc
        .perform(get("/api/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.indexing.lastIndexedCommit").value(expectedHead));
  }

  private IndexJobState pollUntilFinished(Set<String> observedProgressWhileRunning) throws Exception {
    long deadlineNanos = System.nanoTime() + POLL_TIMEOUT.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      MvcResult result = mockMvc.perform(get("/api/index/status")).andReturn();
      IndexJobState state = objectMapper.readValue(result.getResponse().getContentAsByteArray(), IndexJobState.class);

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
    Files.writeString(repoDir.resolve("README.md"), "hello", StandardCharsets.UTF_8);
    runGit(repoDir, "add", ".");
    runGit(repoDir, "commit", "-m", "initial");
  }

  private static void initTempGitRepoWithCodeAndSpec(Path repoDir) throws Exception {
    runGit(repoDir, "init");
    runGit(repoDir, "config", "user.email", "test@example.com");
    runGit(repoDir, "config", "user.name", "Test User");

    Files.createDirectories(repoDir.resolve("src/main/java/app/core"));
    Files.writeString(
        repoDir.resolve("src/main/java/app/core/Foo.java"),
        "package app.core;\n\npublic class Foo {}\n",
        StandardCharsets.UTF_8);

    Files.createDirectories(repoDir.resolve("spec"));
    Files.writeString(repoDir.resolve("spec/US_9999_Test.md"), "# Spec\n", StandardCharsets.UTF_8);

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
