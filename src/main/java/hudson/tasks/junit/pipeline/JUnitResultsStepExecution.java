package hudson.tasks.junit.pipeline;


import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.junit.TestResultSummary;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import javax.annotation.Nonnull;

public class JUnitResultsStepExecution extends SynchronousNonBlockingStepExecution<TestResultSummary> {

    private transient final JUnitResultsStep step;

    public JUnitResultsStepExecution(@Nonnull JUnitResultsStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    protected TestResultSummary run() throws Exception {
        FilePath workspace = getContext().get(FilePath.class);
        workspace.mkdirs();
        Run<?,?> run = getContext().get(Run.class);
        TaskListener listener = getContext().get(TaskListener.class);
        Launcher launcher = getContext().get(Launcher.class);
        FlowNode node = getContext().get(FlowNode.class);

        String nodeId = node.getId();

        TestResultAction testResultAction = JUnitResultArchiver.parseAndAttach(step, nodeId, run, workspace, launcher, listener);

        if (testResultAction != null) {
            // TODO: Once JENKINS-43995 lands, update this to set the step status instead of the entire build.
            if (testResultAction.getResult().getFailCount() > 0) {
                getContext().setResult(Result.UNSTABLE);
            }
            return new TestResultSummary(testResultAction.getResult().getResultByRunAndNode(run.getExternalizableId(), nodeId));
        }

        return new TestResultSummary();
    }

    private static final long serialVersionUID = 1L;
}