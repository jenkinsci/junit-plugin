package hudson.tasks.test;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class PipelineBlockWithTests implements Serializable {
    private final String blockId;
    private final Map<String,PipelineBlockWithTests> childBlocks = new TreeMap<>();
    private final Set<String> leafNodes = new TreeSet<>();

    public PipelineBlockWithTests(@Nonnull String blockId) {
        this.blockId = blockId;
    }

    @Nonnull
    public String getBlockId() {
        return blockId;
    }

    @Nonnull
    public Map<String,PipelineBlockWithTests> getChildBlocks() {
        return childBlocks;
    }

    @Nonnull
    public Set<String> getLeafNodes() {
        return leafNodes;
    }

    public void addChildBlock(@Nonnull PipelineBlockWithTests child) {
        childBlocks.put(child.getBlockId(), child);
    }

    public void addLeafNode(@Nonnull String leafNode)  {
        leafNodes.add(leafNode);
    }

    public void merge(@Nonnull PipelineBlockWithTests toMerge) {
        if (toMerge.getBlockId().equals(blockId)) {
            if (!this.equals(toMerge)) {
                for (String childId : toMerge.getChildBlocks().keySet()) {
                    if (!childBlocks.containsKey(childId)) {
                        childBlocks.put(childId, toMerge.getChildBlocks().get(childId));
                    } else {
                        childBlocks.get(childId).merge(toMerge.getChildBlocks().get(childId));
                    }
                }
                leafNodes.addAll(toMerge.getLeafNodes());
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        PipelineBlockWithTests that = (PipelineBlockWithTests) o;

        return that.getBlockId().equals(getBlockId()) &&
                that.getChildBlocks().equals(getChildBlocks()) &&
                that.getLeafNodes().equals(getLeafNodes());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getBlockId().hashCode();
        result = 31 * result + getChildBlocks().hashCode();
        result = 31 * result + getLeafNodes().hashCode();

        return result;
    }

    @Nonnull
    public Set<String> nodesWithTests() {
        Set<String> nodes = new TreeSet<>();

        nodes.addAll(leafNodes);
        for (PipelineBlockWithTests child : childBlocks.values()) {
            nodes.addAll(child.nodesWithTests());
        }

        return nodes;
    }

    private static final long serialVersionUID = 1L;
}
