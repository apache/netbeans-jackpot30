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

import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.openide.filesystems.FileObject;

/**
 *
 * @author lahvac
 */
public abstract class AbstractDescriptor {

    private final AtomicReference<String> displayName = new AtomicReference<String>();
    private final AtomicReference<FileObject> file = new AtomicReference<FileObject>();

    protected AbstractDescriptor() {
    }

    public String getProjectName() {
        String displayName = this.displayName.get();

        if (displayName != null) {
            return displayName;
        }

        FileObject file = getFileObject();

        if (file == null) {
            return null;
        }

        Project prj = FileOwnerQuery.getOwner(file);

        if (prj == null) {
            return null;
        }

        this.displayName.set(displayName = ProjectUtils.getInformation(prj).getDisplayName());

        return displayName;
    }

    public Icon getProjectIcon() {
        FileObject file = getFileObject();

        if (file == null) {
            return null;
        }

        Project prj = FileOwnerQuery.getOwner(file);

        if (prj == null) {
            return null;
        }

        return ProjectUtils.getInformation(prj).getIcon();
    }

    public FileObject getFileObject() {
        FileObject f = this.file.get();

        if (f == null) {
            f = resolveFile();
            
            if (f != null) {
                this.file.set(f);
            }
        }

        return f;
    }

    protected abstract FileObject resolveFile();
}
