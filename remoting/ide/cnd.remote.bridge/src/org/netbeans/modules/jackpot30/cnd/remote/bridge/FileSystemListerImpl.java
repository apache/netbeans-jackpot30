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
package org.netbeans.modules.jackpot30.cnd.remote.bridge;

import java.util.ArrayList;
import java.util.Collection;
import org.netbeans.modules.cnd.api.remote.ServerList;
import org.netbeans.modules.jackpot30.remoting.api.FileSystemLister;
import org.netbeans.modules.nativeexecution.api.ExecutionEnvironment;
import org.netbeans.modules.remote.spi.FileSystemProvider;
import org.openide.filesystems.FileSystem;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=FileSystemLister.class)
public class FileSystemListerImpl implements FileSystemLister {

    @Override public Collection<? extends FileSystem> getKnownFileSystems() {
        Collection<FileSystem> fss = new ArrayList<FileSystem>();

        for (ExecutionEnvironment ee : ServerList.getEnvironments()) {
            if (ee.isLocal()) continue;
            
            FileSystem fs = FileSystemProvider.getFileSystem(ee);

            if (fs != null) {
                fss.add(fs);
            }
        }

        return fss;
    }

}
