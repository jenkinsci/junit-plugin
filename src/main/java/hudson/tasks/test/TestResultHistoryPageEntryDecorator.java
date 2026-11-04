package hudson.tasks.test;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.tasks.junit.TestResultAction;
import hudson.widgets.HistoryWidget;
import java.util.Objects;
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

        private final String urlName;
        private final int failCount;
        private final int skipCount;
        private final int totalCount;
        private final int regressionCount;
        private final int fixedCount;

        // Only uses counts stored with the build, as loading every build's full report would make the page slow
        Summary(AbstractTestResultAction<?> action) {
            this.urlName = action.getUrlName();
            this.failCount = action.getFailCount();
            this.skipCount = action.getSkipCount();
            this.totalCount = action.getTotalCount();
            if (action instanceof TestResultAction testResultAction) {
                this.regressionCount = Objects.requireNonNullElse(testResultAction.getRecordedRegressionCount(), 0);
                this.fixedCount = Objects.requireNonNullElse(testResultAction.getRecordedFixedCount(), 0);
            } else {
                this.regressionCount = 0;
                this.fixedCount = 0;
            }
        }

        /**
         * The URL of the test report, relative to the build
         */
        public String getUrlName() {
            return urlName;
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
