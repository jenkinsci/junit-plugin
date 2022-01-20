package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.JUnitPublisher;

import io.jenkins.plugins.analysis.junit.testresults.BuildTestResults;
import io.jenkins.plugins.analysis.junit.testresults.BuildTestResultsByPackage;
import io.jenkins.plugins.analysis.junit.util.FixedCopyJobDecorator;
import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests the published unit test results of a build which are filtered by a package.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class BuildTestResultsByPackageTest extends AbstractJUnitTest {

    @Test
    public void verifyWithFailures() {

        BuildTestResultsByPackage buildTestResultsByPackage = createBuildJobAndOpenBuildTestResultsByPackage(
                "/failure/three_failed_two_succeeded.xml",
                "UNSTABLE",
                "com.simple.project"
        );

        assertThat(buildTestResultsByPackage.failedTestTableExists()).isTrue();
        assertThat(buildTestResultsByPackage.getFailedTestTableEntries()).extracting(List::size).isEqualTo(2);

        TestUtils.assertElementInCollection(buildTestResultsByPackage.getFailedTestTableEntries(),
                failedTestTableEntry -> failedTestTableEntry.getTestName().equals("com.simple.project.AppTest.testAppFailNoMessage"),
                failedTestTableEntry -> failedTestTableEntry.getTestName().equals("com.simple.project.AppTest.testAppFailNoStacktrace"));

        assertThat(buildTestResultsByPackage.getClassTableEntries()).extracting(List::size).isEqualTo(2);

        TestUtils.assertElementInCollection(buildTestResultsByPackage.getClassTableEntries(),
                classTableEntry -> classTableEntry.getClassName().equals("AppTest"),
                classTableEntry -> classTableEntry.getClassName().equals("ApplicationTest"));
    }

    @Test
    public void verifyWithNoFailures() {

        BuildTestResultsByPackage buildTestResultsByPackage = createBuildJobAndOpenBuildTestResultsByPackage(
                "/success/TEST-com.simple.project.AppTest.xml",
                "SUCCESS",
                "com.simple.project"
        );

        assertThat(buildTestResultsByPackage.failedTestTableExists()).isFalse();

        assertThat(buildTestResultsByPackage.getClassTableEntries()).extracting(List::size).isEqualTo(1);

        TestUtils.assertElementInCollection(buildTestResultsByPackage.getClassTableEntries(),
                classTableEntry -> classTableEntry.getClassName().equals("AppTest"));
    }

    // TODO: verify emtpy result ?
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

    private BuildTestResultsByPackage createBuildJobAndOpenBuildTestResultsByPackage(String testResultsReport, String expectedBuildResult, String packageName) {
        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList(testResultsReport), expectedBuildResult);

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        return buildSummary
                .openBuildTestResults()
                .openTestResultsByPackage(packageName);

    }

}
