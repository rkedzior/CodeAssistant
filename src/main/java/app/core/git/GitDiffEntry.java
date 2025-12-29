package app.core.git;

public record GitDiffEntry(Type type, String path, String previousPath) {
  public enum Type {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED
  }
}
