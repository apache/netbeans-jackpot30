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
package org.netbeans.modules.jackpot30.backend.ui.highlighting;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import org.netbeans.api.lexer.Token;
import org.netbeans.modules.jackpot30.backend.ui.highlighting.ColoringAttributes.Coloring;
import org.netbeans.modules.jackpot30.resolve.api.CompilationInfo;


/**
 *
 * @author Jan Lahoda
 */
public class SemanticHighlighter {
    
    static Coloring collection2Coloring(Collection<ColoringAttributes> attr) {
        Coloring c = ColoringAttributes.empty();

        for (ColoringAttributes a : attr) {
            c = ColoringAttributes.add(c, a);
        }

        return c;
    }

    public static Map<Token, Coloring> computeHighlights(CompilationInfo info, TokenList tl) {
        DetectorVisitor v = new DetectorVisitor(info, tl);
        
        long start = System.currentTimeMillis();
        
        Map<Token, Coloring> newColoring = new IdentityHashMap<Token, Coloring>();

        CompilationUnitTree cu = info.getCompilationUnit();
        
        v.scan(cu, null);
        
        Map<Token, Coloring> oldColors = new HashMap<Token, Coloring>();
        Map<Token, Coloring> removedTokens = new IdentityHashMap<Token, Coloring>(oldColors);
        Set<Token> addedTokens = new HashSet<Token>();
        
        for (Element decl : v.type2Uses.keySet()) {
            List<Use> uses = v.type2Uses.get(decl);
            
            for (Use u : uses) {
                if (u.spec == null)
                    continue;
                
                if (u.type.contains(UseTypes.DECLARATION) && isPrivateElement(decl)) {
                    if ((decl.getKind().isField() && !isSerialVersionUID(info, decl)) || isLocalVariableClosure(decl)) {
                        if (!hasAllTypes(uses, EnumSet.of(UseTypes.READ, UseTypes.WRITE))) {
                            u.spec.add(ColoringAttributes.UNUSED);
                        }
                    }
                    
                    if ((decl.getKind() == ElementKind.CONSTRUCTOR && !decl.getModifiers().contains(Modifier.PRIVATE)) || decl.getKind() == ElementKind.METHOD) {
                        if (!hasAllTypes(uses, EnumSet.of(UseTypes.EXECUTE))) {
                            u.spec.add(ColoringAttributes.UNUSED);
                        }
                    }
                    
                    if (decl.getKind().isClass() || decl.getKind().isInterface()) {
                        if (!hasAllTypes(uses, EnumSet.of(UseTypes.CLASS_USE))) {
                            u.spec.add(ColoringAttributes.UNUSED);
                        }
                    }
                }
                
                Coloring c = collection2Coloring(u.spec);
                
                Token t = v.tree2Token.get(u.tree.getLeaf());
                
                if (t != null) {
                    newColoring.put(t, c);

                    Coloring oldColoring = removedTokens.remove(t);

                    if (oldColoring == null || !oldColoring.equals(c)) {
                        addedTokens.add(t);
                    }
                }
            }
        }
        
        return newColoring;
    }
    
        
    private static boolean hasAllTypes(List<Use> uses, Collection<UseTypes> types) {
        EnumSet e = EnumSet.copyOf(types);
        
        for (Use u : uses) {
            if (types.isEmpty()) {
                return true;
            }
            
            types.removeAll(u.type);
        }
        
        return types.isEmpty();
    }
    
    private enum UseTypes {
        READ, WRITE, EXECUTE, DECLARATION, CLASS_USE;
    }
    
    private static boolean isLocalVariableClosure(Element el) {
        return el.getKind() == ElementKind.PARAMETER || el.getKind() == ElementKind.LOCAL_VARIABLE
                || el.getKind() == ElementKind.RESOURCE_VARIABLE || el.getKind() == ElementKind.EXCEPTION_PARAMETER;
    }
    
    /** Detects static final long SerialVersionUID 
     * @return true if element is final static long serialVersionUID
     */
    private static boolean isSerialVersionUID(CompilationInfo info, Element el) {
        if (el.getKind().isField() && el.getModifiers().contains(Modifier.FINAL) 
                && el.getModifiers().contains(Modifier.STATIC)
                && info.getTypes().getPrimitiveType(TypeKind.LONG).equals(el.asType())
                && el.getSimpleName().toString().equals("serialVersionUID"))
            return true;
        else
            return false;
    }
        
    private static class Use {
        private Collection<UseTypes> type;
        private TreePath     tree;
        private Collection<ColoringAttributes> spec;
        
        public Use(Collection<UseTypes> type, TreePath tree, Collection<ColoringAttributes> spec) {
            this.type = type;
            this.tree = tree;
            this.spec = spec;
        }
        
        @Override
        public String toString() {
            return "Use: " + type;
        }
    }
    
    private static class DetectorVisitor extends TreePathScanner<Void, EnumSet<UseTypes>> {
        
        private CompilationInfo info;
        private Map<Element, List<Use>> type2Uses;
        
        private Map<Tree, Token> tree2Token;
        private TokenList tl;
        private long memberSelectBypass = -1;
        
