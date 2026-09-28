package loader;

import model.UnifiedProperty;
import utils.PriceFormatter;

import java.util.List;
import java.util.Map;

// Indian_housing_Delhi_data.txt: house_type,house_size,location,city,latitude,
// longitude,price,currency,numBathrooms,numBalconies,isNegotiable,priceSqFt,
// verificationDate,description,SecurityDeposit,Status. The presence of a
// SecurityDeposit column and monthly-scale price values marks these as rentals.
public class DelhiHousingDatasetLoader extends AbstractCsvDatasetLoader {

    @Override
    public String getSourceId() {
        return "delhi";
    }

    @Override
    public String getDisplayName() {
        return "Delhi Housing";
    }

    @Override
    protected UnifiedProperty mapRow(Map<String, Integer> h, List<String> header, List<String> row) {

        String houseType = cleaned(h, row, "house_type");
        String location = cleaned(h, row, "location");
        String city = cleaned(h, row, "city");

        if (houseType.isEmpty() && location.isEmpty() && city.isEmpty()) {
            return null;
        }

        String priceRaw = cell(h, row, "price");
        String currency = cleaned(h, row, "currency");
        String priceDisplay = (currency.isEmpty() || currency.equalsIgnoreCase("INR"))
                ? PriceFormatter.formatDisplay(priceRaw)
                : (priceRaw.isBlank() ? "N/A" : priceRaw.trim() + " " + currency);

        return new UnifiedProperty(
                getSourceId(),
                getDisplayName(),
                houseType,
                location,
                city,
                "",
                houseType,
                "",
                "Rent",
                cleaned(h, row, "house_size"),
                "",
                priceDisplay,
                PriceFormatter.parseNumeric(priceRaw),
                cleaned(h, row, "description"),
                rawRow(header, row)
        );
    }
}
