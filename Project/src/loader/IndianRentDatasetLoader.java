package loader;

import model.UnifiedProperty;
import utils.PriceFormatter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Indian_House_Rent_Dataset.txt: Posted On,BHK,Size,Floor,Area Type,Area
// Locality,City,Furnishing Status,Tenant Preferred,Bathroom,Point of Contact,
// Rent. Known-dirty (stray characters in City/Tenant Preferred, missing
// fields); left as-is rather than "corrected", since KMP/Z substring search
// still finds the real place name inside a noisy city value.
public class IndianRentDatasetLoader extends AbstractCsvDatasetLoader {

    @Override
    public String getSourceId() {
        return "rent";
    }

    @Override
    public String getDisplayName() {
        return "Indian House Rent";
    }

    @Override
    protected UnifiedProperty mapRow(Map<String, Integer> h, List<String> header, List<String> row) {

        String locality = cleaned(h, row, "Area Locality");
        String city = cleaned(h, row, "City");
        String rentRaw = cell(h, row, "Rent");

        if (locality.isEmpty() && city.isEmpty() && rentRaw.isBlank()) {
            return null;
        }

        String place = !locality.isEmpty() ? locality : city;
        String title = place.isEmpty() ? "Rental Property" : ("Rental Property in " + place);

        String size = cleaned(h, row, "Size");
        String areaType = cleaned(h, row, "Area Type");
        String area = areaType.isEmpty() ? size : (size.isEmpty() ? areaType : size + " sqft (" + areaType + ")");

        String floor = cleaned(h, row, "Floor");
        String tenant = cleaned(h, row, "Tenant Preferred");
        String contact = cleaned(h, row, "Point of Contact");
        String posted = cleaned(h, row, "Posted On");
        String description = Stream.of(
                        floor.isEmpty() ? null : "Floor: " + floor,
                        tenant.isEmpty() ? null : "Tenant Preferred: " + tenant,
                        contact.isEmpty() ? null : "Contact: " + contact,
                        posted.isEmpty() ? null : "Posted: " + posted)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("; "));

        return new UnifiedProperty(
                getSourceId(),
                getDisplayName(),
                title,
                locality,
                city,
                "",
                "",
                cleaned(h, row, "BHK"),
                "Rent",
                area,
                cleaned(h, row, "Furnishing Status"),
                PriceFormatter.formatDisplay(rentRaw),
                PriceFormatter.parseNumeric(rentRaw),
                description,
                rawRow(header, row)
        );
    }
}
