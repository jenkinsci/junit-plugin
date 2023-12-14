package hudson.tasks.test.helper;

import org.htmlunit.html.HtmlPage;

public class TestResultsPage {
    protected HtmlPage htmlPage;

    public TestResultsPage(HtmlPage htmlPage) {
        this.htmlPage = htmlPage;
    }

    public void hasLinkToTest(String testName) {
        htmlPage.getAnchorByText(testName);
    }

    public void hasLinkToTestResultOfBuild(String projectName, int buildNumber) {
        htmlPage.getAnchorByHref("/jenkins/job/" + projectName + "/" + buildNumber + "/testReport/");
    }
}
