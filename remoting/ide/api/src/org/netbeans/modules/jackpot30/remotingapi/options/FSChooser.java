/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2011 Sun Microsystems, Inc.
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
