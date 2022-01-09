package io.jenkins.plugins.analysis.junit;

import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

public class JUnitBuildSummary extends PageObject {

    WebElement testResultLinkElement;
    List<WebElement> failedTests;
    String buildStatus;

    // TODO: was ist diese ID ?
    private final String id;

    public JUnitBuildSummary(final Build parent, String id) {
        super(parent, parent.url(id));
        this.id = id;

        testResultLinkElement = getElement(By.linkText("Test Result"));
        WebElement table = getElement(By.cssSelector("ul[style='list-style-type: none; margin: 0;']"));
        failedTests = table.findElements(By.cssSelector(".shown"));

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

    public WebElement getTestResultLink() {
        return testResultLinkElement;
    }

    /**
     * Returns the texts of the failed tests.
     *
     * @return the details
     */
    public List<String> getFailedTestNames() {
        return failedTests.stream().map(WebElement::getText).collect(Collectors.toList());
    }

    public String getBuildStatus() {
        return buildStatus;
    }

    private boolean hasBuildStatus(String buildStatus) {
        return !driver.findElements(By.cssSelector("svg[tooltip=" + buildStatus + "]")).isEmpty();
    }
}
