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

package org.netbeans.modules.jackpot30.impl.duplicates.hints;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.JavaSource.Priority;
import org.netbeans.api.java.source.JavaSourceTaskFactory;
import org.netbeans.api.java.source.support.EditorAwareJavaSourceTaskFactory;
import org.netbeans.modules.jackpot30.impl.duplicates.ComputeDuplicates;
import org.netbeans.modules.jackpot30.impl.duplicates.ComputeDuplicates.DuplicateDescription;
import org.netbeans.modules.jackpot30.impl.duplicates.indexing.RemoteDuplicatesIndex;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.filesystems.FileObject;
import org.openide.util.NbCollections;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class FindDuplicates implements CancellableTask<CompilationInfo> {

    private final AtomicBoolean cancel = new AtomicBoolean();
    
    public void run(CompilationInfo info) throws Exception {
        cancel.set(false);

        long start = System.currentTimeMillis();
        try {
            Collection<? extends ErrorDescription> eds = computeErrorDescription(info);

            if (cancel.get()) return;

            if (eds == null) {
                eds = Collections.emptyList();
            }

            HintsController.setErrors(info.getFileObject(), FindDuplicates.class.getName(), eds);
        } finally {
            long end = System.currentTimeMillis();

            Logger.getLogger("TIMER").log(Level.FINE, "Duplicates in editor", new Object[] {info.getFileObject(), end - start});
        }
    }

    private Collection<? extends ErrorDescription> computeErrorDescription(CompilationInfo info) throws Exception {
        List<ErrorDescription> result = new LinkedList<ErrorDescription>();

        Map<String, long[]> encoded = ComputeDuplicates.encodeGeneralized(info);
        Iterator<? extends DuplicateDescription> duplicates = RemoteDuplicatesIndex.findDuplicates(encoded, info.getFileObject(), cancel).iterator();

        for (DuplicateDescription dd : NbCollections.iterable(duplicates)) {
            long[] spans = encoded.get(dd.hash);

            for (int c = 0; c < spans.length; c += 2) {
                if (cancel.get()) return null;
                result.add(ErrorDescriptionFactory.createErrorDescription(Severity.WARNING, "Duplicate of code from " + dd.dupes.get(0).file, info.getFileObject(), (int) spans[c], (int) spans[c + 1]));
            }
        }

        return result;
    }
    
    public void cancel() {
        cancel.set(true);
    }

    @ServiceProvider(service=JavaSourceTaskFactory.class)
    public static final class FactoryImpl extends EditorAwareJavaSourceTaskFactory {

        public FactoryImpl() {
            super(Phase.RESOLVED, Priority.LOW);
        }

        @Override
        protected CancellableTask<CompilationInfo> createTask(FileObject file) {
            return new FindDuplicates();
        }
        
    }

}
