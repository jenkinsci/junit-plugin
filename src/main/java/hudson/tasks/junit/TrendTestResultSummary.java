package hudson.tasks.junit;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static hudson.tasks.test.TestResultTrendSeriesBuilder.FAILED_KEY;
import static hudson.tasks.test.TestResultTrendSeriesBuilder.PASSED_KEY;
import static hudson.tasks.test.TestResultTrendSeriesBuilder.SKIPPED_KEY;
import static hudson.tasks.test.TestResultTrendSeriesBuilder.TOTALS_KEY;

public class TrendTestResultSummary implements Serializable {
    
    private int buildNumber;
    private TestResultSummary testResultSummary;

    public TrendTestResultSummary(int buildNumber, TestResultSummary testResultSummary) {
        this.buildNumber = buildNumber;
        this.testResultSummary = testResultSummary;
    }

    public Map<String, Integer> toMap() {
        Map<String, Integer> series = new HashMap<>();
        int totalCount = testResultSummary.getTotalCount();
        int failCount = testResultSummary.getFailCount();
        int skipCount = testResultSummary.getSkipCount();
        series.put(TOTALS_KEY, totalCount);
        series.put(PASSED_KEY, totalCount - failCount - skipCount);
        series.put(FAILED_KEY, failCount);
        series.put(SKIPPED_KEY, skipCount);
        return series;
    }

    public int getBuildNumber() {
        return buildNumber;
    }
    
    public String getDisplayName() {
        return "#" + buildNumber;
    }

    public TestResultSummary getTestResultSummary() {
        return testResultSummary;
    }
}
