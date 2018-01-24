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
 * @(#)GetConsumersHandler.java	1.16 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.util.HashMap;
import java.util.Iterator;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Vector;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.net.IPAddress;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.admin.ConsumerInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.Session;

public class GetConsumersHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public GetConsumersHandler(AdminDataHandler parent) {
	super(parent);
    }

    /**
     * Handle the incomming administration message.
     *
     * @param con	The Connection the message came in on.
     * @param cmd_msg	The administration message
     * @param cmd_props The properties from the administration message
     */
    public boolean handle(IMQConnection con, Packet cmd_msg,
				       Hashtable cmd_props) {

	if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                 cmd_props);
        }

	String destination = (String)cmd_props.get(MessageType.JMQ_DESTINATION);
	Integer destType = (Integer)cmd_props.get(MessageType.JMQ_DEST_TYPE);

	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);
        int status = Status.OK;

        Vector v = new Vector();
        String errMsg = null;

	if ((destination == null) || (destType == null))  {
            errMsg = "Destination name and type not specified";
            logger.log(Logger.ERROR, errMsg);

            status = Status.ERROR;
	}

        if (destination != null)  {
            try {
                Destination[] ds = DL.getDestination(null, destination, 
                                    DestType.isQueue(destType.intValue()));
                Destination d = ds[0]; //PART

		if (d != null) {
	            Iterator cons = d.getConsumers();

	            while (cons.hasNext())  {
		        Consumer oneCon = (Consumer)cons.next();

                        HashMap h = constructConsumerInfo(oneCon.getConsumerUID(), d);

		        v.add(h);
	            }
	        } else  {
                    errMsg= rb.getString(rb.X_DESTINATION_NOT_FOUND, 
                               destination);
                    status = Status.NOT_FOUND;
		}

            } catch (Exception ex) {
	        logger.logStack(Logger.ERROR, ex.getMessage(), ex);
	        status = Status.ERROR;
                assert false;
            }
        }     

	setProperties(reply, MessageType.GET_CONSUMERS_REPLY,
		status, errMsg);

	setBodyObject(reply, v);
	parent.sendReply(con, cmd_msg, reply);
        return true;
    }

    public static HashMap constructConsumerInfo(ConsumerUID cid, Destination d)  {
        HashMap h = new HashMap();

        h.put("AcknowledgeMode", getAcknowledgeMode(cid));
        h.put("ClientID", getClientID(cid));
        h.put("ConnectionID", getConnectionID(cid));
        h.put("ConsumerID", Long.toString(cid.longValue()));
        h.put("DestinationName", d.getDestinationName());
        h.put("DestinationType", 
		(d.isQueue() ? 
		    Integer.valueOf(DestType.DEST_TYPE_QUEUE) : 
		    Integer.valueOf(DestType.DEST_TYPE_TOPIC)));

        h.put("Durable", getDurable(cid));

        h.put("DurableActive", getDurableActive(cid));

        h.put("DurableName", getDurableName(cid));

        h.put("FlowPaused", getFlowPaused(cid));

        h.put("Host", getHost(cid));

        h.put("LastAckTime", getLastAckTime(cid));

        h.put("NumMsgs", getNumMsgs(cid));

        h.put("NumMsgsPendingAcks", getNumMsgsPendingAcks(cid));

        h.put("Selector", getSelector(cid));

        h.put("ServiceName", getServiceName(cid));

        h.put("User", getUser(cid));

        return (h);
    }

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

    private static Integer getAcknowledgeMode(ConsumerUID cid)  {
	/*
	 * Note: May need to convert to javax.jms.Session constant values just
	 * in case.
	 */
        return (Integer.valueOf(toExternalAckMode(cid.getAckType())));
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

    public static boolean isDurable(ConsumerUID cid)  {
	Consumer con = Consumer.getConsumer(cid);

	if (con != null)  {
	    return (con.isDurableSubscriber());
	}

	return (false);
    }

    private static Boolean getDurable(ConsumerUID cid)  {
        return (Boolean.valueOf(isDurable(cid)));
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

    private static Boolean getDurableActive(ConsumerUID cid)  {
	if (!getDurable(cid).booleanValue())  {
	    return Boolean.FALSE;
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
	    return Boolean.FALSE;
	}

        return (Boolean.valueOf(con.getIsFlowPaused()));
    }

    public static ConnectionInfo getConnectionInfo(long id)  {
	ConnectionManager cm = Globals.getConnectionManager();
	ConnectionInfo cxnInfo = null;
	IMQConnection  cxn = null;

	cxn = (IMQConnection)cm.getConnection(new ConnectionUID(id));

	if (cxn == null)  {
	    return (null);
	}

	cxnInfo = cxn.getConnectionInfo();

	return (cxnInfo);
    }

    private static String getHost(ConsumerUID cid)  {
	ConnectionUID cxnId = getConnectionUID(cid);

	if (cxnId == null)  {
	    return (null);
	}

	ConnectionInfo cxnInfo = getConnectionInfo(cxnId.longValue());

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

    private static String getSelector(ConsumerUID cid)  {
	Consumer con = Consumer.getConsumer(cid);

	if (con == null)  {
	    return (null);
	}

        return (con.getSelectorStr());
    }

    public static String getServiceOfConnection(long id)  {
	ConnectionInfo cxnInfo = getConnectionInfo(id);

	if (cxnInfo == null)  {
	    return (null);
	}

	return(cxnInfo.service);
    }

    private static String getServiceName(ConsumerUID cid)  {
	ConnectionUID cxnId = getConnectionUID(cid);

	if (cxnId == null)  {
	    return (null);
	}

	String serviceName = getServiceOfConnection(cxnId.longValue());

	return (serviceName);
    }

    private static String getUser(ConsumerUID cid)  {
	ConnectionUID cxnId = getConnectionUID(cid);

	if (cxnId == null)  {
	    return (null);
	}

	ConnectionInfo cxnInfo = getConnectionInfo(cxnId.longValue());

	if (cxnInfo == null)  {
	    return (null);
	}

	return (cxnInfo.user);
    }
}
