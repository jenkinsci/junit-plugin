package io.jenkins.plugins.analysis.junit.builddetail.tableentry;

import java.util.Optional;

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

    private final int totalDiff;

    public ClassTableEntry(final String className, final String classLink, final int duration, final int fail,
            final Optional<Integer> failDiff, final int skip,
            final Optional<Integer> skipDiff, final int pass, final Optional<Integer> passDiff, final int total, final int totalDiff) {
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

    public String getClassName() {
        return className;
    }

    public String getClassLink() {
        return classLink;
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

    public int getTotalDiff() {
        return totalDiff;
    }
}
