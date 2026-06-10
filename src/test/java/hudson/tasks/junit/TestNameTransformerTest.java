package hudson.tasks.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jenkins.security.MasterToSlaveCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class TestNameTransformerTest {

    private static final String UNIQUE_NAME_FOR_TEST = "unique-name-to-test-name-transformer";

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

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testNameIsTransformed() {
        assertEquals(
                UNIQUE_NAME_FOR_TEST + "-transformed", TestNameTransformer.getTransformedName(UNIQUE_NAME_FOR_TEST));
    }

    @Issue("JENKINS-61787")
    @Test
    void testNameIsNotTransformedRemotely() throws Exception {
        assertEquals(UNIQUE_NAME_FOR_TEST, j.createOnlineSlave().getChannel().call(new Remote()));
    }

    private static final class Remote extends MasterToSlaveCallable<String, RuntimeException> {
        @Override
        public String call() throws RuntimeException {
            return TestNameTransformer.getTransformedName(UNIQUE_NAME_FOR_TEST);
        }
    }
}
