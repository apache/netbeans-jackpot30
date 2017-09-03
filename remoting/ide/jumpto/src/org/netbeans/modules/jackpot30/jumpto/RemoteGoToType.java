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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.swing.Icon;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.UiUtils;
import org.netbeans.api.java.source.ui.ElementIcons;
import org.netbeans.api.java.source.ui.ElementOpen;
import org.netbeans.modules.jackpot30.jumpto.RemoteGoToType.RemoteTypeDescriptor;
import org.netbeans.modules.jackpot30.jumpto.RemoteQuery.SimpleNameable;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.modules.jackpot30.remoting.api.WebUtilities;
import org.netbeans.spi.jumpto.type.SearchType;
import org.netbeans.spi.jumpto.type.TypeDescriptor;
import org.netbeans.spi.jumpto.type.TypeProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=TypeProvider.class)
public class RemoteGoToType extends RemoteQuery<RemoteTypeDescriptor, String> implements TypeProvider {

    public RemoteGoToType() {}

    public RemoteGoToType(boolean synchronous) {
        super(synchronous);
    }

    @Override
    public String name() {
        return "Jackpot 3.0 Remote Index Type Provider";
    }

    @Override
    public String getDisplayName() {
        return "Jackpot 3.0 Remote Index Type Provider";
    }

    @Override
    public void computeTypeNames(Context context, final Result result) {
        performQuery(context.getText(), context.getSearchType(), new ResultWrapper<RemoteTypeDescriptor>() {
            @Override public void setMessage(String message) {
                result.setMessage(message);
            }
            @Override public void addResult(RemoteTypeDescriptor r) {
                result.addResult(r);
            }
        });
    }

    @Override
    protected URI computeURL(RemoteIndex idx, String text, SearchType searchType) {
        try {
            return new URI(idx.remote.toExternalForm() + "/type/search?path=" + WebUtilities.escapeForQuery(idx.remoteSegment) + "&prefix=" + WebUtilities.escapeForQuery(text));
        } catch (URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    @Override
    protected RemoteTypeDescriptor decode(RemoteIndex idx, String root, String binaryName) {
        return new RemoteTypeDescriptor(idx, root, binaryName);
    }

    static final class RemoteTypeDescriptor extends TypeDescriptor implements SimpleNameable {

        private final String binaryName;
        private final AbstractDescriptor delegate;

        public RemoteTypeDescriptor(final RemoteIndex origin, final String relativePath, final String binaryName) {
            this.binaryName = binaryName;

            delegate = new AbstractDescriptor() {
                @Override
                protected FileObject resolveFile() {
                    String fqn = binaryName;

                    if (fqn.contains("$")) {
                        fqn = fqn.substring(0, fqn.indexOf("$"));
                    }

                    return origin.getFile(relativePath + fqn.replace('.', '/') + ".java");
                }
            };
        }

        @Override
        public String getSimpleName() {
            int dollar = binaryName.lastIndexOf("$");
            
            if (dollar >= 0) return binaryName.substring(dollar + 1);
            else {
                int dot = binaryName.lastIndexOf(".");
                
                if (dot >= 0) return binaryName.substring(dot + 1);
                else return binaryName;
            }
        }

        @Override
        public String getOuterName() {
            int dollar = binaryName.lastIndexOf("$");
            int dot = binaryName.lastIndexOf(".");

            if (dollar >= 0 && dot >= 0) return binaryName.substring(dot + 1, dollar).replace("$", ".");
            else return null;
        }

        @Override
        public String getTypeName() {
            if (getOuterName() != null)
                return getSimpleName() + " in " + getOuterName();
            else
                return getSimpleName();
        }

        @Override
        public String getContextName() {
            int dot = binaryName.lastIndexOf(".");

            if (dot >= 0) return " (" + binaryName.substring(0, dot) + ")";
            else return "";
        }

        @Override
        public Icon getIcon() {
            return ElementIcons.getElementIcon(ElementKind.CLASS, EnumSet.noneOf(Modifier.class));
        }

        @Override
        public String getProjectName() {
            return delegate.getProjectName();
        }

        @Override
        public Icon getProjectIcon() {
            return delegate.getProjectIcon();
        }

        @Override
        public FileObject getFileObject() {
            return delegate.getFileObject();
        }

        @Override
        public int getOffset() {
            return 0;
        }

        @Override
        public void open() {
            FileObject file = getFileObject();

            if (file == null) return ; //XXX tell to the user

            if ("text/x-java".equals(FileUtil.getMIMEType(file, "text/x-java"))) {
                ClasspathInfo cpInfo = ClasspathInfo.create(file);
                ElementHandle<?> handle = ElementHandle.createTypeElementHandle(ElementKind.CLASS, binaryName);

                ElementOpen.open(cpInfo, handle);
            } else {
                //TODO: should jump to the correct place in the file
                UiUtils.open(file, 0);
            }
        }

    }
}
