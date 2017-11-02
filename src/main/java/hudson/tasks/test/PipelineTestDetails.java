package hudson.tasks.test;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder class for recording additional Pipeline-related arguments needed for test parsing and test results.
 */
public class PipelineTestDetails implements Serializable {
    private String nodeId;
    private List<String> enclosingBlocks = new ArrayList<>();
    private List<String> enclosingBlockNames = new ArrayList<>();

    @CheckForNull
    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(@Nonnull String nodeId) {
        this.nodeId = nodeId;
    }

    @Nonnull
    public List<String> getEnclosingBlocks() {
        return enclosingBlocks;
    }

    public void setEnclosingBlocks(@Nonnull List<String> enclosingBlocks) {
        this.enclosingBlocks.addAll(enclosingBlocks);
    }

    @Nonnull
    public List<String> getEnclosingBlockNames() {
        return enclosingBlockNames;
    }

    public void setEnclosingBlockNames(@Nonnull List<String> enclosingBlockNames) {
        this.enclosingBlockNames.addAll(enclosingBlockNames);
    }

    private static final long serialVersionUID = 1L;
}
