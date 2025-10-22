package hudson.tasks.junit;

import java.util.ArrayList;
import java.util.List;

public class Widget {

    private final String symbol;
    private final String symbolClass;
    private final List<String> lines = new ArrayList<>();

    public Widget(TestResult result) {
        int failCount = result.getFailCount();
        boolean isFailed = failCount > 0;
        int totalCount = result.getTotalCount();

        this.symbol = isFailed
                ? "symbol-close-circle-outline plugin-ionicons-api"
                : "symbol-checkmark-done-outline plugin-ionicons-api";
        this.symbolClass = isFailed ? "jenkins-!-error-color" : "jenkins-!-success-color";

        List<String> counts = new ArrayList<>();

        if (isFailed) {
            lines.add(Messages.Widget_Failed(failCount));
            counts.add(Messages.Widget_Passed(result.getPassCount()));

            long regressions = result.getSuites().stream()
                    .flatMap(e -> e.getCases().stream())
                    .filter(e -> {
                        var previousResult = e.getPreviousResult();
                        if (previousResult == null) {
                            return false;
                        }
                        return e.isPassed();
                    })
                    .count();

            if (regressions > 0) {
                lines.add(Messages.Widget_Regression(regressions));
            }

        } else {
            lines.add(Messages.Widget_AllTestsPassing());
        }

        if (result.getSkipCount() > 0) {
            counts.add(Messages.Widget_Skipped(result.getSkipCount()));
        }

        counts.add(Messages.Widget_Total(totalCount));

        lines.add(String.join(", ", counts));

        lines.add(Messages.Widget_Took(result.getDurationString()));
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSymbolClass() {
        return symbolClass;
    }

    public List<String> getLines() {
        return lines;
    }
}
