/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Michael B. Donohue, Yahoo!, Inc., Andrew Bayer
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import static hudson.Util.fixNull;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.InvisibleAction;
import hudson.model.ItemGroup;
import hudson.tasks.junit.Helper;
import jenkins.model.Jenkins;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import org.acegisecurity.AccessDeniedException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Aggregates downstream test reports into a single consolidated report,
 * so that people can see the overall test results in one page
 * when tests are scattered across many different jobs.
 *
 * @author Kohsuke Kawaguchi
 */
public class AggregatedTestResultPublisher extends Recorder {
    /**
     * Jobs to aggregate. Comma separated.
     * Null if triggering downstreams.
     */
    public final String jobs;

    /**
     * Should failed builds be included?
     */
    public final boolean includeFailedBuilds;

    public AggregatedTestResultPublisher(String jobs) {
        this(jobs, false);
    }
    
    public AggregatedTestResultPublisher(String jobs, boolean includeFailedBuilds) {
        this.jobs = Util.fixEmptyAndTrim(jobs);
        this.includeFailedBuilds = includeFailedBuilds;
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        // add a TestResult just so that it can show up later.
        build.addAction(new TestResultAction(jobs, includeFailedBuilds, build));
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return Collections.singleton(new TestResultProjectAction(project));
    }

    /**
     * Action that serves the aggregated record.
     *
     * TODO: persist some information so that even when some of the individuals
     * are gone, we can still retain some useful information.
     */
    public static final class TestResultAction extends AbstractTestResultAction {
        /**
         * Jobs to aggregate. Comma separated.
         * Null if doing downstream projects.
         */
        private final @CheckForNull String jobs;

        /**
         * Should failed builds be included?
         */
        private final boolean includeFailedBuilds;
        
        /**
         * The last time the fields of this object is computed from the rest.
         */
        private transient long lastUpdated = 0;
        /**
         * When was the last time any build completed?
         */
        private static long lastChanged = 0;

        private transient int failCount;
        private transient int totalCount;
        private transient List<AbstractTestResultAction> individuals;
        /**
         * Projects that haven't run yet.
         */
        private transient List<AbstractProject> didntRun;
        private transient List<AbstractProject> noFingerprints;

        @SuppressWarnings("deprecation") // calls getProject in constructor, so needs owner immediately
        public TestResultAction(String jobs, boolean includeFailedBuilds, AbstractBuild<?,?> owner) {
            super(owner);
            this.includeFailedBuilds = includeFailedBuilds;
            
            if(jobs==null) {
                // resolve null as the transitive downstream jobs
                StringBuilder buf = new StringBuilder();
                for (AbstractProject p : getProject().getTransitiveDownstreamProjects()) {
                    if(buf.length()>0)  buf.append(',');
                    buf.append(p.getFullName());
                }
                jobs = buf.toString();
            }
            this.jobs = jobs;
        }

        /**
         * Gets the jobs to be monitored.
         */
        public Collection<AbstractProject> getJobs() {
            List<AbstractProject> r = new ArrayList<AbstractProject>();
            if (jobs != null) {
                for (String job : Util.tokenize(jobs,",")) {
                    try {
                        AbstractProject j = Helper.getActiveInstance().getItemByFullName(job.trim(), AbstractProject.class);
                        if (j != null) {
                            r.add(j);
                        }
                    } catch (AccessDeniedException x) {
                        // just skip it
                    }
                }
            }
            return r;
        }

        public boolean getIncludeFailedBuilds() {
            return includeFailedBuilds;
        }
        
        private AbstractProject<?,?> getProject() {
            return owner.getProject();
        }

        public int getFailCount() {
            upToDateCheck();
            return failCount;
        }

        public int getTotalCount() {
            upToDateCheck();
            return totalCount;
        }

