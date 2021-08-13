package hudson.tasks.test;

import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.LineSeries;
import edu.hm.hafner.echarts.LinesChartModel;
import edu.hm.hafner.echarts.LinesDataSet;
import edu.hm.hafner.echarts.Palette;
import hudson.tasks.junit.TestDurationResultSummary;
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
        LinesChartModel model = new LinesChartModel();
        model.setDomainAxisLabels(dataSet.getDomainAxisLabels());
        model.setBuildNumbers(dataSet.getBuildNumbers());

        LineSeries duration = new LineSeries(SECONDS, Palette.GREEN.getNormal(),
                LineSeries.StackedMode.STACKED, LineSeries.FilledMode.FILLED);
        duration.addAll(dataSet.getSeries(SECONDS));
        model.addSeries(duration);
        
        return model;
    }
}
