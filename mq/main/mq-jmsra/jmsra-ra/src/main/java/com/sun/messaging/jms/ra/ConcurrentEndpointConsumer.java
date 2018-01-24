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

import java.util.Iterator;
import java.util.Vector;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.Topic;
import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.spi.endpoint.MessageEndpointFactory;

/**
 *
 */
public class ConcurrentEndpointConsumer extends EndpointConsumer {

    /**
     *  The number of concurrent delvery threads that will be running
     */
    private static final int numConcurrentConsumers = 20;

    /**
     *  The Vector that holds the list of created DirectConnection objects
     */
    private Vector<DirectConnection> connections = 
            new Vector<DirectConnection>(this.numConcurrentConsumers);

    /** Creates a new instance of ConcurrentEndpointConsumer */
    public ConcurrentEndpointConsumer(com.sun.messaging.jms.ra.ResourceAdapter ra,
            MessageEndpointFactory endpointFactory,
            javax.resource.spi.ActivationSpec spec,
            boolean isRADirect)
    throws ResourceException {
        super(ra, endpointFactory, spec);
        //connections = new Vector<DirectConnection>(this.numConcurrentConsumers);
        //this.onMessageMethod = ra._getOnMessageMethod();
//        try {
//            this.isDeliveryTransacted = 
//                    endpointFactory.isDeliveryTransacted(this.onMessageMethod);
//        } catch (NoSuchMethodException ex) {
//            //Assume delivery is non-transacted
//            //Fix to throw NotSupportedException on activation
//            //ex.printStackTrace();
//        }
    }

    /**
     *  Start the Direct MessageConsumer
     */
    protected void startDirectConsumer()
    throws NotSupportedException {
        //cycle through connections and start them
        DirectConnection dc = null;
        Iterator<DirectConnection> k = this.connections.iterator();
        while (k.hasNext()) {
            dc = k.next();
            try {
                dc.start();
            } catch (JMSException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     *  Stop the Direct MessageConsumer
     */
    protected void stopDirectConsumer()
    throws Exception {
        
    }

    protected void createDirectMessageConsumer(/*MessageEndpointFactory epFactory,
            String username, String password, String selector,
            boolean isDurable, String subscriptionName,
            int maxRedeliveryCount, boolean noAckDelivery*/)
    throws NotSupportedException {
        try {
            for (int i=0; i<this.numConcurrentConsumers; i++){
        
                //Use method that avoids allocation via the ConnectionManager
                DirectConnection dc = (DirectConnection)
                        dcf._createConnection(username, password);
                this.connections.add(dc);
                /*
                if (effectiveCId != null) {
                    this.dc._setClientID(effectiveCId);
                }
                */
                DirectSession ds = (DirectSession)dc.createSession(false,
                                Session.CLIENT_ACKNOWLEDGE);
                DirectConsumer msgConsumer = (DirectConsumer)
                    (isDurable
                        ? ds.createDurableSubscriber(
                            (Topic)destination,
                            subscriptionName, selector, false)
                        : ds.createConsumer(destination, selector)
                    );
                DirectMessageListener dMsgListener = 
                        new DirectMessageListener(this, this.endpointFactory,
                        dc,
                        this.onMessageMethod,
                        this.isDeliveryTransacted, 
                        this.exRedeliveryAttempts, this.noAckDelivery);
                msgConsumer.setMessageListener(
                        (javax.jms.MessageListener)dMsgListener);
                //dc.start();
            }
        } catch (JMSException ex) {
            ex.printStackTrace();
        }
    }
}
