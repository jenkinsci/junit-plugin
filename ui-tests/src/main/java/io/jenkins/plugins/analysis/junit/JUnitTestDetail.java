package io.jenkins.plugins.analysis.junit;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.google.inject.Injector;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

/**
 * {@link PageObject} representing the detail view of a failed JUnit test.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class JUnitTestDetail extends PageObject {

    private final WebElement title;
    private final WebElement subTitle;

    private final Optional<WebElement> errorMessage;
    private final Optional<WebElement> stackTrace;
    private final Optional<WebElement> standardOutput;

    /**
     * Creates a new page object representing the junit detail view of a failed JUnit test.
     *
     * @param parent
     *          a finished build configured with a static analysis tool
     */
    public JUnitTestDetail(final Build parent) {
        super(parent, parent.url("testReport"));

        WebElement pageContent = getElement(By.cssSelector("#main-panel"));

        title = pageContent.findElement(By.cssSelector("h1"));
        subTitle = pageContent.findElement(By.cssSelector("p"));

        int errorMessageHeaderIndex = -1;
        int stackTraceHeaderIndex = -1;
        int standardOutputHeaderIndex = -1;
        List<WebElement> pageContentChildren = pageContent.findElements(By.cssSelector("*"));

        int counter = 0;
        for(WebElement element : pageContentChildren) {
            if(element.getTagName().equals("h3")) {
                if(element.getText().equals("Error Message")) {
                    errorMessageHeaderIndex = counter;
                }
                else if(element.getText().equals("Stacktrace")) {
                    stackTraceHeaderIndex = counter;
                }
                else if(element.getText().equals("Standard Output")) {
                    standardOutputHeaderIndex = counter;
                }
            }
            ++counter;
        }

        errorMessage = errorMessageHeaderIndex >= 0 ? Optional.of(pageContentChildren.get(errorMessageHeaderIndex + 1)) : Optional.empty();
        stackTrace = stackTraceHeaderIndex >= 0 ? Optional.of(pageContentChildren.get(stackTraceHeaderIndex + 1)) : Optional.empty();
        standardOutput = standardOutputHeaderIndex >= 0 ? Optional.of(pageContentChildren.get(standardOutputHeaderIndex + 1)) : Optional.empty();

    }

    //TODO: Junit here
    /**
     * Creates an instance of the page displaying the details of the issues. This constructor is used for injecting a
     * filtered instance of the page (e.g. by clicking on links which open a filtered instance of a AnalysisResult.
     *
     * @param injector
     *         the injector of the page
     * @param url
     *         the url of the page
     */
    @SuppressWarnings("unused") // Required to dynamically create page object using reflection
    public JUnitTestDetail(final Injector injector, final URL url) {
        super(injector, url);

        WebElement pageContent = getElement(By.cssSelector("#main-panel"));

        title = pageContent.findElement(By.cssSelector("h1"));
        subTitle = pageContent.findElement(By.cssSelector("p"));

        int errorMessageHeaderIndex = -1;
        int stackTraceHeaderIndex = -1;
        int standardOutputHeaderIndex = -1;
        List<WebElement> pageContentChildren = pageContent.findElements(By.cssSelector("*"));

        int counter = 0;
        for(WebElement element : pageContentChildren) {
            if(element.getTagName().equals("h3")) {
                if(element.getText().equals("Error Message")) {
                    errorMessageHeaderIndex = counter;
                }
                else if(element.getText().equals("Stacktrace")) {
                    stackTraceHeaderIndex = counter;
                }
                else if(element.getText().equals("Standard Output")) {
                    standardOutputHeaderIndex = counter;
                }
            }
            ++counter;
        }

        errorMessage = errorMessageHeaderIndex >= 0 ? Optional.of(pageContentChildren.get(errorMessageHeaderIndex + 1)) : Optional.empty();
        stackTrace = stackTraceHeaderIndex >= 0 ? Optional.of(pageContentChildren.get(stackTraceHeaderIndex + 1)) : Optional.empty();
        standardOutput = standardOutputHeaderIndex >= 0 ? Optional.of(pageContentChildren.get(standardOutputHeaderIndex + 1)) : Optional.empty();
    }

    /**
     * Returns the title of the detail view.
     *
     * @return the title of the detail view
     */
    public String getTitle() { return title.getText(); }

    /**
     * Returns the subtitle of the detail view, which is the test.
     *
     * @return the subtitle of the detail view
     */
    public String getSubTitle() { return subTitle.findElement(By.cssSelector("span")).getText() + subTitle.getText(); }

    /**
     * Returns the error message telling the user why the test has failed.
     *
     * @return the error message
     */
    public Optional<String> getErrorMessage() { return errorMessage.map(WebElement::getText); }

    /**
     * Returns the stack trace providing more information about the failed test.
     *
     * @return the stack trace of the failed test
     */
    public Optional<String> getStackTrace() { return stackTrace.map(WebElement::getText); }

    /**
     * Returns the standard output providing more information about the test.
     *
     * @return the standard output of the test
     */
    public Optional<String> getStandardOutput() { return standardOutput.map(WebElement::getText); }

}
