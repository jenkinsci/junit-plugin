package hudson.tasks.junit;

import java.io.Serializable;

public record FlakyFailure(String message, String type, String stackTrace, String stdout, String stderr)
        implements Serializable {}
