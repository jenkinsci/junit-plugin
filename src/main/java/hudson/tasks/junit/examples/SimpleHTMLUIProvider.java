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
package hudson.tasks.junit.examples;

import hudson.Extension;
import hudson.model.Run;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.CustomUIProvider;
import hudson.tasks.junit.TestResult;
import java.io.IOException;
import java.io.PrintWriter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Example custom UI provider that demonstrates the CustomUIProvider extension point.
 *
 * <p>This is a simple test implementation that shows basic HTML rendering.
 * In production, you would create this in a separate plugin.
 */
@Extension
public class SimpleHTMLUIProvider extends CustomUIProvider {

    @Override
    public String getId() {
        return "simple-html-ui";
    }

    @Override
    public String getDisplayName() {
        return "Simple HTML UI (Test Example)";
    }

    @Override
    public void renderTestResultUI(TestResult testResult, StaplerRequest2 req, StaplerResponse2 rsp)
            throws IOException {
        rsp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = rsp.getWriter();

        out.println("<!DOCTYPE html>");
        out.println("<html><head>");
        out.println("<title>Custom Test Results - " + testResult.getDisplayName() + "</title>");
        out.println("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        out.println("<style>");
        out.println("* { box-sizing: border-box; margin: 0; padding: 0; }");
        out.println("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif;");
        out.println("       background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);");
        out.println("       padding: 40px; min-height: 100vh; }");
        out.println(".container { max-width: 1200px; margin: 0 auto; }");
        out.println(".header { background: white; padding: 30px; border-radius: 12px;");
        out.println("          box-shadow: 0 8px 32px rgba(0,0,0,0.1); margin-bottom: 30px; }");
        out.println(".header h1 { color: #333; font-size: 32px; margin-bottom: 10px; }");
        out.println(".header p { color: #666; font-size: 14px; }");
        out.println(".badge { display: inline-block; background: #667eea; color: white;");
        out.println("         padding: 4px 12px; border-radius: 12px; font-size: 12px;");
        out.println("         font-weight: 600; margin-left: 10px; }");
        out.println(".stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));");
        out.println("         gap: 20px; margin-bottom: 30px; }");
        out.println(".stat { background: white; padding: 24px; border-radius: 12px;");
        out.println("        box-shadow: 0 4px 16px rgba(0,0,0,0.1);");
        out.println("        transition: transform 0.2s; }");
        out.println(".stat:hover { transform: translateY(-4px); }");
        out.println(".stat.pass { border-left: 4px solid #10b981; }");
        out.println(".stat.fail { border-left: 4px solid #ef4444; }");
        out.println(".stat.skip { border-left: 4px solid #f59e0b; }");
        out.println(".stat.total { border-left: 4px solid #667eea; }");
        out.println(".stat-number { font-size: 48px; font-weight: bold; margin-bottom: 8px; }");
        out.println(".stat.pass .stat-number { color: #10b981; }");
        out.println(".stat.fail .stat-number { color: #ef4444; }");
        out.println(".stat.skip .stat-number { color: #f59e0b; }");
        out.println(".stat.total .stat-number { color: #667eea; }");
        out.println(".stat-label { color: #666; font-size: 14px; font-weight: 500; }");
        out.println(".details { background: white; padding: 30px; border-radius: 12px;");
        out.println("          box-shadow: 0 4px 16px rgba(0,0,0,0.1); }");
        out.println(".details h2 { color: #333; margin-bottom: 20px; }");
        out.println(".info-row { display: flex; padding: 12px 0; border-bottom: 1px solid #eee; }");
        out.println(".info-row:last-child { border-bottom: none; }");
        out.println(".info-label { font-weight: 600; color: #555; min-width: 150px; }");
        out.println(".info-value { color: #333; }");
        out.println(".btn { display: inline-block; background: #667eea; color: white;");
        out.println("       padding: 12px 24px; border-radius: 8px; text-decoration: none;");
        out.println("       font-weight: 600; margin-top: 20px; transition: background 0.2s; }");
        out.println(".btn:hover { background: #5568d3; }");
        out.println(".footer { text-align: center; color: white; margin-top: 40px;");
        out.println("          font-size: 14px; opacity: 0.9; }");
        out.println("</style>");
        out.println("</head><body>");

        out.println("<div class='container'>");

        // Header
        out.println("<div class='header'>");
        out.println("<h1>🎨 Custom Test Results");
        out.println("<span class='badge'>CUSTOM UI</span></h1>");
        out.println("<p>Powered by SimpleHTMLUIProvider - Custom UI Extension Point Demo</p>");
        out.println("</div>");

        // Stats
        out.println("<div class='stats'>");

        out.println("<div class='stat total'>");
        out.println("<div class='stat-number'>" + testResult.getTotalCount() + "</div>");
        out.println("<div class='stat-label'>Total Tests</div>");
        out.println("</div>");

        out.println("<div class='stat pass'>");
        out.println("<div class='stat-number'>" + testResult.getPassCount() + "</div>");
        out.println("<div class='stat-label'>✓ Passed</div>");
        out.println("</div>");

        out.println("<div class='stat fail'>");
        out.println("<div class='stat-number'>" + testResult.getFailCount() + "</div>");
        out.println("<div class='stat-label'>✗ Failed</div>");
        out.println("</div>");

        out.println("<div class='stat skip'>");
        out.println("<div class='stat-number'>" + testResult.getSkipCount() + "</div>");
        out.println("<div class='stat-label'>○ Skipped</div>");
        out.println("</div>");

        out.println("</div>");

        // Details
        out.println("<div class='details'>");
        out.println("<h2>Test Details</h2>");

        out.println("<div class='info-row'>");
        out.println("<div class='info-label'>Test Suite:</div>");
        out.println("<div class='info-value'>" + testResult.getDisplayName() + "</div>");
        out.println("</div>");

        out.println("<div class='info-row'>");
        out.println("<div class='info-label'>Duration:</div>");
        out.println("<div class='info-value'>" + testResult.getDurationString() + "</div>");
        out.println("</div>");

        out.println("<div class='info-row'>");
        out.println("<div class='info-label'>Success Rate:</div>");
        double successRate = testResult.getTotalCount() > 0
                ? (testResult.getPassCount() * 100.0 / testResult.getTotalCount())
                : 0;
        out.println("<div class='info-value'>" + String.format("%.1f%%", successRate) + "</div>");
        out.println("</div>");

        out.println("<div class='info-row'>");
        out.println("<div class='info-label'>Build:</div>");
        out.println("<div class='info-value'>#" + testResult.getRun().getNumber() + "</div>");
        out.println("</div>");

        out.println("<a href='../../' class='btn'>← Back to Build</a>");
        out.println("</div>");

        out.println("<div class='footer'>");
        out.println("<p>This is a custom UI provided by the Custom UI Provider extension point.</p>");
        out.println("<p>You can create your own provider plugin to display test results however you like!</p>");
        out.println("</div>");

        out.println("</div>");
        out.println("</body></html>");
    }

    @Override
    public void renderCaseResultUI(CaseResult caseResult, StaplerRequest2 req, StaplerResponse2 rsp)
            throws IOException {
        // For individual test cases, just redirect back to parent for simplicity
        rsp.sendRedirect2("../..");
    }

    @Override
    public boolean isApplicable(Run<?, ?> run) {
        // This provider applies to all builds
        return true;
    }
}
