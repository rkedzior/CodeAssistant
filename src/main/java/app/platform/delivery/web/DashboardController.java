package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigPort;
import app.core.projectstate.ProjectMetadataState;
import app.core.projectstate.ProjectStatePort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
  private final ProjectConfigPort projectConfigPort;
  private final ProjectStatePort projectStatePort;

  public DashboardController(ProjectConfigPort projectConfigPort, ProjectStatePort projectStatePort) {
    this.projectConfigPort = projectConfigPort;
    this.projectStatePort = projectStatePort;
  }

  @GetMapping("/")
  public String dashboard(Model model) {
    model.addAttribute("configured", projectConfigPort.load().isPresent());
    ProjectMetadataState metadataState = projectStatePort.getOrCreateMetadata();
    String lastIndexedCommit = metadataState.metadata().lastIndexedCommit();
    model.addAttribute(
        "lastIndexedCommitDisplay",
        lastIndexedCommit == null || lastIndexedCommit.isBlank() ? "Not indexed" : lastIndexedCommit);
    return "dashboard";
  }
}
