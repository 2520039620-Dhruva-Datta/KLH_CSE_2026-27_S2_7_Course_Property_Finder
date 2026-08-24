package utils;

import java.util.List;

public class TableFormatter {

    public static class Column {
        final String header;
        final int width;
        final boolean alignRight;

        public Column(String header, int width) {
            this(header, width, false);
        }

        public Column(String header, int width, boolean alignRight) {
            this.header = header;
            this.width = width;
            this.alignRight = alignRight;
        }
    }

    public static void printTable(List<Column> columns, List<String[]> rows) {
        printBorder(columns);
        printRow(columns, headerValues(columns));
        printBorder(columns);
        for (String[] row : rows) {
            printRow(columns, row);
        }
        printBorder(columns);
    }

    private static String[] headerValues(List<Column> columns) {
        String[] headers = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            headers[i] = columns.get(i).header;
        }
        return headers;
    }

    private static void printBorder(List<Column> columns) {
        StringBuilder line = new StringBuilder("+");
        for (Column column : columns) {
            line.append("-".repeat(column.width + 2)).append("+");
        }
        System.out.println(line);
    }

    private static void printRow(List<Column> columns, String[] values) {
        StringBuilder line = new StringBuilder("|");
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            String value = (i < values.length && values[i] != null) ? values[i] : "";
            line.append(" ").append(fit(value, column)).append(" |");
        }
        System.out.println(line);
    }

    // Truncates with "..." if too long, otherwise pads to the column width
    // (right-aligned for numeric-style columns, left-aligned otherwise).
    private static String fit(String value, Column column) {
        if (value.length() > column.width) {
            return column.width <= 3
                    ? value.substring(0, column.width)
                    : value.substring(0, column.width - 3) + "...";
        }
        String padding = " ".repeat(column.width - value.length());
        return column.alignRight ? padding + value : value + padding;
    }
}
