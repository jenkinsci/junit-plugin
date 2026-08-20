/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikhil Tiwari
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Global configuration for JUnit Custom UI Provider.
 *
 * <p>This configuration allows setting a default custom UI provider for all jobs
 * at the Jenkins system level. The configured provider applies globally to all
 * jobs in the Jenkins instance.
 *
 * @since TBD
 */
@Extension(ordinal = 100)
@Symbol("junitCustomUI")
public class CustomUIProviderGlobalConfiguration extends GlobalConfiguration {

    private String customUIProviderId;

    @DataBoundConstructor
    public CustomUIProviderGlobalConfiguration() {
        load();
    }

    @Override
    public String getDisplayName() {
        return "JUnit Test Results";
    }

    /**
     * Gets the globally configured custom UI provider ID.
     *
     * @return the custom UI provider ID, or null if default UI should be used
     */
    @CheckForNull
    public String getCustomUIProviderId() {
        return customUIProviderId;
    }

    /**
     * Sets the globally configured custom UI provider ID.
     *
     * @param customUIProviderId the custom UI provider ID, or null to use default UI
     */
    @DataBoundSetter
    public void setCustomUIProviderId(String customUIProviderId) {
        this.customUIProviderId = customUIProviderId;
        save();
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }

    /**
     * Gets the singleton instance of this configuration.
     *
     * @return the global configuration instance
     */
    @NonNull
    public static CustomUIProviderGlobalConfiguration get() {
        return ExtensionList.lookupSingleton(CustomUIProviderGlobalConfiguration.class);
    }

    /**
     * Populates the dropdown list with available custom UI providers.
     *
     * @return list of available custom UI providers
     */
    public ListBoxModel doFillCustomUIProviderIdItems() {
        ListBoxModel items = new ListBoxModel();
        items.add("Default UI", "");

        // Add all registered custom UI providers
        for (CustomUIProvider provider : CustomUIProvider.all()) {
            items.add(provider.getDisplayName(), provider.getId());
        }

        return items;
    }
}
