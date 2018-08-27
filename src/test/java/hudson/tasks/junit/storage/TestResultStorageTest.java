/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

package hudson.tasks.junit.storage;

import hudson.model.Label;
import hudson.model.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import org.jenkinsci.plugins.database.GlobalDatabaseConfiguration;
import org.jenkinsci.plugins.database.h2.LocalH2Database;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class TestResultStorageTest {
    
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    
    @Rule public JenkinsRule r = new JenkinsRule();

    /** Need {@link LocalH2Database#getAutoServer} so that {@link Impl} can make connections from an agent JVM. */
    @Before public void autoServer() throws Exception {
        LocalH2Database database = (LocalH2Database) GlobalDatabaseConfiguration.get().getDatabase();
        GlobalDatabaseConfiguration.get().setDatabase(new LocalH2Database(database.getPath(), true));
    }

    @Ignore("TODO")
    @Test public void smokes() throws Exception {
        r.createSlave(Label.get("remote"));
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node('remote') {\n" +
                "  writeFile file: 'x.xml', text: '''<testsuite name='sweet'><testcase classname='Klazz' name='test'><error message='failure'/></testcase></testsuite>'''\n" +
                "  junit 'x.xml'\n" +
                "}", true));
        r.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));
        // TODO verify that there is no junitResult.xml on disk
        // TODO query TestResultAction in various ways
        try (Connection connection = GlobalDatabaseConfiguration.get().getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM INFORMATION_SCHEMA.TABLES");
                ResultSet result = statement.executeQuery()) {
            printResultSet(result);
        }
        // TODO dump, then later verify, DB structure
        fail("TODO");
    }

    // https://gist.github.com/mikbuch/299568988fa7997cb28c7c84309232b1
    private static void printResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnsNumber = rsmd.getColumnCount();
        for (int i = 1; i <= columnsNumber; i++) {
            if (i > 1) {
                System.out.print(" | ");
            }
            System.out.print(rsmd.getColumnName(i));
        }
        System.out.println();
        while (rs.next()) {
            for (int i = 1; i <= columnsNumber; i++) {
                if (i > 1) {
                    System.out.print(" | ");
                }
                System.out.print(rs.getString(i));
            }
            System.out.println();
        }
    }

    @TestExtension public static class Impl implements TestResultStorage {

        // TODO use XStream to remote the [LocalH2]Database

    }

}
