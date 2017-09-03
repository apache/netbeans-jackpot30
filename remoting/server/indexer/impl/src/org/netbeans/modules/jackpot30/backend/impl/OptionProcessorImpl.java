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

package org.netbeans.modules.jackpot30.backend.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.FSDirectory;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.queries.SourceForBinaryQuery;
import org.netbeans.api.java.queries.SourceForBinaryQuery.Result2;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.modules.jackpot30.backend.impl.spi.IndexAccessor;
import org.netbeans.modules.jackpot30.backend.impl.spi.StatisticsGenerator;
import org.netbeans.modules.java.source.indexing.JavaIndex;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.netbeans.spi.sendopts.Env;
import org.netbeans.spi.sendopts.Option;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.openide.LifecycleManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=OptionProcessor.class)
public class OptionProcessorImpl extends OptionProcessor {

    private static final Logger LOG = Logger.getLogger(OptionProcessorImpl.class.getName());
    private final Option CATEGORY_ID = Option.requiredArgument(Option.NO_SHORT_NAME, "category-id");
    private final Option CATEGORY_NAME = Option.requiredArgument(Option.NO_SHORT_NAME, "category-name");
    private final Option CATEGORY_PROJECTS = Option.additionalArguments(Option.NO_SHORT_NAME, "category-projects");
    private final Option CATEGORY_ROOT_DIR = Option.requiredArgument(Option.NO_SHORT_NAME, "category-root-dir");
    private final Option CACHE_TARGET = Option.requiredArgument(Option.NO_SHORT_NAME, "cache-target");
    private final Option INFO = Option.requiredArgument(Option.NO_SHORT_NAME, "info");
    private final Set<Option> OPTIONS = new HashSet<Option>(Arrays.asList(CATEGORY_ID, CATEGORY_NAME, CATEGORY_PROJECTS, CATEGORY_ROOT_DIR, CACHE_TARGET, INFO));
    private final boolean STORE_CLASSPATH = true;
    
    @Override
    protected Set<Option> getOptions() {
        return OPTIONS;
    }

