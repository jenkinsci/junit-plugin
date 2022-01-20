package io.jenkins.plugins.analysis.junit;

import org.junit.Test;

import com.gargoylesoftware.htmlunit.ScriptResult;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.util.TestUtils;

public class ProjectSummaryTest extends AbstractJUnitTest {
    //TODO: @Niko

    @Test
    public void test() {

        Build lastBuild = TestUtils.createTwoBuildsWithIncreasedTestFailures(this);

        lastBuild.clickLink("Back to Project");

        JUnitProjectSummary projectSummary = new JUnitProjectSummary(lastBuild);

        //JUnitBuildSummary buildSummary = new JUnitBuildSummary(lastBuild);

    }

    public String getTrendChartById(final String elementId) {
        Object result = executeScript(String.format(
                "delete(window.Array.prototype.toJSON) %n"
                        + "return JSON.stringify(echarts.getInstanceByDom(document.getElementById(\"%s\")).getOption())",
                elementId));
        ScriptResult scriptResult = new ScriptResult(result);

        return scriptResult.getJavaScriptResult().toString();
    }
}
