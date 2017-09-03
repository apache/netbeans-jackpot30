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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.JavaSource.Priority;
import org.netbeans.api.java.source.JavaSourceTaskFactory;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.netbeans.modules.parsing.impl.indexing.SPIAccessor;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexer;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexerFactory;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.filesystems.FileAlreadyLockedException;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public abstract class DeferredCustomIndexer extends CustomIndexer {

    private static final Logger LOG = Logger.getLogger(DeferredCustomIndexer.class.getName());
    
    private final DeferredCustomIndexerFactory factory;

    protected DeferredCustomIndexer(DeferredCustomIndexerFactory factory) {
        this.factory = factory;
    }

    protected abstract void doIndex(DeferredContext ctx, Collection<? extends FileObject> modifiedAndAdded, Collection<? extends String> removed) throws IOException;

    @Override
    protected final void index(Iterable<? extends Indexable> files, Context context) {
        update(factory, context.getRootURI(), files, Collections.<Indexable>emptyList());
    }

    private static void dump(File where, Iterable<? extends String> lines) {
        Writer out = null;

        try {
            out = new BufferedWriter(new OutputStreamWriter(FileUtil.createData(where).getOutputStream(), "UTF-8"));
            
            for (String line : lines) {
                out.write(line);
                out.write("\n");
            }
        } catch (FileAlreadyLockedException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    private static Set<String> gatherRelativePaths(Iterable<? extends Indexable> it) {
        Set<String> result = new HashSet<String>();

        for (Indexable i : it) {
            result.add(i.getRelativePath());
        }

        return result;
    }

    private static void update(DeferredCustomIndexerFactory factory, URL root, Iterable<? extends Indexable> modified, Iterable<? extends Indexable> deleted) {
        try {
            Set<String> mod = gatherRelativePaths(modified);
            Set<String> del = gatherRelativePaths(deleted);

            File cacheRoot = cacheRoot(root, factory);
            
            File modifiedFile = new File(cacheRoot, "modified");
            FileObject modifiedFileFO = FileUtil.toFileObject(modifiedFile);
            Set<String> modifiedFiles = modifiedFileFO != null ? new HashSet<String>(modifiedFileFO.asLines("UTF-8")) : new HashSet<String>();
            boolean modifiedFilesChanged = modifiedFiles.removeAll(del);

            modifiedFilesChanged |= modifiedFiles.addAll(mod);

            if (modifiedFilesChanged) {
                dump(modifiedFile, modifiedFiles);
            }

            File deletedFile = new File(cacheRoot, "deleted");
            FileObject deletedFileFO = FileUtil.toFileObject(deletedFile);
            Set<String> deletedFiles = deletedFileFO != null ? new HashSet<String>(deletedFileFO.asLines("UTF-8")) : new HashSet<String>();

            boolean deletedFilesChanged = deletedFiles.removeAll(mod);

            deletedFilesChanged |= deletedFiles.addAll(del);

            if (deletedFilesChanged) {
                dump(deletedFile, deletedFiles);
            }

            if (!modifiedFiles.isEmpty() || !deletedFiles.isEmpty()) {
                add2TODO(root, factory);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
    
    public static abstract class DeferredCustomIndexerFactory extends CustomIndexerFactory {

        public abstract DeferredCustomIndexer createIndexer();

        @Override
        public final void filesDeleted(Iterable<? extends Indexable> deleted, Context context) {
            update(this, context.getRootURI(), Collections.<Indexable>emptyList(), deleted);
        }

        @Override
        public final void filesDirty(Iterable<? extends Indexable> dirty, Context context) {}

        @Override
        public final boolean supportsEmbeddedIndexers() {
            return false;
        }

        @Override
        public final void rootsRemoved(Iterable<? extends URL> removedRoots) {
            super.rootsRemoved(removedRoots);
        }

        @Override
        public final void scanFinished(Context context) {
            super.scanFinished(context);
        }

        @Override
        public final boolean scanStarted(Context context) {
            return super.scanStarted(context);
        }

        public void updateIndex(final URL root, final AtomicBoolean cancel) throws IOException {
            final FileObject rootFO = URLMapper.findFileObject(root);
            final ClasspathInfo cpInfo = ClasspathInfo.create(ClassPath.EMPTY, ClassPath.EMPTY, ClassPath.EMPTY);

            JavaSource.create(cpInfo).runUserActionTask(new Task<CompilationController>() {
                public void run(CompilationController parameter) throws Exception {
                    if (cancel.get()) return ;
                    updateRoot(DeferredCustomIndexerFactory.this, root, rootFO, cancel);
                }
            }, true);
        }

    }

    public static final class DeferredContext {
        private final @NonNull URL root;
        private final @NonNull FileObject rootFileObject;
        private final @NonNull FileObject cacheRoot;
        private final @NonNull Set<? extends FileObject> modifiedAndAdded;
        private final @NonNull Set<? extends String> removed;
        private final @NonNull AtomicBoolean cancel;

        public DeferredContext(URL root, FileObject rootFileObject, FileObject cacheRoot, Set<? extends FileObject> modifiedAndAdded, Set<? extends String> removed, AtomicBoolean cancel) {
            this.root = root;
            this.rootFileObject = rootFileObject;
            this.cacheRoot = cacheRoot;
            this.modifiedAndAdded = modifiedAndAdded;
            this.removed = removed;
            this.cancel = cancel;
        }

        public @NonNull URL getRoot() {
            return root;
        }

        public @NonNull FileObject getRootFileObject() {
            return rootFileObject;
        }

        public FileObject getCacheRoot() {
            return cacheRoot;
        }

        public boolean isCancelled() {
            return cancel.get();
        }

        public void handledModifiedFile(FileObject file) {
            modifiedAndAdded.remove(file);
        }

        public void handledRemovedFile(String relative) {
            removed.remove(relative);
        }
    }

    /*return: true == done*/
    private static boolean updateRoot(DeferredCustomIndexerFactory factory, URL root, FileObject rootFO, AtomicBoolean cancel) throws IOException {
        LOG.log(Level.FINE, "updating: {0}, for indexer: {1}", new Object[] {root.toExternalForm(), factory.getIndexerName()});
         File cacheRoot = cacheRoot(root, factory);
         FileObject deletedFile = FileUtil.toFileObject(new File(cacheRoot, "deleted"));
         Set<String> deletedFiles = deletedFile != null ? new HashSet<String>(deletedFile.asLines("UTF-8")) : Collections.<String>emptySet();

         FileObject modifiedFile = FileUtil.toFileObject(new File(cacheRoot, "modified"));
         Set<String> modifiedFiles = modifiedFile != null ? new HashSet<String>(modifiedFile.asLines("UTF-8")) : Collections.<String>emptySet();

         Set<FileObject> toIndex = new HashSet<FileObject>();

         for (String r : modifiedFiles) {
             FileObject f = rootFO.getFileObject(r);

             if (f != null) {
                 toIndex.add(f);
             }
         }

         if (!toIndex.isEmpty() || !modifiedFiles.isEmpty()) {
             factory.createIndexer().doIndex(new DeferredContext(root, rootFO, FileUtil.toFileObject(cacheRoot), toIndex, deletedFiles, cancel), new HashSet<FileObject>(toIndex), new HashSet<String>(deletedFiles));
         }

         boolean done = true;

         if (deletedFile != null) {
             if (deletedFiles.isEmpty()) {
                 deletedFile.delete();
             } else {
                 dump(new File(cacheRoot, "deleted"), deletedFiles);
                 done = false;
             }
         }
         if (modifiedFile != null) {
             if (toIndex.isEmpty()) {
                 modifiedFile.delete();
             }  else {
                 modifiedFiles.clear();

                 for (FileObject f : toIndex) {
                     modifiedFiles.add(FileUtil.getRelativePath(rootFO, f));
                 }

                 dump(new File(cacheRoot, "modified"), modifiedFiles);
                 done = false;
             }
         }

         return done;
    }

    private static final Map<String, TODO> todo = new HashMap<String, TODO>(); //XXX: synchronization!!!

    private static void add2TODO(URL root, DeferredCustomIndexerFactory factory) {
        if (DISABLED_INDEXERS.contains(factory.getIndexerName())) return;
        
        boolean wasEmpty = todo.isEmpty();
        TODO roots = todo.get(factory.getIndexerName());

        if (roots == null) {
            todo.put(factory.getIndexerName(), roots = new TODO(factory));
        }

        roots.roots.add(root);

        LOG.log(Level.FINE, "add2TODO, root: {0}, for factory: {1}, wasEmpty: {2}, todo: {3}", new Object[] {root.toExternalForm(), factory.getIndexerName(), wasEmpty, todo.toString()});
        
        if (wasEmpty) RunAsNeededFactory.fileChanged();
        else RunAsNeededFactory.refresh();
    }
    
    private static File cacheRoot(URL root, CustomIndexerFactory factory) throws IOException {
        FileObject indexBaseFolder = CacheFolder.getDataFolder(root);
        String path = SPIAccessor.getInstance().getIndexerPath(factory.getIndexerName(), factory.getIndexVersion());
        FileObject indexFolder = FileUtil.createFolder(indexBaseFolder, path);
        return FileUtil.toFile(indexFolder);
    }

    private static final Set<String> DISABLED_INDEXERS = Collections.synchronizedSet(new HashSet<String>());

    private static class UpdateWorker implements CancellableTask<CompilationInfo> {

        private static ProgressHandle progressForCurrentFactory;
        private static DeferredCustomIndexerFactory currentFactory;
        
        private final AtomicBoolean cancel = new AtomicBoolean();

        public void run(CompilationInfo parameter) throws Exception {
            cancel.set(false);

            for (Iterator<Entry<String, TODO>> it = todo.entrySet().iterator(); it.hasNext();) {
                if (cancel.get()) return;

                final Entry<String, TODO> e = it.next();

                if (DISABLED_INDEXERS.contains(e.getKey())) {
                    it.remove();
                    continue;
                }
                
                if (currentFactory != e.getValue().factory) {
                    if (progressForCurrentFactory != null) {
                        progressForCurrentFactory.finish();
                    }

                    currentFactory = e.getValue().factory;
                    progressForCurrentFactory = ProgressHandleFactory.createSystemHandle("Background indexing for: " + currentFactory.getIndexerName(), new Cancellable() {
                        public boolean cancel() {
                            assert SwingUtilities.isEventDispatchThread();

                            JButton disableInThisSession = new JButton("Disable in This Session");
                            JButton disablePermanently = new JButton("Disable Permanently");

                            disablePermanently.setEnabled(false);

                            Object[] buttons = new Object[]{disableInThisSession, disablePermanently, DialogDescriptor.CANCEL_OPTION};
                            DialogDescriptor dd = new DialogDescriptor("Disable background indexing for: " + e.getValue().factory.getIndexerName(), "Disable Background Indexing", true, buttons, disableInThisSession, DialogDescriptor.DEFAULT_ALIGN, null, null);

                            dd.setClosingOptions(buttons);

                            Object result = DialogDisplayer.getDefault().notify(dd);

                            if (result == disableInThisSession) {
                                DISABLED_INDEXERS.add(e.getKey());
                                return true;
                            } else if (result == disablePermanently) {
                                throw new UnsupportedOperationException();
                            } else {
                                return false;
                            }
                        }
                    });

                    progressForCurrentFactory.start();
                }

                for (Iterator<URL> factIt = e.getValue().roots.iterator(); factIt.hasNext();) {
                    if (cancel.get()) return;

                    URL root = factIt.next();
                    FileObject rootFO = URLMapper.findFileObject(root);

                    if (rootFO == null) {
                        //already deleted
                        it.remove();
                        continue;
                    }

                    if (updateRoot(e.getValue().factory, root, rootFO, cancel)) {
                        factIt.remove();
                    } else {
                        if (!cancel.get()) {
                            LOG.log(Level.WARNING, "indexer: {0} did not update all files even if the process was not cancelled", currentFactory.getIndexerName());
                        }
                    }
                }

                if (e.getValue().roots.isEmpty())
                    it.remove();

                progressForCurrentFactory.finish();
                progressForCurrentFactory = null;
                currentFactory = null;
            }

            if (todo.isEmpty()) RunAsNeededFactory.fileChanged();
        }

        public void cancel() {
            cancel.set(true);
        }
    }

    private static final class TODO {
        final DeferredCustomIndexerFactory factory;
        final Collection<URL> roots = new HashSet<URL>();
        TODO(DeferredCustomIndexerFactory factory) {
            this.factory = factory;
        }
    }

    private static final boolean DEFERRED_INDEXER_ENABLED = Boolean.getBoolean(DeferredCustomIndexerFactory.class.getName() + ".enable");
    
    private static final FileObject EMPTY_FILE;

    static {
        try {
            EMPTY_FILE = FileUtil.createMemoryFileSystem().getRoot().createData("empty.java");
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @ServiceProvider(service=JavaSourceTaskFactory.class)
    public static final class RunAsNeededFactory extends JavaSourceTaskFactory {

        public RunAsNeededFactory() {
            super(Phase.PARSED, Priority.MIN);
        }

        @Override
        protected CancellableTask<CompilationInfo> createTask(FileObject file) {
            return new UpdateWorker();
        }

        @Override
        protected Collection<FileObject> getFileObjects() {
            return DEFERRED_INDEXER_ENABLED && !todo.isEmpty() ? Collections.singletonList(EMPTY_FILE) : Collections.<FileObject>emptyList();
        }

        public static void fileChanged() {
            for (JavaSourceTaskFactory f : Lookup.getDefault().lookupAll(JavaSourceTaskFactory.class)) {
                if (f instanceof RunAsNeededFactory) {
                    ((RunAsNeededFactory) f).fileObjectsChanged();
                }
            }
        }

        public static void refresh() {
            for (JavaSourceTaskFactory f : Lookup.getDefault().lookupAll(JavaSourceTaskFactory.class)) {
                if (f instanceof RunAsNeededFactory) {
                    ((RunAsNeededFactory) f).reschedule(EMPTY_FILE);
                }
            }
        }
    }
}
