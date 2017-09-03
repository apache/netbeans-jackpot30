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

package org.netbeans.modules.jackpot30.common.api;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=IndexAccess.class)
public class IndexAccess {
    
    private URL root;
    private IndexWriter w;
    
    public @NonNull IndexWriter getIndexWriter(@NonNull URL root, @NonNull FileObject cacheRoot, @NonNull String subindexName) {
        if (w == null) {
            this.root = root;
            File cacheRootFile = FileUtil.toFile(cacheRoot);
            try {
                w = new IndexWriter(FSDirectory.open(new File(cacheRootFile, subindexName)), new NoAnalyzer(), IndexWriter.MaxFieldLength.UNLIMITED);
            } catch (CorruptIndexException ex) {
                throw new IllegalStateException(ex);
            } catch (LockObtainFailedException ex) {
                throw new IllegalStateException(ex);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
        
        return w;
    }
    
    public void finish() {
        this.root = null;
        if (w != null) {
            try {
                w.close();
            } catch (CorruptIndexException ex) {
                throw new IllegalStateException(ex);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            w = null;
        }
    }
    
    public String getRelativePath(Indexable i) {
        try {
            return root.toURI().relativize(i.getURL().toURI()).toString();
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(ex);
        }
    }
    
    public static final class NoAnalyzer extends Analyzer {

        @Override
        public TokenStream tokenStream(String string, Reader reader) {
            throw new UnsupportedOperationException("Should not be called");
        }

    }
}
