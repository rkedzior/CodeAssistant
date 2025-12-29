package app.core.indexing;

import java.time.Instant;

public record IndexJobState(
    IndexJobStatus status,
    String progress,
    Instant startedAt,
    Instant finishedAt,
    String error,
    IndexIngestionStatus ingestion) {
  public static IndexJobState idle() {
    return new IndexJobState(
        IndexJobStatus.IDLE, "Idle", null, null, null, IndexIngestionStatus.empty());
  }
}
