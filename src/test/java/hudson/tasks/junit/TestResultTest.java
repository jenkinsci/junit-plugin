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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.tools.ant.DirectoryScanner;
import org.junit.Test;

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
        XmlFile xmlFile = new XmlFile(TestResultAction.XSTREAM, getDataFile("junitResult.xml"));
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

    @Test
    public void skipOldReports() throws Exception {
        long start = System.currentTimeMillis();
        File testResultFile1 = new File("src/test/resources/hudson/tasks/junit/old-reports/junit-report-1.xml");
        Files.setLastModifiedTime(testResultFile1.toPath(), FileTime.fromMillis(start + 10));
        File testResultFile2 = new File("src/test/resources/hudson/tasks/junit/old-reports/junit-report-2.xml");
        Files.setLastModifiedTime(testResultFile2.toPath(), FileTime.fromMillis(start - 4000));
        DirectoryScanner directoryScanner = new DirectoryScanner();
        directoryScanner.setBasedir(new File("src/test/resources/hudson/tasks/junit/old-reports/"));
        directoryScanner.setIncludes(new String[]{"*.xml"});
        directoryScanner.scan();
        assertEquals( "directory scanner must find 2 files", 2, directoryScanner.getIncludedFiles().length);
        TestResult testResult = new TestResult(start, directoryScanner, true, false, false, new PipelineTestDetails(),true);
        testResult.tally();

        assertEquals("Wrong number of testsuites", 2, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 3, testResult.getTotalCount());

    }

    @Test
    public void parseOldReports() throws Exception {
        long start = System.currentTimeMillis();
        File testResultFile1 = new File("src/test/resources/hudson/tasks/junit/old-reports/junit-report-1.xml");
        Files.setLastModifiedTime(testResultFile1.toPath(), FileTime.fromMillis(start + 10));
        File testResultFile2 = new File("src/test/resources/hudson/tasks/junit/old-reports/junit-report-2.xml");
        Files.setLastModifiedTime(testResultFile2.toPath(), FileTime.fromMillis(start - 4000));
        DirectoryScanner directoryScanner = new DirectoryScanner();
        directoryScanner.setBasedir(new File("src/test/resources/hudson/tasks/junit/old-reports/"));
        directoryScanner.setIncludes(new String[]{"*.xml"});
        directoryScanner.scan();
        assertEquals( "directory scanner must find 2 files", 2, directoryScanner.getIncludedFiles().length);
        TestResult testResult = new TestResult(start, directoryScanner, true, false, new PipelineTestDetails(),false);
        testResult.tally();

        assertEquals("Wrong number of testsuites", 4, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 6, testResult.getTotalCount());

    }
    @Test
    public void clampDuration() throws Exception {
        long start = System.currentTimeMillis();
        File testResultFile1 = new File("src/test/resources/hudson/tasks/junit/junit-report-bad-duration.xml");
        DirectoryScanner directoryScanner = new DirectoryScanner();
        directoryScanner.setBasedir(new File("src/test/resources/hudson/tasks/junit/"));
        directoryScanner.setIncludes(new String[]{"*-bad-duration.xml"});
        directoryScanner.scan();
        assertEquals( "directory scanner must find 1 files", 1, directoryScanner.getIncludedFiles().length);
        TestResult testResult = new TestResult(start, directoryScanner, true, false, new PipelineTestDetails(), false);
        testResult.tally();
        assertEquals("Negative duration is invalid", 100, testResult.getDuration(), 0.00001);
        assertEquals("Wrong number of testsuites", 1, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 2, testResult.getTotalCount());
    }
    @Test
    public void testStartTimes() throws Exception {
    	// Tests that start times are as expected for file with a mix of valid,
    	// invalid, and unspecified timestamps.
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("junit-report-testsuite-various-timestamps.xml"));
        testResult.tally();
        // Test that TestResult startTime is the startTime of the earliest suite.
        assertEquals(1704281235000L, testResult.getStartTime());
        
        // Test that suites have correct start times
        List<SuiteResult> suites = (List<SuiteResult>)testResult.getSuites();
        assertEquals(-1, suites.get(0).getStartTime());
        assertEquals(1704284831000L, suites.get(1).getStartTime());
        assertEquals(1704285613000L, suites.get(2).getStartTime());
        assertEquals(1704284864000L, suites.get(3).getStartTime());
        assertEquals(-1, suites.get(4).getStartTime());
        assertEquals(-1, suites.get(5).getStartTime());
        assertEquals(1704288431210L, suites.get(6).getStartTime());
        assertEquals(1704281235000L, suites.get(7).getStartTime());
        
        // Test each package and its descendants for correct start times.
        PackageResult pkg =  testResult.byPackage("(root)");
        assertEquals(1704281235000L, pkg.getStartTime());
        
        ClassResult class1 = pkg.getClassResult("contents adjust properly when resizing test");
        CaseResult case1 = class1.getCaseResult("testResize");
        assertEquals(1704288431210L, class1.getStartTime());
        assertEquals(1704288431210L, case1.getStartTime());
        
        ClassResult class2 = pkg.getClassResult("date reflects offset test");
        CaseResult case2 = class2.getCaseResult("testDate");
        assertEquals(-1, class2.getStartTime());
        assertEquals(-1, case2.getStartTime());
        
        ClassResult class3 = pkg.getClassResult("get test");
        CaseResult case3 = class3.getCaseResult("testGet");
        assertEquals(-1, class3.getStartTime());
        assertEquals(-1, case3.getStartTime());
        
        ClassResult class4 = pkg.getClassResult("testButtons");
        CaseResult case4 = class4.getCaseResult("home_button_redirects_to_home_test");
        CaseResult case5 = class4.getCaseResult("sign_out_button_ends_session_test");
        CaseResult case6 = class4.getCaseResult("sign_out_button_redirects_to_sign_in_test");
        assertEquals(1704285613000L, class4.getStartTime());
        assertEquals(1704285613000L, case4.getStartTime());
        assertEquals(1704285617000L, case5.getStartTime());
        assertEquals(1704285628000L, case6.getStartTime());
        
        ClassResult class5 = pkg.getClassResult("testPassword");
        CaseResult case7 = class5.getCaseResult("invalid_if_password_does_not_match_test");
        CaseResult case8 = class5.getCaseResult("invalid_if_password_is_weak_test");
        assertEquals(1704284831000L, class5.getStartTime());
        assertEquals(1704284831000L, case7.getStartTime());
        assertEquals(1704284838000L, case8.getStartTime());
        
        ClassResult class6 = pkg.getClassResult("pages load in under ten seconds under ideal conditions test");
        CaseResult case9 = class6.getCaseResult("testExperience");
        assertEquals(1704284864000L, class6.getStartTime());
        assertEquals(1704284864000L, case9.getStartTime());
        
        ClassResult class7 = pkg.getClassResult("popups triggered when hovering test");
        CaseResult case10 = class7.getCaseResult("testPopup");
        assertEquals(-1, class7.getStartTime());
        assertEquals(-1, case10.getStartTime());
        
        ClassResult class8 = pkg.getClassResult("proper images displayed when items added");
        CaseResult case11 = class8.getCaseResult("testShop");
        assertEquals(1704281235000L, class8.getStartTime());
        assertEquals(1704281235000L, case11.getStartTime());
       
        ClassResult class9 = pkg.getClassResult("time offset is correct test");
        CaseResult case12 = class9.getCaseResult("testOffset");
        assertEquals(-1, class9.getStartTime());
        assertEquals(-1, case12.getStartTime());
        
    }

}
