package hudson.tasks.junit;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum StdioRetention {
    ALL(Messages.StdioRetention_All_DisplayName()),
    FAILED(Messages.StdioRetention_Failed_DisplayName()),
    NONE(Messages.StdioRetention_None_DisplayName());

    private final String displayName;

    StdioRetention(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static final StdioRetention DEFAULT = NONE;

    public static StdioRetention fromKeepLongStdio(boolean value) {
        return value ? ALL : NONE;
    }

    public static StdioRetention parse(String value) {
        if (value == null || value.isEmpty()) {
            return DEFAULT;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unrecognized value '" + value + "'; must be one of the following: "
                            + Stream.of(values()).map(Enum::name).collect(Collectors.joining(", ")),
                    e);
        }
    }
}
