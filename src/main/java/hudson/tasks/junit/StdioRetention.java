package hudson.tasks.junit;

public enum StdioRetention {

    all,
    failed,
    none;

    public static final StdioRetention DEFAULT = none;

    public static StdioRetention fromKeepLongStdio(boolean value) {
        return value ? all : none;
    }

}
