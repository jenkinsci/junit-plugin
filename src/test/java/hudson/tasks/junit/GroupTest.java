/*
 * The MIT License
 * 
 * Copyright (c) 2015, Hyunil Shin
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
package hudson.tasks.junit;

import hudson.model.FreeStyleProject;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.FreeStyleBuild;
import hudson.Launcher;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestBuilder;
import java.io.IOException;

/**
 * Tests for {@link GroupByError, @link GroupedCaseResults}
 */
public class GroupTest extends HudsonTestCase {


    /**
     * Verifies that the failed tests are grouped by error message.
     */
    public void testFreestyleErrorMsgAndStacktraceRender() throws Exception {
        FreeStyleBuild b = configureTestBuild(null);
        TestResult tr = b.getAction(TestResultAction.class).getResult();
        assertEquals(4,tr.getFailedTests().size());

        GroupByError groupByError = new GroupByError(tr);
        assertEquals("Should have had two groups, but had " + groupByError.getGroups().size(), 2, groupByError.getGroups().size());
        
        
        GroupedCaseResults group1 = groupByError.getGroups().get(0);
        assertEquals("Should have had three elements, but had " + group1.getCount(), 3, group1.getCount());
        assertEquals(true, group1.similar(new CaseResult(null, "ddd", "some.package.somewhere.WhooHoo: "
        		+ "[ID : 245025], [TID : 3311e81d-c848-4d60-1111-f1fb2ff06a1f],"
        		+ " - message : On Provision problem."), 0.9f));
        
        GroupedCaseResults group2 = groupByError.getGroups().get(1);
        assertEquals("Should have had one elements, but had " + group2.getCount(), 1, group2.getCount());
        assertEquals("java.lang.NullPointerException: null", group2.getRepErrorMessage());
    }
    

    private FreeStyleBuild configureTestBuild(String projectName) throws Exception {
        FreeStyleProject p = projectName == null ? createFreeStyleProject() : createFreeStyleProject(projectName);
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("junit.xml").copyFrom(
                    getClass().getResource("junit-report-group-by-error.xml"));
                return true;
            }
        });
        p.getPublishersList().add(new JUnitResultArchiver("*.xml"));
        return assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());
    }
}
