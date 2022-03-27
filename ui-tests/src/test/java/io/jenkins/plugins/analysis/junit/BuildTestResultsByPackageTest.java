package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.testresults.BuildTestResultsByPackage;
import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static io.jenkins.plugins.analysis.junit.Assertions.*;

/**
 * Tests the published unit test results of a build which are filtered by a package.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class BuildTestResultsByPackageTest extends AbstractJUnitTest {

    /**
     * Verifies correct numbers of failed and passed tests, failed tests table and the test classes table are shown correctly.
     */
    @Test
    public void verifyWithFailures() {

        BuildTestResultsByPackage buildTestResultsByPackage = createBuildJobAndOpenBuildTestResultsByPackage(
                "/failure/three_failed_two_succeeded.xml",
                "UNSTABLE",
                "com.simple.project"
        );

        assertThat(buildTestResultsByPackage)
                .hasNumberOfFailures(2)
                .hasNumberOfTests(3);

        TestUtils.assertElementInCollection(buildTestResultsByPackage.getFailedTestTableEntries(),
                failedTestTableEntry -> failedTestTableEntry.getTestName()
                        .equals("com.simple.project.AppTest.testAppFailNoMessage"),
                failedTestTableEntry -> failedTestTableEntry.getTestName()
                        .equals("com.simple.project.AppTest.testAppFailNoStacktrace"));

        TestUtils.assertElementInCollection(buildTestResultsByPackage.getClassTableEntries(),
                classTableEntry -> classTableEntry.getClassName().equals("AppTest"),
                classTableEntry -> classTableEntry.getClassName().equals("ApplicationTest"));
    }

    /**
     * Verifies correct numbers of failed and passed tests, no failed tests table is shown and the test classes table is shown correctly.
     */
    @Test
    public void verifyWithNoFailures() {

        BuildTestResultsByPackage buildTestResultsByPackage = createBuildJobAndOpenBuildTestResultsByPackage(
                "/success/TEST-com.simple.project.AppTest.xml",
                "SUCCESS",
                "com.simple.project"
        );

        assertThat(buildTestResultsByPackage)
                .hasNumberOfFailures(0)
                .hasNumberOfTests(1);

        assertThat(buildTestResultsByPackage).hasFailedTestsTable(Optional.empty());

        TestUtils.assertElementInCollection(buildTestResultsByPackage.getClassTableEntries(),
                classTableEntry -> classTableEntry.getClassName().equals("AppTest"));
    }

    /**
     * Verifies increase/ decrease in failure/ passed tests count of two consecutive builds are shown correctly.
     */
    @Test
    public void verifiesTestHasStatusRegressionWhenTestFailedAfterSuccessfulTestBefore() {

        Build lastBuild = TestUtils.createTwoBuildsWithIncreasedTestFailures(this);

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(lastBuild);
        BuildTestResultsByPackage buildTestResultsByPackage = buildSummary
                .openBuildTestResults()
                .openTestResultsByPackage("com.another.simple.project");

        TestUtils.assertElementInCollection(buildTestResultsByPackage.getClassTableEntries(),
                packageTableEntry -> packageTableEntry.getFailDiff().get().equals(1)
                        && packageTableEntry.getPassDiff().get().equals(-1)
        );
    }

    private BuildTestResultsByPackage createBuildJobAndOpenBuildTestResultsByPackage(String testResultsReport,
            String expectedBuildResult, String packageName) {
        Build build = TestUtils.createFreeStyleJobAndRunBuild(
                this,
                Arrays.asList(testResultsReport), expectedBuildResult);

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        return buildSummary
                .openBuildTestResults()
                .openTestResultsByPackage(packageName);

    }
}
