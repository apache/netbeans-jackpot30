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

package org.netbeans.modules.jackpot30.impl.duplicates;

import org.netbeans.api.progress.ProgressHandle;
import org.openide.filesystems.FileObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.api.java.source.TestUtilities;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.modules.jackpot30.impl.duplicates.ComputeDuplicates.DuplicateDescription;
import org.netbeans.modules.jackpot30.impl.duplicates.ComputeDuplicates.Span;
import org.netbeans.modules.jackpot30.common.test.IndexTestBase;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbCollections;
import static org.junit.Assert.*;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.modules.jackpot30.impl.duplicates.indexing.DuplicatesCustomIndexerImpl;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class ComputeDuplicatesTest extends IndexTestBase {

    public ComputeDuplicatesTest(String name) {
        super(name);
    }

    public void testDuplicateDuplicates() throws Exception {
        writeFilesAndWaitForScan(src,
                                 new File("test/Test1.java", "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); } }"),
                                 new File("test/Test2.java", "package test; public class Test2 { private int a; private void test() { java.io.File f = null; f.isDirectory(); } }"),
                                 new File("test/Test3.java", "package test; public class Test3 { private void test() { for (int i = 0; i < 10; i++) { System.err.println(3 * i); System.err.println(4 * i); } }"));

        verifyDuplicates("test/Test1.java",
                         "private void test() { java.io.File f = null; f.isDirectory(); }",
                         "test/Test2.java",
                         "private void test() { java.io.File f = null; f.isDirectory(); }");
    }

    public void testCrossIndex() throws Exception {
        writeFilesAndWaitForScan(src,
                                 new File("test/Test1.java", "package test; public class Test1 { private void test() { java.io.File f = null; f.isDirectory(); } }"));

        writeFilesAndWaitForScan(src2,
                                 new File("test/Test2.java", "package test; public class Test2 { private int a; private void test() { java.io.File f = null; f.isDirectory(); } }"));

        verifyDuplicates("test/Test1.java",
                         "private void test() { java.io.File f = null; f.isDirectory(); }",
                         "test/Test2.java",
                         "private void test() { java.io.File f = null; f.isDirectory(); }");
    }

    private void verifyDuplicates(String... fileAndDuplicateCode) throws Exception {
        Map<String, String> duplicatesGolden = new HashMap<String, String>();

        for (int cntr = 0; cntr < fileAndDuplicateCode.length; cntr += 2) {
            duplicatesGolden.put(fileAndDuplicateCode[cntr], fileAndDuplicateCode[cntr + 1]);
        }

        Map<String, String> duplicatesReal = new HashMap<String, String>();
        ProgressHandle handle = ProgressHandleFactory.createHandle("test");

        handle.start();
        
        for (DuplicateDescription dd : NbCollections.iterable(new ComputeDuplicates().computeDuplicatesForAllOpenedProjects(handle, new AtomicBoolean()))) {
            for (Span s : dd.dupes) {
                duplicatesReal.put(relativePath(s.file), TestUtilities.copyFileToString(FileUtil.toFile(s.file)).substring(s.startOff, s.endOff));
            }
        }

        assertEquals(duplicatesGolden, duplicatesReal);
    }

    private String relativePath(FileObject file) {
        return FileUtil.isParentOf(src, file) ? FileUtil.getRelativePath(src, file) : FileUtil.getRelativePath(src2, file);
    }
    
    @ServiceProvider(service=MimeDataProvider.class)
    public static final class MimeDataProviderImpl implements MimeDataProvider {

        private final Lookup L = Lookups.fixed(new DuplicatesCustomIndexerImpl.FactoryImpl());
        
        @Override
        public Lookup getLookup(MimePath mimePath) {
            if ("text/x-java".equals(mimePath.getPath())) return L;
            return Lookup.EMPTY;
        }
        
    }
}