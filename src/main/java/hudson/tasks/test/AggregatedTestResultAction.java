/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Red Hat, Inc., Yahoo!, Inc.
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

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AbstractTestResultAction} that aggregates all the test results
 * from the corresponding {@link Run}s.
 *
 * <p>
 * (This has nothing to do with {@link AggregatedTestResultPublisher}, unfortunately)
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class AggregatedTestResultAction extends AbstractTestResultAction {
    private int failCount,skipCount,totalCount;

    public static final class Child {
        /**
         * Name of the module. Could be relative to something.
         * The interpretation of this is done by
         * {@link AggregatedTestResultAction#getChildName(AbstractTestResultAction)} and
         * {@link AggregatedTestResultAction#resolveChild(Child)} and
         */
        public final String name;
        public final int build;

        public Child(String name, int build) {
            this.name = name;
            this.build = build;
        }
    }

    /**
     * child builds whose test results are used for aggregation.
     */
    public final List<Child> children = new ArrayList<Child>();

    @Deprecated
    public AggregatedTestResultAction(AbstractBuild owner) {
        super(owner);
    }

    /** @since 1.545 */
    public AggregatedTestResultAction() {}

    protected void update(List<? extends AbstractTestResultAction> children) {
        failCount = skipCount = totalCount = 0;
        this.children.clear();
        for (AbstractTestResultAction tr : children)
            add(tr);
    }

    protected void add(AbstractTestResultAction child) {
        failCount += child.getFailCount();
        skipCount += child.getSkipCount();
        totalCount += child.getTotalCount();
        this.children.add(new Child(getChildName(child),child.run.number));
    }

    public int getFailCount() {
        return failCount;
    }

    @Override
    public int getSkipCount() {
        return skipCount;
    }

    public int getTotalCount() {
        return totalCount;
    }
   
    public List<ChildReport> getResult() {
        // I think this is a reasonable default.
        return getChildReports();
    }

    @Override
    public List<? extends TestResult> getFailedTests() {
        List<TestResult> failedTests = new ArrayList<TestResult>(failCount);
        for (ChildReport childReport : getChildReports()) {
            if (childReport.result instanceof TestResult) {
                failedTests.addAll(((TestResult) childReport.result).getFailedTests());
            }
        }
        return failedTests;
    }

    /**
     * Data-binding bean for the remote API.
     */
    @ExportedBean(defaultVisibility=2)
    public static final class ChildReport {
        @Deprecated
        public final AbstractBuild<?,?> child;
        /**
         * @since 1.2-beta-1
         */
        @Exported(name="child")
        public final Run<?,?> run;
        @Exported
        public final Object result;

        @Deprecated
        public ChildReport(AbstractBuild<?, ?> child, AbstractTestResultAction result) {
            this((Run) child, result);
        }

        /**
         * @since 1.2-beta-1
         */
        public ChildReport(Run<?,?> run, AbstractTestResultAction result) {
            this.child = run instanceof AbstractBuild ? (AbstractBuild) run : null;
            this.run = run;
            this.result = result!=null ? result.getResult() : null;
        }
    }

    /**
     * Mainly for the remote API. Expose results from children.
     */
    @Exported(inline=true)
    public List<ChildReport> getChildReports() {
        return new AbstractList<ChildReport>() {
            public ChildReport get(int index) {
                return new ChildReport(
                        resolveRun(children.get(index)),
                        getChildReport(children.get(index)));
            }

            public int size() {
                return children.size();
            }
        };
    }

    protected abstract String getChildName(AbstractTestResultAction tr);

    /**
     * @since 1.2-beta-1
     */
    public Run<?,?> resolveRun(Child child) {
        return resolveChild(child);
    }

    @Deprecated
    public AbstractBuild<?,?> resolveChild(Child child) {
        if (Util.isOverridden(AggregatedTestResultAction.class, getClass(), "resolveRun", Child.class)) {
            Run<?,?> r = resolveRun(child);
            return r instanceof AbstractBuild ? (AbstractBuild) r : null;
        } else {
            throw new AbstractMethodError("you must override resolveRun");
        }
    }

    /**
     * Uses {@link #resolveChild(Child)} and obtain the
     * {@link AbstractTestResultAction} object for the given child.
     */
    protected AbstractTestResultAction getChildReport(Child child) {
        Run<?,?> b = resolveRun(child);
        if(b==null) return null;
        return b.getAction(AbstractTestResultAction.class);
    }

    /**
     * Since there's no TestObject that points this action as the owner
     * (aggregated {@link TestObject}s point to their respective real owners, not 'this'),
     * so this method should be never invoked.
     *
     * @deprecated
     *      so that IDE warns you if you accidentally try to call it.
     */
    @Override
    protected final String getDescription(TestObject object) {
        throw new AssertionError();
    }

    /**
     * See {@link #getDescription(TestObject)}
     *
     * @deprecated
     *      so that IDE warns you if you accidentally try to call it.
     */
    @Override
    protected final void setDescription(TestObject object, String description) {
        throw new AssertionError();
    }
}
