package hudson.tasks.test;

import java.util.List;

import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.LineSeries;
import edu.hm.hafner.echarts.LinesChartModel;
import edu.hm.hafner.echarts.LinesDataSet;
import edu.hm.hafner.echarts.Palette;
import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.tasks.junit.TrendTestResultSummary;

import static hudson.tasks.test.TestResultTrendSeriesBuilder.*;

public class TestResultTrendChart {

    public LinesChartModel create(final List<TrendTestResultSummary> results) {
        LinesDataSet dataset = new LinesDataSet();
        results.forEach(result -> dataset.add(result.getDisplayName(), result.toMap(), result.getBuildNumber()));

        return getLinesChartModel(dataset);
    }

    public LinesChartModel create(@NonNull final Iterable results,
                                  final ChartModelConfiguration configuration) {
        TestResultTrendSeriesBuilder builder = new TestResultTrendSeriesBuilder();
        LinesDataSet dataSet = builder.createDataSet(configuration, results);

        return getLinesChartModel(dataSet);
    }

    public LinesChartModel createFromTestObject(final Iterable results,
                                  final ChartModelConfiguration configuration) {
        TestObjectTrendSeriesBuilder builder = new TestObjectTrendSeriesBuilder();
        LinesDataSet dataSet = builder.createDataSet(configuration, results);

        return getLinesChartModel(dataSet);
    }

    private LinesChartModel getLinesChartModel(final LinesDataSet dataSet) {
        LinesChartModel model = new LinesChartModel(dataSet);
        LineSeries failed = new LineSeries("Failed", Palette.RED.getNormal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        failed.addAll(dataSet.getSeries(FAILED_KEY));
        model.addSeries(failed);

        LineSeries skipped = new LineSeries("Skipped", Palette.GRAY.getNormal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        skipped.addAll(dataSet.getSeries(SKIPPED_KEY));
        model.addSeries(skipped);

        LineSeries passed = new LineSeries("Passed", Palette.BLUE.getNormal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        passed.addAll(dataSet.getSeries(PASSED_KEY));
        model.addSeries(passed);

        return model;
    }
}
