package loader;

import model.UnifiedProperty;
import utils.PriceFormatter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// One tab-separated file per state / union territory (Goa.txt, Tamil_Nadu.txt, ...),
// with the columns: No., Source, Property, Location, City / State, Type / Config,
// Area, Price / Rent, Furnishing, Bathrooms, Tennants, Listing Type -- plus an
// optional trailing Description column in the files that received merged data.
//
// The older Delhi housing, Hyderabad and Indian house rent datasets were merged
// into these files (see tools.MergeIntoStateFiles). Each row keeps the name of the
// dataset it came from in its Source column, so a state file can hold rows from
// several datasets and every row still shows where it originated.
public class StateListingsDatasetLoader extends AbstractCsvDatasetLoader {

    // State / UT names exactly as they appear in the files' City / State column.
    // The file name is derived from the name (see fileNameFor).
    public static final List<String> STATE_NAMES = List.of(
            "Andaman & Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
            "Chandigarh", "Chhattisgarh", "Dadra & Nagar Haveli and Daman & Diu", "Delhi", "Goa",
            "Gujarat", "Haryana", "Himachal Pradesh", "Jammu & Kashmir", "Jharkhand", "Karnataka",
            "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur",
            "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Puducherry", "Punjab", "Rajasthan",
            "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand",
            "West Bengal"
    );

    // Rent listings whose city column was blank or unreadable, so they could not be
    // assigned to any state file. Kept as their own small dataset rather than guessed.
    public static final String UNASSIGNED_RENT_SOURCE_ID = "rent-unassigned";
    public static final String UNASSIGNED_RENT_LABEL = "Indian House Rent (unassigned)";
    public static final String UNASSIGNED_RENT_FILE = "Indian_House_Rent_Unassigned.txt";

    private final String sourceId;
    private final String displayName;
    private final String fileName;

    private StateListingsDatasetLoader(String sourceId, String displayName, String fileName) {
        this.sourceId = sourceId;
        this.displayName = displayName;
        this.fileName = fileName;
    }

    public static StateListingsDatasetLoader forState(String stateName) {
        String fileName = fileNameFor(stateName);
        return new StateListingsDatasetLoader(sourceIdFor(stateName), stateName + " Properties (" + fileName + ")", fileName);
    }

    public static StateListingsDatasetLoader unassignedRent() {
        return new StateListingsDatasetLoader(UNASSIGNED_RENT_SOURCE_ID, UNASSIGNED_RENT_LABEL, UNASSIGNED_RENT_FILE);
    }

    // "Andaman & Nicobar Islands" -> "Andaman_and_Nicobar_Islands.txt"
    public static String fileNameFor(String stateName) {
        return stateName.replace("&", "and").replaceAll("\\s+", "_") + ".txt";
    }

    public static String sourceIdFor(String stateName) {
        return "state:" + stateName;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public String getSourceId() {
        return sourceId;
    }

    // Matches the "Source" column of the file's own rows, e.g. "Goa Properties (Goa.txt)".
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    protected char delimiter() {
        return '\t';
    }

    // Plain TSV: the merged rent rows contain stray '"' characters that are data, not quoting.
    @Override
    protected boolean quotedFields() {
        return false;
    }

    @Override
    protected UnifiedProperty mapRow(Map<String, Integer> h, List<String> header, List<String> row) {

        String title = cleaned(h, row, "Property");
        String locality = cleaned(h, row, "Location");
        String[] cityState = splitPair(cleaned(h, row, "City / State"));
        String[] typeConfig = splitPair(cleaned(h, row, "Type / Config"));

        if (title.isEmpty() && locality.isEmpty() && cityState[0].isEmpty()) {
            return null;
        }

        String bathrooms = cleaned(h, row, "Bathrooms");
        String tenants = cleaned(h, row, "Tennants"); // sic: the source files misspell this header
        String facts = Stream.of(
                        bathrooms.isEmpty() ? null : "Bathrooms: " + bathrooms,
                        tenants.isEmpty() ? null : "Tenants Preferred: " + tenants)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        String details = cleaned(h, row, "Description");
        String description = facts.isEmpty() ? details : (details.isEmpty() ? facts : facts + "\n" + details);

        String priceRaw = cell(h, row, "Price / Rent");
        String source = cleaned(h, row, "Source");

        return new UnifiedProperty(
                sourceId,
                source.isEmpty() ? displayName : source,
                title,
                locality,
                cityState[0],
                cityState[1],
                typeConfig[0],
                typeConfig[1],
                cleaned(h, row, "Listing Type"),
                cleaned(h, row, "Area"),
                cleaned(h, row, "Furnishing"),
                PriceFormatter.formatDisplay(priceRaw),
                PriceFormatter.parseNumeric(priceRaw),
                description,
                rawRow(header, row)
        );
    }

    // Splits a "left / right" cell (City / State, Type / Config) into its two
    // halves; a cell with no " / " keeps everything in the left half. A half
    // written as N/A (merged rows with no value for it) comes back blank.
    private static String[] splitPair(String value) {
        String[] parts = value.split(" / ", 2);
        return new String[] { blankIfMissing(parts[0]), parts.length > 1 ? blankIfMissing(parts[1]) : "" };
    }

    private static String blankIfMissing(String part) {
        String trimmed = part.trim();
        return isMissingSentinel(trimmed) ? "" : trimmed;
    }
}
