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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.steps.scm.GitStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class AbstractJUnitPipelineTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

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
