package search;

// Wagner-Fischer dynamic programming edit distance -- used to power fuzzy
// (typo-tolerant) location search, e.g. matching "Gachibowlii" to "Gachibowli".
public class LevenshteinSearch {

    private LevenshteinSearch() {
    }

    public static int distance(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";

        int n = a.length();
        int m = b.length();

        if (n == 0) return m;
        if (m == 0) return n;

        // Two-row rolling DP table: dp[j] = edit distance between a[0..i) and b[0..j).
        int[] previousRow = new int[m + 1];
        int[] currentRow = new int[m + 1];

        for (int j = 0; j <= m; j++) {
            previousRow[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            currentRow[0] = i;
            char ca = a.charAt(i - 1);

            for (int j = 1; j <= m; j++) {
                char cb = b.charAt(j - 1);
                int substitutionCost = (ca == cb) ? 0 : 1;

                int deletion = previousRow[j] + 1;
                int insertion = currentRow[j - 1] + 1;
                int substitution = previousRow[j - 1] + substitutionCost;

                currentRow[j] = Math.min(Math.min(deletion, insertion), substitution);
            }

            int[] swap = previousRow;
            previousRow = currentRow;
            currentRow = swap;
        }

        return previousRow[m];
    }

    // Adaptive tolerance: short place names can't absorb as many typos as long
    // ones before the match becomes meaningless noise.
    public static int maxDistanceFor(String pattern) {
        int length = pattern.length();
        if (length <= 4) return 1;
        if (length <= 8) return 2;
        return 3;
    }
}
