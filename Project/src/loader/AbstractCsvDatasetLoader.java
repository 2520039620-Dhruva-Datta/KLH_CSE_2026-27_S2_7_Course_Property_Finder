package loader;

import model.UnifiedProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Shared load loop for every dataset: read+parse the file once, walk each data
// row through the subclass's own column mapping, and never let one malformed
// row (or a missing/unreadable file) take down the whole application.
public abstract class AbstractCsvDatasetLoader implements DatasetLoader {

    @Override
    public DatasetLoadResult load(Path file) {

        List<UnifiedProperty> properties = new ArrayList<>();

        if (!Files.exists(file)) {
            return new DatasetLoadResult(getSourceId(), getDisplayName(), properties, 0, 0,
                    "File not found: " + file);
        }

        List<List<String>> rows;
        try {
            rows = CsvParser.parse(file, delimiter(), quotedFields());
        } catch (IOException e) {
            return new DatasetLoadResult(getSourceId(), getDisplayName(), properties, 0, 0,
                    "Could not read file: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            return new DatasetLoadResult(getSourceId(), getDisplayName(), properties, 0, 0, "File is empty");
        }

        List<String> header = rows.get(0);
        Map<String, Integer> headerIndex = buildHeaderIndex(header);

        int loaded = 0;
        int skipped = 0;

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.isEmpty() || (row.size() == 1 && row.get(0).isBlank())) {
                continue; // trailing/blank line, not a real record either way
            }
            try {
                UnifiedProperty property = mapRow(headerIndex, header, row);
                if (property != null) {
                    properties.add(property);
                    loaded++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                skipped++;
            }
        }

        return new DatasetLoadResult(getSourceId(), getDisplayName(), properties, loaded, skipped, null);
    }

    // Column separator of the source file; loaders for tab-separated files override this.
    protected char delimiter() {
        return ',';
    }

    // Whether "..." quoting (with embedded delimiters/newlines) applies. True for the
    // CSV datasets; the plain tab-separated state files turn it off.
    protected boolean quotedFields() {
        return true;
    }

    private static Map<String, Integer> buildHeaderIndex(List<String> header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            index.put(header.get(i).trim(), i);
        }
        return index;
    }

    protected static String cell(Map<String, Integer> headerIndex, List<String> row, String column) {
        Integer idx = headerIndex.get(column);
        if (idx == null || idx >= row.size()) {
            return "";
        }
        String value = row.get(idx);
        return value == null ? "" : value.trim();
    }

    // Same as cell(), but also treats common missing-value sentinels used across
    // these datasets ("Not provided", "null", "N/A") as blank.
    protected static String cleaned(Map<String, Integer> headerIndex, List<String> row, String column) {
        String value = cell(headerIndex, row, column);
        return isMissingSentinel(value) ? "" : value;
    }

    protected static boolean isMissingSentinel(String value) {
        return value == null || value.isBlank()
                || value.equalsIgnoreCase("Not provided")
                || value.equalsIgnoreCase("null")
                || value.equalsIgnoreCase("N/A");
    }

    // Preserves original column order (unlike headerIndex, a HashMap) so the
    // details dialog can list a record's fields the way the source file did.
    protected static Map<String, String> rawRow(List<String> header, List<String> row) {
        Map<String, String> raw = new java.util.LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String value = i < row.size() ? row.get(i) : "";
            raw.put(header.get(i).trim(), value == null ? "" : value.trim());
        }
        return raw;
    }

    protected abstract UnifiedProperty mapRow(Map<String, Integer> headerIndex, List<String> header, List<String> row);
}
