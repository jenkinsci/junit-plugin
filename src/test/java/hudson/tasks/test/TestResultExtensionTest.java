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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import java.util.concurrent.TimeUnit;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * A test case to make sure that the TestResult extension mechanism
 * is working properly.
 */
@WithJenkins
class TestResultExtensionTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testTrivialRecorder() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("trivialtest");
        TrivialTestResultRecorder recorder = new TrivialTestResultRecorder();
        project.getPublishersList().add(recorder);
        project.getBuildersList().add(new TouchBuilder());

        FreeStyleBuild build = project.scheduleBuild2(0).get(5, TimeUnit.MINUTES); /* leave room for debugging*/
        j.assertBuildStatus(Result.SUCCESS, build);
        TrivialTestResultAction action = build.getAction(TrivialTestResultAction.class);
        assertNotNull(action, "we should have an action");
        assertNotNull(action.run, "parent action should have an owner");
        Object resultObject = action.getResult();
        assertNotNull(resultObject, "we should have a result");
        assertInstanceOf(TestResult.class, resultObject, "result should be an TestResult");
        TestResult result = (TestResult) resultObject;
        Run<?, ?> ownerBuild = result.getRun();
        assertNotNull(ownerBuild, "we should have an owner");
        assertNotNull(result.getTestActions(), "we should have a list of test actions");

        // Validate that there are test results where I expect them to be:
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage projectPage = wc.getPage(project);
        j.assertGoodStatus(projectPage);
        HtmlPage testReportPage = wc.getPage(project, "/lastBuild/testReport/");
        j.assertGoodStatus(testReportPage);
    }
}
