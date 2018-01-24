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

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.*;

import javax.resource.spi.UnavailableException;
import javax.resource.spi.work.*;
import javax.resource.spi.endpoint.*;

import java.lang.reflect.Method;
import com.sun.messaging.jmq.jmsclient.ConnectionMetaDataImpl;
import com.sun.messaging.jmq.jmsclient.MessageImpl;
import com.sun.messaging.jms.ra.api.JMSRAOnMessageRunner;

/**
 *  Runs a single message through an application server endpoint
 */

public class OnMessageRunner implements Work, JMSRAOnMessageRunner {

    /** the id of this OnMessageRunner */
    protected int omrId;

    /** the message being processed */
    private Message message;

    /** the thread processing the message */
    //private Thread omrThread;

    /** The OnMessageRunnerPool this belongs to */
    private OnMessageRunnerPool omrPool;

    com.sun.messaging.jmq.jmsclient.MessageImpl mqmsg;
    com.sun.messaging.jmq.jmsclient.SessionImpl mqsess;
    
    private DirectPacket dpMsg = null;
    private DirectSession ds = null;

    /** The MessageEndpoint for this MessageListener instance */
    private MessageEndpoint msgEndpoint = null;

    /** The MessageEndpointFactory for this onMessageRunner instance */
    private MessageEndpointFactory epFactory;

    /** The EndpointConsumer for this onMessageRunner instance */
    private EndpointConsumer epConsumer;

    /** The ActivationSpec for this MessageListener instance */
    private com.sun.messaging.jms.ra.ActivationSpec spec = null;

    /** The XAResource for this MessageListener instance */
    private com.sun.messaging.jmq.jmsclient.XAResourceForRA xar = null;
    private DirectXAResource dxar = null;
    private Object xarSyncObj = null;

    /** The onMessage Method that this MessageListener instance will call */
    private Method onMessage = null;

    /** Whether delivery is transacted or not */
    private boolean transactedDelivery = false;

    /** Whether the endpoint acquired is valid or not */
    private boolean endpointValid = false;

    /** Whether running in Direct mode or not */
    private boolean useDirect = false;

    /* Loggers */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.OnMessageRunner";
    protected static transient final String _lgrNameInboundMessage =
            "javax.resourceadapter.mqjmsra.inbound.message";
    protected static transient final Logger _loggerIM =
            Logger.getLogger(_lgrNameInboundMessage);
    protected static transient final String _lgrMIDPrefix = "MQJMSRA_MR";
    protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";
 
