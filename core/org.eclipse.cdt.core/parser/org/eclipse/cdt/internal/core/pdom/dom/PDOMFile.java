/*******************************************************************************
 * Copyright (c) 2005, 2006 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX - Initial API and implementation
 * Markus Schorn (Wind River Systems)
 *******************************************************************************/
package org.eclipse.cdt.internal.core.pdom.dom;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIncludeStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorMacroDefinition;
import org.eclipse.cdt.core.index.IIndexInclude;
import org.eclipse.cdt.core.index.IIndexMacro;
import org.eclipse.cdt.internal.core.index.IIndexFragment;
import org.eclipse.cdt.internal.core.index.IIndexFragmentFile;
import org.eclipse.cdt.internal.core.index.IWritableIndexFragment;
import org.eclipse.cdt.internal.core.pdom.PDOM;
import org.eclipse.cdt.internal.core.pdom.db.Database;
import org.eclipse.cdt.internal.core.pdom.db.IBTreeComparator;
import org.eclipse.cdt.internal.core.pdom.db.IBTreeVisitor;
import org.eclipse.cdt.internal.core.pdom.db.IString;
import org.eclipse.core.runtime.CoreException;

/**
 * Represents a file containing names.
 * 
 * @author Doug Schaefer
 *
 */
public class PDOMFile implements IIndexFragmentFile {

	private final PDOM pdom;
	private final int record;
	
	private static final int FIRST_NAME = 0;
	private static final int FIRST_INCLUDE = 4;
	private static final int FIRST_INCLUDED_BY = 8;
	private static final int FIRST_MACRO = 12;
	private static final int FILE_NAME = 16;
	private static final int TIME_STAMP = 20;
	
	private static final int RECORD_SIZE = 28;
	
	public static class Comparator implements IBTreeComparator {
		private Database db;
		
		public Comparator(Database db) {
			this.db = db;
		}
		
		public int compare(int record1, int record2) throws CoreException {
			IString name1 = db.getString(db.getInt(record1 + FILE_NAME));
			IString name2 = db.getString(db.getInt(record2 + FILE_NAME));
			return name1.compare(name2);
		}
	}
	
	public static class Finder implements IBTreeVisitor {
		private final Database db;
		private final String key;
		private int record;
		
		public Finder(Database db, String key) {
			this.db = db;
			this.key = key;
		}
		
		public int compare(int record) throws CoreException {
			IString name = db.getString(db.getInt(record + FILE_NAME));
			return name.compare(key);
		}

		public boolean visit(int record) throws CoreException {
			this.record = record;
			return false;
		}
		
		public int getRecord() {
			return record;
		}
	}
	
	public PDOMFile(PDOM pdom, int record) {
		this.pdom = pdom;
		this.record = record;
	}
	
	public PDOMFile(PDOM pdom, String filename) throws CoreException {
		this.pdom = pdom;
		Database db = pdom.getDB();
		record = db.malloc(RECORD_SIZE);
		db.putInt(record + FILE_NAME, db.newString(filename).getRecord());
		db.putLong(record + TIME_STAMP, 0);
		setFirstName(null);
		setFirstInclude(null);
		setFirstIncludedBy(null);
	}
	
