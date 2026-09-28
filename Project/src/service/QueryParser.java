package service;

import java.util.ArrayList;
import java.util.List;

// Splits a raw search box entry into individual location patterns. A single
// term yields a one-element list; "Kokapet, Gachibowli, Tellapur" yields three.
public class QueryParser {

    private QueryParser() {
    }

    public static List<String> splitPatterns(String rawQuery) {
        List<String> patterns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i <= rawQuery.length(); i++) {
            char c = i < rawQuery.length() ? rawQuery.charAt(i) : ',';
            if (c == ',') {
                String part = current.toString().trim();
                if (!part.isEmpty()) {
                    patterns.add(part);
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        return patterns.isEmpty() ? List.of(rawQuery.trim()) : patterns;
    }
}
