package app.core.vectorstore;

import java.util.Map;

public record VectorStoreFile(String fileId, byte[] content, Map<String, String> attributes) {}

