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
package org.eclipse.team.internal.ccvs.ui.repo;


import java.util.Properties;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.util.KnownRepositories;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.actions.CVSAction;
import org.eclipse.team.internal.ccvs.ui.model.AllRootsElement;
import org.eclipse.team.internal.ccvs.ui.wizards.NewLocationWizard;
import org.eclipse.team.internal.core.TeamPlugin;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.dialogs.PropertyDialogAction;
import org.eclipse.ui.help.WorkbenchHelp;

/**
 * RepositoriesView is a view on a set of known CVS repositories
 * which allows navigation of the structure of the repository and
 * the performing of CVS-specific operations on the repository contents.
 */
public class RepositoriesView extends RemoteViewPart {
	public static final String VIEW_ID = "org.eclipse.team.ccvs.ui.RepositoriesView"; //$NON-NLS-1$
	
	// The root
	private AllRootsElement root;
	
	// Actions
	private Action newAction;
	private Action newAnonAction;
	private PropertyDialogAction propertiesAction;
	private RemoveRootAction removeRootAction;
	
	IRepositoryListener listener = new IRepositoryListener() {
		public void repositoryAdded(final ICVSRepositoryLocation root) {
			getViewer().getControl().getDisplay().syncExec(new Runnable() {
				public void run() {
					refreshViewer();
					getViewer().setSelection(new StructuredSelection(root));
				}
			});
		}
		public void repositoryRemoved(ICVSRepositoryLocation root) {
			refresh();
		}
		public void repositoriesChanged(ICVSRepositoryLocation[] roots) {
			refresh();
		}
		private void refresh() {
			Display display = getViewer().getControl().getDisplay();
			display.syncExec(new Runnable() {
				public void run() {
					RepositoriesView.this.refreshViewer();
				}
			});
		}
	};
	
	/**
	 * Constructor for RepositoriesView.
	 * @param partName
	 */
	public RepositoriesView() {
		super(VIEW_ID);
	}

	/**
	 * Contribute actions to the view
	 */
	protected void contributeActions() {
		
		final Shell shell = getShell();
		
		// Create actions

		// New Repository (popup)
		newAction = new Action(Policy.bind("RepositoriesView.new"), CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_NEWLOCATION)) { //$NON-NLS-1$
			public void run() {
				NewLocationWizard wizard = new NewLocationWizard();
				WizardDialog dialog = new WizardDialog(shell, wizard);
				dialog.open();
			}
		};
		WorkbenchHelp.setHelp(newAction, IHelpContextIds.NEW_REPOSITORY_LOCATION_ACTION);
		
