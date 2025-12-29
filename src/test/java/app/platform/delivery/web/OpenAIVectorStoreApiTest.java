package app.platform.delivery.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openai.client.OpenAIClient;
import com.openai.core.AutoPager;
import com.openai.core.JsonString;
import com.openai.models.vectorstores.VectorStore;
import com.openai.models.vectorstores.VectorStoreCreateParams;
import com.openai.models.vectorstores.VectorStoreRetrieveParams;
import com.openai.models.vectorstores.files.FileListPage;
import com.openai.models.vectorstores.files.FileListParams;
import com.openai.models.vectorstores.files.VectorStoreFile;
import com.openai.services.blocking.VectorStoreService;
import com.openai.services.blocking.vectorstores.FileService;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenAIVectorStoreApiTest {
  @Autowired private MockMvc mockMvc;

  @MockBean private OpenAIClient client;
  @MockBean private VectorStoreService vectorStoreService;
  @MockBean private FileService vectorStoreFileService;

  @BeforeEach
  void setUp() {
    when(client.vectorStores()).thenReturn(vectorStoreService);
    when(vectorStoreService.files()).thenReturn(vectorStoreFileService);
  }

  @Test
  void createVectorStore_returnsVectorStoreId() throws Exception {
    VectorStore vectorStore = vectorStore("vs_123", "My Store", 123L);
    when(vectorStoreService.create(any(VectorStoreCreateParams.class))).thenReturn(vectorStore);

    mockMvc
        .perform(
            post("/api/openai/vectorstore")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"My Store\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vectorStoreId").value("vs_123"));
  }

  @Test
  void validateVectorStore_returnsDetails() throws Exception {
    VectorStore vectorStore = vectorStore("vs_123", "My Store", 123L);
    when(vectorStoreService.retrieve(any(VectorStoreRetrieveParams.class))).thenReturn(vectorStore);

    FileListPage page = mock(FileListPage.class);
    @SuppressWarnings("unchecked")
    AutoPager<VectorStoreFile> autoPager = mock(AutoPager.class);
    when(vectorStoreFileService.list(any(FileListParams.class))).thenReturn(page);
    when(page.autoPager()).thenReturn(autoPager);
    when(autoPager.stream())
        .thenReturn(Stream.of(vectorStoreFile("file_1", "vs_123"), vectorStoreFile("file_2", "vs_123")));

    mockMvc
        .perform(get("/api/openai/vectorstore/vs_123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exists").value(true))
        .andExpect(jsonPath("$.name").value("My Store"))
        .andExpect(jsonPath("$.fileCount").value(2))
        .andExpect(jsonPath("$.createdAt").value(123));
  }

  private static VectorStoreFile vectorStoreFile(String id, String vectorStoreId) {
    return VectorStoreFile.builder()
        .id(id)
        .createdAt(0L)
        .lastError(Optional.empty())
        .status(VectorStoreFile.Status.COMPLETED)
        .usageBytes(0L)
        .vectorStoreId(vectorStoreId)
        .object_(JsonString.of("vector_store.file"))
        .build();
  }

  private static VectorStore vectorStore(String id, String name, long createdAt) {
    return VectorStore.builder()
        .id(id)
        .name(name)
        .createdAt(createdAt)
        .lastActiveAt(createdAt)
        .metadata(VectorStore.Metadata.builder().build())
        .status(VectorStore.Status.COMPLETED)
        .usageBytes(0L)
        .fileCounts(
            VectorStore.FileCounts.builder()
                .total(0L)
                .completed(0L)
                .failed(0L)
                .inProgress(0L)
                .cancelled(0L)
                .build())
        .build();
  }
}
