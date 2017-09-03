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

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.jackpot30.backend.base.CategoryStorage;
import org.netbeans.modules.jackpot30.backend.base.SourceRoot;
import org.netbeans.modules.java.source.indexing.JavaIndex;
import org.openide.filesystems.FileObject;

/**
 *
 * @author lahvac
 */
public class ResolveService {

    static SourceRoot sourceRoot(CategoryStorage category, String relativePath) {
        for (SourceRoot sr : category.getSourceRoots()) {
            if (relativePath.startsWith(sr.getRelativePath())) {
                return sr;
            }
        }

        throw new IllegalStateException();
    }

    Javac javacFor(String segment, String relative) throws IOException, InterruptedException {
        CategoryStorage category = CategoryStorage.forId(segment);
        return Javac.get(sourceRoot(category, relative));
    }

    public static CompilationInfo parse(String segment, String relative) throws IOException, InterruptedException {
        CategoryStorage category = CategoryStorage.forId(segment);
        Javac javac = Javac.get(sourceRoot(category, relative));
        return javac.parse(relative);
    }

    public static String resolveSource(String segment, String relative, String signature) throws IOException, InterruptedException {
        String fqn = topLevelClassFromSignature(signature);
        SourceRoot sourceRoot = sourceRoot(CategoryStorage.forId(segment), relative);
        List<String> classpathElements = new ArrayList<String>();

        classpathElements.add(sourceRoot.getCode());

        String classpath = sourceRoot.getClassPathString();

        if (classpath != null) classpathElements.addAll(Arrays.asList(classpath.split(":")));

        for (String element : classpathElements) {
            if (element.endsWith(".jar")) continue;

            String file = fileForFQN(sourceRoot.getCategory(), element, fqn);

            if (file != null) {
                return file;
            }
        }

        return null;
    }

    public static Map<? extends CategoryStorage, ? extends Iterable<? extends String>> findSourcesContaining(String signature) throws IOException, InterruptedException {
        Map<CategoryStorage, Iterable<? extends String>> result = new HashMap<CategoryStorage, Iterable<? extends String>>();
        String fqn = topLevelClassFromSignature(signature);

        for (CategoryStorage category : CategoryStorage.listCategories()) {
            //would it be faster to check if the given class is in the current category?
            
            List<String> files = new ArrayList<String>();

            for (SourceRoot sourceRoot : category.getSourceRoots()) {
                String file = fileForFQN(category, sourceRoot.getCode(), fqn);

                if (file != null) {
                    files.add(file);
                }
            }

            if (!files.isEmpty()) {
                result.put(category, files);
            }
        }

        return result;
    }

    private static String fileForFQN(CategoryStorage category, String code, String fqn) throws IOException {
        FileObject root = category.getEmbeddedJarRoot(code);
        FileObject fqn2files = root != null ? root.getFileObject("java/" + JavaIndex.VERSION + "/fqn2files.properties") : null;

        if (fqn2files == null) return null;

        Properties props = new Properties();
        InputStream in = fqn2files.getInputStream();

        try {
            props.load(in);
        } finally {
            in.close();
        }

        String file = props.getProperty(fqn);

        if (file != null) {
            return file.substring("rel:/".length());
        }

        return null;
    }

    private static String topLevelClassFromSignature(String signature) {
        String fqn = signature.split(":")[1];

        if (fqn.indexOf('$') != (-1)) {//not fully correct
            return fqn.substring(0, fqn.indexOf('$'));
        } else {
            return fqn;
        }
    }

