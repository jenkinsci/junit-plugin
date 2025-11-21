package hudson.tasks.junit;

import java.io.Serializable;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class FlakyFailure implements Serializable {
    private String message;
    private String type;
    private String stackTrace;
    private String stdout;
    private String stderr;

    public FlakyFailure() {}

    public FlakyFailure(String message, String type, String stackTrace, String stdout, String stderr) {
        this.message = message;
        this.type = type;
        this.stackTrace = stackTrace;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }
}
