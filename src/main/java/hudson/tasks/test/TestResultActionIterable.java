package hudson.tasks.test;

import edu.hm.hafner.echarts.Build;
import edu.hm.hafner.echarts.BuildResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Run;
import hudson.tasks.junit.TestResultAction;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class TestResultActionIterable implements Iterable {
    private TestResultAction latestAction;
    
    /**
     * Creates a new iterator that selects action of the given type {@code actionType}.
     *
     * @param baseline
     *         the baseline to start from
     */
    public TestResultActionIterable(final TestResultAction baseline) {
        this.latestAction = baseline;
    }

    public static TestResultAction getBuildActionFromHistoryStartingFrom(
            @Nullable final Run<?, ?> baseline) {
        for (Run<?, ?> run = baseline; run != null; run = run.getPreviousBuild()) {
            TestResultAction action = run.getAction(TestResultAction.class);
            if (action != null) {
                return action;
            }
        }

        return null;
    }

    @NonNull
    @Override
    public Iterator<BuildResult<TestResultAction>> iterator() {
        if (latestAction == null) {
            return new TestResultActionIterator(null);
        }
        return new TestResultActionIterator(latestAction.run);
    }

    private static class TestResultActionIterator implements Iterator<BuildResult<TestResultAction>> {
        private Run<?, ?> cursor;
        private Run<?, ?> initialValue;

        /**
         * Creates a new iterator starting from the baseline.
         *
         * @param baseline
         *         the run to start from
         */
        TestResultActionIterator(final Run<?, ?> baseline) {
            initialValue = baseline;
            
        }

        @Override
        public boolean hasNext() {
            if (initialValue != null) {
                return initialValue.getAction(TestResultAction.class) != null;
            }
            
            if (cursor == null) {
                return false;
            }
            
            Run<?, ?> previousBuild = cursor.getPreviousBuild();
            if (previousBuild != null) {
                return previousBuild.getAction(TestResultAction.class) != null;
            }
            return false;
        }

        @Override
        public BuildResult<TestResultAction> next() {
            if (initialValue == null && cursor == null) {
                throw new NoSuchElementException(
                        "There is no action available anymore. Use hasNext() before calling next().");
            }
            Run<?, ?> run;
            if (initialValue != null) {
                run = initialValue;
                initialValue = null;
            } else {
                run = cursor.getPreviousBuild();
            }

            TestResultAction buildAction = getBuildActionFromHistoryStartingFrom(run);
            if (buildAction != null) {
                cursor = buildAction.run;

                int buildTimeInSeconds = (int) (cursor.getTimeInMillis() / 1000);
                Build build = new Build(cursor.getNumber(), cursor.getDisplayName(), buildTimeInSeconds);
                return new BuildResult<>(build, buildAction);
            }

            throw new NoSuchElementException("No more runs with a test result available: " + cursor);
        }
    }

}
