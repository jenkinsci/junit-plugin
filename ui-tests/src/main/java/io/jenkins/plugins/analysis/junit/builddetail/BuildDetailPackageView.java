package io.jenkins.plugins.analysis.junit.builddetail;

import java.net.URL;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.google.inject.Injector;

import org.jenkinsci.test.acceptance.po.Build;

import io.jenkins.plugins.analysis.junit.builddetail.tableentry.PackageTableEntry;

public class BuildDetailPackageView extends BuildDetailViewIncludingFailedTestTable {

    private final List<WebElement> packageLinks;
    private final List<PackageTableEntry> packageTableEntries;

    public BuildDetailPackageView(final Build parent) {
        super(parent);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        packageLinks = TestResultTableUtil.getLinksOfTableItems(mainPanel);
        packageTableEntries = initializePackageTableEntries(mainPanel);
    }

    public BuildDetailPackageView(final Injector injector, final URL url) {
        super(injector, url);

        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        packageLinks = TestResultTableUtil.getLinksOfTableItems(mainPanel);
        packageTableEntries = initializePackageTableEntries(mainPanel);
    }

    public List<PackageTableEntry> getPackageTableEntries() {
        return packageTableEntries;
    }

    public BuildDetailClassView openClassDetailView(final String packageName) {
        WebElement link = packageLinks.stream()
                .filter(packageLink -> packageLink.getText().equals(packageName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(packageName));
        return openPage(link, BuildDetailClassView.class);
    }

    private List<PackageTableEntry> initializePackageTableEntries(final WebElement mainPanel) {
        return TestResultTableUtil.getTableItemsWithoutHeader(mainPanel).stream()
                .map(this::webElementToPackageTableEntry)
                .collect(Collectors.toList());
    }

    private PackageTableEntry webElementToPackageTableEntry(final WebElement trElement) {
        List<WebElement> columns = trElement.findElements(By.cssSelector("td"));

        WebElement linkElement = columns.get(0).findElement(By.cssSelector("a.model-link.inside"));
        String packageName = linkElement.getText();
        String packageLink = linkElement.getAttribute("href");
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

        return new PackageTableEntry(packageName, packageLink, duration, fail, failDiff, skip, skipDiff,
                pass, passDiff, total, totalDiff);
    }


}
