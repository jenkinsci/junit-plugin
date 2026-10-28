package io.jenkins.plugins.junit.storage;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResult;
import java.util.function.Consumer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Lightweight view of a single test case in a single build, as returned by
 * {@link TestResultImpl#forEachCaseResultSummary(int, int, Consumer)}.
 *
 * <p>Unlike {@link CaseResult} it carries no stdout, stderr, stack trace or parent objects, so a storage
 * implementation can produce one per row for many builds at once without hydrating a full
 * {@link TestResult} for each build.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public final class CaseResultSummary {
    private final int build;
    private final String suiteName;
    private final String className;
    private final String testName;
    private final boolean failed;
    private final boolean skipped;
    private final float duration;

    /**
     * @param build the build number the case was recorded in
     * @param suiteName the name of the suite containing the case
     * @param className the fully qualified class name of the case
     * @param testName the raw test name of the case
     * @param failed whether the case failed or errored (ignored if {@code skipped})
     * @param skipped whether the case was skipped
     * @param duration the duration of the case, in seconds
     */
    public CaseResultSummary(
            int build,
            @CheckForNull String suiteName,
            @NonNull String className,
            @CheckForNull String testName,
            boolean failed,
            boolean skipped,
            float duration) {
        this.build = build;
        this.suiteName = suiteName;
        this.className = className;
        this.testName = testName;
        this.failed = failed && !skipped;
        this.skipped = skipped;
        this.duration = duration;
    }

    public int getBuild() {
        return build;
    }

    @CheckForNull
    public String getSuiteName() {
        return suiteName;
    }

    /**
     * @return the fully qualified class name, as {@link CaseResult#getClassName()}
     */
    @NonNull
    public String getClassName() {
        return className;
    }

    /**
     * @return the test name, as {@link CaseResult#getName()}
     */
    @NonNull
    public String getName() {
        if (testName == null || testName.isEmpty()) {
            return "(?)";
        }
        return testName;
    }

    /**
     * @return the package name, as {@link CaseResult#getPackageName()}
     */
    @NonNull
    public String getPackageName() {
        int idx = className.lastIndexOf('.');
        return idx < 0 ? "(root)" : className.substring(0, idx);
    }

    /**
     * @return the class name without its package, as {@link CaseResult#getSimpleName()}
     */
    @NonNull
    public String getSimpleName() {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    public boolean isFailed() {
        return failed;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public boolean isPassed() {
        return !failed && !skipped;
    }

    public float getDuration() {
        return duration;
    }
}
