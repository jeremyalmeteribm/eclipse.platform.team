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

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.ICompareStrategy;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;

/**
 * Toggles the activation of a compare strategy
 */
public class ChangeCompareStrategyPropertyAction extends Action implements IPropertyChangeListener, DisposeListener {

	public static final String COMPARE_STRATEGIES = "COMPARE_STRATEGIES"; //$NON-NLS-1$
	public static final String COMPARE_STRATEGY_ACTIONS = "COMPARE_STRATEGY_ACTIONS"; //$NON-NLS-1$
	public static final String COMPARE_STRATEGIES_INITIALIZING = "COMPARE_STRATEGIES_INITIALIZING"; //$NON-NLS-1$
	
	private CompareConfiguration fCompareConfiguration;
	private ResourceBundle fBundle;
	private String fPrefix = "strategy."; //$NON-NLS-1$
	private CompareStrategyDescriptor fCompareStrategyDescriptor;
	private ICompareStrategy fCompareStrategy;
	
	public ChangeCompareStrategyPropertyAction(CompareStrategyDescriptor compareStrategyDescriptor,  CompareConfiguration compareConfiguration) {
		this.fBundle = compareStrategyDescriptor.getResourceBundle();
		this.fCompareStrategyDescriptor = compareStrategyDescriptor; 
		this.fCompareStrategy = compareStrategyDescriptor.createCompareStrategy();
		Utilities.initAction(this, fBundle, fPrefix);
		
		//Utilities only loads images from compare plugin
		setImageDescriptor(compareStrategyDescriptor.getImageDescriptor());
		setId(compareStrategyDescriptor.getStrategyId());//TODO this ok?
		setCompareConfiguration(compareConfiguration);
		
		if (fCompareStrategy.isEnabledInitially()) {
			setChecked(true);
			setProperty(true);
		}
	}
	
	void setProperty(boolean state){
		if (fCompareConfiguration != null){
			Map strategies =  new HashMap();
			if(fCompareConfiguration.getProperty(COMPARE_STRATEGIES) != null){
				strategies.putAll((Map)fCompareConfiguration.getProperty(COMPARE_STRATEGIES));
			}
			if(state){
				strategies.put(fCompareStrategyDescriptor.getStrategyId(), fCompareStrategy);
			}else{
				strategies.remove(fCompareStrategyDescriptor.getStrategyId());
			}
			fCompareConfiguration.setProperty(COMPARE_STRATEGIES, strategies);
		}
	}

	boolean getProperty(){
		Map strategies = (Map) fCompareConfiguration.getProperty(COMPARE_STRATEGIES);
		if(strategies == null){
			strategies = new HashMap();
		}
		return strategies.containsKey(fCompareStrategyDescriptor.getStrategyId());
	}

	public void run() {
		boolean b= !getProperty();
		setChecked(b);
		setProperty(b);
	}

	public void setChecked(boolean state) {
		super.setChecked(state);
		Utilities.initToggleAction(this, fBundle, fPrefix, state);
	}
	
	public void setCompareConfiguration(CompareConfiguration cc) {
		if (fCompareConfiguration != null)
			fCompareConfiguration.removePropertyChangeListener(this);
		fCompareConfiguration= cc;
		if (fCompareConfiguration != null)
			fCompareConfiguration.addPropertyChangeListener(this);
		setChecked(getProperty());
	}

	public void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty().equals(COMPARE_STRATEGIES) && event.getNewValue() instanceof Map) {
			setChecked(((Map)event.getNewValue()).containsKey(fCompareStrategyDescriptor.getStrategyId()));
		}
	}
	
	public void dispose(){
		if (fCompareConfiguration != null)
			fCompareConfiguration.removePropertyChangeListener(this);
	}
	
	public void widgetDisposed(DisposeEvent e) {
		dispose();
	}
	
	public String getStrategyId() {
		return fCompareStrategyDescriptor.getStrategyId();
	}
	
	public void setInput(Object input, Object ancestor, Object left, Object right)  {
		fCompareStrategy.setInput(input, ancestor, left, right);
	}
}
