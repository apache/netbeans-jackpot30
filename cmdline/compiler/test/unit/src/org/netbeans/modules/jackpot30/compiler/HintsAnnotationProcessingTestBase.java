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
package org.netbeans.modules.jackpot30.compiler;

import com.sun.tools.javac.Main;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class HintsAnnotationProcessingTestBase extends NbTestCase {

    public HintsAnnotationProcessingTestBase(String name) {
        super(name);
    }

    protected File workDir;
    protected File src;
    protected File sourceOutput;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        clearWorkDir();

        workDir = getWorkDir();
        sourceOutput = new File(workDir, "src-out");
        sourceOutput.mkdirs();
        src = new File(workDir, "src");
        src.mkdirs();
    }

    protected void runCompiler(String... fileContentAndExtraOptions) throws Exception {
        List<String> fileAndContent = new LinkedList<String>();
        List<String> extraOptions = new LinkedList<String>();
        List<String> fileContentAndExtraOptionsList = Arrays.asList(fileContentAndExtraOptions);
        int nullPos = fileContentAndExtraOptionsList.indexOf(null);

        if (nullPos == (-1)) {
            fileAndContent = fileContentAndExtraOptionsList;
            extraOptions = Collections.emptyList();
        } else {
            fileAndContent = fileContentAndExtraOptionsList.subList(0, nullPos);
            extraOptions = fileContentAndExtraOptionsList.subList(nullPos + 1, fileContentAndExtraOptionsList.size());
        }

        assertTrue(fileAndContent.size() % 2 == 0);

        List<String> options = new LinkedList<String>();

        for (int cntr = 0; cntr < fileAndContent.size(); cntr += 2) {
            String file = createAndFill(fileAndContent.get(cntr), fileAndContent.get(cntr + 1)).getAbsolutePath();

            if (file.endsWith(".java")) {
                options.add(file);
            }
        }

        if (!extraOptions.contains("-sourcepath")) {
            options.add("-sourcepath");
            options.add(src.getAbsolutePath());
        }
        
        options.add("-s");
        options.add(sourceOutput.getAbsolutePath());
        options.add("-source");
        options.add("1.7");
        options.add("-Xjcov");

        for (String eo : extraOptions) {
            options.add(eo.replace("${workdir}", workDir.getAbsolutePath()));
        }

        reallyRunCompiler(workDir, options.toArray(new String[0]));
    }

    protected void reallyRunCompiler(File workDir, String... params) throws Exception {
        String javacJar = System.getProperty("test.javacJar");

        if (javacJar == null) {
            String oldUserDir = System.getProperty("user.dir");

            System.setProperty("user.dir", workDir.getAbsolutePath());

            try {
                assertEquals(0, Main.compile(params));
            } finally {
                System.setProperty("user.dir", oldUserDir);
            }
        } else {
            File compiler = new File(javacJar);

            assertTrue(compiler.exists());

            List<String> ll = new LinkedList<String>();

            ll.add("java");
//            ll.add("-Xdebug");
//            ll.add("-Xrunjdwp:transport=dt_socket,suspend=y,server=y,address=8889");
            ll.add("-Xbootclasspath/p:" + compiler.getAbsolutePath());
            ll.add("com.sun.tools.javac.Main");
            ll.addAll(Arrays.asList(params));

            try {
                Process p = Runtime.getRuntime().exec(ll.toArray(new String[0]), null, workDir);

                new CopyStream(p.getInputStream(), System.out).start();
                new CopyStream(p.getErrorStream(), System.err).start();

                assertEquals(0, p.waitFor());
            } catch (Throwable t) {
                throw new IOException(t);
            }
        }
    }

    private static final class CopyStream extends Thread {
        private final InputStream ins;
        private final OutputStream out;

        public CopyStream(InputStream ins, OutputStream out) {
            this.ins = ins;
            this.out = out;
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

    }

    private File createAndFill(String path, String content) throws IOException {
        File source = new File(workDir, path);

        source.getParentFile().mkdirs();
        
        Writer out = new OutputStreamWriter(new FileOutputStream(source));

        out.write(content);

        out.close();

        return source;
    }

    protected static String readFully(File file) throws IOException {
        if (!file.canRead()) return null;
        StringBuilder res = new StringBuilder();
        Reader in = new InputStreamReader(new FileInputStream(file));
        int read;
        
        while ((read = in.read()) != (-1)) {
            res.append((char) read);
        }

        return res.toString();
    }

}
