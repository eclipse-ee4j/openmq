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
 * @(#)BrokerEventListener.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.jmsservice;

/**
 *
 */
public interface BrokerEventListener {

    /**
     *  Notify the BrokerEventListener that the state of the broker has changed
     *
     *  @param  error The BrokerErrorEvent that conatins additional information
     *          about the broker erros event.
     */
    public void brokerEvent(BrokerEvent error);
    
    /**
     *  Notify the BrokerEventLstener that the broker would like to shut down.<p>
     *
     *  @param event The BrokerEvent that contains information about the reason
     *               why the broker is requesting to exit. The event Id could
     *               be one of:<p>
     *       <UL>
     *         <LI>REASON_SHUTDOWN</LI>
     *         <LI>REASON_RESTART</LI>
     *         <LI>REASON_FATAL</LI>
     *         <LI>REASON_ERROR</LI>
     *         <LI>REASON_EXCEPTION</LI>
     *         <LI>REASON_STOP</LI>
     *       </UL>
     *  @param thr An optional Throwable
     *
     *  @return true Not used
     */
    public boolean exitRequested(BrokerEvent event, Throwable thr);

}
