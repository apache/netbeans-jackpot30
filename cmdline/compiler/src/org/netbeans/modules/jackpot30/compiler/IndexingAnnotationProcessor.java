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
