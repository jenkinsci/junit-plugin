package hudson.tasks.junit;

import static org.junit.jupiter.api.Assertions.*;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Tests for {@link CustomUIProvider} extension point and global configuration.
 */
public class CustomUIProviderTest {

    @RegisterExtension
    public JenkinsRule jenkins = new JenkinsRule();

    /**
     * Clean up global configuration after each test to prevent test pollution.
     */
    @AfterEach
    public void cleanupGlobalConfig() {
        CustomUIProviderGlobalConfiguration.get().setCustomUIProviderId(null);
    }

    @Test
    public void testCustomUIProviderRegistration() {
        // Test that the CustomUIProvider extension point can be queried
        assertNotNull(CustomUIProvider.all(), "CustomUIProvider.all() should not return null");
    }

    @Test
    public void testFindByIdReturnsNullForInvalidId() {
        CustomUIProvider provider = CustomUIProvider.findById("non-existent-provider");
        assertNull(provider, "Should return null for non-existent provider ID");
    }

    @Test
    public void testFindByIdReturnsNullForNullId() {
        CustomUIProvider provider = CustomUIProvider.findById(null);
        assertNull(provider, "Should return null for null provider ID");
    }

    @Test
    public void testFindByIdReturnsNullForEmptyId() {
        CustomUIProvider provider = CustomUIProvider.findById("");
        assertNull(provider, "Should return null for empty provider ID");
    }

    @Test
    public void testTestResultActionCustomUIConfiguration() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        Run<?, ?> build = project.scheduleBuild2(0).get();

        TestResult testResult = new TestResult();
        TestResultAction action = new TestResultAction(build, testResult, null);

        // Test default value
        assertNull(action.getCustomUIProviderId(), "Default customUIProviderId should be null");
        assertFalse(action.useCustomUI(), "useCustomUI should return false by default");

        // Test setting custom UI provider ID
        action.setCustomUIProviderId("test-provider");
        assertEquals("test-provider", action.getCustomUIProviderId(), "CustomUIProviderId should be set");

