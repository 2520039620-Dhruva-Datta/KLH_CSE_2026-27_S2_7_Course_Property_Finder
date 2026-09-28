package service;

import model.UnifiedProperty;
import search.AhoCorasick;
import search.KMPSearch;
import search.LevenshteinSearch;
import search.RabinKarpSearch;
import search.SuffixArraySearch;
import search.ZAlgorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

// The one place that actually invokes every string-matching algorithm against
// property data. Location search only ever runs against
// getLocationSearchTextLower() (locality/city/state), never the free-text
// description, per the location-search spec.
public class SearchService {

    // Suffix array indexes are expensive to build (O(n log n) over the whole
    // corpus) but cheap to query repeatedly, so one is kept per dataset scope
    // ("All Sources", "Delhi", ...) and reused across searches instead of
    // being rebuilt every time -- this is what makes Suffix Array Search a
    // genuinely "indexed / repeated search" mode rather than a one-off scan.
    private final Map<String, IndexedCorpus> suffixIndexCache = new HashMap<>();

    // Real-estate location strings repeat enormously (all sources together have
    // ~320,000 rows over under 5,000 distinct locality/city/state combinations),
    // so the suffix array is built over the DISTINCT location strings only -- a
    // ~67x smaller corpus -- with each unique string mapped back to every
    // property that shares it. This is a genuine indexing decision (dedupe the
    // corpus before indexing it), not an approximation: query correctness is
    // unaffected because every property sharing a matched string is still
    // reported.
    private static final class IndexedCorpus {
        final SuffixArraySearch search;
        final List<String> uniqueDocuments;
        final List<List<Integer>> originalIndicesByDocument;

        IndexedCorpus(SuffixArraySearch search, List<String> uniqueDocuments,
                      List<List<Integer>> originalIndicesByDocument) {
            this.search = search;
            this.uniqueDocuments = uniqueDocuments;
            this.originalIndicesByDocument = originalIndicesByDocument;
        }
    }

    // ---- Exact single-pattern algorithms: KMP, Z, Rabin-Karp ----

