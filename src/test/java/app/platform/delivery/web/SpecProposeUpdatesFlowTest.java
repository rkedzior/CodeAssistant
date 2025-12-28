package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigMode;
import app.core.projectconfig.ProjectConfigPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
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
class SpecProposeUpdatesFlowTest {
  private static final String SPEC_A_PATH = "spec/a.md";
  private static final String SPEC_B_PATH = "spec/b.md";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ProjectConfigPort projectConfigPort;

  @TempDir Path tempDir;

  @BeforeEach
  void setUpRepo() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir.resolve("spec"));

    Files.writeString(repoDir.resolve(SPEC_A_PATH), "spec-a: known content", StandardCharsets.UTF_8);
    Files.writeString(repoDir.resolve(SPEC_B_PATH), "spec-b: known content", StandardCharsets.UTF_8);

    projectConfigPort.save(
        new ProjectConfig(ProjectConfigMode.LOCAL, null, repoDir.toString(), null, null, null, null));
  }

  @Test
  void proposeSpecUpdatesApi_returnsProposalsForAllSpecFiles_includingObservationText()
      throws Exception {
    String marker = "BL-0802 unique observation marker: " + UUID.randomUUID();
    createObservation(marker);

    mockMvc
        .perform(post("/api/spec/propose-updates"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].path").value(SPEC_A_PATH))
        .andExpect(jsonPath("$[1].path").value(SPEC_B_PATH))
        .andExpect(jsonPath("$[0].proposedContent").value(containsString(marker)))
        .andExpect(jsonPath("$[1].proposedContent").value(containsString(marker)));
  }

  @Test
  void proposeSpecUpdatesUi_rendersProposalsContainingFilePathsAndObservationText()
      throws Exception {
    String marker = "BL-0802 unique UI marker: " + UUID.randomUUID();
    createObservation(marker);

    mockMvc
        .perform(post("/spec/propose-updates"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString(SPEC_A_PATH)))
        .andExpect(content().string(containsString(SPEC_B_PATH)))
        .andExpect(content().string(containsString(marker)));
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
