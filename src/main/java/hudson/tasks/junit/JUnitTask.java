package hudson.tasks.junit;

import java.util.List;

public interface JUnitTask {
    String getTestResults();

    double getHealthScaleFactor();

    List<TestDataPublisher> getTestDataPublishers();

    boolean isKeepLongStdio();

    boolean isAllowEmptyResults();
}
