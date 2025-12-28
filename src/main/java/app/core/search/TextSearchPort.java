package app.core.search;

public interface TextSearchPort {
  TextSearchResponse search(String query, boolean regex);

  default TextSearchResponse searchExact(String query) {
    return search(query, false);
  }
}
