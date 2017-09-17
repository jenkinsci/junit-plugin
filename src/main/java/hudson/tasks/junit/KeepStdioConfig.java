package hudson.tasks.junit;

import java.io.Serializable;

/**
 * Object storing all stdio-keeping related configuration.
 */
public final class KeepStdioConfig implements Serializable {

    private final static int DEFAULT_MAX_SUCCEEDED_SIZE = 1000;

    private final static int DEFAULT_MAX_FAILED_SIZE = 100000;

    private final boolean keepLongStdio;

    private final int maxSucceededSize;

    private final int maxFailedSize;

    public KeepStdioConfig(boolean keepLongStdio, int maxSucceededSize, int maxFailedSize) {
        this.keepLongStdio = keepLongStdio;
        this.maxFailedSize = maxFailedSize;
        this.maxSucceededSize = maxSucceededSize;
    }

    /**
     * If true, retain a suite's complete stdout/stderr even if this is huge and the
     * suite passed.
     *
     * @return true if all stdio should be kept.
     */
    public boolean isKeepLongStdio() {
        return keepLongStdio;
    }

    /**
     * @return maximum number of bytes to keep for a succeeded test, or -1 if infinite.
     */
    public int getMaxSucceededSize() {
        return maxSucceededSize;
    }

    /**
     * @return maximum number of bytes to keep for a failed test, or -1 if infinite.
     */
    public int getMaxFailedSize() {
        return maxFailedSize;
    }

    /**
     * Get a new {@link KeepStdioConfig} initialized with defaults values.
     */
    public static KeepStdioConfig defaults() {
        return new KeepStdioConfig(false, DEFAULT_MAX_SUCCEEDED_SIZE, DEFAULT_MAX_FAILED_SIZE);
    }

    /**
     * Get a new {@link KeepStdioConfig} with given <code>keepLongStdio</code>, and
     * defaults values for other configuration parameters.
     */
    public static KeepStdioConfig defaults(boolean keepLongStdio) {
        return new KeepStdioConfig(keepLongStdio, DEFAULT_MAX_SUCCEEDED_SIZE, DEFAULT_MAX_FAILED_SIZE);
    }

    /**
     * Get the maximum size of data to keep for a test, according to configuration.
     *
     * @param failed if test has failed.
     * @return maximum number of bytes to keep.
     */
    public int getMaxSize(boolean failed) {
        if (keepLongStdio) {
            return -1;
        }

        if (failed) {
            if (maxFailedSize < 0)
                return -1;
            return maxFailedSize;
        } else {
            if (maxSucceededSize < 0)
                return -1;
            return maxSucceededSize;
        }
    }
}
