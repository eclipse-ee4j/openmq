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
 * @(#)DestinationNotification.java	1.6 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

import javax.management.Notification;

/**
 * Class containing information on consumer notifications.
 *
 * <P>
 * The MQ specific fields in this notification is TBD.
 */
public class DestinationNotification extends MQNotification  {
    /** 
     * A destination was compacted.
     */
    public static final String		DESTINATION_COMPACT = MQNotification.PREFIX + "destination.compact";

    /** 
     * A destination was created.
     */
    public static final String		DESTINATION_CREATE = MQNotification.PREFIX + "destination.create";

    /** 
     * A destination was destroyed.
     */
    public static final String		DESTINATION_DESTROY = MQNotification.PREFIX + "destination.destroy";

    /** 
     * A destination was paused.
     */
    public static final String		DESTINATION_PAUSE = MQNotification.PREFIX + "destination.pause";

    /** 
     * A destination was purged.
     */
    public static final String		DESTINATION_PURGE = MQNotification.PREFIX + "destination.purge";

    /** 
     * A destination was resumed.
     */
    public static final String		DESTINATION_RESUME = MQNotification.PREFIX + "destination.resume";

    private String		destName;
    private String		destType;
    private String		pauseType;
    private boolean		createdByAdmin;

    /**
     * Creates a DestinationNotification object.
     *
     * @param type		The notification type.
     * @param source		The notification source.
     * @param sequenceNumber	The notification sequence number within the source object.
     */
    public DestinationNotification(String type, Object source, long sequenceNumber) {
	super(type, source, sequenceNumber);
    }

    public void setDestinationName(String name)  {
	destName = name;
    }
    public String getDestinationName()  {
	return(destName);
    }

    public void setDestinationType(String type)  {
	destType = type;
    }
    public String getDestinationType()  {
	return(destType);
    }

    public void setPauseType(String pauseType)  {
	this.pauseType = pauseType;
    }
    public String getPauseType()  {
	return(pauseType);
    }

    public void setCreatedByAdmin(boolean createdByAdmin)  {
	this.createdByAdmin = createdByAdmin;
    }
    public boolean getCreatedByAdmin()  {
	return(createdByAdmin);
    }

}
