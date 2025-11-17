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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

/**
 * Tests the JUnit result XML file parsing in {@link TestResult}.
 *
 * @author dty
 */
public class TestResultTest {

    @TempDir
    private File tmp;

    protected static File getDataFile(String name) throws URISyntaxException {
        return new File(TestResultTest.class.getResource(name).toURI());
    }

    /**
     * Verifies that all suites of an Eclipse Plug-in Test Suite are collected.
     * These suites don't differ by name (and timestamp), the y differ by 'id'.
     */
    @Test
    void testIpsTests() throws Exception {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("eclipse-plugin-test-report.xml"), new PipelineTestDetails());

        Collection<SuiteResult> suites = testResult.getSuites();
        assertEquals(16, suites.size(), "Wrong number of test suites");
        int testCaseCount = 0;
        for (SuiteResult suite : suites) {
            testCaseCount += suite.getCases().size();
        }
        assertEquals(3366, testCaseCount, "Wrong number of test cases");
    }

    /**
     * This test verifies compatibility of JUnit test results persisted to
     * XML prior to the test code refactoring.
     *
     * @throws Exception
     */
    @Test
    void testXmlCompatibility() throws Exception {
        XmlFile xmlFile = new XmlFile(TestResultAction.XSTREAM, getDataFile("junitResult.xml"));
        TestResult result = (TestResult) xmlFile.read();

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
    void testSkippedMessageIsAddedWhenTheMessageAttributeIsNull() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("SKIPPED_MESSAGE/skippedTestResult.xml"), null);
        List<SuiteResult> suiteResults = new ArrayList<>(testResult.getSuites());
        CaseResult caseResult = suiteResults.get(0).getCases().get(0);
        assertEquals(
                "Given skip This Test........................................................pending\n",
                caseResult.getSkippedMessage());
    }

    /**
     * When test methods are parametrized, they can occur multiple times in the testresults XMLs.
     * Test that these are counted correctly.
     */
    @Issue("JENKINS-13214")
    @Test
    void testDuplicateTestMethods() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-13214/27449.xml"), null);
        testResult.parse(getDataFile("JENKINS-13214/27540.xml"), null);
        testResult.parse(getDataFile("JENKINS-13214/29734.xml"), null);
        testResult.tally();

        assertEquals(1, testResult.getSuites().size(), "Wrong number of test suites");
        assertEquals(3, testResult.getTotalCount(), "Wrong number of test cases");
    }

    @Issue("JENKINS-12457")
    @Test
    void testTestSuiteDistributedOverMultipleFilesIsCountedAsOne() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_a1.xml"), null);
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_a2.xml"), null);
        testResult.tally();

        assertEquals(1, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(2, testResult.getTotalCount(), "Wrong number of test cases");

        // check duration: 157.980 (TestSuite_a1.xml) and 15.000 (TestSuite_a2.xml) = 172.98
        assertEquals(172.98, testResult.getDuration(), 0.1, "Wrong duration for test result");
    }

    @Issue("JENKINS-41134")
    @Test
    void testMerge() throws IOException, URISyntaxException {
        TestResult first = new TestResult();
        TestResult second = new TestResult();

        first.parse(getDataFile("JENKINS-41134/TestSuite_first.xml"), null);
        second.parse(getDataFile("JENKINS-41134/TestSuite_second.xml"), null);
        assertEquals(0, first.getFailCount(), "Fail count should be 0");
        first.merge(second);
        assertEquals(1, first.getFailCount(), "Fail count should now be 1");

        first = new TestResult();
        second = new TestResult();
        first.parse(getDataFile("JENKINS-41134/TestSuite_first.xml"), null);
        second.parse(getDataFile("JENKINS-41134/TestSuite_second_dup_first.xml"), null);
        assertEquals(0, first.getFailCount(), "Fail count should be 0");
        first.merge(second);
        assertEquals(1, first.getFailCount(), "Fail count should now be 1");
    }

    @Issue("JENKINS-37598")
    @Test
    void testMergeWithTime() throws Exception {
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
    void testMergeWithoutTime() throws Exception {
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
    void testSuiteWithMultipleClasses() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-42438/junit-report-1.xml"));
        testResult.tally();

        assertEquals(1, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(11, testResult.getTotalCount(), "Wrong number of test cases");

        // The suite duration is non-sensical for Android tests.
        // This looks like a bug in the JUnit runner used by Android tests.
        assertEquals(2.0, testResult.getDuration(), 0.1, "Wrong duration for test result");

        SuiteResult suite =
                testResult.getSuite("org.catrobat.paintroid.test.integration.ActivityOpenedFromPocketCodeNewImageTest");
        assertNotNull(suite);

        assertEquals(2, suite.getClassNames().size(), "Wrong number of test classes");

        CaseResult case1 = suite.getCase(
                "org.catrobat.paintroid.test.integration.BitmapIntegrationTest.testDrawingSurfaceBitmapIsScreenSize");
        assertNotNull(case1);
        ClassResult class1 = case1.getParent();
        assertNotNull(class1);
        assertEquals("org.catrobat.paintroid.test.integration.BitmapIntegrationTest", class1.getFullName());
        assertEquals(5.0, class1.getDuration(), 0.1, "Wrong duration for test class");

        CaseResult case2 = suite.getCase(
                "org.catrobat.paintroid.test.integration.LandscapeTest.testColorPickerDialogSwitchTabsInLandscape");
        assertNotNull(case2);
        ClassResult class2 = case2.getParent();
        assertNotNull(class2);
        assertEquals("org.catrobat.paintroid.test.integration.LandscapeTest", class2.getFullName());
        assertEquals(93.0, class2.getDuration(), 0.1, "Wrong duration for test class");
    }

    @Issue("JENKINS-48583")
    @Test
    void testMergeOriginalAntOutput() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-48583/TEST-com.sample.test.TestMessage.xml"), null);
        testResult.parse(getDataFile("JENKINS-48583/TEST-com.sample.test.TestMessage2.xml"), null);
        testResult.parse(getDataFile("JENKINS-48583/TESTS-TestSuites.xml"), null);
        testResult.parse(getDataFile("JENKINS-48583/TEST-com.sample.test.TestMessage.xml"), null);
        testResult.tally();

        assertEquals(2, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(7, testResult.getTotalCount(), "Wrong number of test cases");
    }

    /**
     * Sometimes legitimage test cases are split over multiple files with identical timestamps.
     */
    @Issue("JENKINS-48583")
    @Test
    void testNonDuplicatedTestSuiteIsCounted() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_b.xml"), null);
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_b_duplicate.xml"), null);
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_b_nonduplicate.xml"), null);
        testResult.tally();

        assertEquals(1, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(3, testResult.getTotalCount(), "Wrong number of test cases");
    }

    @Issue("JENKINS-63113")
    @Test
    void testTestcaseWithEmptyName() throws Exception {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("junit-report-empty-testcasename.xml"));
        testResult.tally();

        assertEquals(1, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(1, testResult.getTotalCount(), "Wrong number of test cases");

        SuiteResult suite = testResult.getSuite("test.TestJUnit5FailingInBeforeAll");
        assertNotNull(suite);

        assertEquals(1, suite.getClassNames().size(), "Wrong number of test classes");
        CaseResult case1 = suite.getCases().get(0);

        assertEquals("test.TestJUnit5FailingInBeforeAll.(?)", case1.getFullName());
        assertEquals("(?)", case1.getDisplayName());
        assertEquals("(?)", case1.getName());
    }

    @Test
    void skipOldReports() throws Exception {
        long start = System.currentTimeMillis();
        File testResultFile1 = new File("src/test/resources/hudson/tasks/junit/old-reports/junit-report-1.xml");
        Files.setLastModifiedTime(testResultFile1.toPath(), FileTime.fromMillis(start + 10));
        File testResultFile2 = new File("src/test/resources/hudson/tasks/junit/old-reports/junit-report-2.xml");
        Files.setLastModifiedTime(testResultFile2.toPath(), FileTime.fromMillis(start - 4000));
        DirectoryScanner directoryScanner = new DirectoryScanner();
        directoryScanner.setBasedir(new File("src/test/resources/hudson/tasks/junit/old-reports/"));
        directoryScanner.setIncludes(new String[] {"*.xml"});
        directoryScanner.scan();
        assertEquals(2, directoryScanner.getIncludedFiles().length, "directory scanner must find 2 files");
        TestResult testResult =
                new TestResult(start, directoryScanner, true, false, false, new PipelineTestDetails(), true);
        testResult.tally();

        assertEquals(2, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(3, testResult.getTotalCount(), "Wrong number of test cases");
    }

    @Test
    void parseOldReports() throws Exception {
        long start = System.currentTimeMillis();
        File testResultFile1 = new File("src/test/resources/hudson/tasks/junit/old-reports/junit-report-1.xml");
        Files.setLastModifiedTime(testResultFile1.toPath(), FileTime.fromMillis(start + 10));
        File testResultFile2 = new File("src/test/resources/hudson/tasks/junit/old-reports/junit-report-2.xml");
        Files.setLastModifiedTime(testResultFile2.toPath(), FileTime.fromMillis(start - 4000));
        DirectoryScanner directoryScanner = new DirectoryScanner();
        directoryScanner.setBasedir(new File("src/test/resources/hudson/tasks/junit/old-reports/"));
        directoryScanner.setIncludes(new String[] {"*.xml"});
        directoryScanner.scan();
        assertEquals(2, directoryScanner.getIncludedFiles().length, "directory scanner must find 2 files");
        TestResult testResult = new TestResult(start, directoryScanner, true, false, new PipelineTestDetails(), false);
        testResult.tally();

        assertEquals(4, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(6, testResult.getTotalCount(), "Wrong number of test cases");
    }

    @Test
    void clampDuration() throws Exception {
        long start = System.currentTimeMillis();
        File testResultFile1 = new File("src/test/resources/hudson/tasks/junit/junit-report-bad-duration.xml");
        DirectoryScanner directoryScanner = new DirectoryScanner();
        directoryScanner.setBasedir(new File("src/test/resources/hudson/tasks/junit/"));
        directoryScanner.setIncludes(new String[] {"*-bad-duration.xml"});
        directoryScanner.scan();
        assertEquals(1, directoryScanner.getIncludedFiles().length, "directory scanner must find 1 files");
        TestResult testResult = new TestResult(start, directoryScanner, true, false, new PipelineTestDetails(), false);
        testResult.tally();
        assertEquals(100, testResult.getDuration(), 0.00001, "Negative duration is invalid");
        assertEquals(1, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(2, testResult.getTotalCount(), "Wrong number of test cases");
    }

    @Test
    void testStartTimes() throws Exception {
        // Tests that start times are as expected for file with a mix of valid,
        // invalid, and unspecified timestamps.
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("junit-report-testsuite-various-timestamps.xml"));
        testResult.tally();
        // Test that TestResult startTime is the startTime of the earliest suite.
        assertEquals(1704281235000L, testResult.getStartTime());

        // Test that suites have correct start times
        List<SuiteResult> suites = (List<SuiteResult>) testResult.getSuites();
        assertEquals(-1, suites.get(0).getStartTime());
        assertEquals(1704284831000L, suites.get(1).getStartTime());
        assertEquals(1704285613000L, suites.get(2).getStartTime());
        assertEquals(1704284864000L, suites.get(3).getStartTime());
        assertEquals(-1, suites.get(4).getStartTime());
        assertEquals(-1, suites.get(5).getStartTime());
        assertEquals(1704288431210L, suites.get(6).getStartTime());
        assertEquals(1704281235000L, suites.get(7).getStartTime());

        // Test each package and its descendants for correct start times.
        PackageResult pkg = testResult.byPackage("(root)");
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

    /*
    For performance reasons, we parse the XML directly.
    Make sure parser handles all the fields.
     */
    @Test
    void bigResultReadWrite() throws Exception {
        List<SuiteResult> results =
                SuiteResult.parse(getDataFile("junit-report-huge.xml"), StdioRetention.ALL, true, true, null);
        assertEquals(1, results.size());
        SuiteResult sr = results.get(0);

        TestResult tr = new TestResult();
        tr.getSuites().add(sr);
        XmlFile f = new XmlFile(TestResultAction.XSTREAM, File.createTempFile("junitResult.xml", null, tmp));
        f.write(tr);

        TestResult tr2 = new TestResult();
        tr2.parse(f);
        XmlFile f2 = new XmlFile(TestResultAction.XSTREAM, File.createTempFile("junitResult2.xml", null, tmp));
        f2.write(tr2);

        assertEquals(
                2,
                tr.getSuites().stream()
                        .findFirst()
                        .orElseThrow()
                        .getProperties()
                        .size());
        assertEquals(
                2,
                tr2.getSuites().stream()
                        .findFirst()
                        .orElseThrow()
                        .getProperties()
                        .size());

        boolean isTwoEqual = FileUtils.contentEquals(f.getFile(), f2.getFile());
        assertTrue(isTwoEqual, "Forgot to implement XML parsing for something?");
    }

    @Issue("GH-237")
    @Test
    void includeFlakyAndRerun() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("gh-237/TEST-io.olamy.AlwaysFailTest.xml"), null);
        testResult.parse(getDataFile("gh-237/TEST-io.olamy.FlakyTest.xml"), null);
        testResult.tally();

        assertEquals(2, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(2, testResult.getTotalCount(), "Wrong number of test cases");

        { // assert on flaky
            SuiteResult flakySuiteResult = testResult.getSuite("io.olamy.FlakyTest");
            assertNotNull(flakySuiteResult);
            assertEquals(
                    2,
                    flakySuiteResult.getCase("io.olamy.FlakyTest.testApp").getFlakyFailures().length,
                    "Wrong number of flayfailures");

            FlakyFailure flakyFailure =
                    flakySuiteResult.getCase("io.olamy.FlakyTest.testApp").getFlakyFailures()[0];
            assertNotNull(flakyFailure);
            assertEquals("junit.framework.AssertionFailedError", flakyFailure.type());
            assertEquals("obvious fail", flakyFailure.message());
            assertTrue(flakyFailure.stackTrace().contains("at io.olamy.FlakyTest.testApp(FlakyTest.java:27)"));
            assertEquals("this will fail maybe", flakyFailure.stdout().trim());
            assertEquals("this will maybe fail", flakyFailure.stderr().trim());
        }

        { // assert on rerun failures
            SuiteResult rerunSuite = testResult.getSuite("io.olamy.AlwaysFailTest");
            assertNotNull(rerunSuite);
            assertEquals(
                    3,
                    rerunSuite.getCase("io.olamy.AlwaysFailTest.testApp").getRerunFailures().length,
                    "Wrong number of rerun failures");

            RerunFailure rerunFailure =
                    rerunSuite.getCase("io.olamy.AlwaysFailTest.testApp").getRerunFailures()[0];
            assertNotNull(rerunFailure);
            assertEquals("junit.framework.AssertionFailedError", rerunFailure.type());
            assertEquals("built to fail", rerunFailure.message());
            assertTrue(
                    rerunFailure.stackTrace().contains("at io.olamy.AlwaysFailTest.testApp(AlwaysFailTest.java:23)"));
            assertEquals("this will fail for real", rerunFailure.stdout().trim());
            assertEquals("this will really fail", rerunFailure.stderr().trim());
        }
    }
}
