package hudson.tasks.junit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private transient TestResult testResult;

    @Deprecated
    @Restricted(DoNotUse.class)
    public TestResultSummary() {}

    public TestResultSummary(int failCount, int skipCount, int passCount, int totalCount) {
        this.failCount = failCount;
        this.skipCount = skipCount;
        this.passCount = passCount;
        this.totalCount = totalCount;
        this.testResult = null;
    }

    public TestResultSummary(TestResult result) {
        this.failCount = result.getFailCount();
        this.skipCount = result.getSkipCount();
        this.passCount = result.getPassCount();
        this.totalCount = result.getTotalCount();
        this.testResult = result;
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

    /**
     * Gets the list of failed tests.
     *
     * This method allows pipeline scripts to access individual failed test details
     * for automated notifications, reporting, or conditional logic.
     *
     * @return List of failed test cases, or empty list if test result is not available
     * @since TODO
     */

    public List<CaseResult> getFailedTests() {
        if (testResult == null) {
            return Collections.emptyList();
        }
        return testResult.getFailedTests();
    }

    /**
     * Gets the list of passed tests.
     *
     * @return List of passed test cases, or empty list if test result is not available
     * @since TODO
     */

    public List<CaseResult> getPassedTests() {
        if (testResult == null) {
            return Collections.emptyList();
        }
        return testResult.getPassedTests();
    }

    /**
     * Gets the list of skipped tests.
     *
     * @return List of skipped test cases, or empty list if test result is not available
     * @since TODO
     */

    public List<CaseResult> getSkippedTests() {
        if (testResult == null) {
            return Collections.emptyList();
        }
        return testResult.getSkippedTests();
    }

    /**
     * Gets all test cases (passed, failed, and skipped).
     *
     * Useful for finding slow tests, generating comprehensive reports,
     * or performing custom analysis on all test results.
     *
     * @return List of all test cases, or empty list if test result is not available
     * @since TODO
     */

    public List<CaseResult> getAllTests() {
        if (testResult == null) {
            return Collections.emptyList();
        }

        // Combine all test results
        List<CaseResult> allTests = new ArrayList<>();
        for (SuiteResult suite : testResult.getSuites()) {
            allTests.addAll(suite.getCases());
        }
        return allTests;
    }
}
