package loader;

import model.UnifiedProperty;
import utils.PriceFormatter;

import java.util.List;
import java.util.Map;

public class HyderabadDatasetLoader extends AbstractCsvDatasetLoader {

    @Override
    public String getSourceId() {
        return "hyderabad";
    }

    @Override
    public String getDisplayName() {
        return "Hyderabad";
    }

    @Override
    protected UnifiedProperty mapRow(Map<String, Integer> h, List<String> header, List<String> row) {

        String title = cleaned(h, row, "ListingTitle");
        String locality = cleaned(h, row, "Locality");

        if (title.isEmpty() && locality.isEmpty()) {
            return null; // effectively an empty/malformed record
        }

        String area = cleaned(h, row, "BuiltUpArea");
        if (area.isEmpty()) {
            area = cleaned(h, row, "CarpetArea");
        }
        if (area.isEmpty()) {
            area = cleaned(h, row, "PlotArea");
        }

        String priceRaw = cell(h, row, "PriceRent");
        String amenities = cleaned(h, row, "Amenities");
        String facing = cleaned(h, row, "Facing");
        String status = cleaned(h, row, "ConstructionStatus");
        String description = String.join("; ",
                        java.util.stream.Stream.of(
                                        status.isEmpty() ? null : "Status: " + status,
                                        facing.isEmpty() ? null : "Facing: " + facing,
                                        amenities.isEmpty() ? null : "Amenities: " + amenities)
                                .filter(java.util.Objects::nonNull)
                                .toArray(String[]::new));

        return new UnifiedProperty(
                getSourceId(),
                getDisplayName(),
                title,
                locality,
                "Hyderabad",
                extractStateFromLocation(cleaned(h, row, "ProjectFullLocation")),
                cleaned(h, row, "PropertyType"),
                cleaned(h, row, "Configuration"),
                cleaned(h, row, "Purpose"),
                area,
                cleaned(h, row, "Furnishing"),
                PriceFormatter.formatDisplay(priceRaw),
                PriceFormatter.parseNumeric(priceRaw),
                description,
                rawRow(header, row)
        );
    }

    // ProjectFullLocation looks like "...Bachupally, Hyderabad - North, Andhra Pradesh";
    // the state, when present at all, is the last comma-separated segment.
    private static String extractStateFromLocation(String fullLocation) {
        if (fullLocation.isEmpty()) {
            return "";
        }
        String[] parts = fullLocation.split(",");
        return parts[parts.length - 1].trim();
    }
}
