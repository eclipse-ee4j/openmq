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
 * @(#)MQNotification.java	1.4 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

import javax.management.Notification;

/**
 * Class containing information on cluster operations.
 *
 * <P>
 * The MQ specific fields in this notification is TBD.
 */
public class MQNotification extends Notification  {
    /** 
     * Prefix for all MQ notification type strings.
     */
    public static final String		PREFIX = "mq.";

    /**
     * Creates a MQNotification object.
     *
     * @param type		The notification type.
     * @param source		The notification source.
     * @param sequenceNumber	The notification sequence number within the source object.
     */
    public MQNotification(String type, Object source, long sequenceNumber) {
	super(type, source, sequenceNumber);
    }
    
}
