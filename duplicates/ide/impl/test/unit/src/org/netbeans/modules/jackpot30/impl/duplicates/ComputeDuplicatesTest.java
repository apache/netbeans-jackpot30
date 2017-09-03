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