package hudson.tasks.test;

import hudson.model.Run;
import org.junit.Assert;
import org.junit.Test;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TestObjectTest {

    public static class TestObjectImpl extends TestObject {
        public TestObjectImpl() {
        }

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
    public void testSafe() {
        String name = "Foo#approve! is called by approve_on_foo?xyz/\\: 50%";
        String encoded = TestObject.safe(name);
        
        Assert.assertFalse(encoded.contains("#"));
        Assert.assertFalse(encoded.contains("?"));
        Assert.assertFalse(encoded.contains("\\"));
        Assert.assertFalse(encoded.contains("/"));
        Assert.assertFalse(encoded.contains(":"));
        Assert.assertFalse(encoded.contains("%"));
    }

    @Test public void uniquifyName() {
        for (int i = 0; i < 2; i++) { // different parents
            final List<TestObject> ts = new ArrayList<TestObject>();
            for (int j = 0; j < 10; j++) {
                final String name = "t" + (int) Math.sqrt(j); // partly unique names
                ts.add(new SimpleCaseResult() {
                    @Override public String getSafeName() {
                        return uniquifyName(ts, name);
                    }
                });
            }
            List<String> names = new ArrayList<String>();
            for (TestObject t : ts) {
                names.add(t.getSafeName());
            }
            assertEquals("[t0, t1, t1_2, t1_3, t2, t2_2, t2_3, t2_4, t2_5, t3]", names.toString());
            Reference<?> r = new WeakReference<Object>(ts.get(4)); // arbitrarily
            ts.clear();
            System.gc();
            Assert.assertNull(r.get());
        }
    }

    @Test
    public void getUrlShouldBeRelativeToContextRoot() {
        TestObject testObject = spy(new TestObjectImpl());
        Run run = mock(Run.class);
        AbstractTestResultAction testResultAction = mock(AbstractTestResultAction.class);
        doCallRealMethod().when(testResultAction).getUrlName();
        doReturn(testResultAction).when(run).getAction(eq(AbstractTestResultAction.class));
        doReturn("job/abc/123/").when(run).getUrl();
        doReturn(run).when(testObject).getRun();
        assertEquals("job/abc/123/testReport/dummy", testObject.getUrl());
    }
    
}
