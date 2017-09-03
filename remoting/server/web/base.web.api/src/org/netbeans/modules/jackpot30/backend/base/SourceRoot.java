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
package org.netbeans.modules.jackpot30.backend.base;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.JarFileSystem;

/**
 *
 * @author lahvac
 */
public class SourceRoot {
    
    private static final Logger LOG = Logger.getLogger(SourceRoot.class.getName());
    private final CategoryStorage category;
    private final String relativePath;
    private final String code;

    SourceRoot(CategoryStorage category, String relativePath, String code) {
        this.category = category;
        this.relativePath = relativePath;
        this.code = code;
    }

    public CategoryStorage getCategory() {
        return category;
    }

    public String getRelativePath() {
        return relativePath;
    }

    private Reference<Collection<FileObject>> classPath;

    public synchronized Collection<FileObject> getClassPath() {
        Collection<FileObject> cp = classPath != null ? classPath.get() : null;
        if (cp == null) {
            classPath = new SoftReference<Collection<FileObject>>(cp = computeClassPath());
        }

        return cp;
    }

    public String getCode() {
        return code;
    }

    public String getClassPathString() {
        try {
            InputStream in = category.getCacheRoot().getFileObject("classpath").getInputStream();
            Properties props = new Properties();

            try {
                props.load(in);
            } finally {
                in.close();
            }

            return props.getProperty(code);
        } catch (IOException ex) {
            Logger.getLogger(SourceRoot.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    private Collection<FileObject> computeClassPath() {
        Collection<FileObject> result = new ArrayList<FileObject>();

        try {
            final String bootPath = System.getProperty("sun.boot.class.path");

            for (String bp : bootPath.split(":")) {
                File f = new File(bp);

                if (!f.canRead()) continue;

                FileObject root = new JarFileSystem(f).getRoot();

                result.add(root);
            }

            String classpath = getClassPathString();

            if (classpath != null) {
                for (String entry : classpath.split(":")) {
                    FileObject root = category.getEmbeddedJarRoot(entry);

                    if (!entry.endsWith(".jar")) {
                        root = root.getFileObject("java/15/classes");
                    }

                    if (root != null) {
                        result.add(root);
                    } else {
                        LOG.log(Level.FINE, "Cannot find {0}", entry);
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();//XXX
        }

        return result;
    }
}
