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
package org.netbeans.modules.jackpot30.ide.usages;

import com.sun.source.util.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dialog;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import org.codeviation.pojson.Pojson;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.ui.ElementHeaders;
import org.netbeans.api.java.source.ui.ScanDialog;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.jackpot30.common.api.JavaUtils;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.modules.jackpot30.remoting.api.Utilities;
import org.netbeans.modules.jackpot30.remoting.api.Utilities.RemoteSourceDescription;
import org.netbeans.modules.jackpot30.remoting.api.WebUtilities;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.NotifyDescriptor.Message;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.nodes.Node;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

@ActionID(category = "Refactoring",
id = "org.netbeans.modules.jackpot30.ide.usages.RemoteUsages")
@ActionRegistration(displayName = "#CTL_RemoteUsages")
@ActionReferences({
    @ActionReference(path = "Menu/Edit", position = 2250)
})
@Messages("CTL_RemoteUsages=Find Remote Usages...")
public final class RemoteUsages implements ActionListener {

    private final RequestProcessor WORKER = new RequestProcessor(RemoteUsages.class.getName(), 1, false, false);
    
    public void actionPerformed(ActionEvent e) {
        JTextComponent comp = EditorRegistry.lastFocusedComponent(); //XXX

        if (comp == null) return;

        final FileObject file = NbEditorUtilities.getFileObject(comp.getDocument());
        final int pos = comp.getCaretPosition();
        final ElementDescription element = findElement(file, pos);

        if (element == null) {
            Message message = new NotifyDescriptor.Message("Cannot find usages of this element", NotifyDescriptor.Message.ERROR_MESSAGE);
            DialogDisplayer.getDefault().notifyLater(message);
            return ;
        }

        final Set<SearchOptions> options = EnumSet.noneOf(SearchOptions.class);
        final JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        final ElementHandle[] searchFor = new ElementHandle[1];
        JPanel dialogContent = constructDialog(element, options, new SelectionListener() {
            @Override
            public void elementSelected(ElementHandle<?> selected) {
                searchFor[0] = selected;
            }
        }, okButton);

        DialogDescriptor dd = new DialogDescriptor(dialogContent, "Remote Find Usages", true, new Object[] {okButton, cancelButton}, okButton, DialogDescriptor.DEFAULT_ALIGN, null, new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { }
        });
        final Dialog d = DialogDisplayer.getDefault().createDialog(dd);

        final AtomicBoolean cancel = new AtomicBoolean();

        okButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                okButton.setEnabled(false);
                WORKER.post(new FindUsagesWorker(searchFor[0], options, d, cancel));
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                cancel.set(true);
                d.setVisible(false);
            }
        });

        d.setVisible(true);
    }

    private static ElementDescription findElement(final FileObject file, final int pos) {
        final ElementDescription[] handle = new ElementDescription[1];

        if ("text/x-java".equals(FileUtil.getMIMEType(file, "text/x-java"))) {
            final JavaSource js = JavaSource.forFileObject(file);

            ScanDialog.runWhenScanFinished(new Runnable() {
                @Override public void run() {
                    try {
                        js.runUserActionTask(new Task<CompilationController>() {
                            @Override public void run(CompilationController parameter) throws Exception {
                                parameter.toPhase(JavaSource.Phase.RESOLVED);

                                TreePath tp = parameter.getTreeUtilities().pathFor(pos);
                                Element el = parameter.getTrees().getElement(tp);

                                if (el != null && JavaUtils.SUPPORTED_KINDS.contains(el.getKind())) {
                                    handle[0] = new ElementDescription(parameter, el);
                                }
                            }
                        }, true);
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }

            }, "Find Remote Usages");

            return handle[0];
        } else {
            RemoteSourceDescription rsd = org.netbeans.modules.jackpot30.remoting.api.Utilities.remoteSource(file);

            if (rsd != null) {
                try {
                    URI sourceURI = new URI(rsd.idx.remote.toExternalForm() + "/ui/target?path=" + WebUtilities.escapeForQuery(rsd.idx.remoteSegment) + "&relative=" + WebUtilities.escapeForQuery(rsd.relative) + "&position=" + pos);
                    Map<Object, Object> targetData = Pojson.load(HashMap.class, sourceURI.toURL().openStream());

                    String signature = (String) targetData.get("signature");

                    if (signature != null) {
                        List<String> baseMethodsSpec = (List<String>) targetData.get("superMethods");
                        baseMethodsSpec = baseMethodsSpec != null ? baseMethodsSpec : Collections.<String>emptyList();
                        List<ElementHandle<?>> baseMethods = new ArrayList<ElementHandle<?>>(baseMethodsSpec.size());
                        for (String spec : baseMethodsSpec) {
                            baseMethods.add(signature2Handle(spec));
                        }
                        return new ElementDescription(signature2Handle(signature),
                                                      baseMethods);
                    }
                } catch (URISyntaxException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }

            return null;
        }
    }

    private static ElementHandle<?> signature2Handle(String signature) {
        if (signature == null) return null;
        String[] parts = signature.split(":");
        ElementHandle<?> h = Utilities.createElementHandle(ElementKind.valueOf(parts[0]),
                                                           parts[1],
                                                           parts.length > 2 ? parts[2] : null,
                                                           parts.length > 3 ? parts[3] : null);
        return h;
    }

    private JPanel constructDialog(ElementDescription toSearch, Set<SearchOptions> options, SelectionListener sl, JButton ok) {
        JPanel searchKind;

        switch (toSearch.element.getKind()) {
            case METHOD: searchKind = new MethodOptions(toSearch, options, sl); break;
            case CLASS:
            case INTERFACE:
            case ANNOTATION_TYPE: searchKind = new ClassOptions(options); break;
            default:
                options.add(RemoteUsages.SearchOptions.USAGES);
                searchKind = new JPanel();
                break;
        }
        
        final JPanel progress = new JPanel();

        progress.setLayout(new CardLayout());
        progress.add(new JPanel(), "hide");
        progress.add(new JLabel("Querying remote server(s), please wait"), "show");

        ok.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                ((CardLayout) progress.getLayout()).show(progress, "show");
            }
        });

        JPanel result = new JPanel();

        result.setLayout(new BorderLayout());
        result.setBorder(new EmptyBorder(new Insets(12, 12, 12, 12)));

        result.add(new JLabel("Usages of: " + toSearch.displayName), BorderLayout.NORTH);
        result.add(searchKind, BorderLayout.CENTER);
        result.add(progress, BorderLayout.SOUTH);

        sl.elementSelected(toSearch.element);
        
        return result;
    }
    
    public static final class ElementDescription {
        public final ElementHandle<?> element;
        public final String displayName;
        public final List<ElementHandle<?>> superMethods;

        public ElementDescription(CompilationInfo info, Element el) {
            element = ElementHandle.create(el);
            displayName = displayNameForElement(ElementHandle.create(el));

            if (el.getKind() == ElementKind.METHOD) {
                superMethods = superMethods(info, new HashSet<TypeElement>(), (ExecutableElement) el, (TypeElement) el.getEnclosingElement());
            } else {
                superMethods = null;
            }
        }

        private List<ElementHandle<?>> superMethods(CompilationInfo info, Set<TypeElement> seenTypes, ExecutableElement baseMethod, TypeElement currentType) {
            if (!seenTypes.add(currentType))
                return Collections.emptyList();

            List<ElementHandle<?>> result = new ArrayList<ElementHandle<?>>();

            for (TypeElement sup : superTypes(info, currentType)) {
                for (ExecutableElement ee : ElementFilter.methodsIn(sup.getEnclosedElements())) {
                    if (info.getElements().overrides(baseMethod, ee, (TypeElement) baseMethod.getEnclosingElement())) {
                        result.add(ElementHandle.create(ee));
                    }
                }

                result.addAll(superMethods(info, seenTypes, baseMethod, currentType));
            }
            
            return result;
        }

        private List<TypeElement> superTypes(CompilationInfo info, TypeElement type) {
            List<TypeElement> superTypes = new ArrayList<TypeElement>();

            for (TypeMirror sup : info.getTypes().directSupertypes(type.asType())) {
                if (sup.getKind() == TypeKind.DECLARED) {
                    superTypes.add((TypeElement) ((DeclaredType) sup).asElement());
                }
            }

            return superTypes;
        }

        public ElementDescription(ElementHandle<?> element, List<ElementHandle<?>> superMethods) {
            this.element = element;
            displayName = displayNameForElement(element);
            this.superMethods = superMethods;
        }

        private String displayNameForElement(ElementHandle<?> el) throws UnsupportedOperationException {
            String[] signatures = SourceUtils.getJVMSignature(el);
            String classSimpleName = signatures[0];
            int lastDotDollar = Math.max(classSimpleName.lastIndexOf('.'), classSimpleName.lastIndexOf('$'));
            if (lastDotDollar > (-1)) classSimpleName = classSimpleName.substring(lastDotDollar + 1);
            switch (el.getKind()) {
                case METHOD:
                    return signatures[1] + Utilities.decodeMethodParameterTypes(signatures[2]);
                case CONSTRUCTOR:
                    return classSimpleName + Utilities.decodeMethodParameterTypes(signatures[2]);
                case CLASS:
                case INTERFACE:
                case ENUM:
                case ANNOTATION_TYPE:
                    return classSimpleName;
                case FIELD:
                case ENUM_CONSTANT:
                    return signatures[1];
                default:
                    throw new UnsupportedOperationException();
            }
        }

    }

    private static class FindUsagesWorker implements Runnable, Cancellable {
        
        private final ElementHandle<?> toSearch;
        private final Set<SearchOptions> options;
        private final Dialog d;
        private final AtomicBoolean cancel;

        public FindUsagesWorker(ElementHandle<?> toSearch, Set<SearchOptions> options, Dialog d, AtomicBoolean cancel) {
            this.toSearch = toSearch;
            this.options = options;
            this.d = d;
            this.cancel = cancel;
        }

        @Override public void run() {
            try {
                final String serialized = JavaUtils.serialize(toSearch);

                Set<FileObject> resultSet = new HashSet<FileObject>();
                List<FileObject> result = new ArrayList<FileObject>();
                Map<RemoteIndex, List<String>> unmappable = new HashMap<RemoteIndex, List<String>>();

                for (RemoteIndex idx : RemoteIndex.loadIndices()) {
                    URL localFolderURL = idx.getLocalFolder();
                    FileObject localFolder = localFolderURL != null ? URLMapper.findFileObject(localFolderURL) : null;

                    if (options.contains(SearchOptions.USAGES)) {
                        URI resolved = new URI(idx.remote.toExternalForm() + "/usages/search?path=" + WebUtilities.escapeForQuery(idx.remoteSegment) + "&signatures=" + WebUtilities.escapeForQuery(serialized));
                        Collection<? extends String> response = WebUtilities.requestStringArrayResponse(resolved, cancel);

                        if (cancel.get()) return;
                        if (response == null) continue;

                        for (String path : response) {
                            if (path.trim().isEmpty()) continue;
                            FileObject file = localFolder != null ? localFolder.getFileObject(path) : null;

                            if (file != null) {
                                if (resultSet.add(file)) {
                                    result.add(file);
                                }
                            } else {
                                List<String> um = unmappable.get(idx);

                                if (um == null) {
                                    unmappable.put(idx, um = new ArrayList<String>());
                                }

                                um.add(path);
                            }
                        }
                    }

                    if (options.contains(SearchOptions.SUB)) {
                        URI resolved;
                        if (toSearch.getKind() == ElementKind.METHOD) {
                            resolved = new URI(idx.remote.toExternalForm() + "/implements/search?path=" + WebUtilities.escapeForQuery(idx.remoteSegment) + "&method=" + WebUtilities.escapeForQuery(serialized));
                        } else {
                            resolved = new URI(idx.remote.toExternalForm() + "/implements/search?path=" + WebUtilities.escapeForQuery(idx.remoteSegment) + "&type=" + WebUtilities.escapeForQuery(toSearch.getBinaryName()));
                        }

                        String response = WebUtilities.requestStringResponse(resolved, cancel);

                        if (cancel.get()) return;
                        if (response == null) continue;

                        //XXX:
                        Map<String, List<Map<String, String>>> formattedResponse = Pojson.load(LinkedHashMap.class, response);

                        for (Entry<String, List<Map<String, String>>> e : formattedResponse.entrySet()) {
                            for (Map<String, String> p : e.getValue()) {
                                String path = p.get("file");
                                FileObject file = localFolder != null ? localFolder.getFileObject(path) : null;

                                if (file != null) {
                                    if (resultSet.add(file)) {
                                        result.add(file);
                                    }
                                } else {
                                    List<String> um = unmappable.get(idx);

                                    if (um == null) {
                                        unmappable.put(idx, um = new ArrayList<String>());
                                    }

                                    um.add(path);
                                }
                            }
                        }
                    }
                }

                final Node view = Nodes.constructSemiLogicalView(result, unmappable, toSearch, options);

                if (!cancel.get()) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            RemoteUsagesWindowTopComponent.openFor(view);
                        }
                    });
                }
            } catch (URISyntaxException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                cancel.set(true);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override public void run() {
                        d.setVisible(false);
                    }
                });
            }
        }

        @Override public boolean cancel() {
            cancel.set(true);
            return true;
        }
    }

    public enum SearchOptions {
        USAGES,
        SUB;
    }

    public interface SelectionListener {
        public void elementSelected(ElementHandle<?> selected);
    }
}
