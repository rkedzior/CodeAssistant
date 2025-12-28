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
import app.core.vectorstore.VectorStoreFile;
import app.core.vectorstore.VectorStorePort;
import app.platform.adapters.vectorstore.InMemoryVectorStoreAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MetadataResilienceTest {
  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(10);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private VectorStorePort vectorStorePort;

  @TempDir Path tempDir;

  @Test
  void metadataMissingInVectorStore_recreatesMetadata_showsNotIndexed_andReindexSucceeds()
      throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepo(repoDir);
    String expectedHead = runGit(repoDir, "rev-parse", "HEAD").trim();

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString())
                .param("openaiApiKey", "sk-test"))
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(get("/api/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fileId").value("metadata.json"))
        .andExpect(jsonPath("$.metadata.schemaVersion").value(1));

    ConcurrentHashMap<String, VectorStoreFile> files = getInMemoryVectorStoreFiles();
    assertTrue(
        files.containsKey("metadata.json"),
        "Expected metadata.json to exist in vector store before deletion.");

    files.remove("metadata.json");
    assertTrue(
        !files.containsKey("metadata.json"),
        "Expected metadata.json to be removed from vector store.");

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("Not indexed")));

    assertTrue(
        files.containsKey("metadata.json"),
        "Expected metadata.json to be recreated in vector store after rendering dashboard.");

    mockMvc.perform(post("/api/index/initial")).andExpect(status().isAccepted());
    IndexJobState finalState = pollUntilFinished();
    assertTrue(
        finalState.status() == IndexJobStatus.SUCCESS,
        "Expected SUCCESS, got " + finalState.status() + " (" + finalState.error() + ")");

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString(expectedHead)))
        .andExpect(content().string(not(containsString("Not indexed"))));
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

    throw new AssertionError("Timed out while waiting for indexing job to finish.");
  }

  @SuppressWarnings("unchecked")
  private ConcurrentHashMap<String, VectorStoreFile> getInMemoryVectorStoreFiles()
      throws ReflectiveOperationException {
    Object target = AopTestUtils.getTargetObject(vectorStorePort);
    if (!(target instanceof InMemoryVectorStoreAdapter)) {
      throw new AssertionError(
          "Expected VectorStorePort to be InMemoryVectorStoreAdapter in test profile, got: "
              + target.getClass().getName());
    }

    Field field = InMemoryVectorStoreAdapter.class.getDeclaredField("files");
    field.setAccessible(true);
    return (ConcurrentHashMap<String, VectorStoreFile>) field.get(target);
  }

  private static void initTempGitRepo(Path repoDir) throws Exception {
    runGit(repoDir, "init");
    runGit(repoDir, "config", "user.email", "test@example.com");
    runGit(repoDir, "config", "user.name", "Test User");
    Files.writeString(repoDir.resolve("README.md"), "hello", StandardCharsets.UTF_8);
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

