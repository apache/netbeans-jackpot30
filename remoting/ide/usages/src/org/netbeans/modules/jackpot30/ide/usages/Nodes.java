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

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import java.awt.Image;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.swing.Action;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.UiUtils;
import org.netbeans.api.java.source.support.CancellableTreePathScanner;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.jackpot30.remoting.api.RemoteIndex;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.openide.actions.OpenAction;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.LineCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.nodes.NodeOp;
import org.openide.text.Line;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author lahvac
 */
public class Nodes {

    public static Node constructSemiLogicalView(Iterable<? extends FileObject> filesWithOccurrences, Map<RemoteIndex, List<String>> unmappable, ElementHandle<?> eh, Set<RemoteUsages.SearchOptions> options) {
        Map<Project, Collection<FileObject>> projects = new HashMap<Project, Collection<FileObject>>();

        for (FileObject file : filesWithOccurrences) {
            Project project = FileOwnerQuery.getOwner(file);

            if (project == null) {
                Logger.getLogger(Nodes.class.getName()).log(Level.WARNING, "Cannot find project for: {0}", FileUtil.getFileDisplayName(file));
            }

            Collection<FileObject> projectFiles = projects.get(project);

            if (projectFiles == null) {
                projects.put(project, projectFiles = new ArrayList<FileObject>());
            }

            projectFiles.add(file);

            //XXX: workarounding NbProject's Evaluator, which is too stupid to fire meaningfull property events, which leads to PackageView rebuilding inadvertedly itself due to virtual CONTAINERSHIP change:
            ClassPath.getClassPath(file, ClassPath.COMPILE).getRoots();
        }

        final Collection<FileObject> outsideProjects = projects.remove(null);

        List<Node> nodes = new ArrayList<Node>(projects.size());

        for (Project p : projects.keySet()) {
            nodes.add(constructSemiLogicalView(p, projects.get(p), eh, options));
        }

        Collections.sort(nodes, new Comparator<Node>() {
            @Override public int compare(Node o1, Node o2) {
                return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
            }
        });

        if (outsideProjects != null) {
            AbstractNode outsideProjectsNode = new AbstractNode(Children.create(new ChildFactory<FileObject>() {
                @Override protected boolean createKeys(List<FileObject> toPopulate) {
                    toPopulate.addAll(outsideProjects);
                    return true;
                }
                @Override protected Node createNodeForKey(FileObject file) {
                    try {
                        DataObject od = DataObject.find(file);
                        return od.getNodeDelegate();
                    } catch (DataObjectNotFoundException ex) {
                        Exceptions.printStackTrace(ex);
                        return null;
                    }
                }
            }, true));

            outsideProjectsNode.setDisplayName("Occurrences outside locally recognized projects");
            nodes.add(outsideProjectsNode);
        }
        
        if (!unmappable.isEmpty()) {
            List<Node> localNodes = new ArrayList<Node>(unmappable.size());

            for (final Entry<RemoteIndex, List<String>> e : unmappable.entrySet()) {
                AbstractNode localNode = new AbstractNode(Children.create(new ChildFactory<String>() {
                    @Override protected boolean createKeys(List<String> toPopulate) {
                        Collections.sort(e.getValue());
                        toPopulate.addAll(e.getValue());
                        return true;
                    }
                    @Override protected Node createNodeForKey(final String rel) {
                        OpenCookie open = new OpenCookie() {
                            @Override public void open() {
                                UiUtils.open(e.getKey().getFile(rel), 0);
                            }
                        };
                        AbstractNode fileNode = new AbstractNode(Children.LEAF, Lookups.singleton(open)) {
                            @Override public Action[] getActions(boolean context) {
                                return new Action[] {
                                    OpenAction.get(OpenAction.class)
                                };
                            }
                            @Override public Action getPreferredAction() {
                                return OpenAction.get(OpenAction.class);
                            }
                        };

                        fileNode.setDisplayName(rel);
                        return fileNode;
                    }
                }, true));

                localNode.setDisplayName("Index: " + e.getKey().remote.toExternalForm() + ", segment: " + e.getKey().remoteSegment);
                localNodes.add(localNode);
            }

            AbstractNode notExisting = new AbstractNode(new DirectChildren(localNodes));

            notExisting.setDisplayName("Occurrences in files that are not locally available");
            nodes.add(notExisting);
        }

        return new AbstractNode(new DirectChildren(nodes));
    }

