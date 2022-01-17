package io.jenkins.plugins.analysis.junit.builddetail.tableentry;

public class TestTableEntry {

    private final String testName;

    private final String testLink;

    private final int duration;

    private final String status;

    public TestTableEntry(final String testName, final String testLink, final int duration, final String status) {
        this.testName = testName;
        this.testLink = testLink;
        this.duration = duration;
        this.status = status;
    }

    public String getTestName() {
        return testName;
    }

    public String getTestLink() {
        return testLink;
    }

    public int getDuration() {
        return duration;
    }

    public String getStatus() {
        return status;
    }
}