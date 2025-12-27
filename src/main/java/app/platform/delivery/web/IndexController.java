package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigPort;
import app.core.indexing.StartInitialIndexUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {
  private final ProjectConfigPort projectConfigPort;
  private final StartInitialIndexUseCase startInitialIndexUseCase;

  public IndexController(ProjectConfigPort projectConfigPort, StartInitialIndexUseCase startInitialIndexUseCase) {
    this.projectConfigPort = projectConfigPort;
    this.startInitialIndexUseCase = startInitialIndexUseCase;
  }

  @GetMapping("/index")
  public String index(Model model) {
    model.addAttribute("configured", projectConfigPort.load().isPresent());
    model.addAttribute("job", startInitialIndexUseCase.getStatus());
    return "index";
  }
}

