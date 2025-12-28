package app.core.analysis;

import java.util.List;

public record AnalysisResponse(
    String prompt, String answer, List<RetrievedContextItem> retrievedContext, String error) {
  public static AnalysisResponse failed(String prompt, String error) {
    return new AnalysisResponse(prompt, "", List.of(), error);
  }
}

