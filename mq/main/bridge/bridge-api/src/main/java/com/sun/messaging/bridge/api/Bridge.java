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

import java.util.ArrayList;
import java.util.Properties;
import java.util.ResourceBundle;
import org.jvnet.hk2.annotations.Contract;
import org.glassfish.hk2.api.PerLookup;


/**
 * The <CODE>Bridge</CODE> interface is to be implemented by  
 * an external (to the bridge service manager) bridge service
 *
 * @author amyk
 */
@Contract
@PerLookup
public interface Bridge 
{

   final public static String JMS_TYPE = "JMS";
   final public static String STOMP_TYPE = "STOMP";

    public enum State {
        STOPPING { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_STOPPING); }},
        STOPPED  { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_STOPPED); }},
        STARTING { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_STARTING); }},
        STARTED  { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_STARTED); }},
        PAUSING  { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_PAUSING); }},
        PAUSED   { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_PAUSED); }},
        RESUMING { public String toString(ResourceBundle rb) { return rb.getString(BridgeCmdSharedResources.I_STATE_RESUMING); }};

        public abstract String toString(ResourceBundle rb);
    };

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
    public boolean start(BridgeContext bc, String[] args) throws Exception;

    /**
     *  Pause the bridge
     *
     * @param bc the bridge context
     * @param args pause parameters  
     *
     * @throws Exception if unable to pause the bridge
     */
    public void pause(BridgeContext bc, String[] args) throws Exception;

    /**
     *  Resume the bridge
     *
     * @param bc the bridge context
     * @param args resume parameters  
     *
     * @throws Exception if unable to resume the bridge
     */
    public void resume(BridgeContext bc, String[] args) throws Exception;


    /**
     * Stop the bridge
     *
     * @param bc the bridge context
     * @param args stop parameters  
     *
     * @throws Exception if unable to stop the bridge 
     */
    public void stop(BridgeContext bc, String[] args) throws Exception;

    /**
     * List the bridge
     *
     * @param bc the bridge context
     * @param args list parameters  
     * @param rb ResourceBundle to be get String resources for data 
     *
     * @throws Exception if unable to list the bridge 
     */
    public ArrayList<BridgeCmdSharedReplyData> list(BridgeContext bc,
                                                    String[] args, 
                                                    ResourceBundle rb)
                                                    throws Exception;


    /**
     *
     * @return the type of the bridge
     */
    public String getType();

    /**
     *
     * @return true if multiple of this type of bridge can coexist
     */
    public boolean isMultipliable();

    /**
     * Guarantee to be called before start() method is called
     */
    public void setName(String name);

    /**
     *
     * @return get the bridge's name
     */
    public String getName();

    /**
     *
     * @return the current state of the bridge 
     */
    public State getState();

    /**
     * 
     * @return an object of exported service corresponding to the className
     */
    public Object getExportedService(Class c, Properties props) throws Exception;
}
