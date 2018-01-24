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

package com.sun.messaging.bridge.api;

import java.util.Locale;
import java.util.Properties;
import org.jvnet.hk2.annotations.Contract;
import javax.inject.Singleton;
import com.sun.messaging.bridge.api.Bridge;

/**
 * Bridge Services Manager interface
 *
 * @author amyk
 */
@Contract
@Singleton
public abstract class BridgeServiceManager 
{

    /**
     * Initialize Bridge Service Manager
     */
    public abstract void init(BridgeBaseContext ctx) throws Exception; 

    /**
     * Start Bridge Service Manager
     */
    public abstract void start() throws Exception; 

    /**
     * Stop Bridge Service Manager 
     */
    public abstract void stop() throws Exception;

   /**
    */
    public abstract BridgeBaseContext getBridgeBaseContext();

    /**
     * @return true if the bridge service manager is running
     */
    public abstract boolean isRunning();

    /**
     *
     */
    public abstract String getAdminDestinationName() throws Exception;
    public abstract String getAdminDestinationClassName() throws Exception;

    public static Object getExportedService(Class c, String bridgeType, Properties props) throws Exception {
        if (c == null) throw new IllegalArgumentException("null class");
        if (bridgeType == null) throw new IllegalArgumentException("null bridge type");

        Bridge b = null;
        Locale loc = Locale.getDefault();
        if (bridgeType.equalsIgnoreCase(Bridge.JMS_TYPE)) {
            b = (Bridge)Class.forName("com.sun.messaging.bridge.service.jms.BridgeImpl").newInstance();
        } else if (bridgeType.toUpperCase(loc).equals(Bridge.STOMP_TYPE)) {
            b = (Bridge)Class.forName("com.sun.messaging.bridge.service.stomp.StompBridge").newInstance();
        } else {
            throw new IllegalArgumentException("Invalid bridge type: "+bridgeType);
        }

        return  b.getExportedService(c, props);
    }

}
