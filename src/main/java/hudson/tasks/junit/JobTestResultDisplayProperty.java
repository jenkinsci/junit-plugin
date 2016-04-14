/*
 * The MIT License
 *
 * Copyright 2016 SAP SE
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

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 */
public class JobTestResultDisplayProperty extends JobProperty<Job<?, ?>> {
	private final boolean disableHistoricalResults;

	public JobTestResultDisplayProperty(Boolean disableHistoricalResults) {
		if ( disableHistoricalResults != null ) {
			this.disableHistoricalResults = disableHistoricalResults;
		}
		else {
			this.disableHistoricalResults = false;
		}
	}

	public boolean getDisableHistoricalResults() {
		return disableHistoricalResults;
	}

	@Extension
	public static class DescriptorImpl extends JobPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return null;
		}

		@Override
		public JobTestResultDisplayProperty newInstance(StaplerRequest req, JSONObject formData) {
			if (formData.isNullObject()) return null;
			return new JobTestResultDisplayProperty(formData.getBoolean("junitsettings-disableHistoricalResults"));
		}
	}
}
