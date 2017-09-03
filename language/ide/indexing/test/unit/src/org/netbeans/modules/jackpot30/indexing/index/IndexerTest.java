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

package org.netbeans.modules.jackpot30.indexing.index;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.SourceUtilsTestUtil;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.TestUtilities;
import org.netbeans.modules.jackpot30.common.test.IndexTestBase;
import org.netbeans.modules.java.hints.providers.spi.HintDescription.AdditionalQueryConstraints;
import org.netbeans.modules.java.hints.spiimpl.Utilities;
import org.netbeans.modules.java.hints.spiimpl.pm.BulkSearch;
import org.netbeans.modules.java.hints.spiimpl.pm.BulkSearch.BulkPattern;
import org.netbeans.modules.parsing.api.indexing.IndexingManager;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;


/**
 *
 * @author lahvac
 */
public class IndexerTest extends IndexTestBase {

    public IndexerTest(String name) {
        super(name);
    }

    public void testMultiplePatternsIndexing() throws Exception {
        writeFilesAndWaitForScan(src,
                                 new File("test/Test1.java", "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); } }"),
                                 new File("test/Test2.java", "package test; public class Test2 { private void test() { new javax.swing.ImageIcon(null); } }"));

        String[] patterns = new String[] {
            "$1.isDirectory()",
            "new ImageIcon($1)"
        };

        verifyIndex(patterns, "test/Test1.java", "test/Test2.java");
    }

    public void testLambdaPattern() throws Exception {
        writeFilesAndWaitForScan(src,
                                 new File("test/Test1.java", "package test; public class Test1 { private void test() { Runnable r = new Runnable() { public void run() { System.err.println(); } } } }"));

        String[] patterns = new String[] {
            "new $type() {\n $mods$ $resultType $methodName($args$) {\n $statements$;\n }\n }\n",
        };

        verifyIndex(patterns, "test/Test1.java");
    }

    public void testUpdates() throws Exception {
        String[] patterns = new String[] {
            "$1.isDirectory()",
            "new ImageIcon($1)"
        };

        writeFilesAndWaitForScan(src,
                                 new File("test/Test1.java", "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); } }"),
                                 new File("test/Test2.java", "package test; public class Test2 { private void test() { new javax.swing.ImageIcon(null); } }"));

        verifyIndex(patterns, "test/Test1.java", "test/Test2.java");
//        assertEquals(2, FileBasedIndex.get(src.getURL()).getIndexInfo().totalFiles);

        src.getFileObject("test/Test1.java").delete();

        assertNull(src.getFileObject("test/Test1.java"));

        IndexingManager.getDefault().refreshIndexAndWait(src.toURL(), null, true);

        verifyIndex(patterns, "test/Test2.java");
//        assertEquals(1, FileBasedIndex.get(src.getURL()).getIndexInfo().totalFiles);

        FileObject test3 = FileUtil.createData(src, "test/Test3.java");
        TestUtilities.copyStringToFile(test3, "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); } }");

        IndexingManager.getDefault().refreshIndexAndWait(src.toURL(), null, true);

        verifyIndex(patterns, "test/Test2.java", "test/Test3.java");
//        assertEquals(2, FileBasedIndex.get(src.getURL()).getIndexInfo().totalFiles);
    }

    public void testPartiallyAttributed1() throws Exception {
        writeFilesAndWaitForScan(src,
                   new File("test/Test1.java", "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); } }"),
                   new File("test/Test2.java", "package test; public class Test2 { private void isDirectory() { this.isDirectory(); } }"),
                   new File("test/Test3.java", "package test; public class Test3 { private void isDirectory() { this.isDirectory(); } }"));

        verifyIndex("$1.isDirectory()", new AdditionalQueryConstraints(Collections.singleton("java.io.File")), "test/Test1.java");
    }

    public void testPartiallyAttributed2() throws Exception {
        writeFilesAndWaitForScan(src,
                   new File("test/Test1.java", "package test; public class Test1 { private void test() { String str = null; int l = str.length(); } }"));

        verifyIndex("$1.length()", new AdditionalQueryConstraints(Collections.singleton("java.lang.CharSequence")), "test/Test1.java");
    }

    public void testFrequencies() throws Exception {
        writeFilesAndWaitForScan(src,
                                 new File("test/Test1.java", "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); f.isDirectory(); } }"),
                                 new File("test/Test2.java", "package test; public class Test2 { private void test() { new javax.swing.ImageIcon(null); java.io.File f = null; f.isDirectory(); } }"),
                                 new File("test/Test3.java", "package test; public class Test3 { private void test() { new javax.swing.ImageIcon(null); new javax.swing.ImageIcon(null); } }")
                                );

        String[] patterns = new String[] {
            "$1.isDirectory()",
            "new ImageIcon($1)"
        };

        Map<String, Map<String, Integer>> golden = new HashMap<String, Map<String, Integer>>();
        
        golden.put("test/Test3.java", Collections.singletonMap("new ImageIcon($1)", 2));
        golden.put("test/Test1.java", Collections.singletonMap("$1.isDirectory()", 2));
        
        Map<String, Integer> freqsTest2 = new HashMap<String, Integer>();
        
        freqsTest2.put("$1.isDirectory()", 1);
        freqsTest2.put("new ImageIcon($1)", 1);
        
        golden.put("test/Test2.java", freqsTest2);
        
        verifyIndexWithFrequencies(patterns, golden);
    }

    public void testInnerClass() throws Exception {
        writeFilesAndWaitForScan(src,
                                 new File("test/Test1.java", "package test; public class Test1 { private static final class O implements Iterable<String> { private String next; private String computeNext() { return null; } public String hasNext() { next = computeNext(); return next != null; } } }"));

        verifyIndex("$this.computeNext()", new AdditionalQueryConstraints(Collections.singleton("test.Test1.O")), "test/Test1.java");
    }

