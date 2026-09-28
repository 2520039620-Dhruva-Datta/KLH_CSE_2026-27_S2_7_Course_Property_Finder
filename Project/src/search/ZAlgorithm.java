package search;

import java.util.ArrayList;
import java.util.List;

public class ZAlgorithm {

    // Sentinel used to join pattern and text without a literal delimiter character.
    // char values only ever range 0..65535, so -1 can never collide with real input,
    // unlike a printable separator (e.g. '$') which could theoretically appear in the text.
    private static final int SEPARATOR = -1;

    public static List<Integer> search(String text, String pattern) {

        List<Integer> matchPositions = new ArrayList<>();

        if (text == null || pattern == null || text.isEmpty() || pattern.isEmpty()) {
            return matchPositions;
        }

        int patternLength = pattern.length();
        int textLength = text.length();

        // combined = pattern chars, then the sentinel, then text chars, all as ints
        // so the sentinel value cannot equal any possible char.
        int[] combined = new int[patternLength + 1 + textLength];
        for (int i = 0; i < patternLength; i++) {
            combined[i] = pattern.charAt(i);
        }
        combined[patternLength] = SEPARATOR;
        for (int i = 0; i < textLength; i++) {
            combined[patternLength + 1 + i] = text.charAt(i);
        }

        int[] z = buildZArray(combined);

        int textStart = patternLength + 1;
        for (int i = textStart; i < combined.length; i++) {
            if (z[i] >= patternLength) {
                matchPositions.add(i - textStart);
            }
        }

        return matchPositions;
    }

    // Standard Z-array construction using the [l, r] Z-box to reuse previously
    // computed prefix-match lengths instead of re-comparing from scratch.
    private static int[] buildZArray(int[] s) {

        int n = s.length;
        int[] z = new int[n];

        int l = 0;
        int r = 0;

        for (int i = 1; i < n; i++) {

            if (i < r) {
                // i falls inside the current Z-box: seed z[i] from the mirrored
                // position z[i - l], capped by how much of the box remains.
                z[i] = Math.min(r - i, z[i - l]);
            }

            while (i + z[i] < n && s[z[i]] == s[i + z[i]]) {
                z[i]++;
            }

            if (i + z[i] > r) {
                l = i;
                r = i + z[i];
            }
        }

        return z;
    }
}
