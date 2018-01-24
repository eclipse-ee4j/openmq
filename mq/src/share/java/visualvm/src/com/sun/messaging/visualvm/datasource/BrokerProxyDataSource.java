/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Image;

import org.openide.util.ImageUtilities;

import com.sun.messaging.visualvm.datasource.MQResourceDescriptor.ResourceType;
import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.jmx.JmxApplicationException;
import com.sun.tools.visualvm.jmx.JmxApplicationsSupport;

public class BrokerProxyDataSource extends MQDataSource {

    private static final Image NODE_ICON = ImageUtilities.loadImage(
            "com/sun/messaging/visualvm/ui/resources/broker.gif", true);
    
	public String getBrokerAddress() {
		return brokerAddress;
	}

	public String getName() {
		return name;
	}

	String brokerAddress;
	String name;

    public BrokerProxyDataSource(Application app, MQDataSource master,
			String brokerAddress, String name) {
        super(master);
        application = app;
		this.name = name;
		this.brokerAddress = brokerAddress;
        this.descriptor = new MQResourceDescriptor(this, name, ResourceType.BROKER_PROXY, null, NODE_ICON);
    }
    
	public void connectToClusteredBroker() {
		
		// connect to port mapper and obtain JMX URL
		String brokerAddress = this.getBrokerAddress();
		String jmxURL = ClusterAccessUtils.getBrokerJMXURL(brokerAddress);
				
		// open it
		JmxApplicationsSupport jmxApplicationsSupport = JmxApplicationsSupport.getInstance();
		try {
			Application newApplication=jmxApplicationsSupport.createJmxApplication(jmxURL,brokerAddress , "", "");
		} catch (JmxApplicationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

}
