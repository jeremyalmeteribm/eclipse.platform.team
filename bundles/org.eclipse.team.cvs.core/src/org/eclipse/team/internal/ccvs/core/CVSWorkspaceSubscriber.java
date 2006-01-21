/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.*;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.resources.mapping.ResourceTraversal;
import org.eclipse.core.runtime.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.*;
import org.eclipse.team.core.diff.IDiffNode;
import org.eclipse.team.core.diff.IThreeWayDiff;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.core.synchronize.SyncInfoSet;
import org.eclipse.team.core.variants.*;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.resources.EclipseSynchronizer;
import org.eclipse.team.internal.ccvs.core.syncinfo.*;
import org.eclipse.team.internal.ccvs.core.util.ResourceStateChangeListeners;

/**
 * CVSWorkspaceSubscriber
 */
public class CVSWorkspaceSubscriber extends CVSSyncTreeSubscriber implements IResourceStateChangeListener {
	
	private CVSResourceVariantTree baseTree, remoteTree;
	
	// qualified name for remote sync info
	private static final String REMOTE_RESOURCE_KEY = "remote-resource-key"; //$NON-NLS-1$

	CVSWorkspaceSubscriber(QualifiedName id, String name) {
		super(id, name);
		
		// install sync info participant
		ResourceVariantByteStore baseSynchronizer = new CVSBaseResourceVariantTree();
		baseTree = new CVSResourceVariantTree(baseSynchronizer, null, getCacheFileContentsHint()) {
			public IResource[] refresh(IResource[] resources, int depth, IProgressMonitor monitor) throws TeamException {
				// TODO Ensure that file contents are cached for modified local files
				try {
					monitor.beginTask(null, 100);
					return new IResource[0];
				} finally {
					monitor.done();
				}
			}
		};
		CVSDescendantResourceVariantByteStore remoteSynchronizer = new CVSDescendantResourceVariantByteStore(
				baseSynchronizer, 
				new PersistantResourceVariantByteStore(new QualifiedName(SYNC_KEY_QUALIFIER, REMOTE_RESOURCE_KEY)));
		remoteTree = new CVSResourceVariantTree(remoteSynchronizer, null, getCacheFileContentsHint());
		
		ResourceStateChangeListeners.getListener().addResourceStateChangeListener(this); 
	}

