package hudson.tasks.junit;

import hudson.Util;
import hudson.model.Run;

public class HistoryTestResultSummary {

    private final Run<?, ?> run;
    private final float duration;
    private final int failCount;
    private final int skipCount;
    private final int passCount;
    private final String description;
    private final hudson.tasks.test.TestResult resultInRun;

    public HistoryTestResultSummary(Run<?, ?> run, hudson.tasks.test.TestResult resultInRun,
    float duration, int failCount, int skipCount, int passCount) {
        this(run, resultInRun, duration, failCount, skipCount, passCount, null);
    }

    public HistoryTestResultSummary(Run<?, ?> run, hudson.tasks.test.TestResult resultInRun, float duration, int failCount, int skipCount, int passCount, String description) {
        this.run = run;
        this.duration = duration;
        this.failCount = failCount;
        this.skipCount = skipCount;
        this.passCount = passCount;
        this.description = description;
        this.resultInRun = resultInRun;
    }

    public String getDescription() {
        return description;
    }

    public Run<?, ?> getRun() {
        return run;
    }

    public String getDurationString() {
        return Util.getTimeSpanString((long) (duration * 1000));
    }

    public int getFailCount() {
        return failCount;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public int getPassCount() {
        return passCount;
    }

    public int getTotalCount() {
        return passCount + skipCount + failCount;
    }

    public float getBadness() {
        return (float)Math.min(1.0, failCount / (getTotalCount() * 0.02));
    }

    public String getFullDisplayName() {
        return run.getFullDisplayName();
    }

    public String getUrl() {
        // TODO may want to try get the test object here
        return run.getUrl() + "testReport/junit";
    }

    public hudson.tasks.test.TestResult getResultInRun() {
        return resultInRun;
    }
}
