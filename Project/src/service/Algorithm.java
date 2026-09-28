package service;

// Every runnable matching algorithm the search layer can dispatch to, plus the
// display metadata the GUI needs (dropdown label, live info panel, and the
// "Algorithms Used" dialog) -- kept in one place so there is a single source
// of truth instead of parallel copies drifting apart.
public enum Algorithm {

    KMP("KMP", "Exact Search",
            "Single exact location search",
            "LPS Array (prefix function)",
            "O(n + m)",
            "search.KMPSearch",
            "Implemented, Used by Auto Mode, Comparison Algorithm"),

    Z("Z Algorithm", "Exact Search",
            "Second exact linear-time search, used to cross-check KMP",
            "Z Array + Z-box [L, R]",
            "O(n + m)",
            "search.ZAlgorithm",
            "Implemented, Comparison Algorithm"),

    RABIN_KARP("Rabin–Karp", "Exact Search",
            "Exact search via rolling hash, used in Compare mode",
            "Rolling Hash + character verification",
            "O(n + m) average, O(n·m) worst case",
            "search.RabinKarpSearch",
            "Implemented, Comparison Algorithm"),

    LEVENSHTEIN("Levenshtein Fuzzy Search", "Fuzzy Search",
            "Finds likely matches despite spelling mistakes",
            "Wagner–Fischer Dynamic Programming (edit distance)",
            "O(n · m)",
            "search.LevenshteinSearch",
            "Implemented, Used by Auto Mode"),

    AHO_CORASICK("Aho–Corasick", "Multi-Pattern Search",
            "Searches for several locations in a single pass",
            "Trie + Failure Links (automaton)",
            "O(n + m + z)",
            "search.AhoCorasick",
            "Implemented, Used by Auto Mode"),

    SUFFIX_ARRAY("Suffix Array Search", "Indexed Search",
            "Repeated / indexed exact search over a static corpus",
            "Suffix Array (radix-sort prefix doubling) + Kasai LCP",
            "O(n log n) build, O(m log n) per query",
            "search.SuffixArraySearch",
            "Implemented, Used by Auto Mode");

    public final String label;
    public final String category;
    public final String purpose;
    public final String technique;
    public final String complexity;
    public final String javaClass;
    public final String status;

    Algorithm(String label, String category, String purpose, String technique,
              String complexity, String javaClass, String status) {
        this.label = label;
        this.category = category;
        this.purpose = purpose;
        this.technique = technique;
        this.complexity = complexity;
        this.javaClass = javaClass;
        this.status = status;
    }
}
