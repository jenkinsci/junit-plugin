package hudson.tasks.junit;

import java.io.Serializable;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public record Failure(String message, String type, String stackTrace, String stdout, String stderr)
        implements Serializable {}
