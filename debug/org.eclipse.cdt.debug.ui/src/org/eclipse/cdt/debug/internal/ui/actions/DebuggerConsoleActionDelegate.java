/*
 *(c) Copyright QNX Software Systems Ltd. 2002.
 * All Rights Reserved.
 * 
 */
package org.eclipse.cdt.debug.internal.ui.actions;

import org.eclipse.cdt.debug.core.IDebuggerProcessSupport;
import org.eclipse.cdt.debug.internal.core.model.CDebugElement;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

/**
 * Enter type comment.
 * 
 * @since: Oct 23, 2002
 */
public class DebuggerConsoleActionDelegate extends AbstractListenerActionDelegate
{
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.debug.internal.ui.actions.AbstractDebugActionDelegate#doAction(Object)
	 */
	protected void doAction( Object element ) throws DebugException
	{
		if ( element != null && element instanceof CDebugElement )
		{
			IDebuggerProcessSupport dps = (IDebuggerProcessSupport)((CDebugElement)element).getDebugTarget().getAdapter( IDebuggerProcessSupport.class );
			if ( dps != null && dps.supportsDebuggerProcess() )
			{
				dps.setDebuggerProcessDefault( !dps.isDebuggerProcessDefault() );
			} 
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.debug.internal.ui.actions.AbstractDebugActionDelegate#isEnabledFor(Object)
	 */
	protected boolean isEnabledFor( Object element )
	{
		if ( element != null && element instanceof CDebugElement )
		{
			IDebuggerProcessSupport dps = (IDebuggerProcessSupport)((CDebugElement)element).getDebugTarget().getAdapter( IDebuggerProcessSupport.class );
			return ( dps != null && dps.supportsDebuggerProcess() ); 
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.debug.internal.ui.actions.AbstractDebugActionDelegate#enableForMultiSelection()
	 */
	protected boolean enableForMultiSelection()
	{
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IActionDelegate#selectionChanged(IAction, ISelection)
	 */
	public void selectionChanged( IAction action, ISelection selection )
	{
		super.selectionChanged(action, selection);
		boolean checked = false;
		if ( selection != null && selection instanceof IStructuredSelection )
		{
			Object element = ((IStructuredSelection)selection).getFirstElement();
			if ( element != null && element instanceof CDebugElement )
			{
				IDebuggerProcessSupport dps = (IDebuggerProcessSupport)((CDebugElement)element).getDebugTarget().getAdapter( IDebuggerProcessSupport.class );
				checked = ( dps != null && dps.supportsDebuggerProcess() ) ? dps.isDebuggerProcessDefault() : false; 
			}
		}
		action.setChecked( checked );
	}
}
