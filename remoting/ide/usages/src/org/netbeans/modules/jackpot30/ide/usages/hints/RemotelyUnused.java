/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
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
 * Portions Copyrighted 2012 Sun Microsystems, Inc.
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
