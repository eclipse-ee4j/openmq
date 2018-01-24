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

import java.util.Properties;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;

/**
 * Event class indicating some actions related to
 * Broker Management.
 *<P>
 * The fields of this event include the various pieces of information
 * needed for broker management tasks.
 */
public class BrokerCmdStatusEvent extends CommonCmdStatusEvent {

    /*******************************************************************************
     * BrokerCmdStatusEvent event types
     * use integers 0 - 1000  to avoid overlap with super class and other subclasses 
     *******************************************************************************/
    public final static int	DESTROY_DST		= 0;
    public final static int	QUERY_SVC		= 1;
    public final static int	LIST_SVC		= 2;
    public final static int	PAUSE_SVC		= 3;
    public final static int	PAUSE_BKR		= 4;
    public final static int	RESUME_SVC		= 5;
    public final static int	RESUME_BKR		= 6;
    public final static int	QUERY_DST		= 7;
    public final static int	LIST_DST		= 8;
    public final static int	CREATE_DST		= 9;
    public final static int	PURGE_DST		= 10;
    public final static int	QUERY_BKR		= 11;
    public final static int	UPDATE_BKR		= 12;
    public final static int	UPDATE_DST		= 13;
    public final static int	UPDATE_SVC		= 14;
    public final static int	RESTART_BKR		= 15;
    public final static int	SHUTDOWN_BKR		= 16;
    public final static int	LIST_DUR		= 17;
    public final static int	DESTROY_DUR		= 18;
    public final static int	METRICS_SVC		= 19;
    public final static int	METRICS_BKR		= 20;
    public final static int	RELOAD_CLS		= 21;
    public final static int	HELLO			= 22;
    public final static int	COMMIT_TXN		= 23;
    public final static int	ROLLBACK_TXN		= 24;
    public final static int	LIST_TXN		= 25;
    public final static int	QUERY_TXN		= 26;
    public final static int	PURGE_DUR 		= 27;
    public final static int	PAUSE_DST 		= 28;
    public final static int	RESUME_DST 		= 29;
    public final static int	METRICS_DST 		= 30;
    public final static int	COMPACT_DST 		= 32;
    public final static int	LIST_CXN 		= 33;
    public final static int	QUERY_CXN 		= 34;
    public final static int	DEBUG 			= 35;
    public final static int	QUIESCE_BKR		= 36;
    public final static int	TAKEOVER_BKR		= 37;
    public final static int	LIST_BKR		= 38;
    public final static int	LIST_JMX		= 39;
    public final static int	DESTROY_CXN 		= 40;
    public final static int	UNQUIESCE_BKR		= 41;
    public final static int	RESET_BKR		= 42;
    public final static int	GET_MSGS		= 43;
    public final static int	DELETE_MSG		= 44;
    public final static int	CHECKPOINT_BKR		= 45;
    public final static int CLUSTER_CHANGE_MASTER   = 46;
    public final static int	MIGRATESTORE_BKR        = 47;

    private transient BrokerAdmin		ba;

    private Properties		bkrProps;

    private String		svcName = null;
    private ServiceInfo		svcInfo = null;

    private String		dstName = null;
    private int			dstType = -1;
    private DestinationInfo	dstInfo = null;

    private String		durName = null;
    private String		clientID = null;

    private long		tid = 0;
    private long		cxnid = 0;

    /**
     * Creates an instance of BrokerAdminEvent
     * @param source the object where the event originated
     * @type the event type
     */
    public BrokerCmdStatusEvent(Object source, int type) {
	super(source, type);
    }

    /**
     * Creates an instance of BrokerAdminEvent
     * @param source the object where the event originated
     * @type the event type
     */
    public BrokerCmdStatusEvent(Object source, BrokerAdmin ba, int type) {
	super(source, type);
	setBrokerAdmin(ba);
    }

    public void setBrokerAdmin(BrokerAdmin ba) {
	this.ba = ba;
    }
    public BrokerAdmin getBrokerAdmin() {
	return (ba);
    }

    public void setServiceName(String svcName) {
	this.svcName = svcName;
    }
    public String getServiceName() {
	return svcName;
    }

    public void setServiceInfo(ServiceInfo svcInfo) {
	this.svcInfo = svcInfo;
    }
    public ServiceInfo getServiceInfo() {
	return (svcInfo);
    }

    public void setDestinationName(String name) {
    this.dstName = name;
    }
    public String getDestinationName() {
    return dstName;
    }

    public void setDestinationType(int type) {
    this.dstType = type;
    }
    public int getDestinationType() {
    return dstType;
    }

    public void setDestinationInfo(DestinationInfo dstInfo) {
    this.dstInfo = dstInfo;
    }
    public DestinationInfo getDestinationInfo() {
    return (dstInfo);
    }

    public void setBrokerProperties(Properties bkrProps) {
	this.bkrProps = bkrProps;
    }
    public Properties getBrokerProperties() {
	return bkrProps;
    }

    public void setDurableName(String durName) {
	this.durName = durName;
    }
    public String getDurableName() {
	return (durName);
    }

    public void setClientID(String id) {
	this.clientID = id;
    }
    public String getClientID() {
	return clientID;
    }

    public void setTid(long tid)  {
	this.tid = tid;
    }
    public long getTid()  {
	return (tid);
    }

    public void setCxnid(long cxnid)  {
	this.cxnid = cxnid;
    }
    public long getCxnid()  {
	return (cxnid);
    }

}
