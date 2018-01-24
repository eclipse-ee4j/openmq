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

package com.sun.messaging.visualvm.datasource;

import com.sun.tools.visualvm.core.datasource.descriptor.DataSourceDescriptor;
import java.awt.Image;

public class MQResourceDescriptor extends DataSourceDescriptor<MQDataSource> {

	public static enum ResourceType {

		// resource types
		SERVICES, DESTINATIONS, CONSUMERS, PRODUCERS, CONNECTIONS, TRANSACTIONS, CLUSTER, BROKER, CLUSTERED_BROKER, LOG, CLUSTER_ROOT, BROKER_PROXY
	}

	private final ResourceType type;

	public MQResourceDescriptor(MQDataSource ds, String name,
			ResourceType type, String description, Image icon) {
		this(ds, name, type, description, icon, positionFor(type),
				EXPAND_NEVER);
	}

	public MQResourceDescriptor(MQDataSource ds, String name,
			ResourceType type, String description, Image icon, int position,
			int expand) {
		super(ds, name, description, icon, position, expand);
		this.type = type;
	}

	public void changePreferredPosition(int newPosition) {
		setPreferredPosition(newPosition);
	}

	public ResourceType getType() {
		return type;
	}

	public void setIcon(Image newIcon) {
		super.setIcon(newIcon);
	}

	/**
	 * Allow the name of the data source to be dynamically changed
	 */
	public boolean supportsRename() {
		return true;
	}

	private static int positionFor(ResourceType resourceType) {

		int result;
		switch (resourceType) {

		case BROKER:
			result = 1;
			break;
		case CLUSTER:
			result = 2;
			break;
		case CLUSTERED_BROKER:
			result = 3;
			break;
		case SERVICES:
			result = 4;
			break;
		case CONNECTIONS:
			result = 5;
			break;
		case DESTINATIONS:
			result = 6;
			break;
		case CONSUMERS:
			result = 7;
			break;
		case PRODUCERS:
			result = 8;
			break;

		case TRANSACTIONS:
			result = 9;
			break;

		case LOG:
			result = 10;
			break;
		default:
			result = POSITION_AT_THE_END;
			break;
		}

		return result;
	}
}
