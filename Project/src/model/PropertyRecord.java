package model;

import java.util.Locale;
import java.util.Map;

public class PropertyRecord {

    private final Map<String, String> fields;
    private final String searchableTextLower;

    public PropertyRecord(Map<String, String> fields) {
        this.fields = fields;
        // All field values concatenated once, so KMP runs against a single string per property.
        this.searchableTextLower = String.join(" | ", fields.values()).toLowerCase(Locale.ROOT);
    }

    public String get(String column) {
        String value = fields.getOrDefault(column, "");
        if (value.isBlank() || value.equalsIgnoreCase("Not provided")) {
            return "N/A";
        }
        return value;
    }

    public String getSearchableTextLower() {
        return searchableTextLower;
    }
}
