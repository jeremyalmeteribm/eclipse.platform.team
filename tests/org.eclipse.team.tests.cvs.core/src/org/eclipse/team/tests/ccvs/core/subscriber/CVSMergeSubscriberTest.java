/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.tests.ccvs.core.subscriber;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.Subscriber;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.tests.ccvs.core.CVSTestSetup;


/**
 * Tests the CVSMergeSubscriber
 */
public class CVSMergeSubscriberTest extends CVSSyncSubscriberTest {
	
	public static Test suite() {
		String testName = System.getProperty("eclipse.cvs.testName");
		if (testName == null) {
			TestSuite suite = new TestSuite(CVSMergeSubscriberTest.class);
			return new CVSTestSetup(suite);
		} else {
			return new CVSTestSetup(new CVSMergeSubscriberTest(testName));
		}
	}
	
	public CVSMergeSubscriberTest() {
		super();
	}
	
	public CVSMergeSubscriberTest(String name) {
		super(name);
	}
	
	private IProject branchProject(IProject project, CVSTag root, CVSTag branch) throws TeamException {
		IProject copy = checkoutCopy(project, "-copy");
		tagProject(project, root, false);
		tagProject(project, branch, false);
		updateProject(copy, branch, false);
		return copy;
	}
	
	private void mergeResources(CVSMergeSubscriber subscriber, IProject project, String[] resourcePaths, boolean allowOverwrite) throws CoreException, TeamException, InvocationTargetException, InterruptedException {
		IResource[] resources = getResources(project, resourcePaths);
		SyncInfo[] infos = createSyncInfos(subscriber, resources);
		mergeResources(subscriber, infos, allowOverwrite);
	}
	
	private void mergeResources(Subscriber subscriber, SyncInfo[] infos, boolean allowOverwrite) throws TeamException, InvocationTargetException, InterruptedException {
		TestMergeUpdateOperation action = new TestMergeUpdateOperation(getElements(infos), allowOverwrite);
		action.run(DEFAULT_MONITOR);
	}
	
