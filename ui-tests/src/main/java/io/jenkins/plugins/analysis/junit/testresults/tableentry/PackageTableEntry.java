package io.jenkins.plugins.analysis.junit.testresults.tableentry;

import java.util.Optional;

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

    public String getPackageName() {
        return packageName;
    }

    public String getPackageLink() {
        return packageLink;
    }

    public int getDuration() {
        return duration;
    }

    public int getFail() {
        return fail;
    }

    public Optional<Integer> getFailDiff() {
        return failDiff;
    }

    public int getSkip() {
        return skip;
    }

    public Optional<Integer> getSkipDiff() {
        return skipDiff;
    }

    public int getPass() {
        return pass;
    }

    public Optional<Integer> getPassDiff() {
        return passDiff;
    }

    public int getTotal() {
        return total;
    }

    public Optional<Integer> getTotalDiff() {
        return totalDiff;
    }
}
