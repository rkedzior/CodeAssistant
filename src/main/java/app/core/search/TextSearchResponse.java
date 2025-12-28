package app.core.search;

import java.util.List;

public record TextSearchResponse(String query, List<TextSearchFileResult> files) {}

