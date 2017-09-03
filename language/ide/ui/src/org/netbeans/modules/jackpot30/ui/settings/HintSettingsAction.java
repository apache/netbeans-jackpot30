/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2012 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2012 Sun Microsystems, Inc.
 */

package org.netbeans.modules.jackpot30.ui.settings;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;

@ActionID(
    category = "Tools",
    id = "org.netbeans.modules.jackpot30.ui.settings.HintSettingsAction")
@ActionRegistration(
    displayName = "#CTL_HintSettingsAction")
@ActionReferences({
    @ActionReference(path = "Menu/Tools", position = 1250)
})
@Messages("CTL_HintSettingsAction=Hint Settings")
public final class HintSettingsAction implements ActionListener {

    public void actionPerformed(ActionEvent e) {
        JFileChooser f = new JFileChooser();

        f.setFileFilter(new FileFilter() {
            @Override public boolean accept(File f) {
                return f.getName().endsWith(".xml");
            }
            @Override public String getDescription() {
                return "XML Files";
            }
        });

        if (f.showDialog(null, "Open") == JFileChooser.APPROVE_OPTION) {
            try {
                File settings = f.getSelectedFile();
                final Preferences p = XMLHintPreferences.from(settings);
                JPanel hintPanel = createHintPanel(p.node("settings"));

                if (hintPanel == null) {
                    //TODO: warn the user
                    return ;
                }

                final JCheckBox runDeclarativeHints = new JCheckBox("Run Declarative Rules");

                runDeclarativeHints.setToolTipText("Should the declarative rules found on classpath be run?");
                runDeclarativeHints.setSelected(p.getBoolean("runDeclarative", true));
                runDeclarativeHints.addActionListener(new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) {
                        p.putBoolean("runDeclarative", runDeclarativeHints.isSelected());
                    }
                });

                JPanel customizer = new JPanel(new BorderLayout());

                customizer.add(hintPanel, BorderLayout.CENTER);
                customizer.add(runDeclarativeHints, BorderLayout.SOUTH);

                JButton save = new JButton("Save");
                DialogDescriptor dd = new DialogDescriptor(customizer, "Settings", true, new Object[] {save, DialogDescriptor.CANCEL_OPTION}, save, DialogDescriptor.DEFAULT_ALIGN, null, null);

                if (DialogDisplayer.getDefault().notify(dd) == save) {
                    p.flush();
                }
            } catch (BackingStoreException ex) {
                Exceptions.printStackTrace(ex);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private static Class<?> loadGlobalClass(String loadClass) {
        ClassLoader l = Lookup.getDefault().lookup(ClassLoader.class);
        
        if (l == null) {
            l = HintSettingsAction.class.getClassLoader();
        }

        try {
            if (l != null) {
                return l.loadClass(loadClass);
            } else {
                return Class.forName(loadClass);
            }
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(HintSettingsAction.class.getName()).log(Level.FINE, null, ex);
            return null;
        }
    }
    
    private static JPanel createHintPanel(Preferences p) {
        //XXX: constructing through reflection, so that we don't need to have too broad implementation dependencies:
        //new HintsPanel(p, new ClassPathBasedHintWrapper())
        try {
            Class<?> classPathBasedHintsWrapper = loadGlobalClass("org.netbeans.modules.java.hints.spiimpl.refactoring.Utilities$ClassPathBasedHintWrapper");
            Class<?> hintsPanel = loadGlobalClass("org.netbeans.modules.java.hints.spiimpl.options.HintsPanel");
            
            if (classPathBasedHintsWrapper == null || hintsPanel == null) return null;

            Constructor<?> newCPBHW = hintsPanel.getConstructor(Preferences.class, classPathBasedHintsWrapper);

            newCPBHW.setAccessible(true);
            
            return (JPanel) newCPBHW.newInstance(p, classPathBasedHintsWrapper.newInstance());
        } catch (InstantiationException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalAccessException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalArgumentException ex) {
            Exceptions.printStackTrace(ex);
        } catch (InvocationTargetException ex) {
            Exceptions.printStackTrace(ex);
        } catch (NoSuchMethodException ex) {
            Exceptions.printStackTrace(ex);
        } catch (SecurityException ex) {
            Exceptions.printStackTrace(ex);
        }

        return null;
    }
}
