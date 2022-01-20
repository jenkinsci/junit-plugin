package io.jenkins.plugins.analysis.junit;

import org.json.JSONException;
import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static io.jenkins.plugins.analysis.junit.JUnitProjectSummaryAssert.*;
import static org.assertj.core.api.AssertionsForClassTypes.*;


/**
 * Tests the published results of JUnit tests on the job summary page.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class ProjectSummaryTest extends AbstractJUnitTest {

    /**
     * Verifies correct information at latest result link.
     */
    @Test
    public void verifyLatestTestResultsInformation() throws JSONException {
        Build lastBuild = TestUtils.createTwoBuildsWithIncreasedTestFailures(this);
        lastBuild.job.open();

        JUnitProjectSummary projectSummary = new JUnitProjectSummary(lastBuild);

        assertThat(projectSummary)
                .hasNumberOfFailures(4)
                .hasFailureDifference(1);

        assertThat(projectSummary.getTitleText())
                .contains("4 failures")
                .contains("+1");
    }

    /**
     * Verifies correct information in the test result trend chart.
     */
    @Test
    public void verifyTrendChartForTwoConsecutiveBuilds() throws JSONException {
        Build lastBuild = TestUtils.createTwoBuildsWithIncreasedTestFailures(this);
        lastBuild.job.open();

        JUnitProjectSummary projectSummary = new JUnitProjectSummary(lastBuild);

        TestUtils.assertElementInCollection(projectSummary.getBuildChartEntries(),
                buildChartEntry -> buildChartEntry.getBuildId() == 1
                        && buildChartEntry.getNumberOfFailedTests() == 3
                        && buildChartEntry.getNumberOfPassedTests() == 2
                        && buildChartEntry.getNumberOfSkippedTests() == 0,
                buildChartEntry -> buildChartEntry.getBuildId() == 2
                        && buildChartEntry.getNumberOfFailedTests() == 4
                        && buildChartEntry.getNumberOfPassedTests() == 1
                        && buildChartEntry.getNumberOfSkippedTests() == 0);
    }
}
