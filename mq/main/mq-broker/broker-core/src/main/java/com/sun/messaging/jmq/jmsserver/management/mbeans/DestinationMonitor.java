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
 * @(#)DestinationMonitor.java	1.25 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.Iterator;
import java.util.ArrayList;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanException;

import com.sun.messaging.jmq.util.DestMetricsCounters;
import com.sun.messaging.jmq.jmsserver.management.util.DestinationUtil;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.plugin.spi.ProducerSpi;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.admin.DestinationInfo;

import com.sun.messaging.jms.management.server.*;

public class DestinationMonitor extends MQMBeanReadOnly  {
    private Destination d = null;

    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(DestinationAttributes.AVG_NUM_ACTIVE_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_AVG_NUM_ACTIVE_CONSUMERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.AVG_NUM_BACKUP_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_AVG_NUM_BACKUP_CONSUMERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.AVG_NUM_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_AVG_NUM_CONSUMERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.AVG_NUM_MSGS,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_AVG_NUM_MSGS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.AVG_TOTAL_MSG_BYTES,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_AVG_TOTAL_MSG_BYTES),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.CONNECTION_ID,
					String.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_CONNECTION_ID),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.CREATED_BY_ADMIN,
					Boolean.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_CREATED_BY_ADMIN),
					true,
					false,
					true),

	    new MBeanAttributeInfo(DestinationAttributes.DISK_RESERVED,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_DISK_RESERVED),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.DISK_USED,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_DISK_USED),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.DISK_UTILIZATION_RATIO,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_DISK_UTILIZATION_RATIO),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.MSG_BYTES_IN,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_MSG_BYTES_IN),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.MSG_BYTES_OUT,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_MSG_BYTES_OUT),
					true,
					false,
					false),
        
	    new MBeanAttributeInfo(DestinationAttributes.NAME,
					String.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NAME),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_ACTIVE_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_ACTIVE_CONSUMERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_BACKUP_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_BACKUP_CONSUMERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_CONSUMERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_WILDCARDS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_WILDCARDS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_WILDCARD_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_WILDCARD_CONSUMERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_WILDCARD_PRODUCERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_WILDCARD_PRODUCERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_MSGS,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_MSGS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_MSGS_REMOTE,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_MSGS_REMOTE),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_MSGS_HELD_IN_TRANSACTION,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_MSGS_HELD_IN_TRANSACTION),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_MSGS_IN,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_MSGS_IN),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_MSGS_OUT,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_MSGS_OUT),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_MSGS_PENDING_ACKS,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_MSGS_PENDING_ACKS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_MSGS_IN_DELAY_DELIVERY,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_MSGS_IN_DELAY_DELIVERY),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_PRODUCERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NUM_PRODUCERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.PEAK_MSG_BYTES,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_PEAK_MSG_BYTES),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.PEAK_NUM_ACTIVE_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_PEAK_NUM_ACTIVE_CONSUMERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.PEAK_NUM_BACKUP_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_PEAK_NUM_BACKUP_CONSUMERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.PEAK_NUM_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_PEAK_NUM_CONSUMERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.PEAK_NUM_MSGS,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_PEAK_NUM_MSGS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.PEAK_TOTAL_MSG_BYTES,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_PEAK_TOTAL_MSG_BYTES),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NEXT_MESSAGE_ID,
					String.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NEXT_MESSAGE_ID),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.STATE,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_STATE),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.STATE_LABEL,
					String.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_STATE_LABEL),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.TEMPORARY,
					Boolean.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_TEMPORARY),
					true,
					false,
					true),

	    new MBeanAttributeInfo(DestinationAttributes.TOTAL_MSG_BYTES,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_TOTAL_MSG_BYTES),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.TOTAL_MSG_BYTES_REMOTE,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_TOTAL_MSG_BYTES_REMOTE),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.TOTAL_MSG_BYTES_HELD_IN_TRANSACTION,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_TOTAL_MSG_BYTES_HELD_IN_TRANSACTION),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.TYPE,
					String.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_TYPE),
					true,
					false,
					false)
			};

    private static MBeanParameterInfo[] numWildcardConsumersSignature = {
	    new MBeanParameterInfo("wildcard", String.class.getName(), 
		        mbr.getString(mbr.I_BKR_OP_WILDCARD_CONSUMERS_DESC))
    		};

    private static MBeanParameterInfo[] numWildcardProducersSignature = {
	    new MBeanParameterInfo("wildcard", String.class.getName(), 
		        mbr.getString(mbr.I_BKR_OP_WILDCARD_PRODUCERS_DESC))
    		};

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(DestinationOperations.GET_ACTIVE_CONSUMER_IDS,
		mbr.getString(mbr.I_DST_OP_GET_ACTIVE_CONSUMER_IDS),
		null , 
		String[].class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(DestinationOperations.GET_BACKUP_CONSUMER_IDS,
		mbr.getString(mbr.I_DST_OP_GET_BACKUP_CONSUMER_IDS),
		null , 
		String[].class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(DestinationOperations.GET_CONNECTION,
		mbr.getString(mbr.I_DST_OP_GET_CONNECTION),
		null , 
		ObjectName.class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(DestinationOperations.GET_CONSUMER_IDS,
		mbr.getString(mbr.I_DST_OP_GET_CONSUMER_IDS),
		null , 
		String[].class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(DestinationOperations.GET_PRODUCER_IDS,
		mbr.getString(mbr.I_DST_OP_GET_PRODUCER_IDS),
		null , 
		String[].class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(DestinationOperations.GET_WILDCARDS,
		mbr.getString(mbr.I_DST_OP_GET_WILDCARDS),
		null , 
		String[].class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(DestinationOperations.GET_CONSUMER_WILDCARDS,
		mbr.getString(mbr.I_DST_OP_GET_CONSUMER_WILDCARDS),
		null , 
		String[].class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(DestinationOperations.GET_NUM_WILDCARD_CONSUMERS,
		mbr.getString(mbr.I_DST_OP_GET_NUM_WILDCARD_CONSUMERS),
		numWildcardConsumersSignature , 
		Integer.class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(DestinationOperations.GET_PRODUCER_WILDCARDS,
		mbr.getString(mbr.I_DST_OP_GET_PRODUCER_WILDCARDS),
		null , 
		String[].class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(DestinationOperations.GET_NUM_WILDCARD_PRODUCERS,
		mbr.getString(mbr.I_DST_OP_GET_NUM_WILDCARD_PRODUCERS),
		numWildcardProducersSignature , 
		Integer.class.getName(),
		MBeanOperationInfo.INFO)
		    };
	
    private static String[] dstNotificationTypes = {
		    DestinationNotification.DESTINATION_COMPACT,
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

    public DestinationMonitor(Destination dest) {
	d = dest;
    }

    public Integer getAvgNumActiveConsumers()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Integer.valueOf(dmc.getAvgActiveConsumers()));
    }

    public Integer getAvgNumBackupConsumers()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Integer.valueOf(dmc.getAvgFailoverConsumers()));
    }

    public Integer getAvgNumConsumers()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Integer.valueOf(dmc.getAvgActiveConsumers()));
    }

    public Long getAvgNumMsgs()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Long.valueOf(dmc.getAverageMessages()));
    }

    public Long getAvgTotalMsgBytes()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Long.valueOf(dmc.getAverageMessageBytes()));
    }

    public String getConnectionID()  {
	ConnectionUID cxnId;

	if (!isTemporary().booleanValue())  {
	    return (null);
	}

	cxnId = d.getConnectionUID();

	if (cxnId == null)  {
	    return (null);
	}

	return (Long.toString(cxnId.longValue()));
    }

    public Boolean isCreatedByAdmin()  {
	boolean b = !(d.isAutoCreated() || d.isInternal() || d.isDMQ() || d.isAdmin());

	return (Boolean.valueOf(b));
    }

    public Boolean getCreatedByAdmin()  {
	return (isCreatedByAdmin());
    }

    public Long getDiskReserved()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Long.valueOf(dmc.getDiskReserved()));
    }

    public Long getDiskUsed()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Long.valueOf(dmc.getDiskUsed()));
    }

    public Integer getDiskUtilizationRatio()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Integer.valueOf(dmc.getDiskUtilizationRatio()));
    }

    public Long getMsgBytesIn()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Long.valueOf(dmc.getMessageBytesIn()));
    }

    public Long getMsgBytesOut()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Long.valueOf(dmc.getMessageBytesOut()));
    }

    public String getName()  {
	return (d.getDestinationName());
    }

    public Integer getNumActiveConsumers()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Integer.valueOf(dmc.getActiveConsumers()));
    }

    public Integer getNumBackupConsumers()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Integer.valueOf(dmc.getFailoverConsumers()));
    }

    public Integer getNumConsumers()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Integer.valueOf(dmc.getNumConsumers()));
    }

    public Integer getNumWildcards() throws MBeanException  {
	int numConsumers = getNumConsumers().intValue();
	int numProducers = getNumProducers().intValue();
	int count = 0;

	if (numConsumers > 0)  {
	    Iterator consumers = d.getConsumers();
	    while (consumers.hasNext()) {
	        Consumer oneCon = (Consumer)consumers.next();

	        if (oneCon.isWildcard())  {
		    ++count;
	        }
	    }
	}

	if (numProducers > 0)  {
	    Iterator producers = d.getProducers();

	    while (producers.hasNext()) {
	        ProducerSpi oneProd = (ProducerSpi)producers.next();
    
	        if (oneProd.isWildcard())  {
		    ++count;
	        }
	    }
	}

	return (Integer.valueOf(count));
    }

    public Integer getNumWildcardConsumers() throws MBeanException  {
	/*
	int numConsumers = getNumConsumers().intValue();
	Iterator consumers;

	if (numConsumers <= 0)  {
	    return (new Integer(0));
	}

	consumers = d.getConsumers();

	int count = 0;
	while (consumers.hasNext()) {
	    Consumer oneCon = (Consumer)consumers.next();

	    if (oneCon.isWildcard())  {
		++count;
	    }
	}

	return (new Integer(count));
	*/

        return(getNumWildcardConsumers(null));
    }

    public Integer getNumWildcardConsumers(String wildcard) throws MBeanException  {
	int numConsumers = getNumConsumers().intValue();

	if (numConsumers <= 0)  {
	    return (Integer.valueOf(0));
	}

	Iterator consumers = d.getConsumers();

	if (consumers == null)  {
	    return (Integer.valueOf(0));
	}

	int count = 0;
	while (consumers.hasNext()) {
	    Consumer oneCon = (Consumer)consumers.next();

	    if (oneCon.isWildcard())  {
		/*
		 * If wildcard param is not null, check for matches
		 * If it is null, return total count of wildcards
		 */
		if (wildcard != null)  {
		    DestinationUID id = oneCon.getDestinationUID();
		    if (id.getName().equals(wildcard))  {
		        count++;
		    }
		} else  {
		    count++;
		}
	    }
	}

        return (Integer.valueOf(count));
    }


    public Integer getNumWildcardProducers() throws MBeanException  {
	/*
	int numProducers = getNumProducers().intValue();
	Iterator producers;

	if (numProducers <= 0)  {
	    return (new Integer(0));
	}

	producers = d.getProducers();

	int count = 0;
	while (producers.hasNext()) {
	    Producer oneProd = (Producer)producers.next();

	    if (oneProd.isWildcard())  {
		++count;
	    }
	}

	return (new Integer(count));
	*/

        return(getNumWildcardProducers(null));
    }

    public Integer getNumWildcardProducers(String wildcard) throws MBeanException  {
	int numProducers = getNumProducers().intValue();

	if (numProducers <= 0)  {
	    return (Integer.valueOf(0));
	}

	Iterator producers = d.getProducers();

	if (producers == null)  {
	    return (Integer.valueOf(0));
	}

	int count = 0;
	while (producers.hasNext()) {
	    ProducerSpi oneProd = (ProducerSpi)producers.next();

	    if (oneProd.isWildcard())  {
		/*
		 * If wildcard param is not null, check for matches
		 * If it is null, return total count of wildcards
		 */
		if (wildcard != null)  {
		    DestinationUID id = oneProd.getDestinationUID();
		    if (id.getName().equals(wildcard))  {
		        count++;
		    }
		} else  {
		    count++;
		}
	    }
	}

        return (Integer.valueOf(count));
    }


    public String[] getWildcards() throws MBeanException  {
	ArrayList<String> al = new ArrayList<String>();
	String[] list = null;
	int numConsumers = getNumConsumers().intValue(),
	    numProducers = getNumProducers().intValue();

	if (numConsumers > 0)  {
	    Iterator consumers = d.getConsumers();

	    while (consumers.hasNext()) {
	        Consumer oneCon = (Consumer)consumers.next();
    
	        if (oneCon.isWildcard())  {
		    DestinationUID id = oneCon.getDestinationUID();
	            al.add(id.getName());
	        }
	    }
	}

	if (numProducers > 0)  {
	    Iterator producers = d.getProducers();

	    while (producers.hasNext()) {
	        ProducerSpi oneProd = (ProducerSpi)producers.next();
    
	        if (oneProd.isWildcard())  {
		    DestinationUID id = oneProd.getDestinationUID();
	            al.add(id.getName());
	        }
	    }
	}

	if (al.size() > 0)  {
	    list = new String [ al.size() ];
	    list = (String[])al.toArray(list);
	}

        return (list);
    }

    public String[] getConsumerWildcards() throws MBeanException  {
	ArrayList<String> al = new ArrayList<String>();
	String[] list = null;
	int numConsumers = getNumConsumers().intValue();
	Iterator consumers;

	if (numConsumers <= 0)  {
	    return (null);
	}

	consumers = d.getConsumers();

	while (consumers.hasNext()) {
	    Consumer oneCon = (Consumer)consumers.next();

	    if (oneCon.isWildcard())  {
		DestinationUID id = oneCon.getDestinationUID();
	        al.add(id.getName());
	    }
	}

	if (al.size() > 0)  {
	    list = new String [ al.size() ];
	    list = (String[])al.toArray(list);
	}

        return (list);
    }


    public String[] getProducerWildcards() throws MBeanException  {
	ArrayList<String> al = new ArrayList<String>();
	String[] list = null;
	int numProducers = getNumProducers().intValue();
	Iterator producers;

	if (numProducers <= 0)  {
	    return (null);
	}

	producers = d.getProducers();

	while (producers.hasNext()) {
	    ProducerSpi oneProd = (ProducerSpi)producers.next();

	    if (oneProd.isWildcard())  {
		DestinationUID id = oneProd.getDestinationUID();
	        al.add(id.getName());
	    }
	}

	if (al.size() > 0)  {
	    list = new String [ al.size() ];
	    list = (String[])al.toArray(list);
	}

        return (list);
    }



    public Long getNumMsgs()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	if (di == null)  {
	    return (null);
	}

	return (Long.valueOf(di.nMessages - di.nTxnMessages));
    }

    public Long getNumMsgsRemote()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	if (di == null)  {
	    return (null);
	}

	return (Long.valueOf(di.nRemoteMessages));
    }

    public Long getNumMsgsHeldInTransaction()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	if (di == null)  {
	    return (null);
	}

	return (Long.valueOf(di.nTxnMessages));
    }

    public Long getNumMsgsIn()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Long.valueOf(dmc.getMessagesIn()));
    }

    public Long getNumMsgsOut()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Long.valueOf(dmc.getMessagesOut()));
    }

    public Long getNumMsgsPendingAcks()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	if (di == null)  {
	    return (null);
	}

	return (Long.valueOf(di.nUnackMessages));
    }

    public Long getNumMsgsInDelayDelivery()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	if (di == null)  {
	    return (null);
	}

	return (Long.valueOf(di.nInDelayMessages));
    }

    public Integer getNumProducers()  {
	return (Integer.valueOf(d.getProducerCount()));
    }

    public Long getPeakMsgBytes()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Long.valueOf(dmc.getHighWaterLargestMsgBytes()));
    }

    public Integer getPeakNumActiveConsumers()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Integer.valueOf(dmc.getHWActiveConsumers()));
    }

    public Integer getPeakNumBackupConsumers()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Integer.valueOf(dmc.getHWFailoverConsumers()));
    }

    public Integer getPeakNumConsumers()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Integer.valueOf(dmc.getHWActiveConsumers()));
    }

    public Long getPeakNumMsgs()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Long.valueOf(dmc.getHighWaterMessages()));
    }

    public Long getPeakTotalMsgBytes()  {
	DestMetricsCounters dmc = d.getMetrics();
	return (Long.valueOf(dmc.getHighWaterMessageBytes()));
    }

    public String getNextMessageID() {
        PacketReference ref = d.peekNext();
        if (ref != null) {
            return ref.getSysMessageID().toString();
        } else {
            return "";
       }
    }

    public Integer getState()  {
	return (Integer.valueOf(
	    DestinationUtil.toExternalDestState(d.getState())));
    }

    public String getStateLabel()  {
	return (DestinationState.toString(
	    DestinationUtil.toExternalDestState(d.getState())));
    }

    public Boolean isTemporary()  {
	return (Boolean.valueOf(d.isTemporary()));
    }
    public Boolean getTemporary()  {
	return (isTemporary());
    }

    public Long getTotalMsgBytes()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	if (di == null)  {
	    return (null);
	}

	return (Long.valueOf(di.nMessageBytes - di.nTxnMessageBytes));
    }

    public Long getTotalMsgBytesRemote()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	if (di == null)  {
	    return (null);
	}

	return (Long.valueOf(di.nRemoteMessageBytes));
    }

    public Long getTotalMsgBytesHeldInTransaction()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	if (di == null)  {
	    return (null);
	}

	return (Long.valueOf(di.nTxnMessageBytes));
    }

    public String getType()  {
	return (d.isQueue() ? 
		DestinationType.QUEUE : DestinationType.TOPIC);
    }

    public String[] getActiveConsumerIDs() throws MBeanException  {
	int numConsumers = getNumActiveConsumers().intValue();
	String ids[];
	Iterator consumers;

	if (numConsumers <= 0)  {
	    return (null);
	}

	consumers = d.getActiveConsumers().iterator();

	ids = new String [ numConsumers ];

	int i = 0;
	while (consumers.hasNext()) {
	    Consumer oneCon = (Consumer)consumers.next();
	    long conID = oneCon.getConsumerUID().longValue();
	    String id;

	    try  {
	        id = Long.toString(conID);

	        ids[i] = id;
	    } catch (Exception ex)  {
	        handleOperationException(DestinationOperations.GET_ACTIVE_CONSUMER_IDS, ex);
	    }

	    i++;
	}

	return (ids);
    }

    public String[] getBackupConsumerIDs() throws MBeanException  {
	int numConsumers = getNumBackupConsumers().intValue();
	String ids[];
	Iterator consumers;

	if (numConsumers <= 0)  {
	    return (null);
	}

	consumers = d.getFailoverConsumers().iterator();

	ids = new String [ numConsumers ];

	int i = 0;
	while (consumers.hasNext()) {
	    Consumer oneCon = (Consumer)consumers.next();
	    long conID = oneCon.getConsumerUID().longValue();
	    String id;

	    try  {
	        id = Long.toString(conID);

	        ids[i] = id;
	    } catch (Exception ex)  {
	        handleOperationException(DestinationOperations.GET_BACKUP_CONSUMER_IDS, ex);
	    }

	    i++;
	}

	return (ids);
    }

    public ObjectName getConnection() throws MBeanException  {
	ConnectionUID cxnId;
	ObjectName oName = null;

	if (!isTemporary().booleanValue())  {
	    return (null);
	}

	cxnId = d.getConnectionUID();

	if (cxnId == null)  {
	    return (null);
	}

	try  {
	    oName = MQObjectName.createConnectionMonitor(Long.toString(cxnId.longValue()));
	} catch (Exception e)  {
	    handleOperationException(DestinationOperations.GET_CONNECTION, e);
	}

	return (oName);
    }

    public String[] getConsumerIDs() throws MBeanException  {
	int numConsumers = getNumConsumers().intValue();
	String ids[];
	Iterator consumers;

	if (numConsumers <= 0)  {
	    return (null);
	}

	consumers = d.getConsumers();

	ids = new String [ numConsumers ];

	int i = 0;
	while (consumers.hasNext()) {
	    Consumer oneCon = (Consumer)consumers.next();
	    long conID = oneCon.getConsumerUID().longValue();
	    String id;

	    try  {
	        id = Long.toString(conID);

	        ids[i] = id;
	    } catch (Exception ex)  {
	        handleOperationException(DestinationOperations.GET_CONSUMER_IDS, ex);
	    }

	    i++;
	}

	return (ids);
    }

    public String[] getProducerIDs() throws MBeanException  {
	int numProducers = getNumProducers().intValue();
	String ids[];
	Iterator producers;

	if (numProducers <= 0)  {
	    return (null);
	}

	producers = d.getProducers();

	ids = new String [ numProducers ];

	int i = 0;
	while (producers.hasNext()) {
	    ProducerSpi oneProd = (ProducerSpi)producers.next();
	    long prodID = oneProd.getProducerUID().longValue();

	    try  {
	        ids[i] = Long.toString(prodID);
	    } catch (Exception ex)  {
	        handleOperationException(DestinationOperations.GET_PRODUCER_IDS, ex);
	    }

	    i++;
	}

	return (ids);
    }

    public String getMBeanName()  {
	return ("DestinationMonitor");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_DST_MON_DESC));
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

    public void notifyDestinationCompact()  {
	DestinationNotification n;
	n = new DestinationNotification(DestinationNotification.DESTINATION_COMPACT,
			this, sequenceNumber++);
	n.setDestinationName(getName());
	n.setDestinationType(getType());

	sendNotification(n);
    }

    public void notifyDestinationPause(String pauseType)  {
	DestinationNotification n;
	n = new DestinationNotification(
			DestinationNotification.DESTINATION_PAUSE, 
			this, sequenceNumber++);
	n.setDestinationName(getName());
	n.setDestinationType(getType());
	n.setPauseType(pauseType);

	sendNotification(n);
    }

    public void notifyDestinationPurge()  {
	DestinationNotification n;
	n = new DestinationNotification(DestinationNotification.DESTINATION_PURGE,
			this, sequenceNumber++);
	n.setDestinationName(getName());
	n.setDestinationType(getType());

	sendNotification(n);
    }

    public void notifyDestinationResume()  {
	DestinationNotification n;
	n = new DestinationNotification(DestinationNotification.DESTINATION_RESUME,
			this, sequenceNumber++);
	n.setDestinationName(getName());
	n.setDestinationType(getType());

	sendNotification(n);
    }
}
