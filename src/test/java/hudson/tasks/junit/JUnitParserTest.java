/*
 * The MIT License
 *
 * Copyright (c) 2009, Yahoo!, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import hudson.tasks.test.TestResult;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Test the mechanism for calling a JUnitParser separately
 * from the JUnitResultsArchiver
 *
 */
@WithJenkins
class JUnitParserTest {

    static hudson.tasks.junit.TestResult theResult = null;

    public static final class JUnitParserTestBuilder extends Builder implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String testResultLocation;

        public JUnitParserTestBuilder(String testResultLocation) {
            this.testResultLocation = testResultLocation;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            System.out.println("in perform...");

            // First touch all the files, so they will be recently modified
            for (FilePath f : build.getWorkspace().list()) {
                f.touch(System.currentTimeMillis());
            }

            System.out.println("...touched everything");
            hudson.tasks.junit.TestResult result = (new JUnitParser())
                    .parseResult(testResultLocation, build, null, build.getWorkspace(), launcher, listener);

            System.out.println("back from parse");
            assertNotNull(result, "we should have a non-null result");
            assertInstanceOf(hudson.tasks.junit.TestResult.class, result, "result should be a TestResult");
            System.out.println("We passed some assertions in the JUnitParserTestBuilder");
            theResult = result;
            return result != null;
        }
    }

    private FreeStyleProject project;
    private String projectName = "junit_parser_test";

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        this.rule = rule;
        theResult = null;
        project = rule.createFreeStyleProject(projectName);
        project.getBuildersList().add(new JUnitParserTestBuilder("*.xml"));
    }

    @LocalData
    @Test
    void testJustParsing() throws Exception {
        FreeStyleBuild build = project.scheduleBuild2(0).get(100, TimeUnit.MINUTES);
        assertNotNull(build);

        // Now let's examine the result. We know lots of stuff about it because
        // we've analyzed the xml source files by hand.
        assertNotNull(theResult, "we should have a result in the static member");

        // Check the overall counts. We should have 1 failure, 0 skips, and 132 passes.
        Collection<? extends TestResult> children = theResult.getChildren();
        assertFalse(children.isEmpty(), "Should have several packages");
        assertTrue(children.size() > 3, "Should have several pacakges");
        int passCount = theResult.getPassCount();
        assertEquals(131, passCount, "expecting many passes");
        int failCount = theResult.getFailCount();
        assertEquals(1, failCount, "we should have one failure");
        assertEquals(0, theResult.getSkipCount(), "expected 0 skips");
        assertEquals(132, theResult.getTotalCount(), "expected 132 total tests");

        // Dig in to the failed test
        final String EXPECTED_FAILING_TEST_NAME = "testDataCompatibilityWith1_282";
        final String EXPECTED_FAILING_TEST_CLASSNAME = "hudson.security.HudsonPrivateSecurityRealmTest";

        Collection<? extends TestResult> failingTests = theResult.getFailedTests();
        assertEquals(1, failingTests.size(), "should have one failed test");
        Map<String, TestResult> failedTestsByName = new HashMap<>();
        for (TestResult r : failingTests) {
            failedTestsByName.put(r.getName(), r);
        }
        assertTrue(failedTestsByName.containsKey(EXPECTED_FAILING_TEST_NAME), "we've got the expected failed test");
        TestResult firstFailedTest = failedTestsByName.get(EXPECTED_FAILING_TEST_NAME);
        assertFalse(firstFailedTest.isPassed(), "should not have passed this test");

        assertInstanceOf(CaseResult.class, firstFailedTest);
        CaseResult firstFailedTestJunit = (CaseResult) firstFailedTest;
        assertEquals(EXPECTED_FAILING_TEST_CLASSNAME, firstFailedTestJunit.getClassName());

        // TODO: Dig in to the passed tests, too

    }
}
