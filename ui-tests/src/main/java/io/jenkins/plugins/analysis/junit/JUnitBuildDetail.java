package io.jenkins.plugins.analysis.junit;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

/**
 * {@link PageObject} representing the detail view of a build's failed Unit tests.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class JUnitBuildDetail extends PageObject {

    // TODO: was ist diese ID ?
    private final String id;

    private final WebElement failedTestsTable;
    private final WebElement allTestsTable;
    //private final Map<FailedTest, WebElement> failedTestsTableElementsByFailedTest;

    private final List<WebElement> testDetailLinks;
    private final List<WebElement> packageDetailLinks;

    private final List<FailedTest> failedTests;
    private final List<Test> allTests;

    /**
     * Creates a new page object representing the detail view of a build's failed Unit tests.
     *
     * @param parent
     *         a finished build configured with a static analysis tool
     * @param id
     *         the type of the result page (e.g. simion, checkstyle, cpd, etc.)
     */
    public JUnitBuildDetail(final Build parent, final String id) {
        super(parent, parent.url(id));
        this.id = id;

        WebElement pageContent = getElement(By.cssSelector("#main-panel"));

        int failedTestsTableIndex = -1;
        int allTestsTableIndex = -1;
        List<WebElement> pageContentChildren = pageContent.findElements(By.cssSelector("*"));

        int counter = 0;
        for (WebElement element : pageContentChildren) {
            if (element.getTagName().equals("h2")) {
                if (element.getText().equals("All Failed Tests")) {
                    failedTestsTableIndex = counter;
                }
                else if (element.getText().equals("All Tests")) {
                    allTestsTableIndex = counter;
                }
            }
            ++counter;
        }

        failedTestsTable = pageContentChildren.get(failedTestsTableIndex);
        allTestsTable = pageContentChildren.get(allTestsTableIndex);

        List<WebElement> failedTestsTableItems = failedTestsTable.findElements(By.cssSelector("tbody tr"));
        List<WebElement> failedTestsTableItemsWithoutHeader = failedTestsTableItems.subList(1,
                failedTestsTableItems.size());

        testDetailLinks = failedTestsTableItemsWithoutHeader.stream()
                .map(trElement -> trElement.findElements(By.cssSelector("td")).get(0).findElement(By.cssSelector("a.model-link.inside")))
                .collect(Collectors.toList());

        failedTests = failedTestsTableItemsWithoutHeader.stream()
                .map(this::webElementToFailedTest)
                .collect(Collectors.toList());

        List<WebElement> allTestsTableBodies = allTestsTable.findElements(By.cssSelector("tbody"));
        WebElement allTestsTableBodyWithoutHeader = allTestsTableBodies.get(1);
        List<WebElement> allTestsTableItemsWithoutHeader = allTestsTableBodyWithoutHeader.findElements(By.cssSelector("tr"));

        packageDetailLinks = allTestsTableItemsWithoutHeader.stream()
                .map(trElement -> trElement.findElements(By.cssSelector("td")).get(0).findElement(By.cssSelector("a.model-link.inside")))
                .collect(Collectors.toList());

        allTests = allTestsTableItemsWithoutHeader.stream()
                .map(this::webElementToTest)
                .collect(Collectors.toList());

    }

    private FailedTest webElementToFailedTest(final WebElement trElement) {
        List<WebElement> columns = trElement.findElements(By.cssSelector("td"));

        String name = columns.get(0).findElement(By.cssSelector("a.model-link.inside")).getText();
        String durationString = columns.get(1).getText().trim();
        int duration = Integer.parseInt(durationString.substring(0, durationString.length() - " ms".length()));
        int age = Integer.parseInt(columns.get(2).findElement(By.cssSelector("a")).getText());

        return new FailedTest(name, duration, age);
    }

    private Test webElementToTest(final WebElement trElement) {
        List<WebElement> columns = trElement.findElements(By.cssSelector("td"));
        int currentColumn = 0;

        String packag3 = columns.get(0).findElement(By.cssSelector("a.model-link.inside")).getText();
        String packageLink = columns.get(0).findElement(By.cssSelector("a.model-link.inside")).getAttribute("href");
        String durationString = columns.get(1).getText().trim();
        int duration = Integer.parseInt(durationString.substring(0, durationString.length() - " ms".length()));
        int fail = Integer.parseInt(columns.get(2).getText());
        int failDiff = Integer.parseInt(columns.get(3).getText());
        int skip = Integer.parseInt(columns.get(4).getText());
        int skipDiff = Integer.parseInt(columns.get(5).getText());
        int pass = Integer.parseInt(columns.get(6).getText());
        int passDiff = Integer.parseInt(columns.get(7).getText());
        int total = Integer.parseInt(columns.get(8).getText());
        int totalDiff = Integer.parseInt(columns.get(9).getText());

        return new Test(packag3, packageLink, duration, fail, failDiff, skip, skipDiff,
                pass, passDiff, total, totalDiff);
    }

    /**
     * Returns the number of failures of this junit run.
     *
     * @return the number of failures
     */
    public int getNumberOfFailures() {
        return failedTests.size();
    }

    public List<FailedTest> getFailedTests() {
        return failedTests;
    }

    public List<Test> getAllTests() {
        return allTests;
    }

    public JUnitTestDetail openTestDetailView(final String testName) {
        WebElement link = testDetailLinks.stream()
                .filter(failedTestLink -> failedTestLink.getText().equals(testName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(testName));

        return openPage(link, JUnitTestDetail.class);
    }

    //TODO: open package details?

    private <T extends PageObject> T openPage(final WebElement link, final Class<T> type) {
        String href = link.getAttribute("href");
        T result = newInstance(type, injector, url(href), id);
        link.click();
        return result;
    }

}


class FailedTest {

    private final String name;

    private final int duration;

    private final int age;

    public FailedTest(final String name, final int duration, final int age) {
        this.name = name;
        this.duration = duration;
        this.age = age;
    }

    public String getName() { return name; }

    public int getDuration() { return duration; }

    public int getAge() { return age; }

}

class Test {

    private final String packag3;

    private final String packageLink;

    private final int duration;

    private final int fail;

    private final int failDiff;

    private final int skip;

    private final int skipDiff;

    private final int pass;

    private final int passDiff;

    private final int total;

    private final int totalDiff;

    public Test(final String packag3, final String packageLink, final int duration, final int fail, final int failDiff,
            final int skip, final int skipDiff, final int pass,
            final int passDiff, final int total, final int totalDiff) {
        this.packag3 = packag3;
        this.packageLink = packageLink;
        this.duration = duration;
        this.fail = fail;
        this.failDiff = failDiff;
        this.skip = skip;
        this.skipDiff = skipDiff;
        this.pass = pass;
        this.passDiff = passDiff;
        this.total = total;
        this.totalDiff = totalDiff;
    }

    public String getPackag3() {
        return packag3;
    }

    public String getPackageLink() {
        return packageLink;
    }

    public int getDuration() {
        return duration;
    }

    public int getFail() {
        return fail;
    }

    public int getFailDiff() {
        return failDiff;
    }

    public int getSkip() {
        return skip;
    }

    public int getSkipDiff() {
        return skipDiff;
    }

    public int getPass() {
        return pass;
    }

    public int getPassDiff() {
        return passDiff;
    }

    public int getTotal() {
        return total;
    }

    public int getTotalDiff() {
        return totalDiff;
    }
}
