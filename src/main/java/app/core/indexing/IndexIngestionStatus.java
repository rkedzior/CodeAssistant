package app.core.indexing;

import java.util.List;

public record IndexIngestionStatus(
    int uploaded,
    int processing,
    int ready,
    int failed,
    List<IngestionFailure> failures,
    String lastError) {
  public static IndexIngestionStatus empty() {
    return new IndexIngestionStatus(0, 0, 0, 0, List.of(), null);
  }

  public record IngestionFailure(String fileId, String path, String status) {}
}
