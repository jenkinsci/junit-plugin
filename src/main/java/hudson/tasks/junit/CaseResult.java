/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Seiji Sogabe, Tom Huybrechts, Yahoo!, Inc.
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
package hudson.tasks.junit;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Run;
import hudson.tasks.test.TestResult;
import hudson.util.TextFile;
import io.jenkins.plugins.junit.storage.FileJunitTestResultStorage;
import io.jenkins.plugins.junit.storage.JunitTestResultStorage;
import io.jenkins.plugins.junit.storage.TestResultImpl;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.commons.io.FileUtils;
import org.dom4j.Element;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.export.Exported;

/**
 * One test result.
 *
 * Non-final since 1.526
 *
 * @author Kohsuke Kawaguchi
 */
public class CaseResult extends TestResult implements Comparable<CaseResult> {
    private static final Logger LOGGER = Logger.getLogger(CaseResult.class.getName());
    private float duration;
    /**
     * Start time in epoch milliseconds - default is -1 for unset
     */
    private long startTime;
    /**
     * In JUnit, a test is a method of a class. This field holds the fully qualified class name
     * that the test was in.
     */
    private String className;
    /**
     * This field retains the method name.
     */
    private String testName;

    private transient String safeName;
    private boolean skipped;
    private boolean keepTestNames;
    private String skippedMessage;
    private String errorStackTrace;
    private String errorDetails;
    private Map<String, String> properties;

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Specific method to restore it")
    private transient SuiteResult parent;

    @SuppressFBWarnings(
            value = "IS2_INCONSISTENT_SYNC",
            justification = "Not guarded, though read in synchronized blocks")
    private transient ClassResult classResult;

    /**
     * Some tools report stdout and stderr at testcase level (such as Maven surefire plugin), others do so at
     * the suite level (such as Ant JUnit task.)
     *
     * If these information are reported at the test case level, these fields are set,
     * otherwise null, in which case {@link SuiteResult#stdout}.
     */
    private String stdout, stderr;

    /**
     * This test has been failing since this build number (not id.)
     *
     * If {@link #isPassed() passing}, this field is left unused to 0.
     */
    private int failedSince;

    private static float parseTime(Element testCase) {
        String time = testCase.attributeValue("time");
        return new TimeToFloat(time).parse();
    }

    /**
     * Used to create a fake failure, when Hudson fails to load data from XML files.
     * Public since 1.526.
     *
     * @param parent Parent result.
     * @param testName Test name.
     * @param errorStackTrace Error stack trace.
     */
    public CaseResult(SuiteResult parent, String testName, String errorStackTrace) {
        this(parent, testName, errorStackTrace, "");
    }

    public CaseResult(SuiteResult parent, String testName, String errorStackTrace, String errorDetails) {
        this.className = parent == null ? "unnamed" : parent.getName();
        this.testName = testName;
        this.errorStackTrace = errorStackTrace;
        this.errorDetails = errorDetails;
        this.parent = parent;
        this.stdout = null;
        this.stderr = null;
        this.duration = 0.0f;
        this.startTime = -1;
        this.skipped = false;
        this.skippedMessage = null;
        this.properties = Collections.emptyMap();
        this.keepTestNames = false;
    }

    @Restricted(Beta.class)
    public CaseResult(
            SuiteResult parent,
            String className,
            String testName,
            String errorDetails,
            String skippedMessage,
            float duration,
            String stdout,
            String stderr,
            String stacktrace) {
        this.className = className;
        this.testName = testName;
        this.errorStackTrace = stacktrace;
        this.errorDetails = errorDetails;
        this.parent = parent;
        this.stdout = fixNULs(stdout);
        this.stderr = fixNULs(stderr);
        this.duration = duration;
        this.startTime = -1;

        this.skipped = skippedMessage != null;
        this.skippedMessage = skippedMessage;
        this.properties = Collections.emptyMap();
        this.keepTestNames = false;
    }

    @Deprecated
    CaseResult(
            SuiteResult parent,
            Element testCase,
            String testClassName,
            boolean keepLongStdio,
            boolean keepProperties,
            boolean keepTestNames) {
        this(
                parent,
                testCase,
                testClassName,
                StdioRetention.fromKeepLongStdio(keepLongStdio),
                keepProperties,
                keepTestNames);
    }

