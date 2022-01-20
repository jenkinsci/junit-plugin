package io.jenkins.plugins.analysis.junit.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SystemUtils;
import org.codehaus.plexus.util.Base64;

import org.jenkinsci.test.acceptance.junit.Resource;
import org.jenkinsci.test.acceptance.po.Job;

/**
 * Wrapper around a {@link Job} with slightly modified behaviour in methods {@link #copyResource(Resource)}, {@link #copyResource(String)} and {@link #copyResource(Resource, String)}.
 * Without these modifications, the following Jenkins error occurs when resources are copied by {@code xcopy} using Windows OS: {@Code Test reports were found but none of them are new. Did tests run?}.
 * The solution applied here is to execute another command {@code touch} to update the timestamp and fix the error.
 * @author Nikolas Paripovic
 */
public class FixedCopyJobDecorator {

    private final Job job;

    /**
     * Custom constructor. Creates object.
     * @param job the jenkins job to apply the fix
     */
    public FixedCopyJobDecorator(Job job) {
        this.job = job;
    }

    /**
     * Gets the origin object with default behaviour.
     * If called {@link Job#copyResource(Resource)}, {@link Job#copyResource(String)} or {@link Job#copyResource(Resource, String)} on this object, no fix (described in this class description) is applied here.
     * @return the origin object
     */
    public Job getJob() {
        return job;
    }

    /**
     * Slightly modified behaviour of {@link Job#copyResource(Resource, String)} with the bugfix described in this class description.
     * @param resource the resource to copy
     * @param fileName the name of the resource to copy
     */
    public void copyResource(Resource resource, String fileName) {
        if (SystemUtils.IS_OS_WINDOWS) {
            job.addBatchStep(copyResourceBatch(resource));
        }
        else {
            job.addShellStep(copyResourceShell(resource, fileName));
        }
    }

    /**
     * Slightly modified behaviour of {@link Job#copyResource(Resource)} with the bugfix described in this class description.
     * @param resource the resource to copy
     */
    public void copyResource(Resource resource) {
        copyResource(resource, resource.getName());
    }

    /**
     * Slightly modified behaviour of {@link Job#copyResource(String)} with the bugfix described in this class description.
     * @param resourcePath the resource to copy
     */
    public void copyResource(String resourcePath) {
        job.ensureConfigPage();
        final Resource res = job.resource(resourcePath);
        //decide whether to utilize copyResource or copyDir
        if (res.asFile().isDirectory()) {
            job.copyDir(res);
        }
        else {
            copyResource(res);
        }
    }

    private String copyResourceBatch(Resource resource) {
        String path = resource.url.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        path = path.replace("/", "\\");
        return xCopy(path, ".") + "\n" + touch(resource.getName());
    }

    private String touch(final String source) {
        return "touch " + source;
    }

    private String xCopy(final String source, final String destination) {
        return "xcopy " + source + " " + destination + " /E /Y";
    }

    private String copyResourceShell(Resource resource, String fileName) {
        try (InputStream in = resource.asInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            try (OutputStream gz = new GZIPOutputStream(out)) {
                IOUtils.copy(in, gz);
            }

            // fileName can include path portion like foo/bar/zot
            return String.format("(mkdir -p %1$s || true) && rm -r %1$s && base64 --decode << ENDOFFILE | gunzip > %1$s \n%2$s\nENDOFFILE",
                    fileName, new String(Base64.encodeBase64Chunked(out.toByteArray())));
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
