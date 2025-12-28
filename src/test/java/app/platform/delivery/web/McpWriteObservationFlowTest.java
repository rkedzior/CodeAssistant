package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class McpWriteObservationFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void mcpWriteObservationApi_postThenObservationsUi_containsText() throws Exception {
    String unique = "BL-1003 mcp-api " + UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/mcp/write_observation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("text", unique, "subtype", "note"))))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.text").value(unique));

    mockMvc
        .perform(get("/observations"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString(unique)));
  }

  @Test
  void mcpStatusWriteObservationAction_postThenObservationsUi_containsText() throws Exception {
    String unique = "BL-1003 mcp-ui " + UUID.randomUUID();

    MockHttpSession session = new MockHttpSession();
    MvcResult postResult =
        mockMvc
            .perform(
                post("/mcp/write-observation")
                    .session(session)
                    .param("text", unique)
                    .param("subtype", "note"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/mcp"))
            .andExpect(flash().attribute("writeObservationSuccessMessage", containsString("write_observation")))
            .andReturn();

    mockMvc
        .perform(
            get(postResult.getResponse().getRedirectedUrl()).session(session))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("Observation saved via write_observation.")));

    mockMvc
        .perform(get("/observations"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString(unique)));
  }
}
