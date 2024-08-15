package io.jenkins.plugins.junit.storage;

import hudson.Extension;
import hudson.model.Run;
import java.io.IOException;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.DataBoundConstructor;

@Extension
@Restricted(Beta.class)
public class FileJunitTestResultStorage extends JunitTestResultStorage {

    @DataBoundConstructor
    public FileJunitTestResultStorage() {}

    @Override
    public RemotePublisher createRemotePublisher(Run<?, ?> build) throws IOException {
        return null;
    }

    @Override
    public TestResultImpl load(String job, int build) {
        return null;
    }

    @Extension
    @Symbol("file")
    public static class DescriptorImpl extends JunitTestResultStorageDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.FileJunitTestResultStorage_displayName();
        }
    }
}
