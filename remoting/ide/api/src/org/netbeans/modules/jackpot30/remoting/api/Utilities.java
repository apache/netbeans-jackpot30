/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2013 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2013 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jackpot30.remoting.api;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.ElementKind;
import javax.swing.text.Document;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;

/**
 *
 * @author lahvac
 */
public class Utilities {
    private static final Logger LOG = Logger.getLogger(Utilities.class.getName());

    private Utilities() {}

    public static final class RemoteSourceDescription {
        public final RemoteIndex idx;
        public final String relative;
        public RemoteSourceDescription(RemoteIndex idx, String relative) {
            this.idx = idx;
            this.relative = relative;
        }
    }

    public static RemoteSourceDescription remoteSource(Document doc) {
        FileObject file = NbEditorUtilities.getFileObject(doc);

        if (file == null) return null;

        return remoteSource(file);
    }

    public static RemoteSourceDescription remoteSource(FileObject file) {
        Object remoteIndexObj = file.getAttribute("remoteIndex");
        RemoteIndex idx = remoteIndexObj instanceof RemoteIndex ? (RemoteIndex) remoteIndexObj : null;
        Object relativeObj = file.getAttribute("relative");
        String relative = relativeObj instanceof String ? (String) relativeObj : null;
        if (idx != null && relative != null) {
            return new RemoteSourceDescription(idx, relative);
        } else {
            return null;
        }
    }

    public static String decodeMethodParameterTypes(String signature) {
        StringBuilder name = new StringBuilder();

        methodParameterTypes(signature, new int[] {0}, name);
        return name.toString();
    }

    private static char getChar (final String buffer, final int pos) {
        if (pos>=buffer.length()) {
            throw new IllegalStateException ();
        }
        return buffer.charAt(pos);
    }

    private static String typeArgument (final String jvmTypeId, final int[] pos) {
        char c = getChar (jvmTypeId, pos[0]);
        switch (c) {
            case '*':
                pos[0]++;
                return ""; //XXX?
            case '+':
                pos[0]++;
                return "? extends " + typeSignatureType(jvmTypeId, pos);
            case '-':
                pos[0]++;
                return "? super " + typeSignatureType(jvmTypeId, pos);
            default:
                return typeSignatureType (jvmTypeId, pos);
        }
    }


    private static void typeArgumentsList (final String jvmTypeId, final int[] pos, StringBuilder result) {
        char c = getChar (jvmTypeId, pos[0]++);
        if (c != '<') {
            throw new IllegalStateException (jvmTypeId);
        }
        c = getChar (jvmTypeId, pos[0]);
        boolean first = true;
        while (c !='>') {
            if (!first) result.append(", ");
            first = false;
            result.append(typeArgument (jvmTypeId, pos));
            c = getChar (jvmTypeId, pos[0]);
        }
        pos[0]++;
    }

    static boolean generateSimpleNames = true;

    private static String typeSignatureType (final String jvmTypeId, final int[] pos) {
        char c = getChar(jvmTypeId, pos[0]++);
        switch (c) {
            case 'B': return "byte";
            case 'C': return "char";
            case 'D': return "double";
            case 'F': return "float";
            case 'I': return "int";
            case 'J': return "long";
            case 'S': return "short";
            case 'V': return "void";
            case 'Z': return "boolean";
            case 'L': {
                StringBuilder builder = new StringBuilder ();
                c = getChar(jvmTypeId, pos[0]++);
                while (c != ';') {
                    if (c == '/' || c == '$') {
                        if (generateSimpleNames) builder.delete(0, builder.length());
                        else builder.append('.');
                    } else {
                        builder.append(c);
                    }

                    if (c=='<') {
                        pos[0]--;
                        typeArgumentsList (jvmTypeId, pos, builder);
                        builder.append(">");
                    }
                    c = getChar(jvmTypeId, pos[0]++);
                }
                return builder.toString();
            }
            case 'T': {
                StringBuilder builder = new StringBuilder ();
                c = getChar(jvmTypeId, pos[0]++);
                while (c != ';') {
                    builder.append(c);
                    c = getChar(jvmTypeId, pos[0]++);
                }
                return builder.toString();
            }
            case '[':
                return typeSignatureType (jvmTypeId, pos) + "[]";
            default:
                return "<unknown-type>";
        }
    }

    private static void methodParameterTypes(final String jvmTypeId, final int[] pos, StringBuilder result) {
        char c = getChar (jvmTypeId, pos[0]);
        if (c == '<') {
            do {
                c = getChar (jvmTypeId, pos[0]++);
            } while (c != '>');
            c = getChar (jvmTypeId, pos[0]);
        }
        if (c!='(') {
            throw new IllegalStateException (jvmTypeId);
        }
        pos[0]++;
        c = getChar (jvmTypeId, pos[0]);
        result.append("(");
        boolean first = true;
        while (c != ')') {
            if (!first) result.append(", ");
            first = false;
            result.append(typeSignatureType (jvmTypeId, pos));
            c = getChar (jvmTypeId, pos[0]);
        }
        result.append(")");
    }

    public static ElementHandle<?> createElementHandle(ElementKind kind, String clazz, String simpleName, String signature) {
        LOG.setLevel(Level.ALL);
        try {
            Class<?> elementHandleAccessor = Class.forName("org.netbeans.modules.java.source.ElementHandleAccessor", false, ElementHandle.class.getClassLoader());
            Field instance = elementHandleAccessor.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            Method m = elementHandleAccessor.getDeclaredMethod("create", ElementKind.class, String[].class);
            String[] signatures;
            switch (kind) {
                case PACKAGE:
                case CLASS:
                case INTERFACE:
                case ENUM:
                case ANNOTATION_TYPE:
                case OTHER:
                    signatures = new String[] {clazz};
                    break;
                case METHOD:
                case CONSTRUCTOR:
                case FIELD:
                case ENUM_CONSTANT:
                    signatures = new String[] {clazz, simpleName, signature};
                    break;
                case INSTANCE_INIT:
                case STATIC_INIT:
                    signatures = new String[] {clazz, simpleName};
                    break;
                default:
                    kind = ElementKind.OTHER;
                    signatures = new String[] {clazz};
                    break;
            }
            return (ElementHandle<?>) m.invoke(instance.get(null), kind, signatures);
        } catch (IllegalAccessException ex) {
            LOG.log(Level.INFO, null, ex);
        } catch (IllegalArgumentException ex) {
            LOG.log(Level.INFO, null, ex);
        } catch (InvocationTargetException ex) {
            LOG.log(Level.INFO, null, ex);
        } catch (NoSuchMethodException ex) {
            LOG.log(Level.INFO, null, ex);
        } catch (NoSuchFieldException ex) {
            LOG.log(Level.INFO, null, ex);
        } catch (SecurityException ex) {
            LOG.log(Level.INFO, null, ex);
        } catch (ClassNotFoundException ex) {
            LOG.log(Level.INFO, null, ex);
        }

        return ElementHandle.createTypeElementHandle(ElementKind.CLASS, clazz);
    }
}
