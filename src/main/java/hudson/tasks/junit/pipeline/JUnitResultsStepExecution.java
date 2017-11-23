package hudson.tasks.junit.pipeline;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.junit.TestResultSummary;
import hudson.tasks.test.PipelineTestDetails;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

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

        List<FlowNode> enclosingBlocks = getEnclosingStagesAndParallels(node);

        PipelineTestDetails pipelineTestDetails = new PipelineTestDetails();
        pipelineTestDetails.setNodeId(nodeId);
        pipelineTestDetails.setEnclosingBlocks(getEnclosingBlockIds(enclosingBlocks));
        pipelineTestDetails.setEnclosingBlockNames(getEnclosingBlockNames(enclosingBlocks));
        TestResultAction testResultAction = JUnitResultArchiver.parseAndAttach(step, pipelineTestDetails, run, workspace, launcher, listener);

        if (testResultAction != null) {
            // TODO: Once JENKINS-43995 lands, update this to set the step status instead of the entire build.
            if (testResultAction.getResult().getFailCount() > 0) {
                run.setResult(Result.UNSTABLE);
            }
            return new TestResultSummary(testResultAction.getResult().getResultByNode(nodeId));
        }

        return new TestResultSummary();
    }

    /**
     * Get the stage and parallel branch start node IDs (not the body nodes) for this node, innermost first.
     * @param node A flownode.
     * @return A nonnull, possibly empty list of stage/parallel branch start nodes, innermost first.
     */
    @Nonnull
    public static List<FlowNode> getEnclosingStagesAndParallels(FlowNode node) {
        List<FlowNode> enclosingBlocks = new ArrayList<>();
        for (FlowNode enclosing : node.getEnclosingBlocks()) {
            if (enclosing != null && enclosing.getAction(LabelAction.class) != null) {
                if (isStageNode(enclosing) ||
                        (enclosing.getAction(ThreadNameAction.class) != null)) {
                    enclosingBlocks.add(enclosing);
                }
            }
        }

        return enclosingBlocks;
    }

    private static boolean isStageNode(@Nonnull FlowNode node) {
        if (node instanceof StepNode) {
            StepDescriptor d = ((StepNode) node).getDescriptor();
            return d != null && d.getFunctionName().equals("stage");
        } else {
            return false;
        }
    }

    @Nonnull
    public static List<String> getEnclosingBlockIds(@Nonnull List<FlowNode> nodes) {
        List<String> ids = new ArrayList<>();
        for (FlowNode n : nodes) {
            ids.add(n.getId());
        }
        return ids;
    }

    @Nonnull
    public static List<String> getEnclosingBlockNames(@Nonnull List<FlowNode> nodes) {
        List<String> names = new ArrayList<>();
        for (FlowNode n : nodes) {
            ThreadNameAction threadNameAction = n.getAction(ThreadNameAction.class);
            LabelAction labelAction = n.getAction(LabelAction.class);
            if (threadNameAction != null) {
                names.add(threadNameAction.getThreadName());
            } else if (labelAction != null) {
                names.add(labelAction.getDisplayName());
            }
        }
        return names;
    }

    private static final long serialVersionUID = 1L;
}