/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.jackpot30.maven;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 *
 * @author lahvac
 */
public class RunJackpot30Test extends TestCase {

    public static TestSuite suite() {
        TestSuite result = new TestSuite();
        String baseDir = System.getProperty("basedir");
        File testsDir = new File(baseDir, "tests");
        File[] tests = testsDir.listFiles();

        assertNotNull(tests);

        for (File test : tests) {
            if (new File(test, "golden").canRead() && new File(test, "pom.xml").canRead()) {
                result.addTest(new RunJackpot30Test(test.getName(), test));
            }
        }
        
        return result;
    }

    private final File testDir;

    public RunJackpot30Test(String testName, File testDir) {
        super(testName);
        this.testDir = testDir;
    }

    @Override
    protected void runTest() throws Throwable {
        String maven = System.getProperty("maven.executable");

        assertNotNull(maven);

        Process p = Runtime.getRuntime().exec(new String[] {
            maven,
            "-Djackpot.plugin.version=12.4",
            "-q",
            "jackpot30:analyze"
        }, null, testDir);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread outCopy = new Thread(new CopyStream(p.getInputStream(), System.out, out));
        Thread errCopy = new Thread(new CopyStream(p.getErrorStream(), System.err));

        outCopy.start();
        errCopy.start();
        
        p.waitFor();

        outCopy.join();
        errCopy.join();

        out.close();

        String output = new String(out.toByteArray());
        Reader in = new InputStreamReader(new FileInputStream(new File(testDir, "golden")), "UTF-8");
        StringBuilder golden = new StringBuilder();

        try {
            int read;

            while ((read = in.read()) != (-1)) {
                golden.append((char) read);
            }

            assertEquals(golden.toString().replace("${basedir}", testDir.getAbsolutePath()), output);
        } finally {
            in.close();
        }
    }

    private static final class CopyStream implements Runnable {
        private final InputStream from;
        private final OutputStream[] to;

        public CopyStream(InputStream from, OutputStream... to) {
            this.from = from;
            this.to = to;
        }

        public void run() {
            try {
                int read;

                while ((read = from.read()) != (-1)) {
                    for (OutputStream out : to) {
                        out.write(read);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

    }
}