    /** Constructs an OnMessageRunner */
    public OnMessageRunner(int omrId, OnMessageRunnerPool omrPool,
            MessageEndpointFactory epFactory,
            EndpointConsumer epConsumer,
            ActivationSpec spec, boolean useDirect)
    {
        Object params[] = new Object[5];
        params[0] = omrPool;
        params[1] = epFactory;
        params[2] = epConsumer;
        params[3] = spec;
        params[4] = Boolean.valueOf(useDirect);

        _loggerIM.entering(_className, "constructor()", params);

        this.omrId = omrId;
        this.omrPool = omrPool;
        this.epFactory = epFactory;
        this.epConsumer = epConsumer;
        this.spec = spec;
        this.useDirect = useDirect;

        if (useDirect) {
            this.dxar = new DirectXAResource(epConsumer.getDirectSession().dc,
                    epConsumer.getDirectSession().jmsservice,
                    epConsumer.getDirectSession().connectionId);
            //Flag that the XAResource is used for an MDB
            this.dxar._setUsedByMDB(true);
        } else {
            while (xar == null) {
                try {
                    xar = new com.sun.messaging.jmq.jmsclient.XAResourceForRA(this, epConsumer.xac);
                    break;
                } catch (JMSException jmse) {
                	_loggerIM.log(Level.INFO,_lgrMID_WRN +"Exception on XAResource creation-",jmse);
                    try {
                        Thread.sleep(2000);
                    } catch (Exception e) {
                    }
                    //System.err.println("MQRA:OMR:Running as non-transacted");
                }
            }
        }
        this.xarSyncObj = (useDirect ? this.dxar : this.xar);
        ////Acquire endpoint from epFactory here
        onMessage = epConsumer.ra._getOnMessageMethod();

        try {
            transactedDelivery = epFactory.isDeliveryTransacted(onMessage);
        } catch (java.lang.NoSuchMethodException nsme) {
            //assume non-transactedDelivery
        }
        for (int i = 1; i < 6; i++ ) {
            try {
                //If it's not deactivated
                if (epConsumer.deactivated != true) {
                    msgEndpoint = epFactory.createEndpoint(useDirect ? dxar : xar);
                    break;
                }
            } catch (UnavailableException ue) {
                try {
                    _loggerIM.info(_lgrMID_INF +"createEndpoint-UnavailableException:Sleeping for:"+i*200);
                    Thread.sleep(i * 200L);
                } catch (InterruptedException ie) {
                }
            }    
        }
        if (msgEndpoint == null) {
            //Could not acquire - shut down delivery of messages in this session
            _loggerIM.info(_lgrMID_INF +"Endpoint Unavailable:Marking OMR as invalid-omrId="+omrId+" for:"+spec.toString());

            //_logger.log(Level.SEVERE, "MQRA:ML:Endpoint Unavailable:Shutting down delivery for "+spec.toString());
            //mqmsg._getSession().closeFromRA();
            //endpoint should be shutdown normally by AS via RA.endpointDeactivation()
            return;
        } else {
            endpointValid = true;
        }
    }

    public void release() {
        //System.out.println("MQRA:OMR:release():tName="+Thread.currentThread().getName());
    }

    public void releaseEndpoint() {
        //System.out.println("MQRA:OMR:releaseEndpoint():omrId="+omrId+" tName="+Thread.currentThread().getName());
        if (msgEndpoint!= null) {
            msgEndpoint.release();
        }
    }

    public void invalidate() {
        //System.out.println("MQRA:OMR:invalidateEndpoint():omrId="+omrId+" tName="+Thread.currentThread().getName());
        endpointValid = false;
    }