        private SourcePositions sourcePositions;
        private ExecutableElement recursionDetector;
        
        private DetectorVisitor(CompilationInfo info, TokenList tl) {
            this.info = info;
            type2Uses = new HashMap<Element, List<Use>>();
            
            tree2Token = new IdentityHashMap<Tree, Token>();
            
            this.tl = tl;
            
            this.sourcePositions = info.getTrees().getSourcePositions();
//            this.pos = pos;
        }
        
        private void firstIdentifier(String name) {
            tl.firstIdentifier(getCurrentPath(), name, tree2Token);
        }
        
        @Override
        public Void visitAssignment(AssignmentTree tree, EnumSet<UseTypes> d) {
            handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getVariable()), EnumSet.of(UseTypes.WRITE));
            
            Tree expr = tree.getExpression();
            
            if (expr instanceof IdentifierTree) {
                TreePath tp = new TreePath(getCurrentPath(), expr);
                handlePossibleIdentifier(tp, EnumSet.of(UseTypes.READ));
            }
            
            scan(tree.getVariable(), EnumSet.of(UseTypes.WRITE));
            scan(tree.getExpression(), EnumSet.of(UseTypes.READ));
            
            return null;
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree tree, EnumSet<UseTypes> d) {
            Set<UseTypes> useTypes = EnumSet.of(UseTypes.WRITE);
            
            if (d != null) {
                useTypes.addAll(d);
            }
            
            handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getVariable()), useTypes);
            
            Tree expr = tree.getExpression();
            
            if (expr instanceof IdentifierTree) {
                TreePath tp = new TreePath(getCurrentPath(), expr);
                handlePossibleIdentifier(tp, EnumSet.of(UseTypes.READ));
            }
            
            scan(tree.getVariable(), EnumSet.of(UseTypes.WRITE));
            scan(tree.getExpression(), EnumSet.of(UseTypes.READ));
            
            return null;
        }

        @Override
        public Void visitReturn(ReturnTree tree, EnumSet<UseTypes> d) {
            if (tree.getExpression() instanceof IdentifierTree) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getExpression()), EnumSet.of(UseTypes.READ));
            }
            
            super.visitReturn(tree, EnumSet.of(UseTypes.READ));
            return null;
        }
        
        @Override
        public Void visitMemberSelect(MemberSelectTree tree, EnumSet<UseTypes> d) {
            long memberSelectBypassLoc = memberSelectBypass;
            
            memberSelectBypass = -1;
            
            Tree expr = tree.getExpression();
            
            if (expr instanceof IdentifierTree) {
                TreePath tp = new TreePath(getCurrentPath(), expr);
                handlePossibleIdentifier(tp, EnumSet.of(UseTypes.READ));
            }
            
            Element el = info.getTrees().getElement(getCurrentPath());
            
            if (el != null && el.getKind().isField()) {
                handlePossibleIdentifier(getCurrentPath(), d == null ? EnumSet.of(UseTypes.READ) : d);
            }
	    
	    if (el != null && (el.getKind().isClass() || el.getKind().isInterface()) && 
		    getCurrentPath().getParentPath().getLeaf().getKind() != Kind.NEW_CLASS) {
		handlePossibleIdentifier(getCurrentPath(), EnumSet.of(UseTypes.CLASS_USE));
	    }
	    
//            System.err.println("XXXX=" + tree.toString());
//            System.err.println("YYYY=" + info.getElement(tree));
            
            super.visitMemberSelect(tree, null);
            
            tl.moveToEnd(tree.getExpression());
            
            if (memberSelectBypassLoc != (-1)) {
                tl.moveToOffset(memberSelectBypassLoc);
            }
            
            firstIdentifier(tree.getIdentifier().toString());
            
            return null;
        }
        
        private void addModifiers(Element decl, Collection<ColoringAttributes> c) {
            if (decl.getModifiers().contains(Modifier.STATIC)) {
                c.add(ColoringAttributes.STATIC);
            }
            
            if (decl.getModifiers().contains(Modifier.ABSTRACT) && !decl.getKind().isInterface()) {
                c.add(ColoringAttributes.ABSTRACT);
            }
            
            boolean accessModifier = false;
            
            if (decl.getModifiers().contains(Modifier.PUBLIC)) {
                c.add(ColoringAttributes.PUBLIC);
                accessModifier = true;
            }
            
            if (decl.getModifiers().contains(Modifier.PROTECTED)) {
                c.add(ColoringAttributes.PROTECTED);
                accessModifier = true;
            }
            
            if (decl.getModifiers().contains(Modifier.PRIVATE)) {
                c.add(ColoringAttributes.PRIVATE);
                accessModifier = true;
            }
            
            if (!accessModifier && !isLocalVariableClosure(decl)) {
                c.add(ColoringAttributes.PACKAGE_PRIVATE);
            }
            
            if (info.getElements().isDeprecated(decl)) {
                c.add(ColoringAttributes.DEPRECATED);
            }
        }
        
        private Collection<ColoringAttributes> getMethodColoring(ExecutableElement mdecl, boolean nct) {
            Collection<ColoringAttributes> c = new ArrayList<ColoringAttributes>();
            
            addModifiers(mdecl, c);
            
            if (mdecl.getKind() == ElementKind.CONSTRUCTOR) {
                c.add(ColoringAttributes.CONSTRUCTOR);

                //#146820:
                if (nct && mdecl.getEnclosingElement() != null && info.getElements().isDeprecated(mdecl.getEnclosingElement())) {
                    c.add(ColoringAttributes.DEPRECATED);
                }
            } else
                c.add(ColoringAttributes.METHOD);
            
            return c;
        }
        
        private Collection<ColoringAttributes> getVariableColoring(Element decl) {
            Collection<ColoringAttributes> c = new ArrayList<ColoringAttributes>();
            
            addModifiers(decl, c);
            
            if (decl.getKind().isField()) {
                c.add(ColoringAttributes.FIELD);
                
                return c;
            }
            
            if (decl.getKind() == ElementKind.LOCAL_VARIABLE || decl.getKind() == ElementKind.RESOURCE_VARIABLE
                    || decl.getKind() == ElementKind.EXCEPTION_PARAMETER) {
                c.add(ColoringAttributes.LOCAL_VARIABLE);
                
                return c;
            }
            
            if (decl.getKind() == ElementKind.PARAMETER) {
                c.add(ColoringAttributes.PARAMETER);
                
                return c;
            }
            
            assert false;
            
            return null;
        }

        private static final Set<Kind> LITERALS = EnumSet.of(Kind.BOOLEAN_LITERAL, Kind.CHAR_LITERAL, Kind.DOUBLE_LITERAL, Kind.FLOAT_LITERAL, Kind.INT_LITERAL, Kind.LONG_LITERAL, Kind.STRING_LITERAL);

        private void handlePossibleIdentifier(TreePath expr, Collection<UseTypes> type) {
            handlePossibleIdentifier(expr, type, null, false, false);
        }
        
        private void handlePossibleIdentifier(TreePath expr, Collection<UseTypes> type, Element decl, boolean providesDecl, boolean nct) {
            
            if (isKeyword(expr.getLeaf())) {
                //ignore keywords:
                return ;
            }

            if (expr.getLeaf().getKind() == Kind.PRIMITIVE_TYPE) {
                //ignore primitive types:
                return ;
            }

            if (LITERALS.contains(expr.getLeaf().getKind())) {
                //ignore literals:
                return ;
            }

            decl = !providesDecl ? info.getTrees().getElement(expr) : decl;
            
            Collection<ColoringAttributes> c = null;
            
            //causes NPE later, as decl is put into list of declarations to handle:
//            if (decl == null) {
//                c = Collections.singletonList(ColoringAttributes.UNDEFINED);
//            }
            
            if (decl != null && (decl.getKind().isField() || isLocalVariableClosure(decl))) {
                c = getVariableColoring(decl);
            }
            
            if (decl != null && decl instanceof ExecutableElement) {
                c = getMethodColoring((ExecutableElement) decl, nct);
            }
            
            if (decl != null && (decl.getKind().isClass() || decl.getKind().isInterface())) {
                //class use make look like read variable access:
                if (type.contains(UseTypes.READ)) {
                    type = EnumSet.copyOf(type);
                    type.remove(UseTypes.READ);
                    type.add(UseTypes.CLASS_USE);
                }
                
                c = new ArrayList<ColoringAttributes>();
                
                addModifiers(decl, c);
                
                switch (decl.getKind()) {
                    case CLASS: c.add(ColoringAttributes.CLASS); break;
                    case INTERFACE: c.add(ColoringAttributes.INTERFACE); break;
                    case ANNOTATION_TYPE: c.add(ColoringAttributes.ANNOTATION_TYPE); break;
                    case ENUM: c.add(ColoringAttributes.ENUM); break;
                }
            }
            
            if (decl != null && type.contains(UseTypes.DECLARATION)) {
                if (c == null) {
                    c = new ArrayList<ColoringAttributes>();
                }
                
                c.add(ColoringAttributes.DECLARATION);
            }
            
            if (c != null) {
                addUse(decl, type, expr, c);
            }
        }
        
        private void addUse(Element decl, Collection<UseTypes> useTypes, TreePath t, Collection<ColoringAttributes> c) {
            if (decl == recursionDetector) {
                useTypes.remove(UseTypes.EXECUTE); //recursive execution is not use
            }
            
            List<Use> uses = type2Uses.get(decl);
            
            if (uses == null) {
                type2Uses.put(decl, uses = new ArrayList<Use>());
            }
            
            Use u = new Use(useTypes, t, c);
            
            uses.add(u);
        }

        @Override
        public Void visitTypeCast(TypeCastTree tree, EnumSet<UseTypes> d) {
            Tree expr = tree.getExpression();
            
            if (expr.getKind() == Kind.IDENTIFIER) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), expr), EnumSet.of(UseTypes.READ));
            }
            
            Tree cast = tree.getType();
            
            if (cast.getKind() == Kind.IDENTIFIER) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), cast), EnumSet.of(UseTypes.READ));
            }
            
            super.visitTypeCast(tree, d);
            return null;
        }

        @Override
        public Void visitInstanceOf(InstanceOfTree tree, EnumSet<UseTypes> d) {
            Tree expr = tree.getExpression();
            
            if (expr instanceof IdentifierTree) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), expr), EnumSet.of(UseTypes.READ));
            }
            
            TreePath tp = new TreePath(getCurrentPath(), tree.getType());
            handlePossibleIdentifier(tp, EnumSet.of(UseTypes.CLASS_USE));
            
            super.visitInstanceOf(tree, null);
            
            //TODO: should be considered
            return null;
        }

        @Override
        public Void visitCompilationUnit(CompilationUnitTree tree, EnumSet<UseTypes> d) {
	    //ignore package X.Y.Z;:
	    //scan(tree.getPackageDecl(), p);
            tl.moveBefore(tree.getImports());
	    scan(tree.getImports(), d);
            tl.moveBefore(tree.getPackageAnnotations());
	    scan(tree.getPackageAnnotations(), d);
            tl.moveToEnd(tree.getImports());
	    scan(tree.getTypeDecls(), d);
	    return null;
        }

        private long startOf(List<? extends Tree> trees) {
            if (trees.isEmpty()) return -1;
            return sourcePositions.getStartPosition(info.getCompilationUnit(), trees.get(0));
        }

        private void handleMethodTypeArguments(TreePath method, List<? extends Tree> tArgs) {
            //the type arguments are before the last identifier in the select, so we should return there:
            //not very efficient, though:
            tl.moveBefore(tArgs);
            
            for (Tree expr : tArgs) {
                if (expr instanceof IdentifierTree) {
                    handlePossibleIdentifier(new TreePath(method, expr), EnumSet.of(UseTypes.CLASS_USE));
                }
            }
        }
        
        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree, EnumSet<UseTypes> d) {
            Tree possibleIdent = tree.getMethodSelect();
            boolean handled = false;
            
            if (possibleIdent.getKind() == Kind.IDENTIFIER) {
                //handle "this" and "super" constructors:
                String ident = ((IdentifierTree) possibleIdent).getName().toString();
                
                if ("super".equals(ident) || "this".equals(ident)) { //NOI18N
                    Element resolved = info.getTrees().getElement(getCurrentPath());
                    
                    addUse(resolved, EnumSet.of(UseTypes.EXECUTE), null, null);
                    handled = true;
                }
            }
            
            if (!handled) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), possibleIdent), EnumSet.of(UseTypes.EXECUTE));
            }
            
            List<? extends Tree> ta = tree.getTypeArguments();
            long afterTypeArguments = ta.isEmpty() ? -1 : info.getTrees().getSourcePositions().getEndPosition(info.getCompilationUnit(), ta.get(ta.size() - 1));
            
            switch (tree.getMethodSelect().getKind()) {
                case IDENTIFIER:
                case MEMBER_SELECT:
                    memberSelectBypass = afterTypeArguments;
                    scan(tree.getMethodSelect(), null);
                    memberSelectBypass = -1;
                    break;
                default:
                    //todo: log
                    scan(tree.getMethodSelect(), null);
            }

            handleMethodTypeArguments(getCurrentPath(), ta);
            
            scan(tree.getTypeArguments(), null);
            
