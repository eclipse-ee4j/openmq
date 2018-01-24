/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.messaging.visualvm.ui;

import javax.management.Attribute;

import com.sun.messaging.jms.management.server.MQObjectName;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

@SuppressWarnings("serial")
public class ConsumerMonitorList extends SingleResourceAttributeList {
   
	public ConsumerMonitorList(DataViewComponent dvc) {
		super(dvc);
	}

	@Override
	String getMBeanObjectName() {
		return MQObjectName.CONSUMER_MANAGER_MONITOR_MBEAN_NAME;
	}
	
	/**
	 * Return whether we want to exclude the specified attribute from the list
	 * 
	 * Subclasses should override this when needed
	 * 
	 * @param attr
	 * @return
	 */
    protected boolean shouldExclude(Attribute attr) {
		return false;
	}

	@Override
	public int getCorner() {
		return DataViewComponent.TOP_LEFT;
	}	
    
}
