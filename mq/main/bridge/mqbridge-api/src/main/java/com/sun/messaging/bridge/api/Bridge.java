/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
import java.util.Properties;
import java.util.ResourceBundle;
import org.jvnet.hk2.annotations.Contract;
import org.glassfish.hk2.api.PerLookup;

/**
 * The <CODE>Bridge</CODE> interface is to be implemented by an external (to the bridge service manager) bridge service
 *
 * @author amyk
 */
@Contract
@PerLookup
public interface Bridge {

    String JMS_TYPE = "JMS";
    String STOMP_TYPE = "STOMP";

    enum State {
        STOPPING(BridgeCmdSharedResources.I_STATE_STOPPING),
        STOPPED(BridgeCmdSharedResources.I_STATE_STOPPED),
        STARTING(BridgeCmdSharedResources.I_STATE_STARTING),
        STARTED(BridgeCmdSharedResources.I_STATE_STARTED),
        PAUSING(BridgeCmdSharedResources.I_STATE_PAUSING),
        PAUSED(BridgeCmdSharedResources.I_STATE_PAUSED),
        RESUMING(BridgeCmdSharedResources.I_STATE_RESUMING);

        private final String resourceKey;

        State(String key) {
            resourceKey = key;
        }

        public String toString(ResourceBundle rb) {
            return rb.getString(resourceKey);
        }
    }

    /**
     * Start the bridge
     *
     * @param bc the bridge context
     * @param args start parameters
     *
     * @return true if successfully started; false if started asynchronously
     *
     * @throws Exception if start failed
     */
    boolean start(BridgeContext bc, String[] args) throws Exception;

    /**
     * Pause the bridge
     *
     * @param bc the bridge context
     * @param args pause parameters
     *
     * @throws Exception if unable to pause the bridge
     */
    void pause(BridgeContext bc, String[] args) throws Exception;

    /**
     * Resume the bridge
     *
     * @param bc the bridge context
     * @param args resume parameters
     *
     * @throws Exception if unable to resume the bridge
     */
    void resume(BridgeContext bc, String[] args) throws Exception;

    /**
     * Stop the bridge
     *
     * @param bc the bridge context
     * @param args stop parameters
     *
     * @throws Exception if unable to stop the bridge
     */
    void stop(BridgeContext bc, String[] args) throws Exception;

    /**
     * List the bridge
     *
     * @param bc the bridge context
     * @param args list parameters
     * @param rb ResourceBundle to be get String resources for data
     *
     * @throws Exception if unable to list the bridge
     */
    ArrayList<BridgeCmdSharedReplyData> list(BridgeContext bc, String[] args, ResourceBundle rb) throws Exception;

    /**
     *
     * @return the type of the bridge
     */
    String getType();

    /**
     *
     * @return true if multiple of this type of bridge can coexist
     */
    boolean isMultipliable();

    /**
     * Guarantee to be called before start() method is called
     */
    void setName(String name);

    /**
     *
     * @return get the bridge's name
     */
    String getName();

    /**
     *
     * @return the current state of the bridge
     */
    State getState();

    /**
     *
     * @return an object of exported service corresponding to the className
     */
    Object getExportedService(Class c, Properties props) throws Exception;
}
