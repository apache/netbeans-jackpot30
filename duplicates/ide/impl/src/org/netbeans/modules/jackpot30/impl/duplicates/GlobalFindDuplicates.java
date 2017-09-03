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
package org.netbeans.modules.jackpot30.impl.duplicates;

import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.modules.jackpot30.impl.duplicates.ComputeDuplicates.DuplicateDescription;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.RequestProcessor;

public final class GlobalFindDuplicates implements ActionListener {

    public void actionPerformed(ActionEvent e) {
        final Iterator<? extends DuplicateDescription>[] dupes = new Iterator[1];
        final ProgressHandle handle = ProgressHandleFactory.createHandle("Compute Duplicates");
        JPanel panel = createPanel(handle);
        final AtomicBoolean cancel = new AtomicBoolean();
        DialogDescriptor w = new DialogDescriptor(panel, "Computing Duplicates", true, new Object[] {DialogDescriptor.CANCEL_OPTION}, DialogDescriptor.CANCEL_OPTION, DialogDescriptor.DEFAULT_ALIGN, HelpCtx.DEFAULT_HELP, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                cancel.set(true);
            }
        });

        w.setClosingOptions(null);

        final Dialog d = DialogDisplayer.getDefault().createDialog(w);
        final AtomicBoolean done = new AtomicBoolean();
        final Collection<String> sourceRoots = new LinkedList<String>();

        WORKER.post(new Runnable() {
            public void run() {
                try {
                    for (ClassPath cp : GlobalPathRegistry.getDefault().getPaths(ClassPath.SOURCE)) {
                        for (ClassPath.Entry e : cp.entries()) {
                            FileObject root = e.getRoot();

                            if (root == null) continue;

                            sourceRoots.add(FileUtil.getFileDisplayName(root));
                        }
                    }

                    dupes[0] = new ComputeDuplicates().computeDuplicatesForAllOpenedProjects(handle, cancel);
                    done.set(true);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                } finally {
                    handle.finish();

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            d.setVisible(false);
                        }
                    });
                }
            }
        });

        handle.start();
        handle.progress(" ");

        d.setVisible(true);

        if (!done.get()) {
            cancel.set(true);
            return;
        }
        
        if (cancel.get()) return;

        NotifyDescriptor nd = new NotifyDescriptor.Message(new DuplicatesListPanel(sourceRoots, dupes[0]));

        DialogDisplayer.getDefault().notifyLater(nd);
    }

    private JPanel createPanel(ProgressHandle handle) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gridBagConstraints;

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new Insets(6, 6, 0, 6);
        panel.add(new JLabel("Computing Duplicates - Please Wait"), gridBagConstraints);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new Insets(6, 6, 0, 6);
        panel.add(ProgressHandleFactory.createProgressComponent(handle), gridBagConstraints);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new Insets(6, 6, 6, 6);
        panel.add(ProgressHandleFactory.createDetailLabelComponent(handle), gridBagConstraints);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        panel.add(new JPanel(), gridBagConstraints);

        return panel;
    }

    private static final RequestProcessor WORKER = new RequestProcessor(GlobalFindDuplicates.class.getName(), 1);
    
}
