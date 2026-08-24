package search;

import java.util.ArrayList;
import java.util.List;

public class KMPSearch {

    public static List<Integer> search(String text, String pattern) {

        List<Integer> matchPositions = new ArrayList<>();

        if (text == null || pattern == null ||
                text.isEmpty() || pattern.isEmpty()) {
            return matchPositions;
        }

        int[] lps = buildLPS(pattern);

        int i = 0; // index for text
        int j = 0; // index for pattern

        while (i < text.length()) {

            if (text.charAt(i) == pattern.charAt(j)) {
                i++;
                j++;
            }

            if (j == pattern.length()) {

                // Pattern found
                matchPositions.add(i - j);

                j = lps[j - 1];

            } else if (i < text.length()
                    && text.charAt(i) != pattern.charAt(j)) {

                if (j != 0) {
                    j = lps[j - 1];
                } else {
                    i++;
                }
            }
        }

        return matchPositions;
    }

    private static int[] buildLPS(String pattern) {

        int[] lps = new int[pattern.length()];

        int length = 0;
        int i = 1;

        lps[0] = 0;

        while (i < pattern.length()) {

            if (pattern.charAt(i) == pattern.charAt(length)) {

                length++;
                lps[i] = length;
                i++;

            } else {

                if (length != 0) {
                    length = lps[length - 1];
                } else {
                    lps[i] = 0;
                    i++;
                }
            }
        }

        return lps;
    }
}
