package service;

import loader.DatasetLoadResult;
import loader.DatasetLoader;
import loader.StateListingsDatasetLoader;
import loader.UpDatasetLoader;
import model.UnifiedProperty;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Loads every known dataset from a data directory and holds the combined result.
// One failed/missing file never prevents the others from loading (section 36).
public class PropertyRepository {

    private final List<DatasetLoadResult> loadResults = new ArrayList<>();
    private final List<UnifiedProperty> allProperties = new ArrayList<>();

    // A loader paired with the file it reads.
    private record DatasetFile(DatasetLoader loader, String fileName) {
    }

    // up.txt, one file per state / UT, and the rent rows that could not be assigned
    // to a state. The Delhi housing, Hyderabad and Indian house rent datasets are
    // deliberately NOT listed: their rows were merged into the state files, so
    // loading the original files as well would count every one of them twice.
    private static List<DatasetFile> knownDatasets() {
        List<DatasetFile> datasets = new ArrayList<>();
        datasets.add(new DatasetFile(new UpDatasetLoader(), "up.txt"));
        for (String stateName : StateListingsDatasetLoader.STATE_NAMES) {
            StateListingsDatasetLoader loader = StateListingsDatasetLoader.forState(stateName);
            datasets.add(new DatasetFile(loader, loader.getFileName()));
        }
        StateListingsDatasetLoader unassignedRent = StateListingsDatasetLoader.unassignedRent();
        datasets.add(new DatasetFile(unassignedRent, unassignedRent.getFileName()));
        return datasets;
    }

    public void loadAll(Path dataDirectory) {
        loadResults.clear();
        allProperties.clear();

        for (DatasetFile dataset : knownDatasets()) {
            DatasetLoader loader = dataset.loader();
            Path file = dataDirectory.resolve(dataset.fileName());
            DatasetLoadResult result;
            try {
                result = loader.load(file);
            } catch (RuntimeException e) {
                result = new DatasetLoadResult(loader.getSourceId(), loader.getDisplayName(),
                        List.of(), 0, 0, "Unexpected error: " + e.getMessage());
            }
            loadResults.add(result);
            if (result.isSuccessful()) {
                allProperties.addAll(result.properties);
            }
        }
    }

    public List<DatasetLoadResult> getLoadResults() {
        return loadResults;
    }

    public List<UnifiedProperty> getAllProperties() {
        return allProperties;
    }

    public List<UnifiedProperty> getPropertiesForSources(java.util.Set<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return allProperties;
        }
        List<UnifiedProperty> filtered = new ArrayList<>();
        for (UnifiedProperty property : allProperties) {
            if (sourceIds.contains(property.getSourceId())) {
                filtered.add(property);
            }
        }
        return filtered;
    }

    public int successfulDatasetCount() {
        return (int) loadResults.stream().filter(DatasetLoadResult::isSuccessful).count();
    }

    public int totalDatasetCount() {
        return loadResults.size();
    }
}
