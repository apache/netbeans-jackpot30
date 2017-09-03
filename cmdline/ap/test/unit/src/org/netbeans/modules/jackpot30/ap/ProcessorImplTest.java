/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2015 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
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
 * Portions Copyrighted 2015 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.ap;

import com.sun.tools.javac.Main;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import org.netbeans.api.java.source.TestUtilities;
import org.netbeans.junit.NbTestCase;

/**
 *
 * @author lahvac
 */
public class ProcessorImplTest extends NbTestCase {

    public ProcessorImplTest(String name) {
        super(name);
    }

    public void testHardcoded1() throws Exception {
        doRunCompiler("",
                      "",
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "    }\n" +
                      "}\n",
                      null,
                      "-source",
                      "7",
                      "-Xlint:-options");
    }

    public void testHardcodedWithConfiguration() throws Exception {
        doRunCompiler("",
                      "${workdir}/src/test/Test.java:4: warning: [Usage_of_size_equals_0] Usage of .size() == 0 can be replaced with .isEmpty()\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "                      ^\n" +
                      "${workdir}/src/test/Test.java:5: warning: [Usage_of_size_equals_0] Usage of .size() == 0 can be replaced with .isEmpty()\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "\t              ^\n" +
                      "2 warnings\n",
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "    }\n" +
                      "}\n",
                      "cfg_hints.xml",
                      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                      "<!DOCTYPE configuration PUBLIC \"-//NetBeans//DTD Tool Configuration 1.0//EN\" \"http://www.netbeans.org/dtds/ToolConfiguration-1_0.dtd\">\n" +
                      "<configuration>\n" +
                      "    <tool kind=\"hints\" type=\"text/x-java\">\n" +
                      "    </tool>\n" +
                      "</configuration>\n",
                      null,
                      "-source",
                      "7",
                      "-Xlint:-options",
                      "-A" + ProcessorImpl.CONFIGURATION_OPTION + "=" + new File(getWorkDir(), "cfg_hints.xml").getAbsolutePath());
    }

    public void testCustomWithSourcePath() throws Exception {
        doRunCompiler("",
                      "${workdir}/src/test/Test.java:4: warning: [test] test\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "                      ^\n" +
                      "${workdir}/src/test/Test.java:5: warning: [test] test\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "\t              ^\n" +
                      "2 warnings\n",
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "    }\n" +
                      "}\n",
                      "src/META-INF/upgrade/test.hint",
                      "$coll.size() == 0 :: $coll instanceof java.util.Collection;;\n",
                      null,
                      "-source",
                      "7",
                      "-Xlint:-options",
                      "-sourcepath",
                      new File(getWorkDir(), "src").getAbsolutePath());
   }

    public void testCustomWithOutSourcePath() throws Exception {
        doRunCompiler("",
                      "${workdir}/src/test/Test.java:4: warning: [test] test\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "                      ^\n" +
                      "${workdir}/src/test/Test.java:5: warning: [test] test\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "\t              ^\n" +
                      "2 warnings\n",
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "    }\n" +
                      "}\n",
                      "src/META-INF/upgrade/test.hint",
                      "$coll.size() == 0 :: $coll instanceof java.util.Collection;;\n",
                      null,
                      "-source",
                      "7",
                      "-Xlint:-options");
   }

    private void doRunCompiler(String stdOut, String stdErr, String... fileContentAndExtraOptions) throws Exception {
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

        clearWorkDir();

        List<String> params = new ArrayList<>();

        for (int cntr = 0; cntr < fileAndContent.size(); cntr += 2) {
            File target = new File(getWorkDir(), fileAndContent.get(cntr));

            target.getParentFile().mkdirs();

            TestUtilities.copyStringToFile(target, fileAndContent.get(cntr + 1));

            if (target.getName().endsWith(".java"))
                params.add(target.getAbsolutePath());
        }

        params.addAll(extraOptions);

        File wd = getWorkDir();
        String[] output = new String[2];
        reallyRunCompiler(wd, output, params.toArray(new String[0]));

        if (stdOut != null) {
            assertEquals(stdOut, output[0].replaceAll(Pattern.quote(wd.getAbsolutePath()), Matcher.quoteReplacement("${workdir}")));
        }

        if (stdErr != null) {
            assertEquals(stdErr, output[1].replaceAll(Pattern.quote(wd.getAbsolutePath()), Matcher.quoteReplacement("${workdir}")));
        }
    }

    protected void reallyRunCompiler(File workDir, String[] output, String... params) throws Exception {
        String oldUserDir = System.getProperty("user.dir");

        System.setProperty("user.dir", workDir.getAbsolutePath());

        PrintStream oldOut = System.out;
        ByteArrayOutputStream outData = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outData, true, "UTF-8"));

        PrintStream oldErr = System.err;
        ByteArrayOutputStream errData = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errData, true, "UTF-8"));

        try {
            assertEquals(0, Main.compile(params));
        } finally {
            System.setProperty("user.dir", oldUserDir);
            System.out.close();
            output[0] = new String(outData.toByteArray(), "UTF-8");
            System.setOut(oldOut);
            System.err.close();
            output[1] = new String(errData.toByteArray(), "UTF-8");
            System.setErr(oldErr);

            System.err.println("stdout: " + output[0]);
            System.err.println("stderr: " + output[1]);
        }
    }
}
