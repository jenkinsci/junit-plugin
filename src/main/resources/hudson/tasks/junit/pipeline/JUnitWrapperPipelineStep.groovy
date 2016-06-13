/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package hudson.tasks.junit.pipeline

import org.jenkinsci.plugins.workflow.cps.CpsScript

class JUnitWrapperPipelineStep implements Serializable {
    CpsScript script

    JUnitWrapperPipelineStep(CpsScript script) {
        this.script = script
    }

    def call(Map args, Closure closure) {
        closure.delegate = script
        closure.resolveStrategy = Closure.DELEGATE_FIRST

        String testResults = args.containsKey("testResults") ? args.testResults : ""
        boolean keepLongStdio = args.containsKey("keepLongStdio") ? args.keepLongStdio : false
        boolean allowEmptyResults = args.containsKey("allowEmptyResults") ? args.allowEmptyResults : false
        Double healthScaleFactor = args.containsKey("healthScaleFactor") ? args.healthScaleFactor : 1.0

        try {
            closure.call()
        } catch (Exception e) {
            throw e
        } finally {
            script.step($class: "JUnitResultArchiver",
                testResults: testResults,
                keepLongStdio: keepLongStdio,
                allowEmptyResults: allowEmptyResults,
                healthScaleFactor: healthScaleFactor)
        }
    }
}