	/* 
	 * Return the list of projects shared with a CVS team provider.
	 * 
	 * [Issue : this will have to change when folders can be shared with
	 * a team provider instead of the current project restriction]
	 * (non-Javadoc)
	 * @see org.eclipse.team.core.sync.ISyncTreeSubscriber#roots()
	 */
	public IResource[] roots() {
		List result = new ArrayList();
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (int i = 0; i < projects.length; i++) {
			IProject project = projects[i];
			if(project.isOpen()) {
				RepositoryProvider provider = RepositoryProvider.getProvider(project, CVSProviderPlugin.getTypeId());
				if(provider != null) {
					result.add(project);
				}
			}
		}
		return (IProject[]) result.toArray(new IProject[result.size()]);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#resourceSyncInfoChanged(org.eclipse.core.resources.IResource[])
	 */
	public void resourceSyncInfoChanged(IResource[] changedResources) {
		internalResourceSyncInfoChanged(changedResources, true); 
	}

	private void internalResourceSyncInfoChanged(IResource[] changedResources, boolean canModifyWorkspace) {
		getRemoteByteStore().handleResourceChanges(changedResources, canModifyWorkspace);	
		fireTeamResourceChange(SubscriberChangeEvent.asSyncChangedDeltas(this, changedResources));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#externalSyncInfoChange(org.eclipse.core.resources.IResource[])
	 */
	public void externalSyncInfoChange(IResource[] changedResources) {
		internalResourceSyncInfoChanged(changedResources, false);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#resourceModified(org.eclipse.core.resources.IResource[])
	 */
	public void resourceModified(IResource[] changedResources) {
		// This is only ever called from a delta POST_CHANGE
		// which causes problems since the workspace tree is closed
		// for modification and we flush the sync info in resourceSyncInfoChanged
		
		// Since the listeners of the Subscriber will also listen to deltas
		// we don't need to propogate this.
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#projectConfigured(org.eclipse.core.resources.IProject)
	 */
	public void projectConfigured(IProject project) {
		SubscriberChangeEvent delta = new SubscriberChangeEvent(this, ISubscriberChangeEvent.ROOT_ADDED, project);
		fireTeamResourceChange(new SubscriberChangeEvent[] {delta});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#projectDeconfigured(org.eclipse.core.resources.IProject)
	 */
	public void projectDeconfigured(IProject project) {
		try {
			getRemoteTree().flushVariants(project, IResource.DEPTH_INFINITE);
		} catch (TeamException e) {
			CVSProviderPlugin.log(e);
		}
		SubscriberChangeEvent delta = new SubscriberChangeEvent(this, ISubscriberChangeEvent.ROOT_REMOVED, project);
		fireTeamResourceChange(new SubscriberChangeEvent[] {delta});
	}

	public void setRemote(IResource resource, IResourceVariant remote, IProgressMonitor monitor) throws TeamException {
		// TODO: This exposes internal behavior to much
		IResource[] changedResources = 
			((CVSResourceVariantTree)getRemoteTree()).collectChanges(resource, remote, IResource.DEPTH_INFINITE, monitor);
		if (changedResources.length != 0) {
			fireTeamResourceChange(SubscriberChangeEvent.asSyncChangedDeltas(this, changedResources));
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getBaseSynchronizationCache()
	 */
	protected IResourceVariantTree getBaseTree() {
		return baseTree;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getRemoteSynchronizationCache()
	 */
	protected IResourceVariantTree getRemoteTree() {
		return remoteTree;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.Subscriber#collectOutOfSync(org.eclipse.core.resources.IResource[], int, org.eclipse.team.core.synchronize.SyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void collectOutOfSync(IResource[] resources, int depth, final SyncInfoSet set, final IProgressMonitor monitor) {
		monitor.beginTask(null, IProgressMonitor.UNKNOWN);
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			try {
				if (!isSupervised(resource)) {
					return;
				}
			} catch (TeamException e) {
				// fallthrough and try to collect sync info
				CVSProviderPlugin.log(e);
			}
			try {
				visit(resource, new IResourceVisitor() {
					public boolean visit(IResource innerResource) throws CoreException {
						try {
							Policy.checkCanceled(monitor);
							if (innerResource.getType() != IResource.FILE) {
								monitor.subTask(NLS.bind(CVSMessages.CVSWorkspaceSubscriber_1, new String[] { innerResource.getFullPath().toString() })); 
							}
							if (isOutOfSync(innerResource, monitor)) {
								SyncInfo info = getSyncInfo(innerResource);
								if (info != null && info.getKind() != 0) {
									set.add(info);
								}
							}
						} catch (TeamException e) {
							set.addError(new TeamStatus(
									IStatus.ERROR, CVSProviderPlugin.ID, ITeamStatus.RESOURCE_SYNC_INFO_ERROR,
									NLS.bind(CVSMessages.CVSWorkspaceSubscriber_2, new String[] { innerResource.getFullPath().toString(), e.getMessage() }), e, innerResource)); 
						}
						return true;
					}
				}, depth);
			} catch (CoreException e) {
				set.addError(new TeamStatus(
						IStatus.ERROR, CVSProviderPlugin.ID, ITeamStatus.SYNC_INFO_SET_ERROR,
						e.getMessage(), e, ResourcesPlugin.getWorkspace().getRoot()));
			}
		}
		monitor.done();
	}
	
	private void visit(IResource resource, IResourceVisitor visitor, int depth) throws CoreException {
		boolean keepGoing = visitor.visit(resource);
		if (keepGoing && depth != IResource.DEPTH_ZERO) {
			IResource[] members = members(resource);
			for (int i = 0; i < members.length; i++) {
				IResource member = members[i];
				visit(member, visitor, depth == IResource.DEPTH_ONE ? IResource.DEPTH_ZERO : IResource.DEPTH_INFINITE);
			}
		}
	}

	/* internal use only */ boolean isOutOfSync(IResource resource, IProgressMonitor monitor) throws TeamException {
		return (hasIncomingChange(resource) || hasOutgoingChange(resource, monitor));
	}
	
	private boolean hasIncomingChange(IResource resource) throws TeamException {
		return getRemoteByteStore().isVariantKnown(resource);
	}
	
	private boolean hasOutgoingChange(IResource resource, IProgressMonitor monitor) throws CVSException {
		if (resource.getType() == IResource.PROJECT || resource.getType() == IResource.ROOT) {
			// a project (or the workspace root) cannot have outgoing changes
			return false;
		}
		int state = EclipseSynchronizer.getInstance().getModificationState(resource.getParent());
		if (state == ICVSFile.CLEAN) {
			// if the parent is known to be clean then the resource must also be clean
			return false;
		}
		if (resource.getType() == IResource.FILE) {
			// A file is an outgoing change if it is modified
			ICVSFile file = CVSWorkspaceRoot.getCVSFileFor((IFile)resource);
			return file.isModified(monitor);
		} else {
			// A folder is an outgoing change if it is not a CVS folder and not ignored
			ICVSFolder folder = CVSWorkspaceRoot.getCVSFolderFor((IContainer)resource);
			return !folder.isCVSFolder() && !folder.isIgnored();
		}
	}
	
	/*
	 * TODO: Should not need to access this here
	 */
	private CVSDescendantResourceVariantByteStore getRemoteByteStore() {
		return (CVSDescendantResourceVariantByteStore)((CVSResourceVariantTree)getRemoteTree()).getByteStore();
	}

	/**
	 * Update the remote tree to the base
	 * @param folder
	 * @param recurse 
	 */
	public void updateRemote(CVSTeamProvider provider, ICVSFolder folder, boolean recurse, IProgressMonitor monitor) throws TeamException {
		try {
			monitor.beginTask(null, IProgressMonitor.UNKNOWN);
			IResource resource = folder.getIResource();
			if (resource != null) {
				ICVSResource tree = buildBaseTree(
						resource, 
						false, 
						Policy.subMonitorFor(monitor, 50));
				setRemote(resource, (IResourceVariant)tree, Policy.subMonitorFor(monitor, 50));
			}
		} finally {
			monitor.done();
		}
	}
	
	public ICVSRemoteResource buildBaseTree(IResource resource, boolean immutable, IProgressMonitor monitor) throws TeamException {
		try {
			monitor.beginTask(null, IProgressMonitor.UNKNOWN);
			return ((CVSResourceVariantTree)getBaseTree()).buildTree(null, resource, immutable, monitor);
		} finally {
			monitor.done();
		}
	}

	public ICVSRemoteResource buildRemoteTree(IResource resource, boolean immutable, IProgressMonitor monitor) throws TeamException {
		try {
			monitor.beginTask(null, IProgressMonitor.UNKNOWN);
			return ((CVSResourceVariantTree)getRemoteTree()).buildTree(null, resource, immutable, monitor);
		} finally {
			monitor.done();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.Subscriber#getState(org.eclipse.core.resources.mapping.ResourceMapping, int, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public int getState(ResourceMapping mapping, int stateMask, IProgressMonitor monitor) throws CoreException {
		if ((stateMask & IThreeWayDiff.INCOMING) == 0) {
			// If we're only interested in outgoing changes, used the cached modified state
			ResourceTraversal[] traversals = mapping.getTraversals(new SubscriberResourceMappingContext(this, false), monitor);
			if (hasLocalChanges(traversals, monitor)) {
				int state = IThreeWayDiff.OUTGOING;
				state |= getOutgoingKind(traversals, monitor);
			} else {
				return 0;
			}
		}
		return super.getState(mapping, stateMask, monitor);
	}
	
	private int getOutgoingKind(ResourceTraversal[] traversals, IProgressMonitor monitor) throws CoreException {
		int kind = 0;
		for (int i = 0; i < traversals.length; i++) {
			ResourceTraversal traversal = traversals[i];
			IResource[] resources = traversal.getResources();
			for (int j = 0; j < resources.length; j++) {
				IResource resource = resources[j];
				IDiffNode node = getDiff(resource);
				if (node == null)
					return IDiffNode.CHANGE;
				int nextKind = node.getKind();
				if (kind == 0)
					kind = nextKind;
				if (nextKind != kind || nextKind == IDiffNode.CHANGE)
					return IDiffNode.CHANGE;
			}
		}
		return kind;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.Subscriber#hasLocalChanges(org.eclipse.core.resources.mapping.ResourceTraversal[], org.eclipse.core.runtime.IProgressMonitor)
	 */
	public boolean hasLocalChanges(ResourceTraversal[] traversals, IProgressMonitor monitor) throws CoreException {
		monitor = Policy.monitorFor(monitor);
		for (int i = 0; i < traversals.length; i++) {
			ResourceTraversal traversal = traversals[i];
			IResource[] resources = traversal.getResources();
			switch (traversal.getDepth()) {
			case IResource.DEPTH_ZERO:
				for (int j = 0; j < resources.length; j++) {
					IResource resource = resources[j];
					if (isDirectlyDirty(resource, monitor)) {
						return true;
					}
				}
				break;
			case IResource.DEPTH_INFINITE:
				for (int j = 0; j < resources.length; j++) {
					IResource resource = resources[j];
					if (isDirty(resource, monitor)) {
						return true;
					}
				}
				break;
			case IResource.DEPTH_ONE:
				for (int j = 0; j < resources.length; j++) {
					IResource resource = resources[j];
					if (isDirectlyDirty(resource, monitor)) {
						return true;
					}
					IResource[] children = members(resource);
					for (int k = 0; k < children.length; k++) {
						IResource child = children[k];				
						if (isDirectlyDirty(child, monitor)) {
							return true;
						}
					}
				}
				break;
			}
		}
		return false;
	}
	
	private boolean isDirectlyDirty(IResource resource, IProgressMonitor monitor) throws CoreException {
		if (resource.getType() == IResource.FILE) {
			if (isDirty(resource, monitor))
				return true;
		} else {
			IDiffNode node = getDiff(resource);
			if (node != null 
					&& node instanceof IThreeWayDiff 
					&& ((IThreeWayDiff)node).getLocalChange() != null
					&& ((IThreeWayDiff)node).getLocalChange().getKind() != IDiffNode.NO_CHANGE)
				return true;
		}
		return false;
	}
	
	public boolean isDirty(final ICVSResource cvsResource, IProgressMonitor monitor) throws CVSException {
		return !cvsResource.isIgnored() && cvsResource.isModified(monitor);
	}
	
	public boolean isDirty(IResource resource, IProgressMonitor monitor) throws CVSException {

		// No need to decorate non-existant resources
		if (!resource.exists()) return false;

		try {
			return isDirty(CVSWorkspaceRoot.getCVSResourceFor(resource), monitor);
		} catch (CVSException e) {
			//if we get an error report it to the log but assume dirty.
			boolean accessible = resource.getProject().isAccessible();
			if (accessible) {
				throw e;
			}
			// Return dirty if the project is open and clean otherwise
			return accessible;
		}

	}

}