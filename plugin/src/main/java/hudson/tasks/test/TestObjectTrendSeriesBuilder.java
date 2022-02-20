package hudson.tasks.test;

import edu.hm.hafner.echarts.SeriesBuilder;
import java.util.HashMap;
import java.util.Map;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import static hudson.tasks.test.TestResultTrendSeriesBuilder.FAILED_KEY;
import static hudson.tasks.test.TestResultTrendSeriesBuilder.PASSED_KEY;
import static hudson.tasks.test.TestResultTrendSeriesBuilder.SKIPPED_KEY;
import static hudson.tasks.test.TestResultTrendSeriesBuilder.TOTALS_KEY;

@Restricted(NoExternalUse.class)
public class TestObjectTrendSeriesBuilder extends SeriesBuilder<TestObject> {

    @Override
    protected Map<String, Integer> computeSeries(TestObject testObject) {
        Map<String, Integer> series = new HashMap<>();
        
        int totalCount = testObject.getTotalCount();
        int failCount = testObject.getFailCount();
        int skipCount = testObject.getSkipCount();
        series.put(TOTALS_KEY, totalCount);
        series.put(PASSED_KEY, totalCount - failCount - skipCount);
        series.put(FAILED_KEY, failCount);
        series.put(SKIPPED_KEY, skipCount);
        return series;
    }
}
