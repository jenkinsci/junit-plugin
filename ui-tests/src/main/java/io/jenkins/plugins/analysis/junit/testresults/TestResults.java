package io.jenkins.plugins.analysis.junit.testresults;

import java.net.URL;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.google.inject.Injector;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

/**
 * Abstract {@link PageObject} base class for test results pages.
 *
 * @author Nikolas Paripovic
 * @author Michael Müller
 */
public abstract class TestResults extends PageObject {

    private final WebElement numberOfFailuresElement;
    private final WebElement numberOfTestsElement;

    /**
     * Creates a new abstract page object as a base class for test results pages.
     *
     * @param parent
     *         a finished build configured with a static analysis tool
     */
    public TestResults(final Build parent) {
        super(parent, parent.url("testReport"));
        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        numberOfFailuresElement = initializeNumberOfFailuresElement(mainPanel);
        numberOfTestsElement = initializeNumberOfTestsElement(mainPanel);
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
    public TestResults(final Injector injector, final URL url) {
        super(injector, url);
        WebElement mainPanel = getElement(By.cssSelector("#main-panel"));
        numberOfFailuresElement = initializeNumberOfFailuresElement(mainPanel);
        numberOfTestsElement = initializeNumberOfTestsElement(mainPanel);
    }

    /**
     * Gets the number of failed tests in this build.
     * @return the number of failures
     */
    public int getNumberOfFailures() {
        String text = numberOfFailuresElement.getText().trim();
        return Integer.parseInt(text.substring(0, text.indexOf(' ')));
    }

    /**
     * Gets the number of processed tests in this build.
     * @return the number of tests
     */
    public int getNumberOfTests() {
        String text = numberOfTestsElement.getText().trim();
        return Integer.parseInt(text.substring(0, text.indexOf(' ')));
    }

    protected <T extends PageObject> T openPage(final WebElement link, final Class<T> type) {
        String href = link.getAttribute("href");
        link.click();
        return newInstance(type, injector, url(href));
    }

    private WebElement initializeNumberOfTestsElement(final WebElement mainPanel) {
        List<WebElement> testsNumberElements = getTestsNumberElements(mainPanel);
        return testsNumberElements.get(testsNumberElements.size() - 1);
    }
    
    private WebElement initializeNumberOfFailuresElement(final WebElement mainPanel) {
        List<WebElement> testsNumberElements = getTestsNumberElements(mainPanel);
        return testsNumberElements.get(0);
    }

    private List<WebElement> getTestsNumberElements(final WebElement mainPanel) {
        return mainPanel.findElements(By.cssSelector("h1 + div div"));
    }
}
