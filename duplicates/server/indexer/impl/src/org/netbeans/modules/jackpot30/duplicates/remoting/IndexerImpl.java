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
package org.netbeans.modules.jackpot30.duplicates.remoting;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URL;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.jackpot30.common.api.IndexAccess;
import org.netbeans.modules.jackpot30.impl.duplicates.indexing.DuplicatesIndex;
import org.netbeans.modules.java.preprocessorbridge.spi.JavaIndexerPlugin;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author lahvac
 */
public class IndexerImpl implements JavaIndexerPlugin {

    private final DuplicatesIndex index;

    public IndexerImpl(URL root, FileObject cacheFolder) {
        try {
            index = new DuplicatesIndex(root, cacheFolder);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void process(CompilationUnitTree toProcess, Indexable indexable, Lookup services) {
        try {
            index.record(services.lookup(Trees.class), indexable, toProcess);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public void delete(Indexable indexable) {
        try {
            index.remove(Lookup.getDefault().lookup(IndexAccess.class).getRelativePath(indexable));
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public void finish() { }

    @MimeRegistration(mimeType="text/x-java", service=Factory.class)
    public static final class FactoryImpl implements Factory {

        @Override
        public JavaIndexerPlugin create(URL root, FileObject cacheFolder) {
            return new IndexerImpl(root, cacheFolder);
        }

    }
}
