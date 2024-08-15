package hudson.tasks.test.helper;

import java.io.IOException;
import org.htmlunit.html.HtmlPage;
import org.xml.sax.SAXException;

public class ProjectPage extends AbstractPage {

    public ProjectPage(HtmlPage projectPage) {
        super(projectPage);
    }


    public LatestTestResultLink getLatestTestReportLink() throws IOException, SAXException {
        return new LatestTestResultLink(getTestReportAnchor(TEST_REPORT_URL));
    }

    public LatestTestResultLink getLatestAggregatedTestReportLink() throws IOException, SAXException {
        return new LatestTestResultLink(getTestReportAnchor(AGGREGATED_TEST_REPORT_URL));
    }

    protected String getHrefFromTestUrl(String testUrl) {
        return "lastCompletedBuild/" + testUrl + "/";
    }

    public void assertNoTestReportLink() {
        assertNoLink(TEST_REPORT_URL);
    }

    public void assertNoAggregatedTestReportLink() {
        assertNoLink(AGGREGATED_TEST_REPORT_URL);
    }

}
