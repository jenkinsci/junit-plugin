package hudson.tasks.junit.pipeline;

import hudson.model.Result;
import hudson.tasks.junit.TestResultAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertNull;

public class JUnitResultsStepTest {
    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    @ClassRule
    public final static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void emptyFails() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "emptyFails");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    sh 'echo hi'\n" +
                "    junitResults('*.xml')\n" +
                "  }\n" +
                "}\n", true));

        WorkflowRun r = j.scheduleBuild2(0).waitForStart();
        rule.assertBuildStatus(Result.FAILURE, rule.waitForCompletion(r));
        rule.assertLogContains("ERROR: No test report files were found. Configuration error?", r);
    }

    @Test
    public void allowEmpty() throws Exception {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "allowEmpty");
        j.setDefinition(new CpsFlowDefinition("stage('first') {\n" +
                "  node {\n" +
                "    sh 'echo hi'\n" +
                "    junitResults(testResults: '*.xml', allowEmptyResults: true)\n" +
                "  }\n" +
                "}\n", true));

        WorkflowRun r = rule.buildAndAssertSuccess(j);
        assertNull(r.getAction(TestResultAction.class));
        rule.assertLogContains("None of the test reports contained any result", r);
    }
}
