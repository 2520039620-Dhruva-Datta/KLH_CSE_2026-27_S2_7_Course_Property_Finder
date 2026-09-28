package service;

import model.UnifiedProperty;

import java.util.List;

public class SearchResult {

    public final String pattern;
    public final Algorithm algorithm;
    public final int occurrences;
    public final List<UnifiedProperty> matchingProperties;
    public final long elapsedNanos;
    public final String note; // optional extra context, e.g. fuzzy match detail or matched sub-patterns

    public SearchResult(String pattern, Algorithm algorithm, int occurrences,
                         List<UnifiedProperty> matchingProperties, long elapsedNanos) {
        this(pattern, algorithm, occurrences, matchingProperties, elapsedNanos, "");
    }

    public SearchResult(String pattern, Algorithm algorithm, int occurrences,
                         List<UnifiedProperty> matchingProperties, long elapsedNanos, String note) {
        this.pattern = pattern;
        this.algorithm = algorithm;
        this.occurrences = occurrences;
        this.matchingProperties = matchingProperties;
        this.elapsedNanos = elapsedNanos;
        this.note = note == null ? "" : note;
    }

    public int propertyCount() {
        return matchingProperties.size();
    }

    public String elapsedDisplay() {
        double millis = elapsedNanos / 1_000_000.0;
        return String.format("%.3f ms", millis);
    }
}
