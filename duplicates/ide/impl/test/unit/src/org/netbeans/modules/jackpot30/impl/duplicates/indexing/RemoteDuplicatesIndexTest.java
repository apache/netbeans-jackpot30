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
package org.netbeans.modules.jackpot30.impl.duplicates.indexing;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.api.java.source.TestUtilities;
import static org.junit.Assert.*;
import org.netbeans.api.java.source.SourceUtilsTestUtil;
import org.netbeans.core.startup.Main;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.jackpot30.impl.TestUtils;
import org.netbeans.modules.jackpot30.impl.duplicates.ComputeDuplicates.DuplicateDescription;
import org.netbeans.modules.jackpot30.impl.duplicates.ComputeDuplicates.Span;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author lahvac
 */
public class RemoteDuplicatesIndexTest extends NbTestCase {

    public RemoteDuplicatesIndexTest(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        SourceUtilsTestUtil.prepareTest(new String[0], new Object[0]);
        Main.initializeURLFactory();
        TestUtils.clearRemoteContent();
        super.setUp();
    }

    public void testFindHashOccurrences() throws Exception {
        clearWorkDir();
        File cacheDir = new File(getWorkDir(), "cache");
        File dataDir = new File(getWorkDir(), "data");

        CacheFolder.setCacheFolder(FileUtil.createFolder(cacheDir));

        FileObject data1 = FileUtil.createFolder(new File(dataDir, "data1"));
        FileObject data2 = FileUtil.createFolder(new File(dataDir, "data2"));
        FileObject source = FileUtil.createData(new File(dataDir, "src/Test.java"));

        TestUtilities.copyStringToFile(data1.createData("T1.java"), "0123456789");
        TestUtilities.copyStringToFile(data1.createData("T2.java"), "0123456789");
        TestUtilities.copyStringToFile(data2.createData("T3.java"), "0123456789");
        TestUtilities.copyStringToFile(data2.createData("T4.java"), "0123456789");
        
        TestUtils.addRemoteContent(new URL("test://test/index/duplicates/findDuplicates?hashes=[%0A%20%20%20%20%2200%22,%0A%20%20%20%20%2201%22,%0A%20%20%20%20%2202%22%0A]"), "{ \"00\": { \"foo1\": [ \"T1.java\", \"T2.java\" ], \"foo2\": [ \"T3.java\", \"T4.java\" ] } }");
        TestUtils.addRemoteContent(new URL("test://test/index/info?path=foo1"), "{ }");
        TestUtils.addRemoteContent(new URL("test://test/index/info?path=foo2"), "{ }");
        RemoteIndex.saveIndices(Arrays.asList(RemoteIndex.create(FileUtil.toFile(data1).toURI().toURL(), new URL("test://test/index"), "foo1"),
                                              RemoteIndex.create(FileUtil.toFile(data2).toURI().toURL(), new URL("test://test/index"), "foo2")));

        TestUtilities.copyStringToFile(source, "01234567890123456789");
        
        Map<String, long[]> hashes = new HashMap<String, long[]>();

        hashes.put("00", new long[] {1, 2, 5, 6});
        hashes.put("01", new long[] {8, 9});
        hashes.put("02", new long[] {3, 4});
        
        List<DuplicateDescription> duplicates = RemoteDuplicatesIndex.findDuplicates(hashes, source, new AtomicBoolean());
        List<String> duplicatesReal = new ArrayList<String>();

        for (DuplicateDescription dd : duplicates) {
            for (Span s : dd.dupes) {
                duplicatesReal.add(s.file.getName());
            }
        }

        assertEquals(Arrays.asList("T1", "T2", "T3", "T4"), duplicatesReal);

        //check local cache:
        duplicates = RemoteDuplicatesIndex.findDuplicates(hashes, source, new AtomicBoolean());
        duplicatesReal = new ArrayList<String>();

        for (DuplicateDescription dd : duplicates) {
            for (Span s : dd.dupes) {
                duplicatesReal.add(s.file.getName());
            }
        }

        assertEquals(Arrays.asList("T1", "T2", "T3", "T4"), duplicatesReal);
    }

}
