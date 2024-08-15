package hudson.tasks.junit;

import hudson.tasks.test.TestResultTrendSeriesBuilder;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

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
        series.put(TestResultTrendSeriesBuilder.TOTALS_KEY, totalCount);
        series.put(TestResultTrendSeriesBuilder.PASSED_KEY, totalCount - failCount - skipCount);
        series.put(TestResultTrendSeriesBuilder.FAILED_KEY, failCount);
        series.put(TestResultTrendSeriesBuilder.SKIPPED_KEY, skipCount);
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
