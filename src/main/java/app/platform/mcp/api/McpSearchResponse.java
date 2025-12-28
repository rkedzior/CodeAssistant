package app.platform.mcp.api;

import app.core.search.SemanticSearchResponse;
import app.core.search.TextSearchResponse;

public record McpSearchResponse(String mode, TextSearchResponse text, SemanticSearchResponse semantic) {}

