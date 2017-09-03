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
package org.netbeans.modules.jackpot30.backend.impl.spi;

import java.net.URISyntaxException;
import java.net.URL;
import org.apache.lucene.index.IndexWriter;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class IndexAccessor {

    private final FileObject root;
    private final IndexWriter w;

    public IndexAccessor(IndexWriter w, FileObject root) {
        this.w = w;
        this.root = root;
    }

    public IndexWriter getIndexWriter() {
        return w;
    }

    public String getPath(URL file) {
        try {
            return root.toURI().relativize(file.toURI()).toString();
        } catch (URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
        }

        return file.toExternalForm();
    }

    public boolean isAcceptable(URL file) {
        return file.toString().startsWith(root.toURL().toString());
    }

    public static IndexAccessor current;
    public static IndexAccessor getCurrent() {
        return current;
    }
}
