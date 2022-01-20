package io.jenkins.plugins.analysis.junit;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.json.Json;

import com.gargoylesoftware.htmlunit.ScriptResult;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

public class JUnitProjectSummary extends PageObject {

    private final WebElement summaryIcon;
    private final WebElement summaryContent;

    private final WebElement titleLink;

    private final List<BuildChartEntry> buildChartEntries = Collections.emptyList();




    public JUnitProjectSummary(final Build parent) {
        super(parent, parent.url(""));

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        List<WebElement> tables = mainPanel.findElements(By.cssSelector("table tbody tr"));

        WebElement junitJobSummaryTableEntry = tables.stream()
                .filter(trElement -> findIconInTableEntry(trElement).isPresent())
                .filter(trElement -> findContentInTableEntry(trElement).isPresent())
                .findAny()
                .orElseThrow(() -> new NoSuchElementException("junit job summary table"));

        summaryIcon = findIconInTableEntry(junitJobSummaryTableEntry).get();
        summaryContent = findContentInTableEntry(junitJobSummaryTableEntry).get();

        titleLink = summaryContent.findElement(By.cssSelector("a"));
        //failed

        String chart = getChart();

        System.out.println("TEST");


    }

    public String getChart() {
        Object result = executeScript(String.format(
                "delete(window.Array.prototype.toJSON) %n"
                        + "return JSON.stringify(echarts.getInstanceByDom(document.getElementsByClassName(\"echarts-trend\")[0]).getOption())"));
        ScriptResult scriptResult = new ScriptResult(result);

        return scriptResult.getJavaScriptResult().toString();
    }

    /**
     * Returns the title text of the summary.
     *
     * @return the title text
     */
    public String getTitleText() {
        return titleLink.getText() + summaryContent.getText();
    }

    /**
     * Returns the number of failures of this junit run.
     *
     * @return the number of failures
     */
    public int getNumberOfFailures() { return 5; }

    public int getFailureDifference() { return 5; }

    public List<BuildChartEntry> getBuildChartEntries() {
        return buildChartEntries;
    }

    private Optional<WebElement> findIconInTableEntry(final WebElement tableEntry) {
        return findOptionalElement(tableEntry, By.cssSelector("td img.icon-clipboard.icon-xlg"));
    }

    private Optional<WebElement> findContentInTableEntry(final WebElement tableEntry) {
        List<WebElement> foundElements = tableEntry.findElements(By.cssSelector("td"));
        return foundElements.stream()
                .filter(foundElement -> findOptionalElement(foundElement, By.cssSelector("a")).isPresent() &&
                        findOptionalElement(foundElement, By.cssSelector("a")).get().getText().equals("Latest Test Result"))
                .findFirst();
    }

    private Optional<WebElement> findOptionalElement(final WebElement webElement, final By byArgument) {
        List<WebElement> foundElements = webElement.findElements(byArgument);
        return foundElements.isEmpty() ? Optional.empty() : Optional.of(foundElements.get(0));
    }

    private List<BuildChartEntry> canvasJsonToBuildChartEntries(String canvasJson) throws JSONException {
        JSONObject jsonObject = new JSONObject(canvasJson);
        JSONObject xAxis = jsonObject.getJSONArray("xAxis").getJSONObject(0);

        return null;

    }


}

class BuildChartEntry {

    final int buildId;

    final int numberOfSkippedTests;

    final int numberOfFailedTests;

    final int numberOfPassedTests;

    public BuildChartEntry(final int buildId, final int numberOfSkippedTests, final int numberOfFailedTests,
            final int numberOfPassedTests) {
        this.buildId = buildId;
        this.numberOfSkippedTests = numberOfSkippedTests;
        this.numberOfFailedTests = numberOfFailedTests;
        this.numberOfPassedTests = numberOfPassedTests;
    }

    public int getBuildId() {
        return buildId;
    }

    public int getNumberOfSkippedTests() {
        return numberOfSkippedTests;
    }

    public int getNumberOfFailedTests() {
        return numberOfFailedTests;
    }

    public int getNumberOfPassedTests() {
        return numberOfPassedTests;
    }
}
