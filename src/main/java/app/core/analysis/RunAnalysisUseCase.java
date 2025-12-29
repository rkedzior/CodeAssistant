package app.core.analysis;

import app.core.projectconfig.ProjectConfigPort;
import app.core.projectstate.ProjectMetadataState;
import app.core.projectstate.ProjectStatePort;
import app.core.search.SemanticSearchPort;
import app.core.search.SemanticSearchResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunAnalysisUseCase {
  private static final int DEFAULT_K = 8;

  private final ProjectConfigPort projectConfigPort;
  private final ProjectStatePort projectStatePort;
  private final SemanticSearchPort semanticSearchPort;
  private final LlmPort llmPort;

  public RunAnalysisUseCase(
      ProjectConfigPort projectConfigPort,
      ProjectStatePort projectStatePort,
      SemanticSearchPort semanticSearchPort,
      LlmPort llmPort) {
    this.projectConfigPort = projectConfigPort;
    this.projectStatePort = projectStatePort;
    this.semanticSearchPort = semanticSearchPort;
    this.llmPort = llmPort;
  }

  public AnalysisResponse analyze(AnalysisRequest request) {
    String prompt = request == null ? null : request.prompt();
    boolean codeScope = request != null && request.codeScope();

    if (prompt == null || prompt.isBlank()) {
      return AnalysisResponse.failed(prompt, "Prompt is required.");
    }

    if (projectConfigPort.load().isEmpty()) {
      return AnalysisResponse.failed(prompt, "Project is not configured yet.");
    }

    ProjectMetadataState metadataState = projectStatePort.getOrCreateMetadata();
    String lastIndexedCommit = metadataState.metadata().lastIndexedCommit();
    if (lastIndexedCommit == null || lastIndexedCommit.isBlank()) {
      return AnalysisResponse.failed(prompt, "Repository is not indexed yet.");
    }

    Map<String, String> filters = new HashMap<>();
    if (codeScope) {
      filters.put("type", "code");
    }

    SemanticSearchResponse retrieved = semanticSearchPort.search(prompt, DEFAULT_K, filters);
    if (retrieved.error() != null) {
      return AnalysisResponse.failed(prompt, retrieved.error());
    }

    List<RetrievedContextItem> context =
        retrieved.results().stream()
            .map(r -> new RetrievedContextItem(r.path(), r.preview()))
            .toList();

    if (context.isEmpty()) {
      return new AnalysisResponse(
          prompt, "", List.of(), "No retrieved context found. Try a different prompt.");
    }

    String answer;
    try {
      answer = llmPort.answer(prompt, context, codeScope);
    } catch (Exception e) {
      String message =
          e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
      return new AnalysisResponse(prompt, "", context, "Analysis failed: " + message);
    }

    if (answer == null || answer.isBlank()) {
      answer = "Local analysis stub: no answer generated.";
    }

    return new AnalysisResponse(prompt, answer, context, null);
  }
}