//            if (tree.getMethodSelect().getKind() == Kind.MEMBER_SELECT && tree2Token.get(tree.getMethodSelect()) == null) {
////                if (ts.moveNext()) ???
//                    firstIdentifier(((MemberSelectTree) tree.getMethodSelect()).getIdentifier().toString());
//            }
            
            for (Tree expr : tree.getArguments()) {
                if (expr instanceof IdentifierTree) {
                    handlePossibleIdentifier(new TreePath(getCurrentPath(), expr), EnumSet.of(UseTypes.READ));
                }
            }
            
            scan(tree.getArguments(), EnumSet.of(UseTypes.READ));
            
//            super.visitMethodInvocation(tree, null);
            return null;
        }

        @Override
        public Void visitIdentifier(IdentifierTree tree, EnumSet<UseTypes> d) {
            if (isSynthetic(getCurrentPath()))
                return null;
//            if ("l".equals(tree.toString())) {
//                Thread.dumpStack();
//            }
//            handlePossibleIdentifier(tree);
//            //also possible type: (like in Collections.EMPTY_LIST):
//            resolveType(tree);
//            Thread.dumpStack();
            
            tl.moveToOffset(sourcePositions.getStartPosition(info.getCompilationUnit(), tree));
            
            if (memberSelectBypass != (-1)) {
                tl.moveToOffset(memberSelectBypass);
                memberSelectBypass = -1;
            }
            
            tl.identifierHere(tree, tree2Token);
            
            if (d != null) {
                handlePossibleIdentifier(getCurrentPath(), d);
            }
            super.visitIdentifier(tree, null);
            return null;
        }
