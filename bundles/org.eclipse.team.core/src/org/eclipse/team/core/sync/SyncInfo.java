package org.eclipse.team.core.sync;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.core.Assert;
import org.eclipse.team.internal.core.Policy;

/**
 * Describes the relative synchronization of a <b>remote</b>
 * resource and a <b>local</b> resource using a <b>base</b>
 * resource for comparison.
 * <p>
 * Differences between the base and local resources
 * are classified as <b>outgoing changes</b>; if there is
 * a difference, the local resource is considered the
 * <b>outgoing resource</b>.
 * </p>
 * <p>
 * Differences between the base and remote resources
 * are classified as <b>incoming changes</b>; if there is
 * a difference, the remote resource is considered the
 * <b>incoming resource</b>.
 * </p>
 * <p>
 * Differences between the local and remote resources
 * determine the <b>sync status</b>. The sync status does
 * not take into account the common resource.
 * </p>
 * <p>
 * Note that under this parse of the world, a resource
 * can have both incoming and outgoing changes at the
 * same time, but may nevertheless be in sync!
 * <p>
 * [Issue: "Gender changes" are also an interesting aspect...
 * ]
 * </p>
 */
public class SyncInfo {
	
	/*====================================================================
	 * Constants defining synchronization types:  
	 *====================================================================*/

	/**
	 * Sync constant (value 0) indicating element is in sync.
	 */
	public static final int IN_SYNC = 0;
	
	/**
	 * Sync constant (value 1) indicating that one side was added.
	 */
	public static final int ADDITION = 1;
	
	/**
	 * Sync constant (value 2) indicating that one side was deleted.
	 */
	public static final int DELETION = 2;
	
	/**
	 * Sync constant (value 3) indicating that one side was changed.
	 */
	public static final int CHANGE = 3;

	/**
	 * Bit mask for extracting the change type.
	 */
	public static final int CHANGE_MASK = CHANGE;
	
	/*====================================================================
	 * Constants defining synchronization direction: 
	 *====================================================================*/
	
	/**
	 * Sync constant (value 4) indicating a change to the local resource.
	 */
	public static final int OUTGOING = 4;
	
	/**
	 * Sync constant (value 8) indicating a change to the remote resource.
	 */
	public static final int INCOMING = 8;
	
	/**
	 * Sync constant (value 12) indicating a change to both the remote and local resources.
	 */
	public static final int CONFLICTING = 12;
	
	/**
	 * Bit mask for extracting the synchronization direction. 
	 */
	public static final int DIRECTION_MASK = CONFLICTING;
	
	/*====================================================================
	 * Constants defining synchronization conflict types:
	 *====================================================================*/
	
	/**
	 * Sync constant (value 16) indication that both the local and remote resources have changed 
	 * relative to the base but their contents are the same. 
	 */
	public static final int PSEUDO_CONFLICT = 16;
	
	/**
	 * Sync constant (value 32) indicating that both the local and remote resources have changed 
	 * relative to the base but their content changes do not conflict (e.g. source file changes on different 
	 * lines). These conflicts could be merged automatically.
	 */
	public static final int AUTOMERGE_CONFLICT = 32;
	
	/**
	 * Sync constant (value 64) indicating that both the local and remote resources have changed relative 
	 * to the base and their content changes conflict (e.g. local and remote resource have changes on 
	 * same lines). These conflicts can only be correctly resolved by the user.
	 */
	public static final int MANUAL_CONFLICT = 64;
	
	/*====================================================================
	 * Members:
	 *====================================================================*/
	 private IResource local;
	 private IRemoteResource base;
	 private IRemoteResource remote;
	 private ISyncTreeSubscriber subscriber;
	
	/**
	 * Construct a sync info object.
	 */
	public SyncInfo(IResource local, IRemoteResource base, IRemoteResource remote, ISyncTreeSubscriber subscriber) {
		this.local = local;
		this.base = base;
		this.remote = remote;
		this.subscriber = subscriber;
	}
	
	
	/**
	 * Returns the state of the local resource. Note that the
	 * resource may or may not exist.
	 *
	 * @return a resource
	 */
	public IResource getLocal() {
		return local;
	}
		
