package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfig;
import app.core.projectconfig.ProjectConfigMode;
import app.core.projectconfig.ProjectConfigPort;
import app.core.projectstate.ProjectStatePort;
import app.platform.openai.OpenAISettingsResolver;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
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
  private final ProjectStatePort projectStatePort;
  private final OpenAISettingsResolver openAISettingsResolver;
  private final boolean allowKeyInUi;

  public ProjectSetupController(
      ProjectConfigPort projectConfigPort,
      ProjectSetupFormValidator projectSetupFormValidator,
      ProjectStatePort projectStatePort,
      OpenAISettingsResolver openAISettingsResolver,
      @Value("${codeassistant.allowKeyInUi:false}") boolean allowKeyInUi) {
    this.projectConfigPort = projectConfigPort;
    this.projectSetupFormValidator = projectSetupFormValidator;
    this.projectStatePort = projectStatePort;
    this.openAISettingsResolver = openAISettingsResolver;
    this.allowKeyInUi = allowKeyInUi;
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
    OpenAISettingsResolver.ResolvedApiKey apiKey = openAISettingsResolver.resolveApiKey();
    model.addAttribute("openaiKeyConfigured", apiKey.apiKey() != null);
    model.addAttribute(
        "openaiKeySource", apiKey.source() == null ? null : apiKey.source().jsonValue());
    model.addAttribute("allowKeyInUi", allowKeyInUi);
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

    ProjectConfig config = toConfig(form);
    projectConfigPort.save(config);
    updateMetadata(config);
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
              form.setOpenaiModel(config.openaiModel());
              form.setOpenaiVectorStoreId(config.openaiVectorStoreId());        
              form.setLocalRepoPath(config.localRepoPath());
              form.setGithubRepo(config.githubRepo());
              form.setGithubToken(config.githubToken());
            });
    return form;
  }

  private ProjectConfig toConfig(ProjectSetupForm form) {
    String openaiModel = normalizeOptional(form.getOpenaiModel());
    String openaiVectorStoreId = normalizeOptional(form.getOpenaiVectorStoreId());
    String openaiApiKey = resolveOpenaiApiKey(form);
    if (form.getMode() == ProjectConfigMode.LOCAL) {
      return new ProjectConfig(
          ProjectConfigMode.LOCAL,
          openaiApiKey,
          form.getLocalRepoPath(),
          null,
          null,
          openaiModel,
          openaiVectorStoreId);
    }

    return new ProjectConfig(
        ProjectConfigMode.GITHUB,
        openaiApiKey,
        null,
        form.getGithubRepo(),
        form.getGithubToken(),
        openaiModel,
        openaiVectorStoreId);
  }

  private String resolveOpenaiApiKey(ProjectSetupForm form) {
    if (!allowKeyInUi) {
      return null;
    }
    String fromForm = normalizeOptional(form.getOpenaiApiKey());
    if (fromForm != null) {
      return fromForm;
    }
    return projectConfigPort
        .load()
        .map(ProjectConfig::openaiApiKey)
        .map(ProjectSetupController::normalizeOptional)
        .orElse(null);
  }

  private static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private void updateMetadata(ProjectConfig config) {
    projectStatePort.saveMetadata(
        projectStatePort.getOrCreateMetadata().metadata().withProjectConfig(config));
  }
}
