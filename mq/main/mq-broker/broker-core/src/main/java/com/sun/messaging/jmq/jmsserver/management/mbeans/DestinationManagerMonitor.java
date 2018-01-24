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
 * @(#)DestinationManagerMonitor.java	1.14 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.List;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanException;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.Queue;
import com.sun.messaging.jmq.jmsserver.management.util.DestinationUtil;

public class DestinationManagerMonitor extends MQMBeanReadOnly  {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(DestinationAttributes.NUM_DESTINATIONS,
					Integer.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_NUM_DESTINATIONS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_MSGS,
					Long.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_NUM_MSGS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_MSGS_IN_DMQ,
					Long.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_NUM_MSGS_IN_DMQ),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.TOTAL_MSG_BYTES,
					Long.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_TOTAL_MSG_BYTES),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.TOTAL_MSG_BYTES_IN_DMQ,
					Long.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_TOTAL_MSG_BYTES_IN_DMQ),
					true,
					false,
					false)
			};

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(DestinationOperations.GET_DESTINATIONS,
	        mbr.getString(mbr.I_DST_MGR_MON_OP_GET_DESTINATIONS),
		null , 
		ObjectName[].class.getName(),
		MBeanOperationInfo.INFO)
		    };
	
    private static String[] dstNotificationTypes = {
		    DestinationNotification.DESTINATION_COMPACT,
		    DestinationNotification.DESTINATION_CREATE,
		    DestinationNotification.DESTINATION_DESTROY,
		    DestinationNotification.DESTINATION_PAUSE,
		    DestinationNotification.DESTINATION_PURGE,
		    DestinationNotification.DESTINATION_RESUME
		};

    private static MBeanNotificationInfo[] notifs = {
	    new MBeanNotificationInfo(
		    dstNotificationTypes,
		    DestinationNotification.class.getName(),
		    mbr.getString(mbr.I_DST_NOTIFICATIONS)
		    )
		};


    public DestinationManagerMonitor()  {
        super();
    }

    public Integer getNumDestinations()  {
	List l = DestinationUtil.getVisibleDestinations();

	return (Integer.valueOf(l.size()));
    }

    public Long getNumMsgs()  {
	return (Long.valueOf(DL.totalCount()));
    }

    public Long getNumMsgsInDMQ()  {
        Queue[] qs = DL.getDMQ(null);
        Queue dmq = qs[0]; //PART
	return (Long.valueOf(dmq.size()));
    }

    public Long getTotalMsgBytes()  {
	return (Long.valueOf(DL.totalBytes()));
    }

    public Long getTotalMsgBytesInDMQ()  {
        Queue[] qs = DL.getDMQ(null);
        Queue dmq = qs[0]; //PART
	return (Long.valueOf(dmq.byteSize()));
    }

    public ObjectName[] getDestinations() throws MBeanException  {
	List dests = DestinationUtil.getVisibleDestinations();

	if (dests.size() == 0)  {
	    return (null);
	}

	ObjectName destONames[] = new ObjectName [ dests.size() ];

	for (int i =0; i < dests.size(); i ++) {
	    Destination d = (Destination)dests.get(i);

	    try  {
	        ObjectName o = MQObjectName.createDestinationMonitor(
				d.isQueue() ? DestinationType.QUEUE : DestinationType.TOPIC,
				d.getDestinationName());

	        destONames[i] = o;
	    } catch (Exception e)  {
		handleOperationException(DestinationOperations.GET_DESTINATIONS, e);
	    }
        }

	return (destONames);
    }

    public String getMBeanName()  {
	return ("DestinationManagerMonitor");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_DST_MGR_MON_DESC));
    }

    public MBeanAttributeInfo[] getMBeanAttributeInfo()  {
	return (attrs);
    }

    public MBeanOperationInfo[] getMBeanOperationInfo()  {
	return (ops);
    }

    public MBeanNotificationInfo[] getMBeanNotificationInfo()  {
	return (notifs);
    }

    public void notifyDestinationCompact(Destination d)  {
	DestinationNotification n;
	n = new DestinationNotification(
			DestinationNotification.DESTINATION_COMPACT, 
			this, sequenceNumber++);

	n.setDestinationName(d.getDestinationName());
	n.setDestinationType(d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC);

	sendNotification(n);
    }

    public void notifyDestinationCreate(Destination d)  {
	DestinationNotification n;
	n = new DestinationNotification(
			DestinationNotification.DESTINATION_CREATE, 
			this, sequenceNumber++);

	boolean b = !(d.isAutoCreated() || d.isInternal() || d.isDMQ() || d.isAdmin());

	n.setDestinationName(d.getDestinationName());
	n.setDestinationType(d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC);
	n.setCreatedByAdmin(b);

	sendNotification(n);
    }

    public void notifyDestinationDestroy(Destination d)  {
	DestinationNotification n;
	n = new DestinationNotification(
			DestinationNotification.DESTINATION_DESTROY, 
			this, sequenceNumber++);
	n.setDestinationName(d.getDestinationName());
	n.setDestinationType(d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC);

	sendNotification(n);
    }

    public void notifyDestinationPause(Destination d, String pauseType)  {
	DestinationNotification n;
	n = new DestinationNotification(
			DestinationNotification.DESTINATION_PAUSE, 
			this, sequenceNumber++);
	n.setDestinationName(d.getDestinationName());
	n.setDestinationType(d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC);
	n.setPauseType(pauseType);

	sendNotification(n);
    }

    public void notifyDestinationPurge(Destination d)  {
	DestinationNotification n;
	n = new DestinationNotification(
			DestinationNotification.DESTINATION_PURGE, 
			this, sequenceNumber++);
	n.setDestinationName(d.getDestinationName());
	n.setDestinationType(d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC);

	sendNotification(n);
    }

    public void notifyDestinationResume(Destination d)  {
	DestinationNotification n;
	n = new DestinationNotification(
			DestinationNotification.DESTINATION_RESUME,
			this, sequenceNumber++);
	n.setDestinationName(d.getDestinationName());
	n.setDestinationType(d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC);

	sendNotification(n);
    }


}
