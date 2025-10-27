/*
 * The MIT License
 *
 * Copyright (c) 2009, Yahoo!, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tasks.test;

import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.junit.TestAction;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * A class that represents a general concept of a test result, without any
 * language or implementation specifics.
 * Subclasses must add @Exported annotation to the fields they want to export.
 *
 * @since 1.343
 */
public abstract class TestResult extends TestObject {
    private static final Logger LOGGER = Logger.getLogger(TestResult.class.getName());

    private static final PolicyFactory POLICY_DEFINITION = new HtmlPolicyBuilder()
            .allowElements("a")
            .allowUrlProtocols("https")
            .allowUrlProtocols("http")
            .allowAttributes("href")
            .onElements("a")
            .toFactory();

    private static final Pattern LINK_REGEX_PATTERN = Pattern.compile("\\b(https?://[^\\s)<>\"]+)");

    /**
     * If the concept of a parent action is important to a subclass, then it should
     * provide a non-noop implementation of this method.
     * @param action Action that points to the top level test result.
     */
    public void setParentAction(AbstractTestResultAction action) {}

    /**
     * Returns the action that points to the top level test result includes
     * this test result.
     *
     * @return action The action that points to the top level test result.
     */
    public AbstractTestResultAction getParentAction() {
        return getRun().getAction(AbstractTestResultAction.class);
    }

    /**
     * Request that the result update its counts of its children. Does not
     * require a parent action or owner or siblings. Subclasses should
     * implement this, unless they are *always* in a tallied state.
     */
    public void tally() {}

    /**
     * Sets the parent test result
     * @param parent Parent test result.
     */
    public void setParent(TestObject parent) {}

    /**
     * Gets the human readable title of this result object.
     *
     * @return the human readable title of this result object.
     */
    public /* abstract */ String getTitle() {
        return "";
    }

    /**
     * Mark a build as unstable if there are failures. Otherwise, leave the
     * build result unchanged.
     *
     * @return {@link Result#UNSTABLE} if there are test failures, null otherwise.
     *
     */
    public Result getBuildResult() {
        if (getFailCount() > 0) {
            return Result.UNSTABLE;
        } else {
            return null;
        }
    }

    /**
     * Time it took to run this test. In seconds.
     */
    @Override
    public /* abstract */ float getDuration() {
        return 0.0f;
    }

    /**
     * Gets the total number of passed tests.
     */
    @Override
    public /* abstract */ int getPassCount() {
        return 0;
    }

    /**
     * Gets the total number of failed tests.
     */
    @Override
    public /* abstract */ int getFailCount() {
        return 0;
    }

    /**
     * Gets the total number of skipped tests.
     */
    @Override
    public /* abstract */ int getSkipCount() {
        return 0;
    }

    /**
     * Gets the counter part of this {@link TestResult} in the previous run.
     *
     * @return null if no such counter part exists.
     */
    @Override
    public TestResult getPreviousResult() {
        Run<?, ?> b = getRun();
        if (b == null) {
            return null;
        }
        Job<?, ?> job = b.getParent();
        while (true) {
            b = b.getPreviousBuild();
            if (b == null) {
                return null;
            }
            try {
                AbstractTestResultAction r = b.getAction(getParentAction().getClass());
                if (r != null) {
                    TestResult result = r.findCorrespondingResult(this.getId());
                    if (result != null) {
                        return result;
                    }
                }
            } catch (RuntimeException e) {
                Run<?, ?> loggedBuild = b;
                LOGGER.log(
                        Level.WARNING,
                        e,
                        () -> "Failed to load (corrupt?) build " + job.getFullName() + " #" + loggedBuild.getNumber()
                                + ", skipping");
            }
        }
    }

    /**
     * Gets the counter part of this {@link TestResult} in the specified run.
     *
     * @return null if no such counter part exists.
     */
    @Override
    public TestResult getResultInRun(Run<?, ?> build) {
        AbstractTestResultAction tra = build.getAction(getParentAction().getClass());
        if (tra == null) {
            tra = build.getAction(AbstractTestResultAction.class);
        }
        return (tra == null) ? null : tra.findCorrespondingResult(this.getId());
    }

    /**
     * Gets the "children" of this test result that failed
     * @return the children of this test result, if any, or an empty collection
     */
    public Collection<? extends TestResult> getFailedTests() {
        return Collections.emptyList();
    }

    /**
     * Gets the "children" of this test result that passed
     * @return the children of this test result, if any, or an empty collection
     */
    public Collection<? extends TestResult> getPassedTests() {
        return Collections.emptyList();
    }

    /**
     * Gets the "children" of this test result that were skipped
     * @return the children of this test result, if any, or an empty list
     */
    public Collection<? extends TestResult> getSkippedTests() {
        return Collections.emptyList();
    }

