package app.platform.adapters.vectorstore;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import app.core.vectorstore.VectorStoreFile;
import com.openai.client.OpenAIClient;
import com.openai.core.AutoPager;
import com.openai.core.JsonString;
import com.openai.core.JsonValue;
import com.openai.core.http.HttpResponse;
import com.openai.models.files.FileContentParams;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileDeleteParams;
import com.openai.models.files.FileObject;
import com.openai.models.vectorstores.files.FileListPage;
import com.openai.models.vectorstores.files.FileListParams;
import com.openai.models.vectorstores.files.FileRetrieveParams;
import com.openai.services.blocking.FileService;
import com.openai.services.blocking.VectorStoreService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
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
  @Mock private VectorStoreService vectorStoreService;
  @Mock private com.openai.services.blocking.vectorstores.FileService vectorStoreFileService;
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

  private static com.openai.models.vectorstores.files.VectorStoreFile vectorStoreFile(
      String id, Map<String, String> attributes) {
    com.openai.models.vectorstores.files.VectorStoreFile.Builder builder =
        com.openai.models.vectorstores.files.VectorStoreFile.builder()
            .id(id)
            .createdAt(0L)
            .lastError(Optional.empty())
            .status(com.openai.models.vectorstores.files.VectorStoreFile.Status.COMPLETED)
            .usageBytes(0L)
            .vectorStoreId(VECTOR_STORE_ID)
            .object_(JsonString.of("vector_store.file"));

    if (attributes != null && !attributes.isEmpty()) {
      com.openai.models.vectorstores.files.VectorStoreFile.Attributes.Builder attributesBuilder =
          com.openai.models.vectorstores.files.VectorStoreFile.Attributes.builder();
      for (Map.Entry<String, String> entry : attributes.entrySet()) {
        attributesBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
      }
      builder.attributes(attributesBuilder.build());
    }

    return builder.build();
  }

  @BeforeEach
  void setUp() {
    doReturn(fileService).when(client).files();
    doReturn(vectorStoreService).when(client).vectorStores();
    doReturn(vectorStoreFileService).when(vectorStoreService).files();

    adapter = new OpenAIVectorStoreAdapter(client, VECTOR_STORE_ID);
  }

  @Test
  void createFile_uploadsFileAndAttachesToVectorStore_includingAttributesJson() {
    FileObject uploaded = fileObject("file_123");
    doReturn(uploaded).when(fileService).create(any(FileCreateParams.class));

    ArgumentCaptor<com.openai.models.vectorstores.files.FileCreateParams> attachCaptor =
        ArgumentCaptor.forClass(com.openai.models.vectorstores.files.FileCreateParams.class);
    doReturn(vectorStoreFile("vsf_1", Map.of())).when(vectorStoreFileService).create(attachCaptor.capture());

    Map<String, String> attributes = Map.of("source", "unit-test", "lang", "java");
    String fileId =
        adapter.createFile("test.txt", "hello".getBytes(StandardCharsets.UTF_8), attributes);

    assertEquals("file_123", fileId);

    com.openai.models.vectorstores.files.FileCreateParams attachParams = attachCaptor.getValue();
    assertEquals(VECTOR_STORE_ID, attachParams.vectorStoreId().orElseThrow());
    assertEquals("file_123", attachParams.fileId());

    com.openai.models.vectorstores.files.FileCreateParams.Attributes attributesParams =
        attachParams.attributes().orElseThrow();
    assertEquals(
        Map.of("source", JsonValue.from("unit-test"), "lang", JsonValue.from("java")),
        attributesParams._additionalProperties());
  }

  @Test
  void createFile_whenAttributesContainPath_deletesExistingWithSamePathFirst() {
    String path = "src/main/java/app/Example.java";
    com.openai.models.vectorstores.files.VectorStoreFile existing =
        vectorStoreFile("file_old", Map.of("path", path));

    FileListPage page = org.mockito.Mockito.mock(FileListPage.class);
    @SuppressWarnings("unchecked")
    AutoPager<com.openai.models.vectorstores.files.VectorStoreFile> autoPager =
        org.mockito.Mockito.mock(AutoPager.class);
    doReturn(page).when(vectorStoreFileService).list(any(FileListParams.class));
    doReturn(autoPager).when(page).autoPager();
    doReturn(Stream.of(existing)).when(autoPager).stream();

    doReturn(fileObject("file_123")).when(fileService).create(any(FileCreateParams.class));
    doReturn(vectorStoreFile("vsf_new", Map.of()))
        .when(vectorStoreFileService)
        .create(any(com.openai.models.vectorstores.files.FileCreateParams.class));

    adapter.createFile("new.txt", "new".getBytes(StandardCharsets.UTF_8), Map.of("path", path));

    ArgumentCaptor<com.openai.models.vectorstores.files.FileDeleteParams> vsDeleteCaptor =
        ArgumentCaptor.forClass(com.openai.models.vectorstores.files.FileDeleteParams.class);
    ArgumentCaptor<FileDeleteParams> fileDeleteCaptor = ArgumentCaptor.forClass(FileDeleteParams.class);

    InOrder inOrder = inOrder(vectorStoreFileService, fileService);
    inOrder.verify(vectorStoreFileService).list(any(FileListParams.class));
    inOrder.verify(vectorStoreFileService).delete(vsDeleteCaptor.capture());
    inOrder.verify(fileService).delete(fileDeleteCaptor.capture());
    inOrder.verify(fileService).create(any(FileCreateParams.class));

    assertEquals(VECTOR_STORE_ID, vsDeleteCaptor.getValue().vectorStoreId());
    assertEquals("file_old", vsDeleteCaptor.getValue().fileId().orElseThrow());
    assertEquals("file_old", fileDeleteCaptor.getValue().fileId().orElseThrow());       

    verify(vectorStoreFileService, times(1))
        .delete(any(com.openai.models.vectorstores.files.FileDeleteParams.class));
    verify(fileService, times(1)).delete(any(FileDeleteParams.class));
  }

  @Test
  void readFile_downloadsContentBytesAndParsesAttributes() {
    String fileId = "file_123";
    byte[] content = new byte[] {1, 2, 3, 4};

    com.openai.models.vectorstores.files.VectorStoreFile retrieved =
        vectorStoreFile(fileId, Map.of("path", "docs/readme.md", "source", "unit-test"));
    doReturn(retrieved).when(vectorStoreFileService).retrieve(any(FileRetrieveParams.class));

    doReturn(200).when(httpResponse).statusCode();
    doReturn(new ByteArrayInputStream(content)).when(httpResponse).body();
    doReturn(httpResponse).when(fileService).content(any(FileContentParams.class));

    VectorStoreFile result = adapter.readFile(fileId);

    assertEquals(fileId, result.fileId());
    assertArrayEquals(content, result.content());
    assertEquals(Map.of("path", "docs/readme.md", "source", "unit-test"), result.attributes());

    ArgumentCaptor<FileContentParams> contentParamsCaptor =
        ArgumentCaptor.forClass(FileContentParams.class);
    verify(fileService).content(contentParamsCaptor.capture());
    assertEquals(fileId, contentParamsCaptor.getValue().fileId().orElseThrow());
  }
}
