/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009-2010 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2009-2010 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.compiler;

import java.io.File;
import java.util.Collections;
import javax.lang.model.type.TypeMirror;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.java.hints.ErrorDescriptionFactory;
import org.netbeans.spi.java.hints.Hint;
import org.netbeans.spi.java.hints.HintContext;
import org.netbeans.spi.java.hints.JavaFix;
import org.netbeans.spi.java.hints.JavaFixUtilities;
import org.netbeans.spi.java.hints.TriggerPattern;

/**
 *
 * @author lahvac
 */
public class HintsAnnotationProcessingTest extends HintsAnnotationProcessingTestBase {

    public HintsAnnotationProcessingTest(String name) {
        super(name);
    }

    public void testRunCompiler1() throws Exception {
        String golden =
                "--- {0}/src/test/Test.java\n" +
                "+++ {0}/src/test/Test.java\n" +
                "@@ -1 +1 @@\n" +
                "-package test; public class Test {private void test(java.io.File f) {f.isDirectory();}}\n" +
                "+package test; public class Test {private void test(java.io.File f) {!f.isFile();}}\n";

        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {private void test(java.io.File f) {f.isDirectory();}}\n",
                              "src/META-INF/upgrade/joFile.hint",
                              "'test':\n$1.isDirectory() :: $1 instanceof java.io.File => !$1.isFile();;",
                              null,
                              "-A" + HintsAnnotationProcessing.CLASSPATH_HINTS_ENABLE + "=true",
                              "-A" + HintsAnnotationProcessing.CLASSPATH_HINTS_FIXES_ENABLE + "=true");
    }

    public void testRunCompiler2() throws Exception {
        String golden =
                "--- {0}/src/test/Test.java\n" +
                "+++ {0}/src/test/Test.java\n" +
                "@@ -1 +1 @@\n" +
                "-package test; public class Test {private void test() {Character.toLowerCase('a');}}\n" +
                "+package test; public class Test {private void test() {Character.toUpperCase('a');}}\n";

        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {private void test() {Character.toLowerCase('a');}}\n",
                              "src/META-INF/upgrade/joFile.hint",
                              "'test':\njava.lang.Character.toLowerCase($1) :: $1 instanceof char => java.lang.Character.toUpperCase($1) ;;",
                              null,
                              "-A" + HintsAnnotationProcessing.CLASSPATH_HINTS_ENABLE + "=true",
                              "-A" + HintsAnnotationProcessing.CLASSPATH_HINTS_FIXES_ENABLE + "=true");
    }

    public void testRunCompilerMulti() throws Exception {
        String golden =
                "--- {0}/src/test/Test.java\n" +
                "+++ {0}/src/test/Test.java\n" +
                "@@ -1 +1 @@\n" +
                "-package test; public class Test {private void test() {Character.toLowerCase('a'); Dep.test();}}\n" +
                "+package test; public class Test {private void test() {Character.toUpperCase('a'); Dep.test();}}\n" +
                "--- {0}/src/test/Dep.java\n" +
                "+++ {0}/src/test/Dep.java\n" +
                "@@ -1 +1 @@\n" +
                "-package test; public class Dep {static void test() {Character.toLowerCase('a');}}\n" +
                "+package test; public class Dep {static void test() {Character.toUpperCase('a');}}\n";

        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {private void test() {Character.toLowerCase('a'); Dep.test();}}\n",
                              "src/test/Dep.java",
                              "package test; public class Dep {static void test() {Character.toLowerCase('a');}}\n",
                              "src/META-INF/upgrade/joFile.hint",
                              "'test':\njava.lang.Character.toLowerCase($1) :: $1 instanceof char => java.lang.Character.toUpperCase($1) ;;",
                              null,
                              "-A" + HintsAnnotationProcessing.CLASSPATH_HINTS_ENABLE + "=true",
                              "-A" + HintsAnnotationProcessing.CLASSPATH_HINTS_FIXES_ENABLE + "=true");
    }

