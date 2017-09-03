/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009 Sun Microsystems, Inc. All rights reserved.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
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
 * Portions Copyrighted 2009 Sun Microsystems, Inc.
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