    CaseResult(
            SuiteResult parent,
            Element testCase,
            String testClassName,
            StdioRetention stdioRetention,
            boolean keepProperties,
            boolean keepTestNames) {
        // schema for JUnit report XML format is not available in Ant,
        // so I don't know for sure what means what.
        // reports in http://www.nabble.com/difference-in-junit-publisher-and-ant-junitreport-tf4308604.html#a12265700
        // indicates that maybe I shouldn't use @classname altogether.

        // String cn = testCase.attributeValue("classname");
        // if(cn==null)
        //    // Maven seems to skip classname, and that shows up in testSuite/@name
        //    cn = parent.getName();

        /*
           According to http://www.nabble.com/NPE-(Fatal%3A-Null)-in-recording-junit-test-results-td23562964.html
           there's some odd-ball cases where testClassName is null but
           @name contains fully qualified name.
        */
        String nameAttr = testCase.attributeValue("name");
        if (testClassName == null && nameAttr.contains(".")) {
            testClassName = nameAttr.substring(0, nameAttr.lastIndexOf('.'));
            nameAttr = nameAttr.substring(nameAttr.lastIndexOf('.') + 1);
        }

        className = testClassName;
        testName = nameAttr;
        errorStackTrace = getError(testCase);
        errorDetails = getErrorMessage(testCase);
        this.parent = parent;
        duration = clampDuration(parseTime(testCase));
        this.startTime = -1;
        skipped = isMarkedAsSkipped(testCase);
        skippedMessage = getSkippedMessage(testCase);
        @SuppressWarnings("LeakingThisInConstructor")
        Collection<CaseResult> _this = Collections.singleton(this);
        stdout = fixNULs(possiblyTrimStdio(_this, stdioRetention, testCase.elementText("system-out")));
        stderr = fixNULs(possiblyTrimStdio(_this, stdioRetention, testCase.elementText("system-err")));

        // parse properties
        Map<String, String> properties = new HashMap<String, String>();
        if (keepProperties) {
            Element properties_element = testCase.element("properties");
            if (properties_element != null) {
                List<Element> property_elements = properties_element.elements("property");
                for (Element prop : property_elements) {
                    if (prop.attributeValue("name") != null) {
                        if (prop.attributeValue("value") != null) {
                            properties.put(prop.attributeValue("name"), prop.attributeValue("value"));
                        } else {
                            properties.put(prop.attributeValue("name"), prop.getText());
                        }
                    }
                }
            }
        }
        this.properties = properties;
        this.keepTestNames = keepTestNames;
    }

    public CaseResult(CaseResult src) {
        this.duration = src.duration;
        this.className = src.className;
        this.testName = src.testName;
        this.skippedMessage = src.skippedMessage;
        this.skipped = src.skipped;
        this.keepTestNames = src.keepTestNames;
        this.errorStackTrace = src.errorStackTrace;
        this.errorDetails = src.errorDetails;
        this.failedSince = src.failedSince;
        this.stdout = src.stdout;
        this.stderr = src.stderr;
        this.properties = new HashMap<>();
        this.properties.putAll(src.properties);
    }

    public static float clampDuration(float d) {
        return Math.min(365.0f * 24 * 60 * 60, Math.max(0.0f, d));
    }

