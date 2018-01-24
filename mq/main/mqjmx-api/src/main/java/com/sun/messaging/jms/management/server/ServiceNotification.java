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
 * @(#)ServiceNotification.java	1.5 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

import javax.management.Notification;

/**
 * Class containing information on service notifications.
 *
 * <P>
 * The MQ specific fields in this notification is TBD.
 */
public class ServiceNotification extends MQNotification  {
    /** 
     * A service was paused.
     */
    public static final String		SERVICE_PAUSE = MQNotification.PREFIX + "service.pause";

    /** 
     * A service was resumed.
     */
    public static final String		SERVICE_RESUME = MQNotification.PREFIX + "service.resume";

    /*
     * Service name
     */
    private String name = null;

    
    /**
     * Creates a ServiceNotification object.
     *
     * @param type		The notification type.
     * @param source		The notification source.
     * @param sequenceNumber	The notification sequence number within the source object.
     */
    public ServiceNotification(String type, Object source, long sequenceNumber) {
	super(type, source, sequenceNumber);
    }

    public void setServiceName(String serviceName)  {
	name = serviceName;
    }
    public String getServiceName()  {
	return (name);
    }
    
}
