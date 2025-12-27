package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigPort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
  private final ProjectConfigPort projectConfigPort;

  public DashboardController(ProjectConfigPort projectConfigPort) {
    this.projectConfigPort = projectConfigPort;
  }

  @GetMapping("/")
  public String dashboard(Model model) {
    model.addAttribute("configured", projectConfigPort.load().isPresent());
    return "dashboard";
  }
}

