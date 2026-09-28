package service;

import model.UnifiedProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// Performance layer, not a matching algorithm: splits the combined "All Sources"
// property list into equal contiguous chunks and runs the already-chosen exact
// algorithm on each chunk concurrently. Chunks are disjoint read-only views of
// the same list, so threads never share mutable state (no races), and results
// are merged strictly in chunk order, so the output is identical -- including
// order -- to a plain sequential scan, with no possibility of duplicates.
//
// Chunking by count rather than by source is deliberate: the sources are very
// uneven (one holds ~84% of all rows), so one-thread-per-source would leave a
// single thread doing nearly all the work and gain nothing.
public class ParallelDatasetSearch {

    // Below this many properties, thread/executor overhead would cost more than
    // the sequential scan it replaces, so callers should just run in-line instead.
    public static final int MIN_PROPERTIES_FOR_PARALLEL = 5000;

    private final SearchService searchService;

    public ParallelDatasetSearch(SearchService searchService) {
        this.searchService = searchService;
    }

    public SearchResult run(List<UnifiedProperty> properties, String pattern, Algorithm algorithm) {
        int threadCount = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), properties.size()));
        int chunkSize = (properties.size() + threadCount - 1) / threadCount;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        long startNanos = System.nanoTime();
        try {
            List<Future<SearchResult>> futures = new ArrayList<>();
            for (int from = 0; from < properties.size(); from += chunkSize) {
                List<UnifiedProperty> chunk = properties.subList(from, Math.min(from + chunkSize, properties.size()));
                futures.add(executor.submit(() -> searchService.search(chunk, pattern, algorithm)));
            }

            // Collected in submission order (not completion order) so the merge is deterministic.
            List<UnifiedProperty> mergedMatches = new ArrayList<>();
            int totalOccurrences = 0;
            for (Future<SearchResult> future : futures) {
                SearchResult partial = future.get();
                mergedMatches.addAll(partial.matchingProperties);
                totalOccurrences += partial.occurrences;
            }
            long elapsedNanos = System.nanoTime() - startNanos;

            String note = "Parallel Dataset Search: " + futures.size() + " chunk(s) on " + threadCount + " thread(s)";
            return new SearchResult(pattern, algorithm, totalOccurrences, mergedMatches, elapsedNanos, note);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Parallel search interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Parallel search failed", e.getCause());
        } finally {
            executor.shutdown();
        }
    }
}
