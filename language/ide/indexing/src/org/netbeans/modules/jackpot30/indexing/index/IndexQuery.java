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

package org.netbeans.modules.jackpot30.indexing.index;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DataFormatException;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.CompressionTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.modules.jackpot30.remoting.api.WebUtilities;
import org.netbeans.modules.java.hints.providers.spi.HintDescription.AdditionalQueryConstraints;
import org.netbeans.modules.java.hints.spiimpl.pm.BulkSearch;
import org.netbeans.modules.java.hints.spiimpl.pm.BulkSearch.BulkPattern;
import org.netbeans.modules.parsing.lucene.support.Convertor;
import org.netbeans.modules.parsing.lucene.support.Index;
import org.netbeans.modules.parsing.lucene.support.Index.Status;
import org.netbeans.modules.parsing.lucene.support.IndexManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public abstract class IndexQuery {

    public abstract Collection<? extends String> findCandidates(BulkPattern pattern) throws IOException;

    public abstract Map<String, Map<String, Integer>> findCandidatesWithFrequencies(BulkPattern pattern) throws IOException;

    public static Map<String, Map<String, Integer>> performLocalQuery(Index index, final BulkPattern pattern, final boolean withFrequencies) throws IOException, InterruptedException, ParseException {
        final Map<String, Map<String, Integer>> result = new HashMap<String, Map<String, Integer>>();

        index.query(new ArrayList<Object>(), new Convertor<Document, Object>() {
            @Override public Object convert(Document doc) {
                try {
                    ByteArrayInputStream in = new ByteArrayInputStream(CompressionTools.decompress(doc.getField("languageEncoded").getBinaryValue()));

                    try {
                        Map<String, Integer> freqs;
                        boolean matches;

                        if (withFrequencies) {
                            freqs = BulkSearch.getDefault().matchesWithFrequencies(in, pattern, new AtomicBoolean());
                            matches = !freqs.isEmpty();
                        } else {
                            freqs = null;
                            matches = BulkSearch.getDefault().matches(in, new AtomicBoolean(), pattern);
                        }

                        if (matches) {
                            result.put(doc.getField("languagePath").stringValue(), freqs);
                        }
                    } finally {
                        in.close();
                    }
                } catch (DataFormatException ex) {
                    throw new IllegalStateException(ex);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }

                return null;
            }
        }, new FieldSelector() {
            public FieldSelectorResult accept(String string) {
                return "languageEncoded".equals(string) || "languagePath".equals(string) ? FieldSelectorResult.LOAD : FieldSelectorResult.NO_LOAD;
            }
        }, null, query(pattern));

        return result;
    }

    private static Query query(BulkPattern pattern) throws ParseException {
        BooleanQuery result = new BooleanQuery();

        for (int cntr = 0; cntr < pattern.getIdentifiers().size(); cntr++) {
            assert !pattern.getRequiredContent().get(cntr).isEmpty();

            BooleanQuery emb = new BooleanQuery();

            for (List<String> c : pattern.getRequiredContent().get(cntr)) {
                if (c.isEmpty()) continue;

                PhraseQuery pq = new PhraseQuery();

                for (String s : c) {
                    pq.add(new Term("languageContent", s));
                }

                emb.add(pq, BooleanClause.Occur.MUST);
            }

            AdditionalQueryConstraints additionalConstraints = pattern.getAdditionalConstraints().get(cntr);

            if (additionalConstraints != null && !additionalConstraints.requiredErasedTypes.isEmpty()) {
                BooleanQuery constraintsQuery = new BooleanQuery();

                constraintsQuery.add(new TermQuery(new Term("languageAttributed", "false")), BooleanClause.Occur.SHOULD);

                BooleanQuery constr = new BooleanQuery();

                for (String tc : additionalConstraints.requiredErasedTypes) {
                    constr.add(new TermQuery(new Term("languageErasedTypes", tc)), BooleanClause.Occur.MUST);
                }

                constraintsQuery.add(constr, BooleanClause.Occur.SHOULD);
                emb.add(constraintsQuery, BooleanClause.Occur.MUST);
            }

            result.add(emb, BooleanClause.Occur.SHOULD);
        }

        return result;
    }

    private static final class LocalIndexQuery extends IndexQuery {
        private final @NullAllowed File cacheDir;

        public LocalIndexQuery(@NullAllowed File cacheDir) {
            this.cacheDir = cacheDir;
        }

        public Collection<? extends String> findCandidates(BulkPattern pattern) throws IOException {
            return findCandidates(pattern, false).keySet();
        }

        public Map<String, Map<String, Integer>> findCandidatesWithFrequencies(BulkPattern pattern) throws IOException {
            return findCandidates(pattern, true);
        }

        private Map<String, Map<String, Integer>> findCandidates(BulkPattern pattern, boolean withFrequencies) throws IOException {
            Index index = IndexManager.createIndex(cacheDir, new KeywordAnalyzer());

            if (index.getStatus(true) != Status.VALID) {
                 return Collections.emptyMap();
            }

            try {
                return performLocalQuery(index, pattern, withFrequencies);
            } catch (InterruptedException ex) {
                throw new IOException(ex);
            } catch (ParseException ex) {
                throw new IOException(ex);
            } finally {
                index.close();
            }
        }

    }
    
    private static final class RemoteIndexQuery extends IndexQuery {
        private final RemoteIndex idx;

        public RemoteIndexQuery(RemoteIndex idx) {
            this.idx = idx;
        }
        
        @Override
        public Collection<? extends String> findCandidates(BulkPattern pattern) throws IOException {
            try {
                StringBuilder patterns = new StringBuilder();

                for (String p : pattern.getPatterns()) {
                    patterns.append(p);
                    patterns.append(";;");
                }

                URI u = new URI(idx.remote.toExternalForm() + "?path=" + WebUtilities.escapeForQuery(idx.remoteSegment) + "&pattern=" + WebUtilities.escapeForQuery(patterns.toString()));

                return new ArrayList<String>(WebUtilities.requestStringArrayResponse(u));
            } catch (URISyntaxException ex) {
                //XXX: better handling?
                Exceptions.printStackTrace(ex);
                return Collections.emptyList();
            }
        }
        @Override
        public Map<String, Map<String, Integer>> findCandidatesWithFrequencies(BulkPattern pattern) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
    
    public static IndexQuery open(URL sourceRoot) throws IOException {
        FileObject cacheFO  = Indexer.resolveCacheFolder(sourceRoot).getFileObject(Indexer.INDEX_NAME);
        File cache = cacheFO != null ? FileUtil.toFile(cacheFO) : null;
        
        return new LocalIndexQuery(cache);
    }

    public static IndexQuery remote(RemoteIndex idx) {
        return new RemoteIndexQuery(idx);
    }
}
