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

import javax.jms.*;

import java.util.Vector;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.resource.spi.endpoint.MessageEndpointFactory;

import com.sun.messaging.jmq.jmsservice.ConsumerClosedNoDeliveryException;

/**
 *  Holds a pool of OnMessageRunner objects.
 */

public class OnMessageRunnerPool {

    /** The maximum number of OnMessageRunner objects allocated */
    private int max;

    /** The minimum number of OnMessageRunner objects allocated */
    private int min;
    
    /** The number of free OnMessageRunner objects */
    private int freeCount;

    /** The slack - number of OnMessageRunner objects that can and have not been created */
    private int slackCount;

    /** The MessageEndpointFactory for this onMessageRunnerPool */
    private MessageEndpointFactory epFactory;

    /** The EndpointConsumer for this onMessageRunnerPool */
    private EndpointConsumer epConsumer;

    /** The ActivationSpec for this MessageListener instance */
    private ActivationSpec spec = null;
    private boolean useDirect = false;

    private ArrayList<OnMessageRunner> onMessageRunners;

    /** The list of available OnMessageRunner objects */
    private Vector<OnMessageRunner> available;

    private volatile boolean deactivating;

    /* Loggers */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.OnMessageRunnerPool";
    protected static transient final String _lgrNameInboundMessage =
            "javax.resourceadapter.mqjmsra.inbound.message";
    protected static transient final Logger _loggerIM =
            Logger.getLogger(_lgrNameInboundMessage);
    protected static transient final String _lgrMIDPrefix = "MQJMSRA_RP";
    protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

    /** Constructs an OnMessagePoolRunner */
    public OnMessageRunnerPool(MessageEndpointFactory epFactory,
                EndpointConsumer epConsumer,
                ActivationSpec spec, boolean useDirect)
    {
        Object params[] = new Object[4];
        params[0] = epFactory;
        params[1] = epConsumer;
        params[2] = spec;
        params[3] = Boolean.valueOf(useDirect);

        _loggerIM.entering(_className, "constructor()", params);

        int minimum, maximum;

        this.epFactory = epFactory;
        this.epConsumer = epConsumer;
        this.spec = spec;
        this.useDirect = useDirect;

        minimum = spec.getEndpointPoolSteadySize();
        maximum = spec.getEndpointPoolMaxSize();

        if (maximum < 1) { this.max = 10; }
        else { this.max = maximum; }
        if (minimum < 1 || minimum > max) { this.min = 1; }
        else { this.min = minimum; }

        available = new Vector<OnMessageRunner>(min);

        /* list of all onMessageRunner objects created */
        onMessageRunners = new ArrayList<OnMessageRunner>(min);

        OnMessageRunner omr;

        for (int i=0; i<min; i++) {
            omr = new OnMessageRunner(i, this, epFactory, 
                    epConsumer, spec, useDirect);
            onMessageRunners.add(omr);
            available.addElement(omr);
        }
        freeCount = min;
        slackCount = max - min;
    }

    /**
     * Get an OnMessageRunner from the pool
     *
     *
     */
    public synchronized OnMessageRunner
    getOnMessageRunner() throws JMSException {

        OnMessageRunner omr;

        //System.out.println("MQRA:OMRP:getOMR()");
        if (available.size() == 0) {
            if (deactivating)
                throw new ConsumerClosedNoDeliveryException("MQRA:OMRP:getOMR:OnMessageRunnerPool is in deactivating");

            //System.out.println("MQRA:OMRP:getOMR:size=0");
            if (slackCount > 0) {
                //System.out.println("MQRA:OMRP:getOMR:adding from slack");
                omr = new OnMessageRunner(onMessageRunners.size(), this, 
                        epFactory, epConsumer, spec, useDirect);
                onMessageRunners.add(omr);
                slackCount--;
                //System.out.println("MQRA:OMRP:getOMR:slack-Getting omr Id="+omr.omrId);
                return omr;
            }
        }
            
        while (available.size() == 0) {
            if (deactivating)
                throw new ConsumerClosedNoDeliveryException("MQRA:OMRP:getOMR:OnMessageRunnerPool is in deactivating");

            try {
                //System.out.println("MQRA:OMRP:getOMR:Waiting...");
                wait();
            } catch (InterruptedException ie) {
                //System.out.println("MQRA:OMRP:getOMR:Interrupted while waiting...throwing exception-"+ie.getMessage());
                JMSException jmse = new com.sun.messaging.jms.JMSException(
                    "MQRA:OMRP:Unable to get OMR from pool:"+ie.getMessage());
                jmse.setLinkedException(ie);
                throw jmse;
            }
        }
        omr = (OnMessageRunner)available.elementAt(available.size()-1);
        //System.out.println("MQRA:OMRP:getOMR:Getting omr at index="+(available.size()-1)+" with omrId="+omr.omrId);
        available.removeElementAt(available.size()-1);
        freeCount--;
        return omr;
    }


    /**
     * Put an OnMessageRunner back to the pool
     *
     */
    public synchronized void
    putOnMessageRunner(OnMessageRunner omr) {

        //System.out.println("MQRA:OMRP:Putting back omrId="+omr.omrId+" at index="+available.size());
        available.addElement(omr);

        //XXX:reduction logic - here if needed

        freeCount++;
        notifyAll();
    }

    public synchronized void
    removeOnMessageRunner(OnMessageRunner omr) {
        int index;

        //System.out.println("MQRA:OMRP:removeOMR:Id="+omr.getId());
        index = onMessageRunners.indexOf(omr);
        if (index != -1) {
            //System.out.println("MQRA:OMRP:removeOMR:Id="+omr.getId()+" at index="+index);
            onMessageRunners.remove(index);
            notifyAll();
            freeCount++;
            if (slackCount < (max-min)) {
                slackCount++;
            }
        }
    }

    public synchronized void
    waitForAllOnMessageRunners() throws JMSException {
        //System.out.println("MQRA:OMRP:wfaOMRs():allocated="+onMessageRunners.size()+" available="+available.size());
        while (available.size() < onMessageRunners.size()) {
            try {
                //System.out.println("(MQRA:OMRP:wfaOMRs:Waiting...");
                wait();
            } catch (InterruptedException ie) {
                //System.out.println("MQRA:OMRP:wfaOMRs:Interrupted while waiting...throwing exception-"+ie.getMessage());
                JMSException jmse = new com.sun.messaging.jms.JMSException(
                    "MQRA:OMRP:Didnot finish waiting for OMRs to return:"+ie.getMessage());
                jmse.setLinkedException(ie);
                throw jmse;
            }
        }
        //System.out.println("MQRA:OMRP:wfaOMRs:DONE:allocated="+onMessageRunners.size()+" available="+available.size());
    }

    public synchronized void
    releaseOnMessageRunners() {
        this.deactivating = true;
        //System.out.println("MQRA:OMRP:releaseOMRs()");
        for (int i= 0; i<onMessageRunners.size(); i++) {
            ((OnMessageRunner)onMessageRunners.get(i)).releaseEndpoint();
        }
        onMessageRunners.clear();
        available.removeAllElements();
        notifyAll();
        freeCount = 0;
        slackCount = max;
        //System.out.println("MQRA:OMRP:releaseOMRs-done");
    }

    public synchronized void
    invalidateOnMessageRunners() {
        //System.err.println("MQRA:OMRP:invalidateOMRs()");
        for (int i= 0; i<onMessageRunners.size(); i++) {
            ((OnMessageRunner)onMessageRunners.get(i)).invalidate();
        }
        releaseOnMessageRunners();
        //System.err.println("MQRA:OMRP:invalidateOMRs-done");
    }
}

