package hudson.tasks.junit.pipeline;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TestResultSummary;
import hudson.tasks.test.PipelineTestDetails;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import static java.util.Objects.requireNonNull;

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
        Run<?,?> run = requireNonNull(getContext().get(Run.class));
        TaskListener listener = getContext().get(TaskListener.class);
        Launcher launcher = getContext().get(Launcher.class);
        FlowNode node = getContext().get(FlowNode.class);

        String nodeId = node.getId();

        List<FlowNode> enclosingBlocks = getEnclosingStagesAndParallels(node);

        PipelineTestDetails pipelineTestDetails = new PipelineTestDetails();
        pipelineTestDetails.setNodeId(nodeId);
        pipelineTestDetails.setEnclosingBlocks(getEnclosingBlockIds(enclosingBlocks));
        pipelineTestDetails.setEnclosingBlockNames(getEnclosingBlockNames(enclosingBlocks));
        try {
            TestResultSummary summary = JUnitResultArchiver.parseAndSummarize(step, pipelineTestDetails, run, workspace, launcher, listener);

            if (summary.getFailCount() > 0) {
                int testFailures = summary.getFailCount();
                if (testFailures > 0) {
                    node.addOrReplaceAction(new WarningAction(Result.UNSTABLE).withMessage(testFailures + " tests failed"));
                    run.setResult(Result.UNSTABLE);
                }
            }
            return summary;
        } catch (Exception e) {
            assert listener != null;
            listener.getLogger().println(e.getMessage());
            throw e;
        }
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
            ThreadNameAction threadNameAction = n.getPersistentAction(ThreadNameAction.class);
            LabelAction labelAction = n.getPersistentAction(LabelAction.class);
            if (threadNameAction != null) {
                // If we're on a parallel branch with the same name as the previous (inner) node, that generally
                // means we're in a Declarative parallel stages situation, so don't add the redundant branch name.
                if (names.isEmpty() || !threadNameAction.getThreadName().equals(names.get(names.size()-1))) {
                    names.add(threadNameAction.getThreadName());
                }
            } else if (labelAction != null) {
                names.add(labelAction.getDisplayName());
            }
        }
        return names;
    }

    private static final long serialVersionUID = 1L;
}