    public void run() {
        synchronized (this.xarSyncObj) {
        boolean sendUndeliverableMsgsToDMQ = spec.getSendUndeliverableMsgsToDMQ();
        if (!endpointValid) {
            _loggerIM.fine(_lgrMID_INF +"run:msgEP invalid-Ack Undeliverable & remove OMR fr pool-omrId="+omrId+":msg="+mqmsg);

            omrPool.removeOnMessageRunner(this);
            try {
                if (this.useDirect) {
                    //Acknowledge this message as DEAD
                } else {
                    mqsess.acknowledgeUndeliverableFromRAEndpoint(mqmsg, xar, sendUndeliverableMsgsToDMQ);
                }
            } catch (JMSException jmse) {
            	_loggerIM.log(Level.WARNING,_lgrMID_WRN +"run:msgEP invalid:JMSException on Ack Undeliverable-",jmse);
            }
            return;
        }
        //System.err.println("MQRA:OMR:run:omrId="+omrId+" in thread name="+Thread.currentThread().getName()+" transacted="+transactedDelivery);

        ClassLoader cl = spec.getContextClassLoader();
        int exRedeliveryAttempts = spec.getEndpointExceptionRedeliveryAttempts();
        int exRedeliveryInterval = spec.getEndpointExceptionRedeliveryInterval();
        //Deliver message to msgEndpoint
        boolean redeliver = true;
        int msgRedeliveryCount = 0;
        int retryCount = 0;
        try {
            if (this.useDirect) {
                msgRedeliveryCount = dpMsg.getIntProperty(ConnectionMetaDataImpl.JMSXDeliveryCount);
                dpMsg.setClientRetries(0);
            } else {
                msgRedeliveryCount = mqmsg.getIntProperty(ConnectionMetaDataImpl.JMSXDeliveryCount);
                mqmsg.setClientRetries(0);
            }
        } catch (JMSException e) {}
        int redeliveryCount = (msgRedeliveryCount > 1) ? (msgRedeliveryCount - 1) : 0;
        while (redeliver == true) {
            try {
                if (transactedDelivery) {
                    //System.err.println("MQRA:OMR:run:beforeDelivery()");
                    msgEndpoint.beforeDelivery(onMessage);
                }
                try {
                    //System.out.println("MQRA:OMR:run:Delivering to onMessage()");
                    if (cl != null) {
                        //System.out.println("MQRA:OMR:run:Setting ContextClassLoader:"+cl.toString());
                        try {
                            Thread.currentThread().setContextClassLoader(cl);
                        } catch (Exception sccle) {
                        	_loggerIM.log(Level.WARNING,_lgrMID_WRN +"run:Exception setting ContextClassLoader:",sccle);
                        }
                    }
                    //System.err.println("MQRA:OMR:run:Deliver Msg:JMSRedeliver="+message.getJMSRedelivered()+" Msg="+message.toString());
                    redeliveryCount++;
                    if (redeliveryCount > 1) {
                        if (this.useDirect) {
                            ((DirectPacket) message).updateDeliveryCount(redeliveryCount);
                        } else {
                            ((MessageImpl) message).updateDeliveryCount(redeliveryCount);
                        }
                    }
                    ((javax.jms.MessageListener)msgEndpoint).onMessage(message);
                    redeliver = false;
                    //System.err.println("MQRA:OMR:run:Delivered successfully-Msg="+message.toString());
                    try {
                        if (this.useDirect) {
                            if (redeliveryCount > 1)
                                dpMsg.updateDeliveryCount(redeliveryCount);
                            //Acknowledge direct message
                            this.dpMsg._acknowledgeThisMessageForMDB(this.dxar);
                            this.dxar.setRollback(false, null);
                        } else {
                            if (redeliveryCount > 1)
                                mqmsg.updateDeliveryCount(redeliveryCount);
                            mqsess.acknowledgeFromRAEndpoint(mqmsg, xar);
                            //System.err.println("MQRA:OMR:run:Acknowledged successfully");
                            //System.err.println("MQRA:OMR:run:omrId="+omrId+" msg acknowledged-msg="+mqmsg.toString());
                            xar.setRollback(false, null);
                        }
                    } catch (JMSException jmse) {
                    	_loggerIM.log(Level.WARNING,_lgrMID_WRN +"run:JMSException on message acknowledgement:Rolling back if in txn",jmse);
                        if (this.useDirect) {
                            this.dxar.setRollback(true, jmse);
                        } else {
                            xar.setRollback(true, jmse);
                        }
                    }
                } catch (Exception rte) {
                	_loggerIM.log(Level.WARNING,_lgrMID_WRN +"run:Caught Exception from onMessage():Redelivering:",rte);
                    try {
                        retryCount++;
                        if (this.useDirect) {
                            dpMsg.setClientRetries(retryCount);
                        } else {
                            mqmsg.setClientRetries(retryCount);
                        }
                        message.setJMSRedelivered(true);
                        if(message instanceof BytesMessage){
                        	((BytesMessage)message).reset();
                        } else if (message instanceof StreamMessage) {
                        	((StreamMessage)message).reset();
                        }
                    } catch (Exception jmsesr) {
                    	_loggerIM.log(Level.WARNING,_lgrMID_WRN +"run:Exception on setJMSRedelivered():",jmsesr);
                    }
                    if (exRedeliveryAttempts > 0) {
                        try {
                            //System.out.println("MQRA:OMR:run:RedeliverInterval-start");
                            Thread.sleep(exRedeliveryInterval);
                            //System.out.println("MQRA:OMR:run:RedeliverInterval-stop");
                        } catch (InterruptedException ie) {
                            //System.out.println("MQRA:OMR:run:RedeliverInterval-interrupted");
                        }
                        exRedeliveryAttempts -= 1;
                    } else {
                    	_loggerIM.fine(_lgrMID_INF +"run:Exhausted redeliveryAttempts-msg="+message.toString());
                    	_loggerIM.fine(_lgrMID_INF +"run:Exhausted redeliveryAttempts-spec="+spec.toString());
                        //if (false) { //ackSendToDMQ not tested  - disabling for alpha
                        if (sendUndeliverableMsgsToDMQ) {
                        	_loggerIM.info(_lgrMID_INF +"run:Message returned & marked for routing to the DMQ");
                        } else {
                        	_loggerIM.info(_lgrMID_INF +"run:Message returned & marked for redelivery by the broker");
                        }
                        try {
                            if (this.useDirect){
                                this.dpMsg._acknowledgeThisMessageAsDeadForMDB(this.dxar);
                                _loggerIM.fine(_lgrMID_INF +"run:omrId="+omrId+":Acked Undeliverable-Msg="+dpMsg.toString());
                            } else {
                                mqsess.acknowledgeUndeliverableFromRAEndpoint(mqmsg, xar, sendUndeliverableMsgsToDMQ);
                                _loggerIM.fine(_lgrMID_INF +":OMR:run:omrId="+omrId+":Acked Undeliverable-Msg="+mqmsg.toString());
                            }
                        } catch (JMSException jmse) {
                        	_loggerIM.log(Level.WARNING,_lgrMID_WRN +"run:JMSException on Acked Undeliverable-",jmse);
                        }
                        redeliver = false;
                    }
                }
                if (transactedDelivery) {
                    //System.err.println("MQRA:OMR:run:afterDelivery()");
                    msgEndpoint.afterDelivery();
                }
            } catch (Throwable t) {
            	_loggerIM.log(Level.WARNING,_lgrMID_WRN +"run:onMessage caught Throwable-before/on/afterDelivery:Class="
                        +t.getClass().getName()
                        +"Msg="+t.getMessage(),t);
                redeliver = false;
            }
        }
        message = null;
        //System.out.println("MQRA:OMR: run:putting back omrId="+omrId);
        omrPool.putOnMessageRunner(this);
        }
    }

