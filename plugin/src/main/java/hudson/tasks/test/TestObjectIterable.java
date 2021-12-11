package hudson.tasks.test;

import edu.hm.hafner.echarts.Build;
import edu.hm.hafner.echarts.BuildResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class TestObjectIterable implements Iterable<BuildResult<TestObject>> {
    private final TestObject latestAction;
    
    /**
     * Creates a new iterator that selects action of the given type {@code actionType}.
     *
     * @param baseline
     *         the baseline to start from
     */
    public TestObjectIterable(final TestObject baseline) {
        this.latestAction = baseline;
    }
    
    @NonNull
    @Override
    public Iterator<BuildResult<TestObject>> iterator() {
        if (latestAction == null) {
            return new TestResultActionIterator(null);
        }
        return new TestResultActionIterator(latestAction);
    }

    private static class TestResultActionIterator implements Iterator<BuildResult<TestObject>> {
        private TestObject cursor;
        private TestObject initialValue;

        /**
         * Creates a new iterator starting from the baseline.
         *
         * @param baseline
         *         the run to start from
         */
        TestResultActionIterator(final TestObject baseline) {
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
            
            TestResult previousBuild = cursor.getPreviousResult();
            return previousBuild != null;
        }

        @Override
        public BuildResult<TestObject> next() {
            if (initialValue == null && cursor == null) {
                throw new NoSuchElementException(
                        "There is no action available anymore. Use hasNext() before calling next().");
            }
            TestObject testResult = getTestResult();
            if (testResult != null) {
                cursor = testResult;
                Run<?, ?> run = cursor.getRun();

                int buildTimeInSeconds = (int) (run.getTimeInMillis() / 1000);
                Build build = new Build(run.getNumber(), run.getDisplayName(), buildTimeInSeconds);
                return new BuildResult<>(build, testResult);
            }

            throw new NoSuchElementException("No more runs with a test result available: " + cursor);
        }

        private TestObject getTestResult() {
            TestObject run;
            if (initialValue != null) {
                run = initialValue;
                initialValue = null;
            } else {
                run = cursor.getPreviousResult();
            }
            return run;
        }
    }

}
