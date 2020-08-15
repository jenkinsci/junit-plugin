package hudson.tasks.test;

import edu.hm.hafner.echarts.SeriesBuilder;
import hudson.tasks.junit.TestResultAction;
import java.util.HashMap;
import java.util.Map;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class TestResultTrendSeriesBuilder extends SeriesBuilder<AbstractTestResultAction> {
    public static final String TOTALS_KEY = "total";
    public static final String PASSED_KEY = "passed";
    public static final String FAILED_KEY = "failed";
    public static final String SKIPPED_KEY = "skipped";

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
