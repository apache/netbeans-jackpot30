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
package org.netbeans.modules.jackpot30.indexer.source;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.document.CompressionTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.jackpot30.backend.impl.spi.IndexAccessor;
import org.netbeans.modules.jackpot30.backend.impl.spi.Utilities;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexer;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexerFactory;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;

/**
 *
 * @author lahvac
 */
public class SourceIndexer extends CustomIndexer {

    private static final String KEY_CONTENT = "content";

    @Override
    protected void index(Iterable<? extends Indexable> files, Context context) {
        try {
            for (Indexable i : files) {
                if (!IndexAccessor.getCurrent().isAcceptable(i.getURL())) continue;
                String relPath = IndexAccessor.getCurrent().getPath(i.getURL());

                if (relPath == null) continue;

                FileObject file = URLMapper.findFileObject(i.getURL());

                if (file == null) {
                    //TODO: log
                    continue;
                }
                
                Document doc = new Document();

                doc.add(new Field("relativePath", relPath, Store.YES, Index.NOT_ANALYZED));
                doc.add(new Field(KEY_CONTENT, CompressionTools.compressString(Utilities.readFully(file)), Store.YES));
                doc.add(new Field("fileMimeType", file.getMIMEType(), Store.YES, Index.NO));
                doc.add(new Field("sizeInBytes", Long.toString(file.getSize()), Store.YES, Index.NO));

                IndexAccessor.getCurrent().getIndexWriter().addDocument(doc);
            }
        } catch (IOException ex) {
            Logger.getLogger(SourceIndexer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @MimeRegistration(mimeType="", service=CustomIndexerFactory.class)
    public static final class FactoryImpl extends CustomIndexerFactory {

        @Override
        public CustomIndexer createIndexer() {
            return new SourceIndexer();
        }

        @Override
        public boolean supportsEmbeddedIndexers() {
            return true;
        }

        @Override
        public void filesDeleted(Iterable<? extends Indexable> deleted, Context context) {
            assert !deleted.iterator().hasNext();
            //TODO: ability to delete from the index:
//            try {
//                DocumentIndex idx = IndexManager.createDocumentIndex(FileUtil.toFile(context.getIndexFolder()));
//
//                for (Indexable i : deleted) {
//                    idx.removeDocument(i.getRelativePath());
//                }
//
//                idx.close();
//            } catch (IOException ex) {
//                Logger.getLogger(SourceIndexer.class.getName()).log(Level.SEVERE, null, ex);
//            }
        }

        @Override
        public void filesDirty(Iterable<? extends Indexable> dirty, Context context) {
        }

        @Override
        public String getIndexerName() {
            return "fullsource";
        }

        @Override
        public int getIndexVersion() {
            return 1;
        }

    }
}
