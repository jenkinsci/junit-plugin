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

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.LinesChartModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TrendTestResultSummary;
import hudson.tasks.test.TestResultTrendChart.PassedColor;

import io.jenkins.plugins.echarts.AsyncConfigurableTrendChart;
import io.jenkins.plugins.echarts.AsyncTrendChart;
import io.jenkins.plugins.junit.storage.FileJunitTestResultStorage;
import io.jenkins.plugins.junit.storage.JunitTestResultStorage;
import io.jenkins.plugins.junit.storage.TestResultImpl;

/**
 * Project action object from test reporter, such as {@link JUnitResultArchiver},
 * which displays the trend report on the project top page.
 *
 * <p>
 * This works with any {@link AbstractTestResultAction} implementation.
 *
 * @author Kohsuke Kawaguchi
 */
public class TestResultProjectAction implements Action, AsyncTrendChart, AsyncConfigurableTrendChart {
    private static final JacksonFacade JACKSON_FACADE = new JacksonFacade();

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
    public TestResultProjectAction(final Job<?,?> job) {
        this.job = job;
        project = job instanceof AbstractProject ? (AbstractProject) job : null;
    }

    @Deprecated
    public TestResultProjectAction(final AbstractProject<?,?> project) {
        this((Job) project);
    }

    /**
     * No task list item.
     */
    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Test Report";
    }

    @Override
    public String getUrlName() {
        return "test";
    }

    public AbstractTestResultAction getLastTestResultAction() {
        /*
        Any build with test results should be considered.
        Nowadays pipeline builds can be failed, even though just a substage failed, whereas other stages do produce test results.
        Using UNSTABLE is not feasible for this, as that does not mark a build as containing a failure for other systems that list the Jenkins builds externally.
        */
        Run<?,?> b = job.getLastBuild();
        while(b!=null) {
            AbstractTestResultAction a = b.getAction(AbstractTestResultAction.class);
            if(a!=null && (!b.isBuilding())) return a;
            b = b.getPreviousBuild();
        }

        return null;
    }

    @Deprecated
    protected LinesChartModel createChartModel() {
        return createChartModel(new ChartModelConfiguration(), PassedColor.BLUE);
    }

    private LinesChartModel createChartModel(ChartModelConfiguration configuration, PassedColor passedColor) {
        Run<?, ?> lastCompletedBuild = job.getLastCompletedBuild();

        JunitTestResultStorage storage = JunitTestResultStorage.find();
        if (!(storage instanceof FileJunitTestResultStorage)) {
            TestResultImpl pluggableStorage = storage.load(lastCompletedBuild.getParent().getFullName(), lastCompletedBuild.getNumber());
            List<TrendTestResultSummary> summary = pluggableStorage.getTrendTestResultSummary();
            if (summary.isEmpty()) {
                return new LinesChartModel();
            }
            return new TestResultTrendChart().create(summary, passedColor);
        }

        TestResultActionIterable buildHistory = createBuildHistory(lastCompletedBuild);
        if (buildHistory == null) {
            return new LinesChartModel();
        }
        return new TestResultTrendChart().create(buildHistory, configuration, passedColor);
    }

    @CheckForNull
    private TestResultActionIterable createBuildHistory(final Run<?, ?> lastCompletedBuild) {
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
    public void doTrend( final StaplerRequest req, final StaplerResponse rsp ) throws IOException, ServletException {
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
    public void doTrendMap( final StaplerRequest req, final StaplerResponse rsp ) throws IOException, ServletException {
        AbstractTestResultAction a = getLastTestResultAction();
        if(a!=null)
            a.doGraphMap(req,rsp);
        else
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Changes the test result report display mode.
     */
    public void doFlipTrend( final StaplerRequest req, final StaplerResponse rsp ) throws IOException, ServletException {
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
        Ancestor a = anc.get(anc.size()-2);
        cookie.setPath(a.getUrl()); // just for this project
        cookie.setMaxAge(60*60*24*365); // 1 year
        rsp.addCookie(cookie);

        // back to the project page
        rsp.sendRedirect("..");
    }

    private static final String FAILURE_ONLY_COOKIE = "TestResultAction_failureOnly";

    @Override @Deprecated
    public String getBuildTrendModel() {
        return new JacksonFacade().toJson(createChartModel());
    }

    @JavaScriptMethod
    @Override
    public String getConfigurableBuildTrendModel(final String configuration) {
        PassedColor useBlue = JACKSON_FACADE.getBoolean(configuration, "useBlue", false) ? PassedColor.BLUE : PassedColor.GREEN;
        return new JacksonFacade().toJson(createChartModel(ChartModelConfiguration.fromJson(configuration), useBlue));
    }

    @Override
    public boolean isTrendVisible() {
        return true;
    }
}
