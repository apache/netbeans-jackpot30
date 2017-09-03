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
package org.netbeans.modules.jackpot30.jumpto;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codeviation.pojson.Pojson;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.modules.jackpot30.jumpto.RemoteQuery.SimpleNameable;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.modules.jackpot30.remoting.api.WebUtilities;
import org.netbeans.spi.jumpto.support.NameMatcher;
import org.netbeans.spi.jumpto.support.NameMatcherFactory;
import org.netbeans.spi.jumpto.type.SearchType;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.RequestProcessor.Task;

/**
 *
 * @author lahvac
 */
public abstract class RemoteQuery<R extends SimpleNameable, P> {

    private static final RequestProcessor WORKER = new RequestProcessor(RemoteGoToType.class.getName(), 1, true, false);

    private final boolean synchronous;

    public RemoteQuery() {
        this(false);
    }

    public RemoteQuery(boolean synchronous) {
        this.synchronous = synchronous;
    }

    private String mostGenericQueryText;
    private List<R> results;
    private AtomicBoolean cancel;
    private Task currentWorker;

    protected final void performQuery(final String text, final SearchType searchType, ResultWrapper<R> result) {
        if (!RemoteIndex.loadIndices().iterator().hasNext()) return; //TODO: optimize!

        synchronized (this) {
            if (mostGenericQueryText == null || !text.startsWith(mostGenericQueryText)) {
                if (currentWorker != null) {
                    cancel.set(true);
                    currentWorker.cancel();
                }

                mostGenericQueryText = text;

                currentWorker = WORKER.create(new ComputeResult(text, searchType, cancel = new AtomicBoolean()));

                currentWorker.schedule(0);
                results = new ArrayList<R>();
            }
        }

        try {
            if (synchronous) {
                currentWorker.waitFinished();
            } else {
                currentWorker.waitFinished(100);
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(RemoteGoToType.class.getName()).log(Level.FINE, null, ex);
        }

        boolean finished = currentWorker.isFinished();
        NameMatcher matcher = NameMatcherFactory.createNameMatcher(text, searchType);

        synchronized (this) {
            for (R td : results) {
                if (matcher.accept(td.getSimpleName()))
                    result.addResult(td);
            }
        }

        if (!finished) {
            result.setMessage("Remote query still running, some remote results may be missing");
        }
    }

    protected abstract URI computeURL(RemoteIndex idx, String text, SearchType searchType);
    protected abstract R decode(RemoteIndex idx, String root, P data);

    private void compute(String text, SearchType searchType, AtomicBoolean cancel) {
        Set<FileObject> sources = GlobalPathRegistry.getDefault().getSourceRoots();
        
        for (RemoteIndex ri : RemoteIndex.loadIndices()) {
            URL localFolder = ri.getLocalFolder();
            FileObject originFolder = localFolder != null ? URLMapper.findFileObject(localFolder) : null;
            URI url = computeURL(ri, text, searchType);

            if (url == null) continue;
            
            String response = WebUtilities.requestStringResponse(url, cancel);

            if (cancel.get()) return;
            if (response == null) continue;

            Reader r = new StringReader(response);
            Collection<R> decoded = new ArrayList<R>();

            try {
                @SuppressWarnings("unchecked") //XXX: should not trust something got from the network!
                Map<String, Collection<P>> objectized = Pojson.load(LinkedHashMap.class, r);

                for (Entry<String, Collection<P>> e : objectized.entrySet()) {
                    if (originFolder != null && sources.contains(originFolder.getFileObject(e.getKey()))) continue;

                    for (P data : e.getValue()) {
                        decoded.add(decode(ri, e.getKey(), data));
                    }
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                continue;
            } finally {
                try {
                    r.close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }

            synchronized (this) {
                if (cancel.get()) return;
                results.addAll(decoded);
            }
        }
    }

    public void cancel() {
    }

    public synchronized void cleanup() {
        if (currentWorker != null) {
            cancel.set(true);
            currentWorker.cancel();
        }
        mostGenericQueryText = null;
        results = null;
        cancel = null;
        currentWorker = null;
    }

    protected static interface ResultWrapper<R> {

        public void setMessage(String message);
        public void addResult(R r);

    }

    protected static interface SimpleNameable {
        public String getSimpleName();
        public FileObject getFileObject();
    }

    private class ComputeResult implements Runnable, Cancellable {

        private final String text;
        private final SearchType searchType;
        private final AtomicBoolean cancel;

        public ComputeResult(String text, SearchType searchType, AtomicBoolean cancel) {
            this.text = text;
            this.searchType = searchType;
            this.cancel = cancel;
        }

        @Override public void run() {
            compute(text, searchType == SearchType.EXACT_NAME ? SearchType.PREFIX : searchType, cancel);
        }

        @Override
        public boolean cancel() {
            cancel.set(true);
            return true;
        }
    }

}
