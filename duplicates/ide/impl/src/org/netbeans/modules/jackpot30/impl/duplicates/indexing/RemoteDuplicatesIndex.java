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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.codeviation.pojson.Pojson;
import org.netbeans.modules.jackpot30.common.api.LuceneHelpers.BitSetCollector;
import org.netbeans.modules.jackpot30.impl.duplicates.ComputeDuplicates.DuplicateDescription;
import org.netbeans.modules.jackpot30.impl.duplicates.ComputeDuplicates.Span;
import org.netbeans.modules.jackpot30.remoting.api.LocalCache;
import org.netbeans.modules.jackpot30.remoting.api.LocalCache.Task;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.modules.jackpot30.remoting.api.WebUtilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
@SuppressWarnings("ClassWithMultipleLoggers")
public class RemoteDuplicatesIndex {

    private static final Logger TIMER = Logger.getLogger("TIMER");

    public static List<DuplicateDescription> findDuplicates(Map<String, long[]> hashes, FileObject currentFile, AtomicBoolean cancel) throws IOException, URISyntaxException {
        return translate(hashes, findHashOccurrences(hashes.keySet(), currentFile, cancel), currentFile);
    }

    private static Map<String, Map<RemoteIndex, Collection<String>>> findHashOccurrences(Collection<? extends String> hashes, FileObject currentFile, AtomicBoolean cancel) throws IOException, URISyntaxException {
        Map<URI, Collection<RemoteIndex>> indices = new LinkedHashMap<URI, Collection<RemoteIndex>>();

        for (RemoteIndex ri : RemoteIndex.loadIndices()) {
            try {
                URI uri = ri.remote.toURI();
                Collection<RemoteIndex> list = indices.get(uri);

                if (list == null) {
                    indices.put(uri, list = new ArrayList<RemoteIndex>());
                }

                list.add(ri);
            } catch (URISyntaxException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        Map<String, Map<RemoteIndex, Collection<String>>> result = new LinkedHashMap<String, Map<RemoteIndex, Collection<String>>>();
        long localTime = 0;
        long remoteTime = 0;

        for (RemoteIndex ri : RemoteIndex.loadIndices()) {
            if (cancel.get()) return Collections.emptyMap();
            
            Set<String> toProcess = new LinkedHashSet<String>(hashes);
            Map<String, Map<String, Collection<? extends String>>> indexResult = new LinkedHashMap<String, Map<String, Collection<? extends String>>>();

            long locS = System.currentTimeMillis();
            indexResult.putAll(findHashOccurrencesInLocalCache(ri, toProcess, cancel));
            localTime += System.currentTimeMillis() - locS;

            toProcess.removeAll(indexResult.keySet());

            if (!toProcess.isEmpty()) {
                long remS = System.currentTimeMillis();
                Map<String, Map<String, Collection<? extends String>>> remoteResults = findHashOccurrencesRemote(ri.remote.toURI(), toProcess, cancel);
                remoteTime += System.currentTimeMillis() - remS;

                Map<String, Map<String, Collection<? extends String>>> toSave = new LinkedHashMap<String, Map<String, Collection<? extends String>>>(remoteResults);

                for (String hash : toProcess) {
                    if (!toSave.containsKey(hash)) {
                        toSave.put(hash, Collections.<String, Collection<? extends String>>emptyMap());
                    }
                }

                if (cancel.get()) return Collections.emptyMap();
                
                saveToLocalCache(ri, toSave);

                indexResult.putAll(remoteResults);
            }

            for (Entry<String, Map<String, Collection<? extends String>>> e : indexResult.entrySet()) {
                Map<RemoteIndex, Collection<String>> hashResult = result.get(e.getKey());

                if (hashResult == null) {
                    result.put(e.getKey(), hashResult = new LinkedHashMap<RemoteIndex, Collection<String>>());
                }

                for (Entry<String, Collection<? extends String>> insideHash : e.getValue().entrySet()) {
                    if (cancel.get()) return Collections.emptyMap();

                    Collection<String> dupes = hashResult.get(ri);

                    if (dupes == null) {
                        hashResult.put(ri, dupes = new LinkedHashSet<String>());
                    }

                    dupes.addAll(insideHash.getValue());
                }
            }
        }

        TIMER.log(Level.FINE, "local hash duplicates", new Object[] {currentFile, localTime});
        TIMER.log(Level.FINE, "remote hash duplicates", new Object[] {currentFile, remoteTime});
        return result;
    }

    private static Map<String, Map<String, Collection<? extends String>>> findHashOccurrencesRemote(URI remoteIndex, Iterable<? extends String> hashes, AtomicBoolean cancel) {
        try {
            String indexURL = remoteIndex.toASCIIString();
            URI u = new URI(indexURL + "/duplicates/findDuplicates?hashes=" + WebUtilities.escapeForQuery(Pojson.save(hashes)));
            String hashesMap = WebUtilities.requestStringResponse(u, cancel);

            if (hashesMap == null || cancel.get()) {
                //some kind of error while getting the duplicates (cannot access remote server)?
                //ignore:
                return Collections.emptyMap();
            }
            return Pojson.load(LinkedHashMap.class, hashesMap);
        } catch (URISyntaxException ex) {
            //XXX: better handling?
            Exceptions.printStackTrace(ex);
            return Collections.emptyMap();
        }
    }

    private static Map<String, Map<String, Collection<? extends String>>> findHashOccurrencesInLocalCache(RemoteIndex ri, final Iterable<? extends String> hashes, AtomicBoolean cancel) throws IOException, URISyntaxException {
        return LocalCache.runOverLocalCache(ri, new Task<IndexReader, Map<String, Map<String, Collection<? extends String>>>>() {
            @Override public Map<String, Map<String, Collection<? extends String>>> run(IndexReader reader, AtomicBoolean cancel) throws IOException {
                Map<String, Map<String, Collection<String>>> result = new LinkedHashMap<String, Map<String, Collection<String>>>();

                for (Entry<String, Collection<? extends String>> e : containsHash(reader, hashes, cancel).entrySet()) {
                    if (cancel.get()) return Collections.emptyMap();

                    Map<String, Collection<String>> forHash = result.get(e.getKey());

                    if (forHash == null) {
                        result.put(e.getKey(), forHash = new LinkedHashMap<String, Collection<String>>());
                    }

                    for (String path : e.getValue()) {
                        String segment = path.substring(0, path.indexOf('/'));

                        path = path.substring(path.indexOf('/') + 1);

                        Collection<String> list = forHash.get(segment);

                        if (list == null) {
                            forHash.put(segment, list = new LinkedList<String>());
                        }

                        list.add(path);
                    }
                }

                return (Map)result; //XXX
            }
        }, Collections.<String, Map<String, Collection<? extends String>>>emptyMap(), cancel);
    }

    private static synchronized void saveToLocalCache(RemoteIndex ri, final Map<String, Map<String, Collection<? extends String>>> what) throws IOException, URISyntaxException {
        LocalCache.saveToLocalCache(ri, new Task<IndexWriter, Void>() {
            @Override public Void run(IndexWriter w, AtomicBoolean cancel) throws IOException {
                for (Entry<String, Map<String, Collection<? extends String>>> e : what.entrySet()) {
                    Document doc = new Document();

                    doc.add(new Field("hash", e.getKey(), Store.YES, Index.NOT_ANALYZED));

                    for (Entry<String, Collection<? extends String>> pe : e.getValue().entrySet()) {
                        for (String path : pe.getValue()) {
                            doc.add(new Field("path", pe.getKey() + "/" + path, Store.YES, Index.NO));
                        }
                    }

                    w.addDocument(doc);
                }

                return null;
            }
        });
    }
    
    private static List<DuplicateDescription> translate(Map<String, long[]> hashes, Map<String, Map<RemoteIndex, Collection<String>>> occ, FileObject currentFile) {
        Map<String, Map<RemoteIndex, Collection<String>>> sorted = hashMap();
        Map<long[], DuplicateDescription> result = new LinkedHashMap<long[], DuplicateDescription>();
        List<long[]> seen = new LinkedList<long[]>();

        sorted.putAll(occ);

        OUTER: for (Entry<String, Map<RemoteIndex, Collection<String>>> e : occ.entrySet()) {
            long[] currentSpan = hashes.get(e.getKey());

            for (Iterator<Entry<long[], DuplicateDescription>> it = result.entrySet().iterator(); it.hasNext();) {
                Entry<long[], DuplicateDescription> span = it.next();

                if (span.getKey()[0] <= currentSpan[0] && span.getKey()[1] >= currentSpan[1]) {
                    continue OUTER;
                }

                if (currentSpan[0] <= span.getKey()[0] && currentSpan[1] >= span.getKey()[1]) {
                    it.remove();
                }
            }

            if (currentSpan[0] == (-1) || currentSpan[1] == (-1)) continue;
            
            seen.add(currentSpan);
            
            String longest = e.getKey();
            List<Span> foundDuplicates = new LinkedList<Span>();

            for (Entry<RemoteIndex, Collection<String>> root2Occurrences : e.getValue().entrySet()) {
                FileObject localRoot = URLMapper.findFileObject(root2Occurrences.getKey().getLocalFolder());

                for (String cand : root2Occurrences.getValue()) {
                    FileObject o = localRoot.getFileObject(cand);

                    if (o == null) continue; //XXX log!
                    if (areEquivalent(currentFile, o)) continue;
                    
                    foundDuplicates.add(new Span(o, -1, -1));
                }
            }

            if (foundDuplicates.isEmpty()) continue;
            
            DuplicateDescription current = DuplicateDescription.of(foundDuplicates, getValue(longest), longest);

            result.put(currentSpan, current);
        }

        return new LinkedList<DuplicateDescription>(result.values());
    }

    private static boolean areEquivalent(FileObject f1, FileObject f2) {
        return f1.equals(f2);
    }

    private static long getValue(String encoded) {
        return Long.parseLong(encoded.substring(encoded.lastIndexOf(":") + 1));
    }

    private static <T> TreeMap<String, T> hashMap() {
        return new TreeMap<String, T>(new Comparator<String>() {
            public int compare(String arg0, String arg1) {
                return (int) Math.signum(getValue(arg1) - getValue(arg0));
            }
        });
    }

    private static Map<String, Collection<? extends String>> containsHash(IndexReader reader, Iterable<? extends String> hashes, AtomicBoolean cancel) throws IOException {
        Map<String, Collection<? extends String>> result = new LinkedHashMap<String, Collection<? extends String>>();

        for (String hash : hashes) {
            if (cancel.get()) return Collections.emptyMap();

            Collection<String> found = new LinkedList<String>();
            Query query = new TermQuery(new Term("hash", hash));
            Searcher s = new IndexSearcher(reader);
            BitSet matchingDocuments = new BitSet(reader.maxDoc());
            Collector c = new BitSetCollector(matchingDocuments);

            s.search(query, c);

            boolean wasFound = false;

            for (int docNum = matchingDocuments.nextSetBit(0); docNum >= 0; docNum = matchingDocuments.nextSetBit(docNum + 1)) {
                if (cancel.get()) return Collections.emptyMap();

                final Document doc = reader.document(docNum);

                found.addAll(Arrays.asList(doc.getValues("path")));
                wasFound = true;
            }

            if (wasFound) {
                result.put(hash, found);
            }
        }

        return result;
    }

}
