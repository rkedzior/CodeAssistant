package app.core.search;

public interface TextSearchPort {
  TextSearchResponse searchExact(String query);
}