//
        @Override
        public Void visitMethod(MethodTree tree, EnumSet<UseTypes> d) {
            if (isSynthetic(getCurrentPath())) {
                return super.visitMethod(tree, d);
            }
//            Element decl = pi.getAttribution().getElement(tree);
//            
//            if (decl != null) {
//                assert decl instanceof ExecutableElement;
//                
//                Coloring c = getMethodColoring((ExecutableElement) decl);
//                HighlightImpl h = createHighlight(decl.getSimpleName(), tree, c, null);
//                
//                if (h != null) {
//                    highlights.add(h);
//                }
//            }

            //#170338: constructor without modifiers:
            tl.moveToOffset(sourcePositions.getStartPosition(info.getCompilationUnit(), tree));

            handlePossibleIdentifier(getCurrentPath(), EnumSet.of(UseTypes.DECLARATION));
            
            for (Tree t : tree.getThrows()) {
                TreePath tp = new TreePath(getCurrentPath(), t);
                handlePossibleIdentifier(tp, EnumSet.of(UseTypes.CLASS_USE));
            }
            
            EnumSet<UseTypes> paramsUseTypes;
            
            Element el = info.getTrees().getElement(getCurrentPath());
            
            if (el != null && (el.getModifiers().contains(Modifier.ABSTRACT) || el.getModifiers().contains(Modifier.NATIVE) || !el.getModifiers().contains(Modifier.PRIVATE))) {
                paramsUseTypes = EnumSet.of(UseTypes.WRITE, UseTypes.READ);
            } else {
                paramsUseTypes = EnumSet.of(UseTypes.WRITE);
            }
        
            scan(tree.getModifiers(), null);
            tl.moveToEnd(tree.getModifiers());
            scan(tree.getTypeParameters(), null);
            tl.moveToEnd(tree.getTypeParameters());
            scan(tree.getReturnType(), EnumSet.of(UseTypes.CLASS_USE));
            tl.moveToEnd(tree.getReturnType());
            
            String name;
            
            if (tree.getReturnType() != null) {
                //method:
                name = tree.getName().toString();
            } else {
                //constructor:
                TreePath tp = getCurrentPath();
                
                while (tp != null && !CLASS_TREE_KINDS.contains(tp.getLeaf().getKind())) {
                    tp = tp.getParentPath();
                }
                
                if (tp != null && CLASS_TREE_KINDS.contains(tp.getLeaf().getKind())) {
                    name = ((ClassTree) tp.getLeaf()).getSimpleName().toString();
                } else {
                    name = null;
                }
            }
            
            if (name != null) {
                firstIdentifier(name);
            }
            
            scan(tree.getParameters(), paramsUseTypes);
            scan(tree.getThrows(), null);
            scan(tree.getDefaultValue(), null);

            recursionDetector = (el != null && el.getKind() == ElementKind.METHOD) ? (ExecutableElement) el : null;
            
            scan(tree.getBody(), null);

            recursionDetector = null;
        
            return null;
        }

        @Override
        public Void visitExpressionStatement(ExpressionStatementTree tree, EnumSet<UseTypes> d) {
//            if (tree instanceof IdentifierTree) {
//                handlePossibleIdentifier(tree, EnumSet.of(UseTypes.READ));
//            }
            
            super.visitExpressionStatement(tree, null);
            return null;
        }

        @Override
        public Void visitParenthesized(ParenthesizedTree tree, EnumSet<UseTypes> d) {
            ExpressionTree expr = tree.getExpression();
            
            if (expr instanceof IdentifierTree) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), expr), EnumSet.of(UseTypes.READ));
            }
            
            super.visitParenthesized(tree, d);
            return null;
        }

        @Override
        public Void visitEnhancedForLoop(EnhancedForLoopTree tree, EnumSet<UseTypes> d) {
            scan(tree.getVariable(), EnumSet.of(UseTypes.WRITE));
            
            if (tree.getExpression().getKind() == Kind.IDENTIFIER)
                handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getExpression()), EnumSet.of(UseTypes.READ));
            
            scan(tree.getExpression(), null);
            scan(tree.getStatement(), null);
            
            return null;
        }
        
        private boolean isStar(ImportTree tree) {
            Tree qualIdent = tree.getQualifiedIdentifier();
            
            if (qualIdent == null || qualIdent.getKind() == Kind.IDENTIFIER) {
                return false;
            }
            
            return ((MemberSelectTree) qualIdent).getIdentifier().contentEquals("*");
        }

        @Override
        public Void visitVariable(VariableTree tree, EnumSet<UseTypes> d) {
            tl.moveToOffset(sourcePositions.getStartPosition(info.getCompilationUnit(), tree));

            if (tree.getType() != null) {
                TreePath type = new TreePath(getCurrentPath(), tree.getType());

                if (type.getLeaf() instanceof ArrayTypeTree) {
                    type = new TreePath(type, ((ArrayTypeTree) type.getLeaf()).getType());
                }

                if (type.getLeaf().getKind() == Kind.IDENTIFIER)
                    handlePossibleIdentifier(type, EnumSet.of(UseTypes.CLASS_USE));
            }
            
            Collection<UseTypes> uses = null;
            
            Element e = info.getTrees().getElement(getCurrentPath());
            if (tree.getInitializer() != null) {
                uses = EnumSet.of(UseTypes.DECLARATION, UseTypes.WRITE);
                if (tree.getInitializer().getKind() == Kind.IDENTIFIER)
                    handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getInitializer()), EnumSet.of(UseTypes.READ));
            } else {
                if (e != null && e.getKind() == ElementKind.FIELD) {
                    uses = EnumSet.of(UseTypes.DECLARATION, UseTypes.WRITE);
                } else {
                    uses = EnumSet.of(UseTypes.DECLARATION);
                }
            }
            
            if (d != null) {
                Set<UseTypes> ut = new HashSet<UseTypes>();
                
                ut.addAll(uses);
                ut.addAll(d);
                
                uses = EnumSet.copyOf(ut);
            }
            
            handlePossibleIdentifier(getCurrentPath(), uses);
            
            scan(tree.getModifiers(), null);
            
            tl.moveToEnd(tree.getModifiers());
            
            scan(tree.getType(), null);
            
            int[] span = new int[] {((JCVariableDecl) tree).pos, ((JCVariableDecl) tree).pos + tree.getName().length()};//info.getTreeUtilities().findNameSpan(tree);
            if (span != null)
                tl.moveToOffset(span[0]);
            else
                tl.moveToEnd(tree.getType());
            
