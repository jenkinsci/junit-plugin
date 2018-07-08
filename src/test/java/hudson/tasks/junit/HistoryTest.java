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

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.Result;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.List;

import static org.junit.Assert.*;

public class HistoryTest {
    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    private FreeStyleProject project;

    private static final String PROJECT_NAME = "wonky";

    @Before
    public void setUp() throws Exception {
        List<FreeStyleProject> projects = rule.jenkins.getAllItems(FreeStyleProject.class);
        Project theProject = null;
        for (Project p : projects) {
            if (p.getName().equals(PROJECT_NAME)) theProject = p;
        }
        assertNotNull("We should have a project named " + PROJECT_NAME, theProject);
        project = (FreeStyleProject) theProject;
    }


    @LocalData
    @Test
    public void testFailedSince() throws Exception {
        assertNotNull("project should exist", project);

        // Check the status of a few builds
        FreeStyleBuild build4 = project.getBuildByNumber(4);
        assertNotNull("build4", build4);
        rule.assertBuildStatus(Result.FAILURE, build4);

        FreeStyleBuild build7 = project.getBuildByNumber(7);
        assertNotNull("build7", build7);
        rule.assertBuildStatus(Result.SUCCESS, build7);

        TestResult tr = build4.getAction(TestResultAction.class).getResult();
        assertEquals(2,tr.getFailedTests().size());

        // In build 4, we expect these tests to have failed since these builds
        // org.jvnet.hudson.examples.small.deep.DeepTest.testScubaGear failed since 3
        // org.jvnet.hudson.examples.small.MiscTest.testEleanor failed since 3

        PackageResult deepPackage = tr.byPackage("org.jvnet.hudson.examples.small.deep");
        assertNotNull("deepPackage", deepPackage);
        assertTrue("package is failed", !deepPackage.isPassed());
        ClassResult deepClass = deepPackage.getClassResult("DeepTest");
        assertNotNull(deepClass);
        assertTrue("class is failed", !deepClass.isPassed());
        CaseResult scubaCase = deepClass.getCaseResult("testScubaGear");
        assertNotNull(scubaCase);
        assertTrue("scubaCase case is failed", !scubaCase.isPassed());
        int scubaFailedSince = scubaCase.getFailedSince();
        assertEquals("scubaCase should have failed since build 3", 3, scubaFailedSince);


        // In build 5 the scuba test begins to pass
        TestResult tr5 = project.getBuildByNumber(5).getAction(TestResultAction.class).getResult();
        assertEquals(1,tr5.getFailedTests().size());
        deepPackage = tr5.byPackage("org.jvnet.hudson.examples.small.deep");
        assertNotNull("deepPackage", deepPackage);
        assertTrue("package is passed", deepPackage.isPassed());
        deepClass = deepPackage.getClassResult("DeepTest");
        assertNotNull(deepClass);
        assertTrue("class is passed", deepClass.isPassed());
        scubaCase = deepClass.getCaseResult("testScubaGear");
        assertNotNull(scubaCase);
        assertTrue("scubaCase case is passed", scubaCase.isPassed());

        // In build5, testEleanor has been failing since build 3
        PackageResult smallPackage = tr5.byPackage("org.jvnet.hudson.examples.small");
        ClassResult miscClass = smallPackage.getClassResult("MiscTest");
        CaseResult eleanorCase = miscClass.getCaseResult("testEleanor");
        assertNotNull(eleanorCase);
        assertTrue("eleanor failed", !eleanorCase.isPassed());
        assertEquals("eleanor has failed since build 3", 3, eleanorCase.getFailedSince());
    }