	/**
	 * Test the basic incoming changes cases
	 * - incoming addition
	 * - incoming deletion
	 * - incoming change
	 * - incoming addition of a folder containing files
	 */
	public void testIncomingChanges() throws InvocationTargetException, InterruptedException, CVSException, CoreException, IOException {
		// Create a test project
		IProject project = createProject("testIncomingChanges", new String[] { "file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		// Checkout and branch a copy
		CVSTag root = new CVSTag("root_branch1", CVSTag.VERSION);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		IProject copy = branchProject(project, root, branch);
		
		// Modify the branch
		addResources(copy, new String[] {"addition.txt", "folderAddition/", "folderAddition/new.txt"}, true);
		deleteResources(copy, new String[] {"folder1/a.txt"}, true);
		
		// modify file1 - make two revisions
		appendText(copy.getFile("file1.txt"), "Appended text 1", false);
		commitProject(copy);
		appendText(copy.getFile("file1.txt"), "Appended text 2", false);
		commitProject(copy);
		
		// modify file2 in both branch and head and ensure it's merged properly 
		appendText(copy.getFile("file2.txt"), "appened text", false);
		commitProject(copy);
		appendText(project.getFile("file2.txt"), "prefixed text", true);
		commitProject(project);		
				
		// create a merge subscriber
		CVSMergeSubscriber subscriber = getSyncInfoSource().createMergeSubscriber(project, root, branch);
		
		// check the sync states
		assertSyncEquals("testIncomingChanges", subscriber, project, 
				new String[]{"file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "addition.txt", "folderAddition/", "folderAddition/new.txt"}, 
				true, 
				new int[]{
						SyncInfo.INCOMING |	SyncInfo.CHANGE, 
						SyncInfo.CONFLICTING |	SyncInfo.CHANGE,
						SyncInfo.IN_SYNC, 
						SyncInfo.INCOMING | SyncInfo.DELETION, 
						SyncInfo.INCOMING | SyncInfo.ADDITION, 
						SyncInfo.INCOMING | SyncInfo.ADDITION,				
						SyncInfo.INCOMING | SyncInfo.ADDITION});

		
		// Perform a merge
		mergeResources(subscriber, project, new String[]{"file1.txt", "file2.txt", "folder1/a.txt", "addition.txt", "folderAddition/", "folderAddition/new.txt"}, true);		

		// check the sync states for the workspace subscriber
		assertSyncEquals("testIncomingChanges", getWorkspaceSubscriber(), project, 
				new String[] { "file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "addition.txt", "folderAddition/", "folderAddition/new.txt"}, 
				true, new int[] {
						SyncInfo.OUTGOING | SyncInfo.CHANGE,
						SyncInfo.OUTGOING | SyncInfo.CHANGE,
						SyncInfo.IN_SYNC,
						SyncInfo.OUTGOING  | SyncInfo.DELETION,
						SyncInfo.OUTGOING | SyncInfo.ADDITION,
						SyncInfo.IN_SYNC,
						SyncInfo.OUTGOING | SyncInfo.ADDITION});
	
		assertEndsWith(project.getFile("file1.txt"), "Appended text 1" + eol + "Appended text 2");
		assertStartsWith(project.getFile("file2.txt"), "prefixed text");
		assertEndsWith(project.getFile("file2.txt"), "appened text");
	}
	
	/**
	 * This tests tests that the cvs update command is sent properly with the two -j options to merge
	 * contents between two revisions into the workspaoce.
	 */
	public void test46007() throws InvocationTargetException, InterruptedException, CVSException, CoreException, IOException {
		// Create a test project
		IProject project = createProject("test46007", new String[] { "file1.txt" });
		appendText(project.getFile("file1.txt"), "dummy", true);
		commitProject(project);
				
		// Checkout and branch a copy
		CVSTag root = new CVSTag("root_branch1", CVSTag.VERSION);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		IProject copy = branchProject(project, root, branch);
		
		// modify file1
		appendText(copy.getFile("file1.txt"), "Appended text 1", true);
		commitProject(copy);
		
		CVSTag root2 = new CVSTag("v1", CVSTag.VERSION);
		tagProject(copy, root2, true);
		appendText(copy.getFile("file1.txt"), "Appended text 2", false);
		commitProject(copy);
		CVSTag root3 = new CVSTag("v2", CVSTag.VERSION);
		tagProject(copy, root3, true);
		
		CVSMergeSubscriber subscriber = getSyncInfoSource().createMergeSubscriber(project, root2, root3);
		assertSyncEquals("test46007", subscriber, project, 
				new String[]{"file1.txt"}, 
				true, 
				new int[]{SyncInfo.CONFLICTING | SyncInfo.CHANGE});
		
		mergeResources(subscriber, project, new String[]{"file1.txt"}, true);		

		assertSyncEquals("test46007", getWorkspaceSubscriber(), project, 
				new String[] { "file1.txt"}, 
				true, new int[] {SyncInfo.OUTGOING | SyncInfo.CHANGE});
		
		// the change made before v1 in the branch should not of been merged into the
		// workspace since the start/end points do not include those changed. Hence,
		// the two -j options sent to the cvs server.
		assertEndsWith(project.getFile("file1.txt"), "Appended text 2");
		assertStartsWith(project.getFile("file1.txt"), "dummy");
	}	
	
	public void testMergableConflicts() throws IOException, TeamException, CoreException, InvocationTargetException, InterruptedException {
		// Create a test project
		IProject project = createProject("testMergableConflicts", new String[] { "file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		setContentsAndEnsureModified(project.getFile("file1.txt"), "some text\nwith several lines\n");
		setContentsAndEnsureModified(project.getFile("file2.txt"), "some text\nwith several lines\n");
		commitProject(project);
		
		// Checkout and branch a copy
		CVSTag root = new CVSTag("root_branch1", CVSTag.VERSION);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		IProject branchedProject = branchProject(project, root, branch);
		
		// modify the branch
		appendText(branchedProject.getFile("file1.txt"), "first line\n", true);
		appendText(branchedProject.getFile("file2.txt"), "last line\n", false);
		commitProject(branchedProject);
		
		// modify HEAD
		appendText(project.getFile("file1.txt"), "last line\n", false);
		commitProject(project);
		// have one local change
		appendText(project.getFile("file2.txt"), "first line\n", true);
		
		// create a merge subscriber
		CVSMergeSubscriber subscriber = getSyncInfoSource().createMergeSubscriber(project, root, branch);
		
		// check the sync states
		assertSyncEquals("testMergableConflicts", subscriber, project, 
				new String[] { "file1.txt", "file2.txt"}, 
				true, new int[] {
							  SyncInfo.CONFLICTING | SyncInfo.CHANGE, 
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE});
		
		// Perform a merge
		mergeResources(subscriber, project, new String[] { 
													   "file1.txt",
		"file2.txt"}, 
		false /* allow overwrite */);
		
		// check the sync states for the workspace subscriber
		assertSyncEquals("testMergableConflicts", getWorkspaceSubscriber(), project, 
				new String[] { "file1.txt", "file2.txt"}, 
				true, new int[] {
							  SyncInfo.OUTGOING | SyncInfo.CHANGE,
							  		SyncInfo.OUTGOING  | SyncInfo.CHANGE});
		
		//TODO: How do we know if the right thing happened to the file contents?	
	}
	
	public void testUnmergableConflicts() throws IOException, TeamException, CoreException, InvocationTargetException, InterruptedException {
		// Create a test project
		IProject project = createProject("testUnmergableConflicts", new String[] { "delete.txt", "file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		setContentsAndEnsureModified(project.getFile("file1.txt"), "some text\nwith several lines\n");
		setContentsAndEnsureModified(project.getFile("file2.txt"), "some text\nwith several lines\n");
		commitProject(project);
		
		// Checkout and branch a copy
		CVSTag root = new CVSTag("root_branch1", CVSTag.VERSION);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		IProject branchedProject = branchProject(project, root, branch);
		
		// modify the branch
		appendText(branchedProject.getFile("file1.txt"), "first line\n", true);
		appendText(branchedProject.getFile("file2.txt"), "last line\n", false);
		addResources(branchedProject, new String[] {"addition.txt"}, false);
		deleteResources(branchedProject, new String[] {"delete.txt", "folder1/a.txt"}, false);
		setContentsAndEnsureModified(branchedProject.getFile("folder1/b.txt"));
		commitProject(branchedProject);
		
		// modify local workspace
		appendText(project.getFile("file1.txt"), "conflict line\n", true);
		setContentsAndEnsureModified(project.getFile("folder1/a.txt"));
		deleteResources(project, new String[] {"delete.txt", "folder1/b.txt"}, false);
		addResources(project, new String[] {"addition.txt"}, false);
		appendText(project.getFile("file2.txt"), "conflict line\n", false);
		
		// create a merge subscriber
		CVSMergeSubscriber subscriber = getSyncInfoSource().createMergeSubscriber(project, root, branch);
		
		// check the sync states
		assertSyncEquals("testUnmergableConflicts", subscriber, project, 
				new String[] { "delete.txt", "file1.txt", "file2.txt", "addition.txt", "folder1/a.txt", "folder1/b.txt"}, 
				true, new int[] {
							  SyncInfo.IN_SYNC, /* TODO: is this OK */
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE, 
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE,
							  		SyncInfo.CONFLICTING | SyncInfo.ADDITION,
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE,
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE});
		
		// TODO: Should actually perform the merge and check the results
		// However, this would require the changes to be redone
		
		// commit to modify HEAD
		commitProject(project);
		
		// check the sync states
		assertSyncEquals("testUnmergableConflicts", subscriber, project, 
				new String[] { "delete.txt", "file1.txt", "file2.txt", "addition.txt", "folder1/a.txt", "folder1/b.txt"}, 
				true, new int[] {
							  SyncInfo.IN_SYNC, /* TODO: is this OK */
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE, 
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE,
							  		SyncInfo.CONFLICTING | SyncInfo.ADDITION,
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE,
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE});
		
		// Perform a merge
		mergeResources(subscriber, project, new String[] { "delete.txt", "file1.txt", "file2.txt", "addition.txt", "folder1/a.txt", "folder1/b.txt"}, true /* allow overwrite */);
		
		// check the sync states for the workspace subscriber
		assertSyncEquals("testUnmergableConflicts", getWorkspaceSubscriber(), project, 
				new String[] { "file1.txt", "file2.txt", "addition.txt", "folder1/a.txt", "folder1/b.txt"}, 
				true, new int[] {
							  SyncInfo.OUTGOING | SyncInfo.CHANGE,
							  		SyncInfo.OUTGOING | SyncInfo.CHANGE,
							  		SyncInfo.OUTGOING | SyncInfo.CHANGE,
							  		SyncInfo.OUTGOING | SyncInfo.DELETION,
							  		SyncInfo.OUTGOING | SyncInfo.ADDITION});
		assertDeleted("testUnmergableConflicts", project, new String[] { "delete.txt" });
		
		//TODO: How do we know if the right thing happend to the file contents?
	}
	
	public void testLocalScrub() throws IOException, TeamException, CoreException, InvocationTargetException, InterruptedException {
		// Create a test project
		IProject project = createProject("testLocalScrub", new String[] { "delete.txt", "file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		setContentsAndEnsureModified(project.getFile("file1.txt"), "some text\nwith several lines\n");
		setContentsAndEnsureModified(project.getFile("file2.txt"), "some text\nwith several lines\n");
		commitProject(project);
		
		// Checkout and branch a copy
		CVSTag root = new CVSTag("root_branch1", CVSTag.VERSION);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		IProject branchedProject = branchProject(project, root, branch);
		
		// modify the branch
		appendText(branchedProject.getFile("file1.txt"), "first line\n", true);
		appendText(branchedProject.getFile("file2.txt"), "last line\n", false);
		addResources(branchedProject, new String[] {"addition.txt"}, false);
		deleteResources(branchedProject, new String[] {"delete.txt", "folder1/a.txt"}, false);
		setContentsAndEnsureModified(branchedProject.getFile("folder1/b.txt"));
		commitProject(branchedProject);
		
		// create a merge subscriber
		CVSMergeSubscriber subscriber = getSyncInfoSource().createMergeSubscriber(project, root, branch);
		
		// check the sync states
		assertSyncEquals("testLocalScrub", subscriber, project, 
				new String[] { "delete.txt", "file1.txt", "file2.txt", "addition.txt", "folder1/a.txt", "folder1/b.txt"}, 
				true, new int[] {
							  SyncInfo.INCOMING | SyncInfo.DELETION,
							  		SyncInfo.INCOMING | SyncInfo.CHANGE, 
							  		SyncInfo.INCOMING | SyncInfo.CHANGE,
							  		SyncInfo.INCOMING | SyncInfo.ADDITION,
							  		SyncInfo.INCOMING | SyncInfo.DELETION,
							  		SyncInfo.INCOMING | SyncInfo.CHANGE});
		
		// scrub the project contents
		IResource[] members = project.members();
		for (int i = 0; i < members.length; i++) {
			IResource resource = members[i];
			if (resource.getName().equals(".project")) continue;
			resource.delete(false, DEFAULT_MONITOR);
		}
		
		// update
		mergeResources(subscriber, project, 
				new String[] { 
						   "delete.txt", 
						   		"file1.txt", 
						   		"file2.txt", 
						   		"addition.txt", 
						   		"folder1/a.txt",
		"folder1/b.txt"}, 
		true /* allow overwrite */);
		
		// commit
		commitProject(project);
	}
	
	public void testBug37546MergeWantsToDeleteNewDirectories() throws CVSException, CoreException {		
		IProject project = createProject("testBug37546", new String[]{"file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		setContentsAndEnsureModified(project.getFile("file1.txt"), "some text\nwith several lines\n");
		commitProject(project);
		
		// Checkout and branch a copy
		CVSTag root = new CVSTag("root_branch1", CVSTag.VERSION);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		IProject branchedProject = branchProject(project, root, branch);
		
		// modify the branch
		addResources(branchedProject, new String[] {"folder2/", "folder2/c.txt"}, true);
		
		// modify HEAD and add the same folder
		addResources(project, new String[] {"folder2/"}, true);
		addResources(project, new String[] {"folder3/"}, true);
		addResources(project, new String[] {"folder4/", "folder4/d.txt"}, false);
		
		// create a merge subscriber
		CVSMergeSubscriber subscriber = getSyncInfoSource().createMergeSubscriber(project, root, branch);
		
		assertSyncEquals("testBug37546", subscriber, project, 
				new String[]{"folder2/", "folder2/c.txt", "folder3/", "folder4/", "folder4/d.txt"}, true, 
				new int[]{
						SyncInfo.IN_SYNC, 
								SyncInfo.INCOMING | SyncInfo.ADDITION,
								SyncInfo.IN_SYNC, SyncInfo.IN_SYNC, SyncInfo.IN_SYNC});		
	}
	
	/*Bug 53129  
	   Outgoing deletions in deleted folders are lost.
	 */
	public void testOutgoingDeletionAfterMergeBug53129() throws TeamException, CoreException, InvocationTargetException, InterruptedException {		
		IProject project = createProject("testBug53129", new String[]{"file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		setContentsAndEnsureModified(project.getFile("file1.txt"), "some text\nwith several lines\n");
		commitProject(project);
		
		// Checkout and branch a copy
		CVSTag root = new CVSTag("root_branch1", CVSTag.VERSION);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		IProject branchedProject = branchProject(project, root, branch);
		
		// modify the branch
		deleteResources(branchedProject, new String[] {"folder1/a.txt", "folder1/b.txt"}, true);
		
		// create a merge subscriber
		CVSMergeSubscriber subscriber = getSyncInfoSource().createMergeSubscriber(project, root, branch);
		
		assertSyncEquals("testBug53129 - 1", subscriber, project, 
				new String[]{"file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"}, true, 
				new int[]{
						SyncInfo.IN_SYNC, SyncInfo.IN_SYNC,
						SyncInfo.INCOMING | SyncInfo.DELETION,
						SyncInfo.INCOMING | SyncInfo.DELETION});
		
		mergeResources(subscriber, project, new String[]{"folder1/a.txt", "folder1/b.txt"}, true);
		
		assertSyncEquals("testBug53129 - 2", getWorkspaceSubscriber(), project, 
				new String[]{"file1.txt", "folder1", "folder1/a.txt", "folder1/b.txt"}, true, 
				new int[]{
						SyncInfo.IN_SYNC, SyncInfo.IN_SYNC, 
						SyncInfo.OUTGOING | SyncInfo.DELETION,
						SyncInfo.OUTGOING | SyncInfo.DELETION});
		
		IFolder f = project.getFolder("folder1");
		f.delete(true, null);
		
		assertSyncEquals("testBug53129 - 3", getWorkspaceSubscriber(), project, 
				new String[]{"file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"}, true, 
				new int[]{
						SyncInfo.IN_SYNC, 
						SyncInfo.IN_SYNC,
						SyncInfo.OUTGOING | SyncInfo.DELETION,
						SyncInfo.OUTGOING | SyncInfo.DELETION});
	}
	
	public void testDisconnectingProject() throws CoreException, IOException, TeamException, InterruptedException {
		// Create a test project (which commits it as well)
		//		Create a test project
		IProject project = createProject("testDisconnect", new String[] { "file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		setContentsAndEnsureModified(project.getFile("file1.txt"), "some text\nwith several lines\n");
		setContentsAndEnsureModified(project.getFile("file2.txt"), "some text\nwith several lines\n");
		commitProject(project);
		
		// Checkout and branch a copy
		CVSTag root = new CVSTag("root_branch1", CVSTag.VERSION);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		IProject branchedProject = branchProject(project, root, branch);
		
		// modify the branch
		appendText(branchedProject.getFile("file1.txt"), "first line\n", true);
		appendText(branchedProject.getFile("file2.txt"), "last line\n", false);
		commitProject(branchedProject);
		
		// create a merge subscriber
		CVSMergeSubscriber subscriber = getSyncInfoSource().createMergeSubscriber(project, root, branch);
		
		RepositoryProvider.unmap(project);
		assertProjectRemoved(subscriber, project);
	}
	
	public void testMarkAsMerged() throws IOException, TeamException, CoreException, InvocationTargetException, InterruptedException {
		// Create a test project
		IProject project = createProject("testMarkAsMerged", new String[] { "delete.txt", "file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		setContentsAndEnsureModified(project.getFile("file1.txt"), "some text\nwith several lines\n");
		setContentsAndEnsureModified(project.getFile("file2.txt"), "some text\nwith several lines\n");
		commitProject(project);
		
		// Checkout and branch a copy
		CVSTag root = new CVSTag("root_branch1", CVSTag.VERSION);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		IProject branchedProject = branchProject(project, root, branch);
		
		// modify the branch
		appendText(branchedProject.getFile("file1.txt"), "first line\n", true);
		appendText(branchedProject.getFile("file2.txt"), "last line\n", false);
		addResources(branchedProject, new String[] {"addition.txt"}, false);
		deleteResources(branchedProject, new String[] {"delete.txt", "folder1/a.txt"}, false);
		setContentsAndEnsureModified(branchedProject.getFile("folder1/b.txt"));
		commitProject(branchedProject);
		
		// modify local workspace
		appendText(project.getFile("file1.txt"), "conflict line\n", true);
		setContentsAndEnsureModified(project.getFile("folder1/a.txt"));
		setContentsAndEnsureModified(project.getFile("delete.txt"));
		addResources(project, new String[] {"addition.txt"}, false);
		appendText(project.getFile("file2.txt"), "conflict line\n", false);
		
		// create a merge subscriber
		CVSMergeSubscriber subscriber = getSyncInfoSource().createMergeSubscriber(project, root, branch);
		
		// check the sync states
		assertSyncEquals("testMarkAsMergedConflicts", subscriber, project, 
				new String[] { "delete.txt", "file1.txt", "file2.txt", "addition.txt", "folder1/a.txt"}, 
				true, new int[] {
							  SyncInfo.CONFLICTING | SyncInfo.CHANGE,
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE, 
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE,
							  		SyncInfo.CONFLICTING | SyncInfo.ADDITION,
							  		SyncInfo.CONFLICTING | SyncInfo.CHANGE});
		
		markAsMerged(subscriber, project, new String[] { "delete.txt", "file1.txt", "file2.txt", "addition.txt", "folder1/a.txt"});
		
		// check the sync states
		assertSyncEquals("testMarkAsMerged", subscriber, project, 
				new String[] { "delete.txt", "file1.txt", "file2.txt", "addition.txt", "folder1/a.txt"}, 
				true, new int[] {
							  SyncInfo.IN_SYNC,
							  		SyncInfo.IN_SYNC, 
							  		SyncInfo.IN_SYNC,
							  		SyncInfo.IN_SYNC,
							  		SyncInfo.IN_SYNC});				
	} 
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		getSyncInfoSource().tearDown();
		super.tearDown();
	}
	
	public void testDeletedAddition() throws TeamException, CoreException, InvocationTargetException, InterruptedException {
		IProject project = createProject("testDeletedAddition", new String[]{"file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		// Checkout and branch a copy
		CVSTag root = new CVSTag("root_branch1", CVSTag.VERSION);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		IProject branchedProject = branchProject(project, root, branch);
		
		// add a file to the branch
		addResources(branchedProject, new String[] {"folder2/", "folder2/added.txt"}, true);
		
		// Setup a merge by creating a merge subscriber
		CVSMergeSubscriber subscriber = getSyncInfoSource().createMergeSubscriber(project, root, branch);
		assertSyncEquals("testDeletedAddition", subscriber, project, 
				new String[]{"folder2/", "folder2/added.txt"}, true, 
				new int[]{
					SyncInfo.INCOMING | SyncInfo.ADDITION, 
					SyncInfo.INCOMING | SyncInfo.ADDITION
				});
		
		// Merge the change with HEAD
		mergeResources(subscriber, project, new String[]{"folder2/", "folder2/added.txt"}, true);
		assertSyncEquals("testDeletedAddition", subscriber, project, 
				new String[]{"folder2/", "folder2/added.txt"}, true, 
				new int[]{
					SyncInfo.IN_SYNC, 
					SyncInfo.IN_SYNC
				});

		// Delete the file from the branch
		deleteResources(branchedProject, new String[] {"folder2/added.txt"}, true);
		assertSyncEquals("testDeletedAddition", subscriber, project, 
				new String[]{"folder2/", "folder2/added.txt"}, true, 
				new int[]{
					SyncInfo.IN_SYNC, 
					SyncInfo.CONFLICTING | SyncInfo.CHANGE
				});
	}
	
	public void testFileAddedToBranch() throws InvocationTargetException, InterruptedException, CoreException, IOException {
		// Create a project
		IProject project = createProject(new String[] { "delete.txt", "file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		// Checkout and branch a copy
		CVSTag root = new CVSTag("root_branch1", CVSTag.VERSION);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		IProject branchedProject = branchProject(project, root, branch);
		
		// Add a file to the branch
		addResources(branchedProject, new String[] {"folder2/", "folder2/added.txt"}, true);
		
		// Merge the file with HEAD but do not commit
		CVSMergeSubscriber subscriber = getSyncInfoSource().createMergeSubscriber(project, root, branch);
		assertSyncEquals("testFileAddedToBranch", subscriber, project, 
				new String[]{"folder2/", "folder2/added.txt"}, true, 
				new int[]{
					SyncInfo.INCOMING | SyncInfo.ADDITION, 
					SyncInfo.INCOMING | SyncInfo.ADDITION
				});
		mergeResources(subscriber, project, new String[]{"folder2/", "folder2/added.txt"}, true /* allow overwrite */);
		
		// Ensure HEAD matches branch
		assertContentsEqual(project, branchedProject);
		
		// Modify the file on the branch
		setContentsAndEnsureModified(branchedProject.getFile("folder2/added.txt"));
		commitProject(branchedProject);
		
		// Merge with HEAD again and commit afterwards
		assertSyncEquals("testFileAddedToBranch", subscriber, project, 
				new String[]{"folder2/added.txt"}, true, 
				new int[]{
					SyncInfo.CONFLICTING | SyncInfo.CHANGE
				});
		mergeResources(subscriber, project, new String[]{"folder2/added.txt"}, true /* allow overwrite */);
		commitProject(project);

		// Ensure HEAD matches branch
		assertContentsEqual(project, branchedProject);
		
		// Modify the file on the branch again
		setContentsAndEnsureModified(branchedProject.getFile("folder2/added.txt"));
		commitProject(branchedProject);
		
		// Merge with HEAD one last time
		assertSyncEquals("testFileAddedToBranch", subscriber, project, 
				new String[]{"folder2/added.txt"}, true, 
				new int[]{
					SyncInfo.CONFLICTING | SyncInfo.CHANGE
				});
		mergeResources(subscriber, project, new String[]{"folder2/added.txt"}, true /* allow overwrite */);

		// Ensure HEAD matches branch
		assertContentsEqual(project, branchedProject);
	}
}