    public boolean equals(Object other) {
        if (other instanceof OnMessageRunner) {
            if (omrId == ((OnMessageRunner)other).getId()) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public int hashCode() {
        return omrId;
    }

    public void onMessage(Message msg) {

        if (this.useDirect) {
        	// RADIRECT
            this.message = msg;
            this.dpMsg = (DirectPacket)message;
            this.ds = (DirectSession)epConsumer.getDirectSession();
            //mqmsg._setConsumerInRA();

            //System.out.println("MQRA:OMR:onMessage():starting Work on omrId="+omrId);
            try {
                epConsumer.ra.workMgr.scheduleWork(this);
            } catch (WorkException we) {
            	_loggerIM.log(Level.INFO,_lgrMID_INF +"onMessage:WorkException-"+we.getMessage()+" on omrId="+omrId,we);
            }
        } else {
        	// LOCAL, REMOTE, APIDIRECT
            //System.err.println("MQRA:OMR:onMessage()");
            message = msg;
            mqmsg = (com.sun.messaging.jmq.jmsclient.MessageImpl)message;
            mqsess = (com.sun.messaging.jmq.jmsclient.SessionImpl)epConsumer.getXASession();
            mqmsg._setConsumerInRA();

            //System.out.println("MQRA:OMR:onMessage():starting Work on omrId="+omrId);
            try {
                epConsumer.ra.workMgr.startWork(this);
            } catch (WorkException we) {
            	_loggerIM.log(Level.INFO,_lgrMID_INF +"onMessage:WorkException-"+we.getMessage()+" on omrId="+omrId,we);;
            }
            ////////Replaced with Work above
            //omrThread = new Thread(this);
            //omrThread.setName("imqOnMessageRunner-Id#"+omrId);
            //
            //omrThread.start();
            ////////
        }
    }

    public int getId() {
        return omrId;
    }

    public boolean isValid() {
        return endpointValid;
    }
    
    public EndpointConsumer getEndpointConsumer() {
    	return this.epConsumer;
    }

}

