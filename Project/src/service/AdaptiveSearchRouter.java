package service;

import model.UnifiedProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// The "Adaptive Algorithm Selection Engine" (a.k.a. Smart Search Router).
// This is plain deterministic rule-based dispatch based on the shape of the
// query and the outcome of a first attempt -- explicitly NOT AI/ML -- so the
// GUI must never describe it as such.
//
// Routing rules:
//   comma-separated query (multiple locations)     -> Aho-Corasick
//   repeat of an earlier (scope, pattern) query     -> Suffix Array (indexed)
//   single query, found by exact search             -> KMP
//   single query, exact search finds nothing        -> Levenshtein fuzzy fallback
//   All Sources + a large-enough exact search        -> also runs Parallel Dataset Search
public class AdaptiveSearchRouter {

    private final SearchService searchService;
    private final ParallelDatasetSearch parallelDatasetSearch;
    private final Set<String> seenQueries = new HashSet<>();

    public AdaptiveSearchRouter(SearchService searchService) {
        this.searchService = searchService;
        this.parallelDatasetSearch = new ParallelDatasetSearch(searchService);
    }

    public RouterDecision route(List<UnifiedProperty> properties, String scopeKey, boolean allSources, String rawPattern) {
        String trimmed = rawPattern.trim();
        List<String> multiPatterns = QueryParser.splitPatterns(trimmed);
        String queryKey = scopeKey + "::" + trimmed.toLowerCase(Locale.ROOT);
        boolean repeat = seenQueries.contains(queryKey);
        seenQueries.add(queryKey);

        if (multiPatterns.size() > 1) {
            return routeMultiPattern(properties, multiPatterns);
        }
        if (repeat) {
            return routeIndexed(properties, scopeKey, trimmed);
        }
        return routeSingleExactThenFuzzy(properties, allSources, trimmed);
    }

    // Runs an exact single-pattern algorithm (KMP / Z / Rabin-Karp), automatically
    // switching to the parallel dataset layer when the scope is "All Sources" and
    // large enough for concurrency to actually pay off. Used both by Auto mode
    // and by manual algorithm selection, so the two never disagree on this rule.
    public SearchResult runExact(List<UnifiedProperty> properties, String pattern, Algorithm algorithm, boolean allSources) {
        boolean useParallel = allSources && properties.size() >= ParallelDatasetSearch.MIN_PROPERTIES_FOR_PARALLEL;
        return useParallel
                ? parallelDatasetSearch.run(properties, pattern, algorithm)
                : searchService.search(properties, pattern, algorithm);
    }

    private RouterDecision routeMultiPattern(List<UnifiedProperty> properties, List<String> patterns) {
        SearchResult result = searchService.searchMultiPattern(properties, patterns);
        String reason = "Multiple locations detected (" + patterns.size() + "): " + String.join(", ", patterns) + ".";
        return new RouterDecision(Algorithm.AHO_CORASICK, reason, Algorithm.AHO_CORASICK.technique,
                Algorithm.AHO_CORASICK.complexity, result, false);
    }

    private RouterDecision routeIndexed(List<UnifiedProperty> properties, String scopeKey, String pattern) {
        SearchResult result = searchService.searchIndexed(properties, scopeKey, pattern);
        String reason = "Repeated query detected for this dataset selection -- reusing the cached "
                + "suffix-array index instead of rescanning from zero.";
        return new RouterDecision(Algorithm.SUFFIX_ARRAY, reason, Algorithm.SUFFIX_ARRAY.technique,
                Algorithm.SUFFIX_ARRAY.complexity, result, false);
    }

    private RouterDecision routeSingleExactThenFuzzy(List<UnifiedProperty> properties, boolean allSources, String pattern) {
        boolean useParallel = allSources && properties.size() >= ParallelDatasetSearch.MIN_PROPERTIES_FOR_PARALLEL;
        SearchResult kmpResult = runExact(properties, pattern, Algorithm.KMP, allSources);

        if (kmpResult.propertyCount() > 0) {
            String reason = "Single exact location query detected.";
            return new RouterDecision(Algorithm.KMP, reason, Algorithm.KMP.technique, Algorithm.KMP.complexity,
                    kmpResult, useParallel);
        }

        SearchResult fuzzyResult = searchService.searchFuzzy(properties, pattern);
        long combinedElapsed = kmpResult.elapsedNanos + fuzzyResult.elapsedNanos;
        SearchResult combined = new SearchResult(pattern, Algorithm.LEVENSHTEIN, fuzzyResult.occurrences,
                fuzzyResult.matchingProperties, combinedElapsed, fuzzyResult.note);

        String reason = "No exact match for \"" + pattern + "\" -- falling back to fuzzy, spelling-tolerant search.";
        return new RouterDecision(Algorithm.LEVENSHTEIN, reason, Algorithm.LEVENSHTEIN.technique,
                Algorithm.LEVENSHTEIN.complexity, combined, false);
    }

    public void resetHistory() {
        seenQueries.clear();
    }
}
