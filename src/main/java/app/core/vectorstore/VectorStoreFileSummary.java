package app.core.vectorstore;

import java.util.Map;

public record VectorStoreFileSummary(String fileId, long sizeBytes, Map<String, String> attributes) {}
