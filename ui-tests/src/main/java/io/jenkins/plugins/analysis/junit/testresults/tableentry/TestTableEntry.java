package io.jenkins.plugins.analysis.junit.testresults.tableentry;

/**
 * The entry of a test table listing test results of the current build.
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class TestTableEntry {

    private final String testName;

    private final String testLink;

    private final int duration;

    private final String status;

    /**
     * Custom constructor. Creates object.
     * @param testName the test nam property
     * @param testLink the target location test link
     * @param duration the duration property
     * @param status the status property
     */
    public TestTableEntry(final String testName, final String testLink, final int duration, final String status) {
        this.testName = testName;
        this.testLink = testLink;
        this.duration = duration;
        this.status = status;
    }

    /**
     * Gets the test name property.
     * @return the test name property
     */
    public String getTestName() {
        return testName;
    }

    /**
     * Gets the target location when clicking on the test name.
     * @return the test link
     */
    public String getTestLink() {
        return testLink;
    }

    /**
     * Gets the duration property.
     * @return the duration property
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Gets the status property.
     * @return the status property
     */
    public String getStatus() {
        return status;
    }
}
