package loader;

import model.UnifiedProperty;

import java.util.List;

// Outcome of loading one dataset file: what was produced, plus enough diagnostics
// (loaded/skipped counts, or a fatal error) for the caller to report data quality
// without the whole application failing over one bad or missing file.
public class DatasetLoadResult {

    public final String sourceId;
    public final String displayName;
    public final List<UnifiedProperty> properties;
    public final int recordsLoaded;
    public final int recordsSkipped;
    public final String fatalError; // non-null only if the whole file could not be read at all

    public DatasetLoadResult(String sourceId, String displayName, List<UnifiedProperty> properties,
                              int recordsLoaded, int recordsSkipped, String fatalError) {
        this.sourceId = sourceId;
        this.displayName = displayName;
        this.properties = properties;
        this.recordsLoaded = recordsLoaded;
        this.recordsSkipped = recordsSkipped;
        this.fatalError = fatalError;
    }

    public boolean isSuccessful() {
        return fatalError == null;
    }
}