//            System.err.println("tree.getName().toString()=" + tree.getName().toString());
            
            firstIdentifier(tree.getName().toString());
            
            tl.moveNext();
            
            scan(tree.getInitializer(), EnumSet.of(UseTypes.READ));
            
            return null;
        }
        
        @Override
        public Void visitAnnotation(AnnotationTree tree, EnumSet<UseTypes> d) {
//            System.err.println("tree.getType()= " + tree.toString());
//            System.err.println("tree.getType()= " + tree.getClass());
//        
            TreePath tp = new TreePath(getCurrentPath(), tree.getAnnotationType());
            handlePossibleIdentifier(tp, EnumSet.of(UseTypes.CLASS_USE));
            super.visitAnnotation(tree, EnumSet.noneOf(UseTypes.class));
            //TODO: maybe should be considered
            return null;
        }

        @Override
        public Void visitNewClass(NewClassTree tree, EnumSet<UseTypes> d) {
//            if (info.getTreeUtilities().isSynthetic(getCurrentPath()))
//                return null;
//            
            Tree exp = tree.getEnclosingExpression();
            if (exp instanceof IdentifierTree) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), exp), EnumSet.of(UseTypes.READ));
            }
            
            TreePath tp;
            Tree ident = tree.getIdentifier();
            
            if (ident.getKind() == Kind.PARAMETERIZED_TYPE) {
                tp = new TreePath(new TreePath(getCurrentPath(), ident), ((ParameterizedTypeTree) ident).getType());
            } else {
                tp = new TreePath(getCurrentPath(), ident);
            }
            
            handlePossibleIdentifier(tp, EnumSet.of(UseTypes.EXECUTE), info.getTrees().getElement(getCurrentPath()), true, true);
            
            Element clazz = info.getTrees().getElement(tp);
            
            if (clazz != null) {
                addUse(clazz, EnumSet.of(UseTypes.CLASS_USE), null, null);
            }
	    
            for (Tree expr : tree.getArguments()) {
                if (expr instanceof IdentifierTree) {
                    handlePossibleIdentifier(new TreePath(getCurrentPath(), expr), EnumSet.of(UseTypes.READ));
                }
            }
            
            scan(tree.getEnclosingExpression(), null);
            scan(tree.getIdentifier(), null);
            scan(tree.getTypeArguments(), null);
            scan(tree.getArguments(), EnumSet.of(UseTypes.READ));
            scan(tree.getClassBody(), null);
            
            return null;
        }

        @Override
        public Void visitParameterizedType(ParameterizedTypeTree tree, EnumSet<UseTypes> d) {
            boolean alreadyHandled = false;
            
            if (getCurrentPath().getParentPath().getLeaf().getKind() == Kind.NEW_CLASS) {
                NewClassTree nct = (NewClassTree) getCurrentPath().getParentPath().getLeaf();
                
                alreadyHandled = nct.getTypeArguments().contains(tree) || nct.getIdentifier() == tree;
            }
            
            if (getCurrentPath().getParentPath().getParentPath().getLeaf().getKind() == Kind.NEW_CLASS) {
                NewClassTree nct = (NewClassTree) getCurrentPath().getParentPath().getParentPath().getLeaf();
                Tree leafToTest = getCurrentPath().getParentPath().getLeaf();

                alreadyHandled = nct.getTypeArguments().contains(leafToTest) || nct.getIdentifier() == leafToTest;
            }
            
            if (!alreadyHandled) {
                //NewClass has already been handled as part of visitNewClass:
                TreePath tp = new TreePath(getCurrentPath(), tree.getType());
                handlePossibleIdentifier(tp, EnumSet.of(UseTypes.CLASS_USE));
            }
            
            for (Tree t : tree.getTypeArguments()) {
                TreePath tp = new TreePath(getCurrentPath(), t);
                handlePossibleIdentifier(tp, EnumSet.of(UseTypes.CLASS_USE));
                
//                HighlightImpl h = createHighlight("", t, TYPE_PARAMETER);
//                
//                if (h != null)
//                    highlights.add(h);
            }
            
            super.visitParameterizedType(tree, null);
            return null;
        }

        @Override
        public Void visitBinary(BinaryTree tree, EnumSet<UseTypes> d) {
            Tree left = tree.getLeftOperand();
            Tree right = tree.getRightOperand();
            
            if (left instanceof IdentifierTree) {
                TreePath tp = new TreePath(getCurrentPath(), left);
                handlePossibleIdentifier(tp, EnumSet.of(UseTypes.READ));
            }
            
            if (right instanceof IdentifierTree) {
                TreePath tp = new TreePath(getCurrentPath(), right);
                handlePossibleIdentifier(tp, EnumSet.of(UseTypes.READ));
            }
            
            super.visitBinary(tree, EnumSet.of(UseTypes.READ));
            return null;
        }

        @Override
        public Void visitClass(ClassTree tree, EnumSet<UseTypes> d) {
            tl.moveToOffset(sourcePositions.getStartPosition(info.getCompilationUnit(), tree));
            for (TypeParameterTree t : tree.getTypeParameters()) {
                for (Tree bound : t.getBounds()) {
                    TreePath tp = new TreePath(new TreePath(getCurrentPath(), t), bound);
                    handlePossibleIdentifier(tp, EnumSet.of(UseTypes.CLASS_USE));
                }
            }
            
            if(getCurrentPath().getParentPath().getLeaf().getKind() != Kind.NEW_CLASS) {
                //NEW_CLASS already handeled by visitnewClass
                Tree extnds = tree.getExtendsClause();

                if (extnds != null) {
                    TreePath tp = new TreePath(getCurrentPath(), extnds);
                    handlePossibleIdentifier(tp, EnumSet.of(UseTypes.CLASS_USE));
                }

                for (Tree t : tree.getImplementsClause()) {
                    TreePath tp = new TreePath(getCurrentPath(), t);
                    handlePossibleIdentifier(tp, EnumSet.of(UseTypes.CLASS_USE));
                }
            }
            
            handlePossibleIdentifier(getCurrentPath(), EnumSet.of(UseTypes.DECLARATION));
            
            scan(tree.getModifiers(), null);
            
//            System.err.println("tree.getModifiers()=" + tree.getModifiers());
//            System.err.println("mod end=" + sourcePositions.getEndPosition(info.getCompilationUnit(), tree.getModifiers()));
//            System.err.println("class start=" + sourcePositions.getStartPosition(info.getCompilationUnit(), tree));
            tl.moveToEnd(tree.getModifiers());
            firstIdentifier(tree.getSimpleName().toString());
            
            //XXX:????
            scan(tree.getTypeParameters(), null);
            scan(tree.getExtendsClause(), null);
            scan(tree.getImplementsClause(), null);

            ExecutableElement prevRecursionDetector = recursionDetector;

            recursionDetector = null;
            
            scan(tree.getMembers(), null);

            recursionDetector = prevRecursionDetector;
            
            //XXX: end ???
            
            return null;
        }
        
        @Override
        public Void visitUnary(UnaryTree tree, EnumSet<UseTypes> d) {
            if (tree.getExpression() instanceof IdentifierTree) {
                switch (tree.getKind()) {
                    case PREFIX_INCREMENT:
                    case PREFIX_DECREMENT:
                    case POSTFIX_INCREMENT:
                    case POSTFIX_DECREMENT:
                        Set<UseTypes> useTypes = EnumSet.of(UseTypes.WRITE);
                        if (d != null) {
                            useTypes.addAll(d);
                        }
                        handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getExpression()), useTypes);
                        break;
                    default:
                        handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getExpression()), EnumSet.of(UseTypes.READ));
                }
            }
            super.visitUnary(tree, d);
            return null;
        }

        @Override
        public Void visitArrayAccess(ArrayAccessTree tree, EnumSet<UseTypes> d) {
            scan(tree.getExpression(), EnumSet.of(UseTypes.READ));
            scan(tree.getIndex(), EnumSet.of(UseTypes.READ));
            
            return null;
        }

        @Override
        public Void visitArrayType(ArrayTypeTree node, EnumSet<UseTypes> p) {
            if (node.getType() != null) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), node.getType()), EnumSet.of(UseTypes.CLASS_USE));
            }
            return super.visitArrayType(node, p);
        }

        @Override
        public Void visitUnionType(UnionTypeTree node, EnumSet<UseTypes> p) {
            for (Tree tree : node.getTypeAlternatives()) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), tree), EnumSet.of(UseTypes.CLASS_USE));
            }
            return super.visitUnionType(node, p);
        }

        @Override
        public Void visitNewArray(NewArrayTree tree, EnumSet<UseTypes> d) {
            if (tree.getType() != null) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getType()), EnumSet.of(UseTypes.CLASS_USE));
            }
            
            scan(tree.getType(), null);
            scan(tree.getDimensions(), EnumSet.of(UseTypes.READ));
            scan(tree.getInitializers(), EnumSet.of(UseTypes.READ));
            
            return null;
        }
        
        @Override
        public Void visitCatch(CatchTree tree, EnumSet<UseTypes> d) {
            scan(tree.getParameter(), EnumSet.of(UseTypes.WRITE));
            scan(tree.getBlock(), null);
            return null;
        }

        @Override
        public Void visitConditionalExpression(ConditionalExpressionTree node, EnumSet<UseTypes> p) {
            return super.visitConditionalExpression(node, EnumSet.of(UseTypes.READ));
        }
        
        @Override
        public Void visitAssert(AssertTree tree, EnumSet<UseTypes> p) {
            if (tree.getCondition().getKind() == Kind.IDENTIFIER)
                handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getCondition()), EnumSet.of(UseTypes.READ));
            if (tree.getDetail() != null && tree.getDetail().getKind() == Kind.IDENTIFIER)
                handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getDetail()), EnumSet.of(UseTypes.READ));
            
            return super.visitAssert(tree, EnumSet.of(UseTypes.READ));
        }
        
        @Override
        public Void visitCase(CaseTree tree, EnumSet<UseTypes> p) {
            if (tree.getExpression() != null && tree.getExpression().getKind() == Kind.IDENTIFIER) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getExpression()), EnumSet.of(UseTypes.READ));
            }
            
            return super.visitCase(tree, null);
        }
        
        @Override
        public Void visitThrow(ThrowTree tree, EnumSet<UseTypes> p) {
            if (tree.getExpression() != null && tree.getExpression().getKind() == Kind.IDENTIFIER) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), tree.getExpression()), EnumSet.of(UseTypes.READ));
            }
            
            return super.visitThrow(tree, p);
        }

        @Override
        public Void visitTypeParameter(TypeParameterTree tree, EnumSet<UseTypes> p) {
            for (Tree bound : tree.getBounds()) {
                if (bound.getKind() == Kind.IDENTIFIER) {
                    TreePath tp = new TreePath(getCurrentPath(), bound);
                    
                    handlePossibleIdentifier(tp, EnumSet.of(UseTypes.CLASS_USE));
                }
            }
            return super.visitTypeParameter(tree, p);
        }

        @Override
        public Void visitForLoop(ForLoopTree node, EnumSet<UseTypes> p) {
            if (node.getCondition() != null && node.getCondition().getKind() == Kind.IDENTIFIER) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), node.getCondition()), EnumSet.of(UseTypes.READ));
            }
            
            return super.visitForLoop(node, p);
        }

        @Override
        public Void visitWildcard(WildcardTree node, EnumSet<UseTypes> p) {
            if (node.getBound() != null && node.getBound().getKind() == Kind.IDENTIFIER) {
                handlePossibleIdentifier(new TreePath(getCurrentPath(), node.getBound()), EnumSet.of(UseTypes.CLASS_USE));
            }
            return super.visitWildcard(node, p);
        }

    }


    //not fully correct, but should suffice now:
    private static boolean isSynthetic(TreePath path) {
        return ((JCTree) path.getLeaf()).pos == (-1);
    }

    //from semantic.Utilities:
    private static final Set<String> keywords;
    private static final Set<String> nonCtorKeywords;

    static {
        keywords = new HashSet<String>();

        keywords.add("true");
        keywords.add("false");
        keywords.add("null");
        keywords.add("this");
        keywords.add("super");
        keywords.add("class");

        nonCtorKeywords = new HashSet<String>(keywords);
        nonCtorKeywords.remove("this");
        nonCtorKeywords.remove("super");

    }

    public static boolean isKeyword(Tree tree) {
        if (tree.getKind() == Kind.IDENTIFIER) {
            return keywords.contains(((IdentifierTree) tree).getName().toString());
        }
        if (tree.getKind() == Kind.MEMBER_SELECT) {
            return keywords.contains(((MemberSelectTree) tree).getIdentifier().toString());
        }

        return false;
    }

    public static boolean isNonCtorKeyword(Tree tree) {
        if (tree.getKind() == Kind.IDENTIFIER) {
            return nonCtorKeywords.contains(((IdentifierTree) tree).getName().toString());
        }
        if (tree.getKind() == Kind.MEMBER_SELECT) {
            return nonCtorKeywords.contains(((MemberSelectTree) tree).getIdentifier().toString());
        }

        return false;
    }

    private static final Set<ElementKind> LOCAL_ELEMENT_KINDS = EnumSet.of(ElementKind.PARAMETER, ElementKind.LOCAL_VARIABLE, ElementKind.EXCEPTION_PARAMETER, ElementKind.RESOURCE_VARIABLE);

    public static boolean isPrivateElement(Element el) {
        return LOCAL_ELEMENT_KINDS.contains(el.getKind()) || el.getModifiers().contains(Modifier.PRIVATE);
    }

    //from TreeUtilities:
    public static final Set<Kind> CLASS_TREE_KINDS = EnumSet.of(Kind.ANNOTATION_TYPE, Kind.CLASS, Kind.ENUM, Kind.INTERFACE);
}
