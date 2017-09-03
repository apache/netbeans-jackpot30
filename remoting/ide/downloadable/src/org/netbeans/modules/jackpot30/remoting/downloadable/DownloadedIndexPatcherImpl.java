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
package org.netbeans.modules.jackpot30.remoting.downloadable;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.JavaClassPathConstants;
import org.netbeans.api.java.queries.AnnotationProcessingQuery;
import org.netbeans.api.java.queries.AnnotationProcessingQuery.Result;
import org.netbeans.api.java.queries.SourceLevelQuery;
import org.netbeans.modules.parsing.impl.indexing.friendapi.DownloadedIndexPatcher;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=DownloadedIndexPatcher.class)
public class DownloadedIndexPatcherImpl implements DownloadedIndexPatcher {

    @Override
    public boolean updateIndex(URL sourceRoot, URL indexFolder) {
        try {
            File cache = new File(indexFolder.toURI());
            File checksums = new File(cache, "java/15/checksums.properties");

            if (!checksums.canRead()) return true; //nothing to fix

            FileObject srcFolderFO = URLMapper.findFileObject(sourceRoot);

            if (srcFolderFO == null) return false;

            Properties cs = loadProperties(checksums);

            if (cs.isEmpty()) return true; //apparently nothing to do

            //XXX HACK:
            String origPrefix = null;
            String in = (String) cs.keySet().iterator().next();

            int idx = Integer.MAX_VALUE;

            while ((idx = in.lastIndexOf('/', idx - 1)) != (-1)) {
                FileObject foundChild = srcFolderFO.getFileObject(in.substring(idx + 1));
                if (foundChild != null && foundChild.canRead()) {
                    origPrefix = in.substring(0, idx + 1);
                    break;
                }
            }

            if (origPrefix == null) {
                //cannot find the original prefix
                return false;
            }

            String newPrefix = srcFolderFO.toURL().toString();

            fixAbsolutePath(checksums, origPrefix, newPrefix);
            fixAbsolutePath(new File(cache, "java/15/fqn2files.properties"), origPrefix, newPrefix);

            if (srcFolderFO != null) {
                verifyAttributes(srcFolderFO, indexFolder, false);
                ensureSourcePath(srcFolderFO, indexFolder);
            }

            return true;
        } catch (URISyntaxException ex) {
            Logger.getLogger(DownloadedIndexPatcherImpl.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } catch (IOException ex) {
            Logger.getLogger(DownloadedIndexPatcherImpl.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    private void fixAbsolutePath(File properties, String origPrefix, String targetPrefix) throws IOException {
        Properties inProps = loadProperties(properties);
        Properties outProps = new Properties();

        for (String k : (Collection<String>) (Collection) inProps.keySet()) {
            String orig = inProps.getProperty(k);

            //XXX HACK

            if (k.startsWith(origPrefix)) k = targetPrefix + k.substring(origPrefix.length());
            if (orig.startsWith(origPrefix)) orig = targetPrefix + orig.substring(origPrefix.length());

            outProps.setProperty(k, orig);
        }
        
        storeProperties(properties, outProps);
    }

    private static final String PROCESSOR_PATH = "processorPath"; //NOI18N
    private static final String APT_ENABLED = "aptEnabled"; //NOI18N
    private static final String ANNOTATION_PROCESSORS = "annotationProcessors"; //NOI18N
    private static final String SOURCE_LEVEL_ROOT = "sourceLevel"; //NOI18N
    private static final String SOURCE_PATH = "sourcePath"; //NOI18N

    boolean verifyAttributes(FileObject root, URL cache, boolean checkOnly) {
        if (root == null)
            return false;
        boolean vote = false;
        try {
            if (ensureAttributeValue(cache, SOURCE_LEVEL_ROOT, SourceLevelQuery.getSourceLevel(root), checkOnly)) {
                vote = true;
                if (checkOnly) {
                    return vote;
                }
            }
            Result aptOptions = AnnotationProcessingQuery.getAnnotationProcessingOptions(root);
            boolean apEnabledOnScan = aptOptions.annotationProcessingEnabled().contains(AnnotationProcessingQuery.Trigger.ON_SCAN);
            if (ensureAttributeValue(cache, APT_ENABLED, apEnabledOnScan ? Boolean.TRUE.toString() : null, checkOnly)) {
                vote = true;
                if (checkOnly) {
                    return vote;
                }
            }
            if (!apEnabledOnScan) {
                //no need to check further:
                return vote;
            }
            ClassPath processorPath = ClassPath.getClassPath(root, JavaClassPathConstants.PROCESSOR_PATH);
            if (processorPath != null && ensureAttributeValue(cache, PROCESSOR_PATH, processorPath.toString(), checkOnly)) {
                vote = true;
                if (checkOnly) {
                    return vote;
                }
            }
            if (ensureAttributeValue(cache, ANNOTATION_PROCESSORS, encodeToStirng(aptOptions.annotationProcessorsToRun()), checkOnly)) {
                vote = true;
                if (checkOnly) {
                    return vote;
                }
            }
        } catch (URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ioe) {
            Exceptions.printStackTrace(ioe);
        }
        return vote;
    }

    public static boolean ensureAttributeValue(final URL root, final String attributeName, final String attributeValue, boolean checkOnly) throws IOException, URISyntaxException {
        File rootFile = new File(root.toURI());
        File attributes = new File(rootFile, "java/15/attributes.properties");
        Properties p = loadProperties(attributes);
        final String current = p.getProperty(attributeName);
        if (current == null) {
            if (attributeValue != null) {
                if (!checkOnly) {
                    p.setProperty(attributeName, attributeValue);
                    storeProperties(attributes, p);
                }
                return true;
            } else {
                return false;
            }
        }
        if (current.equals(attributeValue)) {
            return false;
        }
        if (!checkOnly) {
            if (attributeValue != null) {
                p.setProperty(attributeName, attributeValue);
            } else {
                p.remove(attributeName);
            }
            storeProperties(attributes, p);
        }
        return true;
    }

    private String encodeToStirng(Iterable<? extends String> strings) {
        if (strings == null)
            return null;
        StringBuilder sb = new StringBuilder();
        for (Iterator it = strings.iterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext())
                sb.append(',');
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
    
    private static boolean ensureSourcePath(final FileObject root, URL cache) throws IOException, URISyntaxException {
        final ClassPath srcPath = ClassPath.getClassPath(root, ClassPath.SOURCE);
        String srcPathStr;
        if (srcPath != null) {
            final StringBuilder sb = new StringBuilder();
            for (ClassPath.Entry entry : srcPath.entries()) {
                sb.append(entry.getURL()).append(' ');  //NOI18N
            }
            srcPathStr = sb.toString();
        } else {
            srcPathStr = "";    //NOI18N
        }
        return ensureAttributeValue(cache, SOURCE_PATH, srcPathStr, false);
    }

    private static Properties loadProperties(File properties) throws IOException, FileNotFoundException {
        Properties inProps = new Properties();
        InputStream inPropsIS = new FileInputStream(properties);
        try {
            inProps.load(inPropsIS);
            inPropsIS.close();
        } finally {
            inPropsIS.close();
        }
        return inProps;
    }
    
    private static void storeProperties(File properties, Properties outProps) throws IOException, FileNotFoundException {
        OutputStream outPropsOS = new FileOutputStream(properties);

        try {
            outProps.store(outPropsOS, "");
            outPropsOS.close();
        } finally {
            outPropsOS.close();
        }
    }

}
