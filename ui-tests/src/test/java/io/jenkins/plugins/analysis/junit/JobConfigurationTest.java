package io.jenkins.plugins.analysis.junit;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;

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
    public void successfulBuildWhenSkipMarkingBuildAsUnstableOnTestFailureChecked() {
        FreeStyleJob j = jenkins.jobs.create();
        j.configure();
        j.copyResource(resource("/failure/com.simple.project.AppTest.txt"));
        j.copyResource(resource("/failure/TEST-com.simple.project.AppTest.xml"));
        JUnitJobConfiguration publisher = j.addPublisher(JUnitJobConfiguration.class);
        publisher.testResults.set("*.xml");
        publisher.setSkipMarkingBuildAsUnstableOnTestFailure(true);
        j.save();

        Build build = j.startBuild();
        assertThat(build.getResult()).isEqualTo("SUCCESS");
    }

    /**
     * Tests if build is successful with no test results when checkbox "Allow empty results" is checked.
     */
    @Test
    public void successfulBuildWhenEmptyTestResultsChecked() {
        FreeStyleJob j = jenkins.jobs.create();
        j.configure();
        JUnitJobConfiguration publisher = j.addPublisher(JUnitJobConfiguration.class);
        publisher.setAllowEmptyResults(true);
        j.save();

        j.startBuild().shouldSucceed();
    }

    /**
     * Tests if long standard output is not truncated in test details when checkbox "Retain long standard output/error" is checked.
     */
    @Test
    public void retainLongStandardOutputError() {
        FreeStyleJob j = jenkins.jobs.create();
        j.configure();
        j.copyResource(resource("/success/junit-with-long-output.xml"));
        JUnitJobConfiguration publisher = j.addPublisher(JUnitJobConfiguration.class);
        publisher.testResults.set("*.xml");
        publisher.setRetainLogStandardOutputError(true);

        j.save();
        Build build = j.startBuild().shouldSucceed();
        j.visit("/job/" + j.name + "/1/testReport/(root)/JUnit/testScore_0_/");
        TestDetail testDetail = new TestDetail(build);

        assertThat(testDetail.getStandardOutput()).isPresent();
        assertThat(testDetail.getStandardOutput().get()).doesNotContain("truncated");
    }
}
