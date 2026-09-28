package search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;

// Indexed exact search for "repeated search over a static corpus" use cases:
// build the suffix array once for a set of documents (property location texts),
// then answer any number of pattern queries against it in O(m log n) each,
// instead of re-scanning every document from scratch per query.
//
// Construction uses prefix doubling with two counting-sort passes per round
// (O(n) per round, O(log n) rounds) rather than a comparator-based sort, since
// a comparator sort would cost O(n log^2 n) -- too slow for a corpus built by
// concatenating hundreds of thousands of property location strings.
//
// Kasai's algorithm builds the companion LCP array in O(n); it is not exposed
// as its own search mode (per spec, it is a supporting structure for the
// suffix array), but it is genuinely used here to answer
// longestRepeatedLocationSubstringLength(), not just built and discarded.
public class SuffixArraySearch {

    private final int[] corpus;      // concatenated char codes of every document, separated by -1
    private final int[] docStart;    // corpus offset where each document begins
    private final int documentCount;
    private final int[] suffixArray;
    private final int[] lcpArray;    // Kasai LCP array, aligned with suffixArray

    public SuffixArraySearch(List<String> documents) {
        this.documentCount = documents.size();
        this.docStart = new int[documentCount];

        int totalLength = 0;
        for (String doc : documents) {
            totalLength += doc.length() + 1; // +1 for the trailing separator
        }
        this.corpus = new int[totalLength];

        int cursor = 0;
        for (int d = 0; d < documentCount; d++) {
            docStart[d] = cursor;
            String doc = documents.get(d);
            for (int i = 0; i < doc.length(); i++) {
                corpus[cursor++] = doc.charAt(i);
            }
            corpus[cursor++] = -1; // separator: never equal to any real char code
        }

        this.suffixArray = buildSuffixArray(corpus);
        this.lcpArray = kasaiLcp(corpus, suffixArray);
    }

    // ---- Public query API ----

    public List<Integer> findMatchingDocumentIndices(String patternLower) {
        TreeSet<Integer> matchedDocs = new TreeSet<>();
        if (patternLower == null || patternLower.isEmpty()) {
            return new ArrayList<>(matchedDocs);
        }
        int[] pattern = toIntArray(patternLower);
        int[] range = occurrenceRange(pattern);
        for (int i = range[0]; i < range[1]; i++) {
            matchedDocs.add(documentOwning(suffixArray[i]));
        }
        return new ArrayList<>(matchedDocs);
    }

    public int countOccurrences(String patternLower) {
        if (patternLower == null || patternLower.isEmpty()) {
            return 0;
        }
        int[] range = occurrenceRange(toIntArray(patternLower));
        return range[1] - range[0];
    }

    // A real, working use of the Kasai LCP array beyond construction: the
    // longest substring shared by two different location strings in the
    // current corpus (0 if every location text is unique with no overlap).
    public int longestRepeatedLocationSubstringLength() {
        int best = 0;
        for (int i = 1; i < lcpArray.length; i++) {
            if (documentOwning(suffixArray[i]) != documentOwning(suffixArray[i - 1])) {
                best = Math.max(best, lcpArray[i]);
            }
        }
        return best;
    }

    // ---- Suffix array query: binary search for the [lower, upper) range of ----
    // ---- suffixes that have `pattern` as a prefix.                        ----

    private int[] occurrenceRange(int[] pattern) {
        int lower = lowerBound(pattern);
        int upper = upperBound(pattern);
        return new int[] { lower, upper };
    }