    public static long[] nameSpan(CompilationInfo info, TreePath forTree) {
        JCTree jcTree = (JCTree) forTree.getLeaf(); //XXX
        Name name = null;
        int pos = jcTree.pos;

        switch (forTree.getLeaf().getKind()) {
            case IDENTIFIER: name = ((IdentifierTree) forTree.getLeaf()).getName(); break;
            case MEMBER_SELECT: name = ((MemberSelectTree) forTree.getLeaf()).getIdentifier(); pos++; break;
            case ANNOTATION_TYPE: case CLASS:
            case ENUM: case INTERFACE:
                name = ((ClassTree) forTree.getLeaf()).getSimpleName();
                
                TokenSequence<JavaTokenId> ts = info.getTokenHierarchy().tokenSequence(JavaTokenId.language());

                ts.move(pos);

                while (ts.moveNext()) {
                    if (ts.token().id() == JavaTokenId.IDENTIFIER) {
                        if (name.contentEquals(ts.token().text())) {
                            pos = ts.offset();
                        }
                        break;
                    }
                }

                break;
            case METHOD:
                if ((((JCMethodDecl) forTree.getLeaf()).getModifiers().flags & Flags.GENERATEDCONSTR) != 0) {
                    //no positions for generated constructors:
                    return new long[] {-1, -1, -1, -1};
                }
                name = ((MethodTree) forTree.getLeaf()).getName();
                if (name.contentEquals("<init>")) {
                    name = ((ClassTree) forTree.getParentPath().getLeaf()).getSimpleName();
                }
                break;
            case VARIABLE: name = ((VariableTree) forTree.getLeaf()).getName(); break;
        }

        if (name != null) {
            return new long[] {
                info.getTrees().getSourcePositions().getStartPosition(forTree.getCompilationUnit(), forTree.getLeaf()),
                info.getTrees().getSourcePositions().getEndPosition(forTree.getCompilationUnit(), forTree.getLeaf()),
                pos,
                pos + name.length()
            };
        }

        return new long[] {
            info.getTrees().getSourcePositions().getStartPosition(forTree.getCompilationUnit(), forTree.getLeaf()),
            info.getTrees().getSourcePositions().getEndPosition(forTree.getCompilationUnit(), forTree.getLeaf()),
            info.getTrees().getSourcePositions().getStartPosition(forTree.getCompilationUnit(), forTree.getLeaf()),
            info.getTrees().getSourcePositions().getEndPosition(forTree.getCompilationUnit(), forTree.getLeaf())
        };
    }

    public static long[] declarationSpans(final CompilationInfo info, final String signature) {
        final long[][] result = new long[1][];

        new TreePathScanner<Void, Void>() {
            @Override public Void visitClass(ClassTree node, Void p) {
                handleDeclaration();
                return super.visitClass(node, p);
            }
            @Override public Void visitMethod(MethodTree node, Void p) {
                handleDeclaration();
                return super.visitMethod(node, p);
            }
            @Override public Void visitVariable(VariableTree node, Void p) {
                handleDeclaration();
                return super.visitVariable(node, p);
            }
            private void handleDeclaration() {
                Element el = info.getTrees().getElement(getCurrentPath());

                if (el == null/*how?*/ || !JavaUtils.SUPPORTED_KINDS.contains(el.getKind())) return ;
                
                String thisSignature = JavaUtils.serialize(ElementHandle.create(el));

                if (thisSignature.equals(signature)) {
                    result[0] = nameSpan(info, getCurrentPath());
                }
            }
        }.scan(info.getCompilationUnit(), null);

        return result[0];
    }

    public static List<long[]> usages(final CompilationInfo info, final String signature) {
        final List<long[]> result = new ArrayList<long[]>();

        new TreePathScanner<Void, Void>() {
            @Override public Void visitIdentifier(IdentifierTree node, Void p) {
                handle();
                return super.visitIdentifier(node, p);
            }
            @Override public Void visitMemberSelect(MemberSelectTree node, Void p) {
                handle();
                return super.visitMemberSelect(node, p);
            }
            private void handle() {
                Element el = info.getTrees().getElement(getCurrentPath());
                if (el == null || !JavaUtils.SUPPORTED_KINDS.contains(el.getKind())) return;
                String thisSignature = JavaUtils.serialize(ElementHandle.create(el));

                if (thisSignature.equals(signature)) {
                    result.add(ResolveService.nameSpan(info, getCurrentPath()));
                }
            }
        }.scan(info.getCompilationUnit(), null);

        return result;
    }

}
