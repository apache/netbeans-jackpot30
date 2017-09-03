/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2012 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2012 Sun Microsystems, Inc.
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
