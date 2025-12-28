package app.platform.delivery.web;

import app.core.analysis.AnalysisRequest;
import app.core.analysis.AnalysisResponse;
import app.core.analysis.RunAnalysisUseCase;
import app.core.projectconfig.ProjectConfigPort;
import app.core.projectstate.ProjectMetadataState;
import app.core.projectstate.ProjectStatePort;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AnalysisController {
  private final ProjectConfigPort projectConfigPort;
  private final ProjectStatePort projectStatePort;
  private final RunAnalysisUseCase runAnalysisUseCase;

  public AnalysisController(
      ProjectConfigPort projectConfigPort,
      ProjectStatePort projectStatePort,
      RunAnalysisUseCase runAnalysisUseCase) {
    this.projectConfigPort = projectConfigPort;
    this.projectStatePort = projectStatePort;
    this.runAnalysisUseCase = runAnalysisUseCase;
  }

  @GetMapping("/analysis")
  public String analysis(Model model) {
    populateBaseModel(model, "", false);
    model.addAttribute("answer", "");
    model.addAttribute("retrievedContext", List.of());
    model.addAttribute("error", null);
    return "analysis";
  }

  @PostMapping("/analysis")
  public String analyze(
      @RequestParam(name = "prompt", required = false) String prompt,
      @RequestParam(name = "codeScope", required = false, defaultValue = "false") boolean codeScope,
      Model model) {
    populateBaseModel(model, prompt, codeScope);
    AnalysisResponse response = runAnalysisUseCase.analyze(new AnalysisRequest(prompt, codeScope));
    model.addAttribute("answer", response.answer());
    model.addAttribute("retrievedContext", response.retrievedContext());
    model.addAttribute("error", response.error());
    return "analysis";
  }

  private void populateBaseModel(Model model, String prompt, boolean codeScope) {
    boolean configured = projectConfigPort.load().isPresent();
    model.addAttribute("configured", configured);

    ProjectMetadataState metadataState = projectStatePort.getOrCreateMetadata();
    String lastIndexedCommit = metadataState.metadata().lastIndexedCommit();
    boolean indexed = lastIndexedCommit != null && !lastIndexedCommit.isBlank();
    model.addAttribute("indexed", indexed);
    model.addAttribute(
        "lastIndexedCommitDisplay", indexed ? lastIndexedCommit.trim() : "Not indexed");

    model.addAttribute("prompt", prompt == null ? "" : prompt);
    model.addAttribute("codeScope", codeScope);
  }
}

