package org.eclipse.compare.structuremergeviewer;

import org.eclipse.compare.ICompareStrategy;
 
/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

 /**
  * An extension to the {@link IStructureCreator2} interface that supports the
  * use of activated compare strategies when generating the string contents
  * of a node.
  * <p>
  * Clients may implement this interface; there is no standard implementation.
  * </p>
  * @since 3.6
  */
 public interface IStructureCreator3 extends IStructureCreator2 {
 		
	 /**
	  * Returns true if the two nodes are equal for comparison purposes.  
	  * If <code>compareStrategies</code> is not empty, the strategies should be applied 
	  * to each line of each node's text representation.  The strategies should be applied to each 
	  * line individually, without delimiters, but with all other whitespace characters intact.
	  * A default implementation <code>StructureCreator.contentsEquals()</code> is available for reuse.
	  * @param node1
	  * @param contributor1 either 'A', 'L', or 'R' for ancestor, left or right contributor
	  * @param node2
	  * @param contributor2 either 'A', 'L', or 'R' for ancestor, left or right contributor
	  * @param ignoreWhitespace if <code>true</code> whitespace characters should be ignored
	  * 		when determining equality.
	  * @param compareStrategies the strategies used to customize the comparison of lines of text.
	  * @return whether the two nodes are equal for comparison purposes
	  */
	 boolean contentsEquals(Object node1, char contributor1,
			 Object node2, char contributor2, boolean ignoreWhitespace, 
			 ICompareStrategy[] compareStrategies);
 
 }