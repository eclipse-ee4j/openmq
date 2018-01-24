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
 * @(#)BrokerEvent.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.jmsservice;

import java.util.EventObject;

/**
 *
 */
public class BrokerEvent extends EventObject {

    public static enum Type {
        READY,          // Broker *ready* after successful JMSBroker.start()
        PAUSED,         // Broker 'paused'
        RESUMED,        // Broker 'resumed'
        SHUTDOWN,       // imqcmd shutdown was executed
        RESTART,        // imqcmd restart was executed
        FATAL_ERROR,    // a fatal broker error occurred
        ERROR,          // a serious but non-fatal error occurred
        EXCEPTION,      // an uncaught throwable has been thrown
    }


    /**
     * Shutdown of the broker has been requested through imqcmd
     */
    //LKS public static final int REASON_SHUTDOWN = 0;

    /**
     * Restart of the broker has been requested through imqcmd
     */
    //LKS public static final int REASON_RESTART = 1;

    /**
     * A fatal error of the broker has occurred
     */
    //LKS public static final int REASON_FATAL = 2;

    /**
     * A serious (but non-fatal) error of the broker has occurred
     */
    //LKS public static final int REASON_ERROR = 3;

    /**
     * An uncaught throwable has been thrown
     */
    //LKS public static final int REASON_EXCEPTION = 4;

    /**
     * JMSBroker.stop() was called
     */
    //LKS public static final int REASON_STOP = 5;
    
    /**
     * The Id of this event
     */
    //LKS private int eventId;

    /**
     *  The type of this event
     */
    private BrokerEvent.Type eventType;
    
    /**
     * The message associated with this event
     */
    private String eventMessage;

    /** Creates a new instance of BrokerEvent with source and type */
    public BrokerEvent(Object source, BrokerEvent.Type type) {
        super (source);
        eventType = type;
    }

   /** Creates a new instance of BrokerEvent with source, type and info */
    public BrokerEvent(Object source, BrokerEvent.Type type, String msg) {
        super (source);
        eventType = type;
        eventMessage = msg;
    }

//------------------------------------------------------------------------------

    /**
     *  returns the Type of this event
     *
     *  @return the Type of this event
     */
    public Type getType(){
        return eventType;
    }

    /**
     *  returns the Name of the event Type
     *
     *  @return The name of the event type as declared
     */
    public String getName(){
        return eventType.name();
    }

    /**
     *  returns the Message associated with this event
     *
     *  @return The message associated with this event
     */
    public String getMessage(){
        return eventMessage;
    }

    public String toString() {
        String str = getName();
        if (eventMessage != null)
            str += " : " + eventMessage;
        return str;
    }

}
