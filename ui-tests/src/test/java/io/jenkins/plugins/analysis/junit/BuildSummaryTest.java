package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;

import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Job;

import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the JUnit tests summary on the build summary page of a job.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class BuildSummaryTest extends AbstractJUnitTest {

    /**
     * Verifies shown failure count when no failures occurred.
     */
    @Test
    public void verifySummaryNoFailures() {
        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/success/com.simple.project.AppTest.txt", "/success/TEST-com.simple.project.AppTest.xml"), "SUCCESS");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);

        assertThat(buildSummary.getTitleText()).containsAnyOf("no failures", "0 failures");
        assertThat(buildSummary.getFailureNames()).isEmpty();
    }

    /**
     * Verifies shown failure count and listed failures when failures occurred.
     */
    @Test
    public void verifySummaryWithFailures() {
        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/parameterized/junit.xml", "/parameterized/testng.xml"), "UNSTABLE");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);

        assertThat(buildSummary.getTitleText()).contains("6 failures");
        assertThat(buildSummary.getFailureNames())
                .containsExactlyInAnyOrder("JUnit.testScore[0]", "JUnit.testScore[1]", "JUnit.testScore[2]", "TestNG.testScore", "TestNG.testScore", "TestNG.testScore");
    }
}
