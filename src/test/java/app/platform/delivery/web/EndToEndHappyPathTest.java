package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.core.analysis.AnalysisRequest;
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
class EndToEndHappyPathTest {
  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(10);
  private static final String PAYMENT_SERVICE_PATH = "src/main/java/app/core/PaymentService.java";
  private static final String SPEC_PATH = "spec/US_9997_Payment.md";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @TempDir Path tempDir;

  @Test
  void endToEndHappyPath_configureIndexSearchAnalyzeObserveAndUpdateSpec() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepoWithPaymentServiceAndSpec(repoDir);

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString()))
        .andExpect(status().is3xxRedirection());

    mockMvc.perform(post("/api/index/initial")).andExpect(status().isAccepted());
    IndexJobState finalState = pollUntilFinished();
    assertTrue(
        finalState.status() == IndexJobStatus.SUCCESS,
        "Expected SUCCESS, got " + finalState.status() + " (" + finalState.error() + ")");

    mockMvc
        .perform(get("/api/search/text").param("query", "PaymentService").param("regex", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.files[*].path", hasItem(PAYMENT_SERVICE_PATH)));

    mockMvc
        .perform(
            post("/api/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new AnalysisRequest("Explain payment capture flow", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer", not(emptyOrNullString())))
        .andExpect(jsonPath("$.retrievedContext.length()").value(greaterThan(0)));

    String marker = "US-1101 marker: " + UUID.randomUUID();
    mockMvc
        .perform(
            post("/api/observations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("text", marker, "subtype", "note"))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(post("/spec/propose-updates"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString(marker)));

    mockMvc
        .perform(post("/spec/apply-updates").param("paths", SPEC_PATH).param("path", SPEC_PATH))
        .andExpect(status().is3xxRedirection());

    String updated = Files.readString(repoDir.resolve(SPEC_PATH), StandardCharsets.UTF_8);
    assertTrue(updated.contains(marker), "Expected spec file to contain marker after apply.");
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

  private static void initTempGitRepoWithPaymentServiceAndSpec(Path repoDir) throws Exception {
    String token = "US1101_TOKEN_" + UUID.randomUUID();

    runGit(repoDir, "init");
    runGit(repoDir, "config", "user.email", "test@example.com");
    runGit(repoDir, "config", "user.name", "Test User");

    Files.createDirectories(repoDir.resolve("src/main/java/app/core"));
    Files.writeString(
        repoDir.resolve(PAYMENT_SERVICE_PATH),
        String.join(
            "\n",
            "package app.core;",
            "",
            "public class PaymentService {",
            "  // " + token,
            "  public void capturePayment() {",
            "    // capture flow",
            "  }",
            "}",
            ""),
        StandardCharsets.UTF_8);

    Files.createDirectories(repoDir.resolve("spec"));
    Files.writeString(
        repoDir.resolve(SPEC_PATH),
        String.join(
            "\n",
            "# US_9997 Payment",
            "",
            "Initial spec content for payment capture flow.",
            ""),
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