    private int lowerBound(int[] pattern) {
        int lo = 0, hi = suffixArray.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compareSuffixToPattern(suffixArray[mid], pattern) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private int upperBound(int[] pattern) {
        int lo = 0, hi = suffixArray.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compareSuffixToPattern(suffixArray[mid], pattern) <= 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    // <0 if the suffix's own prefix of pattern-length is lexicographically before
    // the pattern, 0 if the pattern is a prefix of the suffix, >0 if after.
    private int compareSuffixToPattern(int suffixStart, int[] pattern) {
        int n = corpus.length;
        for (int k = 0; k < pattern.length; k++) {
            if (suffixStart + k >= n) {
                return -1;
            }
            int diff = corpus[suffixStart + k] - pattern[k];
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    private int documentOwning(int corpusPosition) {
        int lo = 0, hi = documentCount - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (docStart[mid] <= corpusPosition) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private static int[] toIntArray(String s) {
        int[] out = new int[s.length()];
        for (int i = 0; i < s.length(); i++) {
            out[i] = s.charAt(i);
        }
        return out;
    }

    // ---- Kasai's LCP array algorithm (support algorithm for the suffix array) ----

    public static int[] kasaiLcp(int[] s, int[] sa) {
        int n = s.length;
        int[] lcp = new int[n];
        if (n == 0) {
            return lcp;
        }
        int[] rankOf = new int[n];
        for (int i = 0; i < n; i++) {
            rankOf[sa[i]] = i;
        }

        int h = 0;
        for (int i = 0; i < n; i++) {
            if (rankOf[i] > 0) {
                int j = sa[rankOf[i] - 1];
                while (i + h < n && j + h < n && s[i + h] == s[j + h]) {
                    h++;
                }
                lcp[rankOf[i]] = h;
                if (h > 0) {
                    h--;
                }
            } else {
                h = 0;
            }
        }
        return lcp;
    }

    // ---- Suffix array construction: prefix doubling with counting sort, O(n log n) ----

    public static int[] buildSuffixArray(int[] s) {
        int n = s.length;
        if (n == 0) {
            return new int[0];
        }

        // Round 0: dense initial ranks from raw values via counting sort over a
        // small fixed alphabet (sentinel -1 -> 0, real char c -> c + 1, so the
        // alphabet size is bounded by 65537 regardless of corpus size).
        final int ALPHABET = 65537;
        int[] natural = identity(n);
        int[] sa = countingSortByKey(natural, i -> shift(s[i]), ALPHABET);

        int[] rank = new int[n];
        rank[sa[0]] = 0;
        for (int i = 1; i < n; i++) {
            int prev = sa[i - 1], cur = sa[i];
            rank[cur] = rank[prev] + ((shift(s[prev]) == shift(s[cur])) ? 0 : 1);
        }

        // Scratch buffers allocated once and reused every round: with a corpus of
        // several million characters and ~log2(n) rounds, allocating fresh n-sized
        // arrays inside the loop (as a naive per-round countingSortByKey call would)
        // generates gigabytes of short-lived garbage and makes construction GC-bound
        // rather than CPU-bound. Reusing fixed buffers keeps this genuinely O(n log n).
        int[] newRank = new int[n];
        int[] bySecond = new int[n];
        int[] byFirst = new int[n];
        int[] count = new int[n + 2];

        for (int k = 1; k < n && rank[sa[n - 1]] < n - 1; k <<= 1) {

            // ---- Pass 1: stable counting sort by secondKey(i) = rank[i+k]+1 (or 0), range [0, n] ----
            Arrays.fill(count, 0, n + 2, 0);
            for (int i = 0; i < n; i++) {
                int key = (i + k < n) ? rank[i + k] + 1 : 0;
                count[key + 1]++;
            }
            for (int i = 0; i <= n; i++) {
                count[i + 1] += count[i];
            }
            for (int i = 0; i < n; i++) {
                int key = (i + k < n) ? rank[i + k] + 1 : 0;
                bySecond[count[key]++] = i;
            }

            // ---- Pass 2: stable counting sort by firstKey = rank[i], range [0, n-1] ----
            Arrays.fill(count, 0, n + 1, 0);
            for (int i = 0; i < n; i++) {
                count[rank[i] + 1]++;
            }
            for (int i = 0; i < n; i++) {
                count[i + 1] += count[i];
            }
            for (int idx = 0; idx < n; idx++) {
                int i = bySecond[idx];
                byFirst[count[rank[i]]++] = i;
            }

            // ---- Re-rank based on the new order ----
            newRank[byFirst[0]] = 0;
            for (int i = 1; i < n; i++) {
                int prev = byFirst[i - 1], cur = byFirst[i];
                int prevSecond = (prev + k < n) ? rank[prev + k] + 1 : 0;
                int curSecond = (cur + k < n) ? rank[cur + k] + 1 : 0;
                boolean same = rank[prev] == rank[cur] && prevSecond == curSecond;
                newRank[cur] = newRank[prev] + (same ? 0 : 1);
            }

            System.arraycopy(byFirst, 0, sa, 0, n);
            System.arraycopy(newRank, 0, rank, 0, n);
        }

        return sa;
    }

    // Stable counting sort of `inputOrder` (an arbitrary enumeration of indices)
    // by keyOf(index), where keys lie in [0, keyRangeExclusive).
    private static int[] countingSortByKey(int[] inputOrder, IntUnaryOperator keyOf, int keyRangeExclusive) {
        int n = inputOrder.length;
        int[] count = new int[keyRangeExclusive + 1];
        for (int idx : inputOrder) {
            count[keyOf.applyAsInt(idx) + 1]++;
        }
        for (int i = 0; i < keyRangeExclusive; i++) {
            count[i + 1] += count[i];
        }
        int[] output = new int[n];
        for (int idx : inputOrder) {
            int key = keyOf.applyAsInt(idx);
            output[count[key]++] = idx;
        }
        return output;
    }

    private static int[] identity(int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = i;
        }
        return out;
    }

    private static int shift(int rawValue) {
        return rawValue + 1; // -1 (sentinel) -> 0 ; char 0..65535 -> 1..65536
    }
}
