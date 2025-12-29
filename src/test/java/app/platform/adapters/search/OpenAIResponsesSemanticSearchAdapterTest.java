package app.platform.adapters.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.core.search.SemanticSearchResponse;
import app.platform.openai.OpenAISettingsResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.CompoundFilter;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.Tool;
import com.openai.services.blocking.ResponseService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAIResponsesSemanticSearchAdapterTest {
  @Mock private OpenAIClient client;
  @Mock private ResponseService responseService;
  @Mock private OpenAISettingsResolver resolver;

  private OpenAIResponsesSemanticSearchAdapter adapter;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    adapter = new OpenAIResponsesSemanticSearchAdapter(client, resolver, objectMapper);
  }

  @Test
  void search_blankQuery_returnsEmptyWithoutCallingOpenAI() {
    SemanticSearchResponse response = adapter.search("   ", 10, Map.of("type", "java"));

    assertEquals(List.of(), response.results());
    assertNull(response.error());
    verify(client, never()).responses();
    verifyNoInteractions(responseService);
    verifyNoInteractions(resolver);
  }

  @Test
  void search_parsesAndSanitizesResults() {
    when(resolver.resolve())
        .thenReturn(
            new OpenAISettingsResolver.ResolvedOpenAISettings(
                true, "gpt-4.1-mini", "vs_123", OpenAISettingsResolver.ApiKeySource.ENV));
    when(client.responses()).thenReturn(responseService);

    String json =
        """
        {
          "query": "q",
          "results": [
            { "path": "src\\\\main\\\\java\\\\app\\\\App.java", "score": 0.99, "preview": " ok " },
            { "path": "/etc/passwd", "score": 0.98 },
            { "path": "../secrets.txt", "score": 0.97 }
          ]
        }
        """;
    Response openAiResponse = responseWithOutputText(json);
    when(responseService.create(any(ResponseCreateParams.class))).thenReturn(openAiResponse);

    SemanticSearchResponse response = adapter.search("find something", 10, Map.of());

    assertNull(response.error());
    assertEquals(1, response.results().size());
    assertEquals("src/main/java/app/App.java", response.results().get(0).path());
  }

  @Test
  void search_buildsFileSearchToolWithVectorStoreAndCompoundFilters() {
    when(resolver.resolve())
        .thenReturn(
            new OpenAISettingsResolver.ResolvedOpenAISettings(
                true, "gpt-4.1-mini", "vs_123", OpenAISettingsResolver.ApiKeySource.ENV));
    when(client.responses()).thenReturn(responseService);
    Response openAiResponse = responseWithOutputText("{\"query\":\"q\",\"results\":[]}");
    when(responseService.create(any(ResponseCreateParams.class))).thenReturn(openAiResponse);

    adapter.search("foo", 10, Map.of("type", "spec", "subtype", "hld"));

    ArgumentCaptor<ResponseCreateParams> captor = ArgumentCaptor.forClass(ResponseCreateParams.class);
    verify(responseService).create(captor.capture());
    ResponseCreateParams params = captor.getValue();

    assertEquals("gpt-4.1-mini", params.model().orElseThrow().asString());

    List<Tool> tools = params.tools().orElseThrow();
    Tool fileSearchTool = tools.stream().filter(Tool::isFileSearch).findFirst().orElseThrow();
    var fileSearch = fileSearchTool.asFileSearch();

    assertTrue(fileSearch.vectorStoreIds().contains("vs_123"));

    assertTrue(fileSearch.filters().isPresent());
    var filters = fileSearch.filters().orElseThrow();
    assertTrue(filters.isCompoundFilter());

    CompoundFilter compound = filters.asCompoundFilter();
    assertEquals(CompoundFilter.Type.AND, compound.type());

    Set<String> keys =
        compound.filters().stream()
            .filter(CompoundFilter.Filter::isComparison)
            .map(f -> f.asComparison().key())
            .collect(Collectors.toSet());
    assertTrue(keys.contains("type"));
    assertTrue(keys.contains("subtype"));
  }

  private static Response responseWithOutputText(String outputText) {
    Response response = mock(Response.class);
    ResponseOutputItem item = mock(ResponseOutputItem.class);
    ResponseOutputMessage message = mock(ResponseOutputMessage.class);
    ResponseOutputMessage.Content content = mock(ResponseOutputMessage.Content.class);
    ResponseOutputText text = mock(ResponseOutputText.class);

    doReturn(List.of(item)).when(response).output();
    doReturn(true).when(item).isMessage();
    doReturn(message).when(item).asMessage();
    doReturn(List.of(content)).when(message).content();
    doReturn(true).when(content).isOutputText();
    doReturn(text).when(content).asOutputText();
    doReturn(outputText).when(text).text();

    return response;
  }
}
