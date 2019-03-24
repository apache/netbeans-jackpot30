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
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.runner.Result;
import org.netbeans.junit.NbTestCase;

/**XXX: should also test error conditions
 *
 * @author lahvac
 */
public class MainTest extends NbTestCase {

    public MainTest(String name) {
        super(name);
    }

    public void testRunCompiler1() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b = c.isEmpty();\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      null,
                      null,
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b = c.size() == 0;\n" +
                      "    }\n" +
                      "}\n",
                      null,
                      "--apply",
                      "--hint",
                      "Usage of .size() == 0");
    }

    public void testDoNotApply() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b1 = c.size() == 0;\n" +
            "\tboolean b2 = c.size() == 0;\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      "${workdir}/src/test/Test.java:4: warning: [Usage_of_size_equals_0] Usage of .size() == 0 can be replaced with .isEmpty()\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "                     ^\n" +
                      "${workdir}/src/test/Test.java:5: warning: [Usage_of_size_equals_0] Usage of .size() == 0 can be replaced with .isEmpty()\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "\t             ^\n",
                      null,
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "\tboolean b2 = c.size() == 0;\n" +
                      "    }\n" +
                      "}\n",
                      null,
                      "--hint",
                      "Usage of .size() == 0",
                      "--no-apply");
    }

    public void testConfig() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private int test(String str) {\n" +
            "        if (\"a\" == str) {\n" +
            "            return 1;\n" +
            "        } else if (\"b\" == str) {\n" +
            "            return 2;\n" +
            "        } else {\n" +
            "            return 3;\n" +
            "        }\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      null,
                      null,
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private int test(String str) {\n" +
                      "        if (\"a\" == str) {\n" +
                      "            return 1;\n" +
                      "        } else if (\"b\" == str) {\n" +
                      "            return 2;\n" +
                      "        } else {\n" +
                      "            return 3;\n" +
                      "        }\n" +
                      "    }\n" +
                      "}\n",
                      null,
                      "--hint",
                      "Use switch over Strings where possible.",
                      "--config",
                      "also-equals=false");
    }

    public void testValidSourceLevel() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b = c.isEmpty();\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      null,
                      null,
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b = c.size() == 0;\n" +
                      "    }\n" +
                      "}\n",
                      null,
                      "--apply",
                      "--hint",
                      "Usage of .size() == 0",
                      "--source",
                      "1.6");
    }

    public void testConfigurationFile() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b = c.isEmpty();\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      null,
                      null,
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b = c.size() == 0;\n" +
                      "    }\n" +
                      "}\n",
                      "settings.xml",
                      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                      "<!DOCTYPE configuration PUBLIC \"-//NetBeans//DTD Tool Configuration 1.0//EN\" \"http://www.netbeans.org/dtds/ToolConfiguration-1_0.dtd\">\n" +
                      "<configuration>\n" +
                      "    <tool kind=\"hints\" type=\"text/x-java\">\n" +
                      "        <node name=\"org.netbeans.modules.java.hints.perf.SizeEqualsZero\">\n" +
                      "            <attribute name=\"enabled\" value=\"true\"/>\n" +
                      "        </node>\n" +
                      "    </tool>\n" +
                      "    <tool kind=\"standalone\" type=\"text/x-java\">\n" +
                      "        <attribute name=\"apply\" value=\"true\"/>\n" +
                      "    </tool>\n" +
                      "</configuration>\n",
                      null,
                      "--config-file",
                      "${workdir}/settings.xml",
                      "--source",
                      "1.6");
    }

    public void testConfigurationFileDisable() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b = c.size() == 0;\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      null,
                      null,
                      "src/test/Test.java",
                      golden,
                      "settings.xml",
                      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                      "<!DOCTYPE configuration PUBLIC \"-//NetBeans//DTD Tool Configuration 1.0//EN\" \"http://www.netbeans.org/dtds/ToolConfiguration-1_0.dtd\">\n" +
                      "<configuration>\n" +
                      "    <tool kind=\"hints\" type=\"text/x-java\">\n" +
                      "        <node name=\"org.netbeans.modules.java.hints.perf.SizeEqualsZero\">\n" +
                      "            <attribute name=\"enabled\" value=\"false\"/>\n" +
                      "        </node>\n" +
                      "    </tool>\n" +
                      "    <tool kind=\"standalone\" type=\"text/x-java\">\n" +
                      "        <attribute name=\"apply\" value=\"true\"/>\n" +
                      "    </tool>\n" +
                      "</configuration>\n",
                      null,
                      "--config-file",
                      "${workdir}/settings.xml",
                      "--source",
                      "1.6");
    }

    public void testConfigurationFileCmdLineOverride() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b = c.size() == 0;\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      "${workdir}/src/test/Test.java:4: warning: [Usage_of_size_equals_0] Usage of .size() == 0 can be replaced with .isEmpty()\n" +
                      "        boolean b = c.size() == 0;\n" +
                      "                    ^\n",
                      null,
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b = c.size() == 0;\n" +
                      "    }\n" +
                      "}\n",
                      "settings.xml",
                      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                      "<!DOCTYPE configuration PUBLIC \"-//NetBeans//DTD Tool Configuration 1.0//EN\" \"http://www.netbeans.org/dtds/ToolConfiguration-1_0.dtd\">\n" +
                      "<configuration>\n" +
                      "    <tool kind=\"hints\" type=\"text/x-java\">\n" +
                      "        <node name=\"org.netbeans.modules.java.hints.perf.SizeEqualsZero\">\n" +
                      "            <attribute name=\"enabled\" value=\"true\"/>\n" +
                      "        </node>\n" +
                      "    </tool>\n" +
                      "    <tool kind=\"standalone\" type=\"text/x-java\">\n" +
                      "        <attribute name=\"apply\" value=\"true\"/>\n" +
                      "    </tool>\n" +
                      "</configuration>\n",
                      null,
                      "--config-file",
                      "${workdir}/settings.xml",
                      "--source",
                      "1.6",
                      "--no-apply");
    }
    
    public void testHintFile() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b = c.size() == 0;\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      "",
                      null,
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b = c.isEmpty();\n" +
                      "    }\n" +
                      "}\n",
                      "test-rule.hint",
                      "$var.isEmpty() => $var.size() == 0;;",
                      null,
                      "--hint-file",
                      "${workdir}/test-rule.hint",
                      "--source",
                      "1.6",
                      "--apply");
    }

    public void testConfigurationFileDeclarative1() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b1 = c.isEmpty();\n" +
            "        boolean b2 = c.size() <= 0;\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      null,
                      null,
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "        boolean b2 = c.size() <= 0;\n" +
                      "    }\n" +
                      "}\n",
                      "META-INF/upgrade/test1.hint",
                      "$c.size() == 0 :: $c instanceof java.util.Collection => $c.isEmpty();;\n",
                      "META-INF/upgrade/test2.hint",
                      "$c.size() <= 0 :: $c instanceof java.util.Collection => $c.isEmpty();;\n",
                      "settings.xml",
                      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                      "<!DOCTYPE configuration PUBLIC \"-//NetBeans//DTD Tool Configuration 1.0//EN\" \"http://www.netbeans.org/dtds/ToolConfiguration-1_0.dtd\">\n" +
                      "<configuration>\n" +
                      "    <tool kind=\"hints\" type=\"text/x-java\">\n" +
                      "        <node name=\"org.netbeans.modules.java.hints.perf.SizeEqualsZero\">\n" +
                      "            <attribute name=\"enabled\" value=\"false\"/>\n" +
                      "        </node>\n" +
                      "        <node name=\"test1.hint\">\n" +
                      "            <attribute name=\"enabled\" value=\"true\"/>\n" +
                      "        </node>\n" +
                      "    </tool>\n" +
                      "    <tool kind=\"standalone\" type=\"text/x-java\">\n" +
                      "        <attribute name=\"apply\" value=\"true\"/>\n" +
                      "        <attribute name=\"runDeclarative\" value=\"false\"/>\n" +
                      "    </tool>\n" +
                      "</configuration>\n",
                      null,
                      "--config-file",
                      "${workdir}/settings.xml",
                      "--source",
                      "1.6");
    }

    public void testConfigurationFileDeclarative1a() throws Exception {
        String code =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b1 = c.size() == 0;\n" +
            "        boolean b2 = c.size() <= 0;\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(code,
                      "${workdir}/src/test/Test.java:4: warning: [test1] test1\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "                     ^\n",
                      null,
                      "src/test/Test.java",
                      code,
                      "META-INF/upgrade/test1.hint",
                      "$c.size() == 0 :: $c instanceof java.util.Collection => $c.isEmpty();;\n",
                      "META-INF/upgrade/test2.hint",
                      "$c.size() <= 0 :: $c instanceof java.util.Collection => $c.isEmpty();;\n",
                      "settings.xml",
                      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                      "<!DOCTYPE configuration PUBLIC \"-//NetBeans//DTD Tool Configuration 1.0//EN\" \"http://www.netbeans.org/dtds/ToolConfiguration-1_0.dtd\">\n" +
                      "<configuration>\n" +
                      "    <tool kind=\"hints\" type=\"text/x-java\">\n" +
                      "        <node name=\"org.netbeans.modules.java.hints.perf.SizeEqualsZero\">\n" +
                      "            <attribute name=\"enabled\" value=\"false\"/>\n" +
                      "        </node>\n" +
                      "        <node name=\"test1.hint\">\n" +
                      "            <attribute name=\"enabled\" value=\"true\"/>\n" +
                      "        </node>\n" +
                      "    </tool>\n" +
                      "    <tool kind=\"standalone\" type=\"text/x-java\">\n" +
                      "        <attribute name=\"apply\" value=\"false\"/>\n" +
                      "        <attribute name=\"runDeclarative\" value=\"false\"/>\n" +
                      "    </tool>\n" +
                      "</configuration>\n",
                      null,
                      "--config-file",
                      "${workdir}/settings.xml",
                      "--source",
                      "1.6");
    }

    public void testConfigurationFileDeclarative2() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b1 = c.isEmpty();\n" +
            "        boolean b2 = c.isEmpty();\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      null,
                      null,
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "        boolean b2 = c.size() <= 0;\n" +
                      "    }\n" +
                      "}\n",
                      "META-INF/upgrade/test1.hint",
                      "$c.size() == 0 :: $c instanceof java.util.Collection => $c.isEmpty();;\n",
                      "META-INF/upgrade/test2.hint",
                      "$c.size() <= 0 :: $c instanceof java.util.Collection => $c.isEmpty();;\n",
                      "settings.xml",
                      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                      "<!DOCTYPE configuration PUBLIC \"-//NetBeans//DTD Tool Configuration 1.0//EN\" \"http://www.netbeans.org/dtds/ToolConfiguration-1_0.dtd\">\n" +
                      "<configuration>\n" +
                      "    <tool kind=\"hints\" type=\"text/x-java\">\n" +
                      "        <node name=\"org.netbeans.modules.java.hints.perf.SizeEqualsZero\">\n" +
                      "            <attribute name=\"enabled\" value=\"false\"/>\n" +
                      "        </node>\n" +
                      "        <node name=\"test1.hint\">\n" +
                      "            <attribute name=\"enabled\" value=\"true\"/>\n" +
                      "        </node>\n" +
                      "    </tool>\n" +
                      "    <tool kind=\"standalone\" type=\"text/x-java\">\n" +
                      "        <attribute name=\"apply\" value=\"true\"/>\n" +
                      "        <attribute name=\"runDeclarative\" value=\"true\"/>\n" +
                      "    </tool>\n" +
                      "</configuration>\n",
                      null,
                      "--config-file",
                      "${workdir}/settings.xml",
                      "--source",
                      "1.6");
    }

    public void testSourcePath() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test() {\n" +
            "        String s = test2.Test2.C;\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      null,
                      null,
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test() {\n" +
                      "        String s = test2.Test2.C.intern();\n" +
                      "    }\n" +
                      "}\n",
                      "src/test2/Test2.java",
                      "package test2;\n" +
                      "public class Test2 {\n" +
                      "    public static final String C = \"a\";\n" +
                      "}\n",
                      null,
                      DONT_APPEND_PATH,
                      "--apply",
                      "--hint",
                      "String.intern() called on constant",
                      "--sourcepath",
                      "${workdir}/src",
                      "${workdir}/src/test");
    }

    public void testWarningsAreErrors() throws Exception {
        String code =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b1 = c.size() == 0;\n" +
            "\tboolean b2 = c.size() == 0;\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(equivalentValidator(code),
                      equivalentValidator(
                          "${workdir}/src/test/Test.java:4: warning: [Usage_of_size_equals_0] Usage of .size() == 0 can be replaced with .isEmpty()\n" +
                          "        boolean b1 = c.size() == 0;\n" +
                          "                     ^\n" +
                          "${workdir}/src/test/Test.java:5: warning: [Usage_of_size_equals_0] Usage of .size() == 0 can be replaced with .isEmpty()\n" +
                          "\tboolean b2 = c.size() == 0;\n" +
                          "\t             ^\n"
                      ),
                      equivalentValidator(null),
                      1,
                      "src/test/Test.java",
                      code,
                      null,
                      "--hint",
                      "Usage of .size() == 0",
                      "--no-apply",
                      "--fail-on-warnings");
    }

    public void testGroups() throws Exception {
        doRunCompiler(null,
                      "${workdir}/src1/test/Test.java:4: warning: [test] test\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "                     ^\n" +
                      "${workdir}/src2/test/Test.java:5: warning: [test] test\n" +
                      "        boolean b2 = c.size() != 0;\n" +
                      "                     ^\n",
                      null,
                      "cp1/META-INF/upgrade/test.hint",
                      "$coll.size() == 0 :: $coll instanceof java.util.Collection;;",
                      "src1/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "        boolean b2 = c.size() != 0;\n" +
                      "    }\n" +
                      "}\n",
                      "cp2/META-INF/upgrade/test.hint",
                      "$coll.size() != 0 :: $coll instanceof java.util.Collection;;",
                      "src2/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "        boolean b2 = c.size() != 0;\n" +
                      "    }\n" +
                      "}\n",
                      null,
                      DONT_APPEND_PATH,
                      "--group",
                      "--classpath ${workdir}/cp1 ${workdir}/src1",
                      "--group",
                      "--classpath ${workdir}/cp2 ${workdir}/src2");
    }

    public void testGroupsList() throws Exception {
        doRunCompiler(null,
                      new Validator() {
                          @Override public void validate(String content) {
                              assertTrue("Missing expected content, actual content: " + content, content.contains("test\n"));
                          }
                      },
                      null,
                      "cp1/META-INF/upgrade/test.hint",
                      "$coll.size() == 0 :: $coll instanceof java.util.Collection;;",
                      "src1/test/Test.java",
                      "\n",
                      "cp2/META-INF/upgrade/test.hint",
                      "$coll.size() != 0 :: $coll instanceof java.util.Collection;;",
                      "src2/test/Test.java",
                      "\n",
                      null,
                      DONT_APPEND_PATH,
                      "--group",
                      "--classpath ${workdir}/cp1 ${workdir}/src1",
                      "--group",
                      "--classpath ${workdir}/cp2 ${workdir}/src2",
                      "--list");
    }

    public void testNoHintsFoundWithGroups() throws Exception {
        doRunCompiler("package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.isEmpty();\n" +
                      "        boolean b2 = c.size() != 0;\n" +
                      "    }\n" +
                      "}\n",
                      "",
                      null,
                      "cp/META-INF/upgrade/test.hint",
                      "$coll.size() == 0 :: $coll instanceof java.util.Collection\n" +
                      "=>\n" +
                      "$coll.isEmpty()\n" +
                      ";;",
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "        boolean b2 = c.size() != 0;\n" +
                      "    }\n" +
                      "}\n",
                      "src2/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b1 = c.size() == 0;\n" +
                      "        boolean b2 = c.size() != 0;\n" +
                      "    }\n" +
                      "}\n",
                      null,
                      DONT_APPEND_PATH,
                      "--group",
                      "--classpath ${workdir}/cp ${workdir}/src",
                      "--group",
                      "${workdir}/src2",
                      "--apply");
    }

    public void testAutomaticTestRun() throws Exception {
        if (System.getProperty("sun.boot.class.path") != null) {
            //TODO XXX: this test does not pass on JDK 8
            return ;
        }
        class Config {
            private final String commandLineOption;
            private final int result;
            private final Validator validator;

            public Config(String commandLineOption, int result, Validator validator) {
                this.commandLineOption = commandLineOption;
                this.result = result;
                this.validator = validator;
            }

        }
        Validator testValidator =
                equivalentValidator("${workdir}/src/META-INF/upgrade/test.test:15: error: [test_failure] Actual results did not match the expected test results. Actual results: []\n" +
                                    "%%TestCase pos-neg\n" +
                                    "^\n" +
                                    "${workdir}/src/META-INF/upgrade/test.test:29: error: [test_failure] Actual results did not match the expected test results. Actual results: [package test;\n" +
                                    "public class Test {{\n" +
                                    "    private void test(java.util.Collection c) {\n" +
                                    "        boolean b1 = c.isEmpty();\n" +
                                    "    }\n" +
                                    "}}\n" +
                                    "]\n" +
                                    "%%TestCase neg-neg\n" +
                                    "^\n" +
                                    "${workdir}/src/test/Test.java:4: warning: [test] test\n" +
                                    "        boolean b1 = c.size() == 0;\n" +
                                    "                     ^\n");
        List<? extends Config> options =
                Arrays.asList(new Config("--run-tests", 1, testValidator),
                              new Config(IGNORE, 0, equivalentValidator("${workdir}/src/test/Test.java:4: warning: [test] test\n" +
                                                                        "        boolean b1 = c.size() == 0;\n" +
                                                                        "                     ^\n")));
        for (Config testConfig : options) {
            doRunCompiler(equivalentValidator("package test;\n" +
                                              "public class Test {\n" +
                                              "    private void test(java.util.Collection c) {\n" +
                                              "        boolean b1 = c.size() == 0;\n" +
                                              "        boolean b2 = c.size() != 0;\n" +
                                              "    }\n" +
                                              "}\n"),
                          testConfig.validator,
                          equivalentValidator(""),
                          testConfig.result,
                          "src/META-INF/upgrade/test.hint",
                          "$coll.size() == 0 :: $coll instanceof java.util.Collection\n" +
                          "=>\n" +
                          "$coll.isEmpty()\n" +
                          ";;",
                          "src/META-INF/upgrade/test.test",
                          "%%TestCase pos-pos\n" +
                          "package test;\n" +
                          "public class Test {{\n" +
                          "    private void test(java.util.Collection c) {\n" +
                          "        boolean b1 = c.size() == 0;\n" +
                          "    }\n" +
                          "}}\n" +
                          "%%=>\n" +
                          "package test;\n" +
                          "public class Test {{\n" +
                          "    private void test(java.util.Collection c) {\n" +
                          "        boolean b1 = c.isEmpty();\n" +
                          "    }\n" +
                          "}}\n" +
                          "%%TestCase pos-neg\n" +
                          "package test;\n" +
                          "public class Test {{\n" +
                          "    private void test(java.util.Collection c) {\n" +
                          "        boolean b1 = c.size() == 0;\n" +
                          "    }\n" +
                          "}}\n" +
                          "%%TestCase neg-pos\n" +
                          "package test;\n" +
                          "public class Test {{\n" +
                          "    private void test(java.util.Collection c) {\n" +
                          "        boolean b1 = c.size() != 0;\n" +
                          "    }\n" +
                          "}}\n" +
                          "%%TestCase neg-neg\n" +
                          "package test;\n" +
                          "public class Test {{\n" +
                          "    private void test(java.util.Collection c) {\n" +
                          "        boolean b1 = c.size() != 0;\n" +
                          "    }\n" +
                          "}}\n" +
                          "%%=>\n" +
                          "package test;\n" +
                          "public class Test {{\n" +
                          "    private void test(java.util.Collection c) {\n" +
                          "        boolean b1 = c.isEmpty();\n" +
                          "    }\n" +
                          "}}\n",
                          "src/test/Test.java",
                          "package test;\n" +
                          "public class Test {\n" +
                          "    private void test(java.util.Collection c) {\n" +
                          "        boolean b1 = c.size() == 0;\n" +
                          "        boolean b2 = c.size() != 0;\n" +
                          "    }\n" +
                          "}\n",
                          null,
                          testConfig.commandLineOption,
                          "--sourcepath", "${workdir}/src");
        }
    }

    public void testLambda() throws Exception {
        doRunCompiler(null,
                      equivalentValidator("${workdir}/src/test/Test.java:4: warning: [test] test\n" +
                                          "        Runnable r = () -> {};\n" +
                                          "                     ^\n" +
                                          "${workdir}/src/test/Test.java:5: warning: [test] test\n" +
                                          "        I1 i1 = (str1, str2) -> {};\n" +
                                          "                ^\n" +
                                          "${workdir}/src/test/Test.java:6: warning: [test] test\n" +
                                          "        I2 i2 = (str1, str2) -> str1;\n" +
                                          "                ^\n" +
                                          "${workdir}/src/test/Test.java:7: warning: [test] test\n" +
                                          "        I3 i3 = str -> { return str; };\n" +
                                          "                ^\n"),
                      equivalentValidator(""),
                      "src/META-INF/upgrade/test.hint",
                      "($args$) -> $expr" +
                      ";;",
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test() {\n" +
                      "        Runnable r = () -> {};\n" +
                      "        I1 i1 = (str1, str2) -> {};\n" +
                      "        I2 i2 = (str1, str2) -> str1;\n" +
                      "        I3 i3 = str -> { return str; };\n" +
                      "    }\n" +
                      "    interface I1 {\n" +
                      "        public void test(String str1, String str2);\n" +
                      "    }\n" +
                      "    interface I2 {\n" +
                      "        public String test(String str1, String str2);\n" +
                      "    }\n" +
                      "    interface I3 {\n" +
                      "        public String test(String str);\n" +
                      "    }\n" +
                      "}\n",
                      null,
                      "-source", "8",
                      "--sourcepath", "${workdir}/src");
    }

    public void testMethodRef() throws Exception {
        doRunCompiler(null,
                      equivalentValidator("${workdir}/src/test/Test.java:4: warning: [test] test\n" +
                                          "        Runnable r = Test::m1;\n" +
                                          "                     ^\n" +
                                          "${workdir}/src/test/Test.java:5: warning: [test] test\n" +
                                          "        I1 i1 = this::m2;\n" +
                                          "                ^\n" +
                                          "${workdir}/src/test/Test.java:6: warning: [test] test\n" +
                                          "        I2 i2 = String::replace;\n" +
                                          "                ^\n" +
                                          "${workdir}/src/test/Test.java:7: warning: [test] test\n" +
                                          "        I3 i3a = String::toLowerCase;\n" +
                                          "                 ^\n" +
                                          "${workdir}/src/test/Test.java:8: warning: [test] test\n" +
                                          "        I3 i3b = String::new;\n" +
                                          "                 ^\n"),
                      equivalentValidator(""),
                      "src/META-INF/upgrade/test.hint",
                      "$clazz \\u003a\\u003a $member;;" +
                      "$clazz \\u003a\\u003a new;;",
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test() {\n" +
                      "        Runnable r = Test::m1;\n" +
                      "        I1 i1 = this::m2;\n" +
                      "        I2 i2 = String::replace;\n" +
                      "        I3 i3a = String::toLowerCase;\n" +
                      "        I3 i3b = String::new;\n" +
                      "    }\n" +
                      "    interface I1 {\n" +
                      "        public void test(String str1, String str2);\n" +
                      "    }\n" +
                      "    interface I2 {\n" +
                      "        public String test(String str1, String str2);\n" +
                      "    }\n" +
                      "    interface I3 {\n" +
                      "        public String test(String str);\n" +
                      "    }\n" +
                      "    private static void m1() { }\n" +
                      "    private void m2(String str1, String str2) { }\n" +
                      "}\n",
                      null,
                      "-source", "8",
                      "--sourcepath", "${workdir}/src");
    }

    public void testGroupsParamEscape() throws Exception {
        assertEquals(Arrays.asList("a b", "a\\b"),
                     Arrays.asList(Main.splitGroupArg("a\\ b a\\\\b")));
    }

    public void testSourceLevelMatches1() throws Exception {
        runSourceLevelMatches("1.8",
                              "${workdir}/src/test/Test.java:4: warning: [Convert_to_Lambda_or_Member_Reference] This anonymous inner class creation can be turned into a lambda expression.\n" +
                              "        Runnable r = new Runnable() { public void run() { } };\n" +
                              "                         ^\n");
    }

    public void testSourceLevelMatches2() throws Exception {
        runSourceLevelMatches("1.7",
                              "");
    }

    private void runSourceLevelMatches(String sourceLevel, String expectedOutput) throws Exception {
        String code = "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        Runnable r = new Runnable() { public void run() { } };\n" +
                      "    }\n" +
                      "}\n";

        doRunCompiler(code,
                      expectedOutput,
                      null,
                      "src/test/Test.java",
                      code,
                      null,
                      "--hint",
                      "Convert to Lambda or Member Reference",
                      "--source",
                      sourceLevel);
    }

    public void testParameterFile() throws Exception {
        String golden =
            "package test;\n" +
            "public class Test {\n" +
            "    private void test(java.util.Collection c) {\n" +
            "        boolean b = c.isEmpty();\n" +
            "    }\n" +
            "}\n";

        doRunCompiler(golden,
                      null,
                      null,
                      "src/test/Test.java",
                      "package test;\n" +
                      "public class Test {\n" +
                      "    private void test(java.util.Collection c) {\n" +
                      "        boolean b = c.size() == 0;\n" +
                      "    }\n" +
                      "}\n",
                      "parameters.txt",
                      "--apply\n" +
                      "--hint\n" +
                      "Usage of .size() == 0\n",
                      null,
                      "@" + getWorkDirPath() + "/parameters.txt");
    }

    private static final String DONT_APPEND_PATH = new String("DONT_APPEND_PATH");
    private static final String IGNORE = new String("IGNORE");

    private void doRunCompiler(String golden, String stdOut, String stdErr, String... fileContentAndExtraOptions) throws Exception {
        doRunCompiler(equivalentValidator(golden), equivalentValidator(stdOut), equivalentValidator(stdErr), fileContentAndExtraOptions);
    }

    private void doRunCompiler(Validator fileContentValidator, Validator stdOutValidator, Validator stdErrValidator, String... fileContentAndExtraOptions) throws Exception {
        doRunCompiler(fileContentValidator, stdOutValidator, stdErrValidator, 0, fileContentAndExtraOptions);
    }

    private void doRunCompiler(Validator fileContentValidator, Validator stdOutValidator, Validator stdErrValidator, int exitcode, String... fileContentAndExtraOptions) throws Exception {
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

        for (int cntr = 0; cntr < fileAndContent.size(); cntr += 2) {
            File target = new File(getWorkDir(), fileAndContent.get(cntr));

            target.getParentFile().mkdirs();
            
            Utils.copyStringToFile(target, fileAndContent.get(cntr + 1));
        }

        File wd = getWorkDir();
        File source = new File(wd, "src/test/Test.java");

        List<String> options = new LinkedList<String>();
        boolean appendPath = true;

        File cache = new File(getWorkDir(), "cache");

        options.add("--cache");
        options.add(cache.getAbsolutePath());

        for (String extraOption : extraOptions) {
            if (extraOption == DONT_APPEND_PATH) {
                appendPath = false;
                continue;
            }
            if (extraOption == IGNORE) {
                continue;
            }
            options.add(extraOption.replace("${workdir}", wd.getAbsolutePath()));
        }

        if (appendPath)
            options.add(wd.getAbsolutePath());

        String[] output = new String[2];

        reallyRunCompiler(wd, exitcode, output, options.toArray(new String[0]));

        if (fileContentValidator != null) {
            fileContentValidator.validate(Utils.copyFileToString(source));
        }
        if (stdOutValidator != null) {
            stdOutValidator.validate(output[0].replaceAll(Pattern.quote(wd.getAbsolutePath()), Matcher.quoteReplacement("${workdir}")));
        }
        if (stdErrValidator != null) {
            stdErrValidator.validate(output[1].replaceAll(Pattern.quote(wd.getAbsolutePath()), Matcher.quoteReplacement("${workdir}")));
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
        }
    }

    //verify that the DeclarativeHintsTestBase works:
    private static final String CODE_RUN_DECLARATIVE =
            "package org.netbeans.modules.jackpot30.cmdline.testtool;\n" +
            "\n" +
            "import junit.framework.TestSuite;\n" +
            "import org.netbeans.modules.java.hints.declarative.test.api.DeclarativeHintsTestBase;\n" +
            "\n" +
            "public class DoRunTests extends DeclarativeHintsTestBase {\n" +
            "\n" +
            "    public static TestSuite suite() {\n" +
            "        return suite(DoRunTests.class);\n" +
            "    }\n" +
            "\n" +
            "}\n";
    public void testRunTest() throws Exception {
        clearWorkDir();

        File wd = getWorkDir();
        File classes = new File(wd, "classes");

        classes.mkdirs();
        Utils.copyStringToFile(new File(classes, "h.hint"), "$1.equals(\"\") :: $1 instanceof java.lang.String => $1.isEmpty();;");

        String test = "%%TestCase pos\n" +
                      "package test;\n" +
                      "public class Test {{\n" +
                      " System.err.println(\"a\".equals(\"\"));\n" +
                      "}}\n" +
                      "%%=>\n" +
                      "package test;\n" +
                      "public class Test {{\n" +
                      " System.err.println(\"a\".isEmpty());\n" +
                      "}}\n" +
                      "%%TestCase neg\n" +
                      "package test;\n" +
                      "public class Test {{\n" +
                      " System.err.println(\"a\".equals(\"a\"));\n" +
                      "}}\n" +
                      "%%=>\n" +
                      "package test;\n" +
                      "public class Test {{\n" +
                      " System.err.println(\"a\".isEmpty());\n" +
                      "}}\n";
        Utils.copyStringToFile(new File(classes, "h.test"), test);

        List<String> options = Arrays.asList("-d", classes.getAbsolutePath(), "-source", "8", "-target", "8");
        List<SourceFO> files = Arrays.asList(new SourceFO("DoRunTests.java", CODE_RUN_DECLARATIVE));

        assertTrue(ToolProvider.getSystemJavaCompiler().getTask(null, null, null, options, null, files).call());

        runAndTest(classes);
    }

    @Override
    protected void setUp() throws Exception {
        clearWorkDir();
        super.setUp();
    }

    private static final class SourceFO extends SimpleJavaFileObject {

        private final String code;
        private SourceFO(String name, String code) throws URISyntaxException {
            super(new URI("mem:///" + name), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return code;
        }

    }

    protected void runAndTest(File classes) throws Exception {
        ClassLoader cl = new URLClassLoader(new URL[] {classes.toURI().toURL()}, MainTest.class.getClassLoader());
        Class<?> doRunTests = Class.forName("org.netbeans.modules.jackpot30.cmdline.testtool.DoRunTests", true, cl);
        Result testResult = org.junit.runner.JUnitCore.runClasses(doRunTests);

        assertEquals(1, testResult.getFailureCount());
        assertTrue(testResult.getFailures().toString(), testResult.getFailures().get(0).getDescription().getMethodName().endsWith("/h.test/neg"));
    }

    private static Validator equivalentValidator(final String expected) {
        if (expected == null) return null;

        return new Validator() {
            @Override public void validate(String content) {
                assertEquals(expected, content);
            }
        };
    }

    private static interface Validator {
        public void validate(String content);
    }
}