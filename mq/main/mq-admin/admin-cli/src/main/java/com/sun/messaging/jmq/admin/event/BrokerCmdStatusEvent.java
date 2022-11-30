/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.admin.event;

import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;

/**
 * Event class indicating some actions related to Broker Management.
 * <P>
 * The fields of this event include the various pieces of information needed for broker management tasks.
 */
public class BrokerCmdStatusEvent extends CommonCmdStatusEvent {
    private static final long serialVersionUID = 1L;

    /*******************************************************************************
     * BrokerCmdStatusEvent event types use integers 0 - 1000 to avoid overlap with super class and other subclasses
     *******************************************************************************/
    public static final int DESTROY_DST = 0;
    public static final int QUERY_SVC = 1;
    public static final int LIST_SVC = 2;
    public static final int PAUSE_SVC = 3;
    public static final int PAUSE_BKR = 4;
    public static final int RESUME_SVC = 5;
    public static final int RESUME_BKR = 6;
    public static final int QUERY_DST = 7;
    public static final int LIST_DST = 8;
    public static final int CREATE_DST = 9;
    public static final int PURGE_DST = 10;
    public static final int QUERY_BKR = 11;
    public static final int UPDATE_BKR = 12;
    public static final int UPDATE_DST = 13;
    public static final int UPDATE_SVC = 14;
    public static final int RESTART_BKR = 15;
    public static final int SHUTDOWN_BKR = 16;
    public static final int LIST_DUR = 17;
    public static final int DESTROY_DUR = 18;
    public static final int METRICS_SVC = 19;
    public static final int METRICS_BKR = 20;
    public static final int RELOAD_CLS = 21;
    public static final int HELLO = 22;
    public static final int COMMIT_TXN = 23;
    public static final int ROLLBACK_TXN = 24;
    public static final int LIST_TXN = 25;
    public static final int QUERY_TXN = 26;
    public static final int PURGE_DUR = 27;
    public static final int PAUSE_DST = 28;
    public static final int RESUME_DST = 29;
    public static final int METRICS_DST = 30;
    public static final int COMPACT_DST = 32;
    public static final int LIST_CXN = 33;
    public static final int QUERY_CXN = 34;
    public static final int DEBUG = 35;
    public static final int QUIESCE_BKR = 36;
    public static final int TAKEOVER_BKR = 37;
    public static final int LIST_BKR = 38;
    public static final int LIST_JMX = 39;
    public static final int DESTROY_CXN = 40;
    public static final int UNQUIESCE_BKR = 41;
    public static final int RESET_BKR = 42;
    public static final int GET_MSGS = 43;
    public static final int DELETE_MSG = 44;
    public static final int CHECKPOINT_BKR = 45;
    public static final int CLUSTER_CHANGE_MASTER = 46;
    public static final int MIGRATESTORE_BKR = 47;

    private transient BrokerAdmin ba;

    private String svcName = null;
    private ServiceInfo svcInfo = null;

    private String dstName = null;

    private DestinationInfo dstInfo = null;

    private String durName = null;
    private String clientID = null;

    /**
     * Creates an instance of BrokerAdminEvent
     * 
     * @param source the object where the event originated
     * @param type the event type
     */
    public BrokerCmdStatusEvent(Object source, int type) {
        super(source, type);
    }

    /**
     * Creates an instance of BrokerAdminEvent
     * 
     * @param source the object where the event originated
     * @param type the event type
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

    public void setDestinationInfo(DestinationInfo dstInfo) {
        this.dstInfo = dstInfo;
    }

    public DestinationInfo getDestinationInfo() {
        return (dstInfo);
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
}
