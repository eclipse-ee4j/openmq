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

package com.sun.messaging.bridge.service.stomp;

import java.util.ArrayList;
import java.util.Properties;
import java.util.ResourceBundle;
import org.jvnet.hk2.annotations.Service;
import org.glassfish.hk2.api.PerLookup;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.bridge.api.Bridge;
import com.sun.messaging.bridge.api.BridgeUtil;
import com.sun.messaging.bridge.api.BridgeContext;
import com.sun.messaging.bridge.api.BridgeException;
import com.sun.messaging.bridge.api.BridgeCmdSharedReplyData;
import com.sun.messaging.bridge.service.stomp.resources.StompBridgeResources;

/**
 * The Stomp Bridge
 * 
 * @author amyk
 *
 */
@Service(name = Bridge.STOMP_TYPE)
@PerLookup
public class StompBridge implements Bridge {
    
    private final String _type = Bridge.STOMP_TYPE;
    private String _name = null ;

    private State _state = State.STOPPED;

    private StompServer _stompServer = null;

    public StompBridge() {};

    /**
     * Start the bridge
     *
     * @param bc the bridge context
     * @param args start parameters 
     *
     * @return true if successfully started; false if started asynchronously
     *
     * @throws Exception if unable to start the bridge
     */
    public synchronized boolean start(BridgeContext bc, String[] args) throws Exception {

        if (args != null) {
            String[] params = {BridgeUtil.toString(args), getType()};
            bc.logInfo(StompServer.getStompBridgeResources().getString(
                       StompBridgeResources.W_IGNORE_START_OPTION, params), null);
        }

        if (_state == State.STARTED) {
            return true;
        }
        _state = State.STARTING;
        boolean inited = false;
        try {
            _stompServer = new StompServer();
            _stompServer.init(bc);
            inited = true;
            _stompServer.start();
            _state = State.STARTED;
            return true;
        } catch (Exception e) {
            bc.logError(e.getMessage(), e);
            try {
            if (inited) stop(bc, null);
            } catch (Throwable t) {}

            if (!inited) { 
                _stompServer = null;
                throw e;
            }
            throw new BridgeException(e.getMessage(), e, Status.CREATED);
        }
    }

    /**
     * Pause the bridge
     *
     * @param bc the bridge context
     * @param args pause parameters 
     *
     * @throws Exception if unable to pause the bridge
     */
    public void pause(BridgeContext bc, String[] args) throws Exception {
        throw new UnsupportedOperationException(StompServer.getStompBridgeResources().getKString(
              StompBridgeResources.X_OPERATION_NO_SUPPORT, String.valueOf("pause"), getType()));
    }

    /**
     * Resume the bridge
     *
     * @param bc the bridge context
     * @param args resume parameters 
     *
     * @throws Exception if unable to resume the bridge
     */
    public void resume(BridgeContext bc, String[] args) throws Exception {
        throw new UnsupportedOperationException(StompServer.getStompBridgeResources().getKString(
              StompBridgeResources.X_OPERATION_NO_SUPPORT, String.valueOf("resume"), getType()));
    }

    /**
     * Stop the bridge
     *
     * @param bc the bridge context
     * @param args stop parameters 
     *
     * @throws Exception if unable to stop the bridge
     */
    public synchronized void stop(BridgeContext bc, String[] args) throws Exception {
        if (args != null) {
            throw new UnsupportedOperationException(StompServer.getStompBridgeResources().getKString(
            StompBridgeResources.X_OPERATION_NO_SUPPORT, String.valueOf(
            "stop(.., "+BridgeUtil.toString(args)+")"), getType())); 
        }
        if (_stompServer == null) {
            _state = State.STOPPED;
            throw new IllegalStateException(StompServer.getStompBridgeResources().getKString(
                  StompBridgeResources.X_BRIDGE_NOT_INITED, getType(), getName()));
        }
        _state = State.STOPPING;
        _stompServer.stop();
        _state = State.STOPPED;
    }


    /**
     * List the bridge
     *
     * @param bc the bridge context
     * @param args list parameters
     * @param rb ResourceBundle to get String resources for data
     *
     * @throws Exception if unable to list the bridge
     */
    public ArrayList<BridgeCmdSharedReplyData> list(BridgeContext bc, 
                                                    String[] args,
                                                    ResourceBundle rb)
                                                    throws Exception {

        throw new UnsupportedOperationException(StompServer.getStompBridgeResources().getKString(
            StompBridgeResources.X_OPERATION_NO_SUPPORT, String.valueOf("list"), getType()));

    }

    /**
     *
     * @return the type of the bridge
     */
    public String getType() {
        return _type;
    }

    /**
     *
     * @return true if multiple of this type of bridge can coexist
     */
    public boolean isMultipliable() {
        return false;
    }


    /**
     *
     * @return set the bridge's name
     */
    public void setName(String name) {
        _name = name;
    }

    /**
     *
     * @return the bridge's name
     */
    public String getName() {
        return _name;
    }

    /**
     *
     * @return a string representing the bridge's status (length <= 15, uppercase)
     */
    public synchronized State getState() {
        return _state;
    }

    /**
     *
     * @return an object of exported service corresponding to the className
     */
    public Object getExportedService(Class c, Properties props) throws Exception {
        throw new UnsupportedOperationException(StompServer.getStompBridgeResources().getKString(
            StompBridgeResources.X_OPERATION_NO_SUPPORT, String.valueOf("getExportedService"), getType()));
    }

}
