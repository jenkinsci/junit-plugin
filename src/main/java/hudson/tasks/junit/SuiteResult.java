/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Xavier Le Vourch, Tom Huybrechts, Yahoo!, Inc., Victor Garcia
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

import hudson.tasks.test.PipelineTestDetails;
import hudson.tasks.test.TestObject;
import hudson.util.io.ParserConfigurator;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.xml.sax.SAXException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Result of one test suite.
 *
 * <p>
 * The notion of "test suite" is rather arbitrary in JUnit ant task.
 * It's basically one invocation of junit.
 *
 * <p>
 * This object is really only used as a part of the persisted
 * object tree.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public final class SuiteResult implements Serializable {
    private static final Logger LOGGER = Logger.getLogger(SuiteResult.class.getName());
    private final String file;
    private final String name;
    private final String stdout;
    private final String stderr;
    private float duration;
    /**
     * The 'timestamp' attribute of  the test suite.
     * AFAICT, this is not a required attribute in XML, so the value may be null.
     */
    private String timestamp;
    /**
     * Optional ID attribute of a test suite. E.g., Eclipse plug-ins tests always have the name 'tests' but a different id.
     **/
    private String id;

    /**
     * Optional time attribute of a test suite. E.g., Suites can use their own time attribute or the sum of their cases' times as before.
     **/
    private String time;

    /**
     * Optional {@link FlowNode#getId()} this suite was generated in.
     */
    private String nodeId;

    private final List<String> enclosingBlocks = new ArrayList<>();

    private final List<String> enclosingBlockNames = new ArrayList<>();

    /**
     * All test cases.
     */
    private final List<CaseResult> cases = new ArrayList<CaseResult>();
    private transient Map<String, CaseResult> casesByName;
    private transient hudson.tasks.junit.TestResult parent;

    @Deprecated
    SuiteResult(String name, String stdout, String stderr) {
        this(name, stdout, stderr, null);
    }

    /**
     * @since 1.22
     */
    SuiteResult(String name, String stdout, String stderr, @CheckForNull PipelineTestDetails pipelineTestDetails) {
        this.name = name;
        this.stderr = stderr;
        this.stdout = stdout;
        // runId is generally going to be not null, but we only care about it if both it and nodeId are not null.
        if (pipelineTestDetails != null && pipelineTestDetails.getNodeId() != null) {
            this.nodeId = pipelineTestDetails.getNodeId();
            this.enclosingBlocks.addAll(pipelineTestDetails.getEnclosingBlocks());
            this.enclosingBlockNames.addAll(pipelineTestDetails.getEnclosingBlockNames());
        } else {
            this.nodeId = null;
        }
        this.file = null;
    }

    private synchronized Map<String, CaseResult> casesByName() {
        if (casesByName == null) {
            casesByName = new HashMap<>();
            for (CaseResult c : cases) {
                casesByName.put(c.getTransformedFullDisplayName(), c);
            }
        }
        return casesByName;
    }

    /**
     * Passed to {@link ParserConfigurator}.
     *
     * @since 1.416
     * @deprecated with no replacement.
     */
    @Deprecated
    public static class SuiteResultParserConfigurationContext {
        public final File xmlReport;

        SuiteResultParserConfigurationContext(File xmlReport) {
            this.xmlReport = xmlReport;
        }
    }

    /**
     * Parses the JUnit XML file into {@link SuiteResult}s.
     * This method returns a collection, as a single XML may have multiple &lt;testsuite>
     * elements wrapped into the top-level &lt;testsuites>.
     */
    static List<SuiteResult> parse(File xmlReport, boolean keepLongStdio, PipelineTestDetails pipelineTestDetails)
            throws DocumentException, IOException, InterruptedException {
        List<SuiteResult> r = new ArrayList<SuiteResult>();

        // parse into DOM
        SAXReader saxReader = new SAXReader();
        
        //source: https://www.owasp.org/index.php/XML_External_Entity_(XXE)_Prevention_Cheat_Sheet => SAXReader
        // setFeatureQuietly(saxReader, "http://apache.org/xml/features/disallow-doctype-decl", true);
        // setFeatureQuietly(saxReader, "http://xml.org/sax/features/external-parameter-entities", false);

        // only that seems to let the initial feature of testng namespace being loaded locally
        setFeatureQuietly(saxReader, "http://xml.org/sax/features/external-general-entities", false);

        saxReader.setEntityResolver(new XMLEntityResolver());

        FileInputStream xmlReportStream = new FileInputStream(xmlReport);
        try {
            Document result = saxReader.read(xmlReportStream);
            Element root = result.getRootElement();

            parseSuite(xmlReport, keepLongStdio, r, root, pipelineTestDetails);
        } finally {
            xmlReportStream.close();
        }

        return r;
    }

    private static void setFeatureQuietly(SAXReader reader, String feature, boolean value) {
        try {
            reader.setFeature(feature, value);
        }
        catch (SAXException ignored) {
            // ignore and continue in case the feature cannot be changed
        }
    }

    private static void parseSuite(File xmlReport, boolean keepLongStdio, List<SuiteResult> r, Element root,
                                   PipelineTestDetails pipelineTestDetails) throws DocumentException, IOException {
        // nested test suites
        @SuppressWarnings("unchecked")
        List<Element> testSuites = (List<Element>) root.elements("testsuite");
        for (Element suite : testSuites)
            parseSuite(xmlReport, keepLongStdio, r, suite, pipelineTestDetails);

        // child test cases
        // FIXME: do this also if no testcases!
        if (root.element("testcase") != null || root.element("error") != null)
            r.add(new SuiteResult(xmlReport, root, keepLongStdio, pipelineTestDetails));
    }

    /**
     * @param xmlReport A JUnit XML report file whose top level element is 'testsuite'.
     * @param suite     The parsed result of {@code xmlReport}
     */
    private SuiteResult(File xmlReport, Element suite, boolean keepLongStdio, @CheckForNull PipelineTestDetails pipelineTestDetails)
            throws DocumentException, IOException {
        this.file = xmlReport.getAbsolutePath();
        String name = suite.attributeValue("name");
        if (name == null)
            // some user reported that name is null in their environment.
            // see http://www.nabble.com/Unexpected-Null-Pointer-Exception-in-Hudson-1.131-tf4314802.html
            name = '(' + xmlReport.getName() + ')';
        else {
            String pkg = suite.attributeValue("package");
            if (pkg != null && pkg.length() > 0) name = pkg + '.' + name;
        }
        this.name = TestObject.safe(name);
        this.timestamp = suite.attributeValue("timestamp");
        this.id = suite.attributeValue("id");
        if (pipelineTestDetails != null && pipelineTestDetails.getNodeId() != null) {
            this.nodeId = pipelineTestDetails.getNodeId();
            this.enclosingBlocks.addAll(pipelineTestDetails.getEnclosingBlocks());
            this.enclosingBlockNames.addAll(pipelineTestDetails.getEnclosingBlockNames());
        }

        // check for test suite time attribute
        if ((this.time = suite.attributeValue("time")) != null) {
            duration = new TimeToFloat(this.time).parse();
        }

        Element ex = suite.element("error");
        if (ex != null) {
            // according to junit-noframes.xsl l.229, this happens when the test class failed to load
            addCase(new CaseResult(this, suite, "<init>", keepLongStdio));
        }

        @SuppressWarnings("unchecked")
        List<Element> testCases = (List<Element>) suite.elements("testcase");
        for (Element e : testCases) {
            // https://issues.jenkins-ci.org/browse/JENKINS-1233 indicates that
            // when <testsuites> is present, we are better off using @classname on the
            // individual testcase class.

            // https://issues.jenkins-ci.org/browse/JENKINS-1463 indicates that
            // @classname may not exist in individual testcase elements. We now
            // also test if the testsuite element has a package name that can be used
            // as the class name instead of the file name which is default.
            String classname = e.attributeValue("classname");
            if (classname == null) {
                classname = suite.attributeValue("name");
            }

            // https://issues.jenkins-ci.org/browse/JENKINS-1233 and
            // http://www.nabble.com/difference-in-junit-publisher-and-ant-junitreport-tf4308604.html#a12265700
            // are at odds with each other --- when both are present,
            // one wants to use @name from <testsuite>,
            // the other wants to use @classname from <testcase>.

            addCase(new CaseResult(this, e, classname, keepLongStdio));
        }

        String stdout = CaseResult.possiblyTrimStdio(cases, keepLongStdio, suite.elementText("system-out"));
        String stderr = CaseResult.possiblyTrimStdio(cases, keepLongStdio, suite.elementText("system-err"));
        if (stdout == null && stderr == null) {
            // Surefire never puts stdout/stderr in the XML. Instead, it goes to a separate file (when ${maven.test.redirectTestOutputToFile}).
            Matcher m = SUREFIRE_FILENAME.matcher(xmlReport.getName());
            if (m.matches()) {
                // look for ***-output.txt from TEST-***.xml
                File mavenOutputFile = new File(xmlReport.getParentFile(), m.group(1) + "-output.txt");
                if (mavenOutputFile.exists()) {
                    try {
                        stdout = CaseResult.possiblyTrimStdio(cases, keepLongStdio, mavenOutputFile);
                    } catch (IOException e) {
                        throw new IOException("Failed to read " + mavenOutputFile, e);
                    }
                }
            }
        }

        this.stdout = stdout;
        this.stderr = stderr;
    }

    /*package*/ void addCase(CaseResult cr) {
        cases.add(cr);
        casesByName().put(cr.getTransformedFullDisplayName(), cr);

        //if suite time was not specified use sum of the cases' times
        if( !hasTimeAttr() ){
            duration += cr.getDuration();
        }
    }

    /**
     * Returns true if the time attribute is present in this Suite.
     */
    private boolean hasTimeAttr() {
        return time != null;
    }

    @Exported(visibility=9)
    public String getName() {
        return name;
    }

    @Exported(visibility=9)
    public float getDuration() {
        return duration;
    }

    /**
     * The possibly-null {@link FlowNode#id} this suite was generated in.
     *
     * @since 1.22
     */
    @Exported(visibility=9)
    @CheckForNull
    public String getNodeId() {
        return nodeId;
    }

    /**
     * The possibly-empty list of {@link FlowNode#id}s for enclosing blocks within which this suite was generated.
     *
     * @since 1.22
     */
    @Exported(visibility=9)
    @Nonnull
    public List<String> getEnclosingBlocks() {
        if (enclosingBlocks != null) {
            return Collections.unmodifiableList(enclosingBlocks);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * The possibly-empty list of display names of enclosing blocks within which this suite was generated.
     *
     * @since 1.22
     */
    @Exported(visibility=9)
    @Nonnull
    public List<String> getEnclosingBlockNames() {
        if (enclosingBlockNames != null) {
            return Collections.unmodifiableList(enclosingBlockNames);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * The stdout of this test.
     *
     * @return the stdout of this test.
     * @since 1.281
     * @see CaseResult#getStdout()
     */
    @Exported
    public String getStdout() {
        return stdout;
    }

    /**
     * The stderr of this test.
     *
     * @return the stderr of this test.
     * @since 1.281
     * @see CaseResult#getStderr()
     */
    @Exported
    public String getStderr() {
        return stderr;
    }

    /**
     * The absolute path to the original test report. OS-dependent.
     *
     * @return the absolute path to the original test report.
     */
    public String getFile() {
        return file;
    }

    public hudson.tasks.junit.TestResult getParent() {
        return parent;
    }

    @Exported(visibility=9)
    public String getTimestamp() {
        return timestamp;
    }

    @Exported(visibility=9)
    public String getId() {
        return id;
    }

    @Exported(inline=true,visibility=9)
    public List<CaseResult> getCases() {
        return cases;
    }

    public SuiteResult getPreviousResult() {
        hudson.tasks.test.TestResult pr = parent.getPreviousResult();
        if(pr==null)    return null;
        if(pr instanceof hudson.tasks.junit.TestResult)
            return ((hudson.tasks.junit.TestResult)pr).getSuite(name);
        return null;
    }

    /**
     * Returns the {@link CaseResult} whose {@link CaseResult#getFullDisplayName()}
     * is the same as the given string.
     *
     * @param caseResultFullDisplayName The case FullDisplayName.
     *
     * @return the {@link CaseResult} with the provided name.
     */
    public CaseResult getCase(String caseResultFullDisplayName) {
        return casesByName().get(caseResultFullDisplayName);
    }

    public Set<String> getClassNames() {
        Set<String> result = new HashSet<String>();
        for (CaseResult c : cases) {
            result.add(c.getClassName());
        }
        return result;
    }

    /** KLUGE. We have to call this to prevent freeze()
     * from calling c.freeze() on all its children,
     * because that in turn calls c.getOwner(),
     * which requires a non-null parent.
     * @param parent
     */
    void setParent(hudson.tasks.junit.TestResult parent) {
        this.parent = parent;
    }

    /*package*/ boolean freeze(hudson.tasks.junit.TestResult owner) {
        if(this.parent!=null)
            return false;   // already frozen

        this.parent = owner;
        for (CaseResult c : cases)
            c.freeze(this);
        return true;
    }

    private static final long serialVersionUID = 1L;

    private static final Pattern SUREFIRE_FILENAME = Pattern.compile("TEST-(.+)\\.xml");

    /**
     * Merges another SuiteResult into this one.
     * 
     * @param sr the SuiteResult to merge into this one
     */
    public void merge(SuiteResult sr) {
        if (sr.hasTimeAttr() ^ hasTimeAttr()){
            LOGGER.warning("Merging of suiteresults with incompatible time attribute may lead to incorrect durations in reports.( "+getFile()+", "+sr.getFile()+")");
        }
        if (hasTimeAttr()) {
            duration += sr.getDuration();
        }
        for (CaseResult cr : sr.getCases()) {
            addCase(cr);
            cr.replaceParent(this);
        }
    }
}
