/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2010 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2008-2010 Sun Microsystems, Inc.
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
