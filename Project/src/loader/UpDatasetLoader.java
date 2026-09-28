package loader;

import model.UnifiedProperty;
import utils.PriceFormatter;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// up.txt: despite the filename, records span many states (plot_type,city,price,
// Unit,Rupee_per_area,Area,state). No locality/title/purpose columns exist, so
// those are derived deterministically from plot_type or set to a fixed constant
// ("Sale" -- every row is a priced plot/property listing, never a rent figure).
public class UpDatasetLoader extends AbstractCsvDatasetLoader {

    private static final Pattern BHK_PREFIX =
            Pattern.compile("^(\\d+\\s*(?:BHK|RK))\\s+(.*)$", Pattern.CASE_INSENSITIVE);

    @Override
    public String getSourceId() {
        return "up";
    }

    @Override
    public String getDisplayName() {
        return "All-India Plots (up.txt)";
    }

    @Override
    protected UnifiedProperty mapRow(Map<String, Integer> h, List<String> header, List<String> row) {

        String plotType = cleaned(h, row, "plot_type");
        String city = cleaned(h, row, "city");
        String priceRaw = cell(h, row, "price");

        if (plotType.isEmpty() && city.isEmpty() && priceRaw.isBlank()) {
            return null;
        }

        String propertyType = plotType;
        String configuration = "";
        Matcher m = BHK_PREFIX.matcher(plotType);
        if (m.matches()) {
            configuration = m.group(1).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            propertyType = m.group(2).trim();
        }

        String rate = cleaned(h, row, "Rupee_per_area");
        String areaValue = cleaned(h, row, "Area");
        String description = rate.isEmpty() ? "" : ("Rate: " + rate);

        return new UnifiedProperty(
                getSourceId(),
                getDisplayName(),
                plotType.isEmpty() ? "Property in " + city : plotType,
                "",
                city,
                titleCase(cleaned(h, row, "state")),
                propertyType,
                configuration,
                "Sale",
                areaValue,
                "",
                PriceFormatter.formatDisplay(priceRaw),
                PriceFormatter.parseNumeric(priceRaw),
                description,
                rawRow(header, row)
        );
    }

    private static String titleCase(String value) {
        if (value.isEmpty()) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        for (String word : value.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }
}
