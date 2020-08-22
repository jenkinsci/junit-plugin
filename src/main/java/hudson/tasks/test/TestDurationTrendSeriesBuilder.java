package hudson.tasks.test;

import edu.hm.hafner.echarts.SeriesBuilder;
import java.util.HashMap;
import java.util.Map;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class TestDurationTrendSeriesBuilder extends SeriesBuilder<AbstractTestResultAction> {
    public static final String SECONDS = "Seconds";

    @Override
    protected Map<String, Integer> computeSeries(AbstractTestResultAction testResultAction) {
        Map<String, Integer> series = new HashMap<>();
        
        if (testResultAction.getResult() instanceof TestResult) {
            final TestResult result = (TestResult) testResultAction.getResult();
            series.put(SECONDS, (int) result.getDuration());
        } else {
            // not sure if we can do anything better here
            series.put(SECONDS, 0);
        }
        return series;
    }
}
