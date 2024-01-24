package hudson.tasks.junit;

public enum StdioRetention {

    all("All"),
    failed("Failed tests only"),
    none("None");

    private final String displayName;

    StdioRetention(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static final StdioRetention DEFAULT = none;

    public static StdioRetention fromKeepLongStdio(boolean value) {
        return value ? all : none;
    }

}
