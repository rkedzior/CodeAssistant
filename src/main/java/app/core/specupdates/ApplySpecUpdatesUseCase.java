package app.core.specupdates;

import app.core.spec.SpecStoragePort;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ApplySpecUpdatesUseCase {
  private final ProposeSpecUpdatesUseCase proposeSpecUpdatesUseCase;
  private final SpecStoragePort specStoragePort;

  public ApplySpecUpdatesUseCase(
      ProposeSpecUpdatesUseCase proposeSpecUpdatesUseCase, SpecStoragePort specStoragePort) {
    this.proposeSpecUpdatesUseCase = proposeSpecUpdatesUseCase;
    this.specStoragePort = specStoragePort;
  }

  public List<String> apply(List<String> repoRelativePaths) {
    List<String> selectedPaths = normalizePaths(repoRelativePaths);
    if (selectedPaths.isEmpty()) return List.of();

    Map<String, SpecUpdateProposal> proposalsByPath =
        proposeSpecUpdatesUseCase.propose().stream()
            .filter(Objects::nonNull)
            .collect(
                Collectors.toMap(
                    SpecUpdateProposal::path, p -> p, (a, b) -> a, TreeMap::new));

    for (String path : selectedPaths) {
      specStoragePort
          .readSpecFile(path)
          .orElseThrow(() -> new IllegalArgumentException("Spec file not found: " + path));

      SpecUpdateProposal proposal = proposalsByPath.get(path);
      if (proposal == null) {
        throw new IllegalArgumentException("No proposal available for path: " + path);
      }

      specStoragePort.writeSpecFile(path, proposal.proposedContent());
    }

    return selectedPaths.stream().sorted().toList();
  }

  private static List<String> normalizePaths(List<String> paths) {
    if (paths == null || paths.isEmpty()) return List.of();
    return paths.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .map(p -> p.replace('\\', '/'))
        .map(
            p -> {
              String normalized = p;
              while (normalized.startsWith("./")) {
                normalized = normalized.substring(2);
              }
              return normalized;
            })
        .filter(p -> !p.isBlank())
        .distinct()
        .sorted()
        .toList();
  }
}

