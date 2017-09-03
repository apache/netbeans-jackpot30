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
