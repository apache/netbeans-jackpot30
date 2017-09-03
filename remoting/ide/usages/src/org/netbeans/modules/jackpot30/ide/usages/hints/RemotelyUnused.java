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

package org.netbeans.modules.jackpot30.ide.usages.hints;

import com.sun.source.tree.Tree.Kind;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TermQuery;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.modules.jackpot30.common.api.JavaUtils;
import org.netbeans.modules.jackpot30.common.api.LuceneHelpers.BitSetCollector;
import org.netbeans.modules.jackpot30.remoting.api.LocalCache;
import org.netbeans.modules.jackpot30.remoting.api.LocalCache.Task;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.modules.jackpot30.remoting.api.WebUtilities;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.java.hints.ErrorDescriptionFactory;
import org.netbeans.spi.java.hints.Hint;
import org.netbeans.spi.java.hints.HintContext;
import org.netbeans.spi.java.hints.TriggerTreeKind;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author lahvac
 */
@Hint(displayName="#DN_RemotelyUnused", description="#DESC_RemotelyUnused", category="general", enabled=false)
@Messages({"DN_RemotelyUnused=Unused in Remote Projects",
           "DESC_RemotelyUnused=Not used in any known remote project",
           "ERR_NoUsages=No usages found in any know projects"})
public class RemotelyUnused {

    private static final String VAL_UNUSED = "unused";
    private static final String VAL_USED = "used";
    private static final String VAL_UNKNOWN = "unknown";

    @TriggerTreeKind({Kind.VARIABLE, Kind.METHOD})
    public static ErrorDescription hint(HintContext ctx) throws URISyntaxException, IOException {
        Element toSearch = ctx.getInfo().getTrees().getElement(ctx.getPath());

        if (toSearch == null) return null;
        if (!toSearch.getKind().isField() && toSearch.getKind() != ElementKind.METHOD && toSearch.getKind() != ElementKind.CONSTRUCTOR) return null;
        if (toSearch.getKind() == ElementKind.METHOD && ctx.getInfo().getElementUtilities().overridesMethod((ExecutableElement) toSearch)) return null;

        final String serialized = JavaUtils.serialize(ElementHandle.create(toSearch));

        for (RemoteIndex idx : RemoteIndex.loadIndices()) {
            String result = LocalCache.runOverLocalCache(idx, new Task<IndexReader, String>() {
                @Override
                public String run(IndexReader reader, AtomicBoolean cancel) throws IOException {
                    Query query = new TermQuery(new Term("usagesSignature", serialized));
                    Searcher s = new IndexSearcher(reader);
                    BitSet matchingDocuments = new BitSet(reader.maxDoc());
                    Collector c = new BitSetCollector(matchingDocuments);

                    s.search(query, c);

                    for (int docNum = matchingDocuments.nextSetBit(0); docNum >= 0; docNum = matchingDocuments.nextSetBit(docNum + 1)) {
                        if (cancel.get()) return VAL_UNKNOWN;

                        final Document doc = reader.document(docNum);

                        return doc.get("usagesUsages");
                    }

                    return VAL_UNKNOWN;
                }
            }, null, new AtomicBoolean()/*XXX*/);

            if (result == null) {
                URI resolved = new URI(idx.remote.toExternalForm() + "/usages/search?path=" + WebUtilities.escapeForQuery(idx.remoteSegment) + "&signatures=" + WebUtilities.escapeForQuery(serialized));
                String response = WebUtilities.requestStringResponse(resolved, new AtomicBoolean());

                if (response != null) {
                    result = response.trim().isEmpty() ? VAL_UNUSED : VAL_USED;
                } else {
                    result = VAL_UNKNOWN;
                }
                final String resultFin = result;
                LocalCache.saveToLocalCache(idx, new Task<IndexWriter, Void>() {
                    @Override public Void run(IndexWriter p, AtomicBoolean cancel) throws IOException {
                        Document doc = new Document();
                        doc.add(new Field("usagesSignature", serialized, Store.NO, Index.NOT_ANALYZED));
                        doc.add(new Field("usagesUsages", resultFin, Store.YES, Index.NO));
                        p.addDocument(doc);
                        return null;
                    }
                });
            }
            if (!VAL_UNUSED.equals(result)) return null;
        }

        return ErrorDescriptionFactory.forName(ctx, ctx.getPath(), Bundle.ERR_NoUsages());
    }
}
