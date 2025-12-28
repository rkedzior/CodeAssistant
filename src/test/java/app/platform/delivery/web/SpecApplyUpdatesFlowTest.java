package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigMode;
import app.core.projectconfig.ProjectConfigPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SpecApplyUpdatesFlowTest {
  private static final String SPEC_A_PATH = "spec/a.md";
  private static final String UPDATES_HEADER = "## Proposed updates from observations";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ProjectConfigPort projectConfigPort;

  @TempDir Path tempDir;

  private Path repoDir;

  @BeforeEach
  void setUpRepo() throws Exception {
    repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir.resolve("spec"));
    Files.writeString(repoDir.resolve(SPEC_A_PATH), "spec-a: base content", StandardCharsets.UTF_8);

    projectConfigPort.save(
        new ProjectConfig(ProjectConfigMode.LOCAL, null, repoDir.toString(), null, null));
  }

  @Test
  void applySpecUpdatesApi_writesUpdatedSpecFileAndReturnsUpdatedPaths() throws Exception {
    String marker = "BL-0803 API unique observation marker: " + UUID.randomUUID();
    createObservation(marker);

    mockMvc
        .perform(
            post("/api/spec/apply-updates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("paths", List.of(SPEC_A_PATH)))))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.updatedPaths", hasSize(1)))
        .andExpect(jsonPath("$.updatedPaths[0]").value(SPEC_A_PATH));

    String updated = Files.readString(repoDir.resolve(SPEC_A_PATH), StandardCharsets.UTF_8);
    assertTrue(updated.contains(UPDATES_HEADER));
    assertTrue(updated.contains(marker));
  }

  @Test
  void applySpecUpdatesUi_showsSuccessMessageAfterApplyingUpdates() throws Exception {
    String marker = "BL-0803 UI unique observation marker: " + UUID.randomUUID();
    createObservation(marker);

    mockMvc.perform(post("/spec/propose-updates").param("path", SPEC_A_PATH)).andExpect(status().isOk());

    MvcResult applyResult =
        mockMvc
            .perform(
                post("/spec/apply-updates").param("paths", SPEC_A_PATH).param("path", SPEC_A_PATH))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/spec?path=" + SPEC_A_PATH))
            .andReturn();

    MockHttpSession session = (MockHttpSession) applyResult.getRequest().getSession();
    String redirectUrl = applyResult.getResponse().getRedirectedUrl();

    mockMvc
        .perform(get(redirectUrl).session(session))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(
            content().string(containsString("Updates applied. Changes are ready to be committed.")));
  }

  private void createObservation(String text) throws Exception {
    mockMvc
        .perform(
            post("/api/observations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("text", text, "subtype", "note"))))
        .andExpect(status().isCreated());
  }
}

