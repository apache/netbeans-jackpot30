/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2011 Sun Microsystems, Inc.
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