    public void testIndexing() throws Exception {
        writeFilesAndWaitForScan(src,
                                 new File("test/Test1.java", "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); } }"),
                                 new File("test/Test2.java", "package test; public class Test2 { private void test() { new javax.swing.ImageIcon(null); } }"));

        verifyIndex("$1.isDirectory()", "test/Test1.java");
        verifyIndex("new ImageIcon($1)", "test/Test2.java");
        
        writeFilesAndWaitForScan(src,
                                 new File("test/Test1.java", "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); new javax.swing.ImageIcon(null); } }"),
                                 new File("test/Test2.java", "package test; public class Test2 { private void test() { new javax.swing.ImageIcon(null); } }"));

        verifyIndex("$1.isDirectory()", "test/Test1.java");
        verifyIndex("new ImageIcon($1)", "test/Test1.java", "test/Test2.java");
        
        writeFilesAndWaitForScan(src,
                                 new File("test/Test1.java", "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); new javax.swing.ImageIcon(null); } }"),
                                 new File("test/Test2.java", "package test; public class Test2 { }"));

        verifyIndex("$1.isDirectory()", "test/Test1.java");
        verifyIndex("new ImageIcon($1)", "test/Test1.java");
    }
    
    private void verifyIndex(final String[] patterns, String... containedIn) throws Exception {
        ClassPath EMPTY = ClassPathSupport.createClassPath(new FileObject[0]);
        ClasspathInfo cpInfo = ClasspathInfo.create(ClassPathSupport.createClassPath(SourceUtilsTestUtil.getBootClassPath().toArray(new URL[0])),
                                                    EMPTY,
                                                    EMPTY);

        final Set<String> real = new HashSet<String>();
        
        JavaSource.create(cpInfo).runUserActionTask(new Task<CompilationController>() {
            public void run(CompilationController parameter) throws Exception {
                real.addAll(IndexQuery.open(src.toURL()).findCandidates(BulkSearch.getDefault().create(parameter, new AtomicBoolean(), patterns)));
            }
        }, true);

        Set<String> golden = new HashSet<String>(Arrays.asList(containedIn));

        assertEquals(golden, real);
    }

    private void verifyIndex(final String pattern, final AdditionalQueryConstraints additionalConstraints, String... containedIn) throws Exception {
        ClassPath EMPTY = ClassPathSupport.createClassPath(new FileObject[0]);
        ClasspathInfo cpInfo = ClasspathInfo.create(ClassPathSupport.createClassPath(SourceUtilsTestUtil.getBootClassPath().toArray(new URL[0])),
                                                    EMPTY,
                                                    EMPTY);

        final Set<String> real = new HashSet<String>();

        JavaSource.create(cpInfo).runUserActionTask(new Task<CompilationController>() {
            public void run(CompilationController parameter) throws Exception {
                BulkPattern bulkPattern = BulkSearch.getDefault().create(Collections.singletonList(pattern),
                                                                          Collections.singletonList(Utilities.parseAndAttribute(parameter, pattern, null)),
                                                                          Collections.singletonList(additionalConstraints),
                                                                          new AtomicBoolean());
                
                real.addAll(IndexQuery.open(src.toURL()).findCandidates(bulkPattern));
            }
        }, true);

        Set<String> golden = new HashSet<String>(Arrays.asList(containedIn));

        assertEquals(golden, real);
    }

    private void verifyIndexWithFrequencies(final String[] patterns, Map<String, Map<String, Integer>> golden) throws Exception {
        ClassPath EMPTY = ClassPathSupport.createClassPath(new FileObject[0]);
        ClasspathInfo cpInfo = ClasspathInfo.create(ClassPathSupport.createClassPath(SourceUtilsTestUtil.getBootClassPath().toArray(new URL[0])),
                                                    EMPTY,
                                                    EMPTY);

        final Map<String, Map<String, Integer>> real = new HashMap<String, Map<String, Integer>>();

        JavaSource.create(cpInfo).runUserActionTask(new Task<CompilationController>() {
            public void run(CompilationController parameter) throws Exception {
                real.putAll(IndexQuery.open(src.toURL()).findCandidatesWithFrequencies(BulkSearch.getDefault().create(parameter, new AtomicBoolean(), patterns)));
            }
        }, true);

        assertEquals(golden, real);
    }

    private void verifyIndex(final String pattern, String... containedIn) throws Exception {
        ClassPath EMPTY = ClassPathSupport.createClassPath(new FileObject[0]);
        ClasspathInfo cpInfo = ClasspathInfo.create(ClassPathSupport.createClassPath(SourceUtilsTestUtil.getBootClassPath().toArray(new URL[0])),
                                                    EMPTY,
                                                    EMPTY);

        final Set<String> real = new HashSet<String>();
        
        JavaSource.create(cpInfo).runUserActionTask(new Task<CompilationController>() {
            public void run(CompilationController parameter) throws Exception {
                real.addAll(IndexQuery.open(src.toURL()).findCandidates(BulkSearch.getDefault().create(parameter, new AtomicBoolean(), pattern)));
            }
        }, true);

        Set<String> golden = new HashSet<String>(Arrays.asList(containedIn));

        assertEquals(golden, real);
    }
    
    @ServiceProvider(service=MimeDataProvider.class)
    public static final class MimeDataProviderImpl implements MimeDataProvider {

        private static final Lookup L = Lookups.fixed(new Indexer.FactoryImpl());
        
        @Override
        public Lookup getLookup(MimePath mp) {
            if ("text/x-java".equals(mp.getPath())) {
                return L;
            }
            return Lookup.EMPTY;
        }
        
    }
}