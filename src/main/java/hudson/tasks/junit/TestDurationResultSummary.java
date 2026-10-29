package hudson.tasks.junit;

import hudson.tasks.test.TestDurationTrendSeriesBuilder;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class TestDurationResultSummary implements Serializable {

    private final int buildNumber;
    private final int duration;

    public TestDurationResultSummary(int buildNumber, float duration) {
        this.buildNumber = buildNumber;
        this.duration = (int) duration;
    }

    public Map<String, Integer> toMap() {
        Map<String, Integer> series = new HashMap<>();
        series.put(TestDurationTrendSeriesBuilder.SECONDS, duration);
        return series;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public String getDisplayName() {
        return "#" + buildNumber;
    }

    public int getDuration() {
        return duration;
    }
}
