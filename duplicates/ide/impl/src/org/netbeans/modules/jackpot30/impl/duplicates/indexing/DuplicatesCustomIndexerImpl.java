/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009-2010 Sun Microsystems, Inc. All rights reserved.
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
 * Portions Copyrighted 2009-2010 Sun Microsystems, Inc.
 */

package org.netbeans.modules.jackpot30.impl.duplicates.indexing;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.Task;
import org.netbeans.modules.parsing.impl.indexing.IndexableImpl;
import org.netbeans.modules.parsing.impl.indexing.SPIAccessor;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class DuplicatesCustomIndexerImpl extends DeferredCustomIndexer {

    public DuplicatesCustomIndexerImpl(DeferredCustomIndexerFactory factory) {
        super(factory);
    }

    protected void doIndex(final DeferredContext ctx, Collection<? extends FileObject> modifiedAndAdded, Collection<? extends String> removed) throws IOException {
        final DuplicatesIndex[] w = new DuplicatesIndex[1];

        try {
            w[0] = new DuplicatesIndex(ctx.getRoot(), ctx.getCacheRoot());

            for (String r : removed) {
                w[0].remove(r);
                ctx.handledRemovedFile(r);
            }

            final ClasspathInfo cpInfo = ClasspathInfo.create(ctx.getRootFileObject());

            JavaSource.create(cpInfo, modifiedAndAdded).runUserActionTask(new Task<CompilationController>() {
                public void run(final CompilationController cc) throws Exception {
                    if (cc.toPhase(Phase.RESOLVED).compareTo(Phase.RESOLVED) < 0)
                        return ;

                    w[0].record(cc, SPIAccessor.getInstance().create(new IndexableImpl() {
                        @Override public String getRelativePath() {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }
                        @Override public URL getURL() {
                            return cc.getFileObject().toURL();
                        }
                        @Override public String getMimeType() {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }
                        @Override public boolean isTypeOf(String mimeType) {
                            throw new UnsupportedOperationException("Not supported yet.");
                        }
                    }), cc.getCompilationUnit());

                    ctx.handledModifiedFile(cc.getFileObject());
                }
            }, true);
        } finally {
            if (w[0] != null) {
                try {
                    w[0].close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }
    
    public static final class FactoryImpl extends DeferredCustomIndexerFactory {

        @Override
        public DeferredCustomIndexer createIndexer() {
            return new DuplicatesCustomIndexerImpl(this);
        }

        @Override
        public String getIndexerName() {
            return DuplicatesIndex.NAME;
        }

        @Override
        public int getIndexVersion() {
            return DuplicatesIndex.VERSION;
        }

    }

}
