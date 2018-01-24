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
 * @(#)ConsumerUtil.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.util;

import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.OpenDataException;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.util.net.IPAddress;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class ConsumerUtil {
    /*
     * Consumer Info composite type for Monitor MBeans
     */
    private static volatile CompositeType monitorCompType = null;

    public static int toExternalAckMode(int internalAckMode)  {
	switch (internalAckMode)  {
	case com.sun.messaging.jmq.jmsserver.core.Session.AUTO_ACKNOWLEDGE:
	    return (javax.jms.Session.AUTO_ACKNOWLEDGE);

	case com.sun.messaging.jmq.jmsserver.core.Session.CLIENT_ACKNOWLEDGE:
	    return (javax.jms.Session.CLIENT_ACKNOWLEDGE);

	case com.sun.messaging.jmq.jmsserver.core.Session.DUPS_OK_ACKNOWLEDGE:
	    return (javax.jms.Session.DUPS_OK_ACKNOWLEDGE);

	case com.sun.messaging.jmq.jmsserver.core.Session.NONE:
	    return (javax.jms.Session.SESSION_TRANSACTED);

	case com.sun.messaging.jmq.jmsserver.core.Session.NO_ACK_ACKNOWLEDGE:
	    return Session.NO_ACK_ACKNOWLEDGE;

	default:
	    return (-1);
	}
    }

    public static String toExternalAckModeString(int internalAckMode)  {
	switch (internalAckMode)  {
	case com.sun.messaging.jmq.jmsserver.core.Session.AUTO_ACKNOWLEDGE:
	    return ("AUTO_ACKNOWLEDGE");

	case com.sun.messaging.jmq.jmsserver.core.Session.CLIENT_ACKNOWLEDGE:
	    return ("CLIENT_ACKNOWLEDGE");

	case com.sun.messaging.jmq.jmsserver.core.Session.DUPS_OK_ACKNOWLEDGE:
	    return ("DUPS_OK_ACKNOWLEDGE");

	case com.sun.messaging.jmq.jmsserver.core.Session.NONE:
	    return ("SESSION_TRANSACTED");

	case com.sun.messaging.jmq.jmsserver.core.Session.NO_ACK_ACKNOWLEDGE:
	    return ("NO_ACKNOWLEDGE");

	default:
	    return ("UNKNOWN");
	}
    }

    public static int toInternalAckMode(int externalAckMode)  {
	switch (externalAckMode)  {
	case javax.jms.Session.AUTO_ACKNOWLEDGE:
	    return (com.sun.messaging.jmq.jmsserver.core.Session.AUTO_ACKNOWLEDGE);

	case javax.jms.Session.CLIENT_ACKNOWLEDGE:
	    return (com.sun.messaging.jmq.jmsserver.core.Session.CLIENT_ACKNOWLEDGE);

	case javax.jms.Session.DUPS_OK_ACKNOWLEDGE:
	    return (com.sun.messaging.jmq.jmsserver.core.Session.DUPS_OK_ACKNOWLEDGE);

	case javax.jms.Session.SESSION_TRANSACTED:
	    return (com.sun.messaging.jmq.jmsserver.core.Session.NONE);

	case Session.NO_ACK_ACKNOWLEDGE:
	    return Session.NO_ACK_ACKNOWLEDGE;

	default:
	    return (-1);
	}
    }

    public static HashMap getAllConsumersNoChildren() {
	Iterator it = Consumer.getAllConsumers();
	HashMap<ConsumerUID,Consumer> consumersNoChildren
				= new HashMap<ConsumerUID,Consumer>();

	while (it.hasNext()) {
	    Consumer oneCon = (Consumer)it.next();
	    ConsumerUID cid = oneCon.getConsumerUID();

	    if (oneCon.getSubscription() == null)  {
                consumersNoChildren.put(cid, oneCon);
	    }
	}

        return (consumersNoChildren);
    }

    public static int getNumConsumersNoChildren() {
        return (getAllConsumersNoChildren().size());
    }

    public static boolean isDurable(ConsumerUID cid)  {
	Consumer con = Consumer.getConsumer(cid);

	if (con != null)  {
	    return (con.isDurableSubscriber());
	}

	return (false);
    }

    public static boolean isWildcard(ConsumerUID cid)  {
	Consumer con = Consumer.getConsumer(cid);

	if (con != null)  {
	    return (con.isWildcard());
	}

	return (false);
    }

    public static String getNextMessageID(ConsumerUID cid) {
	Consumer con = Consumer.getConsumer(cid);

	if (con != null)  {
	    PacketReference r =  con.peekNext();
        if (r != null) 
                return r.getSysMessageID().toString();
	}
    return "";
    }

    public static boolean isDurableActive(ConsumerUID cid)  {
	if (!isDurable(cid))  {
	    return (false);
	}

	Consumer con = Consumer.getConsumer(cid);

	if (con != null)  {
	    return (con.isActive());
	}

	return (false);

    }

    public static ConnectionUID getConnectionUID(ConsumerUID cid)  {
	Consumer con = Consumer.getConsumer(cid);
	ConnectionUID cxnId = null;

	if (isDurable(cid))  {
	    if (!isDurableActive(cid))  {
		/*
		 * Return null if this is an inactive durable
		 */
		return (null);
	    }
	}

	/*
	if (con instanceof Subscription)  {
	    List l = ((Subscription)con).getChildConsumers();
	    Consumer c = (Consumer)l.get(0);
	    System.err.println("Consumer from child list: " + c);

	    cid = c.getConnectionUID();
	} else  {
	    cid = con.getConnectionUID();
	}
	*/

	if (con != null)  {
	    cxnId = con.getConnectionUID();
	}

	return (cxnId);
    }

    public static Destination getDestination(ConsumerUID cid)  {
        Consumer con = Consumer.getConsumer(cid);
        Destination d = null;

	if (con != null)  {
            d = con.getFirstDestination();
	}

	return (d);
    }

    public static DestinationUID getDestinationUID(ConsumerUID cid)  {
        Consumer con = Consumer.getConsumer(cid);
        DestinationUID id = null;

	if (con != null)  {
            id = con.getDestinationUID();
	}

	return (id);
    }

    public static Long getCreationTime(ConsumerUID cid)  {
	long currentTime = System.currentTimeMillis();

	return (Long.valueOf(currentTime - cid.age(currentTime)));
    }

    public static String[] getConsumerIDs()  {
	int numConsumers = 0;
	String ids[];
	Iterator consumers;
	HashSet hs;

	hs = new HashSet(getAllConsumersNoChildren().values());
	numConsumers = hs.size();

	if (numConsumers <= 0)  {
	    return (null);
	}

	ids = new String [ numConsumers ];

	consumers = hs.iterator();

	int i = 0;
	while (consumers.hasNext()) {
	    Consumer oneCon = (Consumer)consumers.next();
	    long conID = oneCon.getConsumerUID().longValue();
	    String id;

	    id = Long.toString(conID);

	    ids[i] = id;

	    i++;
	}

	return (ids);
    }

    public static CompositeData[] getConsumerInfo()
				throws BrokerException, OpenDataException  {
	String[] ids = getConsumerIDs();

	if (ids == null)  {
	    return (null);
	}

	CompositeData cds[] = new CompositeData [ ids.length ];

	for (int i = 0; i < ids.length; ++i)  {
	    cds[i] = getConsumerInfo(ids[i]);
	}
	
	return (cds);
    }

    public static CompositeData getConsumerInfo(String consumerID) 
				throws BrokerException, OpenDataException  {
	CompositeData cd = null;
	ConsumerUID tmpcid, cid = null;
        BrokerResources	rb = Globals.getBrokerResources();

	if (consumerID == null)  {
	    throw new 
		IllegalArgumentException(rb.getString(rb.X_JMX_NULL_CONSUMER_ID_SPEC));
	}

	long longCid = 0;

	try  {
	    longCid = Long.parseLong(consumerID);
	} catch (NumberFormatException e)  {
	    throw new 
		IllegalArgumentException(rb.getString(rb.X_JMX_INVALID_CONSUMER_ID_SPEC, consumerID));
	}

	tmpcid = new ConsumerUID(longCid);

	Consumer con = Consumer.getConsumer(tmpcid);

	if (con == null)  {
	    throw new BrokerException(rb.getString(rb.X_JMX_CONSUMER_NOT_FOUND, consumerID));
	}
    con.load(); // triggers the broker to load messages if necessary

	cid = con.getConsumerUID();

	if (cid == null)  {
	    throw new BrokerException(rb.getString(rb.X_JMX_CONSUMER_NOT_FOUND, consumerID));
	}

	cd = getConsumerInfo(cid);

	return (cd);
    }

    private static Integer getAcknowledgeMode(ConsumerUID cid)  {
	/*
	 * Note: May need to convert to javax.jms.Session constant values just
	 * in case.
	 */
        return (Integer.valueOf(toExternalAckMode(cid.getAckType())));
    }

    private static String getAcknowledgeModeLabel(ConsumerUID cid)  {
        return (toExternalAckModeString(cid.getAckType()));
    }

    private static String getClientID(ConsumerUID cid)  {
	Consumer con = Consumer.getConsumer(cid);

	if (con == null)  {
	    return (null);
	}

        return (con.getClientID());
    }

    private static String getConnectionID(ConsumerUID cid)  {
	ConnectionUID cxnId = getConnectionUID(cid);

	if (cxnId == null)  {
	    return (null);
	}

	return(Long.toString(cxnId.longValue()));
    }

    private static String[] getDestinationNames(ConsumerUID cid)  {
        Consumer con = Consumer.getConsumer(cid);
	String[] ret = null;

	if (con == null)  {
	    return (null);
	}

	ArrayList<String> al = new ArrayList<String>();
	Set dests = con.getUniqueDestinations();
    if (dests == null) return null;
	Iterator itr = dests.iterator();
	while (itr.hasNext()) {
	    Destination dest = (Destination)itr.next();
	    al.add(dest.getDestinationName());
	}

	if (al.size() > 0)  {
	    ret = new String [ al.size() ];
	    ret = (String[])al.toArray(ret);
	}

	return (ret);

    }

    private static String getDestinationName(ConsumerUID cid)  {
	/*
	Destination d = ConsumerUtil.getDestination(cid);

	if (d == null)  {
	    return (null);
	}

	return(d.getDestinationName());
	*/
	DestinationUID id = ConsumerUtil.getDestinationUID(cid);

	if (id == null)  {
	    return (null);
	}

	return(id.getName());
    }

    private static String getDestinationType(ConsumerUID cid)  {
	Destination d = ConsumerUtil.getDestination(cid);

	if (d == null)  {
	    return (null);
	}

	return(d.isQueue() ? DestinationType.QUEUE : DestinationType.TOPIC);
    }

    private static Boolean getWildcard(ConsumerUID cid)  {
        return (Boolean.valueOf(isWildcard(cid)));
    }

    private static Boolean getDurable(ConsumerUID cid)  {
        return (Boolean.valueOf(isDurable(cid)));
    }

    private static Boolean getDurableActive(ConsumerUID cid)  {
	if (!getDurable(cid).booleanValue())  {
	    return (null);
	}

        return (Boolean.valueOf(isDurableActive(cid)));
    }

    private static String getDurableName(ConsumerUID cid)  {
	Consumer con;
	Subscription sub;

	if (!getDurable(cid).booleanValue())  {
	    return (null);
	}

	con = Consumer.getConsumer(cid);
	if (con == null)  {
	    return (null);
	}

	if (con instanceof Subscription)  {
            return (((Subscription)con).getDurableName());
	} else  {
	    sub = con.getSubscription();

	    if (sub != null)  {
                return (sub.getDurableName());
	    } else  {
                return (null);
	    }
	}
    }

    private static Boolean getFlowPaused(ConsumerUID cid)  {
	Consumer con = Consumer.getConsumer(cid);

	if (con == null)  {
	    return (null);
	}

        return (Boolean.valueOf(con.getIsFlowPaused()));
    }

    private static String getHost(ConsumerUID cid)  {
	ConnectionUID cxnId = getConnectionUID(cid);

	if (cxnId == null)  {
	    return (null);
	}

	ConnectionInfo cxnInfo = ConnectionUtil.getConnectionInfo(cxnId.longValue());

	if (cxnInfo == null)  {
	    return (null);
	}

	String host = null;

	if (cxnInfo.remoteIP != null) {
            host = String.valueOf(
		IPAddress.rawIPToString(cxnInfo.remoteIP, true, true));
        }

	return (host);
    }

    private static Long getLastAckTime(ConsumerUID cid)  {
	Consumer con = Consumer.getConsumer(cid);

	if (con == null)  {
	    return (null);
	}

	return (Long.valueOf(con.getLastAckTime()));
    }

    private static Long getNumMsgs(ConsumerUID cid)  {
	Consumer con = Consumer.getConsumer(cid);

	if (con == null)  {
	    return (null);
	}

        return (Long.valueOf(con.totalMsgsDelivered()));
    }

    private static Long getNumMsgsPendingAcks(ConsumerUID cid)  {
	Consumer con = Consumer.getConsumer(cid);

	if (con == null)  {
	    return (null);
	}

        return (Long.valueOf(con.numPendingAcks()));
    }

    private static Long getNumPendingMsgs(ConsumerUID cid) {
        Consumer con = Consumer.getConsumer(cid);
        if (con == null) return null;
        return (Long.valueOf(con.numInProcessMsgs()));
    }

    private static String getSelector(ConsumerUID cid)  {
	Consumer con = Consumer.getConsumer(cid);

	if (con == null)  {
	    return (null);
	}

        return (con.getSelectorStr());
    }

    private static String getServiceName(ConsumerUID cid)  {
	ConnectionUID cxnId = getConnectionUID(cid);

	if (cxnId == null)  {
	    return (null);
	}

	String serviceName = ConnectionUtil.getServiceOfConnection(cxnId.longValue());

	return (serviceName);
    }

    private static String getUser(ConsumerUID cid)  {
	ConnectionUID cxnId = getConnectionUID(cid);

	if (cxnId == null)  {
	    return (null);
	}

	ConnectionInfo cxnInfo = ConnectionUtil.getConnectionInfo(cxnId.longValue());

	if (cxnInfo == null)  {
	    return (null);
	}

	return (cxnInfo.user);
    }


    private static CompositeData getConsumerInfo(ConsumerUID cid) 
						throws OpenDataException  {
        /*
         * Consumer Info item names for Monitor MBeans
         */
        final String[] consumerInfoMonitorItemNames = {
                            ConsumerInfo.ACKNOWLEDGE_MODE,
                            ConsumerInfo.ACKNOWLEDGE_MODE_LABEL,
                            ConsumerInfo.CLIENT_ID,
                            ConsumerInfo.CONNECTION_ID,
                            ConsumerInfo.CONSUMER_ID,
                            ConsumerInfo.CREATION_TIME,
                            ConsumerInfo.DESTINATION_NAME,
                            ConsumerInfo.DESTINATION_NAMES,
                            ConsumerInfo.DESTINATION_TYPE,
                            ConsumerInfo.DURABLE,
                            ConsumerInfo.DURABLE_ACTIVE,
                            ConsumerInfo.DURABLE_NAME,
                            ConsumerInfo.FLOW_PAUSED,
                            ConsumerInfo.HOST,
                            ConsumerInfo.LAST_ACK_TIME,
                            ConsumerInfo.NUM_MSGS,
                            ConsumerInfo.NUM_MSGS_PENDING,
                            ConsumerInfo.NUM_MSGS_PENDING_ACKS,
                            ConsumerInfo.SELECTOR,
                            ConsumerInfo.SERVICE_NAME,
                            ConsumerInfo.USER,
                            ConsumerInfo.WILDCARD,
                            ConsumerInfo.NEXT_MESSAGE_ID
                    };

        /*
         * Consumer Info item description for Monitor MBeans
         * TBD: use real descriptions
         */
        final String[] consumerInfoMonitorItemDesc = consumerInfoMonitorItemNames;

        /*
         * Consumer Info item types for Monitor MBeans
         */
        final OpenType[] consumerInfoMonitorItemTypes = {
			    SimpleType.INTEGER,		// ack mode
			    SimpleType.STRING,		// ack mode label
			    SimpleType.STRING,		// client ID
			    SimpleType.STRING,		// connection ID
			    SimpleType.STRING,		// consumer ID
			    SimpleType.LONG,		// creation time
			    SimpleType.STRING,		// dest name
			    new ArrayType(1, 
				SimpleType.STRING),	// dest names
			    SimpleType.STRING,		// dest type
			    SimpleType.BOOLEAN,		// durable
			    SimpleType.BOOLEAN,		// durable active
			    SimpleType.STRING,		// durable name
			    SimpleType.BOOLEAN,		// flow paused
			    SimpleType.STRING,		// host
			    SimpleType.LONG,		// last ack time
			    SimpleType.LONG,		// num msgs
			    SimpleType.LONG,		// num msgs pending 
			    SimpleType.LONG,		// num msgs pending acks
			    SimpleType.STRING,		// selector
			    SimpleType.STRING,		// service name
			    SimpleType.STRING,		// user
			    SimpleType.BOOLEAN,		// wildcard
			    SimpleType.STRING		// next Message
                    };

	final Object[] consumerInfoMonitorItemValues = {
			    getAcknowledgeMode(cid),
			    getAcknowledgeModeLabel(cid),
			    getClientID(cid),
                            getConnectionID(cid),
			    Long.toString(cid.longValue()),
                            getCreationTime(cid),
                            getDestinationName(cid),
                            getDestinationNames(cid),
                            getDestinationType(cid),
			    getDurable(cid),
			    getDurableActive(cid),
			    getDurableName(cid),
                            getFlowPaused(cid),
                            getHost(cid),
                            getLastAckTime(cid),
                            getNumMsgs(cid),
                            getNumPendingMsgs(cid),
                            getNumMsgsPendingAcks(cid),
                            getSelector(cid),
                            getServiceName(cid),
                            getUser(cid),
                            getWildcard(cid),
                            getNextMessageID(cid)
			};
	CompositeData cd = null;
        //Consumer con = Consumer.getConsumer(cid);

        if (monitorCompType == null)  {
            monitorCompType = new CompositeType("ConsumerMonitorInfo", "ConsumerMonitorInfo", 
                        consumerInfoMonitorItemNames, consumerInfoMonitorItemDesc, 
				consumerInfoMonitorItemTypes);
        }

	cd = new CompositeDataSupport(monitorCompType, 
			consumerInfoMonitorItemNames, consumerInfoMonitorItemValues);
	
	return (cd);
    }
}
