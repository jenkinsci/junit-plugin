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
package hudson.tasks.junit;

import hudson.XmlFile;
import hudson.tasks.test.PipelineTestDetails;
import hudson.util.HeapSpaceStringConverter;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.jvnet.hudson.test.Bug;

import com.thoughtworks.xstream.XStream;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.*;

/**
 * Tests the JUnit result XML file parsing in {@link TestResult}.
 *
 * @author dty
 */
public class TestResultTest {
    protected static File getDataFile(String name) throws URISyntaxException {
        return new File(TestResultTest.class.getResource(name).toURI());
    }

    /**
     * Verifies that all suites of an Eclipse Plug-in Test Suite are collected.
     * These suites don't differ by name (and timestamp), the y differ by 'id'.
     */
    @Test
    public void testIpsTests() throws Exception {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("eclipse-plugin-test-report.xml"), new PipelineTestDetails());

        Collection<SuiteResult> suites = testResult.getSuites();
        assertEquals("Wrong number of test suites", 16, suites.size());
        int testCaseCount = 0;
        for (SuiteResult suite : suites) {
            testCaseCount += suite.getCases().size();
        }
        assertEquals("Wrong number of test cases", 3366, testCaseCount);
    }

    /**
     * This test verifies compatibility of JUnit test results persisted to
     * XML prior to the test code refactoring.
     * 
     * @throws Exception
     */
    @Test
    public void testXmlCompatibility() throws Exception {
        XmlFile xmlFile = new XmlFile(XSTREAM, getDataFile("junitResult.xml"));
        TestResult result = (TestResult)xmlFile.read();

        // Regenerate the transient data
        result.tally();
        assertEquals(9, result.getTotalCount());
        assertEquals(1, result.getSkipCount());
        assertEquals(1, result.getFailCount());

        // XStream seems to produce some weird rounding errors...
        assertEquals(0.576, result.getDuration(), 0.0001);

        Collection<SuiteResult> suites = result.getSuites();
        assertEquals(6, suites.size());

        List<CaseResult> failedTests = result.getFailedTests();
        assertEquals(1, failedTests.size());

        SuiteResult failedSuite = result.getSuite("broken");
        assertNotNull(failedSuite);
        CaseResult failedCase = failedSuite.getCase("breakable.misc.UglyTest.becomeUglier");
        assertNotNull(failedCase);
        assertFalse(failedCase.isSkipped());
        assertFalse(failedCase.isPassed());
        assertEquals(5, failedCase.getFailedSince());
    }

    /**
     * When  skipped test case result does not contain message attribute then the skipped xml element text is retrieved
     */
    @Test
    public void testSkippedMessageIsAddedWhenTheMessageAttributeIsNull() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("SKIPPED_MESSAGE/skippedTestResult.xml"), null);
        List<SuiteResult> suiteResults = new ArrayList<>(testResult.getSuites());
        CaseResult caseResult = suiteResults.get(0).getCases().get(0);
        assertEquals("Given skip This Test........................................................pending\n", caseResult.getSkippedMessage());
    }

    /**
     * When test methods are parametrized, they can occur multiple times in the testresults XMLs.
     * Test that these are counted correctly.
     */
    @Issue("JENKINS-13214")
    @Test
    public void testDuplicateTestMethods() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-13214/27449.xml"), null);
        testResult.parse(getDataFile("JENKINS-13214/27540.xml"), null);
        testResult.parse(getDataFile("JENKINS-13214/29734.xml"), null);
        testResult.tally();
        
        assertEquals("Wrong number of test suites", 1, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 3, testResult.getTotalCount());
    }
    
    @Issue("JENKINS-12457")
    @Test
    public void testTestSuiteDistributedOverMultipleFilesIsCountedAsOne() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_a1.xml"), null);
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_a2.xml"), null);
        testResult.tally();
        
        assertEquals("Wrong number of testsuites", 1, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 2, testResult.getTotalCount());
        
        // check duration: 157.980 (TestSuite_a1.xml) and 15.000 (TestSuite_a2.xml) = 172.98 
        assertEquals("Wrong duration for test result", 172.98, testResult.getDuration(), 0.1);
    }

    @Issue("JENKINS-41134")
    @Test
    public void testMerge() throws IOException, URISyntaxException {
        TestResult first = new TestResult();
        TestResult second = new TestResult();

        first.parse(getDataFile("JENKINS-41134/TestSuite_first.xml"), null);
        second.parse(getDataFile("JENKINS-41134/TestSuite_second.xml"), null);
        assertEquals("Fail count should be 0", 0, first.getFailCount());
        first.merge(second);
        assertEquals("Fail count should now be 1", 1, first.getFailCount());

        first = new TestResult();
        second = new TestResult();
        first.parse(getDataFile("JENKINS-41134/TestSuite_first.xml"), null);
        second.parse(getDataFile("JENKINS-41134/TestSuite_second_dup_first.xml"), null);
        assertEquals("Fail count should be 0", 0, first.getFailCount());
        first.merge(second);
        assertEquals("Fail count should now be 1", 1, first.getFailCount());
    }

    @Issue("JENKINS-37598")
    @Test
    public void testMergeWithTime() throws Exception {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("junit-report-time-aggregation.xml"));
        testResult.tally();

        assertEquals(1, testResult.getSuites().size());
        SuiteResult suite = testResult.getSuite("test.fs.FileSystemTests");
        assertEquals(3, suite.getCases().size());
        assertEquals(100, suite.getDuration(), 2);
    }

    @Issue("JENKINS-37598")
    @Test
    public void testMergeWithoutTime() throws Exception {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("junit-report-time-aggregation2.xml"));
        testResult.tally();

        assertEquals(1, testResult.getSuites().size());
        SuiteResult suite = testResult.getSuite("test.fs.FileSystemTests");
        assertEquals(3, suite.getCases().size());
        assertEquals(30, suite.getDuration(), 2);
    }

    @Issue("JENKINS-42438")
    @Test
    public void testSuiteWithMultipleClasses() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-42438/junit-report-1.xml"));
        testResult.tally();

        assertEquals("Wrong number of testsuites", 1, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 11, testResult.getTotalCount());

        // The suite duration is non-sensical for Android tests.
        // This looks like a bug in the JUnit runner used by Android tests.
        assertEquals("Wrong duration for test result", 2.0, testResult.getDuration(), 0.1);

        SuiteResult suite = testResult.getSuite("org.catrobat.paintroid.test.integration.ActivityOpenedFromPocketCodeNewImageTest");
        assertNotNull(suite);

        assertEquals("Wrong number of test classes", 2, suite.getClassNames().size());

        CaseResult case1 = suite.getCase("org.catrobat.paintroid.test.integration.BitmapIntegrationTest.testDrawingSurfaceBitmapIsScreenSize");
        assertNotNull(case1);
        ClassResult class1 = case1.getParent();
        assertNotNull(class1);
        assertEquals("org.catrobat.paintroid.test.integration.BitmapIntegrationTest", class1.getFullName());
        assertEquals("Wrong duration for test class", 5.0, class1.getDuration(),0.1);

        CaseResult case2 = suite.getCase("org.catrobat.paintroid.test.integration.LandscapeTest.testColorPickerDialogSwitchTabsInLandscape");
        assertNotNull(case2);
        ClassResult class2 = case2.getParent();
        assertNotNull(class2);
        assertEquals("org.catrobat.paintroid.test.integration.LandscapeTest", class2.getFullName());
        assertEquals("Wrong duration for test class", 93.0, class2.getDuration(), 0.1);
    }

    @Issue("JENKINS-48583")
    @Test
    public void testMergeOriginalAntOutput() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-48583/TEST-com.sample.test.TestMessage.xml"), null);
        testResult.parse(getDataFile("JENKINS-48583/TEST-com.sample.test.TestMessage2.xml"), null);
        testResult.parse(getDataFile("JENKINS-48583/TESTS-TestSuites.xml"), null);
        testResult.parse(getDataFile("JENKINS-48583/TEST-com.sample.test.TestMessage.xml"), null);
        testResult.tally();
        
        assertEquals("Wrong number of testsuites", 2, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 7, testResult.getTotalCount());
    }
    
    /**
     * Sometimes legitimage test cases are split over multiple files with identical timestamps.
     */
    @Issue("JENKINS-48583")
    @Test
    public void testNonDuplicatedTestSuiteIsCounted() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_b.xml"), null);
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_b_duplicate.xml"), null);
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_b_nonduplicate.xml"), null);
        testResult.tally();

        assertEquals("Wrong number of testsuites", 1, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 3, testResult.getTotalCount());
    }
    
    @Issue("JENKINS-63113")
    @Test
    public void testTestcaseWithEmptyName() throws Exception {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("junit-report-empty-testcasename.xml"));
        testResult.tally();

        assertEquals("Wrong number of testsuites", 1, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 1, testResult.getTotalCount());

        SuiteResult suite = testResult.getSuite("test.TestJUnit5FailingInBeforeAll");
        assertNotNull(suite);

        assertEquals("Wrong number of test classes", 1, suite.getClassNames().size());
        CaseResult case1 = suite.getCases().get(0);

        assertEquals("test.TestJUnit5FailingInBeforeAll.(?)", case1.getFullName());
        assertEquals("(?)", case1.getDisplayName());
        assertEquals("(?)", case1.getName());
    }

    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("result",TestResult.class);
        XSTREAM.alias("suite",SuiteResult.class);
        XSTREAM.alias("case",CaseResult.class);
        XSTREAM.registerConverter(new HeapSpaceStringConverter(),100);
    }
}

