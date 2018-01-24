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
 * @(#)LogNotification.java	1.7 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

import javax.management.Notification;

/**
 * Class containing information on log related notifications.
 * Log Notifications are sent when an entry in the broker log is made.
 */
public class LogNotification extends MQNotification  {
    public static final String		LOG_LEVEL_PREFIX = MQNotification.PREFIX 
						+ "log.level.";

    public static final String		LOG_LEVEL_WARNING = LOG_LEVEL_PREFIX
						+ LogLevel.WARNING;

    public static final String		LOG_LEVEL_ERROR = LOG_LEVEL_PREFIX
						+ LogLevel.ERROR;

    public static final String		LOG_LEVEL_INFO = LOG_LEVEL_PREFIX
						+ LogLevel.INFO;

    private String message;
    private String level;

    /**
     * Creates a LogNotification object.
     *
     * @param type		The notification type.
     * @param source		The notification source.
     * @param sequenceNumber	The notification sequence number within the source object.
     */
    public LogNotification(String type, Object source, long sequenceNumber) {
	super(type, source, sequenceNumber);
    }

    /**
     * Sets the message related to this log notification.
     *
     * @param msg The log message for this notification.
     */
    public void setMessage(String msg)  {
	this.message = msg;
    }
    /**
     * Returns message related to this log notification.
     *
     * @return The log message for this notification.
     */
    public String getMessage()  {
	return (message);
    }

    /**
     * Sets the log level related to this log notification.
     *
     * @param level The log level for this notification.
     */
    public void setLevel(String level)  {
	this.level = level;
    }
    /**
     * Returns the log level related to this log notification.
     *
     * @return The log level for this notification.
     */
    public String getLevel()  {
	return (level);
    }
}
