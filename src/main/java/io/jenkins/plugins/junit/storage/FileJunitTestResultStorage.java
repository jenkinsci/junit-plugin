package io.jenkins.plugins.junit.storage;

import hudson.Extension;
import hudson.model.Run;
import java.io.IOException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

@Extension
@Restricted(Beta.class)
public class FileJunitTestResultStorage extends JunitTestResultStorage {
    @Override
    public RemotePublisher createRemotePublisher(Run<?, ?> build) throws IOException {
        return null;
    }

    @Override
    public TestResultImpl load(String job, int build) {
        return null;
    }

    @Extension
    public static class DescriptorImpl extends JunitTestResultStorageDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.FileJunitTestResultStorage_displayName();
        }

    }
}
