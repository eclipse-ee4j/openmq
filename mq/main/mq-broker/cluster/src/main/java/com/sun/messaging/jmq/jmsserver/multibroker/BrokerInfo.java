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
 * @(#)BrokerInfo.java	1.15 07/02/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker;

import java.io.*;
import java.net.InetAddress;

import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.util.UID;

/**
 * This class encapsulates general information about a broker.
 * Each broker maintains a list of <code> BrokerInfo </code> objects
 * representing the brokers known to be in the same cluster.
 */
public class BrokerInfo implements Serializable {
    static final long serialVersionUID = 6384851141864345643L;

    private static boolean DEBUG = false;

    private BrokerAddress brokerAddr = null;
    private String description = null;
    private long startTime = 0;
    private boolean storeDirtyFlag = false;

    private String heartbeatHostAddress = null ;
    private int heartbeatPort = -1;
    private int heartbeatInterval = 0;

	private Integer clusterProtocolVersion = null;

    private transient String realRemote =  null;

    public BrokerInfo() {
    }

	public Integer getClusterProtocolVersion() {
		return clusterProtocolVersion;
	}

	public void setClusterProtocolVersion(Integer v) {
		this.clusterProtocolVersion = v;
	}

    public void setBrokerAddr(BrokerAddress brokerAddr) {
        this.brokerAddr = brokerAddr;
    }

    public BrokerAddress getBrokerAddr() {
        return brokerAddr;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStoreDirtyFlag(boolean storeDirtyFlag) {
        this.storeDirtyFlag = storeDirtyFlag;
    }

    public boolean getStoreDirtyFlag() {
        return storeDirtyFlag;
    }

    public void setHeartbeatHostAddress(String ip) {
        heartbeatHostAddress =  ip;
    }

    public String getHeartbeatHostAddress() {
        return heartbeatHostAddress;
          
    }

    public void setHeartbeatPort(int p) {
        heartbeatPort = p;
    }

    public int getHeartbeatPort() {
        return heartbeatPort;
    }

    public void setHeartbeatInterval(int s) {
        heartbeatInterval = s;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(
                              "\n\tAddress = " + brokerAddr +
                              "\n\tStartTime = " + startTime +
          ((DEBUG == true) ? ("\n\tDescription = " + description +
                              "\n\tStoreDirty = " + storeDirtyFlag): "")+
                              "\n\tProtocolVersion = " + clusterProtocolVersion);
        if (heartbeatHostAddress != null) {
            sb.append("\n\tHeartbeatHost = " + heartbeatHostAddress +
                      "\n\tHeartbeatPort = " + heartbeatPort);
        }
        return sb.toString();
    }

    public void setRealRemoteString(String str) {
        this.realRemote = str; 
    }

    public String getRealRemoteString() {
        return realRemote;
    }
}

/*
 * EOF
 */
