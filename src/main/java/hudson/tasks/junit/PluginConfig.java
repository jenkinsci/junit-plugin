package hudson.tasks.junit;

import java.io.Serializable;

/**
 * An object storing the plugin configuration.
 */
public class PluginConfig implements Serializable {

  private final static int DEFAULT_MAX_SUCCEEDED_SIZE = 1000;

  private final static int DEFAULT_MAX_FAILED_SIZE = 100000;

  private final boolean keepLongStdio;

  private final int maxSucceededSize;

  private final int maxFailedSize;

  public PluginConfig(boolean keepLongStdio, int maxSucceededSize, int maxFailedSize) {
    this.keepLongStdio = keepLongStdio;
    this.maxFailedSize = maxFailedSize;
    this.maxSucceededSize = maxSucceededSize;
  }

  /**
   * If true, retain a suite's complete stdout/stderr even if this is huge and the
   * suite passed.
   * @return
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
   * Get a new {@link PluginConfig} initialized with defaults values.
   */
  public static PluginConfig defaults() {
    return new PluginConfig(false, DEFAULT_MAX_SUCCEEDED_SIZE, DEFAULT_MAX_FAILED_SIZE);
  }

  /**
   * Get a new {@link PluginConfig} with given <code>keepLongStdio</code>, and
   * defaults values for other configuration parameters.
   */
  public static PluginConfig defaults(boolean keepLongStdio) {
    return new PluginConfig(keepLongStdio, DEFAULT_MAX_SUCCEEDED_SIZE, DEFAULT_MAX_FAILED_SIZE);
  }

  /**
   * Get the maximum size of data to keep for a test, according to configuration.
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
    }
    else {
      if (maxSucceededSize < 0)
        return -1;
      return maxSucceededSize;
    }
  }
}