	public int getRecord() {
		return record;
	}
	
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (obj instanceof PDOMFile) {
			PDOMFile other = (PDOMFile)obj;
			return pdom.equals(other.pdom) && record == other.record;
		}
		return false;
	}
	
	public IString getFileName() throws CoreException {
		Database db = pdom.getDB();
		return db.getString(db.getInt(record + FILE_NAME));
	}
	
	public void setFilename(String newName) throws CoreException {
		Database db = pdom.getDB();
		int oldRecord = db.getInt(record + FILE_NAME);
		db.free(oldRecord);
		db.putInt(record + FILE_NAME, db.newString(newName).getRecord());
	}

	public long getTimestamp() throws CoreException {
		Database db = pdom.getDB();
		return db.getLong(record + TIME_STAMP);
	}
	
	public void setTimestamp(long timestamp) throws CoreException {
		Database db= pdom.getDB();
		db.putLong(record + TIME_STAMP, timestamp);
	}
	
	public PDOMName getFirstName() throws CoreException {
		int namerec = pdom.getDB().getInt(record + FIRST_NAME);
		return namerec != 0 ? new PDOMName(pdom, namerec) : null;
	}

	public void setFirstName(PDOMName firstName) throws CoreException {
		int namerec = firstName != null ? firstName.getRecord() : 0;
		pdom.getDB().putInt(record + FIRST_NAME, namerec);
	}
	
	public void addName(PDOMName name) throws CoreException {
		PDOMName firstName = getFirstName();
		if (firstName != null) {
			name.setNextInFile(firstName);
			firstName.setPrevInFile(name);
		}
		setFirstName(name);
	}
	
	public PDOMInclude getFirstInclude() throws CoreException {
		int increc = pdom.getDB().getInt(record + FIRST_INCLUDE);
		return increc != 0 ? new PDOMInclude(pdom, increc) : null;
	}
	
	public void setFirstInclude(PDOMInclude include) throws CoreException {
		int rec = include != null ? include.getRecord() : 0;
		pdom.getDB().putInt(record + FIRST_INCLUDE, rec);
	}
	
	public PDOMInclude getFirstIncludedBy() throws CoreException {
		int rec = pdom.getDB().getInt(record + FIRST_INCLUDED_BY);
		return rec != 0 ? new PDOMInclude(pdom, rec) : null;
	}
	
	public void setFirstIncludedBy(PDOMInclude includedBy) throws CoreException {
		int rec = includedBy != null ? includedBy.getRecord() : 0;
		pdom.getDB().putInt(record + FIRST_INCLUDED_BY, rec);
	}
	
	public PDOMMacro getFirstMacro() throws CoreException {
		int rec = pdom.getDB().getInt(record + FIRST_MACRO);
		return rec != 0 ? new PDOMMacro(pdom, rec) : null;
	}

	public void setFirstMacro(PDOMMacro macro) throws CoreException {
		int rec = macro != null ? macro.getRecord() : 0;
		pdom.getDB().putInt(record + FIRST_MACRO, rec);
	}
	
	public void addMacros(IASTPreprocessorMacroDefinition[] macros) throws CoreException {
		assert getFirstMacro() == null;
				
		PDOMMacro lastMacro= null;
		for (int i = 0; i < macros.length; i++) {
			IASTPreprocessorMacroDefinition macro = macros[i];
			PDOMMacro pdomMacro = new PDOMMacro(pdom, macro);
			if (lastMacro == null) {
				setFirstMacro(pdomMacro);
			}
			else {
				lastMacro.setNextMacro(pdomMacro);
			}
			lastMacro= pdomMacro;
		}
	}
	
	public void clear() throws CoreException {
		// Remove the includes
		PDOMInclude include = getFirstInclude();
		while (include != null) {
			PDOMInclude nextInclude = include.getNextInIncludes();
			include.delete();
			include = nextInclude;
		}
		setFirstInclude(include);
		
		// Delete all the macros in this file
		PDOMMacro macro = getFirstMacro();
		while (macro != null) {
			PDOMMacro nextMacro = macro.getNextMacro();
			macro.delete();
			macro = nextMacro;
		}
		setFirstMacro(null);
		
		// Delete all the names in this file
		PDOMName name = getFirstName();
		while (name != null) {
			PDOMName nextName = name.getNextInFile();
			name.delete();
			name = nextName;
		}
		setFirstName(null);
	}
	
	public void addIncludesTo(IIndexFragmentFile[] files, IASTPreprocessorIncludeStatement[] includes) throws CoreException {
		assert files.length == includes.length;
		assert getFirstInclude() == null;
		
		PDOMInclude lastInclude= null;
		for (int i = 0; i < includes.length; i++) {
			IASTPreprocessorIncludeStatement statement = includes[i];
			PDOMFile file= (PDOMFile) files[i];
			assert file.getIndexFragment() instanceof IWritableIndexFragment;
			
			PDOMInclude pdomInclude = new PDOMInclude(pdom, statement);
			pdomInclude.setIncludedBy(this);
			pdomInclude.setIncludes(file);
			
			file.addIncludedBy(pdomInclude);
			if (lastInclude == null) {
				setFirstInclude(pdomInclude);
			}
			else {
				lastInclude.setNextInIncludes(pdomInclude);
			}
			lastInclude= pdomInclude;
		}
	}
	
	public void addIncludedBy(PDOMInclude include) throws CoreException {
		PDOMInclude firstIncludedBy = getFirstIncludedBy();
		if (firstIncludedBy != null) {
			include.setNextInIncludedBy(firstIncludedBy);
			firstIncludedBy.setPrevInIncludedBy(include);
		}
		setFirstIncludedBy(include);
	}
	
	
	
	public IIndexInclude[] getIncludes() throws CoreException {
		List result= new ArrayList();
		PDOMInclude include = getFirstInclude();
		while (include != null) {
			result.add(include);
			include = include.getNextInIncludes();
		}
		return (IIndexInclude[]) result.toArray(new IIndexInclude[result.size()]);
	}

	public String getLocation() throws CoreException {
		return getFileName().getString();
	}

	public IIndexMacro[] getMacros() throws CoreException {
		List result= new ArrayList();
		PDOMMacro macro = getFirstMacro();
		while (macro != null) {
			result.add(macro);
			macro = macro.getNextMacro();
		}
		return (IIndexMacro[]) result.toArray(new IIndexMacro[result.size()]);
	}
	
	public IIndexFragment getIndexFragment() {
		return pdom;
	}
}
