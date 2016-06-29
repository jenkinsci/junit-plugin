/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts, Yahoo! Inc., InfraDNA, Inc.
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

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractModelObject;
import hudson.model.Api;
import hudson.model.Run;
import hudson.tasks.test.AbstractTestResultAction;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;
import java.util.List;

/**
 * Stub of base class for all test result objects. The real implementation of
 * the TestObject is in hudson.tasks.test.TestObject. This class simply
 * defines abstract methods so that legacy code will continue to compile.
 *
 * @deprecated
 *      Use {@link hudson.tasks.test.TestObject} instead.
 * 
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class TestObject extends AbstractModelObject implements Serializable {

    @Deprecated
    public AbstractBuild<?,?> getOwner() {
        if (Util.isOverridden(TestObject.class, getClass(), "getRun")) {
            Run<?,?> r = getRun();
            return r instanceof AbstractBuild ? (AbstractBuild) r : null;
        } else {
            throw new AbstractMethodError("you must override getRun");
        }
    }

    /**
     * @return the run in which this test was executed.
     * @since 1.2-beta-1
     */
    public Run<?,?> getRun() {
        return getOwner();
    }
   
    public abstract TestObject getParent();

	public abstract String getId();

    /**
     * Returns the URL of this {@link TestObject}, relative to the context root.
     *
     * @return
     *      String like "job/foo/32/testReport/junit/com.company/Class" with no trailing or leading slash.
     */
	public abstract String getUrl(); 

	public abstract TestResult getTestResult();

    public  abstract AbstractTestResultAction getTestResultAction();

    public  abstract  List<TestAction> getTestActions();

    public abstract <T> T getTestAction(Class<T> klazz);

    /**
	 * Gets the counter part of this {@link TestObject} in the previous run.
	 * 
	 * @return null if no such counter part exists.
	 */
	public abstract TestObject getPreviousResult();

    @Deprecated
	public TestObject getResultInBuild(AbstractBuild<?,?> build) {
        if (Util.isOverridden(TestObject.class, getClass(), "getResultInRun", Run.class)) {
            return getResultInRun(build);
        } else {
            throw new AbstractMethodError("you must override getResultInRun");
        }
    }

    /**
     * @param run The run for which the run is requested.
     *
     * @return the test result for the provided run.
     * @since 1.2-beta-1
     */
	public TestObject getResultInRun(Run<?,?> run) {
        if (run instanceof AbstractBuild) {
            return getResultInBuild((AbstractBuild) run);
        } else {
            throw new AbstractMethodError("you must override getResultInRun");
        }
    }

	/**
	 * Time took to run this test. In seconds.
     *
     * @return the time in seconds the test ran.
	 */
	public abstract float getDuration();

	/**
	 * Returns the string representation of the {@link #getDuration()}, in a
	 * human readable format.
     *
     * @return a string representation of {@link #getDuration()}.
	 */
	public abstract String getDurationString();

    public abstract String getDescription();

    public abstract void setDescription(String description);

    /**
	 * Exposes this object through the remote API.
     *
     * @return the api for this test object.
	 */
	public abstract Api getApi();

    /**
	 * Gets the name of this object.
     *
     * @return the name of this object.
	 */
	public abstract String getName();

    /**
	 * Gets the version of {@link #getName()} that's URL-safe.
     *
     * @return the URL-safe name of this object.
	 */
	public abstract String getSafeName();

    public abstract String getSearchUrl();

    /**
     * Gets the total number of passed tests.
     *
     * @return the total number of passed tests.
     */
    public abstract int getPassCount();

    /**
     * Gets the total number of failed tests.
     *
     * @return the total number of failed tests.
     */
    public abstract int getFailCount();

    /**
     * Gets the total number of skipped tests.
     *
     * @return the total number of skipped tests.
     */
    public abstract int getSkipCount();

    /**
     * Gets the total number of tests.
     *
     * @return the total number of tests.
     */
    public abstract int getTotalCount();

    public abstract History getHistory();

//    public abstract Object getDynamic(String token, StaplerRequest req,
//			StaplerResponse rsp);
//
//    public abstract  HttpResponse doSubmitDescription(
//			@QueryParameter String description) throws IOException,
//			ServletException;

}
