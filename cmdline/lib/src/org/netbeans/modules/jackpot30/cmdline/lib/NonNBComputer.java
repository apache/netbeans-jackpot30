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
