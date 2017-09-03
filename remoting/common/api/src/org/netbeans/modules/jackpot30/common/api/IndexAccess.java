/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2012 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2012 Sun Microsystems, Inc.
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
