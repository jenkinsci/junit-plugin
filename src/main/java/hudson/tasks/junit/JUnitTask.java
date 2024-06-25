package hudson.tasks.junit;

import java.util.List;

public interface JUnitTask {
    String getTestResults();

    double getHealthScaleFactor();

    List<TestDataPublisher> getTestDataPublishers();

    String getStdioRetention();

    default StdioRetention getParsedStdioRetention() {
        return StdioRetention.parse(getStdioRetention());
    }

    @Deprecated
    boolean isKeepLongStdio();

    boolean isKeepProperties();

    boolean isKeepTestNames();

    boolean isAllowEmptyResults();
    
    boolean isSkipPublishingChecks();

    String getChecksName();

    boolean isSkipOldReports();

}
