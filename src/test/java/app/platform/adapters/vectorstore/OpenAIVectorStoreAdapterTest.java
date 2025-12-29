package app.platform.adapters.vectorstore;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import app.core.vectorstore.VectorStoreFile;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonObject;
import com.openai.core.JsonString;
import com.openai.core.JsonValue;
import com.openai.core.http.HttpResponse;
import com.openai.models.BetaVectorStoreFileCreateParams;
import com.openai.models.BetaVectorStoreFileDeleteParams;
import com.openai.models.BetaVectorStoreFileListPage;
import com.openai.models.BetaVectorStoreFileListParams;
import com.openai.models.BetaVectorStoreFileRetrieveParams;
import com.openai.models.FileContentParams;
import com.openai.models.FileCreateParams;
import com.openai.models.FileDeleteParams;
import com.openai.models.FileObject;
import com.openai.services.blocking.BetaService;
import com.openai.services.blocking.FileService;
import com.openai.services.blocking.beta.VectorStoreService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAIVectorStoreAdapterTest {
  private static final String VECTOR_STORE_ID = "vs_test";

  @Mock private OpenAIClient client;
  @Mock private FileService fileService;
  @Mock private BetaService betaService;
  @Mock private VectorStoreService vectorStoreService;
  @Mock private com.openai.services.blocking.beta.vectorStores.FileService vectorStoreFileService;
  @Mock private HttpResponse httpResponse;

  private OpenAIVectorStoreAdapter adapter;

  private static FileObject fileObject(String id) {
    return FileObject.builder()
        .id(id)
        .bytes(123L)
        .createdAt(0L)
        .filename("test.txt")
        .purpose(FileObject.Purpose.ASSISTANTS)
        .status(FileObject.Status.PROCESSED)
        .object_(JsonString.of("file"))
        .build();
  }

  private static com.openai.models.VectorStoreFile vectorStoreFile(
      String id, Map<String, String> attributes) {
    com.openai.models.VectorStoreFile.Builder builder =
        com.openai.models.VectorStoreFile.builder()
            .id(id)
            .createdAt(0L)
            .lastError(java.util.Optional.empty())
            .status(com.openai.models.VectorStoreFile.Status.COMPLETED)
            .usageBytes(0L)
            .vectorStoreId(VECTOR_STORE_ID)
            .object_(JsonString.of("vector_store.file"));

    if (attributes != null && !attributes.isEmpty()) {
      Map<String, JsonValue> jsonAttributes =
          attributes.entrySet().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      Map.Entry::getKey, e -> JsonString.of(e.getValue())));
      builder.putAdditionalProperty("attributes", JsonObject.of(jsonAttributes));
    }

    return builder.build();
  }

  @BeforeEach
  void setUp() {
    doReturn(fileService).when(client).files();
    doReturn(betaService).when(client).beta();
    doReturn(vectorStoreService).when(betaService).vectorStores();
    doReturn(vectorStoreFileService).when(vectorStoreService).files();

    adapter = new OpenAIVectorStoreAdapter(client, VECTOR_STORE_ID);
  }

  @Test
  void createFile_uploadsFileAndAttachesToVectorStore_includingAttributesJson() {
    FileObject uploaded = fileObject("file_123");
    doReturn(uploaded).when(fileService).create(any(FileCreateParams.class));

    ArgumentCaptor<BetaVectorStoreFileCreateParams> attachCaptor =
        ArgumentCaptor.forClass(BetaVectorStoreFileCreateParams.class);
    doReturn(vectorStoreFile("vsf_1", Map.of()))
        .when(vectorStoreFileService)
        .create(attachCaptor.capture());

    Map<String, String> attributes = Map.of("source", "unit-test", "lang", "java");
    String fileId =
        adapter.createFile("test.txt", "hello".getBytes(StandardCharsets.UTF_8), attributes);

    assertEquals("file_123", fileId);

    BetaVectorStoreFileCreateParams attachParams = attachCaptor.getValue();
    assertEquals(VECTOR_STORE_ID, attachParams.vectorStoreId());
    assertEquals("file_123", attachParams.fileId());

    JsonValue attributesValue = attachParams._additionalBodyProperties().get("attributes");
    JsonObject attributesJson = assertInstanceOf(JsonObject.class, attributesValue);
    assertEquals(
        JsonObject.of(
            Map.of("source", JsonString.of("unit-test"), "lang", JsonString.of("java"))),
        attributesJson);
  }

  @Test
  void createFile_whenAttributesContainPath_deletesExistingWithSamePathFirst() {
    String path = "src/main/java/app/Example.java";
    com.openai.models.VectorStoreFile existing = vectorStoreFile("file_old", Map.of("path", path));

    BetaVectorStoreFileListParams listParams =
        BetaVectorStoreFileListParams.builder().vectorStoreId(VECTOR_STORE_ID).limit(100L).build();
    BetaVectorStoreFileListPage.Response response =
        BetaVectorStoreFileListPage.Response.builder().data(List.of(existing)).hasMore(false).build();
    BetaVectorStoreFileListPage page = BetaVectorStoreFileListPage.of(vectorStoreFileService, listParams, response);

    doReturn(page).when(vectorStoreFileService).list(any(BetaVectorStoreFileListParams.class));

    doReturn(fileObject("file_123"))
        .when(fileService)
        .create(any(FileCreateParams.class));
    doReturn(vectorStoreFile("vsf_new", Map.of()))
        .when(vectorStoreFileService)
        .create(any(BetaVectorStoreFileCreateParams.class));

    adapter.createFile("new.txt", "new".getBytes(StandardCharsets.UTF_8), Map.of("path", path));

    ArgumentCaptor<BetaVectorStoreFileDeleteParams> vsDeleteCaptor =
        ArgumentCaptor.forClass(BetaVectorStoreFileDeleteParams.class);
    ArgumentCaptor<FileDeleteParams> fileDeleteCaptor = ArgumentCaptor.forClass(FileDeleteParams.class);

    InOrder inOrder = inOrder(vectorStoreFileService, fileService);
    inOrder.verify(vectorStoreFileService).delete(vsDeleteCaptor.capture());
    inOrder.verify(fileService).delete(fileDeleteCaptor.capture());

    assertEquals(VECTOR_STORE_ID, vsDeleteCaptor.getValue().vectorStoreId());
    assertEquals("file_old", vsDeleteCaptor.getValue().fileId());
    assertEquals("file_old", fileDeleteCaptor.getValue().fileId());

    verify(vectorStoreFileService, times(1)).delete(any(BetaVectorStoreFileDeleteParams.class));
    verify(fileService, times(1)).delete(any(FileDeleteParams.class));
  }

  @Test
  void readFile_downloadsContentBytesAndParsesAttributes() {
    String fileId = "file_123";
    byte[] content = new byte[] {1, 2, 3, 4};

    com.openai.models.VectorStoreFile retrieved =
        vectorStoreFile(fileId, Map.of("path", "docs/readme.md", "source", "unit-test"));
    doReturn(retrieved)
        .when(vectorStoreFileService)
        .retrieve(any(BetaVectorStoreFileRetrieveParams.class));

    doReturn(200).when(httpResponse).statusCode();
    doReturn(new ByteArrayInputStream(content)).when(httpResponse).body();
    doReturn(httpResponse).when(fileService).content(any(FileContentParams.class));

    VectorStoreFile result = adapter.readFile(fileId);

    assertEquals(fileId, result.fileId());
    assertArrayEquals(content, result.content());
    assertEquals(Map.of("path", "docs/readme.md", "source", "unit-test"), result.attributes());

    ArgumentCaptor<FileContentParams> contentParamsCaptor = ArgumentCaptor.forClass(FileContentParams.class);
    verify(fileService).content(contentParamsCaptor.capture());
    assertEquals(fileId, contentParamsCaptor.getValue().fileId());
  }
}
