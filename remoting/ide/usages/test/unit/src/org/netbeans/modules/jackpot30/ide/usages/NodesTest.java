/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2011 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.ide.usages;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.SourceUtilsTestUtil;
import org.netbeans.api.java.source.TestUtilities;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.nodes.Node;

/**
 *
 * @author lahvac
 */
public class NodesTest extends NbTestCase {

    public NodesTest(String testName) {
        super(testName);
    }

    public void testA() throws Exception {
        FileObject data = FileUtil.createData(src, "test/Test.java");
        TestUtilities.copyStringToFile(data, "package test;\n" +
                                             "public class Test {\n" +
                                             "    public Test () {}\n" +
                                             "    private void test() { new Test(); }\n" +
                                             "}\n");
        CompilationInfo info = SourceUtilsTestUtil.getCompilationInfo(JavaSource.forFileObject(data), Phase.RESOLVED);
        ElementHandle<?> eh = ElementHandle.create(info.getTopLevelElements().get(0).getEnclosedElements().get(0));
        List<Node> constructed = new ArrayList<Node>();

        Nodes.computeOccurrences(data, eh, EnumSet.of(RemoteUsages.SearchOptions.USAGES), constructed);

        Node n = constructed.get(0);

        assertEquals("    private void test() { new <b>Test</b>(); }<br>", n.getHtmlDisplayName());
    }

    public void testMethodImplementations() throws Exception {
        FileObject data = FileUtil.createData(src, "test/Test.java");
        TestUtilities.copyStringToFile(data, "package test;\n" +
                                             "public class Test implements Runnable {\n" +
                                             "    public void run() {\n" +
                                             "         Runnable r = null;\n" +
                                             "         r.run();\n" +
                                             "    }\n" +
                                             "}\n");
        CompilationInfo info = SourceUtilsTestUtil.getCompilationInfo(JavaSource.forFileObject(data), Phase.RESOLVED);
        TypeElement runnable = info.getElements().getTypeElement("java.lang.Runnable");

        assertNotNull(runnable);

        ElementHandle<?> eh = ElementHandle.create(runnable.getEnclosedElements().get(0));
        List<Node> constructed = new ArrayList<Node>();

        Nodes.computeOccurrences(data, eh, EnumSet.of(RemoteUsages.SearchOptions.SUB), constructed);

        assertEquals(1, constructed.size());
        
        Node n = constructed.get(0);

        assertEquals("    public void <b>run</b>() {<br>", n.getHtmlDisplayName());
    }

    public void testSubtypes() throws Exception {
        FileObject data = FileUtil.createData(src, "test/Test.java");
        TestUtilities.copyStringToFile(data, "package test;\n" +
                                             "public class Test implements Runnable {\n" +
                                             "    public void run() {\n" +
                                             "         Runnable r = null;\n" +
                                             "         r.run();\n" +
                                             "    }\n" +
                                             "}\n");
        CompilationInfo info = SourceUtilsTestUtil.getCompilationInfo(JavaSource.forFileObject(data), Phase.RESOLVED);
        TypeElement runnable = info.getElements().getTypeElement("java.lang.Runnable");

        assertNotNull(runnable);

        ElementHandle<?> eh = ElementHandle.create(runnable);
        List<Node> constructed = new ArrayList<Node>();

        Nodes.computeOccurrences(data, eh, EnumSet.of(RemoteUsages.SearchOptions.SUB), constructed);

        assertEquals(1, constructed.size());

        Node n = constructed.get(0);

        assertEquals("public class <b>Test</b> implements Runnable {<br>", n.getHtmlDisplayName());
    }

    @Override
    protected void setUp() throws Exception {
        SourceUtilsTestUtil.prepareTest(new String[0], new Object[0]);
        prepareTest();
        super.setUp();
    }

    private FileObject src;

    private void prepareTest() throws Exception {
        FileObject workdir = SourceUtilsTestUtil.makeScratchDir(this);

        src = FileUtil.createFolder(workdir, "src");

        FileObject cache = FileUtil.createFolder(workdir, "cache");

        CacheFolder.setCacheFolder(cache);
    }
}
