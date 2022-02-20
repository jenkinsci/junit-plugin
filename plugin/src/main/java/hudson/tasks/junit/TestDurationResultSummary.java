package hudson.tasks.junit;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static hudson.tasks.test.TestDurationTrendSeriesBuilder.SECONDS;

public class TestDurationResultSummary implements Serializable {
    
    private final int buildNumber;
    private final int duration;

    public TestDurationResultSummary(int buildNumber, float duration) {
        this.buildNumber = buildNumber;
        this.duration = (int) duration;
    }

    public Map<String, Integer> toMap() {
        Map<String, Integer> series = new HashMap<>();
        series.put(SECONDS, duration);
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
