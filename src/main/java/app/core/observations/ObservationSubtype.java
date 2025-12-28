package app.core.observations;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ObservationSubtype {
  NOTE("note"),
  DECISION("decision"),
  RISK("risk"),
  OTHER("other");

  private final String key;

  ObservationSubtype(String key) {
    this.key = key;
  }

  @JsonValue
  public String key() {
    return key;
  }

  @JsonCreator
  public static ObservationSubtype fromJson(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (ObservationSubtype subtype : values()) {
      if (subtype.key.equals(normalized)) {
        return subtype;
      }
    }
    throw new IllegalArgumentException("Invalid observation subtype: " + value);
  }
}

