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
package org.netbeans.modules.jackpot30.impl.duplicates;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.modules.jackpot30.common.api.LuceneHelpers.BitSetCollector;
import org.netbeans.modules.jackpot30.impl.duplicates.indexing.DuplicatesCustomIndexerImpl;
import org.netbeans.modules.jackpot30.impl.duplicates.indexing.DuplicatesIndex;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;


/**
 *
 * @author lahvac
 */
public class ComputeDuplicates {

    public Iterator<? extends DuplicateDescription> computeDuplicatesForAllOpenedProjects(ProgressHandle progress, AtomicBoolean cancel) throws IOException {
        Set<URL> urls = new HashSet<URL>();

        for (ClassPath cp : GlobalPathRegistry.getDefault().getPaths(ClassPath.SOURCE)) {
            for (ClassPath.Entry e : cp.entries()) {
                urls.add(e.getURL());
            }
        }

        long start = System.currentTimeMillis();
        try {
            return computeDuplicates(urls, progress, cancel);
        } finally {
            System.err.println("duplicates for all open projects: " + (System.currentTimeMillis() - start));
        }
    }

    public Iterator<? extends DuplicateDescription> computeDuplicates(Set<URL> forURLs, ProgressHandle progress, AtomicBoolean cancel) throws IOException {
        Map<IndexReader, FileObject> readers2Roots = new LinkedHashMap<IndexReader, FileObject>();

        progress.progress("Updating indices");

        for (URL u : forURLs) {
            try {
                //TODO: needs to be removed for server mode
                new DuplicatesCustomIndexerImpl.FactoryImpl().updateIndex(u, cancel); //TODO: show updating progress to the user
                
                File cacheRoot = cacheRoot(u);

                File dir = new File(cacheRoot, DuplicatesIndex.NAME);

                if (dir.listFiles() != null && dir.listFiles().length > 0) {
                    IndexReader reader = IndexReader.open(FSDirectory.open(dir), true);

                    readers2Roots.put(reader, URLMapper.findFileObject(u));
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        
        progress.progress("Searching for duplicates");

        MultiReader r = new MultiReader(readers2Roots.keySet().toArray(new IndexReader[0]));

        List<String> dd = new ArrayList<String>(getDuplicatedValues(r, "duplicatesGeneralized", cancel));

        sortHashes(dd);

        //TODO: only show valuable duplicates?:
//        dd = dd.subList(0, dd.size() / 10 + 1);

        return new DuplicatesIterator(readers2Roots, dd, 2);
    }

    public static Iterator<? extends DuplicateDescription> XXXduplicatesOf(Map<IndexReader, FileObject> readers2Roots, Collection<String> hashes) {
        List<String> hashesList = new ArrayList<String>(hashes);
        sortHashes(hashesList);
        return new DuplicatesIterator(readers2Roots, hashesList, 1);
    }

    private static File cacheRoot(URL sourceRoot) throws IOException {
        FileObject dataFolder = CacheFolder.getDataFolder(sourceRoot);
        FileObject cacheFO  = dataFolder.getFileObject(DuplicatesIndex.NAME + "/" +DuplicatesIndex.VERSION);
        File cache = cacheFO != null ? FileUtil.toFile(cacheFO) : null;
        
        return cache;
    }
    
    private static final class DuplicatesIterator implements Iterator<DuplicateDescription> {
        private final Map<IndexReader, FileObject> readers2Roots;
        private final Iterator<String> duplicateCandidates;
        private final int minDuplicates;
        private final List<DuplicateDescription> result = new LinkedList<DuplicateDescription>();

        public DuplicatesIterator(Map<IndexReader, FileObject> readers2Roots, Iterable<String> duplicateCandidates, int minDuplicates) {
            this.readers2Roots = readers2Roots;
            this.duplicateCandidates = duplicateCandidates.iterator();
            this.minDuplicates = minDuplicates;
        }

        private DuplicateDescription nextDescription() throws IOException {
        while (duplicateCandidates.hasNext()) {
            String longest = duplicateCandidates.next();
            List<Span> foundDuplicates = new LinkedList<Span>();

            Query query = new TermQuery(new Term("duplicatesGeneralized", longest));

            for (Entry<IndexReader, FileObject> e : readers2Roots.entrySet()) {
                Searcher s = new IndexSearcher(e.getKey());
                BitSet matchingDocuments = new BitSet(e.getKey().maxDoc());
                Collector c = new BitSetCollector(matchingDocuments);

                s.search(query, c);

                for (int docNum = matchingDocuments.nextSetBit(0); docNum >= 0; docNum = matchingDocuments.nextSetBit(docNum + 1)) {
                    final Document doc = e.getKey().document(docNum);
                    int pos = Arrays.binarySearch(doc.getValues("duplicatesGeneralized"), longest);

                    if (pos < 0) {
                        continue;
                    }
                    
                    String spanSpec = doc.getValues("duplicatesPositions")[pos];
                    String relPath = doc.getField("duplicatesPath").stringValue();

                    for (String spanPart : spanSpec.split(";")) {
                        Span span = Span.of(e.getValue().getFileObject(relPath), spanPart);

                        if (span != null) {
                            foundDuplicates.add(span);
                        }
                    }
                }
            }

            if (foundDuplicates.size() >= minDuplicates) {
                DuplicateDescription current = DuplicateDescription.of(foundDuplicates, getValue(longest), longest);
                boolean add = true;

                for (Iterator<DuplicateDescription> it = result.iterator(); it.hasNext();) {
                    DuplicateDescription existing = it.next();

                    if (subsumes(existing, current)) {
                        add = false;
                        break;
                    }

                    if (subsumes(current, existing)) {
                        //can happen? (note that the duplicates are sorted by value)
                        it.remove();
                    }
                }

                if (add) {
                    result.add(current);
                    return current;
                }
            }

        }
        return null;
        }

        private DuplicateDescription next;

        public boolean hasNext() {
            if (next == null) {
                try {
                    next = nextDescription();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }

            return next != null;
        }

        public DuplicateDescription next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            DuplicateDescription r = next;

            next = null;
            return r;
        }

        public void remove() {
            throw new UnsupportedOperationException("Not supported.");
        }

    }

    private static List<String> getDuplicatedValues(IndexReader ir, String field, AtomicBoolean cancel) throws IOException {
        List<String> values = new ArrayList<String>();
        TermEnum terms = ir.terms( new Term(field));
        //while (terms.next()) {
        do {
            if (cancel.get()) return Collections.emptyList();

            final Term term =  terms.term();

            if ( !field.equals( term.field() ) ) {
                break;
            }

            if (terms.docFreq() < 2) continue;

            values.add(term.text());
        }
        while (terms.next());
        return values;
    }

    private static long getValue(String encoded) {
        return Long.parseLong(encoded.substring(encoded.lastIndexOf(":") + 1));
    }

    private static void sortHashes(List<String> hashes) {
        Collections.sort(hashes, new Comparator<String>() {
            public int compare(String arg0, String arg1) {
                return (int) Math.signum(getValue(arg1) - getValue(arg0));
            }
        });
    }
    
    private static boolean subsumes(DuplicateDescription bigger, DuplicateDescription smaller) {
        Set<FileObject> bFiles = new HashSet<FileObject>();

        for (Span s : bigger.dupes) {
            bFiles.add(s.file);
        }

        Set<FileObject> sFiles = new HashSet<FileObject>();

        for (Span s : smaller.dupes) {
            sFiles.add(s.file);
        }

        if (!bFiles.equals(sFiles)) return false;

        Span testAgainst = bigger.dupes.get(0);

        for (Span s : smaller.dupes) {
            if (s.file == testAgainst.file) {
                if (   (testAgainst.startOff <= s.startOff && testAgainst.endOff > s.endOff)
                    || (testAgainst.startOff < s.startOff && testAgainst.endOff >= s.endOff)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static Map<String, long[]> encodeGeneralized(CompilationInfo info) {
        return encodeGeneralized(info.getTrees(), info.getCompilationUnit());
    }

    public static Map<String, long[]> encodeGeneralized(final Trees trees, final CompilationUnitTree cut) {
        final SourcePositions sp = trees.getSourcePositions();
        final Map<String, Collection<Long>> positions = new HashMap<String, Collection<Long>>();

        new TreePathScanner<Void, Void>() {
            @Override
            public Void scan(Tree tree, Void p) {
                if (tree == null) return null;
                if (getCurrentPath() != null) {
                    DigestOutputStream baos = null;
                    PrintWriter out = null;
                    try {
                        baos = new DigestOutputStream(new ByteArrayOutputStream(), MessageDigest.getInstance("MD5"));
                        out = new PrintWriter(new OutputStreamWriter(baos, "UTF-8"));
                        GeneralizePattern gen = new GeneralizePattern(out, trees);
                        gen.scan(new TreePath(getCurrentPath(), tree), null);
                        out.close();
                        if (gen.value >= MINIMAL_VALUE) {
                            StringBuilder text = new StringBuilder();
                            byte[] bytes = baos.getMessageDigest().digest();
                            for (int cntr = 0; cntr < 4; cntr++) {
                                text.append(String.format("%02X", bytes[cntr]));
                            }
                            text.append(':').append(gen.value);
                            String enc = text.toString();
                            Collection<Long> spanSpecs = positions.get(enc);
                            if (spanSpecs == null) {
                                positions.put(enc, spanSpecs = new LinkedList<Long>());
//                            } else {
//                                spanSpecs.append(";");
                            }
                            long start = sp.getStartPosition(cut, tree);
//                            spanSpecs.append(start).append(":").append(sp.getEndPosition(cut, tree) - start);
                            spanSpecs.add(start);
                            spanSpecs.add(sp.getEndPosition(cut, tree));
                        }
                    } catch (UnsupportedEncodingException ex) {
                        Exceptions.printStackTrace(ex);
                    } catch (NoSuchAlgorithmException ex) {
                        Exceptions.printStackTrace(ex);
                    } finally {
                        try {
                            baos.close();
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                        out.close();
                    }
                }
                return super.scan(tree, p);
            }
        }.scan(cut, null);

        Map<String, long[]> result = new TreeMap<String, long[]>();

        for (Entry<String, Collection<Long>> e : positions.entrySet()) {
            long[] spans = new long[e.getValue().size()];
            int idx = 0;

            for (Long l : e.getValue()) {
                spans[idx++] = l;
            }

            result.put(e.getKey(), spans);
        }

        return result;
    }

    private static final class GeneralizePattern extends TreePathScanner<Void, Void> {

        public final Map<Tree, Tree> tree2Variable = new HashMap<Tree, Tree>();
        private final Map<Element, String> element2Variable = new HashMap<Element, String>();
        private final PrintWriter to;
        private final Trees javacTrees;
        private long value;

        private int currentVariableIndex = 0;

        public GeneralizePattern(PrintWriter to, Trees javacTrees) {
            this.to = to;
            this.javacTrees = javacTrees;
        }

        private @NonNull String getVariable(@NonNull Element el) {
            String var = element2Variable.get(el);

            if (var == null) {
                element2Variable.put(el, var = "$" + currentVariableIndex++);
            }

            return var;
        }

        private boolean shouldBeGeneralized(@NonNull Element el) {
            if (el.getModifiers().contains(Modifier.PRIVATE)) {
                return true;
            }

            switch (el.getKind()) {
                case LOCAL_VARIABLE:
                case EXCEPTION_PARAMETER:
                case PARAMETER:
                    return true;
            }

            return false;
        }

        @Override
        public Void scan(Tree tree, Void p) {
            if (tree != null) {
                to.append(tree.getKind().name());
                value++;
            }
            return super.scan(tree, p);
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void p) {
            Element e = javacTrees.getElement(getCurrentPath());

            if (e != null && shouldBeGeneralized(e)) {
                to.append(getVariable(e));
                value--;
                return null;
            } else {
                to.append(node.getName());
            }

            return super.visitIdentifier(node, p);
        }

        @Override
        public Void visitVariable(VariableTree node, Void p) {
            Element e = javacTrees.getElement(getCurrentPath());

            if (e != null && shouldBeGeneralized(e)) {
                to.append(getVariable(e));
            } else {
                to.append(node.getName());
            }

            return super.visitVariable(node, p);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void p) {
            return null;
        }

    }

    private static final int MINIMAL_VALUE = 10;

    public static final class DuplicateDescription {

        public final List<Span> dupes;
        public final long value;
        public final String hash;

        private DuplicateDescription(List<Span> dupes, long value, String hash) {
            this.dupes = dupes;
            this.value = value;
            this.hash = hash;
        }

        public static DuplicateDescription of(List<Span> dupes, long value, String hash) {
            return new DuplicateDescription(dupes, value, hash);
        }
    }

    public static final class Span {
        public final FileObject file;
        public final int startOff;
        public final int endOff;

        public Span(FileObject file, int startOff, int endOff) {
            this.file = file;
            this.startOff = startOff;
            this.endOff = endOff;
        }

        public static @CheckForNull Span of(FileObject file, String spanSpec) {
            String[] split = spanSpec.split(":");
            int start = Integer.valueOf(split[0]);
            int end = start + Integer.valueOf(split[1]);
            if (start < 0 || end < 0) return null; //XXX

            return new Span(file, start, end);
        }

    }
}
