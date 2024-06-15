/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.plugins.junit.storage;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitParser;
import hudson.tasks.junit.TestResult;
import java.io.IOException;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Allows test results to be saved and loaded from an external storage service.
 */
@Restricted(Beta.class)
public abstract class JunitTestResultStorage extends AbstractDescribableImpl<JunitTestResultStorage> implements ExtensionPoint {

    public RemotePublisher createRemotePublisher(Run<?,?> build) throws IOException {
        throw new UnsupportedOperationException("Implement createRemotePublisher(Run<?,?>, String) instead.");
    }

    /**
     * Runs during {@link JUnitParser#summarizeResult}.
     */
    public abstract RemotePublisher createRemotePublisher(Run<?,?> build, @CheckForNull String flowNodeId) throws IOException;

    /**
     * Remotable hook to perform test result publishing.
     */
    public interface RemotePublisher extends SerializableOnlyOverRemoting {
        void publish(TestResult result, TaskListener listener) throws IOException;
    }

    public abstract TestResultImpl load(String job, int build);

    // for now, AbstractTestResultAction.descriptions and testData are not pluggable

    public static JunitTestResultStorage find() {
        return JunitTestResultStorageConfiguration.get().getStorage();
    }

}
