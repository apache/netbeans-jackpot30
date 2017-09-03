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

package org.netbeans.modules.jackpot30.common.test;

import java.net.URL;
import java.util.Collections;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.java.source.SourceUtilsTestUtil;
import org.netbeans.api.java.source.TestUtilities;
import org.netbeans.core.startup.Main;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings.GlobalSettingsProvider;
import org.netbeans.modules.java.source.indexing.JavaCustomIndexer;
import org.netbeans.modules.parsing.impl.indexing.MimeTypes;
import org.netbeans.modules.parsing.impl.indexing.RepositoryUpdater;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.netbeans.spi.java.classpath.ClassPathProvider;
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
public abstract class IndexTestBase extends NbTestCase {

    public IndexTestBase(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        SourceUtilsTestUtil.prepareTest(new String[0], new Object[] {new TestClassPathProvider()});
        Main.initializeURLFactory();
        org.netbeans.api.project.ui.OpenProjects.getDefault().getOpenProjects();
        prepareTest();
        MimeTypes.setAllMimeTypes(Collections.singleton("text/x-java"));
        GlobalPathRegistry.getDefault().register(ClassPath.SOURCE, new ClassPath[] {sourceCP});
        RepositoryUpdater.getDefault().start(true);
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        GlobalPathRegistry.getDefault().unregister(ClassPath.SOURCE, new ClassPath[] {sourceCP});
    }

    protected FileObject sources;
    protected FileObject src;
    protected FileObject src2;
    protected FileObject build;
    protected FileObject cache;

    private ClassPath sourceCP;

    private void prepareTest() throws Exception {
        FileObject workdir = SourceUtilsTestUtil.makeScratchDir(this);

        sources = FileUtil.createFolder(workdir, "sources");
        src = FileUtil.createFolder(sources, "src");
        src2 = FileUtil.createFolder(sources, "src2");
        build = FileUtil.createFolder(workdir, "build");
        cache = FileUtil.createFolder(workdir, "cache");

        sourceCP = ClassPathSupport.createClassPath(src, src2);
        
        SourceUtilsTestUtil.prepareTest(src, build, cache);
    }

    public static void writeFiles(FileObject sourceRoot, File... files) throws Exception {
        for (FileObject c : sourceRoot.getChildren()) {
            c.delete();
        }

        for (File f : files) {
            FileObject fo = FileUtil.createData(sourceRoot, f.filename);
            TestUtilities.copyStringToFile(fo, f.content);
        }
    }

    public static void writeFilesAndWaitForScan(FileObject sourceRoot, File... files) throws Exception {
        writeFiles(sourceRoot, files);
        SourceUtils.waitScanFinished();
    }

    public static final class File {
        public final String filename;
        public final String content;

        public File(String filename, String content) {
            this.filename = filename;
            this.content = content;
        }
    }

    @ServiceProvider(service=MimeDataProvider.class)
    public static final class MimeDataProviderImpl implements MimeDataProvider {

        private static final Lookup L = Lookups.fixed(new JavaCustomIndexer.Factory(), new GlobalSettingsProvider());
        
        @Override
        public Lookup getLookup(MimePath mp) {
            if ("text/x-java".equals(mp.getPath())) {
                return L;
            }
            return Lookup.EMPTY;
        }
        
    }

    private class TestClassPathProvider implements ClassPathProvider {
        
        public TestClassPathProvider() {
        }
        
        public ClassPath findClassPath(FileObject file, String type) {
            try {
            if (ClassPath.BOOT == type) {
                return ClassPathSupport.createClassPath(SourceUtilsTestUtil.getBootClassPath().toArray(new URL[0]));
            }
            
            if (ClassPath.SOURCE == type) {
                return sourceCP;
            }
            
            if (ClassPath.COMPILE == type) {
                return ClassPathSupport.createClassPath(new URL[0]);
            }
            
            if (ClassPath.EXECUTE == type) {
                return ClassPathSupport.createClassPath(new FileObject[] {
                    build
                });
            }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
        
    }
}
