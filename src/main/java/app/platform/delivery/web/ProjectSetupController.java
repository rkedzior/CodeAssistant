package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigMode;
import app.core.projectconfig.ProjectConfigPort;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProjectSetupController {
  private final ProjectConfigPort projectConfigPort;
  private final ProjectSetupFormValidator projectSetupFormValidator;

  public ProjectSetupController(
      ProjectConfigPort projectConfigPort, ProjectSetupFormValidator projectSetupFormValidator) {
    this.projectConfigPort = projectConfigPort;
    this.projectSetupFormValidator = projectSetupFormValidator;
  }

  @InitBinder("form")
  void initBinder(WebDataBinder binder) {
    binder.addValidators(projectSetupFormValidator);
  }

  @GetMapping("/setup")
  public String setup(Model model) {
    if (!model.containsAttribute("form")) {
      model.addAttribute("form", toFormOrDefault());
    }
    return "setup";
  }

  @PostMapping("/setup")
  public String setupSubmit(
      @Valid @ModelAttribute("form") ProjectSetupForm form,
      BindingResult bindingResult,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      return "setup";
    }

    projectConfigPort.save(toConfig(form));
    redirectAttributes.addFlashAttribute("successMessage", "Configuration saved.");
    return "redirect:/";
  }

  private ProjectSetupForm toFormOrDefault() {
    ProjectSetupForm form = new ProjectSetupForm();
    projectConfigPort
        .load()
        .ifPresent(
            config -> {
              form.setMode(config.mode());
              form.setOpenaiApiKey(config.openaiApiKey());
              form.setOpenaiModel(config.openaiModel());
              form.setOpenaiVectorStoreId(config.openaiVectorStoreId());
              form.setLocalRepoPath(config.localRepoPath());
              form.setGithubRepo(config.githubRepo());
              form.setGithubToken(config.githubToken());
            });
    return form;
  }

  private static ProjectConfig toConfig(ProjectSetupForm form) {
    String openaiModel = normalizeOptional(form.getOpenaiModel());
    String openaiVectorStoreId = normalizeOptional(form.getOpenaiVectorStoreId());
    if (form.getMode() == ProjectConfigMode.LOCAL) {
      return new ProjectConfig(
          ProjectConfigMode.LOCAL,
          form.getOpenaiApiKey(),
          form.getLocalRepoPath(),
          null,
          null,
          openaiModel,
          openaiVectorStoreId);
    }

    return new ProjectConfig(
        ProjectConfigMode.GITHUB,
        form.getOpenaiApiKey(),
        null,
        form.getGithubRepo(),
        form.getGithubToken(),
        openaiModel,
        openaiVectorStoreId);
  }

  private static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
