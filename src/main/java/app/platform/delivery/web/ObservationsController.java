package app.platform.delivery.web;

import app.core.observations.Observation;
import app.core.observations.ObservationSubtype;
import app.core.observations.ObservationsPort;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ObservationsController {
  private final ObservationsPort observationsPort;

  public ObservationsController(ObservationsPort observationsPort) {
    this.observationsPort = observationsPort;
  }

  @GetMapping("/observations")
  public String observations(Model model) {
    if (!model.containsAttribute("text")) model.addAttribute("text", "");
    if (!model.containsAttribute("subtype")) model.addAttribute("subtype", ObservationSubtype.NOTE.key());

    model.addAttribute("subtypes", Arrays.stream(ObservationSubtype.values()).toList());
    model.addAttribute("observations", toRows(observationsPort.list()));
    return "observations";
  }

  @PostMapping("/observations")
  public String addObservation(
      @RequestParam(name = "text", required = false) String text,
      @RequestParam(name = "subtype", required = false) String subtype,
      Model model,
      RedirectAttributes redirectAttributes) {
    String normalizedText = text == null ? "" : text;
    String normalizedSubtype = subtype == null ? ObservationSubtype.NOTE.key() : subtype.trim();

    model.addAttribute("text", normalizedText);
    model.addAttribute("subtype", normalizedSubtype);

    if (normalizedText.isBlank()) {
      model.addAttribute("errorMessage", "Observation text must not be blank.");
      return observations(model);
    }

    ObservationSubtype parsedSubtype;
    try {
      parsedSubtype = ObservationSubtype.fromJson(normalizedSubtype);
    } catch (IllegalArgumentException e) {
      model.addAttribute("errorMessage", e.getMessage());
      return observations(model);
    }

    observationsPort.save(normalizedText, parsedSubtype);
    redirectAttributes.addFlashAttribute("successMessage", "Observation saved.");
    return "redirect:/observations";
  }

  private static List<ObservationRow> toRows(List<Observation> observations) {
    if (observations == null || observations.isEmpty()) return List.of();
    return observations.stream()
        .map(
            obs ->
                new ObservationRow(
                    obs.id(),
                    obs.subtype() == null ? ObservationSubtype.OTHER.key() : obs.subtype().key(),
                    obs.text(),
                    obs.createdAt() > 0 ? Instant.ofEpochMilli(obs.createdAt()) : null))
        .toList();
  }

  public record ObservationRow(String id, String subtype, String text, Instant createdAt) {}
}

