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

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.java.source.CompilationInfo;

/**
 *
 * @author lahvac
 */
public class PatternGenerator {

    public static @CheckForNull String generateFindUsagesScript(CompilationInfo info, Element usagesOf) {
        switch (usagesOf.getKind()) {
            case METHOD: return generateMethodFindUsagesScript(info, (ExecutableElement) usagesOf);
            case ENUM_CONSTANT:
            case FIELD: return generateFieldFindUsagesScript(info, (VariableElement) usagesOf);
            case ANNOTATION_TYPE:
            case CLASS:
            case ENUM:
            case INTERFACE: return generateClassFindUsagesScript(info, (TypeElement) usagesOf);
            default: return null;
        }
    }

    private static @NonNull String generateMethodFindUsagesScript(CompilationInfo info, ExecutableElement usagesOf) {
        StringBuilder script = new StringBuilder();
        StringBuilder parameters = new StringBuilder();
        StringBuilder constraints = new StringBuilder();
        String enclosingType = info.getTypes().erasure(usagesOf.getEnclosingElement().asType()).toString();
        int count = 1;

        if (usagesOf.getModifiers().contains(Modifier.STATIC)) {
            script.append(enclosingType);
        } else {
            script.append("$this");
            constraints.append("$this instanceof ").append(enclosingType);
        }

        script.append(".").append(usagesOf.getSimpleName()).append("(");

        for (VariableElement p : usagesOf.getParameters()) {
            if (count > 1) {
                parameters.append(", ");
            }

            if (constraints.length() > 0) {
                constraints.append(" && ");
            }
            parameters.append("$").append(count);
            constraints.append("$").append(count).append(" instanceof ").append(info.getTypes().erasure(p.asType()));
            count++;
        }

        script.append(parameters).append(")");

        if (constraints.length() > 0) {
            script.append(" :: ").append(constraints);
        }

        script.append(";;");

        return script.toString();
    }

    private static @NonNull String generateFieldFindUsagesScript(CompilationInfo info, VariableElement usagesOf) {
        StringBuilder script = new StringBuilder();
        StringBuilder constraints = new StringBuilder();
        String enclosingType = info.getTypes().erasure(usagesOf.getEnclosingElement().asType()).toString();
        int count = 1;

        if (usagesOf.getModifiers().contains(Modifier.STATIC)) {
            script.append(enclosingType);
        } else {
            script.append("$this");
            constraints.append("$this instanceof ").append(enclosingType);
        }

        script.append(".").append(usagesOf.getSimpleName());

        if (constraints.length() > 0) {
            script.append(" :: ").append(constraints);
        }

        script.append(";;");

        return script.toString();
    }

    private static @NonNull String generateClassFindUsagesScript(CompilationInfo info, TypeElement usagesOf) {
        StringBuilder script = new StringBuilder();

        script.append(usagesOf.getQualifiedName());
        script.append(";;");

        return script.toString();
    }

}
