package loader;

import java.nio.file.Path;

// One implementation per dataset schema. Each knows only its own column layout
// and how to map a row into a UnifiedProperty; nothing else in the app needs to
// know that up.txt, Delhi housing, and the rent dataset all look different.
public interface DatasetLoader {

    String getSourceId();

    String getDisplayName();

    DatasetLoadResult load(Path file);
}
