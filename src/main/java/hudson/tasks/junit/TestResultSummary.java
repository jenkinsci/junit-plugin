package hudson.tasks.junit;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import java.io.Serializable;

/**
 * CPS-friendly summary of test results that
 * serializes counts
 */
public class TestResultSummary implements Serializable {


    private int failCount;
    private int passCount;
    private int skipCount;
    private int totalCount;

    public TestResultSummary(TestResult result) {
        failCount = result.getFailCount();
        passCount = result.getPassCount();
        skipCount = result.getSkipCount();
        totalCount = result.getTotalCount();
    }

    @Whitelisted
    public int getFailCount() {
        return failCount;
    }

    @Whitelisted
    public int getPassCount() {
        return passCount;
    }

    @Whitelisted
    public int getSkipCount() {
        return skipCount;
    }

    @Whitelisted
    public int getTotalCount() {
        return totalCount;
    }

}
