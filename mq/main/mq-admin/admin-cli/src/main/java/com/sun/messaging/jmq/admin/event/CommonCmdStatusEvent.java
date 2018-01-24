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
 * @(#)BrokerCmdStatusEvent.java	1.23 06/27/07
 */ 

package com.sun.messaging.jmq.admin.event;


/**
 * Event class indicating some common actions related to broker Management.
 * This class is subclassed by imqcmd (BrokerCmdStatusEvent), 
 *                             imqadmin (BrokerCmdStatusEvent)
 * and                         imqbridgemgr (BridgeCmdStatusEvent)
 *<P>
 */
public class CommonCmdStatusEvent extends AdminEvent {

    /********************************************************
     * CommonCmdStatusEvent event types
     * use integers >= 10000 to avoid overlap with subclasses
     ********************************************************/
    public final static int	BROKER_BUSY 		= 10000;

    private int			replyType;
    private String		replyTypeString;

    private boolean		success = true;

    private Exception		linkedException;

    private int			numRetriesAttempted = 0,
				maxNumRetries = 0;
    private long		retryTimeount = 0;

	private Object      returnedObject = null;

    /**
     * Creates an instance of CommonCmdStatusEvent
     * @param source the object where the event originated
     * @type the event type
     */
    public CommonCmdStatusEvent(Object source, int type) {
	super(source, type);
    }

    public void setSuccess(boolean b)  {
	success = b;
    }

    public boolean getSuccess()  {
	return (success);
    }

    public void setReturnedObject(Object obj) {
    this.returnedObject = obj;
    }
    public Object getReturnedObject() {
    return returnedObject;
    }

    public void setReplyType(int type)  {
	replyType = type;
    }
    public int getReplyType()  {
	return (replyType);
    }

    public void setReplyTypeString(String typeString)  {
	replyTypeString = typeString;
    }
    public String getReplyTypeString()  {
	return (replyTypeString);
    }

    public void setLinkedException(Exception e)  {
	linkedException = e;
    }
    public Exception getLinkedException()  {
	return (linkedException);
    }

    public void setNumRetriesAttempted(int numRetriesAttempted)  {
	this.numRetriesAttempted = numRetriesAttempted;
    }
    public int getNumRetriesAttempted()  {
	return (numRetriesAttempted);
    }

    public void setMaxNumRetries(int maxNumRetries)  {
	this.maxNumRetries = maxNumRetries;
    }
    public int getMaxNumRetries()  {
	return (maxNumRetries);
    }

    public void setRetryTimeount(long retryTimeount)  {
	this.retryTimeount = retryTimeount;
    }
    public long getRetryTimeount()  {
	return (retryTimeount);
    }
}
