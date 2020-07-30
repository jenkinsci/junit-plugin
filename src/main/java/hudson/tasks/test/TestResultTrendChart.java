package hudson.tasks.test;

import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.LineSeries;
import edu.hm.hafner.echarts.LinesChartModel;
import edu.hm.hafner.echarts.LinesDataSet;
import edu.hm.hafner.echarts.Palette;

public class TestResultTrendChart {

    public LinesChartModel create(final Iterable results,
                                  final ChartModelConfiguration configuration) {
        TestResultTrendSeriesBuilder builder = new TestResultTrendSeriesBuilder();
        LinesDataSet dataSet = builder.createDataSet(configuration, results);

        LinesChartModel model = new LinesChartModel();
        model.setDomainAxisLabels(dataSet.getDomainAxisLabels());
        model.setBuildNumbers(dataSet.getBuildNumbers());

        LineSeries failed = new LineSeries("Failed", Palette.RED.getNormal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        failed.addAll(dataSet.getSeries(TestResultTrendSeriesBuilder.FAILED_KEY));
        model.addSeries(failed);

        LineSeries skipped = new LineSeries("Skipped", Palette.GRAY.getNormal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        skipped.addAll(dataSet.getSeries(TestResultTrendSeriesBuilder.SKIPPED_KEY));
        model.addSeries(skipped);

        LineSeries passed = new LineSeries("Passed", Palette.BLUE.getNormal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        passed.addAll(dataSet.getSeries(TestResultTrendSeriesBuilder.PASSED_KEY));
        model.addSeries(passed);

        return model;
    }
}
