package hudson.tasks.test.helper;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.util.List;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import org.xml.sax.SAXException;

public abstract class AbstractPage {
    public static final String AGGREGATED_TEST_REPORT_URL = "aggregatedTestReport";
    public static final String TEST_REPORT_URL = "testReport";

    protected HtmlPage htmlPage;

    public AbstractPage(HtmlPage htmlPage) {
        this.htmlPage = htmlPage;
    }

    protected abstract String getHrefFromTestUrl(String testUrl);

    public HtmlAnchor getTestReportAnchor(String testUrl) throws IOException, SAXException {
        return htmlPage.getAnchorByHref(getHrefFromTestUrl(testUrl));
    }

    public void assertNoLink(String url) {
        List<HtmlAnchor> anchors = htmlPage.getAnchors();
        boolean found = false;
        String fullUrl = getHrefFromTestUrl(url);
        for (HtmlAnchor anchor : anchors) {
            if (fullUrl.equals(anchor.getHrefAttribute())) {
                found = true;
                break;
            }
        }
        assertFalse(found, "Link to " + fullUrl + " found, but should not be present");
    }
}
