package app.core.specupdates;

import app.core.observations.Observation;
import app.core.observations.ObservationsPort;
import app.core.spec.SpecFile;
import app.core.spec.SpecStoragePort;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ProposeSpecUpdatesUseCase {
  private static final int MAX_OBSERVATIONS = 20;
  private static final String SECTION_HEADER = "## Proposed updates from observations";

  private final ObservationsPort observationsPort;
  private final SpecStoragePort specStoragePort;

  public ProposeSpecUpdatesUseCase(ObservationsPort observationsPort, SpecStoragePort specStoragePort) {
    this.observationsPort = observationsPort;
    this.specStoragePort = specStoragePort;
  }

  public List<SpecUpdateProposal> propose() {
    List<String> paths = specStoragePort.listSpecFiles();
    List<Observation> observations = observationsPort.list();

    List<String> bullets = toObservationBullets(observations);
    if (bullets.isEmpty()) return List.of();

    return paths.stream()
        .filter(Objects::nonNull)
        .sorted()
        .map(path -> buildProposal(path, bullets))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<SpecUpdateProposal> buildProposal(String path, List<String> bullets) {
    Optional<SpecFile> existing = specStoragePort.readSpecFile(path);
    if (existing.isEmpty()) return Optional.empty();

    String base = existing.get().content() == null ? "" : existing.get().content();
    String trimmed = base.stripTrailing();

    StringBuilder proposed = new StringBuilder(trimmed);
    if (!trimmed.isBlank()) {
      if (!trimmed.endsWith("\n")) proposed.append("\n");
      proposed.append("\n");
    }
    proposed.append(SECTION_HEADER).append("\n\n");
    for (String bullet : bullets) {
      proposed.append("- ").append(bullet).append("\n");
    }

    return Optional.of(
        new SpecUpdateProposal(
            existing.get().path(),
            proposed.toString(),
            "Appends a new section with recent observations."));
  }

  private static List<String> toObservationBullets(List<Observation> observations) {
    if (observations == null || observations.isEmpty()) return List.of();

    List<Observation> normalized =
        observations.stream()
            .filter(Objects::nonNull)
            .filter(o -> o.text() != null && !o.text().isBlank())
            .sorted(
                Comparator.comparingLong(Observation::createdAt)
                    .thenComparing(o -> o.id() == null ? "" : o.id()))
            .toList();
    if (normalized.isEmpty()) return List.of();

    int from = Math.max(0, normalized.size() - MAX_OBSERVATIONS);
    return normalized.subList(from, normalized.size()).stream().map(Observation::text).toList();
  }
}

