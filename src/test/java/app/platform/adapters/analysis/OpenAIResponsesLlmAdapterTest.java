package app.platform.adapters.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.platform.openai.OpenAISettingsResolver;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.Tool;
import com.openai.services.blocking.ResponseService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAIResponsesLlmAdapterTest {
  @Mock private OpenAIClient client;
  @Mock private ResponseService responseService;
  @Mock private OpenAISettingsResolver resolver;

  private OpenAIResponsesLlmAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new OpenAIResponsesLlmAdapter(client, resolver);
  }

  @Test
  void answer_blankPrompt_returnsEmptyWithoutCallingOpenAI() {
    String result = adapter.answer("   ", List.of(), true);

    assertEquals("", result);
    verify(client, never()).responses();
    verifyNoInteractions(responseService);
    verifyNoInteractions(resolver);
  }

  @Test
  void answer_missingVectorStoreId_throws() {
    when(resolver.resolve())
        .thenReturn(
            new OpenAISettingsResolver.ResolvedOpenAISettings(
                true, "gpt-4.1-mini", null, OpenAISettingsResolver.ApiKeySource.ENV));

    assertThrows(
        IllegalStateException.class, () -> adapter.answer("test", List.of(), false));
  }

  @Test
  void answer_buildsFileSearchToolWithCodeScopeFilter() {
    when(resolver.resolve())
        .thenReturn(
            new OpenAISettingsResolver.ResolvedOpenAISettings(
                true, "gpt-4.1-mini", "vs_123", OpenAISettingsResolver.ApiKeySource.ENV));
    when(client.responses()).thenReturn(responseService);
    Response openAiResponse = responseWithOutputText("done");
    when(responseService.create(any(ResponseCreateParams.class))).thenReturn(openAiResponse);

    String result = adapter.answer("question", List.of(), true);
    assertEquals("done", result);

    ArgumentCaptor<ResponseCreateParams> captor = ArgumentCaptor.forClass(ResponseCreateParams.class);
    verify(responseService).create(captor.capture());
    ResponseCreateParams params = captor.getValue();

    List<Tool> tools = params.tools().orElseThrow();
    Tool fileSearchTool = tools.stream().filter(Tool::isFileSearch).findFirst().orElseThrow();
    var fileSearch = fileSearchTool.asFileSearch();

    assertEquals("gpt-4.1-mini", params.model().orElseThrow().asString());
    assertEquals("vs_123", fileSearch.vectorStoreIds().get(0));
    var filters = fileSearch.filters().orElseThrow();
    assertEquals("type", filters.asComparisonFilter().key());
    Object rawValue = filters.asComparisonFilter().value();
    String value = rawValue == null ? "" : rawValue.toString();
    org.junit.jupiter.api.Assertions.assertTrue(value.contains("code"));
  }

  private static Response responseWithOutputText(String outputText) {
    Response response = org.mockito.Mockito.mock(Response.class);
    ResponseOutputItem item = org.mockito.Mockito.mock(ResponseOutputItem.class);
    ResponseOutputMessage message = org.mockito.Mockito.mock(ResponseOutputMessage.class);
    ResponseOutputMessage.Content content = org.mockito.Mockito.mock(ResponseOutputMessage.Content.class);
    ResponseOutputText text = org.mockito.Mockito.mock(ResponseOutputText.class);

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
