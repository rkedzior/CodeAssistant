package app.core.search;

public class InvalidRegexException extends RuntimeException {
  private final String regex;

  public InvalidRegexException(String regex, String message) {
    super(message);
    this.regex = regex;
  }

  public String regex() {
    return regex;
  }
}
