package io.jenkins.plugins.analysis.junit;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

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

    private final WebElement errorMessage;
    private final WebElement stackTrace;

    /**
     * Creates a new page object representing the junit detail view of a failed JUnit test.
     *
     * @param parent
     *          a finished build configured with a static analysis tool
     */
    public JUnitTestDetail(final Build parent) {
        super(parent, parent.url("testReport"));

        WebElement pageContent = getElement(By.cssSelector("#main-panel"));

        title = pageContent.findElement(By.cssSelector(".result-failed"));
        subTitle = pageContent.findElement(By.cssSelector("p"));

        int errorMessageHeaderIndex = -1;
        int stackTraceIndex = -1;
        List<WebElement> pageContentChildren = pageContent.findElements(By.cssSelector("*"));

        int counter = 0;
        for(WebElement element : pageContentChildren) {
            if(element.getTagName().equals("h3")) {
                if(element.getText().equals("Error Message")) {
                    errorMessageHeaderIndex = counter;
                }
                else if(element.getText().equals("Stacktrace")) {
                    stackTraceIndex = counter;
                }
            }
            ++counter;
        }

        errorMessage = pageContentChildren.get(errorMessageHeaderIndex);
        stackTrace = pageContentChildren.get(stackTraceIndex);

    }

    /**
     * Returns the title of the detail view.
     *
     * @return the title of the detail view
     */
    public String getTitle() { return title.getText(); }

    /**
     * Returns the subtitle of the detail view, which is the failed test.
     *
     * @return the subtitle of the detail view
     */
    public String getSubTitle() { return subTitle.findElement(By.cssSelector("span")).getText() + subTitle.getText(); }

    /**
     * Returns the error message telling the user why the test has failed.
     *
     * @return the error message
     */
    public String getErrorMessage() { return errorMessage.getText(); }

    /**
     * Returns the stack trace providing more information about the failed test.
     *
     * @return the stack trace of the failed test
     */
    public String getStackTrace() { return stackTrace.getText(); }



}
