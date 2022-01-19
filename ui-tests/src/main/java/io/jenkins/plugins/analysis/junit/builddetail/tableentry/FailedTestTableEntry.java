package io.jenkins.plugins.analysis.junit.builddetail.tableentry;

import java.util.Optional;

public class FailedTestTableEntry {

    private final String testName;

    private final String testLink;

    private final int duration;

    private final int age;

    private final Optional<String> errorDetails;

    private final Optional<String> stackTrace;

    public FailedTestTableEntry(final String testName, final String testLink, final int duration, final int age,
            final Optional<String> errorDetails,
            final Optional<String> stackTrace) {
        this.testName = testName;
        this.testLink = testLink;
        this.duration = duration;
        this.age = age;
        this.errorDetails = errorDetails;
        this.stackTrace = stackTrace;
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

    public int getAge() {
        return age;
    }

    public  Optional<String> getErrorDetails() {
        return errorDetails;
    }

    public Optional<String> getStackTrace() {
        return stackTrace;
    }
}
