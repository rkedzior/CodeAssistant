package app.platform.delivery.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetadataApiTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void getMetadata_bootstrapsMetadataWhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fileId").value("metadata.json"))
        .andExpect(jsonPath("$.attributes.type").value("documentation"))
        .andExpect(jsonPath("$.attributes.subtype").value("metadata"))
        .andExpect(jsonPath("$.metadata.schemaVersion").value(2))
        .andExpect(jsonPath("$.metadata.indexing.lastIndexedCommit").doesNotExist());
  }
}
