/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2010 Sun Microsystems, Inc.
 */

package org.netbeans.modules.jackpot30.cmdline.lib;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.modules.java.hints.spiimpl.hints.HintsInvoker;
import org.netbeans.modules.java.hints.spiimpl.options.HintsSettings;
import org.netbeans.modules.parsing.impl.indexing.CacheFolder;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;

/**
 *
 * @author lahvac
 */
public class NonNBComputer {

    public static Map<URL, List<List<Object>>> compute(List<URL> bootCPList, List<URL> compileCPList, List<URL> sourceCPList, List<URL> sourceFilesList, final AtomicBoolean cancel) throws IOException {
        ClassPath bootCP = ClassPathSupport.createClassPath(bootCPList.toArray(new URL[0]));
        ClassPath compileCP = ClassPathSupport.createClassPath(compileCPList.toArray(new URL[0]));
        ClassPath sourceCP = ClassPathSupport.createClassPath(sourceCPList.toArray(new URL[0]));

        List<FileObject> sourceFiles = new LinkedList<FileObject>();

        for (URL sf : sourceFilesList) {
            sourceFiles.add(URLMapper.findFileObject(sf));
        }

        ClasspathInfo cpInfo = ClasspathInfo.create(bootCP, compileCP, sourceCP);
        final Map<URL, List<List<Object>>> result = new HashMap<URL, List<List<Object>>>();

        JavaSource.create(cpInfo, sourceFiles).runUserActionTask(new Task<CompilationController>() {

            @Override
            public void run(CompilationController parameter) throws Exception {
                if (parameter.toPhase(JavaSource.Phase.RESOLVED).compareTo(JavaSource.Phase.RESOLVED) < 0) {
                    return;
                }

                List<ErrorDescription> eds = new HintsInvoker(HintsSettings.getSettingsFor(parameter.getFileObject()), cancel).computeHints(parameter);
                List<List<Object>> currentResult = new LinkedList<List<Object>>();

                if (eds != null) {
                    for (ErrorDescription ed : eds) {
                        currentResult.add(Arrays.<Object>asList(ed.getDescription(), ed.getRange().getBegin().getOffset(), ed.getRange().getEnd().getOffset()));
                    }
                }

                result.put(parameter.getFileObject().getURL(), currentResult);
            }
        }, true);

        return result;
    }

    static {
        try {
            File tmp = File.createTempFile("jackpot30", null);

            tmp.delete();
            tmp.mkdirs();
            tmp.deleteOnExit();

            tmp = FileUtil.normalizeFile(tmp);
            FileUtil.refreshFor(tmp.getParentFile());

            org.openide.filesystems.FileObject tmpFO = FileUtil.toFileObject(tmp);

            if (tmpFO != null) {
                CacheFolder.setCacheFolder(tmpFO);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
