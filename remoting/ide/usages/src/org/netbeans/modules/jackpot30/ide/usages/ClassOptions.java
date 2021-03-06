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

import java.util.Set;

/**
 *
 * @author lahvac
 */
public class ClassOptions extends javax.swing.JPanel {

    private final Set<RemoteUsages.SearchOptions> options;
    
    public ClassOptions(Set<RemoteUsages.SearchOptions> options) {
        this.options = options;
        initComponents();
        usages.setSelected(true);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup = new javax.swing.ButtonGroup();
        usages = new javax.swing.JRadioButton();
        subclasses = new javax.swing.JRadioButton();

        buttonGroup.add(usages);
        usages.setText(org.openide.util.NbBundle.getMessage(ClassOptions.class, "ClassOptions.usages.text", new Object[] {})); // NOI18N
        usages.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                usagesItemStateChanged(evt);
            }
        });

        buttonGroup.add(subclasses);
        subclasses.setText(org.openide.util.NbBundle.getMessage(ClassOptions.class, "ClassOptions.subclasses.text", new Object[] {})); // NOI18N
        subclasses.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                subclassesItemStateChanged(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(usages)
                    .addComponent(subclasses))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(usages)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(subclasses)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void usagesItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_usagesItemStateChanged
        if (usages.isSelected())
            options.add(RemoteUsages.SearchOptions.USAGES);
        else
            options.remove(RemoteUsages.SearchOptions.USAGES);
    }//GEN-LAST:event_usagesItemStateChanged

    private void subclassesItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_subclassesItemStateChanged
        if (subclasses.isSelected())
            options.add(RemoteUsages.SearchOptions.SUB);
        else
            options.remove(RemoteUsages.SearchOptions.SUB);
    }//GEN-LAST:event_subclassesItemStateChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup;
    private javax.swing.JRadioButton subclasses;
    private javax.swing.JRadioButton usages;
    // End of variables declaration//GEN-END:variables
}
