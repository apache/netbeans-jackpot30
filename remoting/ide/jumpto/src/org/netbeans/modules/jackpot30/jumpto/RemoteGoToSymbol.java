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
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.swing.Icon;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.ui.ElementIcons;
import org.netbeans.api.java.source.ui.ElementOpen;
import org.netbeans.modules.jackpot30.jumpto.RemoteGoToSymbol.RemoteSymbolDescriptor;
import org.netbeans.modules.jackpot30.jumpto.RemoteQuery.SimpleNameable;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.modules.jackpot30.remoting.api.Utilities;
import org.netbeans.modules.jackpot30.remoting.api.WebUtilities;
import org.netbeans.spi.jumpto.symbol.SymbolDescriptor;
import org.netbeans.spi.jumpto.symbol.SymbolProvider;
import org.netbeans.spi.jumpto.type.SearchType;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=SymbolProvider.class)
public class RemoteGoToSymbol extends RemoteQuery<RemoteSymbolDescriptor, Map<String, Object>> implements SymbolProvider {

    @Override
    public String name() {
        return "Jackpot 3.0 Remote Index Symbol Provider";
    }

    @Override
    public String getDisplayName() {
        return "Jackpot 3.0 Remote Index Symbol Provider";
    }

    @Override
    public void computeSymbolNames(Context context, final Result result) {
        performQuery(context.getText(), context.getSearchType(), new ResultWrapper<RemoteSymbolDescriptor>() {
            @Override public void setMessage(String message) {
                result.setMessage(message);
            }
            @Override public void addResult(RemoteSymbolDescriptor r) {
                result.addResult(r);
            }
        });
    }

    @Override
    protected URI computeURL(RemoteIndex idx, String text, SearchType searchType) {
        try {
            return new URI(idx.remote.toExternalForm() + "/symbol/search?path=" + WebUtilities.escapeForQuery(idx.remoteSegment) + "&prefix=" + WebUtilities.escapeForQuery(text) + "&querykind=" + WebUtilities.escapeForQuery(searchType.name()));
        } catch (URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    @Override
    protected RemoteSymbolDescriptor decode(RemoteIndex idx, String root, Map<String, Object> properties) {
        return new RemoteSymbolDescriptor(idx, properties);
    }

    static final class RemoteSymbolDescriptor extends SymbolDescriptor implements SimpleNameable {

        private final Map<String, Object> properties;
        private final AbstractDescriptor delegate;

        public RemoteSymbolDescriptor(final RemoteIndex origin, final Map<String, Object> properties) {
            this.properties = properties;
            this.delegate = new AbstractDescriptor() {
                @Override
                protected FileObject resolveFile() {
                    String relativePath = (String) properties.get("file");
                    FileObject originFolder = URLMapper.findFileObject(origin.getLocalFolder());

                    return originFolder != null ? originFolder.getFileObject(relativePath) : null;
                }
            };
        }

        @Override
        public Icon getIcon() {
            ElementKind kind = resolveKind();
            Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
            Set<String> modNames = new HashSet<String>((Collection<String>) properties.get("modifiers"));

            for (Modifier mod : Modifier.values()) {
                if (modNames.contains(mod.name())) {
                    modifiers.add(mod);
                }
            }

            return ElementIcons.getElementIcon(kind, modifiers);
        }

        private ElementKind resolveKind() {
            String kindName = (String) properties.get("kind"); //XXX: cast
            ElementKind kind = ElementKind.OTHER;

            for (ElementKind k : ElementKind.values()) {
                if (k.name().equals(kindName)) {
                    kind = k;
                    break;
                }
            }

            return kind;
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

            ClasspathInfo cpInfo = ClasspathInfo.create(file);
            ElementHandle<?> handle = Utilities.createElementHandle(resolveKind(), (String) properties.get("enclosingFQN"), (String) properties.get("simpleName"), (String) properties.get("vmsignature"));

            ElementOpen.open(cpInfo, handle);
        }

        @Override
        public String getSymbolName() {
            StringBuilder name = new StringBuilder();

            name.append(properties.get("simpleName"));

            if (properties.containsKey("signature") && (resolveKind() == ElementKind.METHOD || resolveKind() == ElementKind.CONSTRUCTOR)) {
                name.append(Utilities.decodeMethodParameterTypes((String) properties.get("signature")));
            }

            return name.toString();
        }

        @Override
        public String getOwnerName() {
            return (String) properties.get("enclosingFQN");
        }

        @Override
        public String getSimpleName() {
            return (String) properties.get("simpleName");
        }

    }
    
}
