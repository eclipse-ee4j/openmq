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

package com.sun.messaging.jms.ra;

import java.lang.reflect.Method;

import java.util.logging.Logger;
import javax.jms.JMSException;
import javax.resource.ResourceException;
import javax.resource.spi.UnavailableException;

import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;

/**
 *
 */
public class DirectMessageListener
implements javax.jms.MessageListener {

    /**
     *  MessageListener instance data
     */
    private DirectConnection dc;    
    private Method onMessageMethod;

    private boolean isDeliveryTransacted = false;
    private int maxRedeliverCount = 1;
    
    // noAckDelivery is not implemented
    //private boolean noAckDelivery = false;

    /** The MessageEndpoint for this DirectMessageListener */
    private MessageEndpoint msgEndpoint = null;

    /**
     *  The XAResource that handles XA transactions for this
     *  DirectMessageListener
     */
    private DirectXAResource dxar = null;

    /* Loggers */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.DirectMessageListener";
    protected static transient final String _lgrNameInboundMessage =
            "javax.resourceadapter.mqjmsra.inbound.message";
    protected static transient final Logger _loggerIM =
            Logger.getLogger(_lgrNameInboundMessage);
    protected static transient final String _lgrMIDPrefix = "MQJMSRA_DML";
    protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

    /** Creates a new instance of DirectMessageListener */
    public DirectMessageListener(EndpointConsumer epConsumer,
            MessageEndpointFactory epFactory, DirectConnection dc,
            Method onMessageMethod,
            boolean isDeliveryTransacted,  int maxRedeliverCount,
            boolean noAckDelivery)
    {
        Object params[] = new Object[7];
        params[0] = epConsumer;
        params[1] = epFactory;
        params[2] = dc;
        params[3] = onMessageMethod;
        params[4] = isDeliveryTransacted;
        params[5] = maxRedeliverCount;
        params[6] = noAckDelivery;

        _loggerIM.entering(_className, "constructor()", params);

        //System.out.println("MQRA:ML:Constructor()-omrp:min,max="+spec.getEndpointPoolSteadySize()+","+spec.getEndpointPoolMaxSize());
        //this.epConsumer = epConsumer;
        //this.epFactory = epFactory;
        //this.spec = (com.sun.messaging.jms.ra.ActivationSpec)spec;

        this.dc = dc;
        this.onMessageMethod = onMessageMethod;
        this.isDeliveryTransacted = isDeliveryTransacted;
        this.maxRedeliverCount= maxRedeliverCount;
        
        // noAckDelivery is not implemented
        //this.noAckDelivery = noAckDelivery;

        this.dxar = new DirectXAResource(this.dc, this.dc._getJMSService(),
                this.dc.getConnectionId());
        this.dxar._setUsedByMDB(true);
        try {
            this.msgEndpoint = epFactory.createEndpoint(this.dxar);
        } catch (UnavailableException ex) {
            System.out.println("DirectMessageListener-Exception creating Endpoint:"
                    + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     *
     */
    public void onMessage(javax.jms.Message jmsMsg) {
        DirectPacket dpMsg = (DirectPacket)jmsMsg;
//        boolean delivered = false;
//        boolean acknowledged = false;
        boolean redeliver = true;
        int redeliverCount = 0;
        while (redeliver == true){
            if (this.isDeliveryTransacted) {
                try {
                    this.msgEndpoint.beforeDelivery(this.onMessageMethod);
                } catch (ResourceException ex) {
                    ex.printStackTrace();
                } catch (NoSuchMethodException ex) {
                    ex.printStackTrace();
                }
            }
            try {
                ((javax.jms.MessageListener)this.msgEndpoint).onMessage(jmsMsg);
//                delivered = true;
                redeliver = false;
                try {
                    dpMsg._acknowledgeThisMessageForMDB(this.dxar);
//                    acknowledged = true;
                    this.dxar.setRollback(false, null);
                } catch (JMSException ex) {
                    ex.printStackTrace();
                }
                
            } catch (Exception rte) {
                //Here if onMessage threw any kind of Exception
                if (redeliverCount > this.maxRedeliverCount){
                    //Turn off redelivery and set cause for rollback if in txn
                    redeliver = false;
                    this.dxar.setRollback(true, rte);
                } else {
                    redeliverCount++;
                }
            }
            if (this.isDeliveryTransacted) {
                try {
                    this.msgEndpoint.afterDelivery();
                } catch (ResourceException ex) {
                    ex.printStackTrace();
                }
            }
        }
//        if (acknowledged != true){
//            //Need to acknowledge as Dead
//        }
    }

}
