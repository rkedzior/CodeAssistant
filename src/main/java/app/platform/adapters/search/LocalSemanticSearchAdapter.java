package app.platform.adapters.search;

import app.core.projectconfig.ProjectConfigPort;
import app.core.search.SemanticSearchPort;
import app.core.search.SemanticSearchResponse;
import app.core.search.SemanticSearchResult;
import app.core.vectorstore.VectorStoreFile;
import app.core.vectorstore.VectorStoreFileSummary;
import app.core.vectorstore.VectorStorePort;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class LocalSemanticSearchAdapter implements SemanticSearchPort {
  private static final int VECTOR_DIM = 512;
  private static final int MAX_K = 50;
  private static final int MAX_BYTES_FOR_SCORING = 200_000;
  private static final int MAX_PREVIEW_CHARS = 240;
  private static final Pattern TOKEN = Pattern.compile("[\\p{IsAlphabetic}\\p{IsDigit}_]{2,}");

  private final ProjectConfigPort projectConfigPort;
  private final VectorStorePort vectorStorePort;

  public LocalSemanticSearchAdapter(ProjectConfigPort projectConfigPort, VectorStorePort vectorStorePort) {
    this.projectConfigPort = projectConfigPort;
    this.vectorStorePort = vectorStorePort;
  }

  @Override
  public SemanticSearchResponse search(String query, int k, Map<String, String> filters) {
    if (query == null || query.isBlank()) {
      return new SemanticSearchResponse(query, List.of(), null);
    }

    if (projectConfigPort.load().isEmpty()) {
      return new SemanticSearchResponse(query, List.of(), "Project is not configured yet.");
    }

    int effectiveK = normalizeK(k);
    List<String> queryTokens = tokenize(query);
    double[] queryVector = vectorize(queryTokens);
    double queryNorm = norm(queryVector);
    if (queryNorm == 0) {
      return new SemanticSearchResponse(query, List.of(), null);
    }

    List<ScoredDoc> scored = new ArrayList<>();
    for (VectorStoreFileSummary summary : vectorStorePort.listFiles()) {
      String path = summary.attributes().get("path");
      if (path == null || path.isBlank()) continue;

      VectorStoreFile file;
      try {
        file = vectorStorePort.readFile(summary.fileId());
      } catch (Exception ignored) {
        continue;
      }

      byte[] bytes = file.content();
      if (bytes == null || bytes.length == 0) continue;
      if (looksBinary(bytes)) continue;

      String content = toUtf8String(bytes, MAX_BYTES_FOR_SCORING);

      List<String> docTokens = tokenize(path + "\n" + content);
      double[] docVector = vectorize(docTokens);
      double score = cosine(queryVector, queryNorm, docVector);
      if (score <= 0) continue;

      String preview = buildPreview(content, queryTokens);
      scored.add(new ScoredDoc(path, score, preview));
    }

    scored.sort(
        Comparator.comparingDouble(ScoredDoc::score).reversed().thenComparing(ScoredDoc::path));

    List<SemanticSearchResult> results =
        scored.stream()
            .limit(effectiveK)
            .map(d -> new SemanticSearchResult(d.path(), d.score(), d.preview()))
            .toList();

    return new SemanticSearchResponse(query, results, null);
  }

  private static int normalizeK(int k) {
    if (k <= 0) return 10;
    return Math.min(k, MAX_K);
  }

  private static List<String> tokenize(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    Matcher matcher = TOKEN.matcher(lower);
    List<String> tokens = new ArrayList<>();
    while (matcher.find()) {
      tokens.add(matcher.group());
    }
    return tokens;
  }

  private static double[] vectorize(List<String> tokens) {
    double[] vector = new double[VECTOR_DIM];
    for (String token : tokens) {
      int index = smear(token.hashCode()) & (VECTOR_DIM - 1);
      vector[index] += 1.0;
    }
    return vector;
  }

  private static double cosine(double[] queryVector, double queryNorm, double[] docVector) {
    double dot = 0;
    double docNormSq = 0;
    for (int i = 0; i < VECTOR_DIM; i++) {
      dot += queryVector[i] * docVector[i];
      docNormSq += docVector[i] * docVector[i];
    }

    if (docNormSq == 0) return 0;
    return dot / (queryNorm * Math.sqrt(docNormSq));
  }

  private static double norm(double[] vector) {
    double sumSq = 0;
    for (double v : vector) {
      sumSq += v * v;
    }
    return Math.sqrt(sumSq);
  }

  private static int smear(int hashCode) {
    int h = hashCode;
    h ^= (h >>> 20) ^ (h >>> 12);
    return h ^ (h >>> 7) ^ (h >>> 4);
  }

  private static boolean looksBinary(byte[] bytes) {
    int max = Math.min(bytes.length, 8192);
    for (int i = 0; i < max; i++) {
      if (bytes[i] == 0) return true;
    }
    return false;
  }

  private static String toUtf8String(byte[] bytes, int maxBytes) {
    int len = Math.min(bytes.length, maxBytes);
    return new String(bytes, 0, len, StandardCharsets.UTF_8);
  }

  private static String buildPreview(String content, List<String> queryTokens) {
    if (content == null || content.isBlank()) return null;

    String normalized = content.replace("\r\n", "\n");
    String lower = normalized.toLowerCase(Locale.ROOT);

    int best = Integer.MAX_VALUE;
    for (String token : queryTokens) {
      if (token.length() < 2) continue;
      int idx = lower.indexOf(token);
      if (idx >= 0 && idx < best) {
        best = idx;
      }
    }

    int start;
    int end;
    if (best != Integer.MAX_VALUE) {
      start = Math.max(0, best - 80);
      end = Math.min(normalized.length(), best + 160);
    } else {
      start = 0;
      end = Math.min(normalized.length(), MAX_PREVIEW_CHARS);
    }

    String slice = normalized.substring(start, end);
    String collapsed = collapseWhitespace(slice).trim();
    if (collapsed.length() > MAX_PREVIEW_CHARS) {
      collapsed = collapsed.substring(0, MAX_PREVIEW_CHARS);
    }

    boolean prefix = start > 0;
    boolean suffix = end < normalized.length();
    if (prefix) collapsed = "…" + collapsed;
    if (suffix) collapsed = collapsed + "…";
    return collapsed;
  }

  private static String collapseWhitespace(String input) {
    StringBuilder out = new StringBuilder(input.length());
    boolean lastWasWhitespace = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      boolean isWs = Character.isWhitespace(c);
      if (isWs) {
        if (!lastWasWhitespace) out.append(' ');
        lastWasWhitespace = true;
      } else {
        out.append(c);
        lastWasWhitespace = false;
      }
    }
    return out.toString();
  }

  private record ScoredDoc(String path, double score, String preview) {}
}

