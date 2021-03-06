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

package org.openide.util.test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import junit.framework.Assert;

/**
 * Utilities useful to those testing JSR 269 annotation processors.
 * <p>If you just want to test that the output of the processor is correct,
 * you do not need to do anything special:
 * just use the annotation on some sample classes nested inside your unit test.
 * They will be processed, and you check that your SPI loads them correctly.
 * These utilities are useful mainly in case you want to check that the processor
 * rejects erroneous sources, and that any messages it prints are reasonable;
 * that it behaves correctly on incremental compilations; etc.
 */
public class AnnotationProcessorTestUtils {

    private AnnotationProcessorTestUtils() {}

    /**
     * Create a source file.
     * @param dir source root
     * @param clazz a fully-qualified class name
     * @param content lines of text (skip package decl)
     */
    public static void makeSource(File dir, String clazz, String... content) throws IOException {
        File f = new File(dir, clazz.replace('.', File.separatorChar) + ".java");
        f.getParentFile().mkdirs();
        Writer w = new FileWriter(f);
        try {
            PrintWriter pw = new PrintWriter(w);
            String pkg = clazz.replaceFirst("\\.[^.]+$", "");
            if (!pkg.equals(clazz)) {
                pw.println("package " + pkg + ";");
            }
            for (String line : content) {
                pw.println(line);
            }
            pw.flush();
        } finally {
            w.close();
        }
    }

    /**
     * Run the Java compiler.
     * (A JSR 199 implementation must be available.)
     * @param src a source root (runs javac on all *.java it finds matching {@code srcIncludes})
     * @param srcIncludes a pattern of source files names without path to compile (useful for testing incremental compiles), or null for all
     * @param dest a dest dir to compile classes to
     * @param cp classpath entries; if null, use Java classpath of test
     * @param stderr output stream to print messages to, or null for test console (i.e. do not capture)
     * @return true if compilation succeeded, false if it failed
     */
    public static boolean runJavac(File src, String srcIncludes, File dest, File[] cp, OutputStream stderr) {
        List<String> args = new ArrayList<String>();
        args.add("-classpath");
        if (cp != null) {
            StringBuffer b = new StringBuffer();
            for (File entry : cp) {
                b.append(File.pathSeparatorChar);
                b.append(entry.getAbsolutePath());
            }
            args.add(b.toString());
        } else {
            args.add(System.getProperty("java.class.path"));
        }
        args.add("-d");
        args.add(dest.getAbsolutePath());
        args.add("-sourcepath");
        args.add(src.getAbsolutePath());
        dest.mkdirs();
        scan(args, src, srcIncludes);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assert.assertNotNull("no JSR 199 compiler impl found; try e.g.: " +
                "test.unit.run.cp.extra=${nb_all}/apisupport.harness/external/openjdk-javac-6-b12.jar", compiler);
        //System.err.println("running javac with args: " + args);
        return compiler.run(null, null, stderr, args.toArray(new String[args.size()])) == 0;
    }
    private static void scan(List<String> names, File f, String includes) {
        if (f.isDirectory()) {
            for (File kid : f.listFiles()) {
                scan(names, kid, includes);
            }
        } else if (f.getName().endsWith(".java") && (includes == null || Pattern.compile(includes).matcher(f.getName()).find())) {
            names.add(f.getAbsolutePath());
        }
    }

}
