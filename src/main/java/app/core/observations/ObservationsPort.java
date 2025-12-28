package app.core.observations;

import java.util.List;

public interface ObservationsPort {
  Observation save(String text, ObservationSubtype subtype);

  List<Observation> list();
}

