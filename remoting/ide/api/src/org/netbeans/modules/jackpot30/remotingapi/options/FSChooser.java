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
package org.netbeans.modules.jackpot30.remotingapi.options;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.netbeans.modules.jackpot30.remoting.api.FileSystemLister;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.BeanTreeView;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.loaders.DataFilter;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class FSChooser {

    public static URL select(String caption, String okButton, URL preselect) {
        Node n = new RootNode();
        final ExplorerManager m = new ExplorerManager();

        m.setRootContext(n);

        class P extends JPanel implements ExplorerManager.Provider {
            public P() {
                setLayout(new BorderLayout());
                add(new BeanTreeView(), BorderLayout.CENTER);
            }
            @Override public ExplorerManager getExplorerManager() {
                return m;
            }
        }

        //XXX: asynchronously!
        FileObject toSelect = preselect != null ? URLMapper.findFileObject(preselect) : null;

        toSelect = toSelect != null ? toSelect : homeFO();

        if (toSelect != null) {
            try {
                Node toSelectNode = findFileNode(n, toSelect);

                if (toSelectNode == null) {
                    FileObject home = homeFO();

                    if (home != null) {
                        toSelectNode = findFileNode(n, home);
                    }
                }

                if (toSelectNode != null) {
                    m.setSelectedNodes(new Node[] {toSelectNode});
                }
            } catch (PropertyVetoException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        final boolean[] accepted = new boolean[1];
        final Dialog[] d = new Dialog[1];
        final JButton ok = new JButton(okButton);

        m.addPropertyChangeListener(new PropertyChangeListener() {
            @Override public void propertyChange(PropertyChangeEvent evt) {
                ok.setEnabled(m.getSelectedNodes().length == 1);
            }
        });

        ok.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                d[0].setVisible(false);
                accepted[0] = true;
            }
        });

        DialogDescriptor dd = new DialogDescriptor(new P(), caption, true, new Object[] {ok, DialogDescriptor.CANCEL_OPTION}, ok, DialogDescriptor.DEFAULT_ALIGN, null, null);

        d[0] = DialogDisplayer.getDefault().createDialog(dd);

        d[0].setVisible(true);

        if (accepted[0]) {
            try {
                return m.getSelectedNodes()[0].getLookup().lookup(FileObject.class).getURL();
            } catch (FileStateInvalidException ex) {
                Exceptions.printStackTrace(ex);
                return null;
            }
        } else {
            return null;
        }
    }

    private static FileObject homeFO() {
        return FileUtil.toFileObject(FileUtil.normalizeFile(new File(System.getProperty("user.home"))));
    }

    private static Node findFileNode(Node root, FileObject file) {
        for (Node n : root.getChildren().getNodes(true)) {
            FileObject p = n.getLookup().lookup(FileObject.class);

            if (p == null) {
                continue;
            }

            if (FileUtil.isParentOf(p, file)) {
                return findFileNode(n, file);
            }

            if (p == file) return n;
        }

        return null;
    }
    
    private static final class RootNode extends AbstractNode {
        public RootNode() {
            super(new RootChildren());
        }
    }

    private static final class RootChildren extends Children.Keys<FileSystem> {

        @Override
        protected Node[] createNodes(FileSystem key) {
            try {
                DataFolder f = DataFolder.findFolder(key.getRoot());
                
                return new Node[] {
                    new FSNode(f)
                };
            } catch (FileStateInvalidException ex) {
                Exceptions.printStackTrace(ex);
                return new Node[0];
            }
        }

        @Override
        protected void addNotify() {
            List<FileSystem> fss = new ArrayList<FileSystem>();

            for (FileSystemLister l : Lookup.getDefault().lookupAll(FileSystemLister.class)) {
                fss.addAll(l.getKnownFileSystems());
            }

            setKeys(fss);
            
            super.addNotify();
        }

        @Override
        protected void removeNotify() {
            super.removeNotify();

            setKeys(Collections.<FileSystem>emptyList());
        }
    }
    
    private static final class FSNode extends FilterNode {

        public FSNode(DataFolder original) throws FileStateInvalidException {
            super(original.getNodeDelegate(), original.createNodeChildren(new DataFilter() {
                @Override public boolean acceptDataObject(DataObject obj) {
                    return obj instanceof DataFolder;
                }
            }));
        }
    }
    
    @ServiceProvider(service=FileSystemLister.class)
    public static final class LocalFileSystemLister implements FileSystemLister {
        @Override public Collection<? extends FileSystem> getKnownFileSystems() {
            Set<FileSystem> fss = new HashSet<FileSystem>();

            for (File f : File.listRoots()) {
                FileObject fo = FileUtil.toFileObject(f);

                if (fo != null) {
                    try {
                        fss.add(fo.getFileSystem());
                    } catch (FileStateInvalidException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }

            return fss;
        }
    }
}
