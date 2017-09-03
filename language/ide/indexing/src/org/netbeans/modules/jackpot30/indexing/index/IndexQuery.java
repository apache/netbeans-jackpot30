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
