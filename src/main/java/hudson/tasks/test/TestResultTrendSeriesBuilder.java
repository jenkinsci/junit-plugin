package hudson.tasks.test;

import edu.hm.hafner.echarts.SeriesBuilder;
import hudson.tasks.junit.TestResultAction;
import java.util.HashMap;
import java.util.Map;

public class TestResultTrendSeriesBuilder extends SeriesBuilder<AbstractTestResultAction> {
    static final String TOTALS_KEY = "total";
    static final String PASSED_KEY = "passed";
    static final String FAILED_KEY = "failed";
    static final String SKIPPED_KEY = "skipped";

    @Override
    protected Map<String, Integer> computeSeries(AbstractTestResultAction testResultAction) {
        Map<String, Integer> series = new HashMap<>();
        series.put(TOTALS_KEY, testResultAction.getTotalCount());
        series.put(PASSED_KEY, testResultAction.getPassedTests().size());
        series.put(FAILED_KEY, testResultAction.getFailCount());
        series.put(SKIPPED_KEY, testResultAction.getSkipCount());
        return series;
    }
}
