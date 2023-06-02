package hudson.tasks.test.helper;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import org.htmlunit.NicelyResynchronizingAjaxController;

/**
 * Creates a {@link WebClient} with deactivated JS.
 *
 * @author Ullrich Hafner
 */
public class WebClientFactory {
    public static WebClient createWebClientWithDisabledJavaScript(final JenkinsRule jenkinsRule) {
        WebClient webClient = jenkinsRule.createWebClient();
        webClient.setJavaScriptEnabled(false);
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        webClient.getCookieManager().setCookiesEnabled(false);
        webClient.getOptions().setCssEnabled(false);
        return webClient;
    }
}
