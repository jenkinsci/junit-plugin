package hudson.tasks.junit;

import java.util.ArrayList;
import java.util.List;

public class Widget {

    private final String symbol;
    private final List<Line> lines = new ArrayList<>();

    public Widget(TestResult result) {
        int failCount = result.getFailCount();
        boolean isFailed = failCount > 0;
        int totalCount = result.getTotalCount();

        this.symbol = isFailed ? "symbol-status-red" : "symbol-status-blue";

        List<String> counts = new ArrayList<>();

        List<Line.Segment> changes = new ArrayList<>();

        if (isFailed) {
            lines.add(new Line(Messages.Widget_Failed(failCount)));
            counts.add(Messages.Widget_Passed(result.getPassCount()));

            long regressions = result.getRegressionCount();

            if (regressions > 0) {
                changes.add(new Line.Segment(
                        Messages.Widget_Regression(regressions), "symbol-trending-down-outline plugin-ionicons-api"));
            }

        } else {
            lines.add(new Line(Messages.Widget_AllTestsPassing()));
        }

        long fixed = result.getFixedCount();

        if (fixed > 0) {
            changes.add(
                    new Line.Segment(Messages.Widget_Fixed(fixed), "symbol-trending-up-outline plugin-ionicons-api"));
        }

        if (!changes.isEmpty()) {
            lines.add(new Line(changes));
        }

        if (result.getSkipCount() > 0) {
            counts.add(Messages.Widget_Skipped(result.getSkipCount()));
        }

        counts.add(Messages.Widget_Total(totalCount));

        lines.add(new Line(String.join(", ", counts)));

        lines.add(new Line(Messages.Widget_Took(result.getDurationString())));
    }

    public String getSymbol() {
        return symbol;
    }

    public List<Line> getLines() {
        return lines;
    }

    public static class Line {

        private final List<Segment> segments;

        Line(String text) {
            this(List.of(new Segment(text, null)));
        }

        Line(List<Segment> segments) {
            this.segments = segments;
        }

        public List<Segment> getSegments() {
            return segments;
        }

        public static class Segment {

            private final String text;
            private final String icon;

            Segment(String text, String icon) {
                this.text = text;
                this.icon = icon;
            }

            public String getText() {
                return text;
            }

            public String getIcon() {
                return icon;
            }
        }
    }
}