    private static Node constructSemiLogicalView(final Project p, final Iterable<? extends FileObject> files, ElementHandle<?> eh, Set<RemoteUsages.SearchOptions> options) {
        final LogicalViewProvider lvp = p.getLookup().lookup(LogicalViewProvider.class);
        final Node view;

        if (lvp != null) {
            view = lvp.createLogicalView();
        } else {
            try {
                view = DataObject.find(p.getProjectDirectory()).getNodeDelegate();
            } catch (DataObjectNotFoundException ex) {
                Exceptions.printStackTrace(ex);
                return new AbstractNode(Children.LEAF);
            }
        }

        return new Wrapper(view, new ComputeNodes(files, view, lvp, p), eh, options);
    }

    private static Node locateChild(Node parent, LogicalViewProvider lvp, FileObject file) {
        if (lvp != null) {
            return lvp.findPath(parent, file);
        }

        throw new UnsupportedOperationException("Not done yet");
    }

    private static class Wrapper extends FilterNode {

        public Wrapper(Node orig, ComputeNodes fileNodes, ElementHandle<?> eh, Set<RemoteUsages.SearchOptions> options) {
            super(orig, new WrapperChildren(orig, fileNodes, eh, options));
        }

        @Override
        public Action[] getActions(boolean context) {
            return new Action[0];
        }

    }

    private static boolean isParent(Node parent, Node child) {
        if (NodeOp.isSon(parent, child)) {
            return true;
        }

        Node p = child.getParentNode();

        if (p == null) {
            return false;
        }

        return isParent(parent, p);
    }

    private static class WrapperChildren extends Children.Keys<Node> {

        private final Node orig;
        private final ComputeNodes fileNodes;
        private final ElementHandle<?> eh;
        private final Set<RemoteUsages.SearchOptions> options;

        public WrapperChildren(Node orig, ComputeNodes fileNodes, ElementHandle<?> eh, Set<RemoteUsages.SearchOptions> options) {
            this.orig = orig;
            this.fileNodes = fileNodes;
            this.eh = eh;
            this.options = options;

        }

        @Override
        protected void addNotify() {
            super.addNotify();
            doSetKeys();
        }

        private void doSetKeys() {
            Node[] nodes = orig.getChildren().getNodes(true);
            List<Node> toSet = new LinkedList<Node>();

            OUTER: for (Node n : nodes) {
                for (Node c : fileNodes.compute()) {
                    if (n == c || isParent(n, c)) {
                        toSet.add(n);
                        continue OUTER;
                    }
                }
            }

            setKeys(toSet);
        }

        @Override
        protected Node[] createNodes(Node key) {
            if (fileNodes.compute().contains(key)) {
                FileObject file = key.getLookup().lookup(FileObject.class);
                Children c = file != null ? Children.create(new UsagesChildren(file, eh, options), true) : Children.LEAF;
                
                return new Node[] {new FilterNode(key, c)}; //XXX
            }
            return new Node[] {new Wrapper(key, fileNodes, eh, options)};
        }

    }

    private static final class DirectChildren extends Children.Keys<Node> {

        public DirectChildren(Collection<Node> nodes) {
            setKeys(nodes);
        }

        @Override
        protected Node[] createNodes(Node key) {
            return new Node[] {key};
        }
    }

