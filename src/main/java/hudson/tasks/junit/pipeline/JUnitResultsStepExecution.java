package hudson.tasks.junit.pipeline;


import com.google.common.base.Predicate;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.junit.TestResultSummary;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.Filterator;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

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

        TestResultAction testResultAction = JUnitResultArchiver.parseAndAttach(step, nodeId, getEnclosingStagesAndParallels(node),
                run, workspace, launcher, listener);

        if (testResultAction != null) {
            // TODO: Once JENKINS-43995 lands, update this to set the step status instead of the entire build.
            if (testResultAction.getResult().getFailCount() > 0) {
                getContext().setResult(Result.UNSTABLE);
            }
            return new TestResultSummary(testResultAction.getResult().getResultByRunAndNode(run.getExternalizableId(), nodeId));
        }

        return new TestResultSummary();
    }

    /**
     * Get the stage and parallel branch start node IDs (not the body nodes) for this node, innermost first.
     * @param node A flownode.
     * @return A nonnull, possibly empty list of stage/parallel branch start node IDs, innermost first.
     */
    @Nonnull
    public static List<String> getEnclosingStagesAndParallels(FlowNode node) {
        List<String> enclosingBlocks = new ArrayList<>();
        for (FlowNode enclosing : node.getEnclosingBlocks()) {
            if (enclosing != null && enclosing.getAction(LabelAction.class) != null) {
                if ((enclosing instanceof StepStartNode && ((StepStartNode) enclosing).getDescriptor() instanceof StageStep.DescriptorImpl) ||
                        (enclosing.getAction(ThreadNameAction.class) != null)) {
                    enclosingBlocks.add(enclosing.getId());
                }
            }
        }

        return enclosingBlocks;
    }

    private static final long serialVersionUID = 1L;
}