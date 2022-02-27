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
package org.netbeans.modules.jackpot30.cmdline.processor;

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
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.jackpot30.cmdline.lib.TestUtils;

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
                      "${workdir}/src/test/Test.java:4: warning: [Usage_of_Collection_Map_size_equals_0] c.size() == 0 can be replaced with c.isEmpty()\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "                      ^\n" +
                      "${workdir}/src/test/Test.java:5: warning: [Usage_of_Collection_Map_size_equals_0] c.size() == 0 can be replaced with c.isEmpty()\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "\t              ^\n" +
                      "2 warnings\n",
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    public boolean test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "        return b1 || b2;\n" +
                      "    }\n" +
                      "}\n",
                      null,
                      "-source",
                      "7",
                      "-Xlint:-options");
    }

    public void testHardcodedWithConfiguration() throws Exception {
        doRunCompiler("",
                      "${workdir}/src/test/Test.java:4: warning: [Usage_of_Collection_Map_size_equals_0] c.size() == 0 can be replaced with c.isEmpty()\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "                      ^\n" +
                      "${workdir}/src/test/Test.java:5: warning: [Usage_of_Collection_Map_size_equals_0] c.size() == 0 can be replaced with c.isEmpty()\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "\t              ^\n" +
                      "2 warnings\n",
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    public boolean test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "        return b1 || b2;\n" +
                      "    }\n" +
                      "}\n",
                      "cfg_hints.xml",
                      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                      "<!DOCTYPE configuration PUBLIC \"-//NetBeans//DTD Tool Configuration 1.0//EN\" \"http://www.netbeans.org/dtds/ToolConfiguration-1_0.dtd\">\n" +
                      "<configuration>\n" +
                      "    <tool kind=\"hints\" type=\"text/x-java\">\n" +
                      "        <node name=\"org.netbeans.modules.java.hints.jdk.ConvertToVarHint\">\n" +
                      "            <attribute name=\"enabled\" value=\"false\"/>\n" +
                      "        </node>\n" +
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
                      "    public boolean test(Test c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "        return b1 || b2;\n" +
                      "    }\n" +
                      "    public int size() { return 0; }\n" +
                      "}\n",
                      "src/META-INF/upgrade/test.hint",
                      "$coll.size() == 0 :: $coll instanceof test.Test;;\n",
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
                      "    public boolean test(Test c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "        return b1 || b2;\n" +
                      "    }\n" +
                      "    public int size() { return 0; }\n" +
                      "}\n",
                      "src/META-INF/upgrade/test.hint",
                      "$coll.size() == 0 :: $coll instanceof test.Test;;\n",
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

        params.add("-processor");
        params.add(ProcessorImpl.class.getName());

        for (int cntr = 0; cntr < fileAndContent.size(); cntr += 2) {
            File target = new File(getWorkDir(), fileAndContent.get(cntr));

            target.getParentFile().mkdirs();

            TestUtils.copyStringToFile(target, fileAndContent.get(cntr + 1));

            if (target.getName().endsWith(".java"))
                params.add(target.getAbsolutePath());
        }

        params.addAll(extraOptions);

        File wd = getWorkDir();
        String[] output = new String[2];
        reallyRunCompiler(wd, 0, output, params.toArray(new String[0]));

        if (stdOut != null) {
            assertEquals(stdOut, output[0].replaceAll(Pattern.quote(wd.getAbsolutePath()), Matcher.quoteReplacement("${workdir}")));
        }

        if (stdErr != null) {
            assertEquals(stdErr, output[1].replaceAll(Pattern.quote(wd.getAbsolutePath()), Matcher.quoteReplacement("${workdir}")));
        }
    }

    protected void reallyRunCompiler(File workDir, int exitcode, String[] output, String... params) throws Exception {
        String oldUserDir = System.getProperty("user.dir");

        System.setProperty("user.dir", workDir.getAbsolutePath());

        PrintStream oldOut = System.out;
        ByteArrayOutputStream outData = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outData, true, "UTF-8"));

        PrintStream oldErr = System.err;
        ByteArrayOutputStream errData = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errData, true, "UTF-8"));

        try {
            assertEquals(exitcode, Main.compile(params));
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
