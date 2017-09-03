/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009-2011 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2009 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.compiler;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.Trees;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import org.netbeans.api.java.source.ClasspathInfo.PathKind;
import org.netbeans.api.java.source.CompilationInfoHack;
import org.netbeans.modules.jackpot30.indexing.index.Indexer;
import org.netbeans.modules.java.preprocessorbridge.spi.JavaIndexerPlugin;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.netbeans.modules.parsing.impl.indexing.FileObjectIndexable;
import org.netbeans.modules.parsing.impl.indexing.SPIAccessor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=AbstractHintsAnnotationProcessing.class)
public class IndexingAnnotationProcessor extends AbstractHintsAnnotationProcessing {

    static final String CACHE_ROOT = "jackpot30_cache_root";
    static final String SOURCE_ROOT = "jackpot30_root";
    static final String INDEXED_FILES = "jackpot30_indexed_files";

    public static final Set<String> OPTIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            CACHE_ROOT,
            SOURCE_ROOT,
            INDEXED_FILES
    )));

    private boolean enabled;
    private Map<FileObject, JavaIndexerPlugin> writers;

    @Override
    protected boolean initialize(ProcessingEnvironment processingEnv) {
        String cacheRoot = processingEnv.getOptions().get(CACHE_ROOT);

        if (cacheRoot == null) return false;

        enabled = true;

        File cache = new File(cacheRoot);
        cache.mkdirs();

        cache = FileUtil.normalizeFile(cache);
        FileUtil.refreshFor(cache.getParentFile());
        CacheFolder.setCacheFolder(FileUtil.toFileObject(cache));

        writers = new HashMap<FileObject, JavaIndexerPlugin>();

        return true;
    }

    @Override
    protected void doProcess(CompilationInfoHack info, ProcessingEnvironment processingEnv, Reporter reporter) {
        try {
            if (!enabled) return;

            FileObject root;

            if (processingEnv.getOptions().containsKey(SOURCE_ROOT)) {
                File rootFile = new File(processingEnv.getOptions().get(SOURCE_ROOT));

                root = FileUtil.toFileObject(FileUtil.normalizeFile(rootFile));

                if (root == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Specified root (" + rootFile.getAbsolutePath() + ") does not exist");
                    return;
                }
            } else {
                root = info.getClasspathInfo().getClassPath(PathKind.SOURCE).findOwnerRoot(info.getFileObject());
                
                if (root == null) {
                    //try to find the source path from the package clause:
                    root = info.getFileObject().getParent();

                    if (info.getCompilationUnit().getPackageName() != null) {
                        Tree t = info.getCompilationUnit().getPackageName();

                        while (t.getKind() == Kind.MEMBER_SELECT) {
                            root = root.getParent();
                            t = ((MemberSelectTree) t).getExpression();
                        }

                        root = root.getParent();
                    }
                }
            }

            JavaIndexerPlugin w = writers.get(root);
            URL sourceRoot = root.toURL();

            if (w == null) {
                writers.put(root, w = new Indexer.FactoryImpl().create(sourceRoot, Indexer.resolveCacheFolder(sourceRoot)));
            }
            
            Lookup services = Lookups.fixed(processingEnv.getElementUtils(), processingEnv.getTypeUtils(), Trees.instance(processingEnv));

            w.process(info.getCompilationUnit(), SPIAccessor.getInstance().create(new FileObjectIndexable(root, info.getFileObject())), services);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    protected void finish() {
        if (!enabled) return;

        for (JavaIndexerPlugin w : writers.values()) {
            w.finish();
        }

        writers = null;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return OPTIONS;
    }

}
