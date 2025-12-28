package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigMode;
import app.core.projectconfig.ProjectConfigPort;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
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
class SpecBrowsingTest {
  private static final String SPEC_A_PATH = "spec/a.md";
  private static final String SPEC_B_PATH = "spec/b.md";
  private static final String SPEC_A_CONTENT = "spec-a: known content";
  private static final String SPEC_B_CONTENT = "spec-b: known content";

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectConfigPort projectConfigPort;

  @TempDir Path tempDir;

  @BeforeEach
  void setUpRepo() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir.resolve("spec"));

    Files.writeString(repoDir.resolve(SPEC_A_PATH), SPEC_A_CONTENT, StandardCharsets.UTF_8);
    Files.writeString(repoDir.resolve(SPEC_B_PATH), SPEC_B_CONTENT, StandardCharsets.UTF_8);
    Files.writeString(repoDir.resolve("not-spec.txt"), "ignore me", StandardCharsets.UTF_8);

    projectConfigPort.save(new ProjectConfig(ProjectConfigMode.LOCAL, null, repoDir.toString(), null, null));
  }

  @Test
  void listSpecFiles_returnsOnlyMarkdownUnderSpecFolder() throws Exception {
    mockMvc
        .perform(get("/api/spec/files"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasItems(SPEC_A_PATH, SPEC_B_PATH)))
        .andExpect(jsonPath("$", not(hasItem("not-spec.txt"))));
  }

  @Test
  void readSpecFile_returnsExpectedContent() throws Exception {
    mockMvc
        .perform(get("/api/spec/file").param("path", SPEC_A_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.path").value(SPEC_A_PATH))
        .andExpect(jsonPath("$.content").value(containsString(SPEC_A_CONTENT)));
  }

  @Test
  void readSpecFile_rejectsPathTraversal() throws Exception {
    mockMvc.perform(get("/api/spec/file").param("path", "../pom.xml")).andExpect(status().isBadRequest());
  }

  @Test
  void specPage_rendersFileContent() throws Exception {
    mockMvc
        .perform(get("/spec").param("path", SPEC_A_PATH))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString(SPEC_A_CONTENT)));
  }
}

