/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009-2010 Sun Microsystems, Inc. All rights reserved.
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
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
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
 * Portions Copyrighted 2009-2010 Sun Microsystems, Inc.
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
