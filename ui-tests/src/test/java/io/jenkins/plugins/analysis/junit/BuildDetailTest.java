package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Job;

import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

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
    
    @Test
    public void verifyDirectUrlToTestReport() {
        Job j = TestUtils.getCreatedFreeStyleJobWithResources(
                this,
                Arrays.asList("/success/TEST-com.simple.project.AppTest.xml"), "SUCCESS");

        Build build = j.startBuild().shouldSucceed();
        j.visit("/job/" + j.name + "/1/testReport");

        JUnitBuildDetail buildDetail = new JUnitBuildDetail(build);
        assertThat(buildDetail.getAllTests()).isEqualTo(1);
    }
}
