package search;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Multi-pattern exact search: builds a trie of all patterns plus Aho-Corasick
// failure links (a BFS over the trie), then scans the text once, following
// failure links on mismatch instead of restarting -- so N patterns are found
// in a single O(n + m + z) pass instead of N separate scans.
public class AhoCorasick {

    public static class Match {
        public final int patternIndex;
        public final int endPosition; // index of the character AFTER the match, in the scanned text

        public Match(int patternIndex, int endPosition) {
            this.patternIndex = patternIndex;
            this.endPosition = endPosition;
        }
    }

    private static class Node {
        final Map<Character, Node> children = new HashMap<>();
        Node fail;
        List<Integer> outputPatternIndices = new ArrayList<>();
    }

    private final Node root = new Node();
    private final List<String> patterns;

    public AhoCorasick(List<String> patterns) {
        this.patterns = patterns;
        buildTrie();
        buildFailureLinks();
    }

    private void buildTrie() {
        for (int idx = 0; idx < patterns.size(); idx++) {
            Node current = root;
            for (char c : patterns.get(idx).toCharArray()) {
                current = current.children.computeIfAbsent(c, k -> new Node());
            }
            current.outputPatternIndices.add(idx);
        }
    }

    private void buildFailureLinks() {
        Deque<Node> queue = new ArrayDeque<>();
        root.fail = root;
        for (Node child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                char c = entry.getKey();
                Node child = entry.getValue();

                Node fallback = current.fail;
                while (fallback != root && !fallback.children.containsKey(c)) {
                    fallback = fallback.fail;
                }
                Node candidate = fallback.children.get(c);
                child.fail = (candidate != null && candidate != child) ? candidate : root;

                // A node inherits its failure link's output set, so a match ending here
                // also reports any shorter pattern that is a suffix of this one.
                child.outputPatternIndices.addAll(child.fail.outputPatternIndices);

                queue.add(child);
            }
        }
    }

    public List<Match> search(String text) {
        List<Match> matches = new ArrayList<>();
        Node current = root;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (current != root && !current.children.containsKey(c)) {
                current = current.fail;
            }
            current = current.children.getOrDefault(c, root);

            for (int patternIndex : current.outputPatternIndices) {
                matches.add(new Match(patternIndex, i + 1));
            }
        }

        return matches;
    }
}
