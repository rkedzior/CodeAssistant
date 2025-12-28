package app.platform.delivery.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.core.observations.Observation;
import app.core.observations.ObservationSubtype;
import app.core.vectorstore.VectorStoreFileSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
class ObservationsFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void observationsApi_createThenList_returnsSavedObservation() throws Exception {
    mockMvc
        .perform(
            post("/api/observations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        Map.of("text", "refactor this", "subtype", "note"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.text").value("refactor this"))
        .andExpect(jsonPath("$.subtype").value("note"))
        .andExpect(jsonPath("$.id").value(startsWith("obs_")));

    MvcResult listResult = mockMvc.perform(get("/api/observations")).andExpect(status().isOk()).andReturn();
    Observation[] observations =
        objectMapper.readValue(listResult.getResponse().getContentAsByteArray(), Observation[].class);
    assertTrue(
        Arrays.stream(observations)
            .anyMatch(obs -> "refactor this".equals(obs.text()) && obs.subtype() == ObservationSubtype.NOTE));

    MvcResult vectorFilesResult =
        mockMvc.perform(get("/api/vectorstore/files")).andExpect(status().isOk()).andReturn();
    VectorStoreFileSummary[] summaries =
        objectMapper.readValue(
            vectorFilesResult.getResponse().getContentAsByteArray(), VectorStoreFileSummary[].class);
    assertTrue(
        Arrays.stream(summaries)
            .anyMatch(
                summary ->
                    "observation".equals(summary.attributes().get("type"))
                        && "note".equals(summary.attributes().get("subtype"))));
  }

  @Test
  void observationsUi_postThenGet_containsSubmittedText() throws Exception {
    mockMvc
        .perform(post("/observations").param("text", "refactor this").param("subtype", "note"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/observations"));

    mockMvc
        .perform(get("/observations"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("refactor this")));
  }
}
