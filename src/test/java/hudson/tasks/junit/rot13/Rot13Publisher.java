package hudson.tasks.junit.rot13;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.ClassResult;
import hudson.tasks.junit.PackageResult;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.test.TestObject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A TestDataPublisher that adds a custom column containing the ROT13-encoded name of each test case, class and package
 * in html tables across result pages. This publisher is intended to be used for validating that custom columns work as
 * expected, but also as an illustration of how to provide custom columns. In this publisher, the values in the columns
 * explicitly state where they're expected to be displayed (for example "ROT13 for failed cases on package page") to
 * make it possible to validate that the correct jelly view is used in the correct page. Real-life implementations of
 * custom columns will likely not need to distinguish between different pages and can get away with fewer TestActions,
 * allowing greater reuse of jelly views.
 */
public class Rot13Publisher extends TestDataPublisher {

    @DataBoundConstructor
    public Rot13Publisher() {}

    @Override
    public Data contributeTestData(
            Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, TestResult testResult)
            throws IOException, InterruptedException {
        Map<String, String> ciphertextMap = new HashMap<>();
        for (PackageResult packageResult : testResult.getChildren()) {
            ciphertextMap.put(packageResult.getName(), rot13(packageResult.getName()));
            for (ClassResult classResult : packageResult.getChildren()) {
                ciphertextMap.put(classResult.getFullName(), rot13(classResult.getName()));
                for (CaseResult caseResult : classResult.getChildren()) {
                    ciphertextMap.put(caseResult.getFullName(), rot13(caseResult.getName()));
                }
            }
        }
        return new Data(ciphertextMap);
    }

    private static String rot13(String cleartext) {
        StringBuilder ciphertext = new StringBuilder();
        cleartext.chars().forEach(c -> {
            if ('a' <= c && c <= 'z') {
                c = c + 13;
                if ('z' < c) {
                    c = c - 26;
                }
            }
            if ('A' <= c && c <= 'Z') {
                c = c + 13;
                if ('Z' < c) {
                    c = c - 26;
                }
            }
            ciphertext.append((char) c);
        });
        return ciphertext.toString();
    }

    public static class Data extends TestResultAction.Data {

        private Map<String, String> ciphertextMap;

        public Data(Map<String, String> ciphertextMap) {
            this.ciphertextMap = ciphertextMap;
        }

        @Override
        @SuppressWarnings("deprecation")
        public List<TestAction> getTestAction(hudson.tasks.junit.TestObject t) {
            TestObject testObject = (TestObject) t;

            if (testObject instanceof CaseResult) {
                return Collections.singletonList(new Rot13CaseAction(ciphertextMap.get(testObject.getFullName())));
            }
            if (testObject instanceof ClassResult) {
                return Collections.singletonList(
                        new Rot13ClassAction(ciphertextMap.get(((ClassResult) testObject).getFullName())));
            }
            if (testObject instanceof PackageResult) {
                return Collections.singletonList(new Rot13PackageAction(ciphertextMap.get(testObject.getName())));
            }
            if (testObject instanceof TestResult) {
                return Collections.singletonList(new Rot13TestAction());
            }
            return Collections.emptyList();
        }
    }

    @Extension
    @Symbol("rot13")
    public static class DescriptorImpl extends Descriptor<TestDataPublisher> {

        @Override
        public String getDisplayName() {
            return "ROT13-encoded test case, class and package names";
        }
    }
}
