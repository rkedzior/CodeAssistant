package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TextSearchApiTest {
  @Autowired private MockMvc mockMvc;

  @TempDir Path tempDir;

  @Test
  void textSearch_returnsTrackedFile_andExcludesGitignoredFile() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepoWithIgnoredMatch(repoDir);

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString())
                .param("openaiApiKey", "sk-test"))
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(get("/api/search/text").param("query", "MyClass"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.query").value("MyClass"))
        .andExpect(jsonPath("$.files[*].path", hasItem("tracked.txt")))
        .andExpect(jsonPath("$.files[*].path", not(hasItem("ignored.log"))));
  }

  @Test
  void textSearch_regexMode_returnsMatchingLine() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepo(repoDir, "foo    bar\nbaz\n", "foo    bar\nbaz\n");

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString())
                .param("openaiApiKey", "sk-test"))
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(get("/api/search/text").param("query", "foo\\s+bar").param("regex", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.query").value("foo\\s+bar"))
        .andExpect(jsonPath("$.files.length()").value(1))
        .andExpect(jsonPath("$.files[0].path").value("tracked.txt"))
        .andExpect(jsonPath("$.files[0].matches[0].lineNumber").value(1))
        .andExpect(jsonPath("$.files[0].matches[0].lineText", containsString("foo")));
  }

  @Test
  void textSearch_invalidRegex_returnsBadRequest_andNoResults() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);
    initTempGitRepo(repoDir, "foo bar\n", "foo bar\n");

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString())
                .param("openaiApiKey", "sk-test"))
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(get("/api/search/text").param("query", "foo(").param("regex", "true"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.query").value("foo("))
        .andExpect(jsonPath("$.files.length()").value(0))
        .andExpect(jsonPath("$.error").value(startsWith("Invalid regex:")));
  }

  private static void initTempGitRepoWithIgnoredMatch(Path repoDir) throws Exception {
    initTempGitRepo(repoDir, "public class MyClass {}\n", "public class MyClass {}\n");
  }

  private static void initTempGitRepo(Path repoDir, String trackedContent, String ignoredContent)
      throws Exception {
    runGit(repoDir, "init");
    runGit(repoDir, "config", "user.email", "test@example.com");
    runGit(repoDir, "config", "user.name", "Test User");

    Files.writeString(repoDir.resolve(".gitignore"), "ignored.log\n", StandardCharsets.UTF_8);
    Files.writeString(repoDir.resolve("tracked.txt"), trackedContent, StandardCharsets.UTF_8);
    Files.writeString(repoDir.resolve("ignored.log"), ignoredContent, StandardCharsets.UTF_8);

    runGit(repoDir, "add", ".gitignore", "tracked.txt");
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
