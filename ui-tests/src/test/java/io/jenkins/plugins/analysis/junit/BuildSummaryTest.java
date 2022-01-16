package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the JUnit summary on the build page of a job.
 *
 * @author MichaelMüller
 * @author Nikolas Paripovic
 */
@WithPlugins("junit")
public class BuildSummaryTest extends AbstractJUnitTest {

    @Test
    public void verifySummaryNoFailures() {
        Build build = TestUtils.createFreeStyleJobWithResources(
                this,
                Arrays.asList("/success/com.simple.project.AppTest.txt", "/success/TEST-com.simple.project.AppTest.xml"), "SUCCESS");

        JUnitBuildSummary buildSummary = new JUnitBuildSummary(build);

        assertThat(buildSummary.getTitleText()).containsAnyOf("no failures", "0 failures");
        assertThat(buildSummary.getFailureNames()).isEmpty();
    }

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
