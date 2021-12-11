package hudson.tasks.test.helper;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class LatestTestResultLink extends AbstractTestResultLink<LatestTestResultLink> {

    public static final String LATEST_TEST_RESULT_STRING = "Latest Test Result";
    public static final String LATEST_AGGREGATED_TEST_RESULT_STRING = "Latest Aggregated Test Result";

    LatestTestResultLink(HtmlAnchor testResultLink) {
        super(testResultLink);
    }

    public LatestTestResultLink assertHasLatestTestResultText() {
        assertThat(testResultLink.getTextContent(), containsString(LATEST_TEST_RESULT_STRING));
        return this;
    }

    public LatestTestResultLink assertHasLatestAggregatedTestResultText() {
        assertThat(testResultLink.getTextContent(), containsString(LATEST_AGGREGATED_TEST_RESULT_STRING));
        return this;
    }
}
