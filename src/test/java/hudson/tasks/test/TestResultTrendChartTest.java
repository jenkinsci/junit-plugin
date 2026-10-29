package hudson.tasks.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.hm.hafner.echarts.LineSeries;
import hudson.tasks.junit.TrendTestResultSummary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestResultTrendChartTest {

    @Test
    void shouldUseCssColorsInChars() {
        var chart = new TestResultTrendChart();

        var summary = mock(TrendTestResultSummary.class);

        when(summary.getBuildNumber()).thenReturn(1);
        when(summary.getDisplayName()).thenReturn("test");
        when(summary.toMap()).thenReturn(Map.of("passed", 1, "failed", 2, "skipped", 3, "total", 6));

        var greenModel = chart.create(List.of(summary), TestResultTrendChart.PassedColor.GREEN);
        verifyColors(greenModel.getSeries(), "--green");

        var blueModel = chart.create(List.of(summary), TestResultTrendChart.PassedColor.BLUE);
        verifyColors(blueModel.getSeries(), "--blue");
    }

    private void verifyColors(List<LineSeries> series, String passed) {
        assertEquals(passed, series.get(0).getItemStyle().getColor());
        assertEquals("--medium-grey", series.get(1).getItemStyle().getColor());
        assertEquals("--red", series.get(2).getItemStyle().getColor());
    }
}
