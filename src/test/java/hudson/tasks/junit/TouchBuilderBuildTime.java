package hudson.tasks.junit;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.Builder;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

/**
 * during unit tests for some reasons build.getStartTimeInMillis can be higher than the first time TouchBuilder is execued
 * so we ensure a real life by at least 1ms older
 */
public class TouchBuilderBuildTime extends Builder implements Serializable {
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        TouchFileCallable touchFileCallable = new TouchFileCallable( build.getTimeInMillis()+1);
        for (FilePath f : build.getWorkspace().list()) {
            f.act(touchFileCallable);
            //f.touch(Math.max(build.getStartTimeInMillis(), build.getTimeInMillis())+5);
        }
        return true;
    }

    private static class TouchFileCallable implements Serializable, FilePath.FileCallable {

        private final long time;
        private TouchFileCallable(long time) {
            this.time = time;
        }
        @Override
        public Object invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            Files.setLastModifiedTime(f.toPath(), FileTime.fromMillis(time));
            return null;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {

        }
    }
}
