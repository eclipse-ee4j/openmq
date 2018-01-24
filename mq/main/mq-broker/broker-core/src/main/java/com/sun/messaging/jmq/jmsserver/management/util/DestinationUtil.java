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
 * @(#)DestinationUtil.java	1.21 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import javax.management.*;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.ConflictException;
import com.sun.messaging.jmq.jmsserver.memory.MemoryGlobals;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.GetDestinationsHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.CreateDestinationHandler;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;

import com.sun.messaging.jmq.io.Status;

import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.DestState;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.ClusterDeliveryPolicy;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.DestLimitBehavior;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.log.Logger;

public class DestinationUtil {
    private static String[] queueCreateAttrs	= {
			    DestinationAttributes.CONSUMER_FLOW_LIMIT,
			    DestinationAttributes.LOCAL_ONLY,
			    DestinationAttributes.LIMIT_BEHAVIOR,
			    DestinationAttributes.LOCAL_DELIVERY_PREFERRED,
			    DestinationAttributes.MAX_BYTES_PER_MSG,
			    DestinationAttributes.MAX_NUM_ACTIVE_CONSUMERS,
			    DestinationAttributes.MAX_NUM_BACKUP_CONSUMERS,
			    DestinationAttributes.MAX_NUM_MSGS,
			    DestinationAttributes.MAX_NUM_PRODUCERS,
			    DestinationAttributes.MAX_TOTAL_MSG_BYTES,
			    DestinationAttributes.USE_DMQ,
			    DestinationAttributes.VALIDATE_XML_SCHEMA_ENABLED,
			    DestinationAttributes.XML_SCHEMA_URI_LIST,
			    DestinationAttributes.NEXT_MESSAGE_ID
			};

    private static String[] topicCreateAttrs	= {
			    DestinationAttributes.CONSUMER_FLOW_LIMIT,
			    DestinationAttributes.LOCAL_ONLY,
			    DestinationAttributes.LIMIT_BEHAVIOR,
			    DestinationAttributes.MAX_BYTES_PER_MSG,
			    DestinationAttributes.MAX_NUM_MSGS,
			    DestinationAttributes.MAX_NUM_PRODUCERS,
			    DestinationAttributes.MAX_TOTAL_MSG_BYTES,
			    DestinationAttributes.USE_DMQ,
			    DestinationAttributes.VALIDATE_XML_SCHEMA_ENABLED,
			    DestinationAttributes.XML_SCHEMA_URI_LIST
			};

    public static int toExternalDestState(int internalDestState)  {
	switch (internalDestState)  {
	case DestState.CONSUMERS_PAUSED:
	    return (DestinationState.CONSUMERS_PAUSED);

	case DestState.PRODUCERS_PAUSED:
	    return (DestinationState.PRODUCERS_PAUSED);

	case DestState.PAUSED:
	    return (DestinationState.PAUSED);

	case DestState.RUNNING:
	    return (DestinationState.RUNNING);

	default:
	    return (DestinationState.UNKNOWN);

	}
    }

    public static int toInternalDestState(int externalDestState)  {
	switch (externalDestState)  {
	case DestinationState.CONSUMERS_PAUSED:
	    return (DestState.CONSUMERS_PAUSED);

	case DestinationState.PRODUCERS_PAUSED:
	    return (DestState.PRODUCERS_PAUSED);

	case DestinationState.PAUSED:
	    return (DestState.PAUSED);

	case DestinationState.RUNNING:
	    return (DestState.RUNNING);

	default:
	    return (DestState.UNKNOWN);

	}
    }

    /*
     * Returns an ArrayList of Destinations that are visible to
     * the outside
     */
    public static List getVisibleDestinations()  {
	int	numDests = 0;

        
	Iterator[] itrs = Globals.getDestinationList().getAllDestinations(null);
        Iterator itr = itrs[0];
	ArrayList al = new ArrayList();

	while (itr.hasNext()) {
	    Destination oneDest = (Destination)itr.next();
	    if (!isVisibleDestination(oneDest))  {
	        continue;
	    }
	    al.add(oneDest);
	}

	return (al);
    }

    /*
     * Returns an ArrayList of Destinations that are visible,
     * is temporary and matches the specified connectionID.
     */
    public static List getVisibleTemporaryDestinations(long connectionID)  {
	int	numDests = 0;

	Iterator[] itrs = Globals.getDestinationList().getAllDestinations(null);
        Iterator itr = itrs[0];
	ArrayList al = new ArrayList();

	while (itr.hasNext()) {
	    Destination oneDest = (Destination)itr.next();
	    if (!isVisibleDestination(oneDest))  {
	        continue;
	    }

	    if (!oneDest.isTemporary())  {
	        continue;
	    }

	    ConnectionUID cxnId = oneDest.getConnectionUID();
	    if (cxnId == null)  {
		continue;
	    }

	    if (!(cxnId.longValue() == connectionID))  {
		continue;
	    }

	    al.add(oneDest);
	}

	return (al);
    }


    public static boolean isVisibleDestination(Destination d)  {
        if (d.isInternal() ||
                d.isAdmin() ||
                (d.getDestinationName().equals(MessageType.JMQ_ADMIN_DEST)) ||
                (d.getDestinationName().equals(MessageType.JMQ_BRIDGE_ADMIN_DEST)))  {
	    return (false);
        }

	return (true);
    }

    /*
     * Get DestinationInfo object from Destination object.
     */
    public static DestinationInfo getDestinationInfo(Destination d)  {
	DestinationInfo di = GetDestinationsHandler.getDestinationInfo(d);

	return (di);
    }

    public static String toExternalDestLimitBehavior(int internalDestLimitBehavior)  {
	switch (internalDestLimitBehavior)  {
	case DestLimitBehavior.FLOW_CONTROL:
	    return (DestinationLimitBehavior.FLOW_CONTROL);
	case DestLimitBehavior.REMOVE_OLDEST:
	    return (DestinationLimitBehavior.REMOVE_OLDEST);
	case DestLimitBehavior.REJECT_NEWEST:
	    return (DestinationLimitBehavior.REJECT_NEWEST);
	case DestLimitBehavior.REMOVE_LOW_PRIORITY:
	    return (DestinationLimitBehavior.REMOVE_LOW_PRIORITY);
	default:
	    return (DestinationLimitBehavior.UNKNOWN);
	}
    }

    public static int toInternalDestLimitBehavior(String externalDestLimitBehavior)  {
	if (externalDestLimitBehavior.equals(DestinationLimitBehavior.FLOW_CONTROL))  {
	    return (DestLimitBehavior.FLOW_CONTROL);
	} else if (externalDestLimitBehavior.equals(DestinationLimitBehavior.REMOVE_OLDEST))  {
	    return (DestLimitBehavior.REMOVE_OLDEST);
	} else if (externalDestLimitBehavior.equals(DestinationLimitBehavior.REJECT_NEWEST))  {
	    return (DestLimitBehavior.REJECT_NEWEST);
	} else if (externalDestLimitBehavior.
			equals(DestinationLimitBehavior.REMOVE_LOW_PRIORITY))  {
	    return (DestLimitBehavior.REMOVE_LOW_PRIORITY);
	} else  {
	    return (DestLimitBehavior.UNKNOWN);
	}
    }
   
    public static int toInternalPauseType(String externalPauseType)  {
	if (externalPauseType.equals(DestinationPauseType.PRODUCERS))  {
	    return (DestState.PRODUCERS_PAUSED);
	} else if (externalPauseType.equals(DestinationPauseType.CONSUMERS))  {
	    return (DestState.CONSUMERS_PAUSED);
	} else if (externalPauseType.equals(DestinationPauseType.ALL))  {
	    return (DestState.PAUSED);
	}

	return (DestState.UNKNOWN);
    }

    public static String toExternalPauseType(int internalPauseType)  {
	switch(internalPauseType)  {
	case DestState.PRODUCERS_PAUSED:
	    return (DestinationPauseType.PRODUCERS);
	case DestState.CONSUMERS_PAUSED:
	    return (DestinationPauseType.CONSUMERS);
	case DestState.PAUSED:
	    return (DestinationPauseType.ALL);
	default:
	    return ("UNKNOWN");
	}
    }

    public static boolean isValidPauseType(String pauseType)  {
	if (pauseType.equals(DestinationPauseType.PRODUCERS))  {
	    return (true);
	} else if (pauseType.equals(DestinationPauseType.CONSUMERS))  {
	    return (true);
	} else if (pauseType.equals(DestinationPauseType.ALL))  {
	    return (true);
	}

	return (false);
    }

    public static void pauseAllDestinations(int internalPauseType)  {
        Iterator[] itrs = Globals.getDestinationList().getAllDestinations(null);
        Iterator itr = itrs[0]; //PART

        while (itr.hasNext()) {
            Destination d =(Destination)itr.next();
            /*
             * Skip internal, admin, or temp destinations.
             * Skipping temp destinations may need to be
             * revisited.
             */
            if (d.isInternal() || d.isAdmin() || d.isTemporary())  {
                continue;
            }

            d.pauseDestination(internalPauseType);
        }

    }

    public static void resumeAllDestinations()  {
        Iterator[] itrs = Globals.getDestinationList().getAllDestinations(null);
        Iterator itr = itrs[0]; //PART

        while (itr.hasNext()) {
            Destination d =(Destination)itr.next();
            if (d.isPaused()) {
                d.resumeDestination();
            }
        }
    }

    public static void compactAllDestinations() throws BrokerException  {
        Iterator[] itrs = Globals.getDestinationList().getAllDestinations(null);
        Iterator itr = itrs[0]; //PART
        boolean docompact = true;
	String errMsg = null;
	BrokerResources rb = Globals.getBrokerResources();
	//Logger logger = Globals.getLogger();

        while (itr.hasNext()) {
            // make sure all are paused
            Destination d = (Destination)itr.next();

            /*
             * Skip internal, admin, or temp destinations.
             * Skipping temp destinations may need to be
             * revisited.
             */
            if (d.isInternal() || d.isAdmin() || d.isTemporary())  {
                continue;
            }

            if (!d.isPaused()) {
                docompact = false;
                String msg = rb.getString(
                        rb.E_SOME_DESTINATIONS_NOT_PAUSED);
                errMsg = rb.getString(rb.X_COMPACT_DSTS_EXCEPTION, msg);

		throw (new BrokerException(errMsg));
            }
        }

        if (docompact) {
            itrs = Globals.getDestinationList().getAllDestinations(null);
            itr = itrs[0];
            while (itr.hasNext()) {
                Destination d = (Destination)itr.next();

                /*
                 * Skip internal, admin, or temp destinations.
                 * Skipping temp destinations may need to be
                 * revisited.
                 */
                if (d.isInternal() || d.isAdmin() || d.isTemporary())  {
                    continue;
                }

                d.compact();
            }
        }
    }
    
    public static void checkDestType(String type) throws BrokerException  {
	if (type == null)  {
	    throw new BrokerException("Null destination type specified");
	}

	if (type.equals(DestinationType.QUEUE) ||
	    type.equals(DestinationType.TOPIC))  {
	    
	    return;
	}

	BrokerResources rb = Globals.getBrokerResources();
	throw new BrokerException(rb.getString(rb.X_JMX_INVALID_DEST_TYPE_SPEC, type));
    }

    public static void checkCreateDestinationAttrs(String type, AttributeList attrs) throws BrokerException {
	String[] validAttrs = null;

	checkDestType(type);

	if (attrs == null)  {
	    return;
	}

        if (type.equals(DestinationType.QUEUE))  {
	    validAttrs = queueCreateAttrs;
	} else if (type.equals(DestinationType.TOPIC))  {
	    validAttrs = topicCreateAttrs;
        }

	for (Iterator i = attrs.iterator(); i.hasNext();) {
	    Attribute attr = (Attribute) i.next();
	    String name = attr.getName();
	    
	    if (!checkOneDestAttr(name, validAttrs))  {
	        BrokerResources rb = Globals.getBrokerResources();
		String err;

		if (type.equals(DestinationType.QUEUE))  {
	            err = rb.getString(rb.X_JMX_INVALID_CREATE_TIME_ATTR_SPEC_QUEUE, name);
		} else  {
	            err = rb.getString(rb.X_JMX_INVALID_CREATE_TIME_ATTR_SPEC_TOPIC, name);
		}
		throw new BrokerException(err);
	    }
	}
    }

    /*
     * Returns true if attrName is valid (ie in validAttrs array),
     * false otherwise.
     */
    private static boolean checkOneDestAttr(String attrName, String[] validAttrs)  {
	if (attrName == null)  {
	    return(false);
	}

	if (validAttrs == null)  {
	    return(false);
	}

	for (int i = 0; i < validAttrs.length; ++i)  {
	    if (attrName.equals(validAttrs[i]))  {
		return (true);
	    }
	}
	return(false);
    }

    public static void createDestination(DestinationInfo info) throws BrokerException {
        String errMsg = null;
        int status = Status.OK;
	BrokerResources rb = Globals.getBrokerResources();
	Logger logger = Globals.getLogger();

        // Default attributes of the destination
        int type = DestType.DEST_TYPE_QUEUE | DestType.DEST_FLAVOR_SINGLE; 
        int maxMessages = -1;
        SizeString maxMessageBytes = null;
        SizeString maxMessageSize = null;

        if (MemoryGlobals.getMEM_DISALLOW_CREATE_DEST()) {
            status = Status.ERROR;
            errMsg = rb.W_LOW_MEM_REJECT_DEST;
 
        } else if (info.isModified(DestinationInfo.NAME)) {
            if (info.isModified(DestinationInfo.TYPE)) {
                type = info.type;
            }
            if (info.isModified(DestinationInfo.MAX_MESSAGES)) {
                maxMessages = info.maxMessages;
            }
            if (info.isModified(DestinationInfo.MAX_MESSAGE_BYTES)) {
                maxMessageBytes = new SizeString();
                maxMessageBytes.setBytes(info.maxMessageBytes);
            }
            if (info.isModified(DestinationInfo.MAX_MESSAGE_SIZE)) {
                maxMessageSize = new SizeString();
                maxMessageSize.setBytes(info.maxMessageSize);
            }

        } else {
            status = Status.ERROR;
            errMsg = rb.X_NO_DEST_NAME_SET;
        }
        // 
        //XXX create destination
        if (status == Status.OK) {

            if (DestType.destNameIsInternal(info.name)) {
                status = Status.ERROR;
                errMsg =  rb.getKString( rb.X_CANNOT_CREATE_INTERNAL_DEST, 
                            info.name,
			    DestType.INTERNAL_DEST_PREFIX);
	    } else  {
                if (CreateDestinationHandler.isValidDestinationName(info.name)) {

                    try {
                        Globals.getDestinationList().createDestination(null, info.name, type);
                    } catch (Exception ex) {
                        status = Status.ERROR;
                        errMsg =  rb.getString( rb.X_CREATE_DEST_EXCEPTION, 
                            info.name, getMessageFromException(ex));
                       if (ex instanceof ConflictException)
                           logger.log(Logger.INFO, errMsg, ex);
                       else
                           logger.logStack(Logger.INFO, errMsg, ex);
                    }
                } else {
                    status = Status.ERROR;
                    errMsg =  rb.getKString( rb.X_DEST_NAME_INVALID, 
                            info.name);
                }
	    }
        }

        if (status == Status.OK) {
            try {

                Destination[] ds = Globals.getDestinationList().getDestination(
                                       null, info.name, DestType.isQueue(type));
                Destination d = ds[0];

                d.setCapacity(maxMessages);
                d.setByteCapacity(maxMessageBytes);
                d.setMaxByteSize(maxMessageSize);
                if (info.isModified(info.DEST_SCOPE)) {
                    int scope = info.destScope;
                    d.setScope(scope);  
                }
                if (info.isModified(info.DEST_LIMIT)) {
                    int destlimit = info.destLimitBehavior;
                    d.setLimitBehavior(destlimit);
                }
                if (info.isModified(info.DEST_PREFETCH)) {
                    int prefetch = info.maxPrefetch;
                    d.setMaxPrefetch(prefetch);
                }
                if (info.isModified(info.DEST_CDP)) {
                    int clusterdeliverypolicy = info.destCDP;
                    d.setClusterDeliveryPolicy(clusterdeliverypolicy);
                }
                if (info.isModified(info.MAX_ACTIVE_CONSUMERS)) {
                    int maxcons = info.maxActiveConsumers;
                    d.setMaxActiveConsumers(maxcons);
                }
                if (info.isModified(info.MAX_PRODUCERS)) {
                    int maxp = info.maxProducers;
                    d.setMaxProducers(maxp);
                }
                if (info.isModified(info.MAX_FAILOVER_CONSUMERS)) {
                    int maxcons = info.maxFailoverConsumers;
                    d.setMaxFailoverConsumers(maxcons);
                }
                if (info.isModified(info.MAX_SHARED_CONSUMERS)) {
                    int maxsharedcons = info.maxNumSharedConsumers;
                    d.setMaxSharedConsumers(maxsharedcons);
                }
                if (info.isModified(info.SHARE_FLOW_LIMIT)) {
                    int sflowlimit = info.sharedConsumerFlowLimit;
                    d.setSharedFlowLimit(sflowlimit);
                }
                if (info.isModified(info.USE_DMQ)) {
                    boolean dmq = info.useDMQ;
                    d.setUseDMQ(dmq);
                }
                d.update();
              
		/*
		// audit logging for create destination
		Globals.getAuditSession().destinationOperation(
			con.getUserName(), con.remoteHostString(),
			MQAuditSession.CREATE_DESTINATION,
			d.isQueue()?MQAuditSession.QUEUE:MQAuditSession.TOPIC,
			d.getDestinationName());
		*/

            } catch (Exception ex) {

                // remove the destination
                try {
                    DestinationUID duid = DestinationUID.getUID(
                        info.name, DestType.isQueue(type));
                    Globals.getDestinationList().removeDestination(null, duid, false, ex.toString());
                } catch (Exception ex1) {
                    // if we cant destroy .. its ok .. ignore the exception
                }

                status = Status.ERROR;
                errMsg = rb.getString( rb.X_UPDATE_DEST_EXCEPTION, 
                            info.name, getMessageFromException(ex));

                logger.log(Logger.WARNING, errMsg, ex);

            }
        }

	if (status != Status.OK)  {
	    throw new BrokerException(errMsg);
	}
    }

    /**
     * Get a message from an Exception. This basically checks
     * if the exception is a BrokerException and properly formats
     * the linked exceptions message. The string returned does
     * NOT include the exception name.
     */
    public static String getMessageFromException(Exception e) {

	String m = e.getMessage();

	if (e instanceof BrokerException) {
            Throwable root_ex = ((BrokerException)e).getCause();
            if (root_ex == null) return m; // no root cause
	    String lm = root_ex.getMessage();

	    if (lm != null) {
	        m = m + "\n" + lm;
            }
	}
        return m;
    }

    public static DestinationInfo getDestinationInfoFromAttrs(String type, 
						String destName, AttributeList attrs)  {
	DestinationInfo info = new DestinationInfo();

	info.setName(destName);

	if (type.equals(DestinationType.QUEUE))  {
	    info.setType(DestType.DEST_TYPE_QUEUE);
	} else if (type.equals(DestinationType.TOPIC))  {
	    info.setType(DestType.DEST_TYPE_TOPIC);
	}

	if (attrs == null)  {
	    return (info);
	}

	/*
	 * We assume that type checking was already handled
	 */

	for (Iterator i = attrs.iterator(); i.hasNext();) {
	    Attribute attr = (Attribute) i.next();
	    String name = attr.getName();
	    Object value = attr.getValue();

	    if (name.equals(DestinationAttributes.CONSUMER_FLOW_LIMIT))  {
		info.setPrefetch(((Long)value).intValue());
	    }

	    if (name.equals(DestinationAttributes.LOCAL_ONLY))  {
		info.setScope(((Boolean)value).booleanValue());
	    }

	    if (name.equals(DestinationAttributes.LIMIT_BEHAVIOR))  {
		info.setLimitBehavior( toInternalDestLimitBehavior((String)value) );
	    }

	    if (name.equals(DestinationAttributes.LOCAL_DELIVERY_PREFERRED))  {
		int cdp;

		if (((Boolean)value).booleanValue())  {
		    cdp = ClusterDeliveryPolicy.LOCAL_PREFERRED;
		} else  {
		    cdp = ClusterDeliveryPolicy.DISTRIBUTED;
		}
		info.setClusterDeliveryPolicy(cdp);
	    }

	    if (name.equals(DestinationAttributes.MAX_BYTES_PER_MSG))  {
		info.setMaxMessageSize(((Long)value).longValue());
	    }

	    if (name.equals(DestinationAttributes.MAX_NUM_ACTIVE_CONSUMERS))  {
		info.setMaxActiveConsumers(((Integer)value).intValue());
	    }

	    if (name.equals(DestinationAttributes.MAX_NUM_BACKUP_CONSUMERS))  {
		info.setMaxFailoverConsumers(((Integer)value).intValue());
	    }

	    if (name.equals(DestinationAttributes.MAX_NUM_MSGS))  {
		info.setMaxMessages(((Long)value).intValue());
	    }

	    if (name.equals(DestinationAttributes.MAX_NUM_PRODUCERS))  {
		info.setMaxProducers(((Integer)value).intValue());
	    }

	    if (name.equals(DestinationAttributes.MAX_TOTAL_MSG_BYTES))  {
		info.setMaxMessageBytes(((Long)value).longValue());
	    }

	    if (name.equals(DestinationAttributes.USE_DMQ))  {
		info.setUseDMQ(((Boolean)value).booleanValue());
	    }
	}

	return (info);
    }

    public static ObjectName getConfigObjectName(Destination d) 
			throws MalformedObjectNameException {
	ObjectName o;

        o = MQObjectName.createDestinationConfig(
                    d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC,
                    d.getDestinationName());

	return (o);
    }

    public static ObjectName getMonitorObjectName(Destination d) 
			throws MalformedObjectNameException {
	ObjectName o;

        o = MQObjectName.createDestinationMonitor(
                    d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC,
                    d.getDestinationName());

	return (o);
    }

    public static Object convertAttrValueInternaltoExternal(int attr,
			Object value)  {
	Object tmp;

	switch (attr)  {
	/*
	 * All the other cases return the right type/value
	case DestinationInfo.DEST_PREFETCH:
	case DestinationInfo.USE_DMQ:
	case DestinationInfo.DEST_CDP:
	case DestinationInfo.MAX_MESSAGE_SIZE:
	case DestinationInfo.MAX_ACTIVE_CONSUMERS:
	case DestinationInfo.MAX_FAILOVER_CONSUMERS:
	case DestinationInfo.MAX_MESSAGES:
	case DestinationInfo.MAX_PRODUCERS:
	case DestinationInfo.MAX_MESSAGE_BYTES:
	    return (value);
	*/

	case DestinationInfo.DEST_LIMIT:
	    if ((value != null) && (value instanceof Integer))  {
		Integer i = (Integer)value;
	        return (DestinationUtil.toExternalDestLimitBehavior(i.intValue()));
	    } else  {
	        return (DestinationLimitBehavior.UNKNOWN);
	    }

	default:
	    return (value);
	}

    }

    /*
     * Convert attribute from DestinationInfo to attribute in DestinationAttributes
     */
    public static String getAttrNameFromDestinationInfoAttr(int attr)  {
	String attrName = null;

	switch (attr)  {
	case DestinationInfo.MAX_MESSAGES:
	    return(DestinationAttributes.MAX_NUM_MSGS);

	case DestinationInfo.MAX_MESSAGE_SIZE:
	    return(DestinationAttributes.MAX_BYTES_PER_MSG);

	case DestinationInfo.MAX_MESSAGE_BYTES:
	    return(DestinationAttributes.MAX_TOTAL_MSG_BYTES);

	case DestinationInfo.DEST_LIMIT:
	    return(DestinationAttributes.LIMIT_BEHAVIOR);

	case DestinationInfo.DEST_CDP:
	    return(DestinationAttributes.LOCAL_DELIVERY_PREFERRED);

	case DestinationInfo.MAX_ACTIVE_CONSUMERS:
	    return(DestinationAttributes.MAX_NUM_ACTIVE_CONSUMERS);

	case DestinationInfo.MAX_FAILOVER_CONSUMERS:
	    return(DestinationAttributes.MAX_NUM_BACKUP_CONSUMERS);

	case DestinationInfo.MAX_PRODUCERS:
	    return(DestinationAttributes.MAX_NUM_PRODUCERS);

	case DestinationInfo.DEST_PREFETCH:
	    return(DestinationAttributes.CONSUMER_FLOW_LIMIT);

	case DestinationInfo.USE_DMQ:
	    return(DestinationAttributes.USE_DMQ);

	/*
	 * The following from DestinationInfo is not used/supported:
	 *
	 *   DestinationInfo.MAX_SHARED_CONSUMERS:
	 *   DestinationInfo.DEST_PREFETCH:
	 *   DestinationInfo.DEST_SCOPE:
	 *
	 */

	default:
	    return (null);
	}
    }

    public static void checkPauseType(String pauseType) throws IllegalArgumentException  {
	if (pauseType.equals(DestinationPauseType.ALL) ||
	    pauseType.equals(DestinationPauseType.PRODUCERS) ||
	    pauseType.equals(DestinationPauseType.CONSUMERS))  {
	    
	    return;
	}

	BrokerResources rb = Globals.getBrokerResources();

	throw new IllegalArgumentException(rb.getString(rb.X_JMX_INVALID_DEST_PAUSE_TYPE_SPEC, pauseType));
    }


}
