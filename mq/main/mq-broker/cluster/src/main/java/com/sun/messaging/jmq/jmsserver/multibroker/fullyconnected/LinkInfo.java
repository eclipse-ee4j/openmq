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
 * @(#)LinkInfo.java	1.4 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected;

import java.io.*;
import java.util.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class LinkInfo implements Serializable {

    public static final int SERVICE_LINK = 0;

    /**
     * non-link request must be negative
     */
    public static final int SERVICE_FILE_TRANSFER = -5000;

    BrokerAddressImpl address;
    BrokerAddressImpl configServer;
    Properties matchProps;

    private int serviceRequestType = SERVICE_LINK; 

    public LinkInfo(BrokerAddressImpl address,
        BrokerAddressImpl configServer, Properties matchProps) {
        this.address = address;
        this.configServer = configServer;
        this.matchProps = matchProps;
    }

    public BrokerAddressImpl getAddress() {
        return address;
    }

    public BrokerAddressImpl getConfigServer() {
        return configServer;
    }

    public Properties getMatchProps() {
        return matchProps;
    }

    public void setServiceRequestType(int type) throws BrokerException {
        if (type != SERVICE_FILE_TRANSFER) {
            throw new BrokerException("Unknown link service request type "+type);
        }
        serviceRequestType = type;
    }

    public int getServiceRequestType() {
        return serviceRequestType;
    }

    public boolean isLinkRequest() {
        return serviceRequestType == SERVICE_LINK;
    }

    public boolean isFileTransferRequest() {
        return serviceRequestType == SERVICE_FILE_TRANSFER;
    }

    public String toString() {
        return "Address = " + address + " configServer = " + configServer;
    }
}

/*
 * EOF
 */
