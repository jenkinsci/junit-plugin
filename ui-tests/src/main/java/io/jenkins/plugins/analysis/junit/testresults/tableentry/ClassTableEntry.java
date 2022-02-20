package io.jenkins.plugins.analysis.junit.testresults.tableentry;

import java.util.Optional;

/**
 * The entry of a class table listing test results of current and previous builds.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class ClassTableEntry {

    private final String className;

    private final String classLink;

    private final int duration;

    private final int fail;

    private final Optional<Integer> failDiff;

    private final int skip;

    private final Optional<Integer> skipDiff;

    private final int pass;

    private final Optional<Integer> passDiff;

    private final int total;

    private final Optional<Integer> totalDiff;

    /**
     * Custom constructor. Creates object.
     * @param className the class name property
     * @param classLink the target location class link
     * @param duration the duration property
     * @param fail the fail property
     * @param failDiff the fail diff property
     * @param skip the skip property
     * @param skipDiff the skip diff property
     * @param pass the pass property
     * @param passDiff the pass diff property
     * @param total the total property
     * @param totalDiff the total diff property
     */
    public ClassTableEntry(final String className, final String classLink, final int duration, final int fail,
            final Optional<Integer> failDiff, final int skip,
            final Optional<Integer> skipDiff, final int pass, final Optional<Integer> passDiff, final int total, final Optional<Integer> totalDiff) {
        this.className = className;
        this.classLink = classLink;
        this.duration = duration;
        this.fail = fail;
        this.failDiff = failDiff;
        this.skip = skip;
        this.skipDiff = skipDiff;
        this.pass = pass;
        this.passDiff = passDiff;
        this.total = total;
        this.totalDiff = totalDiff;
    }

    /**
     * Gets the property class name.
     * @return the class name property
     */
    public String getClassName() {
        return className;
    }

    /**
     * Gets the target location when clicking on the class name.
     * @return the class link
     */
    public String getClassLink() {
        return classLink;
    }

    /**
     * Gets the property duration.
     * @return the duration property
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Gets the property fail.
     * @return the fail property
     */
    public int getFail() {
        return fail;
    }

    /**
     * Gets the optional property fail diff.
     * @return the fail diff property
     */
    public Optional<Integer> getFailDiff() {
        return failDiff;
    }

    /**
     * Gets the property skip.
     * @return the skip property
     */
    public int getSkip() {
        return skip;
    }

    /**
     * Gets the optional property skip diff.
     * @return the skip diff property
     */
    public Optional<Integer> getSkipDiff() {
        return skipDiff;
    }

    /**
     * Gets the property pass.
     * @return the pass property
     */
    public int getPass() {
        return pass;
    }

    /**
     * Gets the optional property pass diff.
     * @return the pass diff property
     */
    public Optional<Integer> getPassDiff() {
        return passDiff;
    }

    /**
     * Gets the property total.
     * @return the total property
     */
    public int getTotal() {
        return total;
    }

    /**
     * Gets the optional property total diff.
     * @return the total diff property
     */
    public Optional<Integer> getTotalDiff() {
        return totalDiff;
    }
}
