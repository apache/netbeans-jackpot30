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
