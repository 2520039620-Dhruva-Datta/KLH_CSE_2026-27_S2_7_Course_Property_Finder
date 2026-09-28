package tools;

import loader.DatasetLoadResult;
import loader.DatasetLoader;
import loader.DelhiHousingDatasetLoader;
import loader.HyderabadDatasetLoader;
import loader.IndianRentDatasetLoader;
import loader.StateListingsDatasetLoader;
import model.UnifiedProperty;
import utils.PriceFormatter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Data preparation, not part of the GUI: folds the older datasets into the
// per-state listing files in data/.
//
//   Indian_housing_Delhi_data.txt   -> Delhi.txt        (every row is a Delhi listing)
//   hyderabad_recent_properties.txt -> Telangana.txt    (every row is a Hyderabad listing)
//   Indian_House_Rent_Dataset.txt   -> routed row by row by the city it names:
//        Kolkata -> West_Bengal.txt, Mumbai -> Maharashtra.txt, Bangalore -> Karnataka.txt,
//        Chennai -> Tamil_Nadu.txt, Hyderabad -> Telangana.txt, Delhi -> Delhi.txt.
//      Rows whose city is blank or unreadable are NOT guessed at; they are written to
//      Indian_House_Rent_Unassigned.txt instead.
//
// The three source files are only read, never modified. Every merged row keeps the name
// of its original dataset in the Source column, and the original columns that have no
// home in the state layout (descriptions, floor, contact, ...) go into a trailing
// Description column. Safe to re-run: each state file is rebuilt from its own rows plus
// freshly merged ones, and nothing is written if any source file cannot be read.
//
// Usage (after compiling):  java -cp out tools.MergeIntoStateFiles [dataDir]
public class MergeIntoStateFiles {

    private static final String[] COLUMNS = {
            "No.", "Source", "Property", "Location", "City / State", "Type / Config", "Area",
            "Price / Rent", "Furnishing", "Bathrooms", "Tennants", "Listing Type", "Description"
    };
    private static final int SOURCE_COLUMN = 1;
    private static final int OWN_COLUMN_COUNT = 12; // columns present in the original state files
    private static final String NA = "N/A";

    // Rent-dataset city (matched inside the dirty City text) -> canonical city and its state.
    private static final String[][] RENT_CITIES = {
            { "mumbai", "Mumbai", "Maharashtra" },
            { "bangalore", "Bangalore", "Karnataka" },
            { "bengaluru", "Bangalore", "Karnataka" },
            { "chennai", "Chennai", "Tamil Nadu" },
            { "kolkata", "Kolkata", "West Bengal" },
            { "hyderabad", "Hyderabad", "Telangana" },
            { "delhi", "Delhi", "Delhi" }
    };

    private static final Pattern PLAIN_AMOUNT = Pattern.compile(Pattern.quote(PriceFormatter.RUPEE) + "[0-9,]+");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("(\\d+)(?:\\.0+)?");

