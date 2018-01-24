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
 * @(#)DestinationInfo.java	1.20 07/02/07
 */ 

package com.sun.messaging.jmq.util.admin;

import java.io.*;
import java.util.Hashtable;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.DestScope;

/**
 * DestinationInfo encapsulates information about a JMQ Destination. It is
 * used to pass this information between the Broker and an
 * administration client.
 */
public class DestinationInfo extends AdminInfo {

    static final long serialVersionUID = 7043096792121903858L;


    /**
     * Number of messages currently in destination. Not Updateable
     */
    public int		nMessages;

    /**
     * Number of message bytes currently in destination. Not Updateable
     */
    public long	        nMessageBytes;

    /**
     * Number of consumers on destination. Not Updateable
     */
    public int		nConsumers;

    /**
     * Number of producers on destination. Not Updateable
     * @since 3.5
     */
    public int		nProducers;



    /**
     * Number of active consumers on destination. Not Updateable
     * @since 3.5
     */
    public int		naConsumers;


    /**
     * Number of failover consumers on destination. Not Updateable
     * @since 3.5
     */
    public int		nfConsumers;

    /**
     * Identifies if the destination was autocreated. Not-updatable.<p>
     * Note: autocreated really indicates that the destination was not created
     *       using imqcmd. e.g. the DMQ is autocreated.
     * @since 3.5
     */
    public boolean autocreated;

    /**
     * Identifies the state of the destination. Not-updateable.
     * @see com.sun.messaging.jmq.util.DestState
     * @since 3.5
     */
    public int destState;

    /*-----------------------------------------------------------------*/

    /**
     * Name of destination. Set at creation only
     */
    public String	name;

    /**
     * Type of destination. Set at creation only. Should be a combination
     * of bitmasks defined by DestType (or a simply type of just queue or
     * topic on a query for 3.0 compatibiity).
     */
    public int		type;

    /**
     * Complete type of destination. A combination
     * of bitmasks defined by DestType.
     */
    public int		fulltype;

    /**
     * Max number of messages this destination can hold. Updateable.<P>
     * <I>Since 3.5, no longer applicable to Queues only.</I>
     */
    public int		maxMessages;

    /**
     * Max number of message bytes this destination can hold. Updateable.
     * <I>Since 3.5, no longer applicable to Queues only.</I>
     */
    public long	        maxMessageBytes;

    /**
     * Max message size that can be sent to this destination. Updateable.
     */
    public long         maxMessageSize;

    /**
     * Identifies the scope of the destination. Updateable.
     * @see com.sun.messaging.jmq.util.DestScope
     * @since 3.5
     */
    public int destScope;

    /**
     * Identifies the limit behavior of the destination. Updateable.
     * @see com.sun.messaging.jmq.util.DestLimitBehavior
     * @since 3.5
     */
    public int destLimitBehavior;
    /**
     * Defined the max prefetch value for the consumer. Max Prefetch
     * is the maximum flow control for a consumer on this destination.
     * May be unset (-1).  Updateable.
     * @since 3.5
     */
    public int maxPrefetch;
    /**
     * Identifies the cluster delivery policy of the destination. Updateable.<P>
     * Only applies to CLUSTER or DISTRIBUTED behavior. Invalid settings
     * for a destination will be ignored.
     *
     * @see com.sun.messaging.jmq.util.ClusterDeliveryPolicy
     * @since 3.5
     */
    public int destCDP;


    /**
     * maximum number of active consumers on a destination. Queue only.
     *
     * @since 3.5
     */
    public int maxActiveConsumers;

    /**
     * maximum number of failover consumers on a destination. Queue only.
     *
     * @since 3.5
     */
    public int maxFailoverConsumers;
    
    /**
     * maximum number of producers on a destination.
     *
     * @since 3.5
     */
    public int maxProducers;


    /**
     * max number of consumers on a single shared subscriber<p>
     * [<i>topic only</i>, <i>private</i>]<p>
     * @since 3.6
     */
    public int maxNumSharedConsumers;

    /**
     * shared consumer flow limit.
     * [<i>topic only</i>, <i>private</i>]<p>
     * @since 3.6
     */
    public int sharedConsumerFlowLimit;

    /**
     * Uses Dead Message Queue
     * @since 3.6
     */
    public boolean useDMQ;

    /**
     * Number of unack'd messages currently in destination. Not Updateable
     * @since 3.6
     */
    public int		nUnackMessages;

    /**
     * Number of messages currently in destination held for delayed delivery
     *
     * Not Updateable
     * @since 5.0 
     */
    public int		nInDelayMessages;
    public long		nInDelayMessageBytes;

    /**
     * number of messages in open transactions 
     * @since 4.0
     */
    public int nTxnMessages;

    /**
     * bytes of messages in open transactions 
     * @since 4.0
     */
    public long nTxnMessageBytes;

    /**
     * Hashtable containing consumer wildcards (eg "news.java.*") and consumer count.
     * Not updateable.
     * @since 4.2
     */
    public Hashtable consumerWildcards;

    /**
     * Hashtable containing producer wildcards (eg "news.java.*") and producer count
     * Not updateable.
     * @since 4.2
     */
    public Hashtable producerWildcards;

    /**
     *
     * @since 4.2
     */
    public boolean validateXMLSchemaEnabled;

    /**
     * 
     * @since 4.2
     */
    public String XMLSchemaUriList;

    /**
     *
     * @since 4.2
     */
    public boolean reloadXMLSchemaOnFailure;

    /**
     *
     * @since 4.2
     */
    public int nRemoteMessages;

    /**
     *
     * @since 4.2
     */
    public long nRemoteMessageBytes;

    public static final int NAME              = 0x00000001;
    public static final int TYPE              = 0x00000002;
    public static final int MAX_MESSAGES      = 0x00000004;
    public static final int MAX_MESSAGE_BYTES = 0x00000008;
    public static final int MAX_MESSAGE_SIZE  = 0x00000010;
    /**
     * @since 3.5
     */
    public static final int DEST_SCOPE  = 0x00000020;
    /**
     * @since 3.5
     */
    public static final int DEST_LIMIT  = 0x00000040;
    /**
     * @since 3.5
     */
    public static final int DEST_PREFETCH  = 0x00000080;
    /**
     * @since 3.5
     */
    public static final int DEST_CDP  = 0x00000100;

    /**
     * @since 3.5
     */
    public static final int MAX_ACTIVE_CONSUMERS = 0x00000200;

    /**
     * @since 3.5
     */
    public static final int MAX_FAILOVER_CONSUMERS = 0x00000400;

    /**
     * @since 3.5
     */
    public static final int MAX_PRODUCERS = 0x00000800;

    public static final int MAX_SHARED_CONSUMERS = 0x00001000;
    public static final int SHARE_FLOW_LIMIT= 0x00002000;

    /**
     * @since 3.6
     */
    public static final int USE_DMQ = 0x00004000;

    /**
     * @since 4.2
     */
    public static final int VALIDATE_XML_SCHEMA_ENABLED = 0x00008000;

    /**
     * @since 4.2
     */
    public static final int XML_SCHEMA_URI_LIST = 0x00010000;

    /**
     * @since 4.2
     */
    public static final int RELOAD_XML_SCHEMA_ON_FAILURE = 0x00020000;

    /**
     * Constructor for Destination.
     *
     */
    public DestinationInfo() {
	reset();
    }

    public void reset() {
	name = null;
	type = 0;
	nMessages = 0;
	nMessageBytes = 0;
	nConsumers = 0;
	nfConsumers = 0;
	autocreated = false;
	maxMessages = 0;
	maxMessageBytes = 0;
        maxMessageSize = 0;
        destState = 0;
        destScope = 0;
        destLimitBehavior = 0;
        maxPrefetch = 0;
        destCDP = 0;
        maxActiveConsumers = 0;
        maxFailoverConsumers = 0;
        maxProducers = 0;
        maxNumSharedConsumers = 0;
        sharedConsumerFlowLimit = 0;
        useDMQ = true;
	nUnackMessages = 0;
	nTxnMessages = 0;
	nTxnMessageBytes = 0;
	consumerWildcards = null;
	producerWildcards = null;
	validateXMLSchemaEnabled = false;
	XMLSchemaUriList = null;
	reloadXMLSchemaOnFailure = false;
        nRemoteMessages = 0;
        nRemoteMessageBytes = 0;

    }

    /**
     * Return a string representation of the destination. 
     *
     * @return String representation of destination.
     */
    public String toString() {

	return name + ": " + DestType.toString(type) + ", "
		+ nConsumers + " consumers, " +
		nMessages + " messages (" +
		maxMessages + " max), " +
		nMessageBytes + " bytes (" +
		maxMessageBytes + "max)";
    }


    /**
     * Set the Destination's name
     *
     * @param name The name of Destination.
     */
    public void setName(String name) {
	this.name = name;
        setModified(NAME);
    }

    /**
     * Set the Destination's tye. This must be made of up bitmasks
     * defined by DestType
     *
     * @param type The type of Destination.
     */
    public void setType(int type) {
	this.type = type;
        setModified(TYPE);
    }

    /**
     * Set the maximum number of messages allowed in this destination.
     * Queues only.
     * 
     * @param n The maximum number of messages allowed in this Queue.
     *         -1 for unlimited.
     */
    public void setMaxMessages(int n) {
	this.maxMessages = n;
        setModified(MAX_MESSAGES);
    }

    /**
     * Set the maximum number of message bytes allowed in this destination.
     * Queues only.
     * 
     * @param The maximum number of message bytes allowed in this Queue.
     */
    public void setMaxMessageBytes(long n) {
	this.maxMessageBytes = n;
        setModified(MAX_MESSAGE_BYTES);
    }

    /**
     * Set the max message size this destination will accept.
     * 
     * @param n The max message size this destination will accept.
     */
    public void setMaxMessageSize(long n) {
	this.maxMessageSize = n;
        setModified(MAX_MESSAGE_SIZE);
    }

    /**
     * Sets the Destination Scope for this destination.
     *
     * @param n The integer value from destScope this destination will accept.
     * @see com.sun.messaging.jmq.util.DestScope
     * @since 3.5
     */
    public void setScope(int n) {
	this.destScope = n;
        setModified(DEST_SCOPE);
    }
    /**
     * Sets the Destination Limit Behavior for this destination.
     *
     * @param n The integer value from DestLimitBehavior this 
     *          destination will accept.
     * @see com.sun.messaging.jmq.util.DestLimitBehavior
     * @since 3.5
     */
    public void setLimitBehavior(int n) {
	this.destLimitBehavior = n;
        setModified(DEST_LIMIT);
    }
    /**
     * Sets the Destination Max Prefetch for this destination.
     *
     * @param n The integer value from DestLimitBehavior this 
     *          destination will accept.
     * @since 3.5
     */
    public void setPrefetch(int n) {
	this.maxPrefetch = n;
        setModified(DEST_PREFETCH);
    }
    /**
     * Sets the Destination's cluster delivery policy for this destination.
     *
     * @param n The integer value from ClusterDeliveryPolicy this 
     *          destination will accept.
     * @see com.sun.messaging.jmq.util.ClusterDeliveryPolicy
     * @since 3.5
     */
    public void setClusterDeliveryPolicy(int n) {
	this.destCDP = n;
        setModified(DEST_CDP);
    }

    /**
     * Sets the Destination's scope for this destination.
     *
     * @param boolean should the destination be local
     * @see com.sun.messaging.jmq.util.DestScope
     * @since 3.5
     */
    public void setScope(boolean local) {
	this.destScope = (local ? DestScope.LOCAL :
                        DestScope.CLUSTER);
        setModified(DEST_SCOPE);
    }

    /**
     * Returns is the destination is local (Scope of
     * local).
     * @see com.sun.messaging.jmq.util.DestScope
     * @since 3.5
     */
    public boolean isDestinationLocal() {
        return this.destScope == DestScope.LOCAL;
    }

    /**
     * Sets the maximum number of active consumers for this
     * destination. Only applies to Queues.
     *
     * @param num number of consumers
     * @since 3.5
     */
    public void setMaxActiveConsumers(int num) {
	this.maxActiveConsumers = num;
        setModified(MAX_ACTIVE_CONSUMERS);
    }

    /**
     * Sets the maximum numer of failover consumers for this
     * destination. Only applies to Queues.
     *
     * @param num number of consumers
     * @since 3.5
     */
    public void setMaxFailoverConsumers(int num) {
	this.maxFailoverConsumers = num;
        setModified(MAX_FAILOVER_CONSUMERS);
    }
    /**
     * Sets the Destination's scope for this destination.
     *
     * @param num number of consumers
     * @since 3.5
     */
    public void setMaxProducers(int num) {
	this.maxProducers = num;
        setModified(MAX_PRODUCERS);
    }

    /*
     * @since 3.6
     */
    public void setMaxNumSharedConsumers(int num) {
	this.maxNumSharedConsumers = num;
        setModified(MAX_SHARED_CONSUMERS);
    }

    /*
     * @since 3.6
     */
    public void setSharedConsumerFlowLimit(int num) {
	this.sharedConsumerFlowLimit = num;
        setModified(SHARE_FLOW_LIMIT);
    }

    /*
     * @since 3.6
     */
    public void setUseDMQ(boolean b) {
	this.useDMQ = b;
        setModified(USE_DMQ);
    }

    /*
     * @since 3.6
     */
    public boolean useDMQ() {
	return (this.useDMQ);
    }

    public void setValidateXMLSchemaEnabled(boolean b)  {
	this.validateXMLSchemaEnabled = b;
        setModified(VALIDATE_XML_SCHEMA_ENABLED);
    }

    public boolean validateXMLSchemaEnabled()  {
	return (this.validateXMLSchemaEnabled);
    }

    public void setXMLSchemaUriList(String s)  {
	this.XMLSchemaUriList = s;
        setModified(XML_SCHEMA_URI_LIST);
    }

    public void setReloadXMLSchemaOnFailure(boolean b)  {
	this.reloadXMLSchemaOnFailure = b;
        setModified(RELOAD_XML_SCHEMA_ON_FAILURE);
    }

    public boolean reloadXMLSchemaOnFailure()  {
	return (this.reloadXMLSchemaOnFailure);
    }


    /**
     * handles translating old packets when class is deserialized
     */
    private void readObject(java.io.ObjectInputStream ois)
        throws IOException, ClassNotFoundException
    {
        ois.defaultReadObject();
        if (fulltype == 0) {
            fulltype = type;
        }
    }


    /**
     * handles translating old packets when class is serialized
     */
    private void writeObject(java.io.ObjectOutputStream oos)
        throws IOException, ClassNotFoundException
    {
        oos.defaultWriteObject();
    }

}
