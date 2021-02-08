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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.MutableComboBoxModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.NbPreferences;
import org.openide.util.WeakListeners;

/**
 *
 * @author lahvac
 */
public class DecompileToolbar extends javax.swing.JPanel {

    private static final int HISTORY_LIMIT = 2;
    
    public DecompileToolbar(final FileObject decompiled, final FileObject originalSource) {
        initComponents();
        
        DefaultComboBoxModel<CompilerDescription> compilerModel = new DefaultComboBoxModel<>();
        Collection<? extends CompilerDescription> compilerDescriptions = CompilerDescription.Factory.descriptions();
        
        for (CompilerDescription cd : compilerDescriptions) {
            compilerModel.addElement(cd);
        }
        
        compiler.setModel(compilerModel);
        compiler.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                if (value instanceof CompilerDescription) {
                    CompilerDescription compilerDescription = (CompilerDescription) value;
                    
                    value = compilerDescription.getName() + (compilerDescription.isValid() ? "" : " - unusable");
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });
        compiler.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                try {
                    decompiled.setAttribute(CompilerDescription.class.getName(), compiler.getSelectedItem());
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
        
        DefaultComboBoxModel<DecompilerDescription> decompilerModel = new DefaultComboBoxModel<>();
        List<DecompilerDescription> decompilers = new ArrayList<>();
        
        if (compilerDescriptions.size() > 0) {
            compiler.setSelectedIndex(0);
            
            for (DecompilerDescription decompiler : DecompilerDescription.getDecompilers()) {
                decompilerModel.addElement(decompiler);
                decompilers.add(decompiler);
            }
        }
        
        decompiler.setModel(decompilerModel);
        decompiler.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                if (value instanceof DecompilerDescription) value = ((DecompilerDescription) value).displayName;
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });
        decompiler.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                try {
                    decompiled.setAttribute(DecompilerDescription.class.getName(), ((DecompilerDescription) decompiler.getSelectedItem()).id);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
        decompiler.setSelectedIndex(0);
        extraOptions.setModel(createModel());
        Object extraParams = originalSource.getAttribute(DecompiledTab.PROP_EXTRA_PARAMS);
        if (extraParams instanceof String) {
            try {
                extraOptions.setSelectedItem(extraParams);
                decompiled.setAttribute(DecompiledTab.PROP_EXTRA_PARAMS, extraParams);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        extraOptions.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String selected = (String) extraOptions.getSelectedItem();

                    masterModel.removeElement(selected);
                    masterModel.insertElementAt(selected, 0);

                    while (masterModel.getSize() > HISTORY_LIMIT) {
                        masterModel.removeElementAt(masterModel.getSize() - 1);
                    }

                    storeMasterData();

                    extraOptions.getModel().setSelectedItem(selected); //the above changes the selection

                    decompiled.setAttribute(DecompiledTab.PROP_EXTRA_PARAMS, selected);
                    originalSource.setAttribute(DecompiledTab.PROP_EXTRA_PARAMS, selected);

                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
    }

    private static MutableComboBoxModel<String> masterModel;

    private static final String KEY_EXTRA_PARAMS_HISTORY = "extraParamsHistory";

    private static Preferences getHistoryNode() {
        Preferences prefs = NbPreferences.forModule(DecompileToolbar.class);
        
        return prefs.node(KEY_EXTRA_PARAMS_HISTORY);
    }

    private ComboBoxModel<String> createModel() {
        if (masterModel == null) {
            masterModel = new DefaultComboBoxModel<>();

            try {
                Preferences prefs = getHistoryNode();
                List<String> keys = new ArrayList<>(Arrays.asList(prefs.keys()));

                Collections.sort(keys);

                for (String key : keys) {
                    masterModel.addElement(prefs.get(key, ""));
                }
            } catch (BackingStoreException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        return new DelegatingModel(masterModel);
    }

    private static void storeMasterData() {
        try {
            Preferences prefs = getHistoryNode();

            prefs.clear();

            for (int i = 0; i < masterModel.getSize(); i++) {
                prefs.put(Integer.toString(i), masterModel.getElementAt(i));
            }
        } catch (BackingStoreException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        compiler = new javax.swing.JComboBox();
        jLabel2 = new javax.swing.JLabel();
        decompiler = new javax.swing.JComboBox();
        jLabel3 = new javax.swing.JLabel();
        extraOptions = new javax.swing.JComboBox();

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(DecompileToolbar.class, "DecompileToolbar.jLabel1.text")); // NOI18N

        compiler.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(DecompileToolbar.class, "DecompileToolbar.jLabel2.text")); // NOI18N

        decompiler.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(DecompileToolbar.class, "DecompileToolbar.jLabel3.text")); // NOI18N

        extraOptions.setEditable(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(compiler, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(decompiler, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(extraOptions, 0, 338, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(jLabel1)
                .addComponent(compiler, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(jLabel2)
                .addComponent(decompiler, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(jLabel3)
                .addComponent(extraOptions, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox compiler;
    private javax.swing.JComboBox decompiler;
    private javax.swing.JComboBox extraOptions;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    // End of variables declaration//GEN-END:variables

    private static class DelegatingModel implements ComboBoxModel<String>, ListDataListener {
        private final ComboBoxModel<String> master;
        private final List<ListDataListener> listeners = new ArrayList<>();

        private Object selected;

        public DelegatingModel(ComboBoxModel<String> master) {
            this.master = master;
            this.master.addListDataListener(WeakListeners.create(ListDataListener.class, this, master));
        }

        @Override
        public void setSelectedItem(Object anItem) {
            this.selected = anItem;
            ListDataEvent del = new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, -1, -1);

            for (ListDataListener l : listeners) {
                l.contentsChanged(del);
            }
        }

        @Override
        public Object getSelectedItem() {
            return selected;
        }

        @Override
        public int getSize() {
            return master.getSize();
        }

        @Override
        public String getElementAt(int index) {
            return master.getElementAt(index);
        }

        @Override
        public void addListDataListener(ListDataListener l) {
            listeners.add(l);
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
            listeners.remove(l);
        }

        @Override
        public void intervalAdded(ListDataEvent e) {
            ListDataEvent del = new ListDataEvent(this, e.getType(), e.getIndex0(), e.getIndex1());

            for (ListDataListener l : listeners) {
                l.intervalAdded(del);
            }
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
            ListDataEvent del = new ListDataEvent(this, e.getType(), e.getIndex0(), e.getIndex1());

            for (ListDataListener l : listeners) {
                l.intervalRemoved(del);
            }
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
            ListDataEvent del = new ListDataEvent(this, e.getType(), e.getIndex0(), e.getIndex1());

            for (ListDataListener l : listeners) {
                l.contentsChanged(del);
            }
        }
    }
}
