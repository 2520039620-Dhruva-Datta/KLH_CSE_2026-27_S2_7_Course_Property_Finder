package loader;

import model.PropertyRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CorpusLoader {

    private final Path corpusDirectory;

    public CorpusLoader(Path corpusDirectory) {
        this.corpusDirectory = corpusDirectory;
    }

    public boolean corpusDirectoryExists() {
        return Files.isDirectory(corpusDirectory);
    }

    // Loads every .txt file in the corpus directory: filename -> full contents
    public Map<String, String> loadCorpus() throws IOException {

        Map<String, String> documents = new LinkedHashMap<>();

        if (!corpusDirectoryExists()) {
            return documents;
        }

        try (DirectoryStream<Path> files = Files.newDirectoryStream(corpusDirectory, "*.txt")) {
            for (Path file : files) {
                String contents = Files.readString(file, StandardCharsets.UTF_8);
                documents.put(file.getFileName().toString(), contents);
            }
        }

        return documents;
    }

    // Parses every .txt file's rows into property records, using the first line as the header.
    public List<PropertyRecord> loadPropertyRecords() throws IOException {

        List<PropertyRecord> records = new ArrayList<>();

        if (!corpusDirectoryExists()) {
            return records;
        }

        try (DirectoryStream<Path> files = Files.newDirectoryStream(corpusDirectory, "*.txt")) {
            for (Path file : files) {

                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                if (lines.isEmpty()) {
                    continue;
                }

                List<String> headers = parseCsvLine(lines.get(0));

                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.isBlank()) {
                        continue;
                    }

                    List<String> values = parseCsvLine(line);
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int col = 0; col < headers.size(); col++) {
                        String value = col < values.size() ? values.get(col) : "";
                        row.put(headers.get(col), value);
                    }
                    records.add(new PropertyRecord(row));
                }
            }
        }

        return records;
    }

    // Manual CSV-style parser: keeps commas inside quoted fields intact and
    // unescapes "" as a literal quote, since the .txt corpus is CSV-formatted.
    private static List<String> parseCsvLine(String line) {

        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());

        return fields;
    }
}
