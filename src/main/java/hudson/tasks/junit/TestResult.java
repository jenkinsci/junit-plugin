/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, id:cactusman, Tom Huybrechts, Yahoo!, Inc.
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.XmlFile;
import hudson.model.Run;
import io.jenkins.plugins.junit.storage.TestResultImpl;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.PipelineTestDetails;
import hudson.tasks.test.PipelineBlockWithTests;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TabulatedResult;
import hudson.tasks.test.TestObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import jenkins.util.SystemProperties;
import org.apache.tools.ant.DirectoryScanner;
import org.dom4j.DocumentException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import com.thoughtworks.xstream.XStream;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Root of all the test results for one build.
 *
 * @author Kohsuke Kawaguchi
 */
public final class TestResult extends MetaTabulatedResult {


    private static final Logger LOGGER = Logger.getLogger(JUnitResultArchiver.class.getName());

    @SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "We do not expect TestResult to be serialized when this field is set.")
    private final @CheckForNull TestResultImpl impl;

    /**
     * List of all {@link SuiteResult}s in this test.
     * This is the core data structure to be persisted in the disk.
     */
    private List<SuiteResult> suites = new ArrayList<>();

    /**
     * {@link #suites} keyed by their names for faster lookup. Since multiple suites can have the same name, holding a collection.
     */
    private transient Map<String, Collection<SuiteResult>> suitesByName;

    /**
     * {@link #suites} keyed by their node ID for faster lookup. May be empty.
     */
    private transient Map<String,List<SuiteResult>> suitesByNode;

    /**
     * Results tabulated by package.
     */
    private transient Map<String,PackageResult> byPackages;

    // set during the freeze phase
    private transient AbstractTestResultAction parentAction;

    private transient TestObject parent;

    /**
     * Number of all leafNodes.
     */
    private transient int totalTests;

    private transient List<CaseResult> passedTests;

    private transient List<CaseResult> skippedTests;

    private transient int skippedTestsCounter;

    private float duration;

    private boolean skipOldReports;
    
    private long startTime = -1;

    /**
     * Number of failed/error leafNodes.
     */
    private transient List<CaseResult> failedTests;

    private StdioRetention stdioRetention;
    private boolean keepTestNames;

    private boolean keepProperties;

    // default 3s as it depends on OS some can be good some not really....
    public static final long FILE_TIME_PRECISION_MARGIN = Long.getLong(TestResult.class.getName() + "filetime.precision.margin", 3000);

    /**
     * Creates an empty result.
     */
    public TestResult() {
        this(false);
    }

    /**
     * @since 1.522
     */
    @Deprecated
    public TestResult(boolean keepLongStdio) {
        this.stdioRetention = StdioRetention.fromKeepLongStdio(keepLongStdio);
        this.keepProperties = false;
        this.keepTestNames = false;
        impl = null;
    }

    // Compatibility to XUnit plugin (and maybe more)
    @Deprecated
    public TestResult(long buildTime, boolean keepLongStdio) throws IOException {
        this.stdioRetention = StdioRetention.fromKeepLongStdio(keepLongStdio);
        this.keepTestNames = false;
        this.keepProperties = false;
        impl = null;
    }

    @Deprecated
    public TestResult(long filesTimestamp, DirectoryScanner results) throws IOException {
        this(filesTimestamp, results, false);
    }

    @Deprecated
    public TestResult(long filesTimestamp, DirectoryScanner results, boolean keepLongStdio) throws IOException {
        this(filesTimestamp, results, StdioRetention.fromKeepLongStdio(keepLongStdio), false, false, null, false);
    }

    @Deprecated
    public TestResult(long filesTimestamp, DirectoryScanner results, boolean keepLongStdio,
                      PipelineTestDetails pipelineTestDetails) throws IOException {
        this.stdioRetention = StdioRetention.fromKeepLongStdio(keepLongStdio);
        this.keepProperties = false;
        impl = null;
        parse(filesTimestamp, results, pipelineTestDetails);
    }
    /**
     * Collect reports from the given {@link DirectoryScanner}, while
     * filtering out all files that were created before the given time.
     * @param filesTimestamp per default files older than this will be ignored (depending on param skipOldReports)
     * @param keepLongStdio if true, retain a suite's complete stdout/stderr even if this is huge and the suite passed
     * @param pipelineTestDetails A {@link PipelineTestDetails} instance containing Pipeline-related additional arguments.
     * @param skipOldReports to parse or not test files older than filesTimestamp
     * @since 1.22
     */
    @Deprecated
    public TestResult(long filesTimestamp, DirectoryScanner results, boolean keepLongStdio, boolean keepProperties,
                      PipelineTestDetails pipelineTestDetails, boolean skipOldReports) throws IOException {
        this(filesTimestamp, results, StdioRetention.fromKeepLongStdio(keepLongStdio), keepProperties, false, pipelineTestDetails, skipOldReports);
    }

    @Deprecated
    public TestResult(long filesTimestamp, DirectoryScanner results, boolean keepLongStdio, boolean keepProperties, boolean keepTestNames,
                      PipelineTestDetails pipelineTestDetails, boolean skipOldReports) throws IOException {
        this(filesTimestamp, results, StdioRetention.fromKeepLongStdio(keepLongStdio), keepProperties, keepTestNames, pipelineTestDetails, skipOldReports);
    }

    /**
     * Collect reports from the given {@link DirectoryScanner}, while
     * filtering out all files that were created before the given time.
     * @param filesTimestamp per default files older than this will be ignored (depending on param skipOldReports)
     * @param stdioRetention how to retain stdout/stderr for large outputs
     * @param keepProperties to keep properties or not
     * @param keepTestNames to prepend parallel test stage to test name or not
     * @param pipelineTestDetails A {@link PipelineTestDetails} instance containing Pipeline-related additional arguments.
     * @param skipOldReports to parse or not test files older than filesTimestamp
     */
    public TestResult(long filesTimestamp, DirectoryScanner results, StdioRetention stdioRetention, boolean keepProperties, boolean keepTestNames,
                      PipelineTestDetails pipelineTestDetails, boolean skipOldReports) throws IOException {
        this.stdioRetention = stdioRetention;
        this.keepProperties = keepProperties;
        this.keepTestNames = keepTestNames;
        impl = null;
        this.skipOldReports = skipOldReports;
        parse(filesTimestamp, results, pipelineTestDetails);
    }

    public TestResult(TestResultImpl impl) {
        this.impl = impl;
        stdioRetention = StdioRetention.DEFAULT; // irrelevant
        this.keepProperties = false; // irrelevant
        keepTestNames = false; // irrelevant
    }

    @CheckForNull
    public TestResultImpl getPluggableStorage() {
        return impl;
    }

    @Override
    public TestObject getParent() {
    	return parent;
    }

    @Override
    public void setParent(TestObject parent) {
        this.parent = parent;
    }

    @Override
    public TestResult getTestResult() {
    	return this;
    }

    public TestResult(TestResult src) {
        this.stdioRetention = src.stdioRetention;
        this.keepProperties = src.keepProperties;
        this.keepTestNames = src.keepTestNames;
        this.duration = src.duration;
        if (src.suites != null) {
            this.suites = new ArrayList<SuiteResult>();
            for (SuiteResult sr : src.suites) {
                suites.add(new SuiteResult(sr));
            }
        } else {
            this.suites = null;
        }
        this.impl = null;
    }

    static final XMLInputFactory xmlFactory;
    static boolean USE_SAFE_XML_FACTORY =
            SystemProperties.getBoolean(TestResult.class.getName() + ".USE_SAFE_XML_FACTORY", true);

    static {
         xmlFactory = XMLInputFactory.newInstance();
         if (USE_SAFE_XML_FACTORY) {
            xmlFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            xmlFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        }
    }

    public static XMLInputFactory getXmlFactory() {
        return xmlFactory;
    }

    public void parse(XmlFile f) throws XMLStreamException, IOException {
        try (Reader r = f.readRaw()){
            final XMLStreamReader reader = getXmlFactory().createXMLStreamReader(r);
            while (reader.hasNext()) {
                final int event = reader.next();
                if (event == XMLStreamReader.START_ELEMENT && reader.getName()
                        .getLocalPart().equals("result")) {
                    parseXmlResult(reader);
                }
            }
            r.close();
        }
    }

    private void parseXmlResult(final XMLStreamReader reader) throws XMLStreamException {
        String ver = reader.getAttributeValue(null, "plugin");
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamReader.END_ELEMENT && reader.getLocalName().equals("result")) {
                return;
            }
            if (event == XMLStreamReader.START_ELEMENT) {
                final String elementName = reader.getLocalName();
                switch (elementName) {
                    case "suites":
                        parseXmlSuites(reader, ver);
                        break;
                    case "duration":
                        duration = CaseResult.clampDuration(new TimeToFloat(reader.getElementText()).parse());
                        break;
                    case "keepLongStdio":
                        stdioRetention = StdioRetention.fromKeepLongStdio(Boolean.parseBoolean(reader.getElementText()));
                        break;
                    case "stdioRetention":
                        stdioRetention = StdioRetention.parse(reader.getElementText());
                        break;
                    case "keepTestNames":
                        keepTestNames = Boolean.parseBoolean(reader.getElementText());
                        break;
                    case "keepProperties":
                        keepProperties = Boolean.parseBoolean(reader.getElementText());
                        break;
                    default:
                        if (LOGGER.isLoggable(Level.FINEST)) {
                            LOGGER.finest("TestResult.parseXmlResult encountered an unknown field: " + elementName);
                        }
                }
            }
        }
    }

    private void parseXmlSuites(final XMLStreamReader reader, String ver) throws XMLStreamException {
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamReader.END_ELEMENT && reader.getLocalName().equals("suites")) {
                return;
            }
            if (event == XMLStreamReader.START_ELEMENT) {
                final String elementName = reader.getLocalName();
                switch (elementName) {
                    case "suite":
                        suites.add(SuiteResult.parse(reader, ver));
                        break;
                    default:
                        if (LOGGER.isLoggable(Level.FINEST)) {
                            LOGGER.finest("TestResult.parseXmlSuites encountered an unknown field: " + elementName);
                        }
                }
            }
        }
    }

    @Deprecated
    public void parse(long filesTimestamp, DirectoryScanner results) throws IOException {
        parse(filesTimestamp, results, null);
    }

    /**
     * Collect reports from the given {@link DirectoryScanner}, while
     * filtering out all files that were created before the given time.
     * @param filesTimestamp file timestamp to filter files older.
     * @param results Directory scanner.
     * @param pipelineTestDetails A {@link PipelineTestDetails} instance containing Pipeline-related additional arguments.
     *
     * @throws IOException if an error occurs.
     * @since 1.22
     */
    public void parse(long filesTimestamp, DirectoryScanner results, PipelineTestDetails pipelineTestDetails) throws IOException {
        String[] includedFiles = results.getIncludedFiles();
        File baseDir = results.getBasedir();
        parse(filesTimestamp,baseDir, pipelineTestDetails,includedFiles);
    }

    @Deprecated
    public void parse(long filesTimestamp, File baseDir, String[] reportFiles)
            throws IOException {
        parse(filesTimestamp, baseDir, null, reportFiles);
    }

    /**
     * Collect reports from the given report files, while
     * filtering out all files that were created before the given time.
     * @param filesTimestamp file timestamp to filter files older.
     * @param baseDir Base directory.
     * @param pipelineTestDetails A {@link PipelineTestDetails} instance containing Pipeline-related additional arguments.
     * @param reportFiles Report files.
     *
     * @throws IOException if an error occurs.
     * @since 1.22
     */
    public void parse(long filesTimestamp, File baseDir, PipelineTestDetails pipelineTestDetails, String[] reportFiles) throws IOException {
        List<File> files = Arrays.stream(reportFiles).map(s -> new File(baseDir, s)).collect(Collectors.toList());
        parse(filesTimestamp, pipelineTestDetails, files);

    }

    private void parse(long filesTimestamp, PipelineTestDetails pipelineTestDetails, Iterable<File> reportFiles) throws IOException {
        for (File reportFile : reportFiles) {
            if(skipOldReports && Files.getLastModifiedTime(reportFile.toPath()).toMillis() < filesTimestamp - FILE_TIME_PRECISION_MARGIN ) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("file " + reportFile + " not parsed: skipOldReports-" + skipOldReports
                            + ",lastModified:" + Files.getLastModifiedTime(reportFile.toPath()).toMillis() + ",filesTimestamp:" + filesTimestamp);
                }
                continue;
            }
            // only count files that were actually updated during this build
            parsePossiblyEmpty(reportFile, pipelineTestDetails);
        }
        if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("testSuites size:" + this.getSuites().size());
    }

    @Override
    public hudson.tasks.test.TestResult getPreviousResult() {
        if (impl != null) {
            return impl.getPreviousResult();
        }
        
        return super.getPreviousResult();
    }

    @Deprecated
    public void parse(long filesTimestamp, Iterable<File> reportFiles) throws IOException {
        parse(filesTimestamp, null, reportFiles);
    }

    /**
     * Collect reports from the given report files
     *
     * @param filesTimestamp Build time.
     * @param reportFiles Report files.
     * @param pipelineTestDetails A {@link PipelineTestDetails} instance containing Pipeline-related additional arguments.
     *
     * @throws IOException if an error occurs.
     * @since 1.22
     */
    public void parse(long filesTimestamp, Iterable<File> reportFiles, PipelineTestDetails pipelineTestDetails) throws IOException {
        parse(filesTimestamp, pipelineTestDetails, reportFiles);
    }

    /**
     * Collect reports from the given report files
     *
     * @param reportFiles Report files.
     * @param pipelineTestDetails A {@link PipelineTestDetails} instance containing Pipeline-related additional arguments.
     *
     * @throws IOException if an error occurs.
     * @deprecated use {@link #parse(long, Iterable, PipelineTestDetails)}
     */
    @Deprecated
    public void parse(Iterable<File> reportFiles, PipelineTestDetails pipelineTestDetails) throws IOException {
        for (File reportFile : reportFiles) {
            // only count files that were actually updated during this build
            parsePossiblyEmpty(reportFile, pipelineTestDetails);
        }
    }

    private void parsePossiblyEmpty(File reportFile, PipelineTestDetails pipelineTestDetails) throws IOException {
        if(reportFile.length()==0) {
            if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("reportFile:" + reportFile + " is empty");
            // this is a typical problem when JVM quits abnormally, like OutOfMemoryError during a test.
            SuiteResult sr = new SuiteResult(reportFile.getName(), "", "", pipelineTestDetails);
            sr.addCase(new CaseResult(sr,"[empty]","Test report file "+reportFile.getAbsolutePath()+" was length 0"));
            add(sr);
        } else {
            parse(reportFile, pipelineTestDetails);
        }
    }
    
    private void add(SuiteResult sr) {
    	long suiteStart = sr.getStartTime();
        for (SuiteResult s : suites) {
            // JENKINS-12457: If a testsuite is distributed over multiple files, merge it into a single SuiteResult:
            if(s.getName().equals(sr.getName()) &&
                    eitherNullOrEq(s.getId(),sr.getId()) &&
                    nullSafeEq(s.getNodeId(),sr.getNodeId()) &&
                    nullSafeEq(s.getEnclosingBlocks(),sr.getEnclosingBlocks()) &&
                    nullSafeEq(s.getEnclosingBlockNames(),sr.getEnclosingBlockNames())) {

                // Set start time to earliest set start of a suite
                if (startTime == -1) {
                    startTime = suiteStart;
                } else if (suiteStart != -1){
                    startTime = Math.min(startTime, suiteStart);
                }
                
                duration += sr.getDuration();
                s.merge(sr);
                return;
            }
        }

        // Set start time to earliest set start of a suite
        if (startTime == -1) {
            startTime = suiteStart;
        } else if (suiteStart != -1){
            startTime = Math.min(startTime, suiteStart);
        }
        
        suites.add(sr);
        duration += sr.getDuration();
    }

    /**
     * Adds the leafNodes from another test result to this one.
     */
    void merge(TestResult other) {
        for (SuiteResult suite : other.suites) {
            suite.setParent(null); // otherwise freeze ignores it
            add(suite);
        }
        tally();
    }
    
    private boolean strictEq(Object lhs, Object rhs) {
        return lhs != null && rhs != null && lhs.equals(rhs);
    }

    private boolean nullSafeEq(Object lhs, Object rhs) {
        if (lhs == null) {
            return rhs == null;
        }
        return lhs.equals(rhs);
    }

    private boolean eitherNullOrEq(Object lhs, Object rhs) {
        // Merged testSuites may have attribute (ID) not preset in the original.
        // If both have an ID, compare it.
        // If either does not have an ID, then assume they are the same.
        return lhs == null || rhs == null || lhs.equals(rhs);
    }

    @Deprecated
    public void parse(File reportFile) throws IOException {
        parse(reportFile, null);
    }

    /**
     * Parses an additional report file.
     * @param reportFile Report file to parse.
     * @param pipelineTestDetails A {@link PipelineTestDetails} instance containing Pipeline-related additional arguments.
     *
     * @throws IOException if an error occurs.
     * @since 1.22
     */
    public void parse(File reportFile, PipelineTestDetails pipelineTestDetails) throws IOException {
        if (impl != null) {
            throw new IllegalStateException("Cannot reparse using a pluggable impl");
        }
        try {
            List<SuiteResult> suiteResults = SuiteResult.parse(reportFile, stdioRetention, keepProperties, keepTestNames, pipelineTestDetails);
            for (SuiteResult suiteResult : suiteResults)
                add(suiteResult);

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("reportFile:" + reportFile + ", lastModified:" + reportFile.lastModified() + " found " + suiteResults.size() + " suite results" );
            }

        } catch (InterruptedException | RuntimeException e) {
            throw new IOException("Failed to read "+reportFile,e);
        } catch (DocumentException e) {
            if (!reportFile.getPath().endsWith(".xml")) {
                throw new IOException("Failed to read "+reportFile+"\n"+
                    "Is this really a JUnit report file? Your configuration must be matching too many files",e);
            } else {
                SuiteResult sr = new SuiteResult(reportFile.getName(), "", "", null);
                StringWriter writer = new StringWriter();
                e.printStackTrace(new PrintWriter(writer));
                String error = "Failed to read test report file "+reportFile.getAbsolutePath()+"\n"+writer.toString();
                sr.addCase(new CaseResult(sr,"[failed-to-read]",error));
                add(sr);
            }
        }
    }

    @Override
    public String getDisplayName() {
        return Messages.TestResult_getDisplayName();
    }

    @Override
    public Run<?,?> getRun() {
        if (parentAction != null) {
            return parentAction.run;
        }
        if (impl == null) {
            return null;
        }
        
        return impl.getRun();
    }

    @Override
    public hudson.tasks.test.TestResult findCorrespondingResult(String id) {
        if (getId().equals(id) || (id == null)) {
            return this;
        }

        String firstElement = null;
        String subId = null;
        int sepIndex = id.indexOf('/');
        if (sepIndex < 0) {
            firstElement = id;
            subId = null;
        } else {
            firstElement = id.substring(0, sepIndex);
            subId = id.substring(sepIndex + 1);
            if (subId.length() == 0) {
                subId = null;
            }
        }

        String packageName = null;
        if (firstElement.equals(getId())) {
            sepIndex = subId.indexOf('/');
            if (sepIndex < 0) {
                packageName = subId;
                subId = null;
            } else {
                packageName = subId.substring(0, sepIndex);
                subId = subId.substring(sepIndex + 1);
            }
        } else {
            packageName = firstElement;
            subId = null;
        }
        PackageResult child = byPackage(packageName);
        if (child != null) {
            if (subId != null) {
                return child.findCorrespondingResult(subId);
            } else {
                return child;
            }
        } else {
            return null;
    }
    }

    @Override
    public String getTitle() {
        return Messages.TestResult_getTitle();
    }

    @Override
    public String getChildTitle() {
        return Messages.TestResult_getChildTitle();
    }

    @Override
    public String getChildType() {
        return "package";
    }

    @Exported(visibility=999)
    @Override
    public float getDuration() {
        if (impl != null) {
            return impl.getTotalTestDuration();
        }
        
        return duration;
    }
    
    public void setStartTime(long start) {
        startTime = start;
    }

    public long getStartTime() {
        return startTime;
    }

    @Exported(visibility=999)
    @Override
    public int getPassCount() {
        if (impl != null) {
            return impl.getPassCount();
        }
        return totalTests-getFailCount()-getSkipCount();
    }

    @Exported(visibility=999)
    @Override
    public int getFailCount() {
        if (impl != null) {
            return impl.getFailCount();
        }
        if(failedTests==null)
            return 0;
        else
        return failedTests.size();
    }

    @Exported(visibility=999)
    @Override
    public int getSkipCount() {
        if (impl != null) {
            return impl.getSkipCount();
        }
        return skippedTestsCounter;
    }

    @Override
    public int getTotalCount() {
        if (impl != null) {
            return impl.getTotalCount();
        }
        return super.getTotalCount();
    }
    
    /**
     * Returns <code>true</code> if this doesn't have any any test results.
     *
     * @return whether this doesn't contain any test results.
     * @since 1.511
     */
    @Exported(visibility=999)
    public boolean isEmpty() {
        return getTotalCount() == 0;
    }

    @Override
    public List<CaseResult> getFailedTests() {
        if (impl != null) {
            return impl.getFailedTests();
        }
        return failedTests;
    }

    /**
     * Gets the "children" of this test result that passed
     *
     * @return the children of this test result, if any, or an empty collection
     */
    @Override
    public synchronized List<CaseResult> getPassedTests() {
        if (impl != null) {
            return impl.getPassedTests();
        }
        
        if(passedTests == null){
            passedTests = new ArrayList<>();
            for(SuiteResult s : suites) {
                for(CaseResult cr : s.getCases()) {
                    if (cr.isPassed()) {
                        passedTests.add(cr);
                    }
                }
            }
        }

        return passedTests;
    }

    /**
     * Gets the "children" of this test result that were skipped
     *
     * @return the children of this test result, if any, or an empty list
     */
    @Override
    public synchronized List<CaseResult> getSkippedTests() {
        if (impl != null) {
            return impl.getSkippedTests();
        }
        
        if(skippedTests == null){
            skippedTests = new ArrayList<>();
            for(SuiteResult s : suites) {
                for(CaseResult cr : s.getCases()) {
                    if (cr.isSkipped()) {
                        skippedTests.add(cr);
                    }
                }
            }
        }

        return skippedTests;
    }

    /**
     * If this test failed, then return the build number
     * when this test started failing.
     */
    @Override
    public int getFailedSince() {
        throw new UnsupportedOperationException();  // TODO: implement!(FIXME: generated)
    }

    /**
     * If this test failed, then return the run
     * when this test started failing.
     */
    @Override
    public Run<?, ?> getFailedSinceRun() {
        throw new UnsupportedOperationException();  // TODO: implement!(FIXME: generated)
    }

    /**
     * The stdout of this test.
     * <p>
     * Depending on the tool that produced the XML report, this method works somewhat inconsistently.
     * With some tools (such as Maven surefire plugin), you get the accurate information, that is
     * the stdout from this test case. With some other tools (such as the JUnit task in Ant), this
     * method returns the stdout produced by the entire test suite.
     * </p>
     * If you need to know which is the case, compare this output from {@link SuiteResult#getStdout()}.
     *
     * @since 1.294
     */
    @Override
    public String getStdout() {
        StringBuilder sb = new StringBuilder();
        for (SuiteResult suite: suites) {
            sb.append("Standard Out (stdout) for Suite: " + suite.getName());
            sb.append(suite.getStdout());
        }
        return sb.toString();
    }

    /**
     * The stderr of this test.
     *
     * @see #getStdout()
     * @since 1.294
     */
    @Override
    public String getStderr() {
        StringBuilder sb = new StringBuilder();
        for (SuiteResult suite: suites) {
            sb.append("Standard Error (stderr) for Suite: " + suite.getName());
            sb.append(suite.getStderr());
        }
        return sb.toString();
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.emptyMap();
    }

    /**
     * If there was an error or a failure, this is the stack trace, or otherwise null.
     */
    @Override
    public String getErrorStackTrace() {
        return "No error stack traces available at this level. Drill down to individual leafNodes to find stack traces.";
    }

    /**
     * If there was an error or a failure, this is the text from the message.
     */
    @Override
    public String getErrorDetails() {
        return "No error details available at this level. Drill down to individual leafNodes to find details.";
    }

    /**
     * @return true if the test was not skipped and did not fail, false otherwise.
     */
    @Override
    public boolean isPassed() {
       return getFailCount() == 0;
    }

    @Override
    public Collection<PackageResult> getChildren() {
        if (impl != null) {
            return impl.getAllPackageResults();
        }
        
        return byPackages.values();
    }

    /**
     * Whether this test result has children.
     */
    @Override
    public boolean hasChildren() {
        return !suites.isEmpty();
    }

    @Exported(inline=true,visibility=9)
    public Collection<SuiteResult> getSuites() {
        if (impl != null) {
            return impl.getSuites();
        }
        return suites;
    }


    @Override
    public String getName() {
        return "junit";
    }

    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        if (token.equals(getId())) {
            return this;
        }

        PackageResult result = byPackage(token);
        if (result != null) {
        	return result;
        } else {
        	return super.getDynamic(token, req, rsp);
        }
    }

    public PackageResult byPackage(String packageName) {
        if (impl != null) {
            return impl.getPackageResult(packageName);
        }
        
        return byPackages.get(packageName);
    }

    /**
     * Returns the first suite with the given name. Prefer using {@link #getSuites(String)} to disambiguate suites with the same name.
     * @param name the suite name
     * @return The first test suite with the given name, or null if not found.
     */
    @CheckForNull
    public SuiteResult getSuite(String name) {
        Collection<SuiteResult> suiteResults = getSuites(name);
        if (suiteResults.isEmpty()) {
            return null;
        } else {
            return suiteResults.iterator().next();
        }
    }

    /**
     * Returns all suites with the given name.
     * @param name the suite name
     * @return all suites with the given name
     */
    @NonNull
    public Collection<SuiteResult> getSuites(String name) {
        if (impl != null) {
            return Collections.singleton(impl.getSuite(name));
        }
        return suitesByName.getOrDefault(name, Collections.emptyList());
    }

    @NonNull
    public TestResult getResultByNode(@NonNull String nodeId) {
        return getResultByNodes(Collections.singletonList(nodeId));
    }

    @NonNull
    public TestResult getResultByNodes(@NonNull List<String> nodeIds) {
        if (impl != null) {
            return impl.getResultByNodes(nodeIds);
        }
        TestResult result = new TestResult();
        for (String n : nodeIds) {
            List<SuiteResult> suites = suitesByNode.get(n);
            if (suites != null) {
                for (SuiteResult s : suites) {
                    result.add(s);
                }
            }
        }
        result.setParentAction(parentAction);

        return result;
    }

     @Override
     public void setParentAction(AbstractTestResultAction action) {
        this.parentAction = action;
        tally(); // I want to be sure to inform our children when we get an action.
     }

     @Override
     public AbstractTestResultAction getParentAction() {
         return this.parentAction;
     }

    /**
     * Recount my children.
     */
    @Override
    public void tally() {
        // TODO allow TestResultStorage to cancel this
        /// Empty out data structures
        // TODO: free children? memmory leak?
        suitesByName = new HashMap<>();
        suitesByNode = new HashMap<>();
        testsByBlock = new HashMap<>();
        failedTests = new ArrayList<>();
        skippedTests = null;
        passedTests = null;
        byPackages = new TreeMap<>();

        totalTests = 0;
        skippedTestsCounter = 0;

        // Ask all of our children to tally themselves
        for (SuiteResult s : suites) {
            s.setParent(this); // kluge to prevent double-counting the results
            suitesByName.merge(s.getName(), Collections.singleton(s), (a,b) -> Stream.concat(a.stream(), b.stream()).collect(Collectors.toList()));
            if (s.getNodeId() != null) {
                addSuiteByNode(s);
            }

            List<CaseResult> cases = s.getCases();

            for (CaseResult cr: cases) {
                cr.setParentAction(this.parentAction);
                cr.setParentSuiteResult(s);
                cr.tally();
            String pkg = cr.getPackageName(), spkg = safe(pkg);
                PackageResult pr = byPackage(spkg);
                if(pr==null)
                    byPackages.put(spkg,pr=new PackageResult(this,pkg));
                
                if (pr.getStartTime() == -1) {
                    pr.setStartTime(s.getStartTime());
                } else if (s.getStartTime() != -1){
                    pr.setStartTime(Math.min(pr.getStartTime(), s.getStartTime()));
                }
                pr.add(cr);
            }
        }

        for (PackageResult pr : byPackages.values()) {
            pr.tally();
            skippedTestsCounter += pr.getSkipCount();
            failedTests.addAll(pr.getFailedTests());
            totalTests += pr.getTotalCount();
        }
    }

    /**
     * Builds up the transient part of the data structure
     * from results {@link #parse(File) parsed} so far.
     *
     * <p>
     * After the data is frozen, more files can be parsed
     * and then freeze can be called again.
     */
    public void freeze(TestResultAction parent) {
        assert impl == null;
        this.parentAction = parent;
        if(suitesByName==null) {
            // freeze for the first time
            suitesByName = new HashMap<>();
            suitesByNode = new HashMap<>();
            testsByBlock = new HashMap<>();
            totalTests = 0;
            failedTests = new ArrayList<>();
            skippedTests = null;
            passedTests = null;
            byPackages = new TreeMap<>();
        }

        for (SuiteResult s : suites) {
            if(!s.freeze(this))      // this is disturbing: has-a-parent is conflated with has-been-counted
                continue;

            suitesByName.merge(s.getName(), Collections.singleton(s), (a,b) -> Stream.concat(a.stream(), b.stream()).collect(Collectors.toList()));

            if (s.getNodeId() != null) {
                addSuiteByNode(s);
            }

            totalTests += s.getCases().size();
            for(CaseResult cr : s.getCases()) {
                if(cr.isSkipped()) {
                    skippedTestsCounter++;
                    if (skippedTests != null) {
                        skippedTests.add(cr);
                    }
                } else if(!cr.isPassed()) {
                    failedTests.add(cr);
                } else {
                    if(passedTests != null) {
                        passedTests.add(cr);
                    }
                }

                String pkg = cr.getPackageName(), spkg = safe(pkg);
                PackageResult pr = byPackage(spkg);
                if(pr==null)
                    byPackages.put(spkg,pr=new PackageResult(this,pkg));
                
                if (pr.getStartTime() == -1) {
                    pr.setStartTime(s.getStartTime());
                } else if (s.getStartTime() != -1){
                    pr.setStartTime(Math.min(pr.getStartTime(), s.getStartTime()));
                }
                pr.add(cr);
            }
        }

        failedTests.sort(CaseResult.BY_AGE);

        if(passedTests != null) {
            passedTests.sort(CaseResult.BY_AGE);
        }

        if(skippedTests != null) {
            skippedTests.sort(CaseResult.BY_AGE);
        }

        for (PackageResult pr : byPackages.values())
            pr.freeze();
    }

    private void addSuiteByNode(SuiteResult s) {
        String nodeId = s.getNodeId();

        if (nodeId != null) {
            // If we don't already have an entry for this node, initialize a list for it.
            if (suitesByNode.get(nodeId) == null) {
                suitesByNode.put(nodeId, new ArrayList<>());
            }
            // Add the suite to the list for the node in the map. Phew.
            suitesByNode.get(nodeId).add(s);

            List<String> enclosingBlocks = new ArrayList<>(s.getEnclosingBlocks());
            if (!enclosingBlocks.isEmpty()) {
                populateBlocks(enclosingBlocks, nodeId, null);
            }
        }
    }

    @NonNull
    public TestResult getResultForPipelineBlock(@NonNull String blockId) {
        PipelineBlockWithTests block = getPipelineBlockWithTests(blockId);
        if (block != null) {
            return (TestResult)blockToTestResult(block, this);
        } else {
            return this;
        }
    }

    public boolean isSkipOldReports() {
        return skipOldReports;
    }

    public void setSkipOldReports(boolean skipOldReports) {
        this.skipOldReports = skipOldReports;
    }

    /**
     * Get an aggregated {@link TestResult} for all test results in a {@link PipelineBlockWithTests} and any children it may have.
     */
    @Override
    @NonNull
    public TabulatedResult blockToTestResult(@NonNull PipelineBlockWithTests block, @NonNull TabulatedResult fullResult) {
        TestResult result = new TestResult();
        for (PipelineBlockWithTests child : block.getChildBlocks().values()) {
            TabulatedResult childResult = blockToTestResult(child, fullResult);
            if (childResult instanceof TestResult) {
                for (SuiteResult s : ((TestResult) childResult).getSuites()) {
                    result.add(s);
                }
            }
        }
        if (fullResult instanceof TestResult) {
            TestResult leafResult = ((TestResult) fullResult).getResultByNodes(new ArrayList<>(block.getLeafNodes()));
            for (SuiteResult s : leafResult.getSuites()) {
                result.add(s);
            }
        }
        result.setParentAction(fullResult.getParentAction());

        return result;
    }



    private static final long serialVersionUID = 1L;

    public CaseResult getCase(String suiteName, String transformedFullDisplayName) {
        return getSuites(suiteName)
                .stream()
                .map(suite -> suite.getCase(transformedFullDisplayName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

}
