package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigMode;
import app.core.projectconfig.ProjectConfigPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "OPENAI_API_KEY=",
      "openai.api.key=",
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenAIStatusApiTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectConfigPort projectConfigPort;

  @Test
  void getOpenAiStatus_whenApiKeyMissingEverywhere_returnsConfiguredFalseAndMissingSource()
      throws Exception {
    projectConfigPort.save(
        new ProjectConfig(ProjectConfigMode.LOCAL, null, null, null, null, null, null));

    mockMvc
        .perform(get("/api/openai/status"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.configured").value(false))
        .andExpect(jsonPath("$.apiKeySource").value("missing"));
  }

  @Test
  void getOpenAiStatus_whenProjectConfigHasKeyModelVectorStore_returnsConfiguredTrueAndDetails()
      throws Exception {
    projectConfigPort.save(
        new ProjectConfig(
            ProjectConfigMode.LOCAL, "sk-test", null, null, null, "gpt-test-model", "vs-test"));

    mockMvc
        .perform(get("/api/openai/status"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.configured").value(true))
        .andExpect(jsonPath("$.apiKeySource").value("projectConfig"))
        .andExpect(jsonPath("$.model").value("gpt-test-model"))
        .andExpect(jsonPath("$.vectorStoreId").value("vs-test"));
  }

  @Test
  void getOpenAiStatus_neverReturnsRawApiKeyString() throws Exception {
    projectConfigPort.save(
        new ProjectConfig(
            ProjectConfigMode.LOCAL, "sk-test", null, null, null, "gpt-test-model", "vs-test"));

    mockMvc
        .perform(get("/api/openai/status"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("sk-test"))));
  }
}

