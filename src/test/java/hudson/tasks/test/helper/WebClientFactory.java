package hudson.tasks.test.helper;

import org.htmlunit.NicelyResynchronizingAjaxController;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Creates a {@link WebClient} with deactivated JS.
 *
 * @author Ullrich Hafner
 */
public class WebClientFactory {
    public static JenkinsRule.WebClient createWebClientWithDisabledJavaScript(final JenkinsRule jenkinsRule) {
        JenkinsRule.WebClient webClient = jenkinsRule.createWebClient();
        webClient.setJavaScriptEnabled(false);
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        webClient.getCookieManager().setCookiesEnabled(false);
        webClient.getOptions().setCssEnabled(false);
        return webClient;
    }
}