    // state name -> merged rows to append (cells 1..12, "No." is assigned when written)
    private final Map<String, List<String[]>> mergedByState = new LinkedHashMap<>();
    private final List<String[]> unassignedRent = new ArrayList<>();
    private final List<String> mergedSourceLabels = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        Path dataDir = Path.of(args.length > 0 ? args[0] : "data");
        try {
            new MergeIntoStateFiles().run(dataDir);
        } catch (IllegalStateException e) {
            System.err.println("Merge aborted, no files were changed: " + e.getMessage());
            System.exit(1);
        }
    }

    private void run(Path dataDir) throws IOException {
        // Everything is read and converted before anything is written.
        collect(new DelhiHousingDatasetLoader(), "Indian_housing_Delhi_data.txt", dataDir, this::delhiRow);
        collect(new HyderabadDatasetLoader(), "hyderabad_recent_properties.txt", dataDir, this::hyderabadRow);
        collect(new IndianRentDatasetLoader(), "Indian_House_Rent_Dataset.txt", dataDir, this::rentRow);

        for (Map.Entry<String, List<String[]>> entry : mergedByState.entrySet()) {
            String stateName = entry.getKey();
            Path file = dataDir.resolve(StateListingsDatasetLoader.fileNameFor(stateName));
            List<String[]> ownRows = readOwnRows(file, StateListingsDatasetLoader.forState(stateName).getDisplayName());
            List<String[]> all = new ArrayList<>(ownRows);
            all.addAll(entry.getValue());
            writeFile(file, all);
            System.out.printf("%-20s %,6d rows = %,d original + %,d merged%n",
                    stateName, all.size(), ownRows.size(), entry.getValue().size());
        }

        writeFile(dataDir.resolve(StateListingsDatasetLoader.UNASSIGNED_RENT_FILE), unassignedRent);
        System.out.printf("%-20s %,6d rows (rent listings with no readable city)%n",
                StateListingsDatasetLoader.UNASSIGNED_RENT_FILE, unassignedRent.size());
    }

    private interface RowConverter {
        void convert(String sourceLabel, UnifiedProperty property);
    }

    private void collect(DatasetLoader loader, String fileName, Path dataDir, RowConverter converter) {
        DatasetLoadResult result = loader.load(dataDir.resolve(fileName));
        if (!result.isSuccessful()) {
            throw new IllegalStateException(fileName + ": " + result.fatalError);
        }
        String sourceLabel = loader.getDisplayName() + " (" + fileName + ")";
        mergedSourceLabels.add(sourceLabel);
        for (UnifiedProperty property : result.properties) {
            converter.convert(sourceLabel, property);
        }
        System.out.printf("read %,d rows from %s%n", result.properties.size(), fileName);
    }

    // ---- per-dataset conversion into the state-file columns ----

    private void delhiRow(String source, UnifiedProperty p) {
        Map<String, String> raw = p.getRawFields();
        add("Delhi", cells(source, p, "Delhi", "Delhi", p.getConfiguration(),
                value(raw.get("numBathrooms")), NA, p.getDescription()));
    }

    private void hyderabadRow(String source, UnifiedProperty p) {
        Map<String, String> raw = p.getRawFields();
        add("Telangana", cells(source, p, "Hyderabad", "Telangana", p.getConfiguration(),
                value(raw.get("Bathrooms")), NA, p.getDescription()));
    }

    private void rentRow(String source, UnifiedProperty p) {
        Map<String, String> raw = p.getRawFields();
        String extra = Stream.of(labelled("Floor", raw.get("Floor")),
                        labelled("Contact", raw.get("Point of Contact")),
                        labelled("Posted", raw.get("Posted On")))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        String bathrooms = value(raw.get("Bathroom"));
        String tenants = value(raw.get("Tenant Preferred"));

        String cityText = p.getCity().toLowerCase();
        for (String[] city : RENT_CITIES) {
            if (cityText.contains(city[0])) {
                add(city[2], cells(source, p, city[1], city[2], bhk(p.getConfiguration()), bathrooms, tenants, extra));
                return;
            }
        }
        unassignedRent.add(cells(source, p, NA, NA, bhk(p.getConfiguration()), bathrooms, tenants, extra));
    }

    private void add(String stateName, String[] row) {
        mergedByState.computeIfAbsent(stateName, name -> new ArrayList<>()).add(row);
    }

    private static String[] cells(String source, UnifiedProperty p, String city, String state, String config,
                                  String bathrooms, String tenants, String description) {
        return new String[] {
                "", // No. -- assigned when the file is written
                source,
                p.getTitle(),
                p.getLocality(),
                pair(city, state),
                pair(p.getPropertyType(), config),
                p.getArea(),
                priceCell(p),
                p.getFurnishing(),
                bathrooms,
                tenants,
                p.getPurpose(),
                description
        };
    }

    // The loader splits these cells on " / ", so a half must never contain that itself.
    private static String pair(String left, String right) {
        return left.replace(" / ", "/") + " / " + right.replace(" / ", "/");
    }

    // The state files write monthly rent as "₹32,500/month". The older datasets give a
    // bare amount for rent listings; sale prices ("₹1.2 Cr", "₹5,500,000") stay as they were.
    private static String priceCell(UnifiedProperty p) {
        String price = p.getPriceDisplay();
        boolean isRent = "Rent".equalsIgnoreCase(p.getPurpose());
        return isRent && PLAIN_AMOUNT.matcher(price).matches() ? price + "/month" : price;
    }

    // The rent dataset stores BHK as "2.0"; the state files write "2 BHK".
    private static String bhk(String configuration) {
        var matcher = WHOLE_NUMBER.matcher(configuration);
        return matcher.matches() ? matcher.group(1) + " BHK" : configuration;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank() && !value.equalsIgnoreCase("N/A")
                && !value.equalsIgnoreCase("null") && !value.equalsIgnoreCase("Not provided");
    }

    private static String value(String raw) {
        return present(raw) ? raw.trim() : NA;
    }

    private static String labelled(String label, String raw) {
        return present(raw) ? label + ": " + raw.trim() : null;
    }

    // ---- reading and writing state files ----

    // The rows that belong to the state file itself (Source = its own name), padded to
    // the full column count. Rows from previously merged datasets are dropped so a
    // re-run does not duplicate them; any other row would be lost, so that aborts.
    private List<String[]> readOwnRows(Path file, String ownSource) throws IOException {
        if (!Files.exists(file)) {
            throw new IllegalStateException("missing state file " + file);
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).startsWith("No.\tSource\tProperty")) {
            throw new IllegalStateException(file.getFileName() + " does not have the expected header");
        }

        List<String[]> own = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length < OWN_COLUMN_COUNT) {
                throw new IllegalStateException(file.getFileName() + " line " + (i + 1) + " has only "
                        + fields.length + " columns");
            }
            String source = fields[SOURCE_COLUMN];
            if (source.equals(ownSource)) {
                String[] row = new String[COLUMNS.length];
                for (int c = 0; c < row.length; c++) {
                    row[c] = c < fields.length ? fields[c] : "";
                }
                own.add(row);
            } else if (!mergedSourceLabels.contains(source)) {
                throw new IllegalStateException(file.getFileName() + " line " + (i + 1)
                        + " has an unexpected Source \"" + source + "\" and would be dropped");
            }
        }
        return own;
    }

    private static void writeFile(Path file, List<String[]> rows) throws IOException {
        StringBuilder out = new StringBuilder(String.join("\t", COLUMNS)).append('\n');
        int number = 1;
        for (String[] row : rows) {
            row[0] = String.valueOf(number++);
            out.append(Stream.of(row).map(MergeIntoStateFiles::flat).collect(Collectors.joining("\t"))).append('\n');
        }
        // Write beside the target first so a failure never leaves a half-written file behind.
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, out, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    // One row per line, one cell per tab: line breaks and tabs inside a cell become spaces.
    private static String flat(String cell) {
        return cell == null ? "" : cell.replaceAll("[\\t\\r\\n]+", " ").trim();
    }
}
