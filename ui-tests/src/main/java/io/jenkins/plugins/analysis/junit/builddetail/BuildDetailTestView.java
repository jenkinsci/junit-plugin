package io.jenkins.plugins.analysis.junit.builddetail;

import java.net.URL;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.google.inject.Injector;

import org.jenkinsci.test.acceptance.po.Build;
import io.jenkins.plugins.analysis.junit.JUnitTestDetail;
import io.jenkins.plugins.analysis.junit.builddetail.tableentry.TestTableEntry;

public class BuildDetailTestView extends BuildDetailView {

    private final List<WebElement> testLinks;
    private final List<TestTableEntry> testTableEntries;

    public BuildDetailTestView(final Build parent) {
        super(parent);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        testLinks = TestResultTableUtil.getLinksOfTableItems(mainPanel);
        testTableEntries = initializeTestTableEntries(mainPanel);
    }

    public BuildDetailTestView(final Injector injector, final URL url) {
        super(injector, url);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        testLinks = TestResultTableUtil.getLinksOfTableItems(mainPanel);
        testTableEntries = initializeTestTableEntries(mainPanel);
    }

    public List<TestTableEntry> getTestTableEntries() {
        return testTableEntries;
    }

    public JUnitTestDetail openTestDetail(final String testName) {
        WebElement link = testLinks.stream()
                .filter(classLink -> classLink.getText().equals(testName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(testName));
        return openPage(link, JUnitTestDetail.class);
    }

    private List<TestTableEntry> initializeTestTableEntries(final WebElement mainPanel) {
        return TestResultTableUtil.getTableItemsWithoutHeader(mainPanel).stream()
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
