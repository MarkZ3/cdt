/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
/*
 * Created on Jun 13, 2003
 */
package org.eclipse.cdt.internal.core.search.matching;

import java.io.IOException;

import org.eclipse.cdt.core.parser.ast.ASTClassKind;
import org.eclipse.cdt.core.parser.ast.IASTClassSpecifier;
import org.eclipse.cdt.core.parser.ast.IASTEnumerationSpecifier;
import org.eclipse.cdt.core.parser.ast.IASTOffsetableElement;
import org.eclipse.cdt.core.parser.ast.IASTOffsetableNamedElement;
import org.eclipse.cdt.core.search.ICSearchScope;
import org.eclipse.cdt.internal.core.index.IEntryResult;
import org.eclipse.cdt.internal.core.index.impl.IndexInput;
import org.eclipse.cdt.internal.core.index.impl.IndexedFile;
import org.eclipse.cdt.internal.core.search.CharOperation;
import org.eclipse.cdt.internal.core.search.IIndexSearchRequestor;
import org.eclipse.cdt.internal.core.search.indexing.AbstractIndexer;


/**
 * @author aniefer
 */

public class ClassDeclarationPattern extends CSearchPattern {

	public ClassDeclarationPattern( int matchMode, boolean caseSensitive ){
		super( matchMode, caseSensitive, DECLARATIONS );
	}
	
	public ClassDeclarationPattern( char[] name, char[][] containers, ASTClassKind kind, int mode, LimitTo limit, boolean caseSensitive ){
		super( mode, caseSensitive, limit );
		
		simpleName = caseSensitive ? name : CharOperation.toLowerCase( name );
		if( caseSensitive || containers == null ){
			containingTypes = containers;
		} else {
			int len = containers.length;
			this.containingTypes = new char[ len ][];
			for( int i = 0; i < len; i++ ){
				this.containingTypes[i] = CharOperation.toLowerCase( containers[i] );
			}
		} 
		
		classKind = kind;
		limitTo = limit;
	}
	
	public int matchLevel( IASTOffsetableElement node ){
		
		if( !( node instanceof IASTClassSpecifier ) && !( node instanceof IASTEnumerationSpecifier ) )
			return IMPOSSIBLE_MATCH;
		
		String nodeName = ((IASTOffsetableNamedElement)node).getName();
		
		//check name, if simpleName == null, its treated the same as "*"	
		if( simpleName != null && !matchesName( simpleName, nodeName.toCharArray() ) ){
			return IMPOSSIBLE_MATCH;
		}

		String [] fullyQualifiedName = null;
		
		if( node instanceof IASTClassSpecifier ){
			IASTClassSpecifier clsSpec = (IASTClassSpecifier) node;
			fullyQualifiedName = clsSpec.getFullyQualifiedName();		
		} else {
			//TODO fully qualified names for enums
		}
		
		//check containing scopes
		if( !matchQualifications( containingTypes, fullyQualifiedName ) ){
			return IMPOSSIBLE_MATCH;
		}
		
		//check type
		if( classKind != null ){
			if( node instanceof IASTClassSpecifier ){
				IASTClassSpecifier clsSpec = (IASTClassSpecifier) node;
				return ( classKind == clsSpec.getClassKind() ) ? ACCURATE_MATCH : IMPOSSIBLE_MATCH;
			} else {
				return ( classKind == ASTClassKind.ENUM ) ? ACCURATE_MATCH : IMPOSSIBLE_MATCH;
			}
		}
		
		return ACCURATE_MATCH;
	}
	
	public char [] getName() {
		return simpleName;
	}
	public char[] [] getContainingTypes () {
		return containingTypes;
	}
	public ASTClassKind getKind(){
		return classKind;
	}

	private char[] 	  simpleName;
	private char[][]  containingTypes;
	private ASTClassKind classKind;
	private LimitTo	  limitTo;
	
	protected char[] decodedSimpleName;
	private char[][] decodedContainingTypes;
	protected char decodedType;

	
	public void feedIndexRequestor(IIndexSearchRequestor requestor, int detailLevel, int[] references, IndexInput input, ICSearchScope scope) throws IOException {
		boolean isClass = decodedType == CLASS_SUFFIX;
		for (int i = 0, max = references.length; i < max; i++) {
			IndexedFile file = input.getIndexedFile(references[i]);
			String path;
			if (file != null && scope.encloses(path =file.getPath())) {
				//TODO: BOG Fix this up - even if it's not a class we still care 
				if (isClass) {
					requestor.acceptClassDeclaration(path, decodedSimpleName, decodedContainingTypes);
				}  else {
					requestor.acceptClassDeclaration(path, decodedSimpleName, decodedContainingTypes);
				}
			}
	}
	}

	protected void decodeIndexEntry(IEntryResult entryResult) {	
		char[] word = entryResult.getWord();
		int size = word.length;

		this.decodedType = word[TYPE_DECL_LENGTH];
		int oldSlash = TYPE_DECL_LENGTH+1;
		int slash = CharOperation.indexOf(SEPARATOR, word, oldSlash+1);
		
		this.decodedSimpleName = CharOperation.subarray(word, oldSlash+1, slash);
	
		if (slash != -1){
			if (slash+1 < size){
				this.decodedContainingTypes = CharOperation.splitOn('/', CharOperation.subarray(word, slash+1, size));
			}
		} 

	}

	public char[] indexEntryPrefix() {
		return AbstractIndexer.bestTypeDeclarationPrefix(
				simpleName,
				containingTypes,
				classKind,
				_matchMode,
				_caseSensitive
		);
	}

	protected boolean matchIndexEntry() {

		//TODO: BOG PUT QUALIFIER CHECKING BACK IN
//		if (containingTypes != null){
//			// empty char[][] means no enclosing type (in which case, the decoded one is the empty char array)
//			if (containingTypes.length == 0){
//				if (decodedContainingTypes != CharOperation.NO_CHAR_CHAR) return false;
//			} else {
//				if (!CharOperation.equals(containingTypes, decodedContainingTypes, _caseSensitive)) return false;
//			}
//		}

		/* check simple name matches */
		if (simpleName != null){
			switch(_matchMode){
				case EXACT_MATCH :
					if (!CharOperation.equals(simpleName, decodedSimpleName, _caseSensitive)){
						return false;
					}
					break;
				case PREFIX_MATCH :
					if (!CharOperation.prefixEquals(simpleName, decodedSimpleName, _caseSensitive)){
						return false;
					}
					break;
				case PATTERN_MATCH :
					if (!CharOperation.match(simpleName, decodedSimpleName, _caseSensitive)){
						return false;
					}
			}
		}
		return true;
	}
	
}