//    public void testNPEFromAttribute() throws Exception {//TODO: does not reproduce the problem - likely caused by null Env<AttrContext> for annonymous innerclasses
//        String golden = null;
//
//        doRunCompiler(golden, "src/test/Test.java",
//                              "package test; public class Test {private void test() {new Runnable() {public void run() {int i = 0; System.err.println(i);}};}}\n",
//                              "src/META-INF/upgrade/joFile.hint",
//                              "'test':\njava.lang.Character.toLowerCase($1) :: $1 instanceof char => java.lang.Character.toUpperCase($1) ;;");
//    }

    public void testTreesCleaning1() throws Exception {
        String golden = null;

        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {private void test() {java.util.Collections.<String>emptyList();}}\n",
                              "src/META-INF/upgrade/joFile.hint",
                              "'test':\njava.lang.Character.toLowerCase($1) :: $1 instanceof char => java.lang.Character.toUpperCase($1) ;;");
    }

    public void testTreesCleaning2() throws Exception {
        String golden = null;

        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {enum A { B; A() {}} }\n",
                              "src/META-INF/upgrade/joFile.hint",
                              "'test':\njava.lang.Character.toLowerCase($1) :: $1 instanceof char => java.lang.Character.toUpperCase($1) ;;");
    }

    public void testTreesCleaningEnumTooMuch() throws Exception {
        String golden = null;

        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {enum A { B; private final int i; A() {this(1);} A(int i) {this.i = i;}} }\n",
                              "src/META-INF/upgrade/joFile.hint",
                              "'test':\njava.lang.Character.toLowerCase($1) :: $1 instanceof char => java.lang.Character.toUpperCase($1) ;;");
    }

    public void testTreesCleaningEnum3() throws Exception {
        String golden = null;

        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {enum A { B(\"a\"){public String toString() {return null;} }; A(String str) {}} }\n",
                              "src/META-INF/upgrade/joFile.hint",
                              "'test':\njava.lang.Character.toLowerCase($1) :: $1 instanceof char => java.lang.Character.toUpperCase($1) ;;");
    }

    public void testCRTable() throws Exception {
        String golden = null;

        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {private void test() {Integer i = 0; i++;}}\n",
                              "src/META-INF/upgrade/joFile.hint",
                              "'test':\njava.lang.Character.toLowerCase($1) :: $1 instanceof char => java.lang.Character.toUpperCase($1) ;;");
    }

    public void testCodeAPI() throws Exception {
        String golden = "--- {0}/src/test/Test.java\n"+
                        "+++ {0}/src/test/Test.java\n"+
                        "@@ -1,2 +1,2 @@\n"+
                        "-package test; public class Test {private void test() {Integer i = 0; if (i == null && null == i) System.err.println(i);\n"+
                        "+package test; public class Test {private void test() {Integer i = 0; if (i == null) System.err.println(i);\n"+
                        " }}\n";
        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {private void test() {Integer i = 0; if (i == null && null == i) System.err.println(i);\n}}\n",
                              null,
                              "-A" + HintsAnnotationProcessing.HARDCODED_HINTS_ENABLE + "=test-hint");
    }

    public void testExtraHints() throws Exception {
        String golden =
                "--- {0}/src/test/Test.java\n" +
                "+++ {0}/src/test/Test.java\n" +
                "@@ -1 +1 @@\n" +
                "-package test; public class Test {private void test() {Character.toLowerCase('a');}}\n" +
                "+package test; public class Test {private void test() {Character.toUpperCase('a');}}\n";

        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {private void test() {Character.toLowerCase('a');}}\n",
                              "extra.hint",
                              "'test':\njava.lang.Character.toLowerCase($1) :: $1 instanceof char => java.lang.Character.toUpperCase($1) ;;",
                              null,
                              "-A" + HintsAnnotationProcessing.EXTRA_HINTS + "=extra.hint");

    }
    public void testHintsOnClassPath() throws Exception {
        String golden =
                "--- {0}/src/test/Test.java\n" +
                "+++ {0}/src/test/Test.java\n" +
                "@@ -1 +1 @@\n" +
                "-package test; public class Test {private void test() {Character.toLowerCase('a');}}\n" +
                "+package test; public class Test {private void test() {Character.toUpperCase('a');}}\n";

        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {private void test() {Character.toLowerCase('a');}}\n",
                              "comp/META-INF/upgrade/joFile.hint",
                              "'test':\njava.lang.Character.toLowerCase($1) :: $1 instanceof char => java.lang.Character.toUpperCase($1) ;;",
                              null,
                              "-classpath",
                              "comp",
                              "-A" + HintsAnnotationProcessing.CLASSPATH_HINTS_ENABLE + "=true",
                              "-A" + HintsAnnotationProcessing.CLASSPATH_HINTS_FIXES_ENABLE + "=true");
    }

    public void testNoDebugInfo() throws Exception {
        String golden =
                "--- {0}/src/test/Test.java\n" +
                "+++ {0}/src/test/Test.java\n" +
                "@@ -1 +1 @@\n" +
                "-package test; public class Test {private void test(java.io.File f) {f.isDirectory();}}\n" +
                "+package test; public class Test {private void test(java.io.File f) {!f.isFile();}}\n";

        doRunCompiler(golden, "src/test/Test.java",
                              "package test; public class Test {private void test(java.io.File f) {f.isDirectory();}}\n",
                              "src/META-INF/upgrade/joFile.hint",
                              "'test':\n$1.isDirectory() :: $1 instanceof java.io.File => !$1.isFile();;",
                              null,
                              "-g:none",
                              "-A" + HintsAnnotationProcessing.CLASSPATH_HINTS_ENABLE + "=true",
                              "-A" + HintsAnnotationProcessing.CLASSPATH_HINTS_FIXES_ENABLE + "=true");
    }

    private void doRunCompiler(String goldenDiff, String... fileContentAndExtraOptions) throws Exception {
        runCompiler(fileContentAndExtraOptions);

        File diff = new File(sourceOutput, "META-INF/upgrade/upgrade.diff");
        String diffText = readFully(diff);

        goldenDiff = goldenDiff != null ? goldenDiff.replace("{0}", workDir.getAbsolutePath()) : null;
        assertEquals(goldenDiff, diffText);
    }

    @Hint(displayName="test", description="test", category="general", id="test-hint")
    @TriggerPattern("$1 == null && null == $1")
    public static ErrorDescription codeHint(HintContext ctx) {
        return ErrorDescriptionFactory.forName(ctx, ctx.getPath(), "test", JavaFixUtilities.rewriteFix(ctx, "test", ctx.getPath(), "$1 == null"));
    }

}
