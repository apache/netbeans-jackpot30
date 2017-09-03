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

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.modules.jackpot30.indexing.index.IndexQuery;
import org.netbeans.modules.java.hints.declarative.Hacks;
import org.netbeans.modules.java.hints.spiimpl.pm.BulkSearch;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author lahvac
 */
public class IndexingAnnotationProcessorTest extends HintsAnnotationProcessingTestBase {
    public IndexingAnnotationProcessorTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        cache = new File(workDir, "cache");
        CacheFolder.setCacheFolder(FileUtil.createFolder(cache));
    }

    private File cache;

    public void testSimpleIndexing() throws Exception {
        runCompiler("src/test/Test1.java",
                    "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); } }",
                    "src/test/Test2.java",
                    "package test; public class Test2 { private void test() { new javax.swing.ImageIcon((byte[]) null); } }",
                    null,
                    "-A" + IndexingAnnotationProcessor.CACHE_ROOT + "=" + cache.getAbsolutePath());

        String[] patterns = new String[]{
            "$1.isDirectory()",
            "new ImageIcon($1)"
        };

        verifyIndex(patterns, "test/Test1.java", "test/Test2.java");
    }

    public void testNoSourcePath() throws Exception {
        runCompiler("src/test/Test1.java",
                    "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); } }",
                    "src/test/Test2.java",
                    "package test; public class Test2 { private void test() { new javax.swing.ImageIcon((byte[]) null); } }",
                    null,
                    "-A" + IndexingAnnotationProcessor.CACHE_ROOT + "=" + cache.getAbsolutePath(),
                    "-sourcepath",
                    "");

        String[] patterns = new String[]{
            "$1.isDirectory()",
            "new ImageIcon($1)"
        };

        verifyIndex(patterns, "test/Test1.java", "test/Test2.java");
    }

    private void verifyIndex(final String[] patterns, String... containedIn) throws Exception {
        ClasspathInfo cpInfo = Hacks.createUniversalCPInfo();
        final Set<String> real = new HashSet<String>();

        JavaSource.create(cpInfo).runUserActionTask(new Task<CompilationController>() {
            public void run(CompilationController parameter) throws Exception {
                real.addAll(IndexQuery.open(src.toURI().toURL()).findCandidates(BulkSearch.getDefault().create(parameter, new AtomicBoolean(), patterns)));
            }
        }, true);

        Set<String> golden = new HashSet<String>(Arrays.asList(containedIn));

        assertEquals(golden, real);
    }
}
