package search;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// Exact pattern search using a rolling hash. A hash collision alone is never
// trusted -- every hash match is verified character-by-character before being
// recorded, so this can never report a false positive.
public class RabinKarpSearch {

    private static final long DEFAULT_BASE = 256L;
    private static final long DEFAULT_MODULUS = 1_000_000_007L;

    // "Randomized Hashing" extension: a small curated set of large primes to
    // pick a modulus from at random per search call. Character verification
    // already rules out false positives regardless of modulus, so this exists
    // purely to avoid a fixed, predictable modulus being targeted by
    // adversarially constructed inputs -- the classic motivation for
    // randomizing a rolling hash.
    private static final long[] CANDIDATE_MODULI = {
            1_000_000_007L, 998_244_353L, 999_999_937L, 100_000_007L
    };

    public static List<Integer> search(String text, String pattern) {
        return search(text, pattern, DEFAULT_BASE, DEFAULT_MODULUS);
    }

    public static List<Integer> searchRandomized(String text, String pattern) {
        long modulus = CANDIDATE_MODULI[ThreadLocalRandom.current().nextInt(CANDIDATE_MODULI.length)];
        return search(text, pattern, DEFAULT_BASE, modulus);
    }

    // Overload used by the "randomized hashing" extension: callers may supply a
    // randomly chosen prime modulus per call so the hash function is not fixed
    // and predictable across runs (a classic defence against adversarial inputs
    // engineered to collide against one specific modulus).
    public static List<Integer> search(String text, String pattern, long base, long modulus) {

        List<Integer> matchPositions = new ArrayList<>();

        if (text == null || pattern == null || text.isEmpty() || pattern.isEmpty()
                || pattern.length() > text.length()) {
            return matchPositions;
        }

        int n = text.length();
        int m = pattern.length();

        long patternHash = 0L;
        long windowHash = 0L;
        long highOrder = 1L; // base^(m-1) mod modulus, used to drop the leading char when sliding

        for (int i = 0; i < m - 1; i++) {
            highOrder = (highOrder * base) % modulus;
        }

        for (int i = 0; i < m; i++) {
            patternHash = (patternHash * base + pattern.charAt(i)) % modulus;
            windowHash = (windowHash * base + text.charAt(i)) % modulus;
        }

        for (int i = 0; i <= n - m; i++) {
            if (patternHash == windowHash && regionMatches(text, i, pattern)) {
                matchPositions.add(i);
            }

            if (i < n - m) {
                windowHash = (windowHash - text.charAt(i) * highOrder % modulus + modulus) % modulus;
                windowHash = (windowHash * base + text.charAt(i + m)) % modulus;
            }
        }

        return matchPositions;
    }

    // The actual character verification step -- this is what makes the search
    // exact rather than "probably correct": a hash match is only a candidate.
    private static boolean regionMatches(String text, int start, String pattern) {
        for (int k = 0; k < pattern.length(); k++) {
            if (text.charAt(start + k) != pattern.charAt(k)) {
                return false;
            }
        }
        return true;
    }
}
