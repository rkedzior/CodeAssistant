package app.platform.mcp.api;

public record McpSearchRequest(String mode, String query, Boolean regex, Integer k, String type, String subtype) {}

