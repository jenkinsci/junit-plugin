package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.testresults.BuildTestResultsByClass;
import io.jenkins.plugins.analysis.junit.testresults.BuildTestResultsByPackage;
import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests the published unit tests results of a build which are filtered by a class.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class BuildTestResultsByClassTest extends AbstractJUnitTest {

    /**
     * Verifies failed tests are listed with correct test status.
     */
    @Test
    public void verifyWithFailures() {

        BuildTestResultsByClass buildTestResultsByClass = createBuildJobAndOpenBuildTestResultsByClass(
                "/failure/three_failed_two_succeeded.xml",
                "UNSTABLE",
                "com.simple.project",
                "AppTest"
        );

        assertThat(buildTestResultsByClass.getTestTableEntries()).extracting(List::size).isEqualTo(2);

        TestUtils.assertElementInCollection(buildTestResultsByClass.getTestTableEntries(),
                testTableEntry -> testTableEntry.getTestName().equals("testAppFailNoMessage"),
                testTableEntry -> testTableEntry.getTestName().equals("testAppFailNoStacktrace"));

        TestUtils.assertElementInCollection(buildTestResultsByClass.getTestTableEntries(),
                testTableEntry -> testTableEntry.getStatus().equals("Failed"),
                testTableEntry -> testTableEntry.getStatus().equals("Failed"));
    }

    /**
     * Verifies passed tests are listed with correct test status.
     */
    @Test
    public void verifyWithNoFailures() {

        BuildTestResultsByClass buildTestResultsByClass = createBuildJobAndOpenBuildTestResultsByClass(
                "/success/TEST-com.simple.project.AppTest.xml",
                "SUCCESS",
                "com.simple.project",
                "AppTest"
        );

        assertThat(buildTestResultsByClass.getTestTableEntries()).extracting(List::size).isEqualTo(1);

        TestUtils.assertElementInCollection(buildTestResultsByClass.getTestTableEntries(),
                testTableEntry -> testTableEntry.getTestName().equals("testApp"));

        TestUtils.assertElementInCollection(buildTestResultsByClass.getTestTableEntries(),
                testTableEntry -> testTableEntry.getStatus().equals("Passed"));
    }

    /**
     * Verifies test has status "Regression" when test failed after success in previous build.
     */
    @Test
    public void verifiesTestHasStatusRegressionWhenTestFailedAfterSuccessfulTestBefore() {

        Build lastBuild = TestUtils.createTwoBuildsWithIncreasedTestFailures(this);

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(lastBuild);
        BuildTestResultsByClass buildTestResultsByClass = buildSummary
                .openBuildTestResults()
                .openTestResultsByPackage("com.another.simple.project")
                .openTestResultsByClass("ApplicationTest");

        TestUtils.assertElementInCollection(buildTestResultsByClass.getTestTableEntries(),
                tableEntry -> tableEntry.getTestName().equals("testAppFail") && tableEntry.getStatus().equals("Failed"),
                tableEntry -> tableEntry.getTestName().equals("testApplicationSuccess") && tableEntry.getStatus().equals("Regression"));
    }

    private BuildTestResultsByClass createBuildJobAndOpenBuildTestResultsByClass(String testResultsReport,
            String expectedBuildResult, String packageName, String className) {
        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList(testResultsReport), expectedBuildResult);

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        BuildTestResultsByPackage buildTestResultsByPackage = buildSummary
                .openBuildTestResults()
                .openTestResultsByPackage(packageName);

        return buildTestResultsByPackage.openTestResultsByClass(className);
    }
}
