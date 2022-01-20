package io.jenkins.plugins.analysis.junit.testresults.tableentry;

import java.util.Optional;

/**
 * The entry of a package table listing test results of current and previous builds.
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class PackageTableEntry {

    private final String packageName;

    private final String packageLink;

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
     * @param packageName the package name property
     * @param packageLink the target location package link
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
    public PackageTableEntry(final String packageName, final String packageLink, final int duration, final int fail,
            final Optional<Integer> failDiff, final int skip,
            final Optional<Integer> skipDiff, final int pass, final Optional<Integer> passDiff, final int total, final Optional<Integer> totalDiff) {
        this.packageName = packageName;
        this.packageLink = packageLink;
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
     * Gets the package name property.
     * @return the package name property
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Gets the target location when clicking on the package name.
     * @return the package link
     */
    public String getPackageLink() {
        return packageLink;
    }

    /**
     * Gets the duration property.
     * @return the duration property
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Gets the fail property.
     * @return the fail property
     */
    public int getFail() {
        return fail;
    }

    /**
     * Gets the optional fail diff property.
     * @return the fail diff property
     */
    public Optional<Integer> getFailDiff() {
        return failDiff;
    }

    /**
     * Gets the skip property.
     * @return the skip property
     */
    public int getSkip() {
        return skip;
    }

    /**
     * Gets the optional skip diff property.
     * @return the skip diff property
     */
    public Optional<Integer> getSkipDiff() {
        return skipDiff;
    }

    /**
     * Gets the pass property.
     * @return the pass property
     */
    public int getPass() {
        return pass;
    }

    /**
     * Gets the optional pass diff property.
     * @return the pass diff property
     */
    public Optional<Integer> getPassDiff() {
        return passDiff;
    }

    /**
     * Gets the total property.
     * @return the total property
     */
    public int getTotal() {
        return total;
    }

    /**
     * Gets the optional total diff property.
     * @return the total diff property
     */
    public Optional<Integer> getTotalDiff() {
        return totalDiff;
    }
}
