/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.core.variants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.core.Assert;
import org.eclipse.team.internal.core.Policy;

/**
 * An implemenation of <code>IResourceVariantTree</code> that provides the logic for
 * refreshing the tree and collecting the results so they can be cached locally.
 * This class does not perform the caching but relies on subclasses to do that by
 * overriding the <code>setVariant</code> method. The subclass 
 * {@link ResourceVariantTree} does provide caching.
 * 
 * @see IResourceVariantTree
 * @see ResourceVariantTree
 * 
 * @since 3.0
 */
public abstract class AbstractResourceVariantTree implements IResourceVariantTree {

	/**
	 * Refreshes the resource variant tree for the specified resources and possibly their descendants,
	 * depending on the depth. The default implementation of this method invokes
	 * <code>refresh(IResource, int, IProgressMonitor)</code> for each resource.
	 * Subclasses may override but should either invoke the above mentioned refresh or 
	 * <code>collectChanges</code> in order to reconcile the resource variant tree.
	 * @param resources the resources whose variants should be refreshed
	 * @param depth the depth of the refresh (one of <code>IResource.DEPTH_ZERO</code>,
	 * <code>IResource.DEPTH_ONE</code>, or <code>IResource.DEPTH_INFINITE</code>)
	 * @param monitor a progress monitor
	 * @return the array of resources whose corresponding varianst have changed
	 * @throws TeamException
	 */
	public IResource[] refresh(IResource[] resources, int depth, IProgressMonitor monitor) throws TeamException {
		List changedResources = new ArrayList();
		monitor.beginTask(null, 100 * resources.length);
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			IResource[] changed = refresh(resource, depth, Policy.subMonitorFor(monitor, 100));
			changedResources.addAll(Arrays.asList(changed));
		}
		monitor.done();
		if (changedResources == null) return new IResource[0];
		return (IResource[]) changedResources.toArray(new IResource[changedResources.size()]);
	}

	/**
	 * Helper method invoked from <code>refresh(IResource[], int, IProgressMonitor monitor)</code>
	 * for each resource. The default implementation performs the following steps:
	 * <ol>
	 * <li>get the resource variant handle corresponding to the local resource by calling
	 * <code>getRemoteTree</code>.
	 * <li>pass the local resource and the resource variant handle to <code>collectChanges</code>
	 * </ol>
	 * Subclasses may override but should perform roughly the same steps.
	 * @param resource the resoure being refreshed
	 * @param depth the depth of the refresh  (one of <code>IResource.DEPTH_ZERO</code>,
	 * <code>IResource.DEPTH_ONE</code>, or <code>IResource.DEPTH_INFINITE</code>)
	 * @param monitor a progress monitor
	 * @return the resource's whose variants have changed
	 * @throws TeamException
	 */
	protected IResource[] refresh(IResource resource, int depth, IProgressMonitor monitor) throws TeamException {
		IResource[] changedResources = null;
		monitor.beginTask(null, 100);
		try {
			monitor.setTaskName(Policy.bind("SynchronizationCacheRefreshOperation.0", resource.getFullPath().makeRelative().toString())); //$NON-NLS-1$
			
			// build the remote tree only if an initial tree hasn't been provided
			IResourceVariant tree = fetchVariant(resource, depth, Policy.subMonitorFor(monitor, 70));
			
			// update the known remote handles 
			IProgressMonitor sub = Policy.infiniteSubMonitorFor(monitor, 30);
			try {
				sub.beginTask(null, 64);
				changedResources = collectChanges(resource, tree, depth, Policy.subMonitorFor(sub, 64));
			} finally {
				sub.done();	 
			}
		} finally {
			monitor.done();
		}
		if (changedResources == null) return new IResource[0];
		return changedResources;
	}

	/**
	 * Collect the changes in the remote tree to the specified depth.
	 * @param local the local resource being refreshed
	 * @param remote the corresponding resource variant
	 * @param depth the depth of the refresh  (one of <code>IResource.DEPTH_ZERO</code>,
	 * <code>IResource.DEPTH_ONE</code>, or <code>IResource.DEPTH_INFINITE</code>)
	 * @param monitor a progress monitor
	 * @return the resource's whose variants have changed
	 * @throws TeamException
	 */
	protected IResource[] collectChanges(IResource local, IResourceVariant remote, int depth, IProgressMonitor monitor) throws TeamException {
		List changedResources = new ArrayList();
		collectChanges(local, remote, changedResources, depth, monitor);
		return (IResource[]) changedResources.toArray(new IResource[changedResources.size()]);
	}

	/**
	 * Fetch the members of the given resource variant handle. This method may
	 * return members that were fetched when <code>fetchVariant</code> was called or
	 * may fetch the children directly (i.e. this method may contact the server). 
	 * @param variant the resource variant
	 * @param progress a progress monitor
	 * @return the members of the resource variant.
	 */
	protected abstract IResourceVariant[] fetchMembers(IResourceVariant variant, IProgressMonitor progress) throws TeamException;

	/**
	 * Fetch the resource variant corresponding to the given resource. The depth
	 * parameter indicates the depth of the refresh operation and also indicates the
	 * depth to which the resource variant's desendants will be traversed. 
	 * This method may prefetch the descendants to the provided depth
	 * or may just return the variant handle corresponding to the given 
	 * local resource, in which case
	 * the descendant variants will be fetched by <code>fetchMembers(IResourceVariant, IProgressMonitor)</code>.
	 * @param resource the local resource
	 * @param depth the depth of the refresh  (one of <code>IResource.DEPTH_ZERO</code>,
	 * <code>IResource.DEPTH_ONE</code>, or <code>IResource.DEPTH_INFINITE</code>)
	 * @param monitor a progress monitor
	 * @return the resource variant corresponding to the given local resource
	 */
	protected abstract IResourceVariant fetchVariant(IResource resource, int depth, IProgressMonitor monitor) throws TeamException;

	/**
	 * Method that is invoked during collection to let subclasses know which members
	 * were collected for the given resource. Implementors should purge any cached 
	 * state for children of the local resource that are no longer members. Any such resources
	 * should be returned to allow clients to clear any state they maintain for those resources.
	 * @param local the local resource
	 * @param members the collected members
	 * @return any resources that were previously collected whose state has been flushed
	 */
	protected IResource[] collectedMembers(IResource local, IResource[] members) throws TeamException {
		return new IResource[0];
	}

	/**
	 * Set the variant associated with the local resource to the newly fetched resource
	 * variant. This method is invoked during change collection and should return whether
	 * the variant associated with the local resource has changed
	 * @param local the local resource
	 * @param remote the newly fetched resoure variant
	 * @return <code>true</code> if the resource variant changed
	 * @throws TeamException
	 */
	protected abstract boolean setVariant(IResource local, IResourceVariant remote) throws TeamException;

	private void collectChanges(IResource local, IResourceVariant remote, Collection changedResources, int depth, IProgressMonitor monitor) throws TeamException {
		boolean changed = setVariant(local, remote);
		if (changed) {
			changedResources.add(local);
		}
		if (depth == IResource.DEPTH_ZERO) return;
		Map children = mergedMembers(local, remote, monitor);	
		for (Iterator it = children.keySet().iterator(); it.hasNext();) {
			IResource localChild = (IResource) it.next();
			IResourceVariant remoteChild = (IResourceVariant)children.get(localChild);
			collectChanges(localChild, remoteChild, changedResources,
					depth == IResource.DEPTH_INFINITE ? IResource.DEPTH_INFINITE : IResource.DEPTH_ZERO, 
					monitor);
		}
		
		IResource[] cleared = collectedMembers(local, (IResource[]) children.keySet().toArray(new IResource[children.keySet().size()]));
		changedResources.addAll(Arrays.asList(cleared));
		monitor.worked(1);
	}

	private Map mergedMembers(IResource local, IResourceVariant remote, IProgressMonitor progress) throws TeamException {
		
		// {IResource -> IResourceVariant}
		Map mergedResources = new HashMap();
		
		IResourceVariant[] remoteChildren;
		if (remote == null) {
			remoteChildren = new IResourceVariant[0];
		} else {
			remoteChildren = fetchMembers(remote, progress);
		}
		
		
		IResource[] localChildren = members(local);		
	
		if (remoteChildren.length > 0 || localChildren.length > 0) {
			Set allSet = new HashSet(20);
			Map localSet = null;
			Map remoteSet = null;
	
			if (localChildren.length > 0) {
				localSet = new HashMap(10);
				for (int i = 0; i < localChildren.length; i++) {
					IResource localChild = localChildren[i];
					String name = localChild.getName();
					localSet.put(name, localChild);
					allSet.add(name);
				}
			}
	
			if (remoteChildren.length > 0) {
				remoteSet = new HashMap(10);
				for (int i = 0; i < remoteChildren.length; i++) {
					IResourceVariant remoteChild = remoteChildren[i];
					String name = remoteChild.getName();
					remoteSet.put(name, remoteChild);
					allSet.add(name);
				}
			}
			
			Iterator e = allSet.iterator();
			while (e.hasNext()) {
				String keyChildName = (String) e.next();
	
				Policy.checkCanceled(progress);
	
				IResource localChild =
					localSet != null ? (IResource) localSet.get(keyChildName) : null;
	
					IResourceVariant remoteChild =
						remoteSet != null ? (IResourceVariant) remoteSet.get(keyChildName) : null;
						
					if (localChild == null) {
						// there has to be a remote resource available if we got this far
						Assert.isTrue(remoteChild != null);
						boolean isContainer = remoteChild.isContainer();				
						localChild = getResourceChild(local /* parent */, keyChildName, isContainer);
					}
					mergedResources.put(localChild, remoteChild);				
			}
		}
		return mergedResources;
	}

	private IResource getResourceChild(IResource parent, String childName, boolean isContainer) {
		if (parent.getType() == IResource.FILE) {
			return null;
		}
		if (isContainer) {
			return ((IContainer) parent).getFolder(new Path(null, childName));
		} else {
			return ((IContainer) parent).getFile(new Path(null, childName));
		}
	}

}
