/*
 * The MIT License
 *
 * Copyright 2010 Jesse Glick.
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

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.localizer.LocaleProvider;

import static org.junit.Assert.assertEquals;

@For(CaseResult.class)
public class CaseResultUnitTest {
    
    public CaseResultUnitTest() {
    }
    
    @Issue("JENKINS-6824")
    @Test
    public void testLocalizationOfStatus() throws Exception {
        LocaleProvider old = LocaleProvider.getProvider();
        try {
            final AtomicReference<Locale> locale = new AtomicReference<>();
            LocaleProvider.setProvider(new LocaleProvider() {
                public @Override Locale get() {
                    return locale.get();
                }
            });
            locale.set(Locale.GERMANY);
            assertEquals("Erfolg", CaseResult.Status.PASSED.getMessage());
            locale.set(Locale.US);
            assertEquals("Passed", CaseResult.Status.PASSED.getMessage());
        } finally {
            LocaleProvider.setProvider(old);
        }
    }
    
}
