/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Tom Huybrechts, Yahoo!, Inc.
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
package hudson.tasks.test;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Cumulated result of multiple tests.
 *
 * <p>
 * On top of {@link TestResult}, this class introduces a tree structure
 * of {@link TestResult}s.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class TabulatedResult extends TestResult {

    /**
     * TODO: javadoc
     */
    protected transient Map<String,PipelineBlockWithTests> testsByBlock;

    /**
     * Gets the child test result objects.
     *
     * @return the child test result objects.
     * @see TestObject#getParent()
     */
    public abstract Collection<? extends TestResult> getChildren();

    public abstract boolean hasChildren();

    public boolean hasMultipleBlocks() {
        if (testsByBlock != null && testsByBlock.size() > 1) {
            // Check for nested runs.
            int nonNested = 0;
            for (PipelineBlockWithTests b : testsByBlock.values()) {
                if (!b.getLeafNodes().isEmpty()) {
                    nonNested++;
                }
            }
            return nonNested > 1;
        }

        return false;
    }

    @CheckForNull
    public PipelineBlockWithTests getPipelineBlockWithTests(@Nonnull String blockId) {
        if (testsByBlock.containsKey(blockId)) {
            return testsByBlock.get(blockId);
        }
        return null;
    }

    protected final void populateBlocks(@Nonnull List<String> innermostFirst, @Nonnull String nodeId,
                                        @CheckForNull PipelineBlockWithTests nested) {
        if (innermostFirst.isEmpty()) {
            if (nested != null) {
                addOrMergeBlock(nested);
            }
        } else {
            String innermost = innermostFirst.remove(0);
            if (nested == null) {
                nested = new PipelineBlockWithTests(innermost);
                nested.addLeafNode(nodeId);
                addOrMergeBlock(nested);
                populateBlocks(innermostFirst, nodeId, nested);
            } else {
                PipelineBlockWithTests nextLevel = new PipelineBlockWithTests(innermost);
                nextLevel.addChildBlock(nested);
                addOrMergeBlock(nextLevel);
                populateBlocks(innermostFirst, nodeId, nextLevel);
            }
        }
    }

    private void addOrMergeBlock(@Nonnull PipelineBlockWithTests b) {
        if (testsByBlock.containsKey(b.getBlockId())) {
            testsByBlock.get(b.getBlockId()).merge(b);
        } else {
            testsByBlock.put(b.getBlockId(), b);
        }
    }

    /**
     * Get an aggregated {@link TabulatedResult} for all test results in a {@link PipelineBlockWithTests} and any children it may have.
     *
     * Default implementation just returns the original.
     */
    @Nonnull
    public TabulatedResult blockToTestResult(@Nonnull PipelineBlockWithTests block, @Nonnull TabulatedResult fullResult) {
        return fullResult;
    }

    public String getChildTitle() {
        return "";
    }
}