	/**
	 * Returns the remote resource handle  for the base resource,
	 * or <code>null</code> if the base resource does not exist.
	 * <p>
	 * [Note: The type of the common resource may be different from the types
	 * of the local and remote resources.
	 * ]
	 * </p>
	 *
	 * @return a remote resource handle, or <code>null</code>
	 */
	public IRemoteResource getBase() {
		return base;
	}
	
	/**
	 * Returns the handle for the remote resource,
	 * or <code>null</code> if the remote resource does not exist.
	 * <p>
	 * [Note: The type of the remote resource may be different from the types
	 * of the local and common resources.
	 * ]
	 * </p>
	 *
	 * @return a remote resource handle, or <code>null</code>
	 */
	public IRemoteResource getRemote() {
		return remote;
	}
	
	/**
	 * Returns the subscriber that created and maintains this sync info
	 * object. 
	 */
	public ISyncTreeSubscriber getSubscriber() {
		return subscriber;
	}
	
	/**
	 * Returns the nature of synchronization of the local and
	 * remote resources.
	 */
	public int getSyncKind(IProgressMonitor progress) {
		
		// TODO: should we cache the sync state for next time. Is this object then immutable?
		// what if the comparison criteria changes? Maybe we need an event marking that the
		// criteria has changed???
		
		progress = Policy.monitorFor(progress);
		int description = IN_SYNC;
	
		IResource local = getLocal();
		IRemoteResource remote = getRemote();
		IRemoteResource base = getBase();
		
		ISyncTreeSubscriber subscriber = getSubscriber();
		ComparisonCriteria criteria = subscriber.getCurrentComparisonCriteria();
		
		boolean localExists = getLocal().exists();
	
		if (subscriber.isThreeWay()) {
			if (base == null) {
				if (remote == null) {
					if (!localExists) {
						Assert.isTrue(false);
					} else {
						description = OUTGOING | ADDITION;
					}
				} else {
					if (!localExists) {
						description = INCOMING | ADDITION;
					} else {
						description = CONFLICTING | ADDITION;
						try {
							progress.beginTask(null, 60);
							if (criteria.compare(local, remote, Policy.subMonitorFor(progress, 30))) {
								description |= PSEUDO_CONFLICT;
							}
						} finally {
							progress.done();
						}
					}
				}
			} else {
				if (!localExists) {
					if (remote == null) {
						description = CONFLICTING | DELETION | PSEUDO_CONFLICT;
					} else {
						if (criteria.compare(base, remote, progress))
							description = OUTGOING | DELETION;
						else
							description = CONFLICTING | CHANGE;
					}
				} else {
					if (remote == null) {
						if (criteria.compare(local, base, progress))
							description = INCOMING | DELETION;
						else
							description = CONFLICTING | CHANGE;
					} else {
						progress.beginTask(null, 90);
						boolean ay = criteria.compare(local, base, Policy.subMonitorFor(progress, 30));
						boolean am = criteria.compare(base, remote, Policy.subMonitorFor(progress, 30));
						if (ay && am) {
							;
						} else if (ay && !am) {
							description = INCOMING | CHANGE;
						} else if (!ay && am) {
							description = OUTGOING | CHANGE;
						} else {
							description = CONFLICTING | CHANGE;
						}
						progress.done();
					}
				}
			}
		} else { // two compare without access to base contents
			if (remote == null) {
				if (!localExists) {
					Assert.isTrue(false);
					// shouldn't happen
				} else {
					description= DELETION;
				}
			} else {
				if (!localExists) {
					description= ADDITION;
				} else {
					if (! criteria.compare(local, remote, Policy.subMonitorFor(progress, 30)))
						description= CHANGE;
				}
			}
		}
		return description;
	}

	/**
	 * @return
	 */
	public boolean isInSync(IProgressMonitor monitor) {
		return getSyncKind(monitor) == IN_SYNC;
	}
}
