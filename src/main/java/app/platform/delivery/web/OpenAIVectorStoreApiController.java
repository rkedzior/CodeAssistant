package app.platform.delivery.web;

import app.platform.openai.OpenAISettingsResolver;
import com.openai.client.OpenAIClient;
import com.openai.errors.NotFoundException;
import com.openai.models.vectorstores.VectorStore;
import com.openai.models.vectorstores.VectorStoreCreateParams;
import com.openai.models.vectorstores.VectorStoreRetrieveParams;
import com.openai.models.vectorstores.files.FileListPage;
import com.openai.models.vectorstores.files.FileListParams;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/openai/vectorstore", produces = MediaType.APPLICATION_JSON_VALUE)
public class OpenAIVectorStoreApiController {
  private final ObjectProvider<OpenAIClient> clientProvider;

  public OpenAIVectorStoreApiController(ObjectProvider<OpenAIClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> createVectorStore(@RequestBody(required = false) CreateVectorStoreRequest request) {
    OpenAIClient client = clientProvider.getIfAvailable();
    if (client == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new ErrorResponse("OpenAI is not configured. Set OPENAI_API_KEY and restart."));
    }

    String name = OpenAISettingsResolver.normalizeOptional(request == null ? null : request.name());
    if (name == null) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Vector store name is required."));
    }

    try {
      VectorStore vectorStore =
          client.vectorStores().create(VectorStoreCreateParams.builder().name(name).build());
      return ResponseEntity.ok(
          new CreateVectorStoreResponse(
              vectorStore.id(), vectorStore.name(), vectorStore.createdAt()));
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(new ErrorResponse("Failed to create vector store."));
    }
  }

  @GetMapping(path = "/{id}")
  public ResponseEntity<?> validateVectorStore(@PathVariable("id") String id) {
    OpenAIClient client = clientProvider.getIfAvailable();
    if (client == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new ErrorResponse("OpenAI is not configured. Set OPENAI_API_KEY and restart."));
    }

    String vectorStoreId = OpenAISettingsResolver.normalizeOptional(id);
    if (vectorStoreId == null) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Vector store id is required."));
    }

    try {
      VectorStore vectorStore =
          client
              .vectorStores()
              .retrieve(VectorStoreRetrieveParams.builder().vectorStoreId(vectorStoreId).build());
      long fileCount = countFiles(client, Objects.requireNonNull(vectorStore.id()));
      return ResponseEntity.ok(
          new VectorStoreValidationResponse(
              true, vectorStore.name(), fileCount, vectorStore.createdAt()));
    } catch (NotFoundException e) {
      return ResponseEntity.ok(new VectorStoreValidationResponse(false, null, null, null));
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(new ErrorResponse("Failed to retrieve vector store."));
    }
  }

  private long countFiles(OpenAIClient client, String vectorStoreId) {
    FileListPage files =
        client.vectorStores().files().list(FileListParams.builder().vectorStoreId(vectorStoreId).build());
    return files.autoPager().stream().count();
  }

  public record CreateVectorStoreRequest(String name) {}

  public record CreateVectorStoreResponse(String vectorStoreId, String name, Long createdAt) {}

  public record VectorStoreValidationResponse(
      boolean exists, String name, Long fileCount, Long createdAt) {}

  public record ErrorResponse(String error) {}
}
