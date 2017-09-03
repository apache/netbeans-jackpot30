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
