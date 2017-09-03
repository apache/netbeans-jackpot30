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

import org.netbeans.modules.jackpot30.common.api.IndexAccess;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.document.CompressionTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.java.hints.spiimpl.pm.BulkSearch;
import org.netbeans.modules.java.hints.spiimpl.pm.BulkSearch.EncodingContext;
import org.netbeans.modules.java.preprocessorbridge.spi.JavaIndexerPlugin;
import org.netbeans.modules.java.source.indexing.JavaIndex;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author lahvac
 */
public final class Indexer implements JavaIndexerPlugin {

    public static final String INDEX_NAME = "jackpot30";
    private final @NonNull URL root;
    private final @NonNull FileObject cacheRoot;
    private final @NonNull IndexAccess access;

    private  Indexer(URL root, FileObject cacheRoot) {
        this.root = root;
        this.cacheRoot = cacheRoot;
        this.access = Lookup.getDefault().lookup(IndexAccess.class);
    }
    
    @Override
    public void process (@NonNull CompilationUnitTree toProcess, @NonNull Indexable indexable, @NonNull Lookup services) {
        IndexWriter luceneWriter = access.getIndexWriter(root, cacheRoot, INDEX_NAME);
        String relative = access.getRelativePath(indexable);
        ByteArrayOutputStream out = null;
        EncodingContext ec;

        try {
            out = new ByteArrayOutputStream();

            ec = new EncodingContext(out, false);

            BulkSearch.getDefault().encode(toProcess, ec, new AtomicBoolean());

            luceneWriter.deleteDocuments(new Term("languagePath", relative));

            Document doc = new Document();

            doc.add(new Field("languageContent", new TokenStreamImpl(ec.getContent())));
            out.close();
            doc.add(new Field("languageEncoded", CompressionTools.compress(out.toByteArray()), Field.Store.YES));
            doc.add(new Field("languagePath", relative, Field.Store.YES, Field.Index.NOT_ANALYZED));

            if (services != null) {
                final Set<String> erased = new HashSet<String>();
                final Trees trees = services.lookup(Trees.class);
                final Types types = services.lookup(Types.class);

                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void scan(Tree tree, Void p) {
                        if (tree != null) {
                            TreePath tp = new TreePath(getCurrentPath(), tree);
                            TypeMirror type = trees.getTypeMirror(tp);

                            if (type != null) {
                                if (type.getKind() == TypeKind.ARRAY) {
                                    erased.add(types.erasure(type).toString());
                                    type = ((ArrayType) type).getComponentType();
                                }

                                if (type.getKind().isPrimitive() || type.getKind() == TypeKind.DECLARED) {
                                    addErasedTypeAndSuperTypes(types, erased, type);
                                }
                            }

                            //bounds for type variables!!!
                        }
                        return super.scan(tree, p);
                    }
                }.scan(toProcess, null);

                doc.add(new Field("languageErasedTypes", new TokenStreamImpl(erased)));
            }
            
            luceneWriter.addDocument(doc);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            Logger.getLogger(Indexer.class.getName()).log(Level.WARNING, null, t);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }


    @Override
    public void delete (@NonNull Indexable indexable) {
        IndexWriter luceneWriter = access.getIndexWriter(root, cacheRoot, INDEX_NAME);
        String relative = access.getRelativePath(indexable);
        
        try {
            luceneWriter.deleteDocuments(new Term("languagePath", relative));
        } catch (CorruptIndexException ex) {
            Logger.getLogger(Indexer.class.getName()).log(Level.WARNING, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Indexer.class.getName()).log(Level.WARNING, null, ex);
        }
    }

    @Override
    public void finish () {
        access.finish();
    }

    private static void addErasedTypeAndSuperTypes(Types javacTypes, Set<String> types, TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            if (types.add(javacTypes.erasure(type).toString())) {
                for (TypeMirror sup : javacTypes.directSupertypes(type)) {
                    addErasedTypeAndSuperTypes(javacTypes, types, sup);
                }
            }
        } else if (type.getKind().isPrimitive()) {
            types.add(type.toString());
        }
    }

    public static final class TokenStreamImpl extends TokenStream {

        private final Iterator<? extends String> tokens;
        private final TermAttribute termAtt;

        public TokenStreamImpl(Iterable<? extends String> tokens) {
            this.tokens = tokens != null ? tokens.iterator() : /*???*/Collections.<String>emptyList().iterator();
            this.termAtt = addAttribute(TermAttribute.class);
        }

        @Override
        public boolean incrementToken() throws IOException {
            if (!tokens.hasNext())
                return false;

            String t = tokens.next();

            termAtt.setTermBuffer(t);
            
            return true;
        }
    }

    public static @NonNull FileObject resolveCacheFolder(@NonNull URL sourceRoot) throws IOException {
        FileObject dataFolder = CacheFolder.getDataFolder(sourceRoot);
        
        return FileUtil.createFolder(dataFolder, JavaIndex.NAME + "/" + JavaIndex.VERSION);
    }
    
    @MimeRegistration(mimeType="text/x-java", service=Factory.class)
    public static final class FactoryImpl implements Factory {

        @Override
        public JavaIndexerPlugin create(URL root, FileObject cacheFolder) {
            return new Indexer(root, cacheFolder);
        }
        
    }
}