        public Object getResult() {
            upToDateCheck();
            return this;
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
        protected String getDescription(TestObject object) {
            throw new AssertionError();
        }

        /**
         * See {@link #getDescription(TestObject)}
         *
         * @deprecated
         *      so that IDE warns you if you accidentally try to call it.
         */
        @Override
        protected void setDescription(TestObject object, String description) {
            throw new AssertionError();
        }

        /**
         * Returns the individual test results that are aggregated.
         */
        public List<AbstractTestResultAction> getIndividuals() {
            upToDateCheck();
            return Collections.unmodifiableList(individuals);
        }

        /**
         * Gets the downstream projects that haven't run yet, but
         * expected to produce test results.
         */
        public List<AbstractProject> getDidntRun() {
            return Collections.unmodifiableList(didntRun);
        }

        /** 
         * Gets the downstream projects that have available test results, but 
         * do not appear to have fingerprinting enabled.
         */
        public List<AbstractProject> getNoFingerprints() {
            return Collections.unmodifiableList(noFingerprints);
        }

        /**
         * Makes sure that the data fields are up to date.
         */
        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "False positive. Short-circuited")
        private synchronized void upToDateCheck() {
            // up to date check
            if(lastUpdated>lastChanged)     return;
            lastUpdated = lastChanged+1;

            int failCount = 0;
            int totalCount = 0;
            List<AbstractTestResultAction> individuals = new ArrayList<AbstractTestResultAction>();
            List<AbstractProject> didntRun = new ArrayList<AbstractProject>();
            List<AbstractProject> noFingerprints = new ArrayList<AbstractProject>();
            for (AbstractProject job : getJobs()) {
                RangeSet rs = owner.getDownstreamRelationship(job);
                if(rs.isEmpty()) {
                    // is this job expected to produce a test result?
                    Run b;
                    if (includeFailedBuilds) {
                        b = job.getLastBuild();
                    } else {
                        b = job.getLastSuccessfulBuild();
                    }
                    if(b!=null && b.getAction(AbstractTestResultAction.class)!=null) {
                        if(b.getAction(FingerprintAction.class)!=null) {
                            didntRun.add(job);
                        } else {
                            noFingerprints.add(job);
                        }
                    }
                } else {
                    for (int n : rs.listNumbersReverse()) {
                        Run b = job.getBuildByNumber(n);
                        if(b==null) continue;
                        Result targetResult;
                        if (includeFailedBuilds) {
                            targetResult = Result.FAILURE;
                        } else {
                            targetResult = Result.UNSTABLE;
                        }
                        
                        if(b.isBuilding() || b.getResult() == null || b.getResult().isWorseThan(targetResult))
                            continue;   // don't count them

                        for( AbstractTestResultAction ta : b.getActions(AbstractTestResultAction.class)) {
                            failCount += ta.getFailCount();
                            totalCount += ta.getTotalCount();
                            individuals.add(ta);
                        }
                        break;
                    }
                }
            }

            this.failCount = failCount;
            this.totalCount = totalCount;
            this.individuals = individuals;
            this.didntRun = didntRun;
            this.noFingerprints = noFingerprints;
        }

        public boolean getHasFingerprintAction() {
            return this.owner.getAction(FingerprintAction.class)!=null;
        }

        @Override
        public String getDisplayName() {
            return Messages.AggregatedTestResultPublisher_Title();
        }

        @Override
        public String getUrlName() {
            return "aggregatedTestReport";
        }

        @Extension
        public static class RunListenerImpl extends RunListener<Run> {
            @Override
            public void onCompleted(Run run, TaskListener listener) {
                lastChanged = System.currentTimeMillis();
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;    // for all types
        }

        public String getDisplayName() {
            return Messages.AggregatedTestResultPublisher_DisplayName();
        }

        public FormValidation doCheck(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if(!project.hasPermission(Item.CONFIGURE))  return FormValidation.ok();

            for (String name : Util.tokenize(fixNull(value), ",")) {
                name = name.trim();
                if (Helper.getActiveInstance().getItem(name,project) == null) {
                    final AbstractProject<?,?> nearest = AbstractProject.findNearest(name);
                    return FormValidation.error(Messages.BuildTrigger_NoSuchProject(name, nearest != null ? nearest.getName() : null));
                }
            }
            
            return FormValidation.ok();
        }

        @Override
        public AggregatedTestResultPublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            // Starting in 1.640, Descriptor#newInstance is
            // newInstance(@CheckForNull StaplerRequest req, @Nonnull JSONObject formData)
            if (formData == null) {
                // Should not happen. See above
                throw new AssertionError("Null parameters to Descriptor#newInstance");
            }
            JSONObject s = formData.getJSONObject("specify");
            if(s.isNullObject())
                return new AggregatedTestResultPublisher(null, req != null && req.getParameter("includeFailedBuilds") != null);
            else
                return new AggregatedTestResultPublisher(s.getString("jobs"), req != null && req.getParameter("includeFailedBuilds") != null);
        }

        public AutoCompletionCandidates doAutoCompleteJobs(@QueryParameter String value, @AncestorInPath Item self, @AncestorInPath ItemGroup container) {
            // Item.READ checked inside
            return AutoCompletionCandidates.ofJobNames(Job.class,value,self,container);
        }
    }

    @Restricted(NoExternalUse.class)
    public static final class TestResultProjectAction extends InvisibleAction {
        public final AbstractProject<?, ?> project;
        private TestResultProjectAction(AbstractProject<?,?> project) {
            this.project = project;
        }
    }

}
