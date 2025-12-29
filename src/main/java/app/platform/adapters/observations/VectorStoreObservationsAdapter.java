package app.platform.adapters.observations;

import app.core.observations.Observation;
import app.core.observations.ObservationSubtype;
import app.core.observations.ObservationsPort;
import app.core.vectorstore.VectorStoreFile;
import app.core.vectorstore.VectorStoreFileSummary;
import app.core.vectorstore.VectorStorePort;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class VectorStoreObservationsAdapter implements ObservationsPort {
  private static final String OBSERVATION_TYPE = "observation";

  private final VectorStorePort vectorStorePort;

  public VectorStoreObservationsAdapter(VectorStorePort vectorStorePort) {
    this.vectorStorePort = vectorStorePort;
  }

  @Override
  public Observation save(String text, ObservationSubtype subtype) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Observation text must not be blank.");
    }
    if (subtype == null) {
      throw new IllegalArgumentException("Observation subtype must not be null.");
    }

    long createdAt = System.currentTimeMillis();
    String random = UUID.randomUUID().toString().replace("-", "");
    String requestedFileId = "obs_" + createdAt + "_" + random;

    String normalizedText = text.trim();
    String storedFileId =
        vectorStorePort.createFile(
            requestedFileId,
            normalizedText.getBytes(StandardCharsets.UTF_8),
            Map.of(
                "type",
                OBSERVATION_TYPE,
                "subtype",
                subtype.key(),
                "createdAt",
                Long.toString(createdAt)));

    return new Observation(storedFileId, subtype, normalizedText, createdAt);
  }

  @Override
  public List<Observation> list() {
    List<Observation> observations = new ArrayList<>();
    for (VectorStoreFileSummary summary : vectorStorePort.listFiles()) {
      if (!isObservation(summary.attributes())) continue;

      VectorStoreFile file;
      try {
        file = vectorStorePort.readFile(summary.fileId());
      } catch (Exception ignored) {
        continue;
      }

      Map<String, String> attrs = file.attributes();
      long createdAt = parseCreatedAt(attrs, file.fileId());
      ObservationSubtype subtype = parseSubtype(attrs);
      String content = new String(file.content(), StandardCharsets.UTF_8);
      observations.add(new Observation(file.fileId(), subtype, content, createdAt));
    }

    observations.sort(
        Comparator.comparingLong(Observation::createdAt)
            .reversed()
            .thenComparing(Observation::id, Comparator.reverseOrder()));
    return observations;
  }

  private static boolean isObservation(Map<String, String> attributes) {
    if (attributes == null || attributes.isEmpty()) return false;
    String type = attributes.get("type");
    if (type == null) return false;
    return OBSERVATION_TYPE.equalsIgnoreCase(type.trim());
  }

  private static ObservationSubtype parseSubtype(Map<String, String> attributes) {
    if (attributes == null) return ObservationSubtype.OTHER;
    String subtype = attributes.get("subtype");
    if (subtype == null || subtype.isBlank()) return ObservationSubtype.OTHER;
    try {
      return ObservationSubtype.fromJson(subtype);
    } catch (IllegalArgumentException ignored) {
      return ObservationSubtype.OTHER;
    }
  }

  private static long parseCreatedAt(Map<String, String> attributes, String fileId) {
    if (attributes != null) {
      String createdAtStr = attributes.get("createdAt");
      if (createdAtStr != null && !createdAtStr.isBlank()) {
        try {
          return Long.parseLong(createdAtStr.trim());
        } catch (NumberFormatException ignored) {
        }
      }
    }

    if (fileId != null && fileId.startsWith("obs_")) {
      int start = "obs_".length();
      int end = fileId.indexOf('_', start);
      if (end > start) {
        try {
          return Long.parseLong(fileId.substring(start, end));
        } catch (NumberFormatException ignored) {
        }
      }
    }

    return 0L;
  }
}
