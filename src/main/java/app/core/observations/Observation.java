package app.core.observations;

public record Observation(String id, ObservationSubtype subtype, String text, long createdAt) {}

