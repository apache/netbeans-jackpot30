/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2011 Sun Microsystems, Inc.
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
