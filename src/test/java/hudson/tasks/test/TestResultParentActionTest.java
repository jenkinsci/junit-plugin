/*
 * The MIT License
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
package hudson.tasks.test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import hudson.model.Run;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the case where {@link TestResult#getParentAction()} returns
 * {@code null} because no {@link AbstractTestResultAction} is attached to the run yet.
 * This happens in particular when a {@link hudson.tasks.junit.TestDataPublisher} walks
 * history during {@link hudson.tasks.junit.JUnitResultArchiver}'s
 * {@code parseAndSummarize}: the action is constructed but only added to the build
 * <em>after</em> the publisher loop. Without a guard, {@code getParentAction().getClass()}
 * NPEs inside {@link TestResult#getPreviousResult} and
 * {@link TestResult#getResultInRun}.
 */
class TestResultParentActionTest {

    @Test
    void getPreviousResultShortCircuitsWhenParentActionNull() {
        TestResult testResult = spy(new SimpleCaseResult());
        Run currentRun = mock(Run.class);
        doReturn(currentRun).when(testResult).getRun();
        doReturn(null).when(testResult).getParentAction();

        assertNull(testResult.getPreviousResult());
        // The fix bails before entering the history-walking loop; without it, the
        // loop iterates getPreviousBuild() repeatedly and NPEs once per previous build.
        verify(currentRun, never()).getPreviousBuild();
    }

    @Test
    void getResultInRunReturnsNullWhenParentActionNull() {
        TestResult testResult = spy(new SimpleCaseResult());
        Run targetRun = mock(Run.class);
        doReturn(null).when(testResult).getParentAction();

        // Without the fix this NPEs on getParentAction().getClass().
        assertNull(testResult.getResultInRun(targetRun));
    }
}
