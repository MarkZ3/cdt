/*******************************************************************************
 * Copyright (c) 2011 Anton Gorenkov 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Anton Gorenkov  - initial implementation
 *******************************************************************************/
package org.eclipse.cdt.codan.internal.checkers;

import org.eclipse.cdt.codan.checkers.CodanCheckersActivator;
import org.eclipse.cdt.codan.core.cxx.CxxAstUtils;
import org.eclipse.cdt.codan.core.cxx.model.AbstractIndexAstChecker;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTParameterDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNamedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNewExpression;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPBinding;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPConstructor;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMethod;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ClassTypeHelper;

/**
 * Reports a problem if object of a class cannot be created because
 * class is abstract (it self or its bases have one or more pure virtual
 * functions).
 * 
 * @author Anton Gorenkov
 * 
 */
public class AbstractClassInstantiationChecker extends AbstractIndexAstChecker {
	public static final String ER_ID = "org.eclipse.cdt.codan.internal.checkers.AbstractClassCreation"; //$NON-NLS-1$

	public void processAst(IASTTranslationUnit ast) {
		ast.accept(new OnEachClass());
	}

	class OnEachClass extends ASTVisitor {
		
		OnEachClass() {
			shouldVisitDeclarations = true;
			shouldVisitExpressions = true;
			shouldVisitParameterDeclarations = true;
		}

		public int visit(IASTDeclaration declaration) {
			// Looking for the variables declarations
			if (declaration instanceof IASTSimpleDeclaration) {
				// If there is at least one non-pointer and non-reference type...
				IASTSimpleDeclaration simpleDecl = (IASTSimpleDeclaration)declaration;
				IASTDeclSpecifier declSpec = simpleDecl.getDeclSpecifier();
				if (declSpec.getStorageClass() != IASTDeclSpecifier.sc_typedef) {
					for (IASTDeclarator declarator : simpleDecl.getDeclarators()) {
						if (!hasPointerOrReference(declarator)) {
							// ... check whether type is an abstract class
							checkClass(declSpec);
							break;
						}
					}
				}
			}
			return PROCESS_CONTINUE;
		}

		public int visit(IASTParameterDeclaration parameterDecl) {
			// Looking for parameters declaration. Skip references & pointers.
			if (!hasPointerOrReference(parameterDecl.getDeclarator())) {
				checkClass(parameterDecl.getDeclSpecifier());
			}
			return PROCESS_CONTINUE;
		}
		
		/**
		 *  Checks whether declarator contains a pinter or reference
		 */
		private boolean hasPointerOrReference(IASTDeclarator declarator) {
			return declarator.getPointerOperators().length != 0;
		}

		private void checkClass(IASTDeclSpecifier declSpec) {
			if (declSpec instanceof ICPPASTNamedTypeSpecifier) {
				IASTName className = ((ICPPASTNamedTypeSpecifier)declSpec).getName();
				IBinding binding = getOrResolveBinding(className);
				if (binding instanceof IType) {
					// Resolve class and check whether it is abstract
					reportProblemsIfAbstract((IType)binding, className);
				}
			}
		}
		
		public int visit(IASTExpression expression) {
			// Looking for the new expression
			if (expression instanceof ICPPASTNewExpression) {
				ICPPASTNewExpression newExpression = (ICPPASTNewExpression)expression;
				if (!hasPointerOrReference(newExpression.getTypeId().getAbstractDeclarator())) {
					// Try to resolve its implicit constructor
					IASTDeclSpecifier declSpecifier = newExpression.getTypeId().getDeclSpecifier();
					if (declSpecifier instanceof ICPPASTNamedTypeSpecifier) {
						IASTName constructorName = ((ICPPASTNamedTypeSpecifier)declSpecifier).getName();
						checkClassConstructor(constructorName);
					}				
				}
			}
			// Looking for direct class constructor call and check it
			else if (expression instanceof ICPPASTFunctionCallExpression) {
				ICPPASTFunctionCallExpression functionCall = (ICPPASTFunctionCallExpression)expression;
				IASTExpression functionName = functionCall.getFunctionNameExpression();
				if (functionName instanceof IASTIdExpression) {
					IASTName constructorName = ((IASTIdExpression)functionName).getName();
					checkClassConstructor(constructorName);
				}
			}
			return PROCESS_CONTINUE;
		}
		
		/** 
		 *  Resolves constructor by AST Name, then get its owner class 
		 *  and check whether it is abstract. If it is - report problems 
		 */
		private void checkClassConstructor(IASTName constructorName) {
			IBinding binding = getOrResolveBinding(constructorName);
			if (binding instanceof ICPPConstructor) {
				// Resolve class and check whether it is abstract
				reportProblemsIfAbstract(((ICPPConstructor)binding).getClassOwner(), constructorName);
			}
			else if (binding instanceof IType) {
				reportProblemsIfAbstract((IType)binding, constructorName);
			}
		}
		
		/**
		 *  Tries to get binding by AST Name. If it is not available - tries to resolve it 
		 */
		private IBinding getOrResolveBinding(IASTName name) {
			IBinding binding = name.getBinding();
			if (binding == null) {
				binding = name.resolveBinding();
			}
			return binding;
		}
		
		/**
		 *  Tries to resolve qualified name. If it is not available returns simple name. 
		 */
		private String resolveName(ICPPBinding binding) {
			try {
				if (binding.isGloballyQualified()) {
					StringBuilder buf = new StringBuilder();
					for (String item : binding.getQualifiedName()) {
						if (buf.length() != 0)
							buf.append("::"); //$NON-NLS-1$
						buf.append(item);
					}
					return buf.toString();
				}
			} catch (DOMException e) {
				CodanCheckersActivator.log(e);
			}
			return binding.getName();
		}

		/**
		 *  Checks whether specified type (class or typedef to the class) is abstract class.
		 *  If it is - reports violations on each pure virtual method 
		 */
		private void reportProblemsIfAbstract(IType typeToCheck, IASTNode problemNode ) {
			IType unwindedType = CxxAstUtils.getInstance().unwindTypedef(typeToCheck);
			if (unwindedType instanceof ICPPClassType) {
				ICPPClassType classType = (ICPPClassType)unwindedType;
				for (ICPPMethod method : ClassTypeHelper.getPureVirtualMethods(classType)) {
					reportProblem(ER_ID, problemNode, resolveName(classType), resolveName(method));
				}
			}
		}
	}
}
