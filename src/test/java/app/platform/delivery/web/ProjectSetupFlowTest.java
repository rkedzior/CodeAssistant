package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectSetupFlowTest {
  @Autowired private MockMvc mockMvc;

  @TempDir Path tempDir;

  @Test
  void localSetup_withModelAndVectorStore_persistsAndShowsOnDashboardAndSetup() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir.resolve(".git"));

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString())
                .param("openaiApiKey", "sk-test")
                .param("openaiModel", "gpt-test-model")
                .param("openaiVectorStoreId", "vs_test12345"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("gpt-test-model")))
        .andExpect(content().string(containsString("vs_test12345")));

    mockMvc
        .perform(get("/setup"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))    
        .andExpect(content().string(containsString("gpt-test-model")))
        .andExpect(content().string(containsString("vs_test12345")));

    mockMvc
        .perform(get("/api/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.openai.vectorStoreId").value("vs_test12345"));
  }

  @Test
  void setupPage_showsVectorStoreActions() throws Exception {
    mockMvc
        .perform(get("/setup"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("Create new vector store")))
        .andExpect(content().string(containsString("Validate")));
  }

  @Test
  void localSetup_invalidVectorStoreId_showsValidationError_andDoesNotConfigure() throws Exception {
    Path repoDir = tempDir.resolve("repo-invalid-vs");
    Files.createDirectories(repoDir.resolve(".git"));

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString())
                .param("openaiApiKey", "sk-test")
                .param("openaiVectorStoreId", "not-a-vs-id"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("Vector store id must match vs_...")));

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Not configured")));
  }

  @Test
  void localSetup_happyPath_marksConfiguredOnDashboard() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir.resolve(".git"));

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString())
                .param("openaiApiKey", "sk-test"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("Configured")));

    mockMvc
        .perform(get("/index"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  void localSetup_withoutModelOrVectorStore_defaultsOnDashboard() throws Exception {
    Path repoDir = tempDir.resolve("repo-defaults");
    Files.createDirectories(repoDir.resolve(".git"));

    mockMvc
        .perform(
            post("/setup")
                .param("mode", "LOCAL")
                .param("localRepoPath", repoDir.toString())
                .param("openaiApiKey", "sk-test")
                .param("openaiVectorStoreId", " "))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("Configured")))
        .andExpect(content().string(containsString("OpenAI model:")))
        .andExpect(content().string(containsString("gpt-4.1-mini")))
        .andExpect(content().string(containsString("Vector store:")))
        .andExpect(content().string(containsString("Not configured")));
  }

  @Test
  void githubSetup_happyPath_marksConfiguredOnDashboard() throws Exception {
    mockMvc
        .perform(
            post("/setup")
                .param("mode", "GITHUB")
                .param("githubRepo", "octo-org/octo-repo")
                .param("githubToken", "ghp_test")
                .param("openaiApiKey", "sk-test"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("Configured")));

    mockMvc
        .perform(get("/index"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  void githubSetup_invalidRepo_showsValidationError_andDoesNotConfigure() throws Exception {
    mockMvc
        .perform(
            post("/setup")
                .param("mode", "GITHUB")
                .param("githubRepo", "invalid repo id")
                .param("githubToken", "ghp_test")
                .param("openaiApiKey", "sk-test"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("GitHub repository must match owner/name.")));

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Not configured")));
  }
}
