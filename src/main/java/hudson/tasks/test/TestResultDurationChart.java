package hudson.tasks.test;

import java.util.List;

import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.LineSeries;
import edu.hm.hafner.echarts.LinesChartModel;
import edu.hm.hafner.echarts.LinesDataSet;
import edu.hm.hafner.echarts.Palette;

import hudson.tasks.junit.TestDurationResultSummary;

import static hudson.tasks.test.TestDurationTrendSeriesBuilder.*;

public class TestResultDurationChart {

    public LinesChartModel create(final List<TestDurationResultSummary> results) {
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

    private LinesChartModel getLinesChartModel(final LinesDataSet dataSet) {
        LinesChartModel model = new LinesChartModel(dataSet);
        LineSeries duration = new LineSeries(SECONDS, Palette.GREEN.getNormal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        duration.addAll(dataSet.getSeries(SECONDS));
        model.addSeries(duration);

        return model;
    }
}
