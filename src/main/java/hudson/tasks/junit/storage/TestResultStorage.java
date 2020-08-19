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

package hudson.tasks.junit.storage;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitParser;
import hudson.tasks.junit.TestResult;
import java.io.IOException;
import javax.annotation.CheckForNull;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Allows test results to be saved and loaded from an external storage service.
 */
@Restricted(Beta.class)
public interface TestResultStorage extends ExtensionPoint {

    /**
     * Runs during {@link JUnitParser#summarizeResult}.
     */
    RemotePublisher createRemotePublisher(Run<?,?> build) throws IOException;

    /**
     * Remotable hook to perform test result publishing.
     */
    interface RemotePublisher extends SerializableOnlyOverRemoting {
        void publish(TestResult result, TaskListener listener) throws IOException;
    }

    TestResultImpl load(String job, int build);

    // TODO substitute trend graph for /job/*/ or /job/*/test/?width=800&height=600 from TestResultProjectAction/{index,floatingBox}
    // TODO substitute count/duration graph for /job/*/*/testReport/pkg/SomeTest/method/history/ and similar from History/index

    // for now, AbstractTestResultAction.descriptions and testData are not pluggable

    static @CheckForNull TestResultStorage find() {
        ExtensionList<TestResultStorage> all = ExtensionList.lookup(TestResultStorage.class);
        return all.isEmpty() ? null : all.iterator().next();
    }

}
