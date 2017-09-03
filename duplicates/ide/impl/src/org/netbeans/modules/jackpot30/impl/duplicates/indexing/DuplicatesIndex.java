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

package org.netbeans.modules.jackpot30.impl.duplicates.indexing;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.Term;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.modules.jackpot30.common.api.IndexAccess;
import org.netbeans.modules.jackpot30.impl.duplicates.ComputeDuplicates;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 *
 * @author lahvac
 */
public class DuplicatesIndex {

    private final org.apache.lucene.index.IndexWriter luceneWriter;

    public DuplicatesIndex(URL sourceRoot, FileObject cacheRoot) throws IOException {
        luceneWriter = Lookup.getDefault().lookup(IndexAccess.class).getIndexWriter(sourceRoot, cacheRoot, NAME);
    }

    public void record(final CompilationInfo info, Indexable idx, final CompilationUnitTree cut) throws IOException {
        record(info.getTrees(), idx, cut);
    }

    public void record(final Trees trees, Indexable idx, final CompilationUnitTree cut) throws IOException {
        String relative = Lookup.getDefault().lookup(IndexAccess.class).getRelativePath(idx);

        try {
            final Document doc = new Document();

            doc.add(new Field("duplicatesPath", relative, Field.Store.YES, Field.Index.NOT_ANALYZED));

            final Map<String, long[]> positions = ComputeDuplicates.encodeGeneralized(trees, cut);

            for (Entry<String, long[]> e : positions.entrySet()) {
                doc.add(new Field("duplicatesGeneralized", e.getKey(), Store.YES, Index.NOT_ANALYZED));

                StringBuilder positionsSpec = new StringBuilder();

                for (int i = 0; i < e.getValue().length; i += 2) {
                    if (positionsSpec.length() > 0) positionsSpec.append(';');
                    positionsSpec.append(e.getValue()[i]).append(':').append(e.getValue()[i + 1] - e.getValue()[i]);
                }

                doc.add(new Field("duplicatesPositions", positionsSpec.toString(), Store.YES, Index.NO));
            }

            luceneWriter.addDocument(doc);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            Logger.getLogger(DuplicatesIndex.class.getName()).log(Level.WARNING, null, t);
        }
    }

    public void remove(String relativePath) throws IOException {
        luceneWriter.deleteDocuments(new Term("duplicatesPath", relativePath));
    }

    public void close() throws IOException {
        Lookup.getDefault().lookup(IndexAccess.class).finish();
    }

    public static final String NAME = "duplicates"; //NOI18N
    public static final int    VERSION = 1; //NOI18N
}
