package app.platform.adapters.analysis;

import app.core.analysis.LlmPort;
import app.core.analysis.RetrievedContextItem;
import app.platform.openai.OpenAISettingsResolver;
import com.openai.client.OpenAIClient;
import com.openai.models.ComparisonFilter;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.FileSearchTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.Tool;
import java.util.List;
import java.util.Objects;

public class OpenAIResponsesLlmAdapter implements LlmPort {
  private static final String INSTRUCTIONS =
      String.join(
          "\n",
          "You are a helpful code analysis assistant.",
          "Use the file_search tool to retrieve relevant code context.",
          "Answer in plain text with clear, concise guidance.");

  private final OpenAIClient client;
  private final OpenAISettingsResolver resolver;

  public OpenAIResponsesLlmAdapter(OpenAIClient client, OpenAISettingsResolver resolver) {
    this.client = Objects.requireNonNull(client);
    this.resolver = Objects.requireNonNull(resolver);
  }

  @Override
  public String answer(
      String prompt, List<RetrievedContextItem> retrievedContext, boolean codeScope) {
    if (prompt == null || prompt.isBlank()) {
      return "";
    }

    OpenAISettingsResolver.ResolvedOpenAISettings settings = resolver.resolve();
    String vectorStoreId = OpenAISettingsResolver.normalizeOptional(settings.vectorStoreId());
    if (vectorStoreId == null) {
      throw new IllegalStateException("OpenAI vector store id is not configured.");
    }

    FileSearchTool.Builder toolBuilder = FileSearchTool.builder().addVectorStoreId(vectorStoreId);
    if (codeScope) {
      ComparisonFilter filter =
          ComparisonFilter.builder()
              .key("type")
              .type(ComparisonFilter.Type.EQ)
              .value("code")
              .build();
      toolBuilder.filters(FileSearchTool.Filters.ofComparisonFilter(filter));
    }

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .model(ResponsesModel.ofString(settings.model()))
            .instructions(INSTRUCTIONS)
            .input(ResponseCreateParams.Input.ofText(prompt))
            .tools(List.of(Tool.ofFileSearch(toolBuilder.build())))
            .build();

    Response response;
    try {
      response = client.responses().create(params);
    } catch (RuntimeException e) {
      throw new IllegalStateException("OpenAI analysis failed: " + e.getMessage(), e);
    }

    String outputText = extractOutputText(response);
    return outputText == null ? "" : outputText.trim();
  }

  private static String extractOutputText(Response response) {
    if (response == null || response.output() == null) return null;
    StringBuilder out = new StringBuilder();
    for (var item : response.output()) {
      if (item == null || !item.isMessage()) continue;
      var message = item.asMessage();
      if (message.content() == null) continue;
      for (var content : message.content()) {
        if (content == null || !content.isOutputText()) continue;
        String text = content.asOutputText().text();
        if (text != null) out.append(text);
      }
    }
    return out.isEmpty() ? null : out.toString();
  }
}