    private static Node noOccurrencesNode() {
        AbstractNode noOccurrences = new AbstractNode(Children.LEAF);

        noOccurrences.setDisplayName("No Occurrences Found");

        return noOccurrences;
    }
    
    private static final class UsagesChildren extends ChildFactory<Node> {

        private final FileObject file;
        private final ElementHandle<?> eh;
        private final Set<RemoteUsages.SearchOptions> options;

        public UsagesChildren(FileObject file, ElementHandle<?> eh, Set<RemoteUsages.SearchOptions> options) {
            this.file = file;
            this.eh = eh;
            this.options = options;
        }

        @Override
        protected boolean createKeys(final List<Node> toPopulate) {
            List<Node> result = new ArrayList<Node>();

            if (!computeOccurrences(file, eh, options, result)) {
                result.clear();

                ClassPath source = ClassPath.getClassPath(file, ClassPath.SOURCE);

                GlobalPathRegistry.getDefault().register(ClassPath.SOURCE, new ClassPath[] {source});

                try {
                    SourceUtils.waitScanFinished();
                    computeOccurrences(file, eh, options, result);
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                } finally {
                    GlobalPathRegistry.getDefault().unregister(ClassPath.SOURCE, new ClassPath[] {source});
                }
            }

            toPopulate.addAll(result);
            
            if (toPopulate.isEmpty()) toPopulate.add(noOccurrencesNode());

            return true;
        }

        @Override
        protected Node createNodeForKey(Node key) {
            return key;
        }

    }

