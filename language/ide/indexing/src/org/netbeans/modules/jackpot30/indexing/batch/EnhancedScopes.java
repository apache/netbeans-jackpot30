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

package org.netbeans.modules.jackpot30.indexing.batch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codeviation.pojson.Pojson;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.modules.jackpot30.indexing.index.IndexQuery;
import org.netbeans.modules.jackpot30.indexing.index.Indexer;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.modules.jackpot30.remoting.api.WebUtilities;
import static org.netbeans.modules.jackpot30.remoting.api.WebUtilities.escapeForQuery;
import org.netbeans.modules.java.hints.providers.spi.HintDescription;
import org.netbeans.modules.java.hints.providers.spi.Trigger.PatternDescription;
import org.netbeans.modules.java.hints.spiimpl.MessageImpl;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.Folder;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.IndexEnquirer;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.LocalIndexEnquirer;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.MapIndices;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.Resource;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.Scope;
import org.netbeans.modules.java.hints.spiimpl.batch.BatchSearch.VerifiedSpansCallBack;
import org.netbeans.modules.java.hints.spiimpl.batch.ProgressHandleWrapper;
import org.netbeans.modules.java.hints.spiimpl.batch.Scopes;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings;
import org.netbeans.modules.java.hints.spiimpl.pm.BulkSearch.BulkPattern;
import org.netbeans.modules.java.preprocessorbridge.spi.JavaIndexerPlugin;
import org.netbeans.modules.parsing.impl.indexing.FileObjectIndexable;
import org.netbeans.modules.parsing.impl.indexing.IndexableImpl;
import org.netbeans.modules.parsing.impl.indexing.SPIAccessor;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class EnhancedScopes {

    private static final Logger LOG = Logger.getLogger(EnhancedScopes.class.getName());

    public static Scope allRemote() {
        return new RemoteIndexScope();
    }

    public static final class GivenFolderScope extends Scope {

        public final String folder;
        public final String indexURL;
        public final String subIndex;
        public final boolean update;

        public GivenFolderScope(String folder, String indexURL, String subIndex, boolean update) {
            this.folder = folder;
            this.indexURL = indexURL;
            this.subIndex = subIndex;
            this.update = update;
        }

        @Override
        public String getDisplayName() {
            return folder;
        }

        @Override
        public Collection<? extends Folder> getTodo() {
            return Collections.singletonList(new Folder(FileUtil.toFileObject(new File(folder))));
        }

        @Override
        public MapIndices getIndexMapper(Iterable<? extends HintDescription> patterns) {
            MapIndices mapper;

            if (indexURL != null) {
                if (subIndex == null) {
                    mapper = new MapIndices() {
                        public IndexEnquirer findIndex(FileObject root, ProgressHandleWrapper progress, boolean recursive) {
                            try {
                                return new SimpleIndexIndexEnquirer(root, createOrUpdateIndex(root, new File(indexURL), update, progress, recursive));
                            } catch (IOException ex) {
                                Exceptions.printStackTrace(ex);
                                return null;
                            }
                        }
                    };
                } else {
                    mapper = new MapIndices() {
                        public IndexEnquirer findIndex(FileObject root, ProgressHandleWrapper progress, boolean recursive) {
                            progress.startNextPart(1);
                            try {
                                return new SimpleIndexIndexEnquirer(root, IndexQuery.remote(RemoteIndex.create(root.toURL(), new URL(indexURL), subIndex)));
                            } catch (MalformedURLException ex) {
                                Exceptions.printStackTrace(ex);
                                return null;
                            }
                        }
                    };
                }
            } else {
                mapper = Scopes.getDefaultIndicesMapper();
            }

            return mapper;
        }

    }

    private static final class RemoteIndexScope extends Scope {
        @Override
        public String getDisplayName() {
            return "All Remote Indices";
        }

        @Override
        public Collection<? extends Folder> getTodo() {
            Collection<Folder> todo = new HashSet<Folder>();

            for (RemoteIndex remoteIndex : RemoteIndex.loadIndices()) {
                FileObject localFolder = URLMapper.findFileObject(remoteIndex.getLocalFolder());

                if (localFolder == null) continue;
                todo.add(new Folder(localFolder));
            }

            return todo;
        }

        @Override
        public MapIndices getIndexMapper(final Iterable<? extends HintDescription> patterns) {
            return new MapIndices() {
                public IndexEnquirer findIndex(FileObject root, ProgressHandleWrapper progress, boolean recursive) {
                    for (RemoteIndex remoteIndex : RemoteIndex.loadIndices()) {
                        FileObject localFolder = URLMapper.findFileObject(remoteIndex.getLocalFolder());

                        if (localFolder == null) continue;
                        if (localFolder == root) {
                            return enquirerForRemoteIndex(root, remoteIndex, patterns);
                        }
                    }
                    throw new IllegalStateException();
                }
            };
        }
    }

    private static IndexQuery createOrUpdateIndex(final FileObject src, File indexRoot, boolean update, ProgressHandleWrapper progress, boolean recursive) throws IOException {
        final JavaIndexerPlugin index = new Indexer.FactoryImpl().create(src.toURL(), FileUtil.toFileObject(indexRoot));

        File timeStampsFile = new File(indexRoot, "timestamps.properties");
        Properties timeStamps = new Properties();

        if (timeStampsFile.exists()) {
            if (!update) {
                progress.startNextPart(1);
                return IndexQuery.open(src.toURL());
            }

            InputStream in = null;

            try {
                in = new BufferedInputStream(new FileInputStream(timeStampsFile));
                timeStamps.load(in);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                try {
                    if (in != null)
                        in.close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

        Collection<FileObject> collected = new LinkedList<FileObject>();
        Set<String> removed = new HashSet<String>(timeStamps.stringPropertyNames());

        org.netbeans.modules.java.hints.spiimpl.batch.BatchUtilities.recursive(src, src, collected, progress, 0, timeStamps, removed, recursive);

        for (String r : removed) {
            index.delete(SPIAccessor.getInstance().create(new FakeIndexableImpl(r)));
        }
        
        JavaSource js = JavaSource.create(ClasspathInfo.create(ClassPath.EMPTY, ClassPath.EMPTY, ClassPath.EMPTY), collected);
        
        js.runUserActionTask(new Task<CompilationController>() {
            @Override public void run(CompilationController parameter) throws Exception {
                if (parameter.toPhase(JavaSource.Phase.PARSED).compareTo(JavaSource.Phase.PARSED) < 0) return;
                
                index.process(parameter.getCompilationUnit(), SPIAccessor.getInstance().create(new FileObjectIndexable(src, parameter.getFileObject())), null);
            }
        }, true);
        
        OutputStream out = null;

        try {
            out = new BufferedOutputStream(new FileOutputStream(timeStampsFile));
            timeStamps.store(out, null);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        return IndexQuery.open(src.toURL());
    }
    
    private static final class FakeIndexableImpl implements IndexableImpl {

        private final String path;

        public FakeIndexableImpl(String path) {
            this.path = path;
        }
        
        @Override public String getRelativePath() {
            return path;
        }

        @Override public URL getURL() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override public String getMimeType() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override public boolean isTypeOf(String mimeType) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
    }

    private static boolean isAttributedIndexWithSpans(RemoteIndex remoteIndex) {
        try {
            URI capabilitiesURI = new URI(remoteIndex.remote.toExternalForm() + "/capabilities");
            String capabilitiesString = WebUtilities.requestStringResponse(capabilitiesURI);

            if (capabilitiesURI == null) return false;

            @SuppressWarnings("unchecked")
            Map<String, Object> capabilities = Pojson.load(HashMap.class, capabilitiesString);

            return capabilities.get("attributed") == Boolean.TRUE; //TODO: should also check "methods contains findWithSpans"
        } catch (URISyntaxException ex) {
            LOG.log(Level.FINE, null, ex);
            return false;
        }
    }

    private static IndexEnquirer enquirerForRemoteIndex(FileObject src, RemoteIndex remoteIndex, Iterable<? extends HintDescription> hints) {
        if (hints == null) return null;
        
        boolean fullySupported = isAttributedIndexWithSpans(remoteIndex);
        StringBuilder textualRepresentation = new StringBuilder();

        for (HintDescription hd : hints) {
            if (!(hd.getTrigger() instanceof PatternDescription)) {
                fullySupported = false;
                break;
            }

            if (((PatternDescription) hd.getTrigger()).getImports().iterator().hasNext()) {
                fullySupported = false;
            }

            if (!fullySupported) break;

            String hintText = hd.getHintText();

            if (hintText != null) {
                textualRepresentation.append(hintText);
            } else {
                textualRepresentation.append(defaultHintText(hd));
                fullySupported = false;
            }

            textualRepresentation.append("\n");
        }

        if (fullySupported) {
            return new RemoteFullyAttributedIndexEnquirer(src, remoteIndex, textualRepresentation.toString());
        } else {
            return new SimpleIndexIndexEnquirer(src, IndexQuery.remote(remoteIndex));
        }
    }

    private static final class SimpleIndexIndexEnquirer extends LocalIndexEnquirer {
        private final IndexQuery idx;
        public SimpleIndexIndexEnquirer(FileObject src, IndexQuery idx) {
            super(src);
            this.idx = idx;
        }
        public Collection<? extends Resource> findResources(final Iterable<? extends HintDescription> hints, ProgressHandleWrapper progress, final Callable<BulkPattern> bulkPattern, final Collection<? super MessageImpl> problems, HintsSettings settingsProvider) {
            final Collection<Resource> result = new ArrayList<Resource>();

            progress.startNextPart(1);

            try {
                BulkPattern bp = bulkPattern.call();

                for (String candidate : idx.findCandidates(bp)) {
                    result.add(new Resource(this, candidate, hints, bp, settingsProvider));
                }
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }

            return result;
        }

    }

    private static final class RemoteFullyAttributedIndexEnquirer extends IndexEnquirer {
        private final RemoteIndex remoteIndex;
        private final String textualHintRepresentation;
        public RemoteFullyAttributedIndexEnquirer(FileObject src, RemoteIndex remoteIndex, String textualHintRepresentation) {
            super(src);
            assert isAttributedIndexWithSpans(remoteIndex);
            this.remoteIndex = remoteIndex;
            this.textualHintRepresentation = textualHintRepresentation;
        }
        public Collection<? extends Resource> findResources(final Iterable<? extends HintDescription> hints, ProgressHandleWrapper progress, final Callable<BulkPattern> bulkPattern, final Collection<? super MessageImpl> problems, HintsSettings settingsProvider) {
            final Collection<Resource> result = new ArrayList<Resource>();

            progress.startNextPart(1);

            try {
                URI u = new URI(remoteIndex.remote.toExternalForm() + "/find?path=" + escapeForQuery(remoteIndex.remoteSegment) + "&pattern=" + escapeForQuery(textualHintRepresentation));

                for (String occurrence : new ArrayList<String>(WebUtilities.requestStringArrayResponse(u))) {
                    try {
                        BulkPattern bp = bulkPattern.call();
                        result.add(new Resource(this, occurrence, hints, bp, settingsProvider));
                    } catch (Exception ex) {
                        //from bulkPattern.call()? should not happen.
                        Exceptions.printStackTrace(ex);
                    }
                }

            } catch (URISyntaxException ex) {
                Exceptions.printStackTrace(ex);
            }

            return result;
        }

        @Override
        public void validateResource(Collection<? extends Resource> resources, ProgressHandleWrapper progress, VerifiedSpansCallBack callback, boolean doNotRegisterClassPath, Collection<? super MessageImpl> problems, AtomicBoolean cancel) {
            for (Resource r : resources) {
                try {
                    URI spanURI = new URI(remoteIndex.remote.toExternalForm() + "/findSpans?path=" + escapeForQuery(remoteIndex.remoteSegment) + "&relativePath=" + escapeForQuery(r.getRelativePath()) + "&pattern=" + escapeForQuery(textualHintRepresentation));
                    FileObject fo = r.getResolvedFile();

                    if (fo == null) {
                        callback.cannotVerifySpan(r);
                    } else {
                        List<ErrorDescription> result = new ArrayList<ErrorDescription>();

                        for (int[] span : parseSpans(WebUtilities.requestStringResponse(spanURI))) {
                            result.add(ErrorDescriptionFactory.createErrorDescription(Severity.WARNING, "Occurrence", fo, span[0], span[1]));
                        }

                        callback.spansVerified(null, r, result);
                    }
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

    }

    private static Iterable<int[]> parseSpans(String from) {
        if (from.isEmpty()) {
            return Collections.emptyList();
        }
        String[] split = from.split(":");
        List<int[]> result = new LinkedList<int[]>();

        for (int i = 0; i < split.length; i += 2) {
            result.add(new int[] {
                Integer.parseInt(split[i + 0].trim()),
                Integer.parseInt(split[i + 1].trim())
            });
        }

        return result;
    }

    private static String defaultHintText(HintDescription hd) {
        StringBuilder result = new StringBuilder();

        PatternDescription pd = (PatternDescription) hd.getTrigger();

        if (pd == null) return null;

        if (pd.getImports().iterator().hasNext()) {
            //cannot currently handle patterns with imports:
            return null;
        }

        result.append(pd.getPattern());

        if (!pd.getConstraints().isEmpty()) {
            result.append(" :: ");

            for (Iterator<Entry<String, String>> it = pd.getConstraints().entrySet().iterator(); it.hasNext(); ) {
                Entry<String, String> e = it.next();

                result.append(e.getKey()).append(" instanceof ").append(e.getValue());

                if (it.hasNext()) {
                    result.append(" && ");
                }
            }
        }

        result.append(";;\n");

        return result.toString();
    }
}
