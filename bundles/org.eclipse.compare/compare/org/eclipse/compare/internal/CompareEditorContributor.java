/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.compare.internal;

import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.NavigationAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.help.IWorkbenchHelpSystem;
import org.eclipse.ui.part.EditorActionBarContributor;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;


public class CompareEditorContributor extends EditorActionBarContributor {
	
	public final static String STRATEGY_SEPARATOR = "compare.strategies"; //$NON-NLS-1$
	public final static String BUILTIN_SEPARATOR = "compare.builtin"; //$NON-NLS-1$
	
	private IEditorPart fActiveEditorPart= null;

	private ChangePropertyAction fIgnoreWhitespace;
	private NavigationAction fNext;
	private NavigationAction fPrevious;
	
	private NavigationAction fToolbarNext;
	private NavigationAction fToolbarPrevious;

	public CompareEditorContributor() {
		ResourceBundle bundle= CompareUI.getResourceBundle();
		
		IWorkbenchHelpSystem helpSystem= PlatformUI.getWorkbench().getHelpSystem();
		
		fIgnoreWhitespace= ChangePropertyAction.createIgnoreWhiteSpaceAction(bundle, null);
		helpSystem.setHelp(fIgnoreWhitespace, ICompareContextIds.IGNORE_WHITESPACE_ACTION);
		
		fNext= new NavigationAction(bundle, true);
		helpSystem.setHelp(fNext, ICompareContextIds.GLOBAL_NEXT_DIFF_ACTION);
		
		fPrevious= new NavigationAction(bundle, false);
		helpSystem.setHelp(fPrevious, ICompareContextIds.GLOBAL_PREVIOUS_DIFF_ACTION);
		
		fToolbarNext= new NavigationAction(bundle, true);
		helpSystem.setHelp(fToolbarNext, ICompareContextIds.NEXT_DIFF_ACTION);
		
		fToolbarPrevious= new NavigationAction(bundle, false);
		helpSystem.setHelp(fToolbarPrevious, ICompareContextIds.PREVIOUS_DIFF_ACTION);
	}

	/*
	 * @see EditorActionBarContributor#contributeToToolBar(IToolBarManager)
	 */
	public void contributeToToolBar(IToolBarManager tbm) {
		tbm.add(new Separator(STRATEGY_SEPARATOR));
		tbm.add(new Separator(BUILTIN_SEPARATOR));
		tbm.appendToGroup(BUILTIN_SEPARATOR, fIgnoreWhitespace);
		tbm.appendToGroup(BUILTIN_SEPARATOR, fToolbarNext);
		tbm.appendToGroup(BUILTIN_SEPARATOR, fToolbarPrevious);
	}
	
	/*
	 * @see EditorActionBarContributor#contributeToMenu(IMenuManager)
	 */
	public void contributeToMenu(IMenuManager menuManager) {
		// empty implementation
	}

	public void setActiveEditor(IEditorPart targetEditor) {
				
		if (fActiveEditorPart == targetEditor)
			return;
			
		fActiveEditorPart= targetEditor;
		
		if (fActiveEditorPart != null) {
			IEditorInput input= fActiveEditorPart.getEditorInput();
			if (input instanceof CompareEditorInput) {
				CompareEditorInput compareInput= (CompareEditorInput) input;
				fNext.setCompareEditorInput(compareInput);
				fPrevious.setCompareEditorInput(compareInput);
				// Begin fix http://bugs.eclipse.org/bugs/show_bug.cgi?id=20105
				fToolbarNext.setCompareEditorInput(compareInput);
				fToolbarPrevious.setCompareEditorInput(compareInput);
				// End fix http://bugs.eclipse.org/bugs/show_bug.cgi?id=20105
			}
		}
			
		if (targetEditor instanceof CompareEditor) {
			IActionBars actionBars= getActionBars();
		
			CompareEditor editor= (CompareEditor) targetEditor;
			editor.setActionBars(actionBars);
		
			actionBars.setGlobalActionHandler(ActionFactory.NEXT.getId(), fNext);
			actionBars.setGlobalActionHandler(ActionFactory.PREVIOUS.getId(), fPrevious);

			actionBars.setGlobalActionHandler(ITextEditorActionDefinitionIds.GOTO_NEXT_ANNOTATION, fNext);
			actionBars.setGlobalActionHandler(ITextEditorActionDefinitionIds.GOTO_PREVIOUS_ANNOTATION, fPrevious);
			
			CompareConfiguration cc= editor.getCompareConfiguration();
			fIgnoreWhitespace.setCompareConfiguration(cc);
			
			IContributionItem[] items = actionBars.getToolBarManager().getItems();
			boolean inStrategies = false;
			for (int i=0;i<items.length;i++) {
				if (items[i].getId().equals(STRATEGY_SEPARATOR)) { //$NON-NLS-1$
					inStrategies = true;
				} else if (items[i].getId().equals(BUILTIN_SEPARATOR)) { //$NON-NLS-1$
					break;
				} else if (inStrategies) {
					actionBars.getToolBarManager().remove(items[i]);
				}
			}
			
			IEditorInput input = editor.getEditorInput();
			if (input instanceof CompareEditorInput) {
				Object strategyActions = ((CompareEditorInput)input).getCompareConfiguration().getProperty(ChangeCompareStrategyPropertyAction.COMPARE_STRATEGY_ACTIONS);
				if (strategyActions instanceof List && !((List)strategyActions).isEmpty()) {
					Iterator i = ((List)strategyActions).iterator();
					while (i.hasNext()) {
						Object next = i.next();
						if (next instanceof ChangeCompareStrategyPropertyAction) 
							actionBars.getToolBarManager().appendToGroup(STRATEGY_SEPARATOR, (ChangeCompareStrategyPropertyAction)next);
					}
					actionBars.getToolBarManager().markDirty();
					actionBars.getToolBarManager().update(true);
					actionBars.updateActionBars();
				}
			}
		} else {
			IActionBars actionBars= getActionBars();
			actionBars.setGlobalActionHandler(ActionFactory.NEXT.getId(), null);
			actionBars.setGlobalActionHandler(ActionFactory.PREVIOUS.getId(), null);
			actionBars.setGlobalActionHandler(ITextEditorActionDefinitionIds.GOTO_NEXT_ANNOTATION, null);
			actionBars.setGlobalActionHandler(ITextEditorActionDefinitionIds.GOTO_PREVIOUS_ANNOTATION, null);
		}
	}
	
	public void dispose() {
		setActiveEditor(null);
		super.dispose();
		fIgnoreWhitespace.dispose();
	}
}
