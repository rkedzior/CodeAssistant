package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigPort;
import app.core.spec.SpecFile;
import app.core.spec.SpecStoragePort;
import app.core.specupdates.ApplySpecUpdatesUseCase;
import app.core.specupdates.ProposeSpecUpdatesUseCase;
import app.core.specupdates.SpecUpdateProposal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SpecController {
  private final ProjectConfigPort projectConfigPort;
  private final SpecStoragePort specStoragePort;
  private final ProposeSpecUpdatesUseCase proposeSpecUpdatesUseCase;
  private final ApplySpecUpdatesUseCase applySpecUpdatesUseCase;

  public SpecController(
      ProjectConfigPort projectConfigPort,
      SpecStoragePort specStoragePort,
      ProposeSpecUpdatesUseCase proposeSpecUpdatesUseCase,
      ApplySpecUpdatesUseCase applySpecUpdatesUseCase) {
    this.projectConfigPort = projectConfigPort;
    this.specStoragePort = specStoragePort;
    this.proposeSpecUpdatesUseCase = proposeSpecUpdatesUseCase;
    this.applySpecUpdatesUseCase = applySpecUpdatesUseCase;
  }

  @GetMapping("/spec")
  public String spec(@RequestParam(name = "path", required = false) String path, Model model) {
    return renderPage(path, false, model);
  }

  @PostMapping("/spec/propose-updates")
  public String proposeUpdates(
      @RequestParam(name = "path", required = false) String path, Model model) {
    return renderPage(path, true, model);
  }

  @PostMapping("/spec/apply-updates")
  public String applyUpdates(
      @RequestParam(name = "paths", required = false) List<String> paths,
      @RequestParam(name = "path", required = false) String path,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (projectConfigPort.load().isEmpty()) {
      return renderPage(path, false, model);
    }

    List<String> updatedPaths;
    try {
      updatedPaths = applySpecUpdatesUseCase.apply(paths);
    } catch (IllegalArgumentException e) {
      model.addAttribute("error", e.getMessage());
      return renderPage(path, true, model);
    } catch (RuntimeException e) {
      model.addAttribute("error", "Failed to apply spec updates.");
      return renderPage(path, true, model);
    }

    if (updatedPaths.isEmpty()) {
      redirectAttributes.addFlashAttribute("successMessage", "No updates selected.");
    } else {
      redirectAttributes.addFlashAttribute(
          "successMessage", "Updates applied. Changes are ready to be committed.");
    }

    if (path == null || path.isBlank()) return "redirect:/spec";
    return "redirect:/spec?path=" + path.trim();
  }

  private String renderPage(String path, boolean includeProposals, Model model) {
    boolean configured = projectConfigPort.load().isPresent();

    model.addAttribute("configured", configured);
    model.addAttribute("files", List.of());
    model.addAttribute("path", path == null ? "" : path);
    model.addAttribute("content", null);
    if (!model.containsAttribute("error")) {
      model.addAttribute("error", null);
    }
    model.addAttribute("proposals", null);

    if (!configured) {
      return "spec";
    }

    List<String> files;
    try {
      files = specStoragePort.listSpecFiles();
    } catch (RuntimeException e) {
      model.addAttribute("error", "Failed to list spec files.");
      return "spec";
    }
    model.addAttribute("files", files);

    if (path == null || path.isBlank()) {
      if (includeProposals) {
        addProposals(model);
      }
      return "spec";
    }

    Optional<SpecFile> file;
    try {
      file = specStoragePort.readSpecFile(path);
    } catch (IllegalArgumentException e) {
      model.addAttribute("error", e.getMessage());
      return "spec";
    } catch (RuntimeException e) {
      model.addAttribute("error", "Failed to read spec file.");
      return "spec";
    }

    if (file.isEmpty()) {
      model.addAttribute("error", "Spec file not found: " + path.trim());
      return "spec";
    }

    model.addAttribute("path", file.get().path());
    model.addAttribute("content", file.get().content());

    if (includeProposals) {
      addProposals(model);
    }
    return "spec";
  }

  private void addProposals(Model model) {
    List<SpecUpdateProposal> proposals;
    try {
      proposals = proposeSpecUpdatesUseCase.propose();
    } catch (RuntimeException e) {
      model.addAttribute("error", "Failed to propose spec updates.");
      return;
    }
    model.addAttribute("proposals", proposals);
  }
}
