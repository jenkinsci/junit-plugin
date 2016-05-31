/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hudson.tasks.junit.pipeline;

import hudson.model.Result;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.steps.scm.GitStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JUnitPipelineStepTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();
    @Rule public GitSampleRepoRule otherRepo = new GitSampleRepoRule();


    @Test
    public void testPassingBuild() throws Exception {
        prepRepoWithJenkinsfileAndZip("passingBuild", "JUnitResultArchiverTest.zip");
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

    @Test
    public void testFailingBuild() throws Exception {
        prepRepoWithJenkinsfileAndZip("failingBuild", "JUnitResultArchiverTest.zip");
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun b = getAndStartBuild();
                story.j.assertLogContains("ERROR: FAIL OUT",
                        story.j.assertBuildStatus(Result.FAILURE, story.j.waitForCompletion(b)));

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

    protected String pipelineSourceFromResources(String pipelineName) throws IOException {
        return fileContentsFromResources(pipelineName + ".groovy");
    }

    protected String fileContentsFromResources(String fileName) throws IOException {
        String fileContents = null;

        URL url = getClass().getResource("/hudson/tasks/junit/" + fileName);
        if (url != null) {
            fileContents = IOUtils.toString(url);
        }

        return fileContents;

    }

    protected void copyZipFileFromResources(String fileName) throws IOException {

        URL url = getClass().getResource("/hudson/tasks/junit/" + fileName);

        if (url != null) {
            File outFile = new File(sampleRepo.toString(), fileName);
            FileUtils.copyURLToFile(url, outFile);
        }
    }

    protected void prepRepoWithJenkinsfileAndZip(String pipelineName, String zipFile) throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile",
                pipelineSourceFromResources(pipelineName));
        sampleRepo.git("add", "Jenkinsfile");

        if (zipFile != null) {
            copyZipFileFromResources(zipFile);
            sampleRepo.git("add", zipFile);
        }

        sampleRepo.git("commit", "--message=files");
    }

    protected WorkflowRun getAndStartBuild() throws Exception {
        WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "Jenkinsfile"));
        return p.scheduleBuild2(0).waitForStart();
    }
}
