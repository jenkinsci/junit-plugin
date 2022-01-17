package io.jenkins.plugins.analysis.junit.builddetail;

import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.google.inject.Injector;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;


public abstract class BuildDetailView extends PageObject {

    private final WebElement numberOfFailuresElement;
    private final WebElement numberOfTestsElement;

    public BuildDetailView(final Build parent) {
        super(parent, parent.url("testReport"));

        WebElement pageContent = getElement(By.cssSelector("#main-panel"));

        numberOfFailuresElement = pageContent.findElements(By.cssSelector("h1 + div div")).get(0);
        numberOfTestsElement = pageContent.findElements(By.cssSelector("h1 + div div")).get(2);

    }

    public BuildDetailView(final Injector injector, final URL url) {
        super(injector, url);

        WebElement pageContent = getElement(By.cssSelector("#main-panel"));

        numberOfFailuresElement = pageContent.findElements(By.cssSelector("h1 + div div")).get(0);
        numberOfTestsElement = pageContent.findElements(By.cssSelector("h1 + div div")).get(2);
    }

    public int getNumberOfFailures() {
        String text = numberOfFailuresElement.getText().trim();
        return Integer.parseInt(text.substring(0, text.indexOf(' ')));
    }

    public int getNumberOfTests() {
        String text = numberOfTestsElement.getText().trim();
        return Integer.parseInt(text.substring(0, text.indexOf(' ')));
    }

    protected <T extends PageObject> T openPage(final WebElement link, final Class<T> type) {
        String href = link.getAttribute("href");
        link.click();
        return newInstance(type, injector, url(href));
    }
}
