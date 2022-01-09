package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;
import java.util.List;

import org.checkerframework.checker.units.qual.C;
import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.JUnitPublisher;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;


/**
 * Tests the JUnit summary on the build page of a job.
 *
 * @author MichaelMüller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class BuildSummaryTest extends AbstractJUnitTest {

    // TODO: Test here if test title (x failures) is correct
    @Test
    public void verifySummaryNoFailures() {
        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/success/com.simple.project.AppTest.txt", "/success/TEST-com.simple.project.AppTest.xml"), "SUCCESS");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build, "junit");

        assertThat(buildSummary.getBuildStatus(), is("Success"));
        assertThatJson(buildSummary.getFailureNames())
                .isArray()
                .hasSize(0);
    }

    @Test
    public void verifySummaryWithFailures() {
        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/parameterized/junit.xml", "/parameterized/testng.xml"), "UNSTABLE");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build, "junit");

        assertThat(buildSummary.getBuildStatus(), is("Unstable"));
        assertThatJson(buildSummary.getFailureNames())
                .isArray()
                .hasSize(6)
                .containsExactly("JUnit.testScore[0]", "JUnit.testScore[1]", "JUnit.testScore[2]", "TestNG.testScore", "TestNG.testScore", "TestNG.testScore");
    }
}
