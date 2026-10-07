/*
 * The MIT License
 *
 * Copyright 2010 Jesse Glick.
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import io.jenkins.plugins.junit.storage.TestResultImpl;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.localizer.LocaleProvider;

@For(CaseResult.class)
class CaseResultUnitTest {

    @Issue("JENKINS-6824")
    @Test
    void testLocalizationOfStatus() {
        LocaleProvider old = LocaleProvider.getProvider();
        try {
            final AtomicReference<Locale> locale = new AtomicReference<>();
            LocaleProvider.setProvider(new LocaleProvider() {
                public @Override Locale get() {
                    return locale.get();
                }
            });
            locale.set(Locale.GERMANY);
            assertEquals("Erfolg", CaseResult.Status.PASSED.getMessage());
            locale.set(Locale.US);
            assertEquals("Passed", CaseResult.Status.PASSED.getMessage());
        } finally {
            LocaleProvider.setProvider(old);
        }
    }

    @Test
    void isUrlValue() {
        CaseResult cr = new CaseResult(null, "testName", null);
        assertTrue(cr.isUrlValue("http://example.com/build/123"));
        assertTrue(cr.isUrlValue("https://example.com/build/123"));
        assertFalse(cr.isUrlValue(null));
        assertFalse(cr.isUrlValue(""));
        assertFalse(cr.isUrlValue("just some text"));
        assertFalse(cr.isUrlValue("ftp://example.com"));
        assertFalse(cr.isUrlValue("https://example.com/with space"));
        assertFalse(cr.isUrlValue("https://example.com\nsecond line"));
    }

    @Test
    void getPreviousResultUsesStorageFastPathWhenSupported() {
        SuiteResult suite = new SuiteResult("suite", null, null, null);
        CaseResult current = new CaseResult(suite, "test1", null);
        CaseResult expected = new CaseResult(new SuiteResult("suite", null, null, null), "test1", null);

        FakeTestResultImpl impl = new FakeTestResultImpl() {
            @Override
            public boolean supportsPreviousCaseResultLookup() {
                return true;
            }

            @Override
            public Optional<CaseResult> getPreviousCaseResult(@NonNull CaseResult queried) {
                assertSame(current, queried);
                return Optional.of(expected);
            }

            @Override
            public TestResult getPreviousResult() {
                fail("should not fall back to the build-by-build walk when storage supports the fast path");
                return null;
            }
        };
        suite.setParent(new TestResult(impl));

        assertSame(expected, current.getPreviousResult());
    }

    @Test
    void getPreviousResultReturnsNullWhenStorageDefinitivelyHasNone() {
        SuiteResult suite = new SuiteResult("suite", null, null, null);
        CaseResult current = new CaseResult(suite, "test1", null);

        FakeTestResultImpl impl = new FakeTestResultImpl() {
            @Override
            public boolean supportsPreviousCaseResultLookup() {
                return true;
            }

            @Override
            public Optional<CaseResult> getPreviousCaseResult(@NonNull CaseResult queried) {
                return Optional.empty();
            }

            @Override
            public TestResult getPreviousResult() {
                fail("should not fall back to the build-by-build walk when storage supports the fast path");
                return null;
            }
        };
        suite.setParent(new TestResult(impl));

        assertNull(current.getPreviousResult());
    }

    @Test
    void getPreviousResultFallsBackToWalkWhenStorageDoesNotSupportFastPath() {
        SuiteResult suite = new SuiteResult("suite", null, null, null);
        CaseResult current = new CaseResult(suite, "test1", null);

        FakeTestResultImpl impl = new FakeTestResultImpl() {
            // supportsPreviousCaseResultLookup() left at its default (false)

            @Override
            public Optional<CaseResult> getPreviousCaseResult(@NonNull CaseResult queried) {
                fail("should not be called when the storage does not support the fast path");
                return Optional.empty();
            }

            @Override
            public TestResult getPreviousResult() {
                // No earlier build at all, so the walk terminates immediately.
                return null;
            }
        };
        suite.setParent(new TestResult(impl));

        assertNull(current.getPreviousResult());
    }

    /**
     * Minimal {@link TestResultImpl} stub implementing only the methods exercised by
     * {@link CaseResult#getPreviousResult()}; every other method throws {@link UnsupportedOperationException}
     * since it is not expected to be called by these tests.
     */
    private abstract static class FakeTestResultImpl implements TestResultImpl {
        @Override
        public int getFailCount() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getSkipCount() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getPassCount() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getTotalCount() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CaseResult> getFailedTests() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CaseResult> getFailedTestsByPackage(String packageName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CaseResult> getSkippedTests() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CaseResult> getSkippedTestsByPackage(String packageName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CaseResult> getPassedTests() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CaseResult> getPassedTestsByPackage(String packageName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PackageResult getPackageResult(String packageName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PackageResult> getAllPackageResults() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TrendTestResultSummary> getTrendTestResultSummary() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TestDurationResultSummary> getTestDurationResultSummary() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<HistoryTestResultSummary> getHistorySummary(int offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getCountOfBuildsWithTestResults() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Run<?, ?> getFailedSinceRun(CaseResult caseResult) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public String getJobName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getBuild() {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public TestResult getResultByNodes(@NonNull List<String> nodeIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SuiteResult getSuite(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<SuiteResult> getSuites() {
            return Collections.emptyList();
        }

        @Override
        public float getTotalTestDuration() {
            throw new UnsupportedOperationException();
        }
    }
}
