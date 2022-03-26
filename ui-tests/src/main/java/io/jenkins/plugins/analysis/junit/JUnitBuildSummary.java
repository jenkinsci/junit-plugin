package io.jenkins.plugins.analysis.junit;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

import io.jenkins.plugins.analysis.junit.testresults.BuildTestResults;

/**
 * {@link PageObject} representing the JUnit summary on the build page of a job.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class JUnitBuildSummary extends PageObject {

    private final WebElement summaryIcon;
    private final WebElement summaryContent;

    private final WebElement titleLink;
    private final List<WebElement> failedTestLinks;

    /**
     * Creates a new page object representing the JUnit summary on the build page of a job.
     *
     * @param parent
     *          a finished build configured with a static analysis tool
     */
    public JUnitBuildSummary(final Build parent) {
        super(parent, parent.url("testReport"));
        
        WebElement table = getElement(By.cssSelector("#main-panel table"));
        List<WebElement> tableEntries = table.findElements(By.cssSelector("tbody tr"));

        WebElement junitBuildSummaryTableEntry = tableEntries.stream()
                .filter(trElement -> findIconInTableEntry(trElement).isPresent())
                .filter(trElement -> findContentInTableEntry(trElement).isPresent())
                .findAny()
                .orElseThrow(() -> new NoSuchElementException("junit build summary table"));

        summaryIcon = findIconInTableEntry(junitBuildSummaryTableEntry).get();
        summaryContent = findContentInTableEntry(junitBuildSummaryTableEntry).get();

        titleLink = summaryContent.findElement(By.cssSelector("a"));
        failedTestLinks = summaryContent.findElements(By.cssSelector("ul li a"));
    }

    private Optional<WebElement> findIconInTableEntry(final WebElement tableEntry) {
        return findOptionalElement(tableEntry, By.cssSelector("td img.icon-clipboard.icon-xlg"));
    }

    private Optional<WebElement> findContentInTableEntry(final WebElement tableEntry) {
        List<WebElement> foundElements = tableEntry.findElements(By.cssSelector("td"));
        return foundElements.stream()
                .filter(foundElement -> findOptionalElement(foundElement, By.cssSelector("a")).isPresent()
                        && findOptionalElement(foundElement, By.cssSelector("a")).get().getText().equals("Test Result"))
                .findFirst();
    }

    private Optional<WebElement> findOptionalElement(final WebElement webElement, final By byArgument) {
        List<WebElement> foundElements = webElement.findElements(byArgument);
        return foundElements.isEmpty() ? Optional.empty() : Optional.of(foundElements.get(0));
    }

    /**
     * Returns the title text of the summary.
     *
     * @return the title text
     */
    public String getTitleText() {
        return summaryContent.getText();
    }

    /**
     * Returns the title link text of the summary.
     *
     * @return the title link text
     */
    public String getTitleLinkText() {
        String text = summaryContent.getText();
        return text.substring(0, text.indexOf('(') - 2);
    }

    /**
     * Returns the number of failures text in the title of the summary.
     *
     * @return the number of failures text in the title
     */
    public String getTitleNumberOfFailuresText() {
        String text = summaryContent.getText();
        return text.substring(text.indexOf('('), text.length() - 1);
    }


    /**
     * Returns the number of failures of this junit run.
     *
     * @return the number of failures
     */
    public int getNumberOfFailures() {
        return failedTestLinks.size();
    }

    /**
     * Returns the failures' names, in appearance order.
     *
     * @return the failures' names
     */
    public List<String> getFailureNames() {
        return failedTestLinks.stream()
                .map(WebElement::getText)
                .collect(Collectors.toList());
    }

    /**
     * Returns the failures' target links, accessible by its name.
     * Method {@link #getFailureNames()} could help to retrieve the origin order.
     *
     * @return the failures' target links
     */
    public Map<String, String> getFailureTargetLinksByName() {
        return failedTestLinks.stream()
                .collect(Collectors.toMap(WebElement::getText, failedTestLink -> failedTestLink.getAttribute("href")));
    }

    /**
     * Opens the build test results page.
     *
     * @return build test results page object.
     */
    public BuildTestResults openBuildTestResults() {
        return openPage(titleLink, BuildTestResults.class);
    }

    /**
     * Opens the detail view of a test.
     *
     * @param testName name of a test.
     * @return test detail page object.
     */
    public TestDetail openTestDetailView(final String testName) {
        WebElement link = failedTestLinks.stream()
                .filter(failedTestLink -> failedTestLink.getText().equals(testName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(testName));

        return openPage(link, TestDetail.class);
    }

    private <T extends PageObject> T openPage(final WebElement link, final Class<T> type) {
        String href = link.getAttribute("href");

        link.click();
        return newInstance(type, injector, url(href));
    }
}
