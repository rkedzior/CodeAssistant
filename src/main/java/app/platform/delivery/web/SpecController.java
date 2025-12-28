package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigPort;
import app.core.spec.SpecFile;
import app.core.spec.SpecStoragePort;
import app.core.specupdates.ProposeSpecUpdatesUseCase;
import app.core.specupdates.SpecUpdateProposal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SpecController {
  private final ProjectConfigPort projectConfigPort;
  private final SpecStoragePort specStoragePort;
  private final ProposeSpecUpdatesUseCase proposeSpecUpdatesUseCase;

  public SpecController(
      ProjectConfigPort projectConfigPort,
      SpecStoragePort specStoragePort,
      ProposeSpecUpdatesUseCase proposeSpecUpdatesUseCase) {
    this.projectConfigPort = projectConfigPort;
    this.specStoragePort = specStoragePort;
    this.proposeSpecUpdatesUseCase = proposeSpecUpdatesUseCase;
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

  private String renderPage(String path, boolean includeProposals, Model model) {
    boolean configured = projectConfigPort.load().isPresent();

    model.addAttribute("configured", configured);
    model.addAttribute("files", List.of());
    model.addAttribute("path", path == null ? "" : path);
    model.addAttribute("content", null);
    model.addAttribute("error", null);
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
