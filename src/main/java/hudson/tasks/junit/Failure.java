package hudson.tasks.junit;

import java.io.Serializable;
import java.util.Objects;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * A failed attempt of a test that was rerun, such as a Surefire {@code flakyFailure} or {@code rerunFailure}.
 *
 * <p>This is deliberately a plain class rather than a {@code record}: {@link CaseResult} instances can end up in
 * {@code build.xml} (for example through a {@link TestResultAction.Data} that references the {@link TestResult}),
 * and XStream's reflection converter cannot deserialize records, which makes the whole build fail to load.
 * The accessor names match the record components this class used to have, so callers are unaffected.
 */
@ExportedBean
public final class Failure implements Serializable {

    private static final long serialVersionUID = 0L;

    private final String message;
    private final String type;
    private final String stackTrace;
    private final String stdout;
    private final String stderr;

    public Failure(String message, String type, String stackTrace, String stdout, String stderr) {
        this.message = message;
        this.type = type;
        this.stackTrace = stackTrace;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public String message() {
        return message;
    }

    public String type() {
        return type;
    }

    public String stackTrace() {
        return stackTrace;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Failure other)) {
            return false;
        }
        return Objects.equals(message, other.message)
                && Objects.equals(type, other.type)
                && Objects.equals(stackTrace, other.stackTrace)
                && Objects.equals(stdout, other.stdout)
                && Objects.equals(stderr, other.stderr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, type, stackTrace, stdout, stderr);
    }

    @Override
    public String toString() {
        return "Failure[message=" + message + ", type=" + type + ", stackTrace=" + stackTrace + ", stdout=" + stdout
                + ", stderr=" + stderr + "]";
    }
}
