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

package org.netbeans.modules.jackpot30.indexing.batch;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.text.Document;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.TreePathHandle;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.modules.diff.builtin.provider.BuiltInDiffProvider;
import org.netbeans.modules.diff.builtin.visualizer.TextDiffVisualizer;
import org.netbeans.modules.java.editor.base.imports.UnusedImports;
import org.netbeans.modules.java.editor.base.semantic.SemanticHighlighterBase;
import org.netbeans.modules.java.editor.semantic.SemanticHighlighter;
import org.netbeans.spi.diff.DiffProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Jan Lahoda
 */
public class BatchUtilities {

    public static void exportDiff(ModificationResult result, @NullAllowed FileObject relativeTo, Writer out) throws IOException {
        for (FileObject f : result.getModifiedFileObjects()) {
            Charset c = FileEncodingQuery.getEncoding(f);
            String orig = new String(f.asBytes(), c);
            String nue = result.getResultingSource(f);

            if (orig.equals(nue)) {
                continue;
            }

            String name = relativeTo != null ? FileUtil.getRelativePath(relativeTo, f) : FileUtil.toFile(f).getAbsolutePath();
            
            doExportDiff(name, orig, nue, out);
        }
    }

    //copied from the diff module:
    private static void doExportDiff(String name, String original, String modified, Writer out) throws IOException {
        DiffProvider diff = new BuiltInDiffProvider();//(DiffProvider) Lookup.getDefault().lookup(DiffProvider.class);

        Reader r1 = null;
        Reader r2 = null;
        org.netbeans.api.diff.Difference[] differences;

        try {
            r1 = new StringReader(original);
            r2 = new StringReader(modified);
            differences = diff.computeDiff(r1, r2);
        } finally {
            if (r1 != null) try { r1.close(); } catch (Exception e) {}
            if (r2 != null) try { r2.close(); } catch (Exception e) {}
        }

        try {
            r1 = new StringReader(original);
            r2 = new StringReader(modified);
            TextDiffVisualizer.TextDiffInfo info = new TextDiffVisualizer.TextDiffInfo(
                name, // NOI18N
                name,  // NOI18N
                null,
                null,
                r1,
                r2,
                differences
            );
            info.setContextMode(true, 3);
            String diffText;
//            if (format == unifiedFilter) {
                diffText = TextDiffVisualizer.differenceToUnifiedDiffText(info);
//            } else {
//                diffText = TextDiffVisualizer.differenceToNormalDiffText(info);
//            }
            out.write(diffText);
        } finally {
            if (r1 != null) try { r1.close(); } catch (Exception e) {}
            if (r2 != null) try { r2.close(); } catch (Exception e) {}
        }
    }

    public static void removeUnusedImports(Collection<? extends FileObject> files) throws IOException {
        Map<ClasspathInfo, Collection<FileObject>> sortedFastFiles = org.netbeans.modules.java.hints.spiimpl.batch.BatchUtilities.sortFiles(files);

        for (Entry<ClasspathInfo, Collection<FileObject>> e : sortedFastFiles.entrySet()) {
            JavaSource.create(e.getKey(), e.getValue()).runModificationTask(new RemoveUnusedImports()).commit();
        }
    }

    private static final class RemoveUnusedImports implements Task<WorkingCopy> {
        public void run(WorkingCopy wc) throws IOException {
            Document doc = wc.getSnapshot().getSource().getDocument(true);

            if (wc.toPhase(Phase.RESOLVED).compareTo(Phase.RESOLVED) < 0) {
                return;
            }

            //compute imports to remove:
            List<TreePathHandle> unusedImports = UnusedImports.computeUnusedImports(wc);
            CompilationUnitTree cut = wc.getCompilationUnit();
            // make the changes to the source
            for (TreePathHandle handle : unusedImports) {
                TreePath path = handle.resolve(wc);
                assert path != null;
                cut = wc.getTreeMaker().removeCompUnitImport(cut,
                        (ImportTree) path.getLeaf());
            }

            if (!unusedImports.isEmpty()) {
                wc.rewrite(wc.getCompilationUnit(), cut);
            }
        }
    }
}