		if (includeAnonConnection()) {
			newAnonAction = new Action(Policy.bind("RepositoriesView.newAnonCVS"), CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_NEWLOCATION)) { //$NON-NLS-1$
				public void run() {
					Properties p = new Properties();
					p.setProperty("connection", "pserver"); //$NON-NLS-1$ //$NON-NLS-2$
					p.setProperty("user", "anonymous"); //$NON-NLS-1$ //$NON-NLS-2$
					p.setProperty("host", "dev.eclipse.org"); //$NON-NLS-1$ //$NON-NLS-2$
					p.setProperty("root", "/home/eclipse"); //$NON-NLS-1$ //$NON-NLS-2$
					NewLocationWizard wizard = new NewLocationWizard(p);
					WizardDialog dialog = new WizardDialog(shell, wizard);
					dialog.open();
				}
			};
			WorkbenchHelp.setHelp(newAnonAction, IHelpContextIds.NEW_DEV_ECLIPSE_REPOSITORY_LOCATION_ACTION);
		}
		
		// Properties
		propertiesAction = new PropertyDialogAction(shell, getViewer());
		getViewSite().getActionBars().setGlobalActionHandler(ActionFactory.PROPERTIES.getId(), propertiesAction);		
		IStructuredSelection selection = (IStructuredSelection)getViewer().getSelection();
		if (selection.size() == 1 && selection.getFirstElement() instanceof RepositoryRoot) {
			propertiesAction.setEnabled(true);
		} else {
			propertiesAction.setEnabled(false);
		}
		getViewer().addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection ss = (IStructuredSelection)event.getSelection();
				boolean enabled = ss.size() == 1 && ss.getFirstElement() instanceof RepositoryRoot;
				propertiesAction.setEnabled(enabled);
			}
		});
		removeRootAction = new RemoveRootAction(viewer.getControl().getShell(), this);
		removeRootAction.selectionChanged((IStructuredSelection)null);
		WorkbenchHelp.setHelp(removeRootAction, IHelpContextIds.REMOVE_REPOSITORY_LOCATION_ACTION);
		IActionBars bars = getViewSite().getActionBars();
		bars.setGlobalActionHandler(ActionFactory.DELETE.getId(), removeRootAction);
		super.contributeActions();
	}
	
	/**
	 * Method includeEclipseConnection.
	 * @return boolean
	 */
	private boolean includeAnonConnection() {
		return System.getProperty("eclipse.cvs.anon") != null; //$NON-NLS-1$
	}

	/**
	 * @see org.eclipse.team.internal.ccvs.ui.repo.RemoteViewPart#addWorkbenchActions(org.eclipse.jface.action.IMenuManager)
	 */
	protected void addWorkbenchActions(IMenuManager manager) {
		// New actions go next
		MenuManager sub = new MenuManager(Policy.bind("RepositoriesView.newSubmenu"), IWorkbenchActionConstants.GROUP_ADD); //$NON-NLS-1$
		manager.add(sub);
		super.addWorkbenchActions(manager);
		IStructuredSelection selection = (IStructuredSelection)getViewer().getSelection();

		removeRootAction.selectionChanged(selection);
		if(removeRootAction.isEnabled()) {
			manager.add(removeRootAction);
		}		

		if (selection.size() == 1 && selection.getFirstElement() instanceof RepositoryRoot) {
			manager.add(new Separator());
			manager.add(propertiesAction);
		}
		sub.add(newAction);
		if (newAnonAction != null)
			sub.add(newAnonAction);
		sub.add(new Separator("group1"));
	}
	
	/*
	 * @see WorkbenchPart#createPartControl
	 */
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		CVSUIPlugin.getPlugin().getRepositoryManager().addRepositoryListener(listener);
	}
	
	/*
	 * @see WorkbenchPart#dispose
	 */
	public void dispose() {
		CVSUIPlugin.getPlugin().getRepositoryManager().removeRepositoryListener(listener);
		super.dispose();
	}
	
	/**
	 * Initialize the repositories and actions
	 */
	private void initialize() {
		root = new AllRootsElement();
	}

	protected void initializeListeners() {
		super.initializeListeners();
		viewer.addSelectionChangedListener(removeRootAction);
		viewer.getControl().addKeyListener(new KeyListener() {
			public void keyPressed(KeyEvent event) {
				handleKeyPressed(event);
			}
			public void keyReleased(KeyEvent event) {
				handleKeyReleased(event);
			}
		});
	}
	public void handleKeyPressed(KeyEvent event) {
		if (event.character == SWT.DEL && event.stateMask == 0) {
			removeRootAction.run();
		}
	}
	protected void handleKeyReleased(KeyEvent event) {
	}
	
	/**
	 * @see org.eclipse.team.internal.ccvs.ui.repo.RemoteViewPart#getTreeInput()
	 */
	protected Object getTreeInput() {
		initialize();
		return root;
	}

	/**
	 * @see org.eclipse.ui.ISelectionListener#selectionChanged(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)
	 */
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		String msg = getStatusLineMessage(selection);
		getViewSite().getActionBars().getStatusLineManager().setMessage(msg);
	}

	private String getStatusLineMessage(ISelection selection) {
		if (selection==null || selection.isEmpty()) return ""; //$NON-NLS-1$
		if (!(selection instanceof IStructuredSelection)) return ""; //$NON-NLS-1$
		IStructuredSelection s = (IStructuredSelection)selection;
		
		if (s.size() > 1) return Policy.bind("RepositoriesView.NItemsSelected", String.valueOf(s.size())); //$NON-NLS-1$
		Object element = CVSAction.getAdapter(s.getFirstElement(), ICVSResource.class);
		if (element instanceof ICVSRemoteResource) {
			ICVSRemoteResource res = (ICVSRemoteResource)element;
			String name;
			if (res.isContainer()) {
				name = res.getRepositoryRelativePath();
			} else { 
				try {
					name = res.getRepositoryRelativePath() + " " + ((ICVSRemoteFile)res).getRevision(); //$NON-NLS-1$
				} catch (TeamException e) {
					TeamPlugin.log(IStatus.ERROR, Policy.bind("RepositoriesView.CannotGetRevision"), e); //$NON-NLS-1$
					name = res.getRepositoryRelativePath();
				} 
			}
			return Policy.bind("RepositoriesView.ResourceInRepository", name, res.getRepository().getLocation()); //$NON-NLS-1$
		}
		return Policy.bind("RepositoriesView.OneItemSelected"); //$NON-NLS-1$
	}
	
	/**
	 * @see org.eclipse.team.internal.ccvs.ui.repo.RemoteViewPart#getHelpContextId()
	 */
	protected String getHelpContextId() {
		return IHelpContextIds.REPOSITORIES_VIEW;
	}

	/**
	 * @see org.eclipse.team.internal.ccvs.ui.repo.RemoteViewPart#getKeyListener()
	 */
	protected KeyAdapter getKeyListener() {
		return new KeyAdapter() {
			public void keyPressed(KeyEvent event) {
				if (event.keyCode == SWT.F5) {
					if (WorkbenchUserAuthenticator.USE_ALTERNATE_PROMPTER) {
						ICVSRepositoryLocation[] locations = KnownRepositories.getInstance().getRepositories();
						for (int i = 0; i < locations.length; i++) {
							locations[i].flushUserInfo();
						}
					} else {
						refreshAll();
					}
				} else if (event.keyCode == SWT.F9 && WorkbenchUserAuthenticator.USE_ALTERNATE_PROMPTER) {
					refreshAll();
				}
			}
		};
	}

}