    /**
     * If this test failed, then return the build number
     * when this test started failing.
     *
     * @return the build number when this test started failing.
     */
    public int getFailedSince() {
        return 0;
    }

    /**
     * If this test failed, then return the run
     * when this test started failing.
     *
     * @return the run when this test started failing.
     */
    public Run<?, ?> getFailedSinceRun() {
        return null;
    }

    /**
     * The stdout of this test.
     *
     * @return the stdout of this test.
     */
    public String getStdout() {
        return "";
    }

    /**
     * The stderr of this test.
     *
     * @return the stderr of this test.
     */
    public String getStderr() {
        return "";
    }

    /**
     * If there was an error or a failure, this is the stack trace, or otherwise null.
     *
     * @return the stack trace of the error or failure.
     */
    public String getErrorStackTrace() {
        return "";
    }

    /**
     * If there was an error or a failure, this is the text from the message.
     *
     * @return the message of the error or failure.
     */
    public String getErrorDetails() {
        return "";
    }

    public Map<String, String> getProperties() {
        return Collections.emptyMap();
    }

    /**
     * @return true if the test was not skipped and did not fail, false otherwise.
     */
    public boolean isPassed() {
        return ((getSkipCount() == 0) && (getFailCount() == 0));
    }

    public String toPrettyString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("Name: ").append(this.getName()).append(", ");
        sb.append("Result: ").append(this.getBuildResult()).append(",\n");
        sb.append("Total Count: ").append(this.getTotalCount()).append(", ");
        sb.append("Fail: ").append(this.getFailCount()).append(", ");
        sb.append("Skipt: ").append(this.getSkipCount()).append(", ");
        sb.append("Pass: ").append(this.getPassCount()).append(",\n");
        sb.append("Test Result Class: ").append(this.getClass().getName()).append(" }\n");
        return sb.toString();
    }

    /**
     * Escapes "&" and "<" characters in the provided text, with the goal of preventing any HTML tags present
     * in the text from being rendered / interpreted as HTML.
     *
     * @param text The text to sanitize
     * @return The sanitized text
     */
    private static String naiveHtmlSanitize(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;");
    }

    private static String escapeHtmlAndMakeLinksClickable(String text) {
        if (text == null) {
            return null;
        }

        StringBuilder annotatedTxtBuilder = new StringBuilder();

        Matcher linkMatcher = LINK_REGEX_PATTERN.matcher(text);

        // Goal: Find all the things that look like URLs in the text, and convert them to clickable
        // <a> tags so they render as clickable links when viewing test output.

        int lastMatchEndIdxExclusive = 0;
        while (linkMatcher.find()) {
            // Group 1 in the regex is just the URL
            String linkUrl = linkMatcher.group(1);

            // Sanitize the final HTML tag we produce to make sure there's nothing malicious in there
            String sanitizedLinkHtmlTag =
                    POLICY_DEFINITION.sanitize(String.format("<a href=\"%s\">%s</a>", linkUrl, linkUrl));

            annotatedTxtBuilder
                    // Append all the chars in-between the last link and this current one, and run that substring
                    // through naive HTML sanitization since we don't want anything other than the <a> tags we're
                    // spawning to actually render as HTML.
                    .append(naiveHtmlSanitize(text.substring(lastMatchEndIdxExclusive, linkMatcher.start())))
                    // Append our clickable <a> tag
                    .append(sanitizedLinkHtmlTag);

            lastMatchEndIdxExclusive = linkMatcher.end();
        }

        // Finish up by sanitizing + appending all the remaining text after the last URL we found
        annotatedTxtBuilder.append(naiveHtmlSanitize(text.substring(lastMatchEndIdxExclusive)));

        return annotatedTxtBuilder.toString();
    }

    /**
     * All JUnit test output (error message, stack trace, std out, std err) shown on the single test case result page
     * is passed through this method when the page is rendered, which processes / sanitizes the text to make it
     * suitable for HTML rendering. Attempts to auto-detect URLs in the test output and convert them to clickable
     * links for convenience. All other HTML will be escaped.
     * <p>
     * Additionally, passes the test output through all the TestActions associated with this result, giving them a
     * chance to apply their own custom rendering / transformations.
     * <p>
     * Note: The test output shown when expanding cases on the full "testReport" page is *not* passed through this
     * method, and instead relies on the Jelly "escape-by-default" flag to escape test output for HTML rendering,
     * which is why links are not clickable in that context.
     *
     * @param text Text to use to annotate the actions.
     * @return the provided text HTML-escaped.
     */
    public String annotate(String text) {
        if (text == null) {
            return null;
        }

        text = escapeHtmlAndMakeLinksClickable(text);

        for (TestAction action : getTestActions()) {
            text = action.annotate(text);
        }
        return text;
    }
}
