package service;

import model.UnifiedProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// "Compare Exact Algorithms" runs KMP, Z and Rabin-Karp against the exact same
// pattern/data and reports whether all three genuinely agree.
public class ExactComparisonResult {

    public final SearchResult kmpResult;
    public final SearchResult zResult;
    public final SearchResult rabinKarpResult;
    public final boolean identical;

    public ExactComparisonResult(SearchResult kmpResult, SearchResult zResult, SearchResult rabinKarpResult) {
        this.kmpResult = kmpResult;
        this.zResult = zResult;
        this.rabinKarpResult = rabinKarpResult;
        this.identical = kmpResult.occurrences == zResult.occurrences
                && kmpResult.occurrences == rabinKarpResult.occurrences
                && sameProperties(kmpResult.matchingProperties, zResult.matchingProperties)
                && sameProperties(kmpResult.matchingProperties, rabinKarpResult.matchingProperties);
    }

    public List<SearchResult> asList() {
        return List.of(kmpResult, zResult, rabinKarpResult);
    }

    // All three algorithms walk the same list of UnifiedProperty instances, so a
    // matching property is the same object reference regardless of which
    // algorithm found it -- identity equality via a set is sufficient.
    private static boolean sameProperties(List<UnifiedProperty> a, List<UnifiedProperty> b) {
        if (a.size() != b.size()) {
            return false;
        }
        Set<UnifiedProperty> setA = new HashSet<>(a);
        Set<UnifiedProperty> setB = new HashSet<>(b);
        return setA.equals(setB);
    }
}
