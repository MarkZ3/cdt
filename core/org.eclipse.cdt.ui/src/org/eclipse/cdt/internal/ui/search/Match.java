/*******************************************************************************
 * Copyright (c) 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corp. - Rational Software - initial implementation
 ******************************************************************************/
/*
 * Created on Jul 10, 2003
 */
package org.eclipse.cdt.internal.ui.search;

import org.eclipse.cdt.core.search.IMatch;
import org.eclipse.swt.graphics.Image;

/**
 * @author aniefer
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class Match implements IMatch{

	public String name;
	public String parent;
	public Image  image;
	public int 	  start;
	public int    end;
	
	public Match( String name, String parent, Image image, int start, int end ){
		this.name = name;
		this.parent = parent;
		this.image = image;
		this.start = start;
		this.end = end;
	}

}