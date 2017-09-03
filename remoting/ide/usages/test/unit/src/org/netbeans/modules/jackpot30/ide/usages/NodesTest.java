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