    @Override
    protected void process(Env env, Map<Option, String[]> optionValues) throws CommandException {
        String categoryId = null;
        String categoryName = null;

        if (optionValues.containsKey(CATEGORY_ID)) {
            categoryId = optionValues.get(CATEGORY_ID)[0];
        }

        if (optionValues.containsKey(CATEGORY_NAME)) {
            categoryName = optionValues.get(CATEGORY_NAME)[0];
        }

        if (optionValues.containsKey(CATEGORY_PROJECTS)) {
            if (categoryId == null) {
                env.getErrorStream().println("Error: no category-id specified!");
                return;
            }

            if (categoryName == null) {
                env.getErrorStream().println("Warning: no category-name specified.");
                return;
            }
        }

        String cacheTarget = optionValues.get(CACHE_TARGET)[0];
        File cache = FileUtil.normalizeFile(new File(cacheTarget));

        cache.getParentFile().mkdirs();

        if (categoryId == null) {
            env.getErrorStream().println("Error: no category-id specified!");
            return;
        }

        File baseDirFile = new File(optionValues.get(CATEGORY_ROOT_DIR)[0]);
        FileObject baseDir = FileUtil.toFileObject(baseDirFile);
        IndexWriter w = null;

        FileObject cacheFolder = CacheFolder.getCacheFolder();
        FileObject cacheTemp = cacheFolder.getFileObject("index");
        Map<String, String> classpath;
        Map<FileObject, String> extraJars = new HashMap<FileObject, String>();

        try {
            if (cacheTemp != null) cacheTemp.delete();

            cacheTemp = cacheFolder.createFolder("index");
            w = new IndexWriter(FSDirectory.open(FileUtil.toFile(cacheTemp)), new KeywordAnalyzer(), IndexWriter.MaxFieldLength.UNLIMITED);

            IndexAccessor.current = new IndexAccessor(w, baseDir);
            Set<FileObject> roots = getRoots(optionValues.get(CATEGORY_PROJECTS), env);

            classpath = indexProjects(roots, extraJars, env);
        } catch (InterruptedException ex) {
            LOG.log(Level.FINE, null, ex);
            throw (CommandException) new CommandException(0).initCause(ex);
        } catch (IOException ex) {
            LOG.log(Level.FINE, null, ex);
            throw (CommandException) new CommandException(0).initCause(ex);
        } finally {
            if (w != null) {
                try {
                    w.optimize(true);
                    w.close(true);
                } catch (CorruptIndexException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

        Map<String, Long> statistics = Collections.emptyMap();
        IndexReader r = null;

        try {
            r = IndexReader.open(FSDirectory.open(FileUtil.toFile(cacheTemp)), true);

            statistics = StatisticsGenerator.generateStatistics(r);
        } catch (CorruptIndexException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

        cacheTemp.refresh();

        JarOutputStream out = null;
        InputStream segments = null;

        try {
            out = new JarOutputStream(new FileOutputStream(cache));
            pack(out, cacheTemp, null, "index", new StringBuilder(categoryId));

            segments = cacheFolder.getFileObject("segments").getInputStream();
            Properties in = new Properties();

            in.load(segments);

            segments.close();//XXX: should be in finally!

            String baseDirPath = baseDirFile.toURI().toString();

            Properties outSegments = new Properties();

            for (String segment : in.stringPropertyNames()) {
                String url = in.getProperty(segment);
                String rel;
                
                if (url.startsWith(baseDirPath)) rel = "rel:/" + url.substring(baseDirPath.length());
                else if (url.startsWith("jar:" + baseDirPath)) rel = "jar:rel:/" + url.substring(4 + baseDirPath.length());
                else rel = url;

                outSegments.setProperty(segment, rel);
            }

            out.putNextEntry(new ZipEntry(categoryId + "/segments"));

            outSegments.store(out, "");

            out.putNextEntry(new ZipEntry(categoryId + "/info"));

            out.write("{\n".getBytes("UTF-8"));
            out.write(("\"displayName\": \"" + categoryName + "\"").getBytes("UTF-8"));
            if (optionValues.containsKey(INFO)) {
                for (String infoValue : optionValues.get(INFO)[0].split(";")) {
                    int eqSign = infoValue.indexOf('=');
                    if (eqSign == (-1)) {
                        LOG.log(Level.INFO, "No ''='' sign in: {0}", infoValue);
                        continue;
                    }
                    out.write((",\n\"" + infoValue.substring(0, eqSign) + "\": \"" + infoValue.substring(eqSign + 1) + "\"").getBytes("UTF-8"));
                }
            }
            out.write(",\n \"statistics\" : {\n".getBytes("UTF-8"));
            boolean wasEntry = false;
            for (Entry<String, Long> e : statistics.entrySet()) {
                if (wasEntry) out.write(", \n".getBytes("UTF-8"));
                out.write(("\"" + e.getKey() + "\" : " + e.getValue()).getBytes("UTF-8"));
                wasEntry = true;
            }
            out.write("\n}\n".getBytes("UTF-8"));
            out.write("\n}\n".getBytes("UTF-8"));

            if (STORE_CLASSPATH) {
                out.putNextEntry(new ZipEntry(categoryId + "/classpath"));

                for (Entry<String, String> e : classpath.entrySet()) {
                    out.write((e.getKey() + "=" + e.getValue() + "\n").getBytes("UTF-8"));
                }

                for (Entry<FileObject, String> ej : extraJars.entrySet()) {
                    out.putNextEntry(new ZipEntry(categoryId + "/" + ej.getValue()));

                    InputStream jarIn = ej.getKey().getInputStream();

                    try {
                        FileUtil.copy(jarIn, out);
                    } finally {
                        jarIn.close();
                    }
                }
            }

            for (FileObject s : cacheFolder.getChildren()) {
                if (!s.isFolder() || !s.getNameExt().startsWith("s") || s.getChildren().length == 0) continue;

                JarOutputStream local = null;
                try {
                    out.putNextEntry(new ZipEntry(categoryId + "/" + s.getNameExt()));

                    local = new JarOutputStream(out);

                    pack(local, s, baseDir.toURI().toString(), "", new StringBuilder(""));
                } finally {
                    if (local != null) {
                        local.finish();
                    }
                }
            }
        } catch (IOException ex) {
            LOG.log(Level.FINE, null, ex);
            throw (CommandException) new CommandException(0).initCause(ex);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    throw (CommandException) new CommandException(0).initCause(ex);
                }
            }

            if (segments != null) {
                try {
                    segments.close();
                } catch (IOException ex) {
                    throw (CommandException) new CommandException(0).initCause(ex);
                }
            }
        }
        
        LifecycleManager.getDefault().exit();
    }

    private Set<FileObject> getRoots(String[] projects, Env env) {
        Set<FileObject> sourceRoots = new HashSet<FileObject>(projects.length * 4 / 3 + 1);

        for (String p : projects) {
            try {
                LOG.log(Level.FINE, "Processing project specified as: {0}", p);
                File f = PropertyUtils.resolveFile(env.getCurrentDirectory(), p);
                File normalized = FileUtil.normalizeFile(f);
                FileObject prjFO = FileUtil.toFileObject(normalized);

                if (prjFO == null) {
                    env.getErrorStream().println("Project location cannot be found: " + p);
                    continue;
                }

                if (!prjFO.isFolder()) {
                    env.getErrorStream().println("Project specified as: " + p + " does not point to a directory (" + FileUtil.getFileDisplayName(prjFO));
                    continue;
                }

                Project prj = ProjectManager.getDefault().findProject(prjFO);

                if (prj == null) {
                    env.getErrorStream().println("Project specified as: " + p + " does not resolve to a project (" + FileUtil.getFileDisplayName(prjFO));
                    continue;
                }

                LOG.log(Level.FINE, "project resolved: {0} ({1})", new Object[] {ProjectUtils.getInformation(prj), prj.getClass()});
                SourceGroup[] javaSG = ProjectUtils.getSources(prj).getSourceGroups("java");

                if (javaSG.length == 0) {
                    env.getErrorStream().println("Project specified as: " + p + " does not define a java source groups (" + FileUtil.getFileDisplayName(prjFO));
                    continue;
                }

                for (SourceGroup sg : javaSG) {
                    LOG.log(Level.FINE, "Found source group: {0}", FileUtil.getFileDisplayName(sg.getRootFolder()));
                    sourceRoots.add(sg.getRootFolder());
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                LOG.log(Level.FINE, null, ex);
                Exceptions.printStackTrace(ex);
                env.getErrorStream().println("Cannot work with project specified as: " + p + " (" + ex.getLocalizedMessage() + ")");
            }
        }

        return sourceRoots;
    }

    private Map<String, String> indexProjects(Set<FileObject> sourceRoots, Map<FileObject, String> extraJars, Env env) throws IOException, InterruptedException {
        if (sourceRoots.isEmpty()) {
            env.getErrorStream().println("Error: There is nothing to index!");
            return Collections.emptyMap();
        } else {
            //XXX: to start up the project systems and RepositoryUpdater:
            ((Runnable) OpenProjects.getDefault().openProjects()).run();
            org.netbeans.api.project.ui.OpenProjects.getDefault().getOpenProjects();
            ClassPath source = ClassPathSupport.createClassPath(sourceRoots.toArray(new FileObject[0]));

            LOG.log(Level.FINE, "Registering as source path: {0}", source.toString());
            GlobalPathRegistry.getDefault().register(ClassPath.SOURCE, new ClassPath[] {source});
            SourceUtils.waitScanFinished();
            Map<String, String> classpath = new HashMap<String, String>();

            if (STORE_CLASSPATH) {
                long extraJarCounter = 0;

                for (FileObject sourceRoot : sourceRoots) {
                    StringBuilder cp = new StringBuilder();
                    ClassPath sourceCP = ClassPath.getClassPath(sourceRoot, ClassPath.SOURCE);

                    if (sourceCP != null) {
                        for (ClassPath.Entry e : sourceCP.entries()) {
                            cp.append(CacheFolder.getDataFolder(e.getURL()).getNameExt());
                            cp.append(":");
                        }
                    }

                    ClassPath compileCP = ClassPath.getClassPath(sourceRoot, ClassPath.COMPILE);

                    if (compileCP != null) {
                        for (ClassPath.Entry e : compileCP.entries()) {
                            Result2 sourceMapping = SourceForBinaryQuery.findSourceRoots2(e.getURL());

                            if (sourceMapping.preferSources() && /*XXX:*/ sourceMapping.getRoots().length > 0) {
                                for (FileObject sr : sourceMapping.getRoots()) {
                                    cp.append(CacheFolder.getDataFolder(sr.toURL()).getNameExt());
                                    cp.append(":");
                                }
                            } else {
                                FileObject root = e.getRoot();
                                FileObject jar = root != null ? FileUtil.getArchiveFile(root) : null;

                                if (jar != null) root = jar;

                                if (root != null && root.isData()) { //XXX: class folders
                                    String rootId = extraJars.get(root);

                                    if (rootId == null) {
                                        extraJars.put(root, rootId = "ej" + extraJarCounter++ + ".jar");
                                    }

                                    cp.append(rootId);
                                    cp.append(":");
                                }
                            }
                        }
                    }

                    if (cp.length() > 0)
                        cp.deleteCharAt(cp.length() - 1);

                    classpath.put(CacheFolder.getDataFolder(sourceRoot.toURL()).getNameExt(), cp.toString());
                }
            }

            GlobalPathRegistry.getDefault().unregister(ClassPath.SOURCE, new ClassPath[] {source});

            return classpath;
        }
    }

    private void pack(JarOutputStream target, FileObject index, String baseURL, String name, StringBuilder relPath) throws IOException {
        int len = relPath.length();
        boolean first = relPath.length() == 0;

        if (!first) relPath.append("/");
        relPath.append(name);

        boolean data = index.isData();

        if (relPath.length() > 0) {
            target.putNextEntry(new ZipEntry(relPath.toString() + (data ? "" : "/")));
        }

        if (data) {
            InputStream in = index.getInputStream();

            try {
                if (baseURL != null && (("java/" + JavaIndex.VERSION + "/checksums.properties").contentEquals(relPath) || ("java/" + JavaIndex.VERSION + "/fqn2files.properties").contentEquals(relPath))) {
                    fixAbsolutePath(in, target, baseURL, "rel:/");
                } else {
                    FileUtil.copy(in, target);
                }
            } finally {
                in.close();
            }
        }

        for (FileObject c : index.getChildren()) {
            if (first && c.getNameExt().equals("segments")) continue;
            pack(target, c, baseURL, c.getNameExt(), relPath);
        }

        relPath.delete(len, relPath.length());
    }

    private void fixAbsolutePath(InputStream original, OutputStream target, String origPrefix, String targetPrefix) throws IOException {
        Properties inProps = new Properties();

        inProps.load(original);

        Properties outProps = new Properties();

        for (String k : (Collection<String>) (Collection) inProps.keySet()) {
            String orig = inProps.getProperty(k);

            //XXX: should only change key or value as appropriate:
            if (k.startsWith(origPrefix)) k = targetPrefix + k.substring(origPrefix.length());
            if (orig.startsWith(origPrefix)) orig = targetPrefix + orig.substring(origPrefix.length());

            outProps.setProperty(k, orig);
        }


        outProps.store(target, "");
    }
}
