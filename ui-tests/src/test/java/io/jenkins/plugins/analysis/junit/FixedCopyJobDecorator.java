package io.jenkins.plugins.analysis.junit;

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

public class FixedCopyJobDecorator {

    private final Job job;

    public FixedCopyJobDecorator(Job job) {
        this.job = job;
    }

    public Job getJob() {
        return job;
    }

    public void copyResource(Resource resource, String fileName) {
        if (SystemUtils.IS_OS_WINDOWS) {
            job.addBatchStep(copyResourceBatch(resource));
        }
        else {
            job.addShellStep(copyResourceShell(resource, fileName));
        }
    }

    public void copyResource(Resource resource) {
        copyResource(resource, resource.getName());
    }

    public void copyResource(String resourcePath) {
        job.ensureConfigPage();
        final Resource res = job.resource(resourcePath);
        //decide whether to utilize copyResource or copyDir
        if (res.asFile().isDirectory()) {
            job.copyDir(res);
        } else {
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
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
