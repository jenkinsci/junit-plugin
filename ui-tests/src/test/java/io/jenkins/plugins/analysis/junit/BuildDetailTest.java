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

import io.jenkins.plugins.analysis.junit.builddetail.BuildDetailClassView;
import io.jenkins.plugins.analysis.junit.builddetail.BuildDetailPackageView;
import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static io.jenkins.plugins.analysis.junit.builddetail.BuildDetailPackageViewAssert.*;
import static io.jenkins.plugins.analysis.junit.builddetail.BuildDetailClassViewAssert.*;
import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.assertj.core.api.CollectionAssert.*;

/**
 * Tests the detail view of a build's failed Unit tests.
 *
 * @author MichaelMüller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class BuildDetailTest extends AbstractJUnitTest {

    @Test
    public void verifyDetailWithFailures() {
        // TODO: verify listed failures, failure count, error details + stack trace by test

        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/parameterized/junit.xml", "/parameterized/testng.xml"), "UNSTABLE");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        BuildDetailPackageView buildDetailPackageView = buildSummary.openBuildDetailView(); // TODO: Better access by navigation icon?

        assertThat(buildDetailPackageView)
                .hasNumberOfFailures(6)
                .hasNumberOfTests(6);

        assertThat(buildDetailPackageView.failedTestTableExists()).isEqualTo(true);
        //assertThat(buildDetailPackageView.getFailedTestTableEntries())

//        Build build = TestUtils.createFreeStyleJobWithResources(
//                this,
//                Arrays.asList("/parameterized/junit.xml", "/parameterized/testng.xml"), "UNSTABLE");
//
//        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
//        JUnitBuildDetail buildDetail = buildSummary.openBuildDetailView();
//
//        assertThat(buildDetail).hasNumberOfFailures(6);
//        assertThat(buildDetail).hasNumberOfFailuresInTitle(6);


        /*assertThat(buildDetail.getNumberOfFailures()).isEqualTo(6);
        assertThat(buildDetail.getNumberOfFailuresInTitle()).isEqualTo(6);
        assertThat(buildDetail.getFailedTests()).asList();*/

        //TODO: How to check with this API???

    }

    @Test
    public void verifyBuildDetailPackageViewWithFailures() {

        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/failure/three_failed_two_succeeded.xml"), "UNSTABLE");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        BuildDetailPackageView buildDetailPackageView = buildSummary.openBuildDetailView(); // TODO: Better access by navigation icon?

        assertThat(buildDetailPackageView)
                .hasNumberOfFailures(3)
                .hasNumberOfTests(5);

        assertThat(buildDetailPackageView.failedTestTableExists()).isTrue();
        assertThat(buildDetailPackageView.getFailedTestTableEntries()).extracting(List::size).isEqualTo(3);

        assertElementInCollection(buildDetailPackageView.getFailedTestTableEntries(),
                failedTestTableEntry -> failedTestTableEntry.getTestName().equals("com.simple.project.AppTest.testAppFailNoMessage"),
                failedTestTableEntry -> failedTestTableEntry.getTestName().equals("com.simple.project.AppTest.testAppFailNoStacktrace"),
                failedTestTableEntry -> failedTestTableEntry.getTestName().equals("com.another.simple.project.ApplicationTest.testAppFail"));

        assertThat(buildDetailPackageView.getPackageTableEntries()).extracting(List::size).isEqualTo(2);

        assertElementInCollection(buildDetailPackageView.getPackageTableEntries(),
                packageTableEntry -> packageTableEntry.getPackageName().equals("com.simple.project"),
                packageTableEntry -> packageTableEntry.getPackageName().equals("com.another.simple.project"));
    }

    @Test
    public void verifyBuildDetailPackageViewWithNoFailures() {

        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/success/TEST-com.simple.project.AppTest.xml"), "SUCCESS");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        BuildDetailPackageView buildDetailPackageView = buildSummary.openBuildDetailView(); // TODO: Better access by navigation icon?

        assertThat(buildDetailPackageView)
                .hasNumberOfFailures(0)
                .hasNumberOfTests(1);

        assertThat(buildDetailPackageView.failedTestTableExists()).isFalse();
        assertThat(buildDetailPackageView.getFailedTestTableEntries()).extracting(List::size).isEqualTo(0);

        assertThat(buildDetailPackageView.getPackageTableEntries()).extracting(List::size).isEqualTo(1);

        assertElementInCollection(buildDetailPackageView.getPackageTableEntries(),
                packageTableEntry -> packageTableEntry.getPackageName().equals("com.simple.project"));

    }

    @Test
    public void verifyBuildDetailPackageViewWithPreviousTests() {

    }

    @Test
    public void verifyBuildDetailClassViewWithFailures() {

        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/failure/three_failed_two_succeeded.xml"), "UNSTABLE");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);
        BuildDetailClassView buildDetailClassView = buildSummary
                .openBuildDetailView()
                .openClassDetailView("com.simple.project");

        //io.jenkins.plugins.analysis.junit.builddetail.BuildDetailClassViewAssert.assertThat(buildDetailClassView).hasNumb

        assertThat(buildDetailClassView)
                .hasNumberOfFailures(2)
                .hasNumberOfTests(3);

        assertThat(buildDetailClassView.failedTestTableExists()).isTrue();
        assertThat(buildDetailClassView.getFailedTestTableEntries()).extracting(List::size).isEqualTo(2);

        assertElementInCollection(buildDetailClassView.getFailedTestTableEntries(),
                failedTestTableEntry -> failedTestTableEntry.getTestName().equals("com.simple.project.AppTest.testAppFailNoMessage"),
                failedTestTableEntry -> failedTestTableEntry.getTestName().equals("com.simple.project.AppTest.testAppFailNoStacktrace"));

        assertThat(buildDetailClassView.getClassTableEntries()).extracting(List::size).isEqualTo(2);

        assertElementInCollection(buildDetailClassView.getClassTableEntries(),
                classTableEntry -> classTableEntry.getClassName().equals("AppTest"),
                classTableEntry -> classTableEntry.getClassName().equals("ApplicationTest"));

    }

    @Test
    public void verifyBuildDetailClassViewWithNoFailures() {

    }

    @Test
    public void verifyBuildDetailClassViewWithPreviousTests() {

    }

    @Test
    public void verifyBuildDetailTestViewWithFailures() {

    }

    @Test
    public void verifyBuildDetailTestViewWithNoFailures() {

    }

    @Test
    public void verifyBuildDetailTestViewWithPreviousTests() {

    }

    @Test
    public void verifyDetailNoFailures() {
        // TODO: verify listed failures (0), failure count (0), test count (1)
    }

    @Test
    public void verifyDetailWithPreviousTests() {
        // TODO: verify change since last build
    }

    @Test
    public void verifyLinkToTestDetails() {

    }

    private <ElementType> void assertElementInCollection(Collection<ElementType> collection, Predicate<ElementType> ...predicates) {
        assertThat(Stream.of(predicates).allMatch(predicate -> collection.stream()
                .filter(predicate)
                .findAny()
                .isPresent())).isTrue();
    }
}
