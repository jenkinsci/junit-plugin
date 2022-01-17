package io.jenkins.plugins.analysis.junit.builddetail;

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

import io.jenkins.plugins.analysis.junit.JUnitTestDetail;
import io.jenkins.plugins.analysis.junit.builddetail.tableentry.FailedTestTableEntry;

public class BuildDetailViewIncludingFailedTestTable extends BuildDetailView {

    private final Optional<WebElement> failedTestsTable;

    private final List<WebElement> failedTestLinks;
    private final List<FailedTestTableEntry> failedTestTableEntries;

    public BuildDetailViewIncludingFailedTestTable(final Build parent) {
        super(parent);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        failedTestsTable = initialFailedTestsTable(mainPanel);

        if(failedTestsTable.isPresent()) {
            failedTestLinks = initializeFailedTestLinks(failedTestsTable.get());
            failedTestTableEntries = initializeFailedTestTableEntries(failedTestsTable.get());
        }
        else {
            failedTestLinks = Collections.emptyList();
            failedTestTableEntries = Collections.emptyList();
        }
    }

    public BuildDetailViewIncludingFailedTestTable(final Injector injector, final URL url) {
        super(injector, url);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        failedTestsTable = initialFailedTestsTable(mainPanel);

        if(failedTestsTable.isPresent()) {
            failedTestLinks = initializeFailedTestLinks(failedTestsTable.get());
            failedTestTableEntries = initializeFailedTestTableEntries(failedTestsTable.get());
        }
        else {
            failedTestLinks = Collections.emptyList();
            failedTestTableEntries = Collections.emptyList();
        }

    }

    public boolean failedTestTableExists() { return failedTestsTable.isPresent(); }

    public List<FailedTestTableEntry> getFailedTestTableEntries() {
        return failedTestTableEntries;
    }

    public JUnitTestDetail openTestDetail(final String testName) {
        WebElement link = failedTestLinks.stream()
                .filter(failedTestLink -> failedTestLink.getText().equals(testName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(testName));
        return openPage(link, JUnitTestDetail.class);
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
        return failedTestsTableIndex >= 0 ? Optional.of(pageContentChildren.get(failedTestsTableIndex)) : Optional.empty();
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

        List<WebElement> detailPreElements = columns.get(0).findElements(By.cssSelector("div.failure-summary pre"));
        String errorDetails = detailPreElements.get(0).getText();
        String stackTrace = detailPreElements.get(1).getText();

        return new FailedTestTableEntry(testName, testLink, duration, age, errorDetails, stackTrace);
    }

}
