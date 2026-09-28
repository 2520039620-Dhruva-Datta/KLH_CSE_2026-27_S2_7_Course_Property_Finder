package model;

import java.util.Locale;
import java.util.Map;

// Common shape every dataset loader maps its own schema into, so the GUI/search
// layer can treat properties from all sources uniformly while still keeping each
// record's original fields available (rawFields) for the details view.
public class UnifiedProperty {

    private final String sourceId;
    private final String sourceDisplayName;
    private final String title;
    private final String locality;
    private final String city;
    private final String state;
    private final String propertyType;
    private final String configuration;
    private final String purpose;
    private final String area;
    private final String furnishing;
    private final String priceDisplay;
    private final Double numericPrice;
    private final String description;
    private final Map<String, String> rawFields;

    // Built only from location-relevant fields (locality/city/state), never from
    // the description, so a property can't match a search purely because a place
    // name happens to appear incidentally in a long free-text description.
    private final String locationSearchTextLower;

    public UnifiedProperty(String sourceId, String sourceDisplayName, String title, String locality,
                            String city, String state, String propertyType, String configuration,
                            String purpose, String area, String furnishing, String priceDisplay,
                            Double numericPrice, String description, Map<String, String> rawFields) {
        this.sourceId = sourceId;
        this.sourceDisplayName = sourceDisplayName;
        this.title = nonBlank(title);
        this.locality = nonBlank(locality);
        this.city = nonBlank(city);
        this.state = nonBlank(state);
        this.propertyType = nonBlank(propertyType);
        this.configuration = nonBlank(configuration);
        this.purpose = nonBlank(purpose);
        this.area = nonBlank(area);
        this.furnishing = nonBlank(furnishing);
        this.priceDisplay = nonBlank(priceDisplay);
        this.numericPrice = numericPrice;
        this.description = description == null ? "" : description;
        this.rawFields = rawFields;
        this.locationSearchTextLower = String.join(" | ", this.locality, this.city, this.state)
                .toLowerCase(Locale.ROOT);
    }

    private static String nonBlank(String value) {
        return (value == null || value.isBlank()) ? "N/A" : value.trim();
    }

    public String getSourceId() { return sourceId; }
    public String getSourceDisplayName() { return sourceDisplayName; }
    public String getTitle() { return title; }
    public String getLocality() { return locality; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPropertyType() { return propertyType; }
    public String getConfiguration() { return configuration; }
    public String getPurpose() { return purpose; }
    public String getArea() { return area; }
    public String getFurnishing() { return furnishing; }
    public String getPriceDisplay() { return priceDisplay; }
    public Double getNumericPrice() { return numericPrice; }
    public String getDescription() { return description; }
    public Map<String, String> getRawFields() { return rawFields; }
    public String getLocationSearchTextLower() { return locationSearchTextLower; }
}
