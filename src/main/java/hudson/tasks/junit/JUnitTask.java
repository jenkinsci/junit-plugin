package hudson.tasks.junit;

import java.util.List;

public interface JUnitTask {
    String getTestResults();

    double getHealthScaleFactor();

    List<TestDataPublisher> getTestDataPublishers();

    StdioRetention getStdioRetention();

    @Deprecated
    default boolean isKeepLongStdio() {
        return StdioRetention.all == getStdioRetention();
    }

    boolean isKeepProperties();

    boolean isAllowEmptyResults();
    
    boolean isSkipPublishingChecks();

    String getChecksName();

    boolean isSkipOldReports();

}