        // Since there's no actual provider registered with this ID, it should still return false
        assertFalse(action.useCustomUI(), "useCustomUI should return false when provider not found");
    }

    @Test
    public void testCustomUIProviderIntegration() throws Exception {
        // Verify the test extension is registered
        CustomUIProvider provider = CustomUIProvider.findById("test-integration-provider");
        assertNotNull(provider, "Test provider should be registered");
        assertEquals("Test Integration Provider", provider.getDisplayName());

        // Create a test result action with custom UI provider
        FreeStyleProject project = jenkins.createFreeStyleProject();
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        TestResult testResult = new TestResult();
        TestResultAction action = new TestResultAction(build, testResult, null);
        action.setCustomUIProviderId("test-integration-provider");

        // Verify custom UI is used
        assertTrue(action.useCustomUI(), "useCustomUI should return true with registered provider");
        assertNotNull(action.getCustomUIProvider(), "getCustomUIProvider should return the provider");
        assertEquals("test-integration-provider", action.getCustomUIProvider().getId());
    }

    @Test
    public void testCustomUIProviderApplicability() throws Exception {
        CustomUIProvider provider = CustomUIProvider.findById("test-conditional-provider");
        assertNotNull(provider, "Conditional provider should be registered");

        FreeStyleProject project = jenkins.createFreeStyleProject();
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        TestResult testResult = new TestResult();
        TestResultAction action = new TestResultAction(build, testResult, null);
        action.setCustomUIProviderId("test-conditional-provider");

        // Since ConditionalTestProvider.isApplicable returns false, custom UI should not be used
        assertFalse(action.useCustomUI(), "useCustomUI should return false when provider is not applicable");
    }

    @Test
    public void testGlobalConfigurationExists() {
        // Test that the global configuration can be retrieved
        CustomUIProviderGlobalConfiguration globalConfig = CustomUIProviderGlobalConfiguration.get();
        assertNotNull(globalConfig, "Global configuration should not be null");
    }

    @Test
    public void testGlobalConfigurationDefaultValue() {
        // Test that the default value is null (default UI)
        CustomUIProviderGlobalConfiguration globalConfig = CustomUIProviderGlobalConfiguration.get();
        assertNull(globalConfig.getCustomUIProviderId(), "Default global custom UI provider ID should be null");
    }

    @Test
    public void testGlobalConfigurationSetAndGet() {
        // Test setting and getting global configuration
        CustomUIProviderGlobalConfiguration globalConfig = CustomUIProviderGlobalConfiguration.get();

        // Set a custom UI provider ID
        globalConfig.setCustomUIProviderId("test-global-provider");
        assertEquals(
                "test-global-provider",
                globalConfig.getCustomUIProviderId(),
                "Global custom UI provider ID should be set");

        // Reset to null
        globalConfig.setCustomUIProviderId(null);
        assertNull(globalConfig.getCustomUIProviderId(), "Global custom UI provider ID should be reset to null");
    }

    @Test
    public void testGlobalConfigurationAppliedToAction() throws Exception {
        // Test that global configuration is applied to test result actions
        CustomUIProviderGlobalConfiguration globalConfig = CustomUIProviderGlobalConfiguration.get();

        // Set global configuration
        globalConfig.setCustomUIProviderId("test-integration-provider");

        // Create a build and action to test that global config is used
        FreeStyleProject project = jenkins.createFreeStyleProject();
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        TestResult testResult = new TestResult();
        TestResultAction action = new TestResultAction(build, testResult, null);

        // Simulate what JUnitResultArchiver.configureAction does
        String providerId = CustomUIProviderGlobalConfiguration.get().getCustomUIProviderId();
        if (providerId != null && !providerId.isEmpty()) {
            action.setCustomUIProviderId(providerId);
        }

        // Verify that the action uses the global configuration
        assertEquals(
                "test-integration-provider", action.getCustomUIProviderId(), "Action should use global configuration");

        // Verify that custom UI is actually used
        assertTrue(action.useCustomUI(), "useCustomUI should return true with global config");
    }

    @Test
    public void testGlobalConfigurationWithNonExistentProvider() throws Exception {
        // Test that global configuration with non-existent provider falls back gracefully
        CustomUIProviderGlobalConfiguration globalConfig = CustomUIProviderGlobalConfiguration.get();

        // Set global configuration to a non-existent provider
        globalConfig.setCustomUIProviderId("non-existent-global-provider");

        // Create a build and action
        FreeStyleProject project = jenkins.createFreeStyleProject();
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        TestResult testResult = new TestResult();
        TestResultAction action = new TestResultAction(build, testResult, null);

        // Simulate what JUnitResultArchiver.configureAction does
        String providerId = CustomUIProviderGlobalConfiguration.get().getCustomUIProviderId();
        if (providerId != null && !providerId.isEmpty()) {
            action.setCustomUIProviderId(providerId);
        }

        // Verify that the action has the provider ID set, but useCustomUI returns false
        assertEquals(
                "non-existent-global-provider",
                action.getCustomUIProviderId(),
                "Action should have the provider ID set");
        assertFalse(action.useCustomUI(), "useCustomUI should return false for non-existent provider");
    }

    /**
     * Test implementation of CustomUIProvider for testing purposes.
     * This is registered via @TestExtension to test actual integration.
     */
    @TestExtension({"testCustomUIProviderIntegration", "testGlobalConfigurationAppliedToAction"})
    public static class TestIntegrationProvider extends CustomUIProvider {
        @Override
        public String getId() {
            return "test-integration-provider";
        }

        @Override
        public String getDisplayName() {
            return "Test Integration Provider";
        }

        @Override
        public void renderTestResultUI(TestResult testResult, StaplerRequest2 req, StaplerResponse2 rsp)
                throws IOException {
            rsp.setContentType("text/html;charset=UTF-8");
            rsp.getWriter().println("<html><body>");
            rsp.getWriter().println("<h1>Test Integration UI</h1>");
            rsp.getWriter().println("<p>Total: " + testResult.getTotalCount() + "</p>");
            rsp.getWriter().println("</body></html>");
        }

        @Override
        public void renderCaseResultUI(CaseResult caseResult, StaplerRequest2 req, StaplerResponse2 rsp)
                throws IOException {
            rsp.setContentType("text/html;charset=UTF-8");
            rsp.getWriter().println("<html><body>");
            rsp.getWriter().println("<h1>Test Case: " + caseResult.getDisplayName() + "</h1>");
            rsp.getWriter().println("</body></html>");
        }

        @Override
        public boolean isApplicable(Run<?, ?> run) {
            return true;
        }
    }

    /**
     * Test provider that is not applicable to any builds.
     */
    @TestExtension("testCustomUIProviderApplicability")
    public static class ConditionalTestProvider extends CustomUIProvider {
        @Override
        public String getId() {
            return "test-conditional-provider";
        }

        @Override
        public String getDisplayName() {
            return "Conditional Test Provider";
        }

        @Override
        public void renderTestResultUI(TestResult testResult, StaplerRequest2 req, StaplerResponse2 rsp)
                throws IOException {
            rsp.setContentType("text/html;charset=UTF-8");
            rsp.getWriter().println("<html><body><h1>Should Not Be Rendered</h1></body></html>");
        }

        @Override
        public boolean isApplicable(Run<?, ?> run) {
            // Return false to test that custom UI is not used when provider is not applicable
            return false;
        }
    }
}
