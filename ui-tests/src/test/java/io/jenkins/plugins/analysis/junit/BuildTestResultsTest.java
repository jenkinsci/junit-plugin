package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.testresults.BuildTestResults;
import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static io.jenkins.plugins.analysis.junit.testresults.BuildDetailPackageViewAssert.*;
import static org.assertj.core.api.AssertionsForClassTypes.*;

/**
 * Tests the detail view of a build's failed Unit tests.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class BuildTestResultsTest extends AbstractJUnitTest {

    @Test
    public void verifyWithFailures() {

        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/failure/three_failed_two_succeeded.xml"), "UNSTABLE");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        BuildTestResults buildTestResults = buildSummary.openBuildTestResults(); // TODO: Better access by navigation icon?

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

    @Test
    public void verifyNoFailures() {

        Build build = TestUtils.createFreeStyleJobWithResources(
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

    // TODO: Optional Test: compare diffs to old build in test result table
    @Test
    public void verifyBuildDetailClassViewWithPreviousTests() {
        // TODO: @Michi:
    }
}
