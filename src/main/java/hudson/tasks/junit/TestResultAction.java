/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Red Hat, Inc., Tom Huybrechts, Yahoo!, Inc.
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
package hudson.tasks.junit;

import com.thoughtworks.xstream.XStream;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.junit.storage.FileJunitTestResultStorage;
import io.jenkins.plugins.junit.storage.JunitTestResultStorage;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestObject;
import hudson.tasks.test.TestResultProjectAction;
import hudson.util.HeapSpaceStringConverter;
import hudson.util.XStream2;
import org.kohsuke.stapler.StaplerProxy;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import jenkins.tasks.SimpleBuildStep;

/**
 * {@link Action} that displays the JUnit test result.
 *
 * <p>
 * The actual test reports are isolated by {@link WeakReference}
 * so that it doesn't eat up too much memory.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressFBWarnings(value = "UG_SYNC_SET_UNSYNC_GET", justification = "False positive")
public class TestResultAction extends AbstractTestResultAction<TestResultAction> implements StaplerProxy, SimpleBuildStep.LastBuildAction {
    private transient WeakReference<TestResult> result;

    /** null only if there is a {@link JunitTestResultStorage} */
    private @Nullable Integer failCount;
    private @Nullable Integer skipCount;
    // Hudson < 1.25 didn't set these fields, so use Integer
    // so that we can distinguish between 0 tests vs not-computed-yet.
    private @Nullable Integer totalCount;
    private Double healthScaleFactor;
    private List<Data> testData = new ArrayList<>();

    @Deprecated
    public TestResultAction(AbstractBuild owner, TestResult result, BuildListener listener) {
        this((Run) owner, result, listener);
    }

    /**
     * @since 1.2-beta-1
     */
    public TestResultAction(Run owner, TestResult result, TaskListener listener) {
        super(owner);
        if (JunitTestResultStorage.find() instanceof FileJunitTestResultStorage) {
            setResult(result, listener);
        }
    }

    @Deprecated
    public TestResultAction(TestResult result, BuildListener listener) {
        this((Run) null, result, listener);
    }

    @SuppressWarnings("deprecation")
    @Override public Collection<? extends Action> getProjectActions() {
        Job<?,?> job = run.getParent();
        if (/* getAction(Class) produces a StackOverflowError */!Util.filter(job.getActions(), TestResultProjectAction.class).isEmpty()) {
            // JENKINS-26077: someone like XUnitPublisher already added one
            return Collections.emptySet();
        }
        return Collections.singleton(new TestResultProjectAction(job));
    }

    /**
     * Overwrites the {@link TestResult} by a new data set.
     * @since 1.2-beta-1
     */
    public synchronized void setResult(TestResult result, TaskListener listener) {
        assert JunitTestResultStorage.find() instanceof FileJunitTestResultStorage;
        result.freeze(this);

        totalCount = result.getTotalCount();
        failCount = result.getFailCount();
        skipCount = result.getSkipCount();

        if (run != null) {
        // persist the data
        try {
            getDataFile().write(result);
        } catch (IOException e) {
            e.printStackTrace(listener.fatalError("Failed to save the JUnit test result"));
        }
        }

        this.result = new WeakReference<TestResult>(result);
    }

    @Deprecated
    public void setResult(TestResult result, BuildListener listener) {
        setResult(result, (TaskListener) listener);
    }

    private XmlFile getDataFile() {
        return new XmlFile(XSTREAM, new File(run.getRootDir(), "junitResult.xml"));
    }

    @Override
    public synchronized TestResult getResult() {
        JunitTestResultStorage storage = JunitTestResultStorage.find();
        if (!(storage instanceof FileJunitTestResultStorage)) {
            return new TestResult(storage.load(run.getParent().getFullName(), run.getNumber()));
        }
        TestResult r;
        if(result==null) {
            r = load();
            result = new WeakReference<TestResult>(r);
        } else {
            r = result.get();
        }

        if(r==null) {
            r = load();
            result = new WeakReference<TestResult>(r);
        }
        if(totalCount==null) {
            totalCount = r.getTotalCount();
            failCount = r.getFailCount();
            skipCount = r.getSkipCount();
        }
        return r;
    }

    @Override
    public synchronized int getFailCount() {
        JunitTestResultStorage storage = JunitTestResultStorage.find();
        if (!(storage instanceof FileJunitTestResultStorage)) {
            return new TestResult(storage.load(run.getParent().getFullName(), run.getNumber())).getFailCount();
        }
        if(totalCount==null)
            getResult();    // this will compute the result
        return failCount;
    }

    @Override
    public synchronized int getSkipCount() {
        JunitTestResultStorage storage = JunitTestResultStorage.find();
        if (!(storage instanceof FileJunitTestResultStorage)) {
            return new TestResult(storage.load(run.getParent().getFullName(), run.getNumber())).getSkipCount();
        }
        if(totalCount==null)
            getResult();    // this will compute the result
        return skipCount;
    }

    @Override
    public synchronized int getTotalCount() {
        JunitTestResultStorage storage = JunitTestResultStorage.find();
        if (!(storage instanceof FileJunitTestResultStorage)) {
            return new TestResult(storage.load(run.getParent().getFullName(), run.getNumber())).getTotalCount();
        }
        if(totalCount==null)
            getResult();    // this will compute the result
        return totalCount;
    }

    @Override
    public double getHealthScaleFactor() {
        return healthScaleFactor == null ? 1.0 : healthScaleFactor;
    }

    public void setHealthScaleFactor(double healthScaleFactor) {
        this.healthScaleFactor = Math.max(0.0,healthScaleFactor);
    }

    @Override
     public List<CaseResult> getFailedTests() {
        TestResult result = getResult();
        return result.getFailedTests();
     }

    @Override
    public List<CaseResult> getPassedTests() {
        return getResult().getPassedTests();
    }

    @Override
    public List<CaseResult> getSkippedTests() {
        return getResult().getSkippedTests();
    }


    /**
     * Loads a {@link TestResult} from disk.
     */
    private TestResult load() {
        TestResult r;
        try {
            r = (TestResult)getDataFile().read();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load "+getDataFile(),e);
            r = new TestResult();   // return a dummy
        }
        r.freeze(this);
        return r;
    }

    public Object getTarget() {
        return getResult();
    }

    public List<TestAction> getActions(TestObject object) {
        List<TestAction> result = new ArrayList<TestAction>();
        // Added check for null testData to avoid NPE from issue 4257.
        if (testData != null) {
            synchronized (testData) {
                for (Data data : testData)
                    for (TestAction ta : data.getTestAction(object))
                        if (ta != null)
                            result.add(ta);
            }
        }
        return Collections.unmodifiableList(result);
    }

    List<Data> getData() {
        return testData;
    }

    /**
     * Replaces to collection of test data associated with this action.
     *
     * <p>
     * This method will not automatically persist the data at the time of addition.
     *
     */
    public void setData(List<Data> testData) {
	      this.testData = testData;
    }

    /**
     * Adds a {@link Data} to the test data associated with this action.
     *
     * <p>
     * This method will not automatically persist the data at the time of addition.
     *
     * @since 1.21
     */
    public void addData(Data data) {
        synchronized (testData) {
            this.testData.add(data);
        }
    }

    /**
     * Merges an additional test result into this one.
     */
    public void mergeResult(TestResult additionalResult, TaskListener listener) {
        TestResult original = getResult();
        original.merge(additionalResult);
        setResult(original, listener);
    }

    /**
     * Resolves {@link TestAction}s for the given {@link TestObject}.
     *
     * <p>
     * This object itself is persisted as a part of {@link Run}, so it needs to be XStream-serializable.
     *
     * @see TestDataPublisher
     */
    public static abstract class Data {
    	/**
    	 * Returns all TestActions for the testObject.
         *
         * @return
         *      Can be empty but never null. The caller must assume that the returned list is read-only.
    	 */
    	public abstract List<? extends TestAction> getTestAction(hudson.tasks.junit.TestObject testObject);
    }

    public Object readResolve() {
        super.readResolve(); // let it do the post-deserialization work
    	if (testData == null) {
    		testData = new ArrayList<Data>(0);
    	}

    	return this;
    }

    private static final Logger logger = Logger.getLogger(TestResultAction.class.getName());

    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("result",TestResult.class);
        XSTREAM.alias("suite",SuiteResult.class);
        XSTREAM.alias("case",CaseResult.class);
        XSTREAM.registerConverter(new HeapSpaceStringConverter(),100);
    }

}
