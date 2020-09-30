/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Tom Huybrechts, Yahoo!, Inc.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import com.google.common.collect.MapMaker;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Functions;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Item;
import hudson.model.Run;
import hudson.tasks.junit.History;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.TestResultAction;
import jenkins.model.Jenkins;

/**
 * Base class for all test result objects.
 * For compatibility with code that expects this class to be in hudson.tasks.junit,
 * we've created a pure-abstract class, hudson.tasks.junit.TestObject. That
 * stub class is deprecated; instead, people should use this class.  
 * 
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
@SuppressFBWarnings(value = "NM_SAME_SIMPLE_NAME_AS_SUPERCLASS", justification = "Accepted")
public abstract class TestObject extends hudson.tasks.junit.TestObject {

    private static final Logger LOGGER = Logger.getLogger(TestObject.class.getName());
    private volatile transient String id;

    /**
     * Reverse pointer of {@link TabulatedResult#getChildren()}.
     *
     * @return the parent {@link TestObject}.
     */
    public abstract TestObject getParent();

    @Override
    public final String getId() {
        if (id == null) {
            StringBuilder buf = new StringBuilder();
            buf.append(getSafeName());

            TestObject parent = getParent();
            if (parent != null) {
                String parentId = parent.getId();
                if ((parentId != null) && (parentId.length() > 0)) {
                    buf.insert(0, '/');
                    buf.insert(0, parent.getId());
                }
            }
            id = buf.toString();
        }
        return id;
    }

    @Override
    public String getUrl() {
        return getRun().getUrl() + getTestResultAction().getUrlName() + "/" + getId();
    }

    public String getFullDisplayName() {
        return getDisplayName();
    }

    /**
     * Returns the top level test result data.
     *
     * @deprecated This method returns a JUnit specific class. Use
     * {@link #getTopLevelTestResult()} instead for a more general interface.
     */
    @Override
    public hudson.tasks.junit.TestResult getTestResult() {
        TestObject parent = getParent();

        return parent == null ? null : getParent().getTestResult();
    }

    /**
     * Returns the top level test result data.
     *
     * @return the top level test result data.
     */    
    public TestResult getTopLevelTestResult() {
        TestObject parent = getParent();

        return parent == null ? null : getParent().getTopLevelTestResult();
    }
    
    /**
     * Computes the relative path to get to this test object from <code>it</code>. If
     * <code>it</code> does not appear in the parent chain for this object, a
     * relative path from the server root will be returned.
     * @param it Target test object.
     *
     * @return A relative path to this object, potentially from the top of the
     * Hudson object model
     */
    public String getRelativePathFrom(TestObject it) {


        // if (it is one of my ancestors) {
        //    return a relative path from it
        // } else {
        //    return a complete path starting with "/"
        // }
       if (it==this) {
            return ".";
        }

        StringBuilder buf = new StringBuilder();
        TestObject next = this;
        TestObject cur = this;  
        // Walk up my ancestors from leaf to root, looking for "it"
        // and accumulating a relative url as I go
        while (next!=null && it!=next) {
            cur = next;
            buf.insert(0,'/');
            buf.insert(0,cur.getSafeName());
            next = cur.getParent();
        }
        if (it==next) {
            return buf.toString();
        } else {
            // Keep adding on to the string we've built so far

            // Start with the test result action
            AbstractTestResultAction action = getTestResultAction();
            if (action==null) {
                LOGGER.warning("trying to get relative path, but we can't determine the action that owns this result.");
                return ""; // this won't take us to the right place, but it also won't 404.
            }
            buf.insert(0,'/');
            buf.insert(0,action.getUrlName());

            // Now the build
            Run<?,?> myBuild = cur.getRun();
            if (myBuild ==null) {
                LOGGER.warning("trying to get relative path, but we can't determine the build that owns this result.");
                return ""; // this won't take us to the right place, but it also won't 404.
            }
            buf.insert(0,'/');
            buf.insert(0,myBuild.getUrl());

            // If we're inside a stapler request, just delegate to Hudson.Functions to get the relative path!
            StaplerRequest req = Stapler.getCurrentRequest();
            if (req!=null && myBuild instanceof Item) {
                buf.insert(0, '/');
                // Ugly but I don't see how else to convince the compiler that myBuild is an Item
                Item myBuildAsItem = (Item) myBuild;
                buf.insert(0, Functions.getRelativeLinkTo(myBuildAsItem));
            } else {
                // We're not in a stapler request. Okay, give up.
                LOGGER.fine("trying to get relative path, but it is not my ancestor, and we are not in a stapler request. Trying absolute jenkins url...");
                String jenkinsUrl = Jenkins.get().getRootUrl();
                if (jenkinsUrl == null || jenkinsUrl.length() == 0) {
                    LOGGER.warning("Can't find anything like a decent hudson url. Punting, returning empty string."); 
                    return "";

                }
                if (!jenkinsUrl.endsWith("/")) {
                    buf.insert(0, '/');
                }
                buf.insert(0, jenkinsUrl);
            }

            LOGGER.log(Level.FINE, "Here is our relative path: {0}", buf);
            return buf.toString(); 
        }

    }

    /**
     * Subclasses may override this method if they are
     * associated with a particular subclass of
     * AbstractTestResultAction. 
     *
     * @return  the test result action that connects this test result to a particular build
     */
    @Override
    public AbstractTestResultAction getTestResultAction() {
        Run<?, ?> owner = getRun();
        if (owner != null) {
            return owner.getAction(AbstractTestResultAction.class);
        } else {
            Run<?, ?> run = Stapler.getCurrentRequest().findAncestorObject(Run.class);
            if (run != null) {
                AbstractTestResultAction action = run.getAction(AbstractTestResultAction.class);
                return action;
            }
            LOGGER.warning("owner is null when trying to getTestResultAction.");
            return null;
        }
    }

    /**
     * Get a list of all TestActions associated with this TestObject. 
     */
    @Override
    @Exported(visibility = 3)
    public List<TestAction> getTestActions() {
        AbstractTestResultAction atra = getTestResultAction();
        if ((atra != null) && (atra instanceof TestResultAction)) {
            TestResultAction tra = (TestResultAction) atra;
            return tra.getActions(this);
        } else {
            return new ArrayList<TestAction>();
        }
    }

    /**
     * Gets a test action of the class passed in. 
     * @param klazz Type of the action to return.
     * @param <T> an instance of the class passed in
     */
    @Override
    public <T> T getTestAction(Class<T> klazz) {
        for (TestAction action : getTestActions()) {
            if (klazz.isAssignableFrom(action.getClass())) {
                return klazz.cast(action);
            }
        }
        return null;
    }

    /**
     * Gets the counterpart of this {@link TestResult} in the previous run.
     *
     * @return null if no such counter part exists.
     */
    public abstract TestResult getPreviousResult();

    @Deprecated
    public TestResult getResultInBuild(AbstractBuild<?, ?> build) {
        return (TestResult) super.getResultInBuild(build);
    }

    /**
     * Gets the counterpart of this {@link TestResult} in the specified run.
     *
     * @return null if no such counter part exists.
     */
    @Override public TestResult getResultInRun(Run<?,?> run) {
        return (TestResult) super.getResultInRun(run);
    }

    /**
     * Find the test result corresponding to the one identified by <code>id</code>
     * within this test result.
     *
     * @param id The path to the original test result
     * @return A corresponding test result, or null if there is no corresponding
     * result.
     */
    public abstract TestResult findCorrespondingResult(String id);

    /**
     * Time took to run this test. In seconds.
     */
    public abstract float getDuration();

    /**
     * Returns the string representation of the {@link #getDuration()}, in a
     * human readable format.
     */
    @Override
    public String getDurationString() {
        return Util.getTimeSpanString((long) (getDuration() * 1000));
    }

    @Override
    public String getDescription() {
        AbstractTestResultAction action = getTestResultAction();
        if (action != null) {
            return action.getDescription(this);
        }
        return "";
    }

    @Override
    public void setDescription(String description) {
        AbstractTestResultAction action = getTestResultAction();
        if (action != null) {
            action.setDescription(this, description);
        }
    }

    /**
     * Exposes this object through the remote API.
     */
    @Override
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Gets the name of this object.
     */
    @Override
    public/* abstract */ String getName() {
        return "";
    }

    /**
     * Gets the full name of this object.
     *
     * @return the full name of this object.
     * @since 1.551
     */
    public String getFullName() {
        StringBuilder sb = new StringBuilder(getName());
        if (getParent() != null) {
            sb.insert(0, " : ");
            sb.insert(0, getParent().getFullName());
        }
        return sb.toString();
    }


    /**
     * Gets the version of {@link #getName()} that's URL-safe.
     */
    @Override
    public String getSafeName() {
        return safe(getName());
    }

    @Override
    public String getSearchUrl() {
        return getSafeName();
    }

    /**
     * #2988: uniquifies a {@link #getSafeName} amongst children of the parent.
     * @param siblings Siblings of the current
     * @param base Prefix to use for the name.
     *
     * @return an unique name amongst children of the parent.
     */
    protected final String uniquifyName(Collection<? extends TestObject> siblings, String base) {
        synchronized (UNIQUIFIED_NAMES) {
            String uniquified = base;
            Map<TestObject,Void> taken = UNIQUIFIED_NAMES.get(base);
            if (taken == null) {
                taken = new WeakHashMap<TestObject,Void>();
                UNIQUIFIED_NAMES.put(base, taken);
            } else {
                Set<TestObject> similars = new HashSet<TestObject>(taken.keySet());
                similars.retainAll(new HashSet<TestObject>(siblings));
                if (!similars.isEmpty()) {
                    uniquified = base + '_' + (similars.size() + 1);
                }
            }
            taken.put(this, null);
            return uniquified;
        }
    }
    private static final Map<String,Map<TestObject,Void>> UNIQUIFIED_NAMES = new MapMaker().makeMap();

    /**
     * Replaces URL-unsafe characters.
     * If s is an empty string, returns "(empty)" otherwise the generated URL would contain
     * two slashes one after the other and getDynamic() would fail.
     * @param s String to process.
     *
     * @return the string with the unsafe characters replaced.
     */
    public static String safe(String s) {
        if (StringUtils.isEmpty(s)) {
            return "(empty)";
        } else {
            // this still seems to be a bit faster than a single replace with regexp
            return s.replace('/', '_').replace('\\', '_').replace(':', '_').replace('?', '_').replace('#', '_').replace('%', '_').replace('<', '_').replace('>', '_');
            // Note: we probably should some helpers like Commons URIEscapeUtils here to escape all invalid URL chars, but then we
            // still would have to escape /, ? and so on
        }
    }

    /**
     * Gets the total number of passed tests.
     */
    public abstract int getPassCount();

    /**
     * Gets the total number of failed tests.
     */
    public abstract int getFailCount();

    /**
     * Gets the total number of skipped tests.
     */
    public abstract int getSkipCount();

    /**
     * Gets the total number of tests.
     */
    @Override
    public int getTotalCount() {
        return getPassCount() + getFailCount() + getSkipCount();
    }

    @Override
    public History getHistory() {
        return new History(this);
    }

    public Object getDynamic(String token, StaplerRequest req,
            StaplerResponse rsp) {
        for (Action a : getTestActions()) {
            if (a == null) {
                continue; // be defensive
            }
            String urlName = a.getUrlName();
            if (urlName == null) {
                continue;
            }
            if (urlName.equals(token)) {
                return a;
            }
        }
        return null;
    }

    @RequirePOST
    public synchronized HttpResponse doSubmitDescription(
            @QueryParameter String description) throws IOException,
            ServletException {
        Run<?, ?> run = getRun();
        if (run == null) {
            LOGGER.severe("getRun() is null, can't save description.");
        } else {
            run.checkPermission(Run.UPDATE);
            setDescription(description);
            run.save();
        }

        return new HttpRedirect(".");
    }
    private static final long serialVersionUID = 1L;
}
