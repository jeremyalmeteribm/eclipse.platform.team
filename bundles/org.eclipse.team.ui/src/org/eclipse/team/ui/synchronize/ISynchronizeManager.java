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
package org.eclipse.team.ui.synchronize;

import org.eclipse.team.internal.ui.registry.ISynchronizeParticipantDescriptor;
import org.eclipse.ui.IWorkbenchPage;

/**
 * Manages synchronization view participants. Clients can programatically add 
 * or remove participants via this manager. Whereas static participants
 * defined in a plugin.xml file are added automatically by the manager.
 * <p>
 * Clients are not intended to implement this interface.
 * </p>
 * @see ISynchronizeParticipant
 * @since 3.0 
 */
public interface ISynchronizeManager {
	
	/**
	 * Registers the given listener for participant notifications. Has
	 * no effect if an identical listener is already registered.
	 * 
	 * @param listener listener to register
	 */
	public void addSynchronizeParticipantListener(ISynchronizeParticipantListener listener);
	
	/**
	 * Deregisters the given listener for participant notifications. Has
	 * no effect if an identical listener is not already registered.
	 * 
	 * @param listener listener to deregister
	 */
	public void removeSynchronizeParticipantListener(ISynchronizeParticipantListener listener);

	/**
	 * Adds the given participants to the synchronize manager. Has no effect for
	 * equivalent participants are already registered. The participants will be added
	 * to any existing synchronize views.
	 * 
	 * @param consoles consoles to add
	 */
	public void addSynchronizeParticipants(ISynchronizeParticipant[] consoles);
	
	/**
	 * Removes the given participants from the synchronize manager. If the participants are
	 * being displayed in any synchronize views, the associated pages will be closed.
	 * 
	 * @param consoles consoles to remove
	 */
	public void removeSynchronizeParticipants(ISynchronizeParticipant[] consoles);
	
	/**
	 * Returns a collection of synchronize participants registered with the synchronize manager.
	 * 
	 * @return a collection of synchronize participants registered with the synchronize manager.
	 */
	public ISynchronizeParticipant[] getSynchronizeParticipants();
	
	/**
	 * Opens the synchronize view in the given page. Has no effect if the view is 
	 * already open in that page. 
	 * 
	 * @return the opened synchronize view 
	 */
	public ISynchronizeView showSynchronizeViewInActivePage(IWorkbenchPage page);
	
	/**
	 * Returns a registered synchronize participant with the given id and instance id.
	 * If instance id is <code>null</code> the first registered participant with id
	 * is returned.
	 * 
	 * @return a registered synchronize participant with the given id, or <code>null</code>
	 * if one with that id is not registered.
	 */
	public ISynchronizeParticipant find(String id, String instance_id);
	
	/**
	 * Returns the participant descriptor for the given participant id or 
	 * <code>null</code> if a descriptor is not found for that id.
	 * 
	 * @return the participant descriptor for the given participant id or 
	 * <code>null</code> if a descriptor is not found for that id.
	 */
	public ISynchronizeParticipantDescriptor getParticipantDescriptor(String id);
}