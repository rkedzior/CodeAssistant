package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigPort;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ConfiguredModelAttributeAdvice {
  private final ProjectConfigPort projectConfigPort;

  public ConfiguredModelAttributeAdvice(ProjectConfigPort projectConfigPort) {
    this.projectConfigPort = projectConfigPort;
  }

  @ModelAttribute("configured")
  public boolean configured() {
    return projectConfigPort.load().isPresent();
  }
}

