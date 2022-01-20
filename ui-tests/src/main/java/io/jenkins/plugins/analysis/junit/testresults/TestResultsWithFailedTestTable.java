package io.jenkins.plugins.analysis.junit.testresults;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.google.inject.Injector;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

import io.jenkins.plugins.analysis.junit.TestDetail;
import io.jenkins.plugins.analysis.junit.testresults.tableentry.FailedTestTableEntry;

/**
 * Abstract {@link PageObject} base class for test results pages including a failed test table.
 *
 * @author Nikolas Paripovic
 * @author Michael Müller
 */
public abstract class TestResultsWithFailedTestTable extends TestResults {

    private final Optional<WebElement> failedTestsTable;

    private final List<WebElement> failedTestLinks;
    private final List<FailedTestTableEntry> failedTestTableEntries;

    /**
     * Creates a new abstract page object as a base class for test results pages.
     *
     * @param parent
     *         a finished build configured with a static analysis tool
     */
    public TestResultsWithFailedTestTable(final Build parent) {
        super(parent);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        failedTestsTable = initialFailedTestsTable(mainPanel);

        if (failedTestsTable.isPresent()) {
            failedTestLinks = initializeFailedTestLinks(failedTestsTable.get());
            failedTestTableEntries = initializeFailedTestTableEntries(failedTestsTable.get());
        }
        else {
            failedTestLinks = Collections.emptyList();
            failedTestTableEntries = Collections.emptyList();
        }
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
    public TestResultsWithFailedTestTable(final Injector injector, final URL url) {
        super(injector, url);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        failedTestsTable = initialFailedTestsTable(mainPanel);

        if (failedTestsTable.isPresent()) {
            failedTestLinks = initializeFailedTestLinks(failedTestsTable.get());
            failedTestTableEntries = initializeFailedTestTableEntries(failedTestsTable.get());
        }
        else {
            failedTestLinks = Collections.emptyList();
            failedTestTableEntries = Collections.emptyList();
        }

    }

    /**
     * Checks whether the failed test table exists or not.
     * With this knowledge, you can call {@link #getFailedTestTableEntries()}
     * @return whether the failed test table exists or not
     */
    public boolean failedTestTableExists() {
        return failedTestsTable.isPresent();
    }

    /**
     * Gets the table entries of the failed tests.
     * @return the failed test table entries.
     */
    public List<FailedTestTableEntry> getFailedTestTableEntries() {
        return failedTestTableEntries;
    }

    /**
     * Opens the test detail page.
     * @param testName the test to open
     * @return the opened page
     */
    public TestDetail openTestDetail(final String testName) {
        WebElement link = failedTestLinks.stream()
                .filter(failedTestLink -> failedTestLink.getText().equals(testName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(testName));
        return openPage(link, TestDetail.class);
    }

    private List<WebElement> getFailedTestsTableItemsWithoutHeader(final WebElement failedTestsTableElement) {
        List<WebElement> failedTestsTableItems = failedTestsTableElement.findElements(By.cssSelector("tbody tr"));
        return failedTestsTableItems.subList(1, failedTestsTableItems.size());
    }

    private List<WebElement> initializeFailedTestLinks(final WebElement failedTestsTableElement) {
        List<WebElement> failedTestsTableItemsWithoutHeader = getFailedTestsTableItemsWithoutHeader(failedTestsTableElement);
        return failedTestsTableItemsWithoutHeader.stream()
                .map(trElement -> trElement.findElements(By.cssSelector("td"))
                        .get(0)
                        .findElement(By.cssSelector("a.model-link.inside")))
                .collect(Collectors.toList());
    }

    private List<FailedTestTableEntry> initializeFailedTestTableEntries(final WebElement failedTestsTableElement) {
        List<WebElement> failedTestsTableItemsWithoutHeader = getFailedTestsTableItemsWithoutHeader(failedTestsTableElement);
        return failedTestsTableItemsWithoutHeader.stream()
                .map(this::webElementToFailedTestTableEntry)
                .collect(Collectors.toList());
    }

    private Optional<WebElement> initialFailedTestsTable(final WebElement mainPanel) {
        int failedTestsTableIndex = -1;
        List<WebElement> pageContentChildren = mainPanel.findElements(By.cssSelector("*"));

        int counter = 0;
        for (WebElement element : pageContentChildren) {
            if (element.getTagName().equals("h2")) {
                if (element.getText().equals("All Failed Tests")) {
                    failedTestsTableIndex = counter;
                }
            }
            ++counter;
        }
        return failedTestsTableIndex >= 0 ? Optional.of(pageContentChildren.get(failedTestsTableIndex + 1)) : Optional.empty();
    }

    private FailedTestTableEntry webElementToFailedTestTableEntry(final WebElement trElement) {
        List<WebElement> columns = trElement.findElements(By.cssSelector("td"));

        WebElement linkElement = columns.get(0).findElement(By.cssSelector("a.model-link.inside"));
        String testName = linkElement.getText();
        String testLink = linkElement.getAttribute("href");
        String durationString = columns.get(1).getText().trim();
        int duration = Integer.parseInt(durationString.substring(0, durationString.length() - " ms".length()));
        int age = Integer.parseInt(columns.get(2).findElement(By.cssSelector("a")).getText());

        WebElement expandLink = columns.get(0).findElement(By.cssSelector("a[title=\"Show details\"]"));
        expandLink.click();

        WebElement failureSummary = columns.get(0).findElement(By.cssSelector("div.failure-summary"));

        List<WebElement> showErrorDetailsLinks = failureSummary.findElements(By.cssSelector("a[title=\"Show Error Details\"]"));
        if (showErrorDetailsLinks.size() > 0) {
            WebElement showErrorDetailsLink = showErrorDetailsLinks.get(0);
            if (!showErrorDetailsLink.getAttribute("style").contains("display: none;")) {
                showErrorDetailsLink.click();
            }
        }

        List<WebElement> showStackTraceLinks = failureSummary.findElements(By.cssSelector("a[title=\"Show Stack Trace\"]"));
        if (showStackTraceLinks.size() > 0) {
            WebElement showStackTraceLink = showStackTraceLinks.get(0);
            if (!showStackTraceLink.getAttribute("style").contains("display: none;")) {
                showStackTraceLink.click();
            }
        }

        List<WebElement> failureSummaryChildren = failureSummary.findElements(By.cssSelector("*"));
        int counter = 0;
        int errorDetailsHeaderIndex = -1;
        int stackTraceHeaderIndex = -1;
        for (WebElement element : failureSummaryChildren) {
            if (element.getTagName().equals("h4")) {
                if (element.findElements(By.cssSelector("a[title=\"Show Error Details\"]")).size() > 0) {
                    errorDetailsHeaderIndex = counter;
                }
                else if (element.findElements(By.cssSelector("a[title=\"Show Stack Trace\"]")).size() > 0) {
                    stackTraceHeaderIndex = counter;
                }
            }
            ++counter;
        }

        Optional<WebElement> errorDetailsPreElement = errorDetailsHeaderIndex == -1 ? Optional.empty() : Optional.of(failureSummaryChildren.get(errorDetailsHeaderIndex + 1));
        Optional<WebElement> stackTracePreElement = stackTraceHeaderIndex == -1 ? Optional.empty() : Optional.of(failureSummaryChildren.get(stackTraceHeaderIndex + 1));

        Optional<String> errorDetails = errorDetailsPreElement.map(WebElement::getText);
        Optional<String> stackTrace = stackTracePreElement.map(WebElement::getText);

        return new FailedTestTableEntry(testName, testLink, duration, age, errorDetails, stackTrace);
    }

}
