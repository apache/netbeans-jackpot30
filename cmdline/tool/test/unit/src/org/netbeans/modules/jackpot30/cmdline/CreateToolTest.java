/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010-2011 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2010-2011 Sun Microsystems, Inc.
 */

package org.netbeans.modules.jackpot30.cmdline;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        ll.add("java");
        ll.add("-Xbootclasspath/p:" + compiler.getAbsolutePath());

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
        ll.add("java");
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
                compiler.delete();
                hintsList.delete();
                compiler = null;
                hintsList = null;
            }
        };
    }

}