    @LocalData
    @Test
    public void testSkippedSince() throws Exception {
        assertNotNull("project should exist", project);

        // Check the status of a few builds
        FreeStyleBuild build8 = project.getBuildByNumber(8);
        assertNotNull("build8", build8);
        rule.assertBuildStatus(Result.SUCCESS, build8);

        FreeStyleBuild build10 = project.getBuildByNumber(10);
        assertNotNull("build10", build10);
        rule.assertBuildStatus(Result.FAILURE, build10);

        TestResult tr8 = build8.getAction(TestResultAction.class).getResult();
        assertEquals(1,tr8.getSkippedTests().size());

        // In build 8 and 9, we expect these tests to have skippedSince:
        // org.jvnet.hudson.examples.small.deep.DeepTest.testScubaOnlyInRedSea skipped since 8

        PackageResult deepPackage = tr8.byPackage("org.jvnet.hudson.examples.small.deep");
        assertNotNull("deepPackage", deepPackage);
        assertTrue("package is failed", !deepPackage.isPassed());
        ClassResult deepClass = deepPackage.getClassResult("DeepTest");
        assertNotNull(deepClass);
        assertTrue("class is failed", !deepClass.isPassed());
        CaseResult testScubaOnlyInRedSeaCase = deepClass.getCaseResult("testScubaOnlyInRedSea");
        assertNotNull(testScubaOnlyInRedSeaCase);
        assertTrue("scubaCase case is not skipped", testScubaOnlyInRedSeaCase.isSkipped());
        int scubaSkippedSince = testScubaOnlyInRedSeaCase.getSkippedSince();
        assertEquals("scubaCase should have skipped since build 8", 8, scubaSkippedSince);

        // In build 9 the scuba in red sea test is still skipped
        TestResult tr9 = project.getBuildByNumber(9).getAction(TestResultAction.class).getResult();
        assertEquals(1,tr9.getSkippedTests().size());
        deepPackage = tr9.byPackage("org.jvnet.hudson.examples.small.deep");
        assertNotNull("deepPackage", deepPackage);
        assertTrue("package is failed", !deepPackage.isPassed());
        deepClass = deepPackage.getClassResult("DeepTest");
        assertNotNull(deepClass);
        assertTrue("class is failed", !deepClass.isPassed());
        testScubaOnlyInRedSeaCase = deepClass.getCaseResult("testScubaOnlyInRedSea");
        assertNotNull(testScubaOnlyInRedSeaCase);
        assertTrue("testScubaOnlyInRedSea case is skipped", testScubaOnlyInRedSeaCase.isSkipped());
        scubaSkippedSince = testScubaOnlyInRedSeaCase.getSkippedSince();
        assertEquals("scubatestScubaOnlyInRedSeaCase should have skipped since build 8", 8, scubaSkippedSince);

        // In build 10 the scuba in red sea test begins to fail
        TestResult tr10 = project.getBuildByNumber(10).getAction(TestResultAction.class).getResult();
        assertEquals(1,tr10.getFailedTests().size());
        deepPackage = tr10.byPackage("org.jvnet.hudson.examples.small.deep");
        assertNotNull("deepPackage", deepPackage);
        assertTrue("package is failed", !deepPackage.isPassed());
        deepClass = deepPackage.getClassResult("DeepTest");
        assertNotNull(deepClass);
        assertTrue("class is failed", !deepClass.isPassed());
        testScubaOnlyInRedSeaCase = deepClass.getCaseResult("testScubaOnlyInRedSea");
        assertNotNull(testScubaOnlyInRedSeaCase);
        assertTrue("testScubaOnlyInRedSea case is failed", testScubaOnlyInRedSeaCase.isFailed());
        scubaSkippedSince = testScubaOnlyInRedSeaCase.getSkippedSince();
        assertEquals("scubatestScubaOnlyInRedSeaCase should have skipped since build 0", 0, scubaSkippedSince);

        // In build11, testScubaOnlyInRedSea is skipped again - skippedSince becomes 11
        TestResult tr11 = project.getBuildByNumber(11).getAction(TestResultAction.class).getResult();
        assertEquals(0,tr11.getFailedTests().size());
        assertEquals(1,tr11.getSkippedTests().size());
        deepPackage = tr11.byPackage("org.jvnet.hudson.examples.small.deep");
        assertNotNull("deepPackage", deepPackage);
        assertTrue("package is failed", !deepPackage.isPassed());
        deepClass = deepPackage.getClassResult("DeepTest");
        assertNotNull(deepClass);
        assertTrue("class is failed", !deepClass.isPassed());
        testScubaOnlyInRedSeaCase = deepClass.getCaseResult("testScubaOnlyInRedSea");
        assertNotNull(testScubaOnlyInRedSeaCase);
        assertTrue("testScubaOnlyInRedSea case is skipped", testScubaOnlyInRedSeaCase.isSkipped());
        scubaSkippedSince = testScubaOnlyInRedSeaCase.getSkippedSince();
        assertEquals("scubatestScubaOnlyInRedSeaCase should have skipped since build 11", 11, scubaSkippedSince);
    }
}
