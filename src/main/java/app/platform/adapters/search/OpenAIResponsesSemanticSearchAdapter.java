package app.platform.adapters.search;

import app.core.search.SemanticSearchPort;
import app.core.search.SemanticSearchResponse;
import app.core.search.SemanticSearchResult;
import app.platform.openai.OpenAISettingsResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.CompoundFilter;
import com.openai.models.ComparisonFilter;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.FileSearchTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextConfig;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.Tool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class OpenAIResponsesSemanticSearchAdapter implements SemanticSearchPort {
  private static final int MAX_K = 50;
  private static final int MAX_PREVIEW_CHARS = 240;

  private static final ResponseTextConfig JSON_SCHEMA_TEXT_CONFIG = buildJsonSchemaTextConfig();

  private static final String INSTRUCTIONS =
      String.join(
          "\n",
          "You are a semantic code search engine.",
          "Use the `file_search` tool to retrieve relevant files/chunks.",
          "Return results strictly as JSON matching the provided schema.",
          "Only include repository-relative file paths.",
          "Preview must be a short plain-text snippet (<= 240 chars) and must not include markdown fences.");

  private final OpenAIClient client;
  private final OpenAISettingsResolver resolver;
  private final ObjectMapper objectMapper;

  public OpenAIResponsesSemanticSearchAdapter(
      OpenAIClient client, OpenAISettingsResolver resolver, ObjectMapper objectMapper) {
    this.client = Objects.requireNonNull(client);
    this.resolver = Objects.requireNonNull(resolver);
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  @Override
  public SemanticSearchResponse search(String query, int k, Map<String, String> filters) {
    if (query == null || query.isBlank()) {
      return new SemanticSearchResponse(query, List.of(), null);
    }

    OpenAISettingsResolver.ResolvedOpenAISettings settings = resolver.resolve();
    String vectorStoreId = OpenAISettingsResolver.normalizeOptional(settings.vectorStoreId());
    if (vectorStoreId == null) {
      return new SemanticSearchResponse(query, List.of(), "OpenAI vector store is not configured.");
    }

    int effectiveK = normalizeK(k);

    FileSearchTool.Builder fileSearchToolBuilder =
        FileSearchTool.builder().addVectorStoreId(vectorStoreId).maxNumResults((long) effectiveK);

    FileSearchTool.Filters fileSearchFilters = toFileSearchFilters(filters);
    if (fileSearchFilters != null) {
      fileSearchToolBuilder.filters(fileSearchFilters);
    }

    ResponsesModel model = ResponsesModel.ofString(settings.model());
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .model(model)
            .instructions(INSTRUCTIONS)
            .input(ResponseCreateParams.Input.ofText(query))
            .tools(List.of(Tool.ofFileSearch(fileSearchToolBuilder.build())))
            .text(JSON_SCHEMA_TEXT_CONFIG)
            .build();

    Response response;
    try {
      response = client.responses().create(params);
    } catch (RuntimeException e) {
      return new SemanticSearchResponse(query, List.of(), "Semantic search failed: " + e.getMessage());
    }

    String outputText = extractOutputText(response);
    if (outputText == null || outputText.isBlank()) {
      return new SemanticSearchResponse(query, List.of(), "Semantic search returned no output.");
    }

    String json = extractJsonObject(outputText);
    if (json == null) {
      return new SemanticSearchResponse(query, List.of(), "Semantic search returned invalid JSON.");
    }

    SemanticSearchResponse parsed;
    try {
      parsed = objectMapper.readValue(json, SemanticSearchResponse.class);
    } catch (Exception e) {
      return new SemanticSearchResponse(query, List.of(), "Semantic search returned invalid JSON.");
    }

    List<SemanticSearchResult> sanitized = sanitizeResults(parsed.results(), effectiveK);
    String queryOut = parsed.query() != null ? parsed.query() : query;
    return new SemanticSearchResponse(queryOut, sanitized, null);
  }

  private static List<SemanticSearchResult> sanitizeResults(List<SemanticSearchResult> results, int k) {
    if (results == null || results.isEmpty() || k <= 0) return List.of();

    List<SemanticSearchResult> sanitized = new ArrayList<>();
    for (SemanticSearchResult result : results) {
      if (result == null) continue;
      String safePath = sanitizePath(result.path());
      if (safePath == null) continue;
      double score = result.score();
      if (!Double.isFinite(score)) continue;

      String preview = sanitizePreview(result.preview());
      sanitized.add(new SemanticSearchResult(safePath, score, preview));
      if (sanitized.size() >= k) break;
    }
    return sanitized.isEmpty() ? List.of() : List.copyOf(sanitized);
  }

  private static String sanitizePreview(String preview) {
    if (preview == null) return null;
    String trimmed = preview.trim();
    if (trimmed.isBlank()) return null;

    String normalized = collapseWhitespace(trimmed.replace("\r\n", "\n").replace("\r", "\n")).trim();
    if (normalized.length() <= MAX_PREVIEW_CHARS) return normalized;
    return normalized.substring(0, MAX_PREVIEW_CHARS);
  }

  private static String collapseWhitespace(String input) {
    StringBuilder out = new StringBuilder(input.length());
    boolean lastWasWhitespace = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      boolean isWs = Character.isWhitespace(c);
      if (isWs) {
        if (!lastWasWhitespace) out.append(' ');
        lastWasWhitespace = true;
      } else {
        out.append(c);
        lastWasWhitespace = false;
      }
    }
    return out.toString();
  }

  private static String sanitizePath(String path) {
    if (path == null) return null;
    String trimmed = path.trim();
    if (trimmed.isBlank()) return null;
    if (trimmed.length() > 400) return null;
    if (trimmed.indexOf('\0') >= 0) return null;
    if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) return null;

    if (trimmed.startsWith("/") || trimmed.startsWith("\\") || trimmed.startsWith("~")) return null;

    String normalized = trimmed.replace('\\', '/');

    if (normalized.matches("(?i)^[a-z]:.*")) return null;
    if (normalized.contains(":")) return null;

    for (String segment : normalized.split("/")) {
      if (segment.equals("..")) return null;
    }

    return normalized;
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

  private static String extractJsonObject(String outputText) {
    if (outputText == null) return null;
    int start = outputText.indexOf('{');
    int end = outputText.lastIndexOf('}');
    if (start < 0 || end < 0 || end <= start) return null;
    return outputText.substring(start, end + 1).trim();
  }

  private static int normalizeK(int k) {
    if (k <= 0) return 10;
    return Math.min(k, MAX_K);
  }

  private static FileSearchTool.Filters toFileSearchFilters(Map<String, String> incoming) {
    if (incoming == null || incoming.isEmpty()) return null;

    Map<String, String> allowed = new HashMap<>();
    for (Map.Entry<String, String> entry : incoming.entrySet()) {
      String key = OpenAISettingsResolver.normalizeOptional(entry.getKey());
      if (key == null) continue;
      String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
      if (!normalizedKey.equals("type") && !normalizedKey.equals("subtype")) continue;

      String value = OpenAISettingsResolver.normalizeOptional(entry.getValue());
      if (value == null) continue;
      allowed.put(normalizedKey, value.trim());
    }

    if (allowed.isEmpty()) return null;

    List<ComparisonFilter> comparisons = new ArrayList<>();
    for (Map.Entry<String, String> entry : allowed.entrySet()) {
      comparisons.add(
          ComparisonFilter.builder()
              .key(entry.getKey())
              .type(ComparisonFilter.Type.EQ)
              .value(entry.getValue())
              .build());
    }

    if (comparisons.size() == 1) {
      return FileSearchTool.Filters.ofComparisonFilter(comparisons.get(0));
    }

    CompoundFilter.Builder compound = CompoundFilter.builder().type(CompoundFilter.Type.AND);
    for (ComparisonFilter comparison : comparisons) {
      compound.addFilter(comparison);
    }
    return FileSearchTool.Filters.ofCompoundFilter(compound.build());
  }

  private static ResponseTextConfig buildJsonSchemaTextConfig() {
    Map<String, Object> itemSchema = new HashMap<>();
    itemSchema.put("type", "object");
    itemSchema.put("additionalProperties", false);
    itemSchema.put("required", List.of("path", "score"));
    itemSchema.put(
        "properties",
        Map.of(
            "path", Map.of("type", "string"),
            "score", Map.of("type", "number"),
            "preview", Map.of("type", List.of("string", "null"))));

    ResponseFormatTextJsonSchemaConfig.Schema schema =
        ResponseFormatTextJsonSchemaConfig.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    Map.of(
                        "query", Map.of("type", "string"),
                        "results", Map.of("type", "array", "items", itemSchema),
                        "error", Map.of("type", List.of("string", "null")))))
            .putAdditionalProperty("required", JsonValue.from(List.of("query", "results")))
            .build();

    ResponseFormatTextJsonSchemaConfig jsonSchemaConfig =
        ResponseFormatTextJsonSchemaConfig.builder()
            .name("semantic_search")
            .schema(schema)
            .strict(true)
            .build();

    ResponseFormatTextConfig format = ResponseFormatTextConfig.ofJsonSchema(jsonSchemaConfig);
    return ResponseTextConfig.builder().format(format).build();
  }
}