    static boolean computeOccurrences(FileObject file, final ElementHandle<?> eh, final Set<RemoteUsages.SearchOptions> options, final List<Node> toPopulate) {
        final boolean[] success = new boolean[] {true};

        try {
            JavaSource.forFileObject(file).runUserActionTask(new Task<CompilationController>() {
                @Override public void run(final CompilationController parameter) throws Exception {
                    parameter.toPhase(Phase.RESOLVED);

                    final Element toFind = eh.resolve(parameter);

                    if (toFind == null) {
                        return;
                    }

                    final AtomicBoolean stop = new AtomicBoolean();

                    new CancellableTreePathScanner<Void, Void>(stop) {
                                    @Override public Void visitIdentifier(IdentifierTree node, Void p) {
                                        handleNode(node.getName(), getCurrentPath().getLeaf());
                                        return super.visitIdentifier(node, p);
                                    }
                                    @Override public Void visitMemberSelect(MemberSelectTree node, Void p) {
                                        handleNode(node.getIdentifier(), getCurrentPath().getLeaf());
                                        return super.visitMemberSelect(node, p);
                                    }
                                    @Override public Void visitNewClass(NewClassTree node, Void p) {
                                        Name simpleName = null;
                                        Tree name = node.getIdentifier();

                                        OUTER: while (true) {
                                            switch (name.getKind()) {
                                                case PARAMETERIZED_TYPE: name = ((ParameterizedTypeTree) name).getType(); break;
                                                case MEMBER_SELECT: simpleName = ((MemberSelectTree) name).getIdentifier(); break OUTER;
                                                case IDENTIFIER: simpleName = ((IdentifierTree) name).getName(); break OUTER;
                                                default: name = node; break OUTER;
                                            }
                                        }

                                        handleNode(simpleName, name);
                                        return super.visitNewClass(node, p);
                                    }
                                    private void handleNode(Name simpleName, Tree toHighlight) {
                                        if (!options.contains(RemoteUsages.SearchOptions.USAGES)) return;
                                        Element el = parameter.getTrees().getElement(getCurrentPath());

                                        if (el == null || el.asType().getKind() == TypeKind.ERROR) {
                                            if (toFind.getSimpleName().equals(simpleName)) {
                                                success[0] = false;
                                                stop.set(true);
                                                return; //TODO: correct? what about the second pass?
                                            }
                                        }
                                        if (Nodes.equals(parameter, toFind, el)) {
                                            toPopulate.add(new OccurrenceNode(parameter, toHighlight));
                                        }
                                    }
                                    @Override
                                    public Void visitMethod(MethodTree node, Void p) {
                                        if (options.contains(RemoteUsages.SearchOptions.SUB) && toFind.getKind() == ElementKind.METHOD) {
                                            boolean found = false;
                                            Element el = parameter.getTrees().getElement(getCurrentPath());

                                            if (el != null && el.getKind() == ElementKind.METHOD) {
                                                if (parameter.getElements().overrides((ExecutableElement) el, (ExecutableElement) toFind, (TypeElement) el.getEnclosingElement())) {
                                                    toPopulate.add(new OccurrenceNode(parameter, node));
                                                    found = true;
                                                }
                                            }

                                            if (!found && el != null && el.getSimpleName().contentEquals(toFind.getSimpleName())) {
                                                for (TypeMirror sup : superTypes((TypeElement) el.getEnclosingElement())) {
                                                    if (sup.getKind() == TypeKind.ERROR) {
                                                        success[0] = false;
                                                        stop.set(true);
                                                        return null; //TODO: correct? what about the second pass?
                                                    }
                                                }
                                            }
                                        }
                                        return super.visitMethod(node, p);
                                    }
                                    @Override
                                    public Void visitClass(ClassTree node, Void p) {
                                        if (options.contains(RemoteUsages.SearchOptions.SUB) && (toFind.getKind().isClass() || toFind.getKind().isInterface())) {
                                            Element el = parameter.getTrees().getElement(getCurrentPath());
                                            boolean wasError = false;

                                            for (TypeMirror sup : superTypes((TypeElement) el)) {
                                                if (sup.getKind() == TypeKind.ERROR) {
                                                    wasError = true;
                                                } else {
                                                    if (toFind.equals(parameter.getTypes().asElement(sup))) {
                                                        wasError = false;
                                                        toPopulate.add(new OccurrenceNode(parameter, node));
                                                        break;
                                                    }
                                                }
                                            }

                                            if (wasError) {
                                                success[0] = false;
                                                stop.set(true);
                                                return null; //TODO: correct? what about the second pass?
                                            }
                                        }
                                        
                                        return super.visitClass(node, p);
                                    }
                                    private Set<TypeMirror> superTypes(TypeElement type) {
                                        Set<TypeMirror> result = new HashSet<TypeMirror>();
                                        List<TypeMirror> todo = new LinkedList<TypeMirror>();
                                        
                                        todo.add(type.asType());
                                        
                                        while (!todo.isEmpty()) {
                                            List<? extends TypeMirror> directSupertypes = parameter.getTypes().directSupertypes(todo.remove(0));

                                            todo.addAll(directSupertypes);
                                            result.addAll(directSupertypes);
                                        }

                                        return result;
                                    }
                                }.scan(parameter.getCompilationUnit(), null);
                }
            }, true);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        return success[0];
    }

    private static boolean equals(CompilationInfo info, Element toFind, Element what) {
        if (toFind == what) return true;
        if (what == null) return false;
        if (toFind.getKind() != what.getKind()) return false;
        if (toFind.getKind() != ElementKind.METHOD) return false;

        return info.getElements().overrides((ExecutableElement) what, (ExecutableElement) toFind, (TypeElement) what.getEnclosingElement());
    }

    private static final class OccurrenceNode extends AbstractNode {
        private final FileObject file;
        private final int pos;
        private final String htmlDisplayName;

        public OccurrenceNode(CompilationInfo info, Tree occurrence) {
            this(info, occurrence, new InstanceContent());
        }

        private OccurrenceNode(CompilationInfo info, Tree occurrence, InstanceContent content) {
            super(Children.LEAF, new AbstractLookup(content));

            int[] span;

            switch (occurrence.getKind()) {
                case MEMBER_SELECT: span = info.getTreeUtilities().findNameSpan((MemberSelectTree) occurrence); break;
                case METHOD: span = info.getTreeUtilities().findNameSpan((MethodTree) occurrence); break;
                case CLASS: span = info.getTreeUtilities().findNameSpan((ClassTree) occurrence); break;
                default:
                    SourcePositions sp = info.getTrees().getSourcePositions();

                    span = new int[] {(int) sp.getStartPosition(info.getCompilationUnit(), occurrence),
                                      (int) sp.getEndPosition(info.getCompilationUnit(), occurrence)};
                    break;
            }

            long startLine = info.getCompilationUnit().getLineMap().getLineNumber(span[0]);
            long startLineStart = info.getCompilationUnit().getLineMap().getStartPosition(startLine);

            String dn;

            try {
                DataObject od = DataObject.find(info.getFileObject());
                LineCookie lc = od.getLookup().lookup(LineCookie.class);
                Line l = lc.getLineSet().getCurrent((int) startLine - 1);
                od.getLookup().lookup(EditorCookie.class).openDocument();
                String line = l.getText();
                int endOnLine = (int) Math.min(line.length(), span[1] - startLineStart);

                dn = translate(line.substring(0, (int) (span[0] - startLineStart))) + "<b>" + translate(line.substring((int) (span[0] - startLineStart), endOnLine)) + "</b>" + translate(line.substring(endOnLine));
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                dn = "Occurrence";
            }

            this.htmlDisplayName = dn;
            this.file = info.getFileObject();
            this.pos = span[0];
            
            content.add(new OpenCookie() {
                @Override public void open() {
                    UiUtils.open(file, pos);
                }
            });
        }

        @Override
        public String getHtmlDisplayName() {
            return htmlDisplayName;
        }

        @Override
        public Action[] getActions(boolean context) {
            return new Action[] {
                OpenAction.get(OpenAction.class)
            };
        }

        @Override
        public Action getPreferredAction() {
            return OpenAction.get(OpenAction.class);
        }

    }

    private static String[] c = new String[] {"&", "<", ">", "\n", "\""}; // NOI18N
    private static String[] tags = new String[] {"&amp;", "&lt;", "&gt;", "<br>", "&quot;"}; // NOI18N

    private static String translate(String input) {
        for (int cntr = 0; cntr < c.length; cntr++) {
            input = input.replaceAll(c[cntr], tags[cntr]);
        }

        return input;
    }

    private static class ComputeNodes  {

        private final Iterable<? extends FileObject> files;
        private final Node view;
        private final LogicalViewProvider lvp;
        private final Project p;

        public ComputeNodes(Iterable<? extends FileObject> files, Node view, LogicalViewProvider lvp, Project p) {
            this.files = files;
            this.view = view;
            this.lvp = lvp;
            this.p = p;
        }
        
        private Collection<Node> result;

        public synchronized Collection<Node> compute() {
            if (result != null) return result;

            Collection<Node> fileNodes = new ArrayList<Node>();

            for (FileObject file : files) {
                Node foundChild = locateChild(view, lvp, file);

                if (foundChild == null) {
                    foundChild = new AbstractNode(Children.LEAF) {
                        @Override
                        public Image getIcon(int type) {
                            return ImageUtilities.icon2Image(ProjectUtils.getInformation(p).getIcon());
                        }
                        @Override
                        public Image getOpenedIcon(int type) {
                            return getIcon(type);
                        }
                        @Override
                        public String getHtmlDisplayName() {
                            return view.getHtmlDisplayName() != null ? NbBundle.getMessage(Nodes.class, "ERR_ProjectNotSupported", view.getHtmlDisplayName()) : null;
                        }
                        @Override
                        public String getDisplayName() {
                            return NbBundle.getMessage(Nodes.class, "ERR_ProjectNotSupported", view.getDisplayName());
                        }
                    };
                }

                fileNodes.add(foundChild);
            }

            return result = fileNodes;
        }
    }
}
