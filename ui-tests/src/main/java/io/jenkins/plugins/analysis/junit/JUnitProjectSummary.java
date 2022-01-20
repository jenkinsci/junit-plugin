package io.jenkins.plugins.analysis.junit;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.json.Json;

import com.gargoylesoftware.htmlunit.ScriptResult;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

/**
 * {@link PageObject} representing the JUnit summary on the job page.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class JUnitProjectSummary extends PageObject {

    private final WebElement summaryIcon;
    private final WebElement summaryContent;

    private final WebElement titleLink;

    private final List<BuildChartEntry> buildChartEntries;

    /**
     * Creates a new page object representing the JUnit summary on the job page.
     *
     * @param parent
     *         a finished build configured with a static analysis tool
     */
    public JUnitProjectSummary(final Build parent) throws JSONException {
        super(parent, parent.url(""));

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        WebElement junitJobSummaryTableEntry = getJunitJobSummaryTableEntry(mainPanel);

        summaryIcon = findIconInTableEntry(junitJobSummaryTableEntry).get();
        summaryContent = findContentInTableEntry(junitJobSummaryTableEntry).get();
        titleLink = summaryContent.findElement(By.cssSelector("a"));
        buildChartEntries = initializeBuildChartEntries();
    }

    /**
     * Gets the title text of the summary.
     *
     * @return the title text
     */
    public String getTitleText() {
        return summaryContent.getText();
    }

    /**
     * Gets the number of failures of this JUnit run.
     *
     * @return the number of failures
     */
    public int getNumberOfFailures() {
        String summaryContentText = summaryContent.getText().trim();
        int fromIndex = summaryContentText.indexOf('(') + 1;
        int toIndex = summaryContentText.indexOf(" ", fromIndex);
        return Integer.parseInt(summaryContentText.substring(fromIndex, toIndex));
    }

    /**
     * Gets the failure difference.
     *
     * @return the failure difference
     */
    public int getFailureDifference() {
        String summaryContentText = summaryContent.getText().trim();
        int fromIndex = summaryContentText.indexOf('/') + 2;
        int toIndex = summaryContentText.length() - 1;
        return Integer.parseInt(summaryContentText.substring(fromIndex, toIndex));
    }

    /**
     * Gets the entries of the build chart.
     *
     * @return the entries of the build chart
     */
    public List<BuildChartEntry> getBuildChartEntries() {
        return buildChartEntries;
    }

    private WebElement getJunitJobSummaryTableEntry(final WebElement mainPanel) {
        List<WebElement> tables = mainPanel.findElements(By.cssSelector("table tbody tr"));
        return tables.stream()
                .filter(trElement -> findIconInTableEntry(trElement).isPresent())
                .filter(trElement -> findContentInTableEntry(trElement).isPresent())
                .findAny()
                .orElseThrow(() -> new NoSuchElementException("junit job summary table"));
    }

    private List<BuildChartEntry> initializeBuildChartEntries() throws JSONException {
        String canvasJson = getJUnitChart();
        return canvasJsonToBuildChartEntries(canvasJson);
    }

    private String getJUnitChart() {
        Object result = executeScript(String.format(
                "delete(window.Array.prototype.toJSON) %n"
                        + "return JSON.stringify(echarts.getInstanceByDom(document.getElementsByClassName(\"echarts-trend\")[0]).getOption())"));
        ScriptResult scriptResult = new ScriptResult(result);

        return scriptResult.getJavaScriptResult().toString();
    }

    private Optional<WebElement> findIconInTableEntry(final WebElement tableEntry) {
        return findOptionalElement(tableEntry, By.cssSelector("td img.icon-clipboard.icon-xlg"));
    }

    private Optional<WebElement> findContentInTableEntry(final WebElement tableEntry) {
        List<WebElement> foundElements = tableEntry.findElements(By.cssSelector("td"));
        return foundElements.stream()
                .filter(foundElement -> findOptionalElement(foundElement, By.cssSelector("a")).isPresent() &&
                        findOptionalElement(foundElement, By.cssSelector("a")).get()
                                .getText()
                                .equals("Latest Test Result"))
                .findFirst();
    }

    private Optional<WebElement> findOptionalElement(final WebElement webElement, final By byArgument) {
        List<WebElement> foundElements = webElement.findElements(byArgument);
        return foundElements.isEmpty() ? Optional.empty() : Optional.of(foundElements.get(0));
    }

    private List<BuildChartEntry> canvasJsonToBuildChartEntries(final String canvasJson) throws JSONException {
        JSONObject jsonObject = new JSONObject(canvasJson);
        JSONArray buildIds = jsonObject.getJSONArray("xAxis").getJSONObject(0).getJSONArray("data");
        JSONArray series = jsonObject.getJSONArray("series");

        JSONArray failedTestNumbers = null;
        JSONArray skippedTestNumbers = null;
        JSONArray passedTestNumbers = null;

        int seriesLength = series.length();
        for (int i = 0; i < seriesLength; i++) {
            JSONObject currentObject = series.getJSONObject(i);
            String seriesName = currentObject.getString("name");
            switch (seriesName) {
                case "Failed":
                    failedTestNumbers = currentObject.getJSONArray("data");
                    break;
                case "Skipped":
                    skippedTestNumbers = currentObject.getJSONArray("data");
                    break;
                case "Passed":
                    passedTestNumbers = currentObject.getJSONArray("data");
                    break;
            }
        }

        JSONArray finalFailedTestNumbers = failedTestNumbers;
        JSONArray finalSkippedTestNumbers = skippedTestNumbers;
        JSONArray finalPassedTestNumbers = passedTestNumbers;
        return IntStream.range(0, buildIds.length())
                .boxed()
                .map(index -> {
                    try {
                        String buildIdString = buildIds.getString(index);
                        int buildId = Integer.parseInt(buildIdString.trim().substring(1));
                        int failedTests = Integer.parseInt(finalFailedTestNumbers.getString(index));
                        int skippedTests = Integer.parseInt(finalSkippedTestNumbers.getString(index));
                        int passedTests = Integer.parseInt(finalPassedTestNumbers.getString(index));
                        return new BuildChartEntry(buildId, skippedTests, failedTests, passedTests);
                    }
                    catch (JSONException e) {
                        throw new NoSuchElementException();
                    }
                })
                .collect(Collectors.toList());
    }
}
