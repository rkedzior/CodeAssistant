package app.platform.adapters.analysis;

import app.core.analysis.LlmPort;
import app.core.analysis.RetrievedContextItem;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
public class LocalLlmAdapter implements LlmPort {
  private static final int MAX_PATHS = 6;
  private static final int MAX_PREVIEW_CHARS = 360;

  private final boolean failOnce;
  private final boolean failAlways;
  private final AtomicBoolean didFailOnce = new AtomicBoolean(false);

  public LocalLlmAdapter(boolean failOnce, boolean failAlways) {
    this.failOnce = failOnce;
    this.failAlways = failAlways;
  }

  @Override
  public String answer(
      String prompt, List<RetrievedContextItem> retrievedContext, boolean codeScope) {
    maybeThrowInjectedFault();

    String normalizedPrompt = prompt == null ? "" : prompt.trim();
    List<RetrievedContextItem> safeContext = retrievedContext == null ? List.of() : retrievedContext;

    StringBuilder out = new StringBuilder();
    out.append("Local analysis (deterministic stub).").append("\n\n");
    out.append("Prompt: ").append(normalizedPrompt).append("\n");
    out.append("Retrieved context items: ").append(safeContext.size()).append("\n");

    int limit = Math.min(MAX_PATHS, safeContext.size());
    for (int i = 0; i < limit; i++) {
      RetrievedContextItem item = safeContext.get(i);
      if (item == null || item.path() == null || item.path().isBlank()) continue;
      out.append("- ").append(item.path().trim()).append("\n");
    }

    String excerpt = firstUsefulPreview(safeContext);
    if (excerpt != null) {
      out.append("\n").append("Excerpt: ").append(excerpt).append("\n");
    }

    out.append("\n");
    out.append("Answer:").append("\n");
    out.append(buildHeuristicAnswer(normalizedPrompt, safeContext));
    return out.toString();
  }

  private void maybeThrowInjectedFault() {
    if (failAlways) {
      throw new IllegalStateException("Injected LLM failure (codeassistant.llm.failAlways=true).");
    }
    if (failOnce && didFailOnce.compareAndSet(false, true)) {
      throw new IllegalStateException("Injected LLM failure (codeassistant.llm.failOnce=true).");
    }
  }

  private static String firstUsefulPreview(List<RetrievedContextItem> items) {  
    for (RetrievedContextItem item : items) {
      if (item == null) continue;
      String preview = item.preview();
      if (preview == null || preview.isBlank()) continue;
      String trimmed = preview.trim().replace("\r\n", "\n");
      if (trimmed.length() > MAX_PREVIEW_CHARS) {
        return trimmed.substring(0, MAX_PREVIEW_CHARS) + "…";
      }
      return trimmed;
    }
    return null;
  }

  private static String buildHeuristicAnswer(String prompt, List<RetrievedContextItem> context) {
    String normalizedPrompt = prompt == null ? "" : prompt.trim();

    StringBuilder out = new StringBuilder();
    out.append("Based on the retrieved snippets, start by scanning the listed files for entry points ")
        .append("related to the prompt")
        .append(normalizedPrompt.isBlank() ? "." : " (“" + normalizedPrompt + "”).")
        .append("\n");
    out.append("- Look for controller/handler endpoints, then follow service calls and validators.\n");
    out.append("- If validation is involved, search for annotations or explicit checks near request DTOs.\n");
    out.append("- Use the previews above to jump to the most relevant region and expand from there.\n");
    out.append("\n");
    out.append("This answer is generated locally (no external network calls).");
    return out.toString();
  }
}
