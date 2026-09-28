package loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Whole-file CSV state machine (not line-by-line), because some source datasets
// contain quoted fields with embedded literal newlines (multiline descriptions).
// Splitting on lines first would silently corrupt those records.
public final class CsvParser {

    private CsvParser() {
    }

    public static List<List<String>> parse(Path file) throws IOException {
        return parse(file, ',', true);
    }

    // The per-state listing files are plain tab-separated text with no quoting (tabs and
    // line breaks are flattened out of cells when they are written), so they are read
    // with quoted = false: a '"' there is just a character, e.g. in a stray-punctuation
    // rent city or tenant value, and must not swallow the tabs and newlines after it.
    public static List<List<String>> parse(Path file, char delimiter, boolean quoted) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return parse(content, delimiter, quoted);
    }

    public static List<List<String>> parse(String content) {
        return parse(content, ',', true);
    }

    public static List<List<String>> parse(String content, char delimiter, boolean quoted) {

        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowStarted = false;

        int length = content.length();
        int i = 0;

        while (i < length) {
            char c = content.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < length && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == delimiter) {
                currentRow.add(field.toString());
                field.setLength(0);
                rowStarted = true;
            } else if (c == '"' && quoted) {
                inQuotes = true;
                rowStarted = true;
            } else {
                switch (c) {
                    case '\r' -> {
                        // ignored; '\n' (or EOF) closes the row
                    }
                    case '\n' -> {
                        currentRow.add(field.toString());
                        field.setLength(0);
                        rows.add(currentRow);
                        currentRow = new ArrayList<>();
                        rowStarted = false;
                    }
                    default -> {
                        field.append(c);
                        rowStarted = true;
                    }
                }
            }
            i++;
        }

        if (rowStarted || field.length() > 0) {
            currentRow.add(field.toString());
            rows.add(currentRow);
        }

        return rows;
    }
}
