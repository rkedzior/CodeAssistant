package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigPort;
import app.core.projectstate.ProjectMetadataState;
import app.core.projectstate.ProjectStatePort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
  private static final String DEFAULT_OPENAI_MODEL = "gpt-4.1-mini";

  private final ProjectConfigPort projectConfigPort;
  private final ProjectStatePort projectStatePort;

  public DashboardController(ProjectConfigPort projectConfigPort, ProjectStatePort projectStatePort) {
    this.projectConfigPort = projectConfigPort;
    this.projectStatePort = projectStatePort;
  }

  @GetMapping("/")
  public String dashboard(Model model) {
    var config = projectConfigPort.load();
    model.addAttribute("configured", config.isPresent());
    model.addAttribute(
        "openaiModelDisplay",
        config.map(c -> normalizeOptional(c.openaiModel())).orElse(DEFAULT_OPENAI_MODEL));
    model.addAttribute(
        "openaiVectorStoreIdDisplay",
        config.map(c -> normalizeOptional(c.openaiVectorStoreId())).orElse("Not configured"));
    ProjectMetadataState metadataState = projectStatePort.getOrCreateMetadata();
    String lastIndexedCommit = metadataState.metadata().lastIndexedCommit();
    model.addAttribute(
        "lastIndexedCommitDisplay",
        lastIndexedCommit == null || lastIndexedCommit.isBlank() ? "Not indexed" : lastIndexedCommit);
    return "dashboard";
  }

  private static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
