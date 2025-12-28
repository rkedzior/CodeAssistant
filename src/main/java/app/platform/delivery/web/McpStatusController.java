package app.platform.delivery.web;

import app.core.observations.Observation;
import app.core.observations.ObservationSubtype;
import app.core.observations.ObservationsPort;
import app.platform.mcp.McpToolRegistry;
import java.util.Arrays;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class McpStatusController {
  private final McpToolRegistry mcpToolRegistry;
  private final ObservationsPort observationsPort;

  public McpStatusController(McpToolRegistry mcpToolRegistry, ObservationsPort observationsPort) {
    this.mcpToolRegistry = mcpToolRegistry;
    this.observationsPort = observationsPort;
  }

  @GetMapping("/mcp")
  public String status(Model model) {
    model.addAttribute("running", true);
    model.addAttribute("tools", mcpToolRegistry.listTools());

    model.addAttribute("subtypes", Arrays.stream(ObservationSubtype.values()).toList());
    if (!model.containsAttribute("writeObservationText"))
      model.addAttribute("writeObservationText", "");
    if (!model.containsAttribute("writeObservationSubtype"))
      model.addAttribute("writeObservationSubtype", ObservationSubtype.NOTE.key());
    return "mcp";
  }

  @PostMapping("/mcp/write-observation")
  public String writeObservation(
      @RequestParam(name = "text", required = false) String text,
      @RequestParam(name = "subtype", required = false) String subtype,
      RedirectAttributes redirectAttributes) {
    String normalizedText = text == null ? "" : text;
    String normalizedSubtype = subtype == null ? ObservationSubtype.NOTE.key() : subtype.trim();

    redirectAttributes.addFlashAttribute("writeObservationText", normalizedText);
    redirectAttributes.addFlashAttribute("writeObservationSubtype", normalizedSubtype);

    if (normalizedText.isBlank()) {
      redirectAttributes.addFlashAttribute(
          "writeObservationErrorMessage", "Observation text must not be blank.");
      return "redirect:/mcp";
    }

    ObservationSubtype parsedSubtype;
    try {
      parsedSubtype = ObservationSubtype.fromJson(normalizedSubtype);
    } catch (IllegalArgumentException e) {
      redirectAttributes.addFlashAttribute("writeObservationErrorMessage", e.getMessage());
      return "redirect:/mcp";
    }

    Observation saved = observationsPort.save(normalizedText, parsedSubtype);
    redirectAttributes.addFlashAttribute(
        "writeObservationSuccessMessage", "Observation saved via write_observation.");
    redirectAttributes.addFlashAttribute(
        "writeObservationResult",
        new WriteObservationResult(
            saved.id(),
            saved.subtype() == null ? ObservationSubtype.OTHER.key() : saved.subtype().key(),
            saved.text(),
            saved.createdAt()));
    return "redirect:/mcp";
  }

  public record WriteObservationResult(String id, String subtype, String text, long createdAt) {}
}
