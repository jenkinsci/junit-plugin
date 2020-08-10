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
        int totalCount = testResultAction.getTotalCount();
        int failCount = testResultAction.getFailCount();
        int skipCount = testResultAction.getSkipCount();
        series.put(TOTALS_KEY, totalCount);
        series.put(PASSED_KEY, totalCount - failCount - skipCount);
        series.put(FAILED_KEY, failCount);
        series.put(SKIPPED_KEY, skipCount);
        return series;
    }
}
