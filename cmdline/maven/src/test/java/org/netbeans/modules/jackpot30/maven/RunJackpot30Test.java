/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2012 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2012 Sun Microsystems, Inc.
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
