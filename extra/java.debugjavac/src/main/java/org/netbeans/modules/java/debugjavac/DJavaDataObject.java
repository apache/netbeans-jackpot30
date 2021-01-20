/*
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
package org.netbeans.modules.java.debugjavac;

import java.io.IOException;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.NbBundle.Messages;

@Messages({
    "LBL_DJava_LOADER=Files of DJava"
})
@MIMEResolver.ExtensionRegistration(
        displayName = "#LBL_DJava_LOADER",
        mimeType = "text/x-java-decompiled",
        extension = {"djava"},
        position = 999207
        )
@DataObject.Registration(
        mimeType = "text/x-java-decompiled",
//        iconBase = "SET/PATH/TO/ICON/HERE",
        displayName = "#LBL_DJava_LOADER",
        position = 300
        )
public class DJavaDataObject extends MultiDataObject {

    public DJavaDataObject(FileObject pf, MultiFileLoader loader) throws DataObjectExistsException, IOException {
        super(pf, loader);
        registerEditor("text/x-java-decompiled", false);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }

}
