package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.po.Job;
import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static org.assertj.core.api.AssertionsForClassTypes.*;

/**
 * Tests the job configuration of the JUnit test results report publisher
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class JobConfigurationTest extends AbstractJUnitTest {

    /**
     * Tests if build is successful with test failures when checkbox "Skip publishing checks" is checked.
     */
    @Test
    public void verifySuccessfulBuildWhenSkipMarkingBuildAsUnstableOnTestFailureChecked() {
        Job job = TestUtils.getCreatedFreeStyleJobWithResources(
                this,
                Arrays.asList("/failure/TEST-com.simple.project.AppTest.xml"),
                true, false, false);

        job.startBuild().shouldSucceed();
    }

    /**
     * Tests if build is successful with no test results when checkbox "Allow empty results" is checked.
     */
    @Test
    public void verifySuccessfulBuildWhenEmptyTestResultsChecked() {
        Job job = TestUtils.getCreatedFreeStyleJobWithResources(
                this,
                Collections.emptyList(),
                false, true, false);

        job.startBuild().shouldSucceed();
    }

    /**
     * Tests if long standard output is not truncated in test details when checkbox "Retain long standard output/error"
     * is checked.
     */
    @Test
    public void verifyRetainLongStandardOutputError() {
        Job job = TestUtils.getCreatedFreeStyleJobWithResources(
                this,
                Arrays.asList("/success/junit-with-long-output.xml"),
                false, false, true);

        job.startBuild().shouldSucceed();

        job.getJenkins().visit("/job/" + job.name + "/1/testReport/(root)/JUnit/testScore_0_/");
        TestDetail testDetail = new TestDetail(job.getLastBuild());

        assertThat(testDetail.getStandardOutput()).isPresent();
        assertThat(testDetail.getStandardOutput().get()).doesNotContain("truncated");
    }
}
