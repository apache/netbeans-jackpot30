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

package org.netbeans.modules.jackpot30.ui;

import com.sun.source.util.TreePath;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.IOException;
import javax.lang.model.element.Element;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.Task;
import org.netbeans.editor.BaseAction;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.datatransfer.ExClipboard;

@Messages("generate-jackpot-script=Generate Jackpot Script")
@EditorActionRegistration(name = "generate-jackpot-script")
public final class GenerateJackpotScript extends BaseAction {

    public GenerateJackpotScript() {
        super();
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        JavaSource js = JavaSource.forDocument(target.getDocument());

        if (js == null) {
            StatusDisplayer.getDefault().setStatusText("Not a Java file.");
            return ;
        }

        final int caret = target.getCaretPosition();

        try {
            js.runUserActionTask(new Task<CompilationController>() {
                @Override public void run(CompilationController parameter) throws Exception {
                    parameter.toPhase(Phase.RESOLVED);
                    TreePath tp = parameter.getTreeUtilities().pathFor(caret);
                    Element  el = parameter.getTrees().getElement(tp);

                    if (el != null) {
                        String script = PatternGenerator.generateFindUsagesScript(parameter, el);
                        Clipboard clipboard = Lookup.getDefault().lookup(ExClipboard.class);

                        if (clipboard == null) {
                            clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        }

                        clipboard.setContents(new StringSelection(script), null);

                        StatusDisplayer.getDefault().setStatusText("Script generated into clipboard.");
                    } else {
                        StatusDisplayer.getDefault().setStatusText("Cannot resolve Java element.");
                    }
                }
            }, true);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

}
