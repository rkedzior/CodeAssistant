package app.core.search;

import java.util.List;

public record SemanticSearchResponse(String query, List<SemanticSearchResult> results, String error) {}

