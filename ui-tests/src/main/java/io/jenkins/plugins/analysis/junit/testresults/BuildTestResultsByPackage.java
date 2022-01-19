package io.jenkins.plugins.analysis.junit.testresults;

import java.net.URL;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.google.inject.Injector;

import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.testresults.tableentry.ClassTableEntry;

public class BuildTestResultsByPackage extends TestResultsWithFailedTestTable {

    private final List<WebElement> classLinks;
    private final List<ClassTableEntry> classTableEntries;

    public BuildTestResultsByPackage(final Build parent) {
        super(parent);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        classLinks = TestResultsTableUtil.getLinksOfTableItems(mainPanel);
        classTableEntries = initializeClassTableEntries(mainPanel);
    }

    public BuildTestResultsByPackage(final Injector injector, final URL url) {
        super(injector, url);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        classLinks = TestResultsTableUtil.getLinksOfTableItems(mainPanel);
        classTableEntries = initializeClassTableEntries(mainPanel);
    }

    public List<ClassTableEntry> getClassTableEntries() {
        return classTableEntries;
    }

    public BuildTestResultsByClass openTestDetailView(final String className) {
        WebElement link = classLinks.stream()
                .filter(classLink -> classLink.getText().equals(className))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(className));
        return openPage(link, BuildTestResultsByClass.class);
    }

    private List<ClassTableEntry> initializeClassTableEntries(final WebElement mainPanel) {
        return TestResultsTableUtil.getTableItemsWithoutHeader(mainPanel).stream()
                .map(this::webElementToClassTableEntry)
                .collect(Collectors.toList());
    }

    private ClassTableEntry webElementToClassTableEntry(final WebElement trElement) {
        List<WebElement> columns = trElement.findElements(By.cssSelector("td"));

        WebElement linkElement = columns.get(0).findElement(By.cssSelector("a.model-link.inside"));
        String className = linkElement.getText();
        String classLink = linkElement.getAttribute("href");
        String durationString = columns.get(1).getText().trim();
        int duration = Integer.parseInt(durationString.substring(0, durationString.length() - " ms".length()));
        int fail = Integer.parseInt(columns.get(2).getText());
        String failDiffString = columns.get(3).getText();
        Optional<Integer> failDiff = failDiffString.isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(failDiffString));
        int skip = Integer.parseInt(columns.get(4).getText());
        String skipDiffString = columns.get(5).getText();
        Optional<Integer> skipDiff = skipDiffString.isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(skipDiffString));
        int pass = Integer.parseInt(columns.get(6).getText());
        String passDiffString = columns.get(5).getText();
        Optional<Integer> passDiff = passDiffString.isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(passDiffString));
        int total = Integer.parseInt(columns.get(8).getText());
        int totalDiff = Integer.parseInt(columns.get(9).getText());

        return new ClassTableEntry(className, classLink, duration, fail, failDiff, skip, skipDiff,
                pass, passDiff, total, totalDiff);
    }
}
