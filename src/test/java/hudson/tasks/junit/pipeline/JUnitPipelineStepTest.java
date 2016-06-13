/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package hudson.tasks.junit.pipeline;

import hudson.model.Result;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import org.junit.runners.model.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JUnitPipelineStepTest extends AbstractJUnitPipelineTest {
    @Test
    public void testPassingBuild() throws Exception {
        prepRepoWithJenkinsfileAndZip("pipelineStepPassingBuild", "JUnitResultArchiverTest.zip");
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun b = getAndStartBuild();
                story.j.assertLogContains("hello",
                        story.j.assertBuildStatus(Result.UNSTABLE, story.j.waitForCompletion(b)));

                assertTestResults(b);
            }
        });
    }

    private void assertTestResults(WorkflowRun b) {
        TestResultAction testResultAction = b.getAction(TestResultAction.class);
        assertNotNull("no TestResultAction", testResultAction);

        TestResult result = testResultAction.getResult();
        assertNotNull("no TestResult", result);

        assertEquals("should have 1 failing test", 1, testResultAction.getFailCount());
        assertEquals("should have 1 failing test", 1, result.getFailCount());

        assertEquals("should have 132 total tests", 132, testResultAction.getTotalCount());
        assertEquals("should have 132 total tests", 132, result.getTotalCount());

    }


}
