package hudson.tasks.test;

import java.util.List;

import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.LineSeries;
import edu.hm.hafner.echarts.LinesChartModel;
import edu.hm.hafner.echarts.LinesDataSet;
import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.tasks.junit.TrendTestResultSummary;
import io.jenkins.plugins.echarts.JenkinsPalette;

import static hudson.tasks.test.TestResultTrendSeriesBuilder.*;

public class TestResultTrendChart {
    enum PassedColor {GREEN, BLUE}

    public LinesChartModel create(final List<TrendTestResultSummary> results) {
        return create(results, PassedColor.BLUE);
    }

    public LinesChartModel create(final List<TrendTestResultSummary> results, final PassedColor passedColor) {
        LinesDataSet dataset = new LinesDataSet();
        results.forEach(result -> dataset.add(result.getDisplayName(), result.toMap(), result.getBuildNumber()));

        return getLinesChartModel(dataset, passedColor);
    }

    public LinesChartModel create(@NonNull final Iterable results, final ChartModelConfiguration configuration) {
        return create(results, configuration, PassedColor.GREEN);
    }

    public LinesChartModel create(@NonNull final Iterable results, final ChartModelConfiguration configuration,
            final PassedColor passedColor) {
        TestResultTrendSeriesBuilder builder = new TestResultTrendSeriesBuilder();
        LinesDataSet dataSet = builder.createDataSet(configuration, results);

        return getLinesChartModel(dataSet, passedColor);
    }

    public LinesChartModel createFromTestObject(final Iterable results,
            final ChartModelConfiguration configuration) {
        return createFromTestObject(results, configuration, PassedColor.GREEN);
    }

    public LinesChartModel createFromTestObject(final Iterable results, final ChartModelConfiguration configuration,
            final PassedColor passedColor) {
        TestObjectTrendSeriesBuilder builder = new TestObjectTrendSeriesBuilder();
        LinesDataSet dataSet = builder.createDataSet(configuration, results);

        return getLinesChartModel(dataSet, passedColor);
    }

    private LinesChartModel getLinesChartModel(final LinesDataSet dataSet, final PassedColor passedColor) {
        LinesChartModel model = new LinesChartModel(dataSet);

        LineSeries passed = new LineSeries("Passed",
                passedColor == PassedColor.BLUE ? JenkinsPalette.BLUE.normal() : JenkinsPalette.GREEN.normal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        passed.addAll(dataSet.getSeries(PASSED_KEY));
        model.addSeries(passed);

        LineSeries skipped = new LineSeries("Skipped", JenkinsPalette.GREY.normal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        skipped.addAll(dataSet.getSeries(SKIPPED_KEY));
        model.addSeries(skipped);

        LineSeries failed = new LineSeries("Failed", JenkinsPalette.RED.normal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        failed.addAll(dataSet.getSeries(FAILED_KEY));
        model.addSeries(failed);

        return model;
    }
}
