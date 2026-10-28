package hudson.tasks.junit;

import java.io.Serializable;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Summary of test results that can be used in Pipeline scripts.
 */
public class TestResultSummary implements Serializable {
    private int failCount;
    private int skipCount;
    private int passCount;
    private int totalCount;
    private float duration;

    @Deprecated
    @Restricted(DoNotUse.class)
    public TestResultSummary() {}

    public TestResultSummary(int failCount, int skipCount, int passCount, int totalCount) {
        this(failCount, skipCount, passCount, totalCount, -1.f);
    }

    public TestResultSummary(int failCount, int skipCount, int passCount, int totalCount, float duration) {
        this.failCount = failCount;
        this.skipCount = skipCount;
        this.passCount = passCount;
        this.totalCount = totalCount;
        this.duration = duration;
    }

    public TestResultSummary(TestResult result) {
        this.failCount = result.getFailCount();
        this.skipCount = result.getSkipCount();
        this.passCount = result.getPassCount();
        this.totalCount = result.getTotalCount();
        this.duration = result.getDuration();
        if (totalCount == 0) {
            for (SuiteResult suite : result.getSuites()) {
                if (!suite.getCases().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Attempt to construct TestResultSummary from TestResult without calling tally/freeze");
                }
            }
        }
    }

    @Whitelisted
    public int getFailCount() {
        return failCount;
    }

    @Whitelisted
    public int getSkipCount() {
        return skipCount;
    }

    @Whitelisted
    public int getPassCount() {
        return passCount;
    }

    @Whitelisted
    public int getTotalCount() {
        return totalCount;
    }

    @Whitelisted
    public float getDuration() {
        return duration;
    }
}
