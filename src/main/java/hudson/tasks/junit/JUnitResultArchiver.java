/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt,
 * Tom Huybrechts, Yahoo!, Inc., Richard Hierlmeier
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

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.junit.TestResultAction.Data;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Generates HTML report from JUnit test result XML files.
 * 
 * @author Kohsuke Kawaguchi
 */
public class JUnitResultArchiver extends Recorder implements SimpleBuildStep {

    /**
     * {@link FileSet} "includes" string, like "foo/bar/*.xml"
     */
    private final String testResults;

    /**
     * If true, retain a suite's complete stdout/stderr even if this is huge and the suite passed.
     * @since 1.358
     */
    private boolean keepLongStdio;

    /**
     * {@link TestDataPublisher}s configured for this archiver, to process the recorded data.
     * For compatibility reasons, can be null.
     * @since 1.320
     */
    private DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers;

    private Double healthScaleFactor;

	@DataBoundConstructor
	public JUnitResultArchiver(String testResults) {
		this.testResults = testResults;
	}

    @Deprecated
    public JUnitResultArchiver(String testResults,
            DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers) {
        this(testResults, false, testDataPublishers);
    }
	
	@Deprecated
	public JUnitResultArchiver(
			String testResults,
            boolean keepLongStdio,
			DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers) {
        this(testResults, keepLongStdio, testDataPublishers, 1.0);
    }

	@Deprecated
	public JUnitResultArchiver(
			String testResults,
            boolean keepLongStdio,
			DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers,
            double healthScaleFactor) {
		this.testResults = testResults;
        setKeepLongStdio(keepLongStdio);
        setTestDataPublishers(testDataPublishers == null ? Collections.<TestDataPublisher>emptyList() : testDataPublishers);
        setHealthScaleFactor(healthScaleFactor);
	}

    private TestResult parse(String expandedTestResults, Run<?,?> run, @Nonnull FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException
    {
        return new JUnitParser(isKeepLongStdio()).parseResult(expandedTestResults, run, workspace, launcher, listener);
    }

    @Deprecated
    protected TestResult parse(String expandedTestResults, AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException
    {
        return parse(expandedTestResults, build, build.getWorkspace(), launcher, listener);
    }

    @Override
	public void perform(Run build, FilePath workspace, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
		listener.getLogger().println(Messages.JUnitResultArchiver_Recording());
		
		final String testResults = build.getEnvironment(listener).expand(this.testResults);

			TestResult result = parse(testResults, build, workspace, launcher, listener);

        synchronized (build) {
            // TODO can the build argument be omitted now, or is it used prior to the call to addAction?
            TestResultAction action = build.getAction(TestResultAction.class);
            boolean appending;
            if (action == null) {
                appending = false;
                action = new TestResultAction(build, result, listener);
            } else {
                appending = true;
                result.freeze(action);
                action.mergeResult(result, listener);
            }
            action.setHealthScaleFactor(getHealthScaleFactor()); // overwrites previous value if appending
			if (result.isEmpty()) {
			    // most likely a configuration error in the job - e.g. false pattern to match the JUnit result files
                listener.getLogger().println(Messages.JUnitResultArchiver_ResultIsEmpty());
                return;
			}

            // TODO: Move into JUnitParser [BUG 3123310]
			List<Data> data = action.getData();
			if (testDataPublishers != null) {
				for (TestDataPublisher tdp : testDataPublishers) {
					Data d = tdp.contributeTestData(build, workspace, launcher, listener, result);
					if (d != null) {
						data.add(d);
					}
				}
			}

            if (appending) {
                build.save();
            } else {
                build.addAction(action);
            }

		if (action.getResult().getFailCount() > 0)
			build.setResult(Result.UNSTABLE);
        }
	}

	/**
	 * Not actually used, but left for backward compatibility
	 * 
	 * @deprecated since 2009-08-10.
	 */
	protected TestResult parseResult(DirectoryScanner ds, long buildTime)
			throws IOException {
		return new TestResult(buildTime, ds);
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public String getTestResults() {
		return testResults;
	}

    public double getHealthScaleFactor() {
        return healthScaleFactor == null ? 1.0 : healthScaleFactor;
    }

    /** @since 1.2-beta-1 */
    @DataBoundSetter public final void setHealthScaleFactor(double healthScaleFactor) {
        this.healthScaleFactor = Math.max(0.0, healthScaleFactor);
    }

    public @Nonnull List<? extends TestDataPublisher> getTestDataPublishers() {
		return testDataPublishers == null ? Collections.<TestDataPublisher>emptyList() : testDataPublishers;
	}

    /** @since 1.2 */
    @DataBoundSetter public final void setTestDataPublishers(@Nonnull List<? extends TestDataPublisher> testDataPublishers) {
        this.testDataPublishers = new DescribableList<TestDataPublisher,Descriptor<TestDataPublisher>>(Saveable.NOOP);
        this.testDataPublishers.addAll(testDataPublishers);
    }

	/**
	 * @return the keepLongStdio
	 */
	public boolean isKeepLongStdio() {
		return keepLongStdio;
	}

    /** @since 1.2-beta-1 */
    @DataBoundSetter public final void setKeepLongStdio(boolean keepLongStdio) {
        this.keepLongStdio = keepLongStdio;
    }

	private static final long serialVersionUID = 1L;

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		public String getDisplayName() {
			return Messages.JUnitResultArchiver_DisplayName();
		}

		/**
		 * Performs on-the-fly validation on the file mask wildcard.
		 */
		public FormValidation doCheckTestResults(
				@AncestorInPath AbstractProject project,
				@QueryParameter String value) throws IOException {
            if (project == null) {
                return FormValidation.ok();
            }
			return FilePath.validateFileMask(project.getSomeWorkspace(), value);
		}

		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

        public FormValidation doCheckHealthScaleFactor(@QueryParameter double value) {
            if (value < 1e-7) return FormValidation.warning("Test health reporting disabled");
            return FormValidation.ok(Messages.JUnitResultArchiver_HealthScaleFactorAnalysis(
                    1,
                    (int) (100.0 - Math.max(0.0, Math.min(100.0, 1 * value))),
                    5,
                    (int) (100.0 - Math.max(0.0, Math.min(100.0, 5 * value)))
            ));
        }
    }
}