    public SearchResult search(List<UnifiedProperty> properties, String pattern, Algorithm algorithm) {
        String normalizedPattern = pattern.toLowerCase(Locale.ROOT);
        List<UnifiedProperty> matches = new ArrayList<>();
        int occurrences = 0;

        long startNanos = System.nanoTime();
        for (UnifiedProperty property : properties) {
            List<Integer> hits = runExactAlgorithm(algorithm, property.getLocationSearchTextLower(), normalizedPattern);
            if (!hits.isEmpty()) {
                matches.add(property);
                occurrences += hits.size();
            }
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        return new SearchResult(pattern, algorithm, occurrences, matches, elapsedNanos);
    }

    private List<Integer> runExactAlgorithm(Algorithm algorithm, String text, String pattern) {
        return switch (algorithm) {
            case KMP -> KMPSearch.search(text, pattern);
            case Z -> ZAlgorithm.search(text, pattern);
            case RABIN_KARP -> RabinKarpSearch.searchRandomized(text, pattern);
            default -> throw new IllegalArgumentException("Not an exact single-pattern algorithm: " + algorithm);
        };
    }

    // Sample size for the untimed warm-up pass in compareExact.
    private static final int WARMUP_SAMPLE = 20_000;

    public ExactComparisonResult compareExact(List<UnifiedProperty> properties, String pattern) {
        // Untimed warm-up: without it, whichever algorithm runs first pays the JVM's
        // JIT-compilation cost and looks slower purely because of run order.
        List<UnifiedProperty> sample = properties.subList(0, Math.min(properties.size(), WARMUP_SAMPLE));
        for (Algorithm algorithm : new Algorithm[] { Algorithm.KMP, Algorithm.Z, Algorithm.RABIN_KARP }) {
            search(sample, pattern, algorithm);
        }

        SearchResult kmpResult = search(properties, pattern, Algorithm.KMP);
        SearchResult zResult = search(properties, pattern, Algorithm.Z);
        SearchResult rabinKarpResult = search(properties, pattern, Algorithm.RABIN_KARP);
        return new ExactComparisonResult(kmpResult, zResult, rabinKarpResult);
    }

    // ---- Fuzzy search: Levenshtein / Wagner-Fischer ----

    public SearchResult searchFuzzy(List<UnifiedProperty> properties, String pattern) {
        String normalizedPattern = pattern.toLowerCase(Locale.ROOT);
        int maxDistance = LevenshteinSearch.maxDistanceFor(normalizedPattern);

        int patternWords = countWords(normalizedPattern);

        List<UnifiedProperty> matches = new ArrayList<>();
        int fuzzyHits = 0;
        String bestSuggestion = null;
        int bestDistance = Integer.MAX_VALUE;

        // Distinct location strings repeat heavily across properties, so the edit
        // distances for a given string are computed once and reused for every
        // property that shares it.
        Map<String, FuzzyOutcome> outcomeByLocation = new HashMap<>();

        long startNanos = System.nanoTime();
        for (UnifiedProperty property : properties) {
            FuzzyOutcome outcome = outcomeByLocation.computeIfAbsent(property.getLocationSearchTextLower(),
                    text -> evaluateFuzzy(text, normalizedPattern, patternWords, maxDistance));
            if (outcome.hits > 0) {
                matches.add(property);
                fuzzyHits += outcome.hits;
                if (outcome.bestDistance < bestDistance) {
                    bestDistance = outcome.bestDistance;
                    bestSuggestion = outcome.bestCandidate;
                }
            }
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        String note = bestSuggestion == null
                ? "No close spelling match found."
                : "Closest match: \"" + bestSuggestion + "\" (edit distance " + bestDistance + ")";

        return new SearchResult(pattern, Algorithm.LEVENSHTEIN, fuzzyHits, matches, elapsedNanos, note);
    }

    private static final class FuzzyOutcome {
        int hits = 0;
        int bestDistance = Integer.MAX_VALUE;
        String bestCandidate = null;
    }

    // Compares the pattern with each comma/pipe-separated location segment AND with
    // every run of `patternWords` consecutive words inside a segment, so a typo of
    // "gachibowli" still matches a locality like "gachibowli phase 3".
    private static FuzzyOutcome evaluateFuzzy(String locationText, String pattern, int patternWords, int maxDistance) {
        FuzzyOutcome outcome = new FuzzyOutcome();
        for (String segment : splitSegments(locationText)) {
            considerCandidate(outcome, pattern, segment, maxDistance);
            List<String> words = splitWords(segment);
            if (words.size() > patternWords) {
                for (int start = 0; start + patternWords <= words.size(); start++) {
                    considerCandidate(outcome, pattern,
                            String.join(" ", words.subList(start, start + patternWords)), maxDistance);
                }
            }
        }
        return outcome;
    }

    private static void considerCandidate(FuzzyOutcome outcome, String pattern, String candidate, int maxDistance) {
        int distance = LevenshteinSearch.distance(pattern, candidate);
        if (distance <= maxDistance) {
            outcome.hits++;
            if (distance < outcome.bestDistance) {
                outcome.bestDistance = distance;
                outcome.bestCandidate = candidate;
            }
        }
    }

    // Hand-written tokenizers (data preparation, not pattern matching): segments are
    // separated by '|' or ',', words by whitespace. "n/a" placeholders are skipped.
    private static List<String> splitSegments(String text) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : '|';
            if (c == '|' || c == ',') {
                String segment = current.toString().trim();
                if (!segment.isEmpty() && !segment.equals("n/a")) {
                    segments.add(segment);
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        return segments;
    }

    private static List<String> splitWords(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : ' ';
            if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    words.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        return words;
    }

    private static int countWords(String text) {
        return Math.max(1, splitWords(text).size());
    }

    // ---- Multi-pattern search: Aho-Corasick ----

    public SearchResult searchMultiPattern(List<UnifiedProperty> properties, List<String> patterns) {
        List<String> normalizedPatterns = new ArrayList<>();
        for (String p : patterns) {
            String trimmed = p.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                normalizedPatterns.add(trimmed);
            }
        }

        List<UnifiedProperty> matches = new ArrayList<>();
        int occurrences = 0;

        long startNanos = System.nanoTime();
        if (!normalizedPatterns.isEmpty()) {
            AhoCorasick automaton = new AhoCorasick(normalizedPatterns);
            for (UnifiedProperty property : properties) {
                List<AhoCorasick.Match> hits = automaton.search(property.getLocationSearchTextLower());
                if (!hits.isEmpty()) {
                    matches.add(property);
                    occurrences += hits.size();
                }
            }
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        String note = "Patterns searched: " + String.join(", ", normalizedPatterns);
        String joinedPattern = String.join(", ", patterns);
        return new SearchResult(joinedPattern, Algorithm.AHO_CORASICK, occurrences, matches, elapsedNanos, note);
    }

    // ---- Indexed search: Suffix Array (+ Kasai LCP internally) ----

    public SearchResult searchIndexed(List<UnifiedProperty> properties, String scopeKey, String pattern) {
        String normalizedPattern = pattern.toLowerCase(Locale.ROOT);

        long startNanos = System.nanoTime();
        IndexedCorpus index = suffixIndexCache.computeIfAbsent(scopeKey, key -> buildIndex(properties));
        List<Integer> matchedDocIndices = index.search.findMatchingDocumentIndices(normalizedPattern);

        TreeSet<Integer> matchedOriginalIndices = new TreeSet<>();
        int occurrences = 0;
        for (int docIndex : matchedDocIndices) {
            List<Integer> originalIndices = index.originalIndicesByDocument.get(docIndex);
            // The suffix array already proved this unique string contains the
            // pattern; a plain KMP pass over that one (short) string gives the
            // exact per-occurrence count, which is then weighted by how many
            // properties share it -- matching how every other algorithm counts
            // occurrences (once per property, not once per distinct string).
            int hitsInThisDocument = KMPSearch.search(index.uniqueDocuments.get(docIndex), normalizedPattern).size();
            occurrences += hitsInThisDocument * originalIndices.size();
            matchedOriginalIndices.addAll(originalIndices);
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        List<UnifiedProperty> matches = new ArrayList<>();
        for (int idx : matchedOriginalIndices) {
            matches.add(properties.get(idx));
        }

        String note = "Indexed corpus: " + index.uniqueDocuments.size() + " unique location strings covering "
                + properties.size() + " properties (built once, reused across repeat searches)";
        return new SearchResult(pattern, Algorithm.SUFFIX_ARRAY, occurrences, matches, elapsedNanos, note);
    }

    private IndexedCorpus buildIndex(List<UnifiedProperty> properties) {
        Map<String, Integer> docIndexByText = new LinkedHashMap<>();
        List<String> uniqueDocuments = new ArrayList<>();
        List<List<Integer>> originalIndicesByDocument = new ArrayList<>();

        for (int i = 0; i < properties.size(); i++) {
            String text = properties.get(i).getLocationSearchTextLower();
            Integer docIndex = docIndexByText.get(text);
            if (docIndex == null) {
                docIndex = uniqueDocuments.size();
                docIndexByText.put(text, docIndex);
                uniqueDocuments.add(text);
                originalIndicesByDocument.add(new ArrayList<>());
            }
            originalIndicesByDocument.get(docIndex).add(i);
        }

        SuffixArraySearch search = new SuffixArraySearch(uniqueDocuments);
        return new IndexedCorpus(search, uniqueDocuments, originalIndicesByDocument);
    }

    // Invalidates cached suffix-array indexes, e.g. after datasets are (re)loaded.
    public void invalidateIndexes() {
        suffixIndexCache.clear();
    }

    public boolean hasIndexFor(String scopeKey) {
        return suffixIndexCache.containsKey(scopeKey);
    }
}
