package io.jenkins.plugins.analysis.junit;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

public class JUnitBuildSummary extends PageObject {

    WebElement title;
    WebElement failuresCounter;
    List<WebElement> failedTests;

    // TODO: was ist diese ID ?
    private final String id;

    public JUnitBuildSummary(final Build parent, String id) {
        super(parent, parent.url(id));
        this.id = id;

        WebElement table = getElement(By.cssSelector("td:contains('Test Result')"));

        title = table.findElement(By.linkText("Test Result"));
        //        failuresCounter = table.findElement(By.)


//        By.id(id + "-summary")
    }

    getTestResultLink() {

    }

    getTestResultSummaryText() {

    }

    getFailedTestDetailLinks() {

    }
}
