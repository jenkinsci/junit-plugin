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

/**
 * {@link PageObject} representing the JUnit summary on the build page of a job.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class JUnitBuildSummary extends PageObject {

    private final String buildStatus;

    private final WebElement summaryIcon;
    private final WebElement summaryContent;

    private final WebElement titleLink;
    private final List<WebElement> failedTestLinks;

    // TODO: was ist diese ID ?
    private final String id;

    /**
     * Creates a new page object representing the junit summary on the build page of a job.
     *
     * @param parent
     *          a finished build configured with a static analysis tool
     * @param id
     *          the type of the result page (e.g. simion, checkstyle, cpd, etc.)
     */
    public JUnitBuildSummary(final Build parent, final String id) {
        super(parent, parent.url(id));
        this.id = id;
        
        WebElement table = getElement(By.cssSelector("#main-panel table"));
        List<WebElement> tableEntries = table.findElements(By.cssSelector("tbody tr"));
        WebElement junitBuildSummaryTableEntry = tableEntries.stream()
                .filter(trElement -> findIconInTableEntry(trElement).isPresent())
                .filter(trElement -> findContentInTableEntry(trElement).isPresent())
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("junit build summary table"));


        summaryIcon = findIconInTableEntry(junitBuildSummaryTableEntry).get();
        summaryContent = findContentInTableEntry(junitBuildSummaryTableEntry).get();

        titleLink = summaryContent.findElement(By.cssSelector("a"));
        failedTestLinks = summaryContent.findElements(By.cssSelector("ul li a"));

        WebElement buildHeadline = getElement(By.className("page-headline"));
        if (hasBuildStatus("Unstable")) {
            buildStatus = "Unstable";
        }
        else if (hasBuildStatus("Failed")) {
            buildStatus = "Failed";
        }
        else {
            buildStatus = "Success";
        }
    }

    private Optional<WebElement> findIconInTableEntry(final WebElement tableEntry) {
        return Optional.ofNullable(tableEntry.findElement(By.cssSelector("td img.icon-clipboard icon-xlg")));
    }

    private Optional<WebElement> findContentInTableEntry(final WebElement tableEntry) {
        List<WebElement> foundElements = tableEntry.findElements(By.cssSelector("td"));
        return foundElements.stream()
                .filter(foundElement -> foundElement.findElement(By.cssSelector("a")) != null &&
                        foundElement.findElement(By.cssSelector("a")).getText().equals("Test Result"))
                .findFirst();
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
    public int getNumberOfFailures() { return failedTestLinks.size(); }

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

    public String getBuildStatus() {
        return buildStatus;
    }

    public JUnitBuildDetail openBuildDetailView() {
        return openPage(titleLink, JUnitBuildDetail.class);
    }

    public JUnitTestDetail openTestDetailView(final String testName) {
        WebElement link = failedTestLinks.stream()
                .filter(failedTestLink -> failedTestLink.getText().equals(testName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(testName));

        return openPage(link, JUnitTestDetail.class);
    }

    private <T extends PageObject> T openPage(final WebElement link, final Class<T> type) {
        String href = link.getAttribute("href");
        T result = newInstance(type, injector, url(href), id);
        link.click();
        return result;
    }

    private boolean hasBuildStatus(final String expectedBuildStatus) {
        return !driver.findElements(By.cssSelector("svg[tooltip=" + expectedBuildStatus + "]")).isEmpty();
    }
}
