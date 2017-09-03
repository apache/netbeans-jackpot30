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
package org.netbeans.modules.jackpot30.resolve.api;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.lexer.TokenHierarchy;

/**
 *
 * @author lahvac
 */
public class CompilationInfo {

    private final Javac javac;
    private final CompilationUnitTree cut;
    private final String text;
    private final TokenHierarchy<?> th;

    public CompilationInfo(Javac javac, CompilationUnitTree cut, String text) {
        this.javac = javac;
        this.cut = cut;
        this.text = text;
        this.th = TokenHierarchy.create(text, JavaTokenId.language());
    }

    public /*@NonNull*/ Trees getTrees() {
        return Trees.instance(javac.getTask());
    }

    public /*@NonNull*/ Types getTypes() {
        return javac.getTask().getTypes();
    }

    public /*@NonNull*/ Elements getElements() {
	return javac.getTask().getElements();
    }

    public CompilationUnitTree getCompilationUnit() {
        return cut;
    }

    public /*@NonNull*/ String getText() {
        return text;
    }

    public /*@NonNull*/ TokenHierarchy<?> getTokenHierarchy() {
        return th;
    }

    public JavacTask getJavacTask() {
        return javac.getTask();
    }
}
