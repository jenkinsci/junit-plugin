package io.jenkins.plugins.analysis.junit.testresults.tableentry;

import java.util.Optional;

/**
 * The entry of a failed test table listing failed tests of the current build.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class FailedTestTableEntry {

    private final String testName;

    private final String testLink;

    private final int duration;

    private final int age;

    private final Optional<String> errorDetails;

    private final Optional<String> stackTrace;

    /**
     * Costum constructor. Creates object.
     * @param testName the test name property
     * @param testLink the target location test link
     * @param duration the duration property
     * @param age the age property
     * @param errorDetails the error details property
     * @param stackTrace the stack trace property
     */
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
     * Gets the age property.
     * @return the age property
     */
    public int getAge() {
        return age;
    }

    /**
     * Gets the optional error details.
     * @return the error details
     */
    public  Optional<String> getErrorDetails() {
        return errorDetails;
    }

    /**
     * Gets the optional stack trace.
     * @return the stack trace
     */
    public Optional<String> getStackTrace() {
        return stackTrace;
    }
}
