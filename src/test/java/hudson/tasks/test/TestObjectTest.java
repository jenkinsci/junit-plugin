package hudson.tasks.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import hudson.model.Run;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestObjectTest {

    public static class TestObjectImpl extends TestObject {
        public TestObjectImpl() {}

        @Override
        public TestObject getParent() {
            return null;
        }

        @Override
        public TestResult getPreviousResult() {
            return null;
        }

        @Override
        public TestResult findCorrespondingResult(String id) {
            return null;
        }

        @Override
        public float getDuration() {
            return 0;
        }

        @Override
        public String getName() {
            return "dummy";
        }

        @Override
        public int getPassCount() {
            return 0;
        }

        @Override
        public int getFailCount() {
            return 0;
        }

        @Override
        public int getSkipCount() {
            return 0;
        }

        @Override
        public String getDisplayName() {
            return null;
        }
    }

    @Test
    void testSafe() {
        String name = "Foo#approve! is <called> by approve_on_foo?xyz/\\: 50%";
        String encoded = TestObject.safe(name);

        assertFalse(encoded.contains("#"));
        assertFalse(encoded.contains("?"));
        assertFalse(encoded.contains("\\"));
        assertFalse(encoded.contains("/"));
        assertFalse(encoded.contains(":"));
        assertFalse(encoded.contains("%"));
        assertFalse(encoded.contains("<"));
        assertFalse(encoded.contains(">"));
    }

    @Test
    void uniquifyName() {
        for (int i = 0; i < 2; i++) { // different parents
            final List<TestObject> ts = new ArrayList<>();
            for (int j = 0; j < 10; j++) {
                final String name = "t" + (int) Math.sqrt(j); // partly unique names
                ts.add(new SimpleCaseResult() {
                    @Override
                    public String getSafeName() {
                        return uniquifyName(ts, name);
                    }
                });
            }
            List<String> names = new ArrayList<>();
            for (TestObject t : ts) {
                names.add(t.getSafeName());
            }
            assertEquals("[t0, t1, t1_2, t1_3, t2, t2_2, t2_3, t2_4, t2_5, t3]", names.toString());
            Reference<?> r = new WeakReference<Object>(ts.get(4)); // arbitrarily
            ts.clear();
            System.gc();
            assertNull(r.get());
        }
    }

    @Test
    void getUrlShouldBeRelativeToContextRoot() {
        TestObject testObject = spy(new TestObjectImpl());
        Run run = mock(Run.class);
        AbstractTestResultAction testResultAction = mock(AbstractTestResultAction.class);
        doCallRealMethod().when(testResultAction).getUrlName();
        doReturn(testResultAction).when(run).getAction(eq(AbstractTestResultAction.class));
        doReturn("job/abc/123/").when(run).getUrl();
        doReturn(run).when(testObject).getRun();
        assertEquals("job/abc/123/testReport/dummy", testObject.getUrl());
    }

    @Test
    void safeReplacesEveryCharacterIllegalInAPathSegment() {
        // RFC 3986 does not allow any of these to appear literally in a URL path segment
        for (char c : " \"#%/:<>?[\\]^`{|}".toCharArray()) {
            assertEquals("a_b", TestObject.safe("a" + c + "b"), "should have replaced '" + c + "'");
        }
    }

    @Test
    void safeReplacesControlCharacters() {
        assertEquals("a_b", TestObject.safe("a\u0000b"));
        assertEquals("a_b", TestObject.safe("a\tb"));
        assertEquals("a_b", TestObject.safe("a\nb"));
        assertEquals("a_b", TestObject.safe("a\u007Fb"));
    }

    @Test
    void safeKeepsCharactersLegalInAPathSegment() {
        String legal = "azAZ09-._~!$&'()*+,;=@";
        assertEquals(legal, TestObject.safe(legal));
    }

    @Test
    void safeKeepsNonAsciiCharacters() {
        // percent-encoded as UTF-8 by the browser, so they survive the round trip
        String name = "Gr\u00fc\u00dfe\u30c6\u30b9\u30c8";
        assertEquals(name, TestObject.safe(name));
    }

    @Test
    void safeReturnsPlaceholderForNullOrEmptyName() {
        assertEquals("(empty)", TestObject.safe(null));
        assertEquals("(empty)", TestObject.safe(""));
    }

    @Test
    void safeReplacesEveryOccurrenceOfAnUnsafeCharacter() {
        assertEquals("a_b_c", TestObject.safe("a|b|c"));
        assertEquals("___", TestObject.safe("|||"));
    }
}
