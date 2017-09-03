/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2013 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2013 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.indexer.usages;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.jackpot30.backend.impl.spi.IndexAccessor;
import org.netbeans.modules.jackpot30.backend.impl.spi.Utilities;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexer;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexerFactory;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class ResourceIndexerImpl extends CustomIndexer {

    @Override
    protected void index(Iterable<? extends Indexable> files, Context context) {
        for (Indexable indexable : files) {
            doIndexFile(indexable);
        }
    }

    private static final Pattern INTERESTING_PATTERN = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*([.-]\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)+");

    private void doIndexFile(Indexable indexable) {
        if (!IndexAccessor.getCurrent().isAcceptable(indexable.getURL()) || "text/x-java".equals(indexable.getMimeType())) return;
        try {
            doDelete(indexable);

            FileObject file = URLMapper.findFileObject(indexable.getURL());

            if (file == null) return ;

            final String relative = IndexAccessor.getCurrent().getPath(indexable.getURL());
            final Document usages = new Document();

            usages.add(new Field("file", relative, Store.YES, Index.NOT_ANALYZED));
            usages.add(new Field(IndexerImpl.KEY_MARKER, "true", Store.NO, Index.NOT_ANALYZED));

            //sources indexer does the same, we should really look into files only once!
            String content = Utilities.readFully(file);
            Matcher matcher = INTERESTING_PATTERN.matcher(content);
            Set<String> SEEN_SIGNATURES = new HashSet<String>();

            while (matcher.find()) {
                String reference = matcher.group();
                String[] elements = reference.split("[.-]");
                StringBuilder currentElement = new StringBuilder();

                currentElement.append(elements[0]);

                for (int i = 1; i < elements.length; i++) {
                    currentElement.append(".");
                    currentElement.append(elements[i]);
                    String serialized = "OTHER:" + currentElement.toString();

                    if (SEEN_SIGNATURES.add(serialized)) {
                        usages.add(new Field(IndexerImpl.KEY_SIGNATURES, serialized, Store.YES, Index.NOT_ANALYZED));
                    }
                }
            }

            IndexAccessor.getCurrent().getIndexWriter().addDocument(usages);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private static void doDelete(Indexable indexable) throws IOException {
        BooleanQuery q = new BooleanQuery();

        q.add(new BooleanClause(new TermQuery(new Term("file", IndexAccessor.getCurrent().getPath(indexable.getURL()))), Occur.MUST));
        q.add(new BooleanClause(new TermQuery(new Term(IndexerImpl.KEY_MARKER, "true")), Occur.MUST));

        IndexAccessor.getCurrent().getIndexWriter().deleteDocuments(q);
    }

    @MimeRegistration(mimeType="", service=CustomIndexerFactory.class)
    public static final class FactoryImpl extends CustomIndexerFactory {

        @Override
        public CustomIndexer createIndexer() {
            return new ResourceIndexerImpl();
        }

        @Override
        public boolean supportsEmbeddedIndexers() {
            return true;
        }

        @Override
        public void filesDeleted(Iterable<? extends Indexable> deleted, Context context) {
            for (Indexable indexable : deleted) {
                try {
                    doDelete(indexable);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

        @Override
        public void filesDirty(Iterable<? extends Indexable> dirty, Context context) { }

        @Override
        public String getIndexerName() {
            return "resource-usages-indexer";
        }

        @Override
        public int getIndexVersion() {
            return 1;
        }
        
    }

}
