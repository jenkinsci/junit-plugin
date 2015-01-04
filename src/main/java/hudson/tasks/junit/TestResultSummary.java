package hudson.tasks.junit;

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
        result.tally();
        failCount = result.getFailCount();
        passCount = result.getPassCount();
        skipCount = result.getSkipCount();
        totalCount = result.getTotalCount();
    }

    public int getFailCount() {
        return failCount;
    }

    public int getPassCount() {
        return passCount;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

}
