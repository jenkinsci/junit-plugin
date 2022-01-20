package io.jenkins.plugins.analysis.junit.testresults;

import java.net.URL;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.google.inject.Injector;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

import io.jenkins.plugins.analysis.junit.TestDetail;
import io.jenkins.plugins.analysis.junit.testresults.tableentry.TestTableEntry;

/**
 * {@link PageObject} representing the third page of the test results of a build.
 * This page contains tests filtered by a specific package and a specific class.
 *
 * @author Nikolas Paripovic
 * @author Michael Müller
 */
public class BuildTestResultsByClass extends TestResults {

    private final List<WebElement> testLinks;
    private final List<TestTableEntry> testTableEntries;

    /**
     * Creates a new page object representing the third page of the test results of a build.
     *
     * @param parent
     *         a finished build configured with a static analysis tool
     */
    public BuildTestResultsByClass(final Build parent) {
        super(parent);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        testLinks = TestResultsTableUtil.getLinksOfTableItems(mainPanel);
        testTableEntries = initializeTestTableEntries(mainPanel);
    }

    /**
     * Creates an instance of the page. This constructor is used for injecting a
     * filtered instance of the page (e.g. by clicking on links which open a filtered instance of a AnalysisResult.
     *
     * @param injector
     *         the injector of the page
     * @param url
     *         the url of the page
     */
    public BuildTestResultsByClass(final Injector injector, final URL url) {
        super(injector, url);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        testLinks = TestResultsTableUtil.getLinksOfTableItems(mainPanel);
        testTableEntries = initializeTestTableEntries(mainPanel);
    }

    /**
     * Gets the entries of the test table.
     * @return the test table entries
     */
    public List<TestTableEntry> getTestTableEntries() {
        return testTableEntries;
    }

    /**
     * Open the test results page, filtered by test name.
     * @param testName the test to filter
     * @return the opened page
     */
    public TestDetail openTestDetail(final String testName) {
        WebElement link = testLinks.stream()
                .filter(classLink -> classLink.getText().equals(testName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(testName));
        return openPage(link, TestDetail.class);
    }

    private List<TestTableEntry> initializeTestTableEntries(final WebElement mainPanel) {
        return TestResultsTableUtil.getTableItemsWithoutHeader(mainPanel).stream()
                .map(this::webElementToTestTableEntry)
                .collect(Collectors.toList());
    }

    private TestTableEntry webElementToTestTableEntry(final WebElement trElement) {
        List<WebElement> columns = trElement.findElements(By.cssSelector("td"));

        WebElement linkElement = columns.get(0).findElement(By.cssSelector("a.model-link.inside"));
        String testName = linkElement.getText();
        String testLink = linkElement.getAttribute("href");
        String durationString = columns.get(1).getText().trim();
        int duration = Integer.parseInt(durationString.substring(0, durationString.length() - " ms".length()));
        String status = columns.get(2).getText();

        return new TestTableEntry(testName, testLink, duration, status);
    }
}
