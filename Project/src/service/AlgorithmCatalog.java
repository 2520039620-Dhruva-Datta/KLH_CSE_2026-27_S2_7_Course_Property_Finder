package service;

import java.util.ArrayList;
import java.util.List;

// Full inventory of every algorithm/technique used anywhere in this project,
// for the "Algorithms Used" dialog. Includes the six dropdown-selectable
// algorithms plus the supporting/performance pieces that are never selected
// directly (Kasai LCP, Parallel Dataset Search, Randomized Hashing) and the
// routing layer itself. Statuses reflect what is actually wired into the
// running app -- nothing here is marked Implemented unless it genuinely runs.
public class AlgorithmCatalog {

    public static class Entry {
        public final String name;
        public final String category;
        public final String purpose;
        public final String technique;
        public final String complexity;
        public final String javaClass;
        public final String status;

        Entry(String name, String category, String purpose, String technique,
              String complexity, String javaClass, String status) {
            this.name = name;
            this.category = category;
            this.purpose = purpose;
            this.technique = technique;
            this.complexity = complexity;
            this.javaClass = javaClass;
            this.status = status;
        }
    }

    private AlgorithmCatalog() {
    }

    public static List<Entry> allEntries() {
        List<Entry> entries = new ArrayList<>();

        for (Algorithm algorithm : new Algorithm[] { Algorithm.KMP, Algorithm.Z, Algorithm.RABIN_KARP }) {
            entries.add(fromAlgorithm(algorithm));
        }
        entries.add(fromAlgorithm(Algorithm.LEVENSHTEIN));
        entries.add(fromAlgorithm(Algorithm.AHO_CORASICK));
        entries.add(fromAlgorithm(Algorithm.SUFFIX_ARRAY));

        entries.add(new Entry(
                "Kasai LCP Array", "Indexed Search",
                "Builds the Longest-Common-Prefix array alongside the suffix array",
                "Rank array + previous-match-length reuse (no re-comparison from scratch)",
                "O(n)",
                "search.SuffixArraySearch#kasaiLcp",
                "Support Algorithm"));

        entries.add(new Entry(
                "Parallel Dataset Search", "Performance Extensions",
                "Speeds up exact search across All Sources by scanning the combined data concurrently",
                "Fixed thread pool, equal contiguous chunks, ordered merge (same result and order as sequential)",
                "O(n / p + m) per thread, p = number of threads",
                "service.ParallelDatasetSearch",
                "Performance Layer"));

        entries.add(new Entry(
                "Randomized Hashing", "Performance Extensions",
                "Reduces the chance of an adversarial worst-case collision in Rabin–Karp",
                "Modulus chosen at random from a curated prime set on every search call",
                "O(n + m) average (same as Rabin–Karp)",
                "search.RabinKarpSearch#searchRandomized",
                "Implemented (optional extension)"));

        entries.add(new Entry(
                "Adaptive Algorithm Selection Engine", "Smart Search Router",
                "Routes each query to the algorithm best suited to its shape -- deterministic rules, not AI/ML",
                "Rule-based dispatch: query shape + first-attempt outcome decide the algorithm",
                "Cost of whichever algorithm it ultimately selects",
                "service.AdaptiveSearchRouter",
                "Implemented"));

        return entries;
    }

    private static Entry fromAlgorithm(Algorithm algorithm) {
        return new Entry(algorithm.label, algorithm.category, algorithm.purpose,
                algorithm.technique, algorithm.complexity, algorithm.javaClass, algorithm.status);
    }
}
