package io.jenkins.plugins.analysis.junit;

import org.json.JSONException;
import org.junit.Test;

import com.gargoylesoftware.htmlunit.ScriptResult;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.util.TestUtils;

import static io.jenkins.plugins.analysis.junit.JUnitProjectSummaryAssert.*;
import static org.assertj.core.api.AssertionsForClassTypes.*;

public class ProjectSummaryTest extends AbstractJUnitTest {

    @Test
    public void verifyTestResults() throws JSONException {
        Build lastBuild = TestUtils.createTwoBuildsWithIncreasedTestFailures(this);
        lastBuild.clickLink("Back to Project");

        JUnitProjectSummary projectSummary = new JUnitProjectSummary(lastBuild);

        assertThat(projectSummary)
                .hasNumberOfFailures(4)
                .hasFailureDifference(1);

        assertThat(projectSummary.getTitleText())
                .contains("4 failures")
                .contains("+1");
    }

    @Test
    public void verifyChart() throws JSONException {
        Build lastBuild = TestUtils.createTwoBuildsWithIncreasedTestFailures(this);
        lastBuild.clickLink("Back to Project");

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
