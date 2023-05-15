package hudson.tasks.test;

import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.LineSeries;
import edu.hm.hafner.echarts.LinesChartModel;
import edu.hm.hafner.echarts.LinesDataSet;
import hudson.tasks.junit.TestDurationResultSummary;
import io.jenkins.plugins.echarts.JenkinsPalette;

import java.util.List;

import static hudson.tasks.test.TestDurationTrendSeriesBuilder.SECONDS;

public class TestResultDurationChart {

    public LinesChartModel create(List<TestDurationResultSummary> results) {
        LinesDataSet dataset = new LinesDataSet();
        results.forEach(result -> dataset.add(result.getDisplayName(), result.toMap(), result.getBuildNumber()));

        return getLinesChartModel(dataset);
    }

    public LinesChartModel create(final Iterable results,
                                  final ChartModelConfiguration configuration) {
        TestDurationTrendSeriesBuilder builder = new TestDurationTrendSeriesBuilder();
        LinesDataSet dataSet = builder.createDataSet(configuration, results);

        return getLinesChartModel(dataSet);
    }

    private LinesChartModel getLinesChartModel(LinesDataSet dataSet) {
        LinesChartModel model = new LinesChartModel(dataSet);

        LineSeries duration = new LineSeries(SECONDS, JenkinsPalette.GREEN.normal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        duration.addAll(dataSet.getSeries(SECONDS));
        model.addSeries(duration);

        return model;
    }
}
