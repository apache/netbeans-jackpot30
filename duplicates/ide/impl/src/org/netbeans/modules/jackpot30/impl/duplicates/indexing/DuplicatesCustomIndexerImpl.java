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
import java.net.URL;
import java.util.Collection;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.Task;
import org.netbeans.modules.parsing.impl.indexing.IndexableImpl;
import org.netbeans.modules.parsing.impl.indexing.SPIAccessor;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class DuplicatesCustomIndexerImpl extends DeferredCustomIndexer {

    public DuplicatesCustomIndexerImpl(DeferredCustomIndexerFactory factory) {
        super(factory);
    }

    protected void doIndex(final DeferredContext ctx, Collection<? extends FileObject> modifiedAndAdded, Collection<? extends String> removed) throws IOException {
        final DuplicatesIndex[] w = new DuplicatesIndex[1];

        try {
            w[0] = new DuplicatesIndex(ctx.getRoot(), ctx.getCacheRoot());

            for (String r : removed) {
                w[0].remove(r);
                ctx.handledRemovedFile(r);
            }

            final ClasspathInfo cpInfo = ClasspathInfo.create(ctx.getRootFileObject());

            JavaSource.create(cpInfo, modifiedAndAdded).runUserActionTask(new Task<CompilationController>() {
                public void run(final CompilationController cc) throws Exception {
                    if (cc.toPhase(Phase.RESOLVED).compareTo(Phase.RESOLVED) < 0)
                        return ;

                    w[0].record(cc, SPIAccessor.getInstance().create(new IndexableImpl() {
                        @Override public String getRelativePath() {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }
                        @Override public URL getURL() {
                            return cc.getFileObject().toURL();
                        }
                        @Override public String getMimeType() {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }
                        @Override public boolean isTypeOf(String mimeType) {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }
                    }), cc.getCompilationUnit());

                    ctx.handledModifiedFile(cc.getFileObject());
                }
            }, true);
        } finally {
            if (w[0] != null) {
                try {
                    w[0].close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }
    
    public static final class FactoryImpl extends DeferredCustomIndexerFactory {

        @Override
        public DeferredCustomIndexer createIndexer() {
            return new DuplicatesCustomIndexerImpl(this);
        }

        @Override
        public String getIndexerName() {
            return DuplicatesIndex.NAME;
        }

        @Override
        public int getIndexVersion() {
            return DuplicatesIndex.VERSION;
        }

    }

}
