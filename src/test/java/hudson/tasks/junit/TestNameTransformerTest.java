package hudson.tasks.junit;

import jenkins.security.MasterToSlaveCallable;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class TestNameTransformerTest {

    private static final String UNIQUE_NAME_FOR_TEST = "unique-name-to-test-name-transformer";
    @Rule public JenkinsRule j = new JenkinsRule();

    @TestExtension
    public static class TestTransformer extends TestNameTransformer {
        @Override
        public String transformName(String name) {
            if (UNIQUE_NAME_FOR_TEST.equals(name)) {
                return name + "-transformed";
            }
            return name;
        }
    }

    @Test
    public void testNameIsTransformed() throws Exception {
        assertEquals(UNIQUE_NAME_FOR_TEST + "-transformed", TestNameTransformer.getTransformedName(UNIQUE_NAME_FOR_TEST));
    }

    @Issue("JENKINS-61787")
    @Test
    public void testNameIsNotTransformedRemotely() throws Exception {
        assertEquals(UNIQUE_NAME_FOR_TEST, j.createOnlineSlave().getChannel().call(new Remote()));
    }
    private static final class Remote extends MasterToSlaveCallable<String, RuntimeException> {
        @Override
        public String call() throws RuntimeException {
            return TestNameTransformer.getTransformedName(UNIQUE_NAME_FOR_TEST);
        }
    }

}

