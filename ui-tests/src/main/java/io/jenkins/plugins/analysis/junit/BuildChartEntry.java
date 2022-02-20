package io.jenkins.plugins.analysis.junit;

/**
 * The entry of the build chart, located on the project overview.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class BuildChartEntry {

    final int buildId;

    final int numberOfSkippedTests;

    final int numberOfFailedTests;

    final int numberOfPassedTests;

    /**
     * Custom constructor. Creates object.
     * @param buildId the build id
     * @param numberOfSkippedTests the number of skipped tests
     * @param numberOfFailedTests the number of failed tests
     * @param numberOfPassedTests the number of passed tests
     */
    public BuildChartEntry(final int buildId, final int numberOfSkippedTests, final int numberOfFailedTests,
            final int numberOfPassedTests) {
        this.buildId = buildId;
        this.numberOfSkippedTests = numberOfSkippedTests;
        this.numberOfFailedTests = numberOfFailedTests;
        this.numberOfPassedTests = numberOfPassedTests;
    }

    /**
     * Gets the build id.
     * @return the build id
     */
    public int getBuildId() {
        return buildId;
    }

    /**
     * Gets the number of skipped tests.
     * @return the number of skipped tests
     */
    public int getNumberOfSkippedTests() {
        return numberOfSkippedTests;
    }

    /**
     * Gets the number of failed tests.
     * @return the number of failed tests
     */
    public int getNumberOfFailedTests() {
        return numberOfFailedTests;
    }

    /**
     * Gets the number of passed tests.
     * @return the number of passed tests
     */
    public int getNumberOfPassedTests() {
        return numberOfPassedTests;
    }
}
