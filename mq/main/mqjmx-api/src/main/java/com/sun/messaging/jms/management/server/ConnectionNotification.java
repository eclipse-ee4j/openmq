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

/*
 * @(#)ConnectionNotification.java	1.9 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

import javax.management.Notification;

/**
 * Class containing information on cluster operations.
 *
 * <P>
 * The MQ specific fields in this notification is TBD.
 */
public class ConnectionNotification extends MQNotification  {
    /** 
     * A connection was created.
     */
    public static final String		CONNECTION_OPEN = MQNotification.PREFIX + "connection.open";

    /** 
     * A connection was closed.
     */
    public static final String		CONNECTION_CLOSE = MQNotification.PREFIX + "connection.close";

    /** 
     * A connection was rejected.
     */
    public static final String		CONNECTION_REJECT = MQNotification.PREFIX + "connection.reject";

    private String id;
    private String serviceName, userName, remoteHost;

    
    /**
     * Creates a ConnectionNotification object.
     *
     * @param type		The notification type.
     * @param source		The notification source.
     * @param sequenceNumber	The notification sequence number within the source object.
     */
    public ConnectionNotification(String type, Object source, long sequenceNumber) {
	super(type, source, sequenceNumber);
    }

    public void setConnectionID(String id)  {
	this.id = id;
    }
    public String getConnectionID()  {
	return(id);
    }

    public void setServiceName(String serviceName)  {
	this.serviceName = serviceName;
    }
    public String getServiceName()  {
	return(serviceName);
    }

    public void setUserName(String userName)  {
	this.userName = userName;
    }
    public String getUserName()  {
	return(userName);
    }

    public void setRemoteHost(String remoteHost)  {
	this.remoteHost = remoteHost;
    }
    public String getRemoteHost()  {
	return(remoteHost);
    }
    
}
