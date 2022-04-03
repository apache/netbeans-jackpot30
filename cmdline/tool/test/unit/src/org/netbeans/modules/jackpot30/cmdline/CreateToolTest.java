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

package org.netbeans.modules.jackpot30.cmdline;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import junit.extensions.TestSetup;
import junit.framework.Test;
import org.hamcrest.Condition;
import org.junit.runner.JUnitCore;
import org.netbeans.junit.NbTestSuite;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class CreateToolTest extends MainTest {

    public CreateToolTest(String name) {
        super(name);
    }

    private static File compiler;

    @Override
    protected void reallyRunCompiler(File workingDir, int exitcode, String[] output, String... params) throws Exception {
        assertNotNull(compiler);
        List<String> ll = new LinkedList<String>();
        ll.addAll(Utils.findJavaLauncher());
        ll.add("-classpath"); ll.add(compiler.getAbsolutePath());

//        ll.add("-Xdebug");
//        ll.add("-Xrunjdwp:transport=dt_socket,suspend=y,server=y,address=8889");

        ll.add("org.netbeans.modules.jackpot30.cmdline.Main");
        ll.addAll(Arrays.asList(params));
        try {
            Process p = Runtime.getRuntime().exec(ll.toArray(new String[0]), null, workingDir);
            CopyStream outCopy = new CopyStream(p.getInputStream(), output, 0);
            CopyStream errCopy = new CopyStream(p.getErrorStream(), output, 1);

            outCopy.start();
            errCopy.start();

            assertEquals(exitcode, p.waitFor());

            outCopy.doJoin();
            errCopy.doJoin();
        } catch (Throwable t) {
            throw new IOException(t);
        }
    }
    
    private static final Set<String> MUST_CONTAIN = new HashSet<String>(Arrays.asList(" 1 failure", "/h.test/neg", "Tests run: 2,  Failures: 1"));
    private static final Set<String> MUST_NOT_CONTAIN = new HashSet<String>(Arrays.asList("/h.test/pos"));

    @Override
    protected void runAndTest(File classes) throws Exception {
        List<String> ll = new LinkedList<String>();
        ll.addAll(Utils.findJavaLauncher());
        ll.add("-classpath");
        ll.add(compiler.getAbsolutePath() + File.pathSeparator + classes.getAbsolutePath() + File.pathSeparator + new File(JUnitCore.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath() + File.pathSeparator + new File(Condition.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath());

//        ll.add("-Xdebug");
//        ll.add("-Xrunjdwp:transport=dt_socket,suspend=y,server=y,address=8889");

        ll.add("org.junit.runner.JUnitCore");
        ll.add("org.netbeans.modules.jackpot30.cmdline.testtool.DoRunTests");

        System.err.println("ll=" + ll);
        Process p = Runtime.getRuntime().exec(ll.toArray(new String[0]), null, getWorkDir());
        String[] output = new String[2];
        CopyStream outCopy = new CopyStream(p.getInputStream(), output, 0);
        CopyStream errCopy = new CopyStream(p.getErrorStream(), output, 1);

        outCopy.start();
        errCopy.start();

        p.waitFor();

        outCopy.doJoin();
        errCopy.doJoin();

        Set<String> mustContainCopy = new HashSet<String>(MUST_CONTAIN);

        System.err.println(output[0]);
        System.err.println(output[1]);

        verify(output[0], mustContainCopy);
        verify(output[1], mustContainCopy);
    }

    private void verify(String output, Set<String> mustContainCopy) {
        for (Iterator<String> it = mustContainCopy.iterator(); it.hasNext();) {
            assertTrue(output.contains(it.next()));
            it.remove();
        }
        for (String nc : MUST_NOT_CONTAIN) {
            assertFalse(output.contains(nc));
        }
    }

    private static final class CopyStream extends Thread {
        private final InputStream ins;
        private final ByteArrayOutputStream out;
        private final String[] target;
        private final int targetIndex;

        public CopyStream(InputStream ins, String[] target, int targetIndex) {
            this.ins = ins;
            this.out = new ByteArrayOutputStream();
            this.target = target;
            this.targetIndex = targetIndex;
        }

        @Override
        public void run() {
            try {
                FileUtil.copy(ins, out);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                try {
                    ins.close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

        public void doJoin() throws InterruptedException, IOException {
            join(60000);
            out.close();
            target[targetIndex] = new String(out.toByteArray());
        }

    }

    public static Test suite() {
        NbTestSuite suite = new NbTestSuite();
        
        suite.addTestSuite(CreateToolTest.class);

        return new TestSetup(suite) {
            private File hintsList;
            protected void setUp() throws Exception {
                compiler = File.createTempFile("jackpot", ".jar");
//                compiler = new File("/tmp/jackpot.jar");
                hintsList = File.createTempFile("hints", "list");

//                if (!compiler.canRead()) {
                    new CreateTool("").createCompiler(compiler, hintsList);
//                }
            }
            protected void tearDown() {
//                compiler.delete();
                hintsList.delete();
                compiler = null;
                hintsList = null;
            }
        };
    }

}
