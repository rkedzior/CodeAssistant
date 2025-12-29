package app.core.analysis;

import java.util.List;

public interface LlmPort {
  String answer(String prompt, List<RetrievedContextItem> retrievedContext, boolean codeScope);
}
