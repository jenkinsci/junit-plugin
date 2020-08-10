package hudson.tasks.test;

import edu.hm.hafner.echarts.Build;
import edu.hm.hafner.echarts.BuildResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class TestResultActionIterable implements Iterable<BuildResult<AbstractTestResultAction<?>>> {
    private final AbstractTestResultAction<?> latestAction;
    
    /**
     * Creates a new iterator that selects action of the given type {@code actionType}.
     *
     * @param baseline
     *         the baseline to start from
     */
    public TestResultActionIterable(final AbstractTestResultAction<?> baseline) {
        this.latestAction = baseline;
    }
    
    @NonNull
    @Override
    public Iterator<BuildResult<AbstractTestResultAction<?>>> iterator() {
        if (latestAction == null) {
            return new TestResultActionIterator(null);
        }
        return new TestResultActionIterator(latestAction);
    }

    private static class TestResultActionIterator implements Iterator<BuildResult<AbstractTestResultAction<?>>> {
        private AbstractTestResultAction<?> cursor;
        private AbstractTestResultAction<?> initialValue;

        /**
         * Creates a new iterator starting from the baseline.
         *
         * @param baseline
         *         the run to start from
         */
        TestResultActionIterator(final AbstractTestResultAction<?> baseline) {
            initialValue = baseline;
        }

        @Override
        public boolean hasNext() {
            if (initialValue != null) {
                return true;
            }
            
            if (cursor == null) {
                return false;
            }
            
            AbstractTestResultAction<?> previousBuild = cursor.getPreviousResult(AbstractTestResultAction.class, true);
            return previousBuild != null;
        }

        @Override
        public BuildResult<AbstractTestResultAction<?>> next() {
            if (initialValue == null && cursor == null) {
                throw new NoSuchElementException(
                        "There is no action available anymore. Use hasNext() before calling next().");
            }
            AbstractTestResultAction<?> buildAction = getBuildAction();
            if (buildAction != null) {
                cursor = buildAction;
                Run<?, ?> run = cursor.run;

                int buildTimeInSeconds = (int) (run.getTimeInMillis() / 1000);
                Build build = new Build(run.getNumber(), run.getDisplayName(), buildTimeInSeconds);
                return new BuildResult<>(build, buildAction);
            }

            throw new NoSuchElementException("No more runs with a test result available: " + cursor);
        }

        private AbstractTestResultAction<?> getBuildAction() {
            AbstractTestResultAction<?> run;
            if (initialValue != null) {
                run = initialValue;
                initialValue = null;
            } else {
                run = cursor.getPreviousResult(AbstractTestResultAction.class, true);
            }
            return run;
        }
    }

}
