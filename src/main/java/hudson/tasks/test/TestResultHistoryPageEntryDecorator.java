package hudson.tasks.test;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.tasks.junit.CaseResult;
import hudson.widgets.HistoryWidget;
import jenkins.model.HistoricalBuild;
import jenkins.widgets.HistoryPageEntryDecorator;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class TestResultHistoryPageEntryDecorator extends HistoryPageEntryDecorator {

    @Override
    public boolean isApplicable(@NonNull HistoryWidget<?, ?> widget, @NonNull HistoricalBuild build) {
        AbstractTestResultAction<?> action = getAction(build);
        return action != null && action.getTotalCount() > 0;
    }

    // The decorator is shared by every entry, so per-build state lives in a Summary rather than on the decorator
    public Summary getSummary(@NonNull HistoricalBuild build) {
        AbstractTestResultAction<?> action = getAction(build);
        return action == null ? null : new Summary(action);
    }

    @CheckForNull
    private static AbstractTestResultAction<?> getAction(HistoricalBuild build) {
        return build instanceof Run<?, ?> run ? run.getAction(AbstractTestResultAction.class) : null;
    }

    public static final class Summary {

        private final int failCount;
        private final int skipCount;
        private final int totalCount;
        private final int regressionCount;
        private final int fixedCount;

        Summary(AbstractTestResultAction<?> action) {
            this.failCount = action.getFailCount();
            this.skipCount = action.getSkipCount();
            this.totalCount = action.getTotalCount();
            this.regressionCount = countRegressions(action);
            this.fixedCount = countFixed(action);
        }

        // Only looks at this build's failures, unlike TestResult#getRegressionCount() which walks every test
        private static int countRegressions(AbstractTestResultAction<?> action) {
            int count = 0;
            for (TestResult test : action.getFailedTests()) {
                if (test instanceof CaseResult caseResult
                        && caseResult.getCondition() == CaseResult.Status.REGRESSION) {
                    count++;
                }
            }
            return count;
        }

        // Walks the previous build's failures rather than this build's passes, as there are far fewer of them
        private static int countFixed(AbstractTestResultAction<?> action) {
            AbstractTestResultAction<?> previous = action.getPreviousResult();
            if (previous == null || !(action.getResult() instanceof TestResult current)) {
                return 0;
            }

            int count = 0;
            for (TestResult test : previous.getFailedTests()) {
                TestResult now = current.findCorrespondingResult(test.getId());
                if (now != null && now.isPassed()) {
                    count++;
                }
            }
            return count;
        }

        public int getFailCount() {
            return failCount;
        }

        public int getSkipCount() {
            return skipCount;
        }

        public int getPassCount() {
            return totalCount - failCount - skipCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getRegressionCount() {
            return regressionCount;
        }

        public int getFixedCount() {
            return fixedCount;
        }
    }
}
