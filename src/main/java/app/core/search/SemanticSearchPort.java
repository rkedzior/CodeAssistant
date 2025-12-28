package app.core.search;

import java.util.Map;

public interface SemanticSearchPort {
  SemanticSearchResponse search(String query, int k, Map<String, String> filters);

  default SemanticSearchResponse search(String query, int k) {
    return search(query, k, Map.of());
  }
}

