package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.testresults.BuildTestResults;
import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static io.jenkins.plugins.analysis.junit.testresults.BuildTestResultsAssert.*;
import static org.assertj.core.api.AssertionsForClassTypes.*;

/**
 * Tests the detail view of failed unit tests of a build.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class BuildTestResultsTest extends AbstractJUnitTest {

    /**
     * Verifies correct numbers total tests and failed tests shown when failures occurred.
     * In addition listed names of failed tests and corresponding packages are verified.
     */
    @Test
    public void verifyWithFailures() {

        Build build = TestUtils.createFreeStyleJobAndRunBuild(
                this,
                Arrays.asList("/failure/three_failed_two_succeeded.xml"), "UNSTABLE");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        BuildTestResults buildTestResults = buildSummary.openBuildTestResults();

        assertThat(buildTestResults)
                .hasNumberOfFailures(3)
                .hasNumberOfTests(5);

        assertThat(buildTestResults.failedTestTableExists()).isTrue();
        assertThat(buildTestResults.getFailedTestTableEntries()).extracting(List::size).isEqualTo(3);

        TestUtils.assertElementInCollection(buildTestResults.getFailedTestTableEntries(),
                failedTestTableEntry -> failedTestTableEntry.getTestName().equals("com.simple.project.AppTest.testAppFailNoMessage"),
                failedTestTableEntry -> failedTestTableEntry.getTestName().equals("com.simple.project.AppTest.testAppFailNoStacktrace"),
                failedTestTableEntry -> failedTestTableEntry.getTestName().equals("com.another.simple.project.ApplicationTest.testAppFail"));

        assertThat(buildTestResults.getPackageTableEntries()).extracting(List::size).isEqualTo(2);

        TestUtils.assertElementInCollection(buildTestResults.getPackageTableEntries(),
                packageTableEntry -> packageTableEntry.getPackageName().equals("com.simple.project"),
                packageTableEntry -> packageTableEntry.getPackageName().equals("com.another.simple.project"));
    }

    /**
     * Verifies correct numbers and lists of total tests and failed tests when no failures occurred.
     */
    @Test
    public void verifyNoFailures() {

        Build build = TestUtils.createFreeStyleJobAndRunBuild(
                this,
                Arrays.asList("/success/TEST-com.simple.project.AppTest.xml"), "SUCCESS");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        BuildTestResults buildTestResults = buildSummary.openBuildTestResults(); // TODO: Better access by navigation icon?

        assertThat(buildTestResults)
                .hasNumberOfFailures(0)
                .hasNumberOfTests(1);

        assertThat(buildTestResults.failedTestTableExists()).isFalse();
        assertThat(buildTestResults.getFailedTestTableEntries()).extracting(List::size).isEqualTo(0);

        assertThat(buildTestResults.getPackageTableEntries()).extracting(List::size).isEqualTo(1);

        TestUtils.assertElementInCollection(buildTestResults.getPackageTableEntries(),
                packageTableEntry -> packageTableEntry.getPackageName().equals("com.simple.project"));
    }

    /**
     * Verifies increase/ decrease in failure/ passed tests count of two consecutive builds are shown correctly.
     */
    @Test
    public void verifyFailureAndPassedTestsDifferenceToPreviousBuild() {

        Build lastBuild = TestUtils.createTwoBuildsWithIncreasedTestFailures(this);
        JUnitBuildSummary buildSummary = new JUnitBuildSummary(lastBuild);
        BuildTestResults buildTestResults = buildSummary.openBuildTestResults();

        assertThat(buildTestResults.getPackageTableEntries()).extracting(List::size).isEqualTo(2);

        TestUtils.assertElementInCollection(buildTestResults.getPackageTableEntries(),
                packageTableEntry -> packageTableEntry.getFailDiff().get().equals(1)
                        && packageTableEntry.getPassDiff().get().equals(-1),
                packageTableEntry -> !packageTableEntry.getFailDiff().isPresent()
                        && !packageTableEntry.getPassDiff().isPresent()
        );
    }
}
