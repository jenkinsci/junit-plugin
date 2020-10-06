/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.LinesChartModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.junit.JUnitResultArchiver;
import io.jenkins.plugins.junit.storage.FileJunitTestResultStorage;
import io.jenkins.plugins.junit.storage.TestResultImpl;
import io.jenkins.plugins.junit.storage.JunitTestResultStorage;
import io.jenkins.plugins.echarts.AsyncTrendChart;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * Project action object from test reporter, such as {@link JUnitResultArchiver},
 * which displays the trend report on the project top page.
 *
 * <p>
 * This works with any {@link AbstractTestResultAction} implementation.
 *
 * @author Kohsuke Kawaguchi
 */
public class TestResultProjectAction implements Action, AsyncTrendChart {
    /**
     * Project that owns this action.
     * @since 1.2-beta-1
     */
    public final Job<?,?> job;

    @Deprecated
    public final AbstractProject<?,?> project;

    /**
     * @since 1.2-beta-1
     */
    public TestResultProjectAction(Job<?,?> job) {
        this.job = job;
        project = job instanceof AbstractProject ? (AbstractProject) job : null;
    }

    @Deprecated
    public TestResultProjectAction(AbstractProject<?,?> project) {
        this((Job) project);
    }

    /**
     * No task list item.
     */
    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Test Report";
    }

    public String getUrlName() {
        return "test";
    }

    public AbstractTestResultAction getLastTestResultAction() {
        final Run<?,?> tb = job.getLastSuccessfulBuild();

        Run<?,?> b = job.getLastBuild();
        while(b!=null) {
            AbstractTestResultAction a = b.getAction(AbstractTestResultAction.class);
            if(a!=null && (!b.isBuilding())) return a;
            if(b==tb)
                // if even the last successful build didn't produce the test result,
                // that means we just don't have any tests configured.
                return null;
            b = b.getPreviousBuild();
        }

        return null;
    }

    protected LinesChartModel createChartModel() {
        Run<?, ?> lastCompletedBuild = job.getLastCompletedBuild();

        JunitTestResultStorage storage = JunitTestResultStorage.find();
        if (!(storage instanceof FileJunitTestResultStorage)) {
            TestResultImpl pluggableStorage = storage.load(lastCompletedBuild.getParent().getFullName(), lastCompletedBuild.getNumber());
            return new TestResultTrendChart().create(pluggableStorage.getTrendTestResultSummary());
        }

        TestResultActionIterable buildHistory = createBuildHistory(lastCompletedBuild);
        if (buildHistory == null) {
            return new LinesChartModel();
        }
        return new TestResultTrendChart().create(buildHistory, new ChartModelConfiguration());
    }

    @CheckForNull
    private TestResultActionIterable createBuildHistory(Run<?, ?> lastCompletedBuild) {
        // some plugins that depend on junit seem to attach the action even though there's no run
        // e.g. xUnit and cucumber
        if (lastCompletedBuild == null) {
            return null;
        }
        AbstractTestResultAction<?> action = lastCompletedBuild.getAction(AbstractTestResultAction.class);
        if (action == null) {
            Run<?, ?> currentBuild = lastCompletedBuild;
            while (action == null) {
                currentBuild = currentBuild.getPreviousBuild();
                if (currentBuild == null) {
                    return null;
                }
                action = currentBuild.getAction(AbstractTestResultAction.class);
            }
        }
        return new TestResultActionIterable(action);
    }

    /**
     * Display the test result trend.
     * 
     * @deprecated Replaced by echarts in TODO
     */
    @Deprecated
    public void doTrend( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        AbstractTestResultAction a = getLastTestResultAction();
        if(a!=null)
            a.doGraph(req,rsp);
        else
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Generates the clickable map HTML fragment for {@link #doTrend(StaplerRequest, StaplerResponse)}.
     *
     * @deprecated Replaced by echarts in TODO
     */
    @Deprecated
    public void doTrendMap( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        AbstractTestResultAction a = getLastTestResultAction();
        if(a!=null)
            a.doGraphMap(req,rsp);
        else
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Changes the test result report display mode.
     */
    public void doFlipTrend( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        boolean failureOnly = false;

        // check the current preference value
        Cookie[] cookies = req.getCookies();
        if(cookies!=null) {
            for (Cookie cookie : cookies) {
                if(cookie.getName().equals(FAILURE_ONLY_COOKIE))
                    failureOnly = Boolean.parseBoolean(cookie.getValue());
            }
        }

        // flip!
        failureOnly = !failureOnly;

        // set the updated value
        Cookie cookie = new Cookie(FAILURE_ONLY_COOKIE,String.valueOf(failureOnly));
        List<Ancestor> anc = req.getAncestors();
        Ancestor a = (Ancestor) anc.get(anc.size()-2);
        cookie.setPath(a.getUrl()); // just for this project
        cookie.setMaxAge(60*60*24*365); // 1 year
        rsp.addCookie(cookie);

        // back to the project page
        rsp.sendRedirect("..");
    }

    private static final String FAILURE_ONLY_COOKIE = "TestResultAction_failureOnly";

    @JavaScriptMethod
    @Override
    public String getBuildTrendModel() {
        return new JacksonFacade().toJson(createChartModel());
    }

    @Override
    public boolean isTrendVisible() {
        return true;
    }
}
