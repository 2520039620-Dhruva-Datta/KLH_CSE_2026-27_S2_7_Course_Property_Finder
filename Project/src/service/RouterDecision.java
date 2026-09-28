package service;

// What the Adaptive Algorithm Selection Engine decided for one search, plus
// the reasoning the GUI displays so "Auto" never behaves like a silent black box.
public class RouterDecision {

    public final Algorithm algorithm;
    public final String reason;
    public final String technique;
    public final String complexity;
    public final SearchResult result;
    public final boolean usedParallel;

    public RouterDecision(Algorithm algorithm, String reason, String technique, String complexity,
                           SearchResult result, boolean usedParallel) {
        this.algorithm = algorithm;
        this.reason = reason;
        this.technique = technique;
        this.complexity = complexity;
        this.result = result;
        this.usedParallel = usedParallel;
    }

    public String explanationText() {
        StringBuilder sb = new StringBuilder();
        sb.append("AUTO SELECTED: ").append(algorithm.label).append('\n');
        sb.append("Reason: ").append(reason).append('\n');
        sb.append("Technique: ").append(technique).append('\n');
        sb.append("Complexity: ").append(complexity);
        if (usedParallel) {
            sb.append('\n').append("Parallel Dataset Search: enabled (All Sources)");
        }
        return sb.toString();
    }
}
