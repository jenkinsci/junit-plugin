package hudson.tasks.test;

import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.LineSeries;
import edu.hm.hafner.echarts.LinesChartModel;
import edu.hm.hafner.echarts.LinesDataSet;
import hudson.tasks.junit.TestDurationResultSummary;
import io.jenkins.plugins.echarts.JenkinsPalette;
import java.util.List;

public class TestResultDurationChart {

    public LinesChartModel create(List<TestDurationResultSummary> results) {
        LinesDataSet dataset = new LinesDataSet();
        results.forEach(result -> dataset.add(result.getDisplayName(), result.toMap(), result.getBuildNumber()));

        return getLinesChartModel(dataset);
    }

    public LinesChartModel create(final Iterable results, final ChartModelConfiguration configuration) {
        TestDurationTrendSeriesBuilder builder = new TestDurationTrendSeriesBuilder();
        LinesDataSet dataSet = builder.createDataSet(configuration, results);

        return getLinesChartModel(dataSet);
    }

    private LinesChartModel getLinesChartModel(LinesDataSet dataSet) {
        LinesChartModel model = new LinesChartModel(dataSet);

        LineSeries duration = new LineSeries(
                TestDurationTrendSeriesBuilder.SECONDS,
                JenkinsPalette.GREEN.normal(),
                LineSeries.StackedMode.STACKED,
                LineSeries.FilledMode.FILLED);
        duration.addAll(dataSet.getSeries(TestDurationTrendSeriesBuilder.SECONDS));
        model.addSeries(duration);

        return model;
    }
}
