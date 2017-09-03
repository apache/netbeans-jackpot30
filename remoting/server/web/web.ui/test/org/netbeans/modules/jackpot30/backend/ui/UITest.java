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

package org.netbeans.modules.jackpot30.backend.ui;

import java.util.ArrayList;
import java.util.Arrays;
import junit.framework.TestCase;

/**
 *
 * @author lahvac
 */
public class UITest extends TestCase {

    public UITest(String testName) {
        super(testName);
    }

    public void testDecodeType() throws Exception {
        assertEquals("CustomizerProvider<ClassPathBasedHintWrapper, JPanel>", UI.decodeSignatureType("Lorg/netbeans/modules/analysis/spi/Analyzer$CustomizerProvider<Lorg/netbeans/modules/java/hints/spiimpl/refactoring/Utilities$ClassPathBasedHintWrapper;Ljavax/swing/JPanel;>;", new int[1]));
        assertEquals("CustomizerProvider<?, ?>", UI.decodeSignatureType("Lorg/netbeans/modules/analysis/spi/Analyzer$CustomizerProvider<**>;", new int[1]));
    }

    public void testMethodSignature() throws Exception {
        assertEquals("(FileObject, List<ErrorDescription>)", UI.decodeMethodSignature("(Lorg/openide/filesystems/FileObject;Ljava/util/List<Lorg/netbeans/spi/editor/hints/ErrorDescription;>;)Ljava/util/List<Lorg/netbeans/spi/editor/hints/ErrorDescription;>;;"));
        assertEquals("()", UI.decodeMethodSignature("<D:Ljava/lang/Object;C:Ljavax/swing/JComponent;>()Lorg/netbeans/modules/analysis/spi/Analyzer$CustomizerProvider<TD;TC;>;;"));
        assertEquals("(Function<P, R>, P)", UI.decodeMethodSignature("<P:Lorg/netbeans/modules/java/source/queries/api/Queries;R:Ljava/lang/Object;>(Lorg/netbeans/modules/java/source/queries/api/Function<TP;TR;>;TP;)Lorg/netbeans/modules/java/source/queries/spi/QueriesController$Context<TR;>;;"));
    }

    public void testSimplifySignature() {
        assertEquals("METHOD:org.netbeans.spi.java.hints.JavaFixUtilities:rewriteFix:(Lorg/netbeans/api/java/source/CompilationInfo;Ljava/lang/String;Lcom/sun/source/util/TreePath;Ljava/lang/String;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;[Ljava/lang/String;)Lorg/netbeans/spi/editor/hints/Fix;",
                     UI.simplify("METHOD:org.netbeans.spi.java.hints.JavaFixUtilities:rewriteFix:(Lorg/netbeans/api/java/source/CompilationInfo;Ljava/lang/String;Lcom/sun/source/util/TreePath;Ljava/lang/String;Ljava/util/Map<Ljava/lang/String;Lcom/sun/source/util/TreePath;>;Ljava/util/Map<Ljava/lang/String;Ljava/util/Collection<Lcom/sun/source/util/TreePath;>;>;Ljava/util/Map<Ljava/lang/String;Ljava/lang/String;>;Ljava/util/Map<Ljava/lang/String;Ljavax/lang/model/type/TypeMirror;>;Ljava/util/Map<Ljava/lang/String;Ljava/lang/String;>;[Ljava/lang/String;)Lorg/netbeans/spi/editor/hints/Fix;;"));
        assertEquals("METHOD:org.netbeans.modules.java.hints.spiimpl.batch.BatchUtilities:fixDependencies:(Lorg/openide/filesystems/FileObject;Ljava/util/List;Ljava/util/Map;)Z",
                     UI.simplify("METHOD:org.netbeans.modules.java.hints.spiimpl.batch.BatchUtilities:fixDependencies:(Lorg/openide/filesystems/FileObject;Ljava/util/List<Lorg/netbeans/spi/java/hints/JavaFix;>;Ljava/util/Map<Lorg/netbeans/api/project/Project;Ljava/util/Set<Ljava/lang/String;>;>;)Z;"));
    }

    public void testSpansColoring() {
        String[] coloring = UI.colorTokens("package test; public class Test { }", new ArrayList<Long>());
        assertEquals("7, 1, 4, 1, 1, 6, 1, 5, 1, 4, 1, 1, 1, 1", coloring[0]);
        assertEquals("KWEEWKWKWEWEWE", coloring[1]);
        coloring = UI.colorTokens("package test; public class Test { }", Arrays.asList(0L, 6L));
        assertEquals("7, 1, 4, 1, 1, 6, 1, 5, 1, 4, 1, 1, 1, 1", coloring[0]);
        assertEquals("LWEEWKWKWEWEWE", coloring[1]);
        coloring = UI.colorTokens("package test; public class Test { }", Arrays.asList(1L, 3L));
        assertEquals("1, 3, 3, 1, 4, 1, 1, 6, 1, 5, 1, 4, 1, 1, 1, 1", coloring[0]);
        assertEquals("KLKWEEWKWKWEWEWE", coloring[1]);
        coloring = UI.colorTokens("package test; public class Test { }", Arrays.asList(16L, 17L));
        assertEquals("7, 1, 4, 1, 1, 2, 2, 2, 1, 5, 1, 4, 1, 1, 1, 1", coloring[0]);
        assertEquals("KWEEWKLKWKWEWEWE", coloring[1]);
        coloring = UI.colorTokens("package test; public class Test { }", Arrays.asList(3L, 17L));
        assertEquals("3, 4, 1, 4, 1, 1, 4, 2, 1, 5, 1, 4, 1, 1, 1, 1", coloring[0]);
        assertEquals("KLXFFXLKWKWEWEWE", coloring[1]);
    }
}
