package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.testresults.BuildTestResults;
import io.jenkins.plugins.analysis.junit.testresults.BuildTestResultsByPackage;
import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static io.jenkins.plugins.analysis.junit.testresults.BuildDetailClassViewAssert.assertThat;
import static io.jenkins.plugins.analysis.junit.testresults.BuildDetailPackageViewAssert.assertThat;
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

        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/success/TEST-com.simple.project.AppTest.xml"), "UNSTABLE");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        BuildTestResultsByPackage buildTestResultsByPackage = buildSummary
                .openBuildDetailView()
                .openClassDetailView("com.simple.project");

        assertThat(buildTestResultsByPackage)
                .hasNumberOfFailures(2)
                .hasNumberOfTests(3);

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

        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/success/TEST-com.simple.project.AppTest.xml"), "SUCCESS");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        BuildTestResultsByPackage buildTestResultsByPackage = buildSummary
                .openBuildDetailView()
                .openClassDetailView("com.simple.project");

        assertThat(buildTestResultsByPackage)
                .hasNumberOfFailures(0)
                .hasNumberOfTests(1);

        assertThat(buildTestResultsByPackage.failedTestTableExists()).isFalse();

        assertThat(buildTestResultsByPackage.getClassTableEntries()).extracting(List::size).isEqualTo(1);

        TestUtils.assertElementInCollection(buildTestResultsByPackage.getClassTableEntries(),
                classTableEntry -> classTableEntry.getClassName().equals("AppTest"));
    }

    // TODO: Optional Test: compare diffs to old build in test result table
    @Test
    public void verifyBuildDetailClassViewWithPreviousTests() {
        // TODO: @Michi:
    }
}
