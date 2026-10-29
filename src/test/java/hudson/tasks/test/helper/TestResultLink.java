package hudson.tasks.test.helper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import org.htmlunit.html.HtmlAnchor;

public class TestResultLink extends AbstractTestResultLink<TestResultLink> {

    public static final String TEST_RESULT_STRING = "Tests";
    public static final String AGGREGATED_TEST_RESULT_STRING = "Aggregated Test Result";

    TestResultLink(HtmlAnchor testResultLink) {
        super(testResultLink);
    }

    public TestResultLink assertHasTestResultText() {
        assertThat(testResultLink.getTextContent(), containsString(TEST_RESULT_STRING));
        return this;
    }

    public TestResultLink assertHasAggregatedTestResultText() {
        assertThat(testResultLink.getTextContent(), containsString(AGGREGATED_TEST_RESULT_STRING));
        return this;
    }
}
