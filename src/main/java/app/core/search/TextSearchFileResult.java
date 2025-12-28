package app.core.search;

import java.util.List;

public record TextSearchFileResult(String path, List<TextSearchMatchLine> matches) {}