    static CaseResult parse(SuiteResult parent, final XMLStreamReader reader, String context, String ver)
            throws XMLStreamException {
        CaseResult r = new CaseResult(parent, null, null, null);
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamReader.END_ELEMENT && reader.getLocalName().equals("case")) {
                return r;
            }
            if (event == XMLStreamReader.START_ELEMENT) {
                final String elementName = reader.getLocalName();
                switch (elementName) {
                    case "duration":
                        r.duration = clampDuration(new TimeToFloat(reader.getElementText()).parse());
                        break;
                    case "startTime":
                        r.startTime = Long.parseLong(reader.getElementText());
                        break;
                    case "className":
                        r.className = reader.getElementText();
                        break;
                    case "testName":
                        r.testName = reader.getElementText();
                        break;
                    case "skippedMessage":
                        r.skippedMessage = reader.getElementText();
                        break;
                    case "skipped":
                        r.skipped = Boolean.parseBoolean(reader.getElementText());
                        break;
                    case "keepTestNames":
                        r.keepTestNames = Boolean.parseBoolean(reader.getElementText());
                        break;
                    case "errorStackTrace":
                        r.errorStackTrace = reader.getElementText();
                        break;
                    case "errorDetails":
                        r.errorDetails = reader.getElementText();
                        break;
                    case "failedSince":
                        r.failedSince = Integer.parseInt(reader.getElementText());
                        break;
                    case "stdout":
                        r.stdout = reader.getElementText();
                        break;
                    case "stderr":
                        r.stderr = reader.getElementText();
                        break;
                    case "properties":
                        r.properties = new HashMap<>();
                        parseProperties(r.properties, reader, context, ver);
                        break;
                    default:
                        LOGGER.finest(() -> "Unknown field in " + context + ": " + elementName);
                }
            }
        }
        return r;
    }

    static void parseProperties(Map<String, String> r, final XMLStreamReader reader, String context, String ver)
            throws XMLStreamException {
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamReader.END_ELEMENT && reader.getLocalName().equals("properties")) {
                return;
            }
            if (event == XMLStreamReader.START_ELEMENT) {
                final String elementName = reader.getLocalName();
                switch (elementName) {
                    case "entry":
                        parseProperty(r, reader, context, ver);
                        break;
                    default:
                        LOGGER.finest(() -> "Unknown field in " + context + ": " + elementName);
                }
            }
        }
    }

    static void parseProperty(Map<String, String> r, final XMLStreamReader reader, String context, String ver)
            throws XMLStreamException {
        String name = null, value = null;
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamReader.END_ELEMENT && reader.getLocalName().equals("entry")) {
                if (name != null && value != null) {
                    r.put(name, value);
                }
                return;
            }
            if (event == XMLStreamReader.START_ELEMENT) {
                final String elementName = reader.getLocalName();
                switch (elementName) {
                    case "string":
                        if (name == null) {
                            name = reader.getElementText();
                        } else {
                            value = reader.getElementText();
                        }
                        break;
                    default:
                        LOGGER.finest(() -> "Unknown field in " + context + ": " + elementName);
                }
            }
        }
    }

    /**
     * Cleans up a truncated string, that might contain unpaired surrogates
     * @param str input string
     * @return input string, possibly truncated to avoid unpaired surrogates
     */
    static CharSequence cleanupTruncated(CharSequence str) {
        // remove unpaired trailing surrogates at the start
        if (str.length() > 0 && Character.isLowSurrogate(str.charAt(0))) {
            str = str.subSequence(1, str.length());
        }
        // remove unpaired leading surrogates at the end
        if (str.length() > 0 && Character.isHighSurrogate(str.charAt(str.length() - 1))) {
            str = str.subSequence(0, str.length() - 1);
        }
        return str;
    }

    static String possiblyTrimStdio(
            Collection<CaseResult> results, StdioRetention stdioRetention, String stdio) { // HUDSON-6516
        if (stdio == null) {
            return null;
        }
        boolean keepAll = stdioRetention == StdioRetention.ALL
                || (stdioRetention == StdioRetention.FAILED && hasFailures(results));
        if (keepAll) {
            return stdio;
        }
        int len = stdio.length();
        int halfMaxSize = halfMaxSize(results);
        int middle = len - halfMaxSize * 2;
        if (middle <= 0) {
            return stdio;
        }
        return cleanupTruncated(stdio.subSequence(0, halfMaxSize)) + "\n...[truncated " + middle + " chars]...\n"
                + cleanupTruncated(stdio.subSequence(len - halfMaxSize, len));
    }

    static String fixNULs(String stdio) { // JENKINS-71139
        return stdio == null ? null : stdio.replace("\u0000", "^@");
    }

    /**
     * Flavor of {@link #possiblyTrimStdio(Collection, StdioRetention, String)} that doesn't try to read the whole thing into memory.
     */
    @SuppressFBWarnings(value = "DM_DEFAULT_ENCODING", justification = "Expected behavior")
    static String possiblyTrimStdio(Collection<CaseResult> results, StdioRetention stdioRetention, File stdio)
            throws IOException {
        long len = stdio.length();
        boolean keepAll = stdioRetention == StdioRetention.ALL
                || (stdioRetention == StdioRetention.FAILED && hasFailures(results));
        if (keepAll && len < 1024 * 1024) {
            return FileUtils.readFileToString(stdio);
        }

        int halfMaxSize = halfMaxSize(results);

        long middle = len - halfMaxSize * 2;
        if (middle <= 0) {
            return FileUtils.readFileToString(stdio);
        }

        TextFile tx = new TextFile(stdio);
        String head = tx.head(halfMaxSize);
        String tail = tx.fastTail(halfMaxSize);

        int headBytes = head.getBytes().length;
        int tailBytes = tail.getBytes().length;

        middle = len - (headBytes + tailBytes);
        if (middle <= 0) {
            // if it turns out that we didn't have any middle section, just return the whole thing
            return FileUtils.readFileToString(stdio);
        }

        return cleanupTruncated(head) + "\n...[truncated " + middle + " bytes]...\n" + cleanupTruncated(tail);
    }

    private static final int HALF_MAX_SIZE = 500;
    private static final int HALF_MAX_FAILING_SIZE = 50000;

    private static int halfMaxSize(Collection<CaseResult> results) {
        return hasFailures(results) ? HALF_MAX_FAILING_SIZE : HALF_MAX_SIZE;
    }

    private static boolean hasFailures(Collection<CaseResult> results) {
        return results.stream().anyMatch(r -> r.errorStackTrace != null);
    }

    @Override
    public ClassResult getParent() {
        return classResult;
    }

    private static String getError(Element testCase) {
        String msg = testCase.elementText("error");
        if (msg != null) {
            return msg;
        }
        return testCase.elementText("failure");
    }

    private static String getErrorMessage(Element testCase) {

        Element msg = testCase.element("error");
        if (msg == null) {
            msg = testCase.element("failure");
        }
        if (msg == null) {
            return null; // no error or failure elements! damn!
        }

        return msg.attributeValue("message");
    }

    /**
     * If the testCase element includes the skipped element (as output by TestNG), then
     * the test has neither passed nor failed, it was never run.
     */
    private static boolean isMarkedAsSkipped(Element testCase) {
        return testCase.element("skipped") != null;
    }

    private static String getSkippedMessage(Element testCase) {
        String message = null;
        Element skippedElement = testCase.element("skipped");

        if (skippedElement != null) {
            message = skippedElement.attributeValue("message");
            if (message == null) {
                message = skippedElement.getText();
            }
        }

        return message;
    }

    public String getTransformedTestName() {
        return TestNameTransformer.getTransformedName(getName());
    }

    @Override
    public String getDisplayName() {
        return getNameWithEnclosingBlocks(getTransformedTestName());
    }

    private String getNameWithEnclosingBlocks(String rawName) {
        // Only prepend the enclosing flow node names if there are any and the run this is in has multiple blocks
        // directly containing
        // test results.
        if (!keepTestNames && !getEnclosingFlowNodeNames().isEmpty()) {
            Run<?, ?> r = getRun();
            if (r != null) {
                TestResultAction action = r.getAction(TestResultAction.class);
                if (action != null && action.getResult().hasMultipleBlocks()) {
                    List<String> enclosingFlowNodeNames = getEnclosingFlowNodeNames();
                    Collections.reverse(enclosingFlowNodeNames);
                    return String.join(" / ", enclosingFlowNodeNames) + " / " + rawName;
                }
            }
        }
        return rawName;
    }

    /**
     * Gets the name of the test, which is returned from {@code TestCase.getName()}
     *
     * <p>
     * Note that this may contain any URL-unfriendly character.
     */
    @Exported(visibility = 999)
    public @Override String getName() {
        if (testName == null || testName.isEmpty()) {
            return "(?)";
        }
        return testName;
    }

    /**
     * Gets the human readable title of this result object.
     */
    @Override
    public String getTitle() {
        return "Case Result: " + getDisplayName();
    }

    /**
     * Gets the duration of the test, in seconds
     */
    @Exported(visibility = 9)
    @Override
    public float getDuration() {
        return duration;
    }

    /**
     * Gets the start time of the test, in epoch milliseconds
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Gets the version of {@link #getName()} that's URL-safe.
     */
    public @Override synchronized String getSafeName() {
        if (safeName != null) {
            return safeName;
        }
        StringBuilder buf = new StringBuilder(getDisplayName());
        for (int i = 0; i < buf.length(); i++) {
            char ch = buf.charAt(i);
            if (!Character.isJavaIdentifierPart(ch)) {
                buf.setCharAt(i, '_');
            }
        }
        Collection<CaseResult> siblings = classResult == null ? Collections.emptyList() : classResult.getChildren();
        return safeName = uniquifyName(siblings, buf.toString());
    }

    /**
     * Gets the class name of a test class.
     *
     * @return the class name of a test class.
     */
    @Exported(visibility = 9)
    public String getClassName() {
        return className;
    }

    /**
     * Gets the simple (not qualified) class name.
     *
     * @return the simple (not qualified) class name.
     */
    public String getSimpleName() {
        int idx = className.lastIndexOf('.');
        return className.substring(idx + 1);
    }

    /**
     * Gets the package name of a test case.
     *
     * @return the package name of a test case.
     */
    public String getPackageName() {
        int idx = className.lastIndexOf('.');
        if (idx < 0) {
            return "(root)";
        } else {
            return className.substring(0, idx);
        }
    }

    @Override
    public String getFullName() {
        return className + '.' + getName();
    }

    /**
     * @since 1.515
     */
    @Override
    public String getFullDisplayName() {
        return getNameWithEnclosingBlocks(getTransformedFullDisplayName());
    }

    public String getTransformedFullDisplayName() {
        return TestNameTransformer.getTransformedName(getFullName());
    }

    @Override
    public int getFailCount() {
        if (isFailed()) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public int getSkipCount() {
        if (isSkipped()) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public int getPassCount() {
        return isPassed() ? 1 : 0;
    }

    /**
     * If this test failed, then return the build number
     * when this test started failing.
     */
    @Exported(visibility = 9)
    @Override
    public int getFailedSince() {
        // If we haven't calculated failedSince yet, and we should,
        // do it now.
        recomputeFailedSinceIfNeeded();
        return failedSince;
    }

    private void recomputeFailedSinceIfNeeded() {
        if (failedSince == 0 && getFailCount() == 1) {
            CaseResult prev = getPreviousResult();
            if (prev != null && prev.isFailed()) {
                this.failedSince = prev.getFailedSince();
            } else if (getRun() != null) {
                this.failedSince = getRun().getNumber();
            } else {
                LOGGER.warning("trouble calculating getFailedSince. We've got prev, but no owner.");
                // failedSince will be 0, which isn't correct.
            }
        }
    }

    @Override
    public Run<?, ?> getFailedSinceRun() {
        JunitTestResultStorage storage = JunitTestResultStorage.find();
        if (!(storage instanceof FileJunitTestResultStorage)) {
            Run<?, ?> run = Stapler.getCurrentRequest2().findAncestorObject(Run.class);
            TestResultImpl pluggableStorage = storage.load(run.getParent().getFullName(), run.getNumber());
            return pluggableStorage.getFailedSinceRun(this);
        }

        return getRun().getParent().getBuildByNumber(getFailedSince());
    }

    /**
     * Gets the number of consecutive builds (including this)
     * that this test case has been failing.
     *
     * @return the number of consecutive failing builds.
     */
    @Exported(visibility = 9)
    public int getAge() {
        if (isPassed()) {
            return 0;
        } else if (getRun() != null) {
            return getRun().getNumber() - getFailedSince() + 1;
        } else {
            LOGGER.fine("Trying to get age of a CaseResult without an owner");
            return 0;
        }
    }

    /**
     * The stdout of this test.
     *
     * <p>
     * Depending on the tool that produced the XML report, this method works somewhat inconsistently.
     * With some tools (such as Maven surefire plugin), you get the accurate information, that is
     * the stdout from this test case. With some other tools (such as the JUnit task in Ant), this
     * method returns the stdout produced by the entire test suite.
     *
     * <p>
     * If you need to know which is the case, compare this output from {@link SuiteResult#getStdout()}.
     * @since 1.294
     */
    @Exported
    @Override
    public String getStdout() {
        if (stdout != null) {
            return stdout;
        }
        SuiteResult sr = getSuiteResult();
        if (sr == null) {
            return "";
        }
        return getSuiteResult().getStdout();
    }

    /**
     * The stderr of this test.
     *
     * @see #getStdout()
     * @since 1.294
     */
    @Exported
    @Override
    public String getStderr() {
        if (stderr != null) {
            return stderr;
        }
        SuiteResult sr = getSuiteResult();
        if (sr == null) {
            return "";
        }
        return getSuiteResult().getStderr();
    }

    static int PREVIOUS_TEST_RESULT_BACKTRACK_BUILDS_MAX = Integer.parseInt(System.getProperty(
            History.HistoryTableResult.class.getName() + ".PREVIOUS_TEST_RESULT_BACKTRACK_BUILDS_MAX", "25"));

    @Override
    public CaseResult getPreviousResult() {
        if (parent == null) {
            return null;
        }

        TestResult previousResult = parent.getParent();
        int n = 0;
        while (previousResult != null && n < PREVIOUS_TEST_RESULT_BACKTRACK_BUILDS_MAX) {
            previousResult = previousResult.getPreviousResult();
            if (previousResult == null) {
                return null;
            }
            if (previousResult instanceof hudson.tasks.junit.TestResult) {
                hudson.tasks.junit.TestResult pr = (hudson.tasks.junit.TestResult) previousResult;
                CaseResult cr = pr.getCase(parent.getName(), getTransformedFullDisplayName());
                if (cr != null) {
                    return cr;
                }
            }
            ++n;
        }
        return null;
    }

    /**
     * Case results have no children
     * @return null
     */
    @Override
    public TestResult findCorrespondingResult(String id) {
        if (id.equals(safe(getName()))) {
            return this;
        }
        return null;
    }

    /**
     * Gets the "children" of this test result that failed
     *
     * @return the children of this test result, if any, or an empty collection
     */
    @Override
    public Collection<? extends TestResult> getFailedTests() {
        return singletonListOfThisOrEmptyList(isFailed());
    }

    /**
     * Gets the "children" of this test result that passed
     *
     * @return the children of this test result, if any, or an empty collection
     */
    @Override
    public Collection<? extends TestResult> getPassedTests() {
        return singletonListOfThisOrEmptyList(isPassed());
    }

    /**
     * Gets the "children" of this test result that were skipped
     *
     * @return the children of this test result, if any, or an empty list
     */
    @Override
    public Collection<? extends TestResult> getSkippedTests() {
        return singletonListOfThisOrEmptyList(isSkipped());
    }

    private Collection<? extends hudson.tasks.test.TestResult> singletonListOfThisOrEmptyList(boolean f) {
        if (f) {
            return Collections.singletonList(this);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * If there was an error or a failure, this is the stack trace, or otherwise null.
     */
    @Exported
    @Override
    public String getErrorStackTrace() {
        return errorStackTrace;
    }

    /**
     * If there was an error or a failure, this is the text from the message.
     */
    @Exported
    @Override
    public String getErrorDetails() {
        return errorDetails;
    }

    @Exported
    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * @return true if the test was not skipped and did not fail, false otherwise.
     */
    @Override
    public boolean isPassed() {
        return !skipped && errorDetails == null && errorStackTrace == null;
    }

    /**
     * Tests whether the test was skipped or not.  TestNG allows tests to be
     * skipped if their dependencies fail or they are part of a group that has
     * been configured to be skipped.
     * @return true if the test was not executed, false otherwise.
     */
    @Exported(visibility = 9)
    public boolean isSkipped() {
        return skipped;
    }

    /**
     * @return true if the test was not skipped and did not pass, false otherwise.
     * @since 1.520
     */
    public boolean isFailed() {
        return !isPassed() && !isSkipped();
    }

    /**
     * Provides the reason given for the test being being skipped.
     * @return the message given for a skipped test if one has been provided, null otherwise.
     * @since 1.507
     */
    @Exported
    public String getSkippedMessage() {
        return skippedMessage;
    }

    public SuiteResult getSuiteResult() {
        return parent;
    }

    @CheckForNull
    public String getFlowNodeId() {
        if (parent != null) {
            return parent.getNodeId();
        }
        return null;
    }

    @NonNull
    public List<String> getEnclosingFlowNodeIds() {
        List<String> enclosing = new ArrayList<>();
        if (parent != null) {
            enclosing.addAll(parent.getEnclosingBlocks());
        }
        return enclosing;
    }

    @NonNull
    public List<String> getEnclosingFlowNodeNames() {
        List<String> enclosing = new ArrayList<>();
        if (parent != null) {
            enclosing.addAll(parent.getEnclosingBlockNames());
        }
        return enclosing;
    }

    @Override
    public Run<?, ?> getRun() {
        SuiteResult sr = getSuiteResult();
        if (sr == null) {
            LOGGER.warning("In getOwner(), getSuiteResult is null");
            return null;
        }

        hudson.tasks.junit.TestResult tr = sr.getParent();

        if (tr == null) {
            LOGGER.warning("In getOwner(), suiteResult.getParent() is null.");
            return null;
        }

        return tr.getRun();
    }

    public void setParentSuiteResult(SuiteResult parent) {
        this.parent = parent;
    }

    public void freeze(SuiteResult parent) {
        this.parent = parent;
        // some old test data doesn't have failedSince value set, so for those compute them.
        recomputeFailedSinceIfNeeded();
    }

    @Override
    public int compareTo(CaseResult that) {
        if (this == that) {
            return 0;
        }
        int r1 = this.className.compareTo(that.className);
        if (r1 != 0) {
            return r1;
        }
        int r2 = this.getName().compareTo(that.getName());
        if (r2 != 0) {
            return r2;
        }
        // Only equals is exact reference
        return System.identityHashCode(this) >= System.identityHashCode(that) ? 1 : -1;
    }

    // Method overridden to provide explicit declaration of the equivalence relation used
    // as Comparable is also implemented
    @Override
    public boolean equals(Object obj) {
        return (this == obj);
    }

    // Method overridden to provide explicit declaration of the equivalence relation used
    // as Comparable is also implemented
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Exported(name = "status", visibility = 9) // because stapler notices suffix 's' and remove it
    public Status getStatus() {
        if (skipped) {
            return Status.SKIPPED;
        }
        CaseResult pr = getPreviousResult();
        if (pr == null) {
            return isPassed() ? Status.PASSED : Status.FAILED;
        }

        if (pr.isPassed()) {
            return isPassed() ? Status.PASSED : Status.REGRESSION;
        } else {
            return isPassed() ? Status.FIXED : Status.FAILED;
        }
    }

    public void setClass(ClassResult classResult) {
        this.classResult = classResult;
    }

    public void setStartTime(long start) {
        startTime = start;
    }

    void replaceParent(SuiteResult parent) {
        this.parent = parent;
    }

    /**
     * Constants that represent the status of this test.
     */
    public enum Status {
        /**
         * This test runs OK, just like its previous run.
         */
        PASSED("jenkins-button jp-pill jenkins-!-success-color", Messages._CaseResult_Status_Passed(), true),
        /**
         * This test was skipped due to configuration or the
         * failure or skipping of a method that it depends on.
         */
        SKIPPED("jenkins-button jp-pill jenkins-!-skipped-color", Messages._CaseResult_Status_Skipped(), false),
        /**
         * This test failed, just like its previous run.
         */
        FAILED("jenkins-button jp-pill jenkins-!-error-color", Messages._CaseResult_Status_Failed(), false),
        /**
         * This test has been failing, but now it runs OK.
         */
        FIXED("jenkins-!-success-color", Messages._CaseResult_Status_Fixed(), true),
        /**
         * This test has been running OK, but now it failed.
         */
        REGRESSION("jenkins-!-error-color", Messages._CaseResult_Status_Regression(), false);

        private final String cssClass;
        private final Localizable message;
        public final boolean isOK;

        Status(String cssClass, Localizable message, boolean OK) {
            this.cssClass = cssClass;
            this.message = message;
            isOK = OK;
        }

        public String getCssClass() {
            return cssClass;
        }

        public String getMessage() {
            return message.toString();
        }

        public boolean isRegression() {
            return this == REGRESSION;
        }
    }

    /**
     * For sorting errors by age.
     */
    /*package*/ static final Comparator<CaseResult> BY_AGE = Comparator.comparingInt(CaseResult::getAge);

    private static final long serialVersionUID = 1L;
}
