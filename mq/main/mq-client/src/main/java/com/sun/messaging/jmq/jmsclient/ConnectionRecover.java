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
 *  @(#)ConnectionRecover.java	1.43 04/03/08
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
//import com.sun.messaging.jmq.io.*;
import com.sun.messaging.*;
//import java.io.*;
import java.util.Enumeration;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
//import com.sun.messaging.jmq.jmsclient.notification.EventHandler;
//import com.sun.messaging.jms.notification.ConnectionReconnectedEvent;

import com.sun.messaging.jmq.jmsclient.resources.*;

/**
 *
 * This class provide a way for the client to recover itself if the
 * connection goes away.
 *
 * <p>The API user is transparent to the recovering attempt.  All
 * consumers are registered to the broker with their original IDs.
 * All producers are added with the same parameters.
 *
 */


public class ConnectionRecover implements Runnable {

    protected ConnectionImpl connection = null;
    //protected ProtocolHandler protocolHandler = null;

    protected final static String iMQConnectionRecover =
        "iMQConnectionRecover-";
    private boolean debug = Debug.debug;

    protected static final int RECOVER_INACTIVE = 0;
    protected static final int RECOVER_STOPPED = 1;
    protected static final int RECOVER_STARTED = 2;
    protected static final int RECOVER_IN_PROCESS = 3;
    protected static final int TRANSPORT_CONNECTED = 4;
    protected static final int RECOVER_SUCCEEDED = 5;
    protected static final int RECOVER_FAILED = 6;
    protected static final int RECOVER_ABORTED = 7;
    
    //string recover state -- used for logging
    private static final String STATES[] = {
    	"RECOVER_INACTIVE",
    	"RECOVER_STOPPED",
    	"RECOVER_STARTED",
    	"RECOVER_IN_PROCESS",
    	"RECOVER_TRANSPORT_CONNECTED",
    	"RECOVER_SUCCEEDED",
    	"RECOVER_FAILED",
    	"RECOVER_ABORTED"
    };
    
    //this flag is set to true when reconnected.
    private int recoverState = RECOVER_INACTIVE;

    private int maxRetries = 100;

    private int failedCount = 0;

    protected Thread recoverThread = null;

    //wait 3 secs and check status.
    private static final int WAIT_TIME = 3000;

    //total wait time out is 600 secs.  This is the time for ReadChannel to
    //wait for this object to become inactive state.
    private static final int MAX_WAIT_COUNT = 200;

    //recover delay time.
    public int recoverDelay = 3000;
    
    private Logger connLogger = ConnectionImpl.connectionLogger;
    
    //enable connection consumer reconnect
    private boolean enableCCReconnect = true;
 
    public ConnectionRecover(ConnectionImpl connection) {
        this.connection = connection;

        String prop = connection.getTrimmedProperty("imq.recover.maxRetries");
        if ( prop != null ) {
            maxRetries = Integer.parseInt(prop);
        }

        prop = connection.getTrimmedProperty("imq.recover.delay");
        if ( prop != null ) {
            recoverDelay = Integer.parseInt(prop);

            if ( connection.isConnectedToHABroker ) {
                if (recoverDelay < ConnectionInitiator.HA_RECONNECT_DELAY) {
                   recoverDelay = ConnectionInitiator.HA_RECONNECT_DELAY;
                }
            }
        }
        
        //HACC
        //we can disable this just in case we breaks something unexpected.
        prop = connection.getTrimmedProperty("imq.recover.connectionConsumer");
        if ( prop != null ) {
            
            boolean flag = Boolean.getBoolean(prop);
            if (flag == false) {
                this.enableCCReconnect = false;
            }
            
        }
        
        logRecoverState(this.RECOVER_INACTIVE);

        //init();
    }

    protected void init() throws JMSException {
        //protocolHandler = connection.protocolHandler;

        Debug.println("*** in ConnectionRecover.init() ..." );

        if ( connection.isConnectedToHABroker ) {

            if (recoverDelay < ConnectionInitiator.HA_RECONNECT_DELAY) {
                recoverDelay = ConnectionInitiator.HA_RECONNECT_DELAY;
            }

            sleep (recoverDelay);
        }

        //if ( connection.isConnectedToHABroker ) {
        //    sleep (recoverDelay);
        //}

        closeProtocolHandler();

        connection.protocolHandler.init(true);
        
        logRecoverState(TRANSPORT_CONNECTED);
       
    }

    /**
     * Start recover in a different thread.
     */
    public void start() {

        //create a new thread and start recover interests, etc
        recoverThread = new Thread(this);
        if (connection.hasDaemonThreads()) {
            recoverThread.setDaemon(true);
        }
        //thread.setName(iMQConnectionRecover + connection.getConnectionID());
        recoverThread.setName(iMQConnectionRecover + "-" +
                              connection.getLocalID() +
                              "-" + connection.getConnectionID());
        
        /**
         * fix for bug 6520902 - client runtime gave up reconnecting.
         * 
         * The state must be set before the thread is started.  This makes it impossible
         * to have the recover thread to finish before the state is set to STARTED again. 
         */
        setRecoverState(RECOVER_STARTED);
        
        recoverThread.start();
        //recoverState = RECOVER_RUNNING;
        //logRecoverState(RECOVER_RUNNING);
    }

    /**
     * Connection recover implementation.
     *
     * The connection recover in HAWK has improved such that client runtime
     * will continue to retry when the cocovery failed.
     *
     *
     *
     */
    public void run() {

        //set the current thread reference.
        connection.protocolHandler.recoverThread = Thread.currentThread();

        try {

            //recoverState = RECOVER_IN_PROCESS;
           
        	setRecoverState(RECOVER_IN_PROCESS);
        	
            recover();

            //recoverState = RECOVER_SUCCEEDED;
            setRecoverState (RECOVER_SUCCEEDED);

            failedCount = 0;

        } catch (JMSException jmse) {
        	
        	setRecoverState(RECOVER_FAILED);
        	
        	//log exception
        	connLogger.log(Level.WARNING, jmse.toString(), jmse);
        	
            //XXX trigger connection recover failed event.
            connection.triggerConnectionReconnectFailedEvent(jmse);
            checkForMaxRetries();
            closeProtocolHandler();
        } finally {

            if (recoverState == RECOVER_SUCCEEDED ) {
            	connection.triggerConnectionReconnectedEvent();
            }

            connection.protocolHandler.recoverThread = null;
            
            //reset state 
        	setRecoverState(RECOVER_INACTIVE);
        }
    }

    /**
     * Recover the connection when it's broken.
     * 1. Delete messages in the session queue and receive queue.
     * 2. Delete unacked messages.
     * 3. Reconnect.
     * 4. hello to broker.
     * 5. Register interests.
     *
     * NOTE: We do not recover if any of the following conditions exists:
     * 1. There are active temporary destinations associated with the
     * connection.
     * 2. There are transacted sessions.
     * 3. There are unacked messages for client acked session.
     */
    protected void recover() throws JMSException {

        try {
            if (debug) {
                Debug.println("BEGIN ConnectionRecover.recover()...");
            }

            //set the current thread reference.
            //protocolHandler.recoverThread = Thread.currentThread();

            /**
             * Synchronized on protocolHandler so that message
             * producers will be block if trying to produce
             * messages during connection recovery.
             *
             * Bug 6157462 -- no need to sync anymore.  all pkt out
             * is synced on the Connection.reconnectSyncObj obj.
             */
            //synchronized ( protocolHandler ) {

            if (connection.isCloseCalled) {
                connection.setReconnecting(false);
                return;
            }

            checkConnectionConsumers();
            
            //HACC -- clear readQs in each CC.
            resetConnectionConsumers();
            
            resetSessions();

            //hand shaking to the broker
            connection.hello(true);
            //this.hello();

            connection.protocolHandler.resetClientID();

            //XXX PROTOCOL3.5
            //Create sessions.
            addSessions();
            
            //register consumers
            addConsumers();

            releaseConnectionConsumers();

            //add producers
            addProducers();
            
            //HACC -- add connection consumer
            //addConnectionConsumers();

            //} //synchronized connection


            //if connection was started, restart the connection
            if ((connection.isStopped == false) && (connection.eventListener == null)) {
                connection.protocolHandler.start();
            }

            if (connection.getEventHandler() != null) {
                connection.getEventHandler().resendConsumerInfoRequests(
                                             connection.protocolHandler);
            }

            if (debug) {
                Debug.info("ConnectionRecover.recover() SUCCESS!!!");
            }

        } catch (JMSException e) {

            if (debug) {
                Debug.println("ConnectionRecover failed.");
                Debug.printStackTrace(e);
            }
            
            throw e;

            //If we catches any exception here, abort ...
            //connection.abort(e);
        } finally {
            releaseConnectionConsumers();

            if (debug) {
                Debug.println("END ConnectionRecover.recover()!!!");
            }

            //protocolHandler.recoverThread = null;

            //if (recoverState && connection.eventListener != null) {
            //    connection.triggerConnectionReconnectedEvent();
            //} else {
            //    connection.setReconnecting(false);
            //}
        }
    }

    /**
     * HACC -- clear cc read queue.
     */
    private void resetConnectionConsumers() {
    	
    	try {
    		int size = this.connection.connectionConsumerTable.size();
    		
    		for (int i = 0; i<size; i++) {
    			ConnectionConsumerImpl ccImpl = 
    			(ConnectionConsumerImpl) connection.connectionConsumerTable.get(i);

                ccImpl.setFailoverInprogress(true);
    			ccImpl.getReadQueue().clear();
    		}
    		
    	} catch (Exception e) {
    		this.connLogger.log(Level.WARNING, e.getMessage(), e);
    	}
    }

    private void releaseConnectionConsumers() {

        try {
            int size = this.connection.connectionConsumerTable.size();

            for (int i = 0; i<size; i++) {
                try {
                    ConnectionConsumerImpl ccImpl =
                    (ConnectionConsumerImpl) connection.connectionConsumerTable.get(i);
                
                    ccImpl.setFailoverInprogress(false);
                } catch (Exception e) {
                    this.connLogger.log(Level.WARNING, e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            this.connLogger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    
    protected void checkConnectionConsumers() throws JMSException {
        
        //from 4.2, by default we enable reconnect for CC.
        //so no more checking for the table size below.
        //HACC
        if (this.enableCCReconnect) {
            return;
        }

        if (connection.connectionConsumerTable.size() > 0) {
            String errorString = AdministeredObject.cr.getKString(
                AdministeredObject.cr.X_CONNECT_RECOVER);

            JMSException jmse =
            new com.sun.messaging.jms.IllegalStateException
               (errorString,AdministeredObject.cr.X_CONNECT_RECOVER);

            ExceptionHandler.throwJMSException(jmse);
        }

    }

    //XXX chiaming REVISIT: handle connectionConsumer?
    protected void resetSessions() throws JMSException {
        Enumeration enum2 = connection.sessionTable.elements();
        while (enum2.hasMoreElements()) {
            SessionImpl session = (SessionImpl) enum2.nextElement();

            //HACC -- sine 4.2, we allow reconnect for connection consumer
            if ((enableCCReconnect==false) &&  session.getMessageListener() != null) {
                String errorString = AdministeredObject.cr.getKString(
                    AdministeredObject.cr.X_CONNECT_RECOVER);


                JMSException jmse =
                new com.sun.messaging.jms.IllegalStateException
                   (errorString,AdministeredObject.cr.X_CONNECT_RECOVER);

                ExceptionHandler.throwJMSException(jmse);
            }

            session.reset();
        }
    }

    protected void addSessions() throws JMSException {
        Enumeration enum2 = connection.sessionTable.elements();
        while (enum2.hasMoreElements()) {
            SessionImpl session = (SessionImpl) enum2.nextElement();
            session.recreateSession();
        }
    }

    /**
     * Reregister all consumers.
     */
    protected void addConsumers() throws JMSException {
        Object tmp[] = connection.interestTable.toArray();
        
        Vector v = new Vector();
        
        for (int i = 0; i < tmp.length; i++) {
        	
        	if ( ((Consumer) tmp[i]).isClosed == false ) {
        		connection.protocolHandler.addInterest((Consumer) tmp[i]);
        	} else {
        		v.add(tmp[i]);
        	}
        	
        }
        
        //clean up closed consumer
        while (v.isEmpty() == false) {
        	Object o = v.firstElement();
        	connection.interestTable.remove( (Consumer) o);
        	v.remove(o);
        }
        
    }

    /**
     * Reregister all producers in a connection
     */
    protected void
        addProducers() throws JMSException {
        Enumeration enum2 = connection.sessionTable.elements();
        while (enum2.hasMoreElements()) {
            SessionImpl session = (SessionImpl) enum2.nextElement();
            addSessionProducers(session);
        }
    }

    /**
     * Add producers in a session. Called by addProducers().
     */
    protected void
        addSessionProducers(SessionImpl session) throws JMSException {
        Enumeration enum2 = session.producers.elements();
        while (enum2.hasMoreElements()) {

            MessageProducerImpl producer =
                (MessageProducerImpl) enum2.nextElement();

            producer.recreateProducer();
        }
    }

    protected synchronized void setRecoverState(int state) {

        if (recoverState != RECOVER_ABORTED) {
            this.recoverState = state;
            logRecoverState(state);
        } else {
        	logRecoverState (RECOVER_ABORTED);
        }

        notifyAll();
    }

    protected synchronized int getRecoverState() {
        return this.recoverState;
    }

    private void closeProtocolHandler() {

        try {
            connection.protocolHandler.close();
        } catch (Exception e) {
            if (debug) {
                Debug.printStackTrace(e);
            }
        }
    }

    /**
     * Check if we should continue to retry.  JMS recover failure may consume
     * broker resources and instability.  We should exit when it is
     * not recoverable.
     */
    private void checkForMaxRetries() {

        failedCount++;

        if ( maxRetries == -1 ) {
            return;
        }

        if (failedCount > maxRetries) {

            setRecoverState(RECOVER_ABORTED);

            if (debug) {
                Debug.println("*** reached max internal retry count: " +
                              maxRetries);
            }
            
            String msg =AdministeredObject.cr.getKString(
					ClientResources.I_CONNECTION_RECOVER_ABORTED, this.connection.getBrokerAddressList(), maxRetries);
            
            this.connLogger.log(Level.SEVERE, msg);
        }
    }


    public synchronized void waitUntilInactive() throws JMSException {

        int wcounter = 0; 
        
        int lctr = 0;

        if ( recoverState == RECOVER_ABORTED ) {
            JMSException jmse = new JMSException ("ConnectionRecover aborted!");
            ExceptionHandler.throwJMSException(jmse);
        }

        while (recoverState != RECOVER_INACTIVE  &&
               (recoverState != RECOVER_ABORTED)) {

            try {
            	
                wait(WAIT_TIME); //wake up every 3 secs.
                
                lctr ++; //log counter
                if (lctr == 5) { //log every 15 secs.
					String msg = AdministeredObject.cr.getKString(
							ClientResources.I_CONNECTION_RECOVER_STATE,
							STATES[getRecoverState()], this.connection
									.getLastContactedBrokerAddress());

					connLogger.log(Level.INFO, msg);
					lctr = 0; //reset.
				}

            } catch (Exception e) {
                connLogger.log(Level.WARNING, e.toString(), e);
            }

            //don't wait if closed.
            if ( connection.isCloseCalled ) {
                setRecoverState(RECOVER_ABORTED);
                return;
            }

            connection.readChannel.closeIOAndNotify();

            wcounter ++;

            //This should never happen.  But we want to exit should this happen.
            if ( wcounter > MAX_WAIT_COUNT ) { //max wait is 10 minutes.
            	
            	//if ( debug ) {
            	//	Debug.getPrintStream().println("*** RUN AWAY THREAD: ConnectionRecover *** ");
            	//	recoverThread.dumpStack();
            	//}
            	
                //abort
                setRecoverState(RECOVER_ABORTED);

                JMSException jmse =
                    new JMSException ("Timeout on ConnectionRecover object.  Broker: " + connection.getLastContactedBrokerAddress());

                //throw jmse;
                ExceptionHandler.throwJMSException(jmse);
            }
        }

    }

    private void sleep (int sleepTime) {

        try {
            int count = sleepTime/WAIT_TIME;

            for ( int i=0; i<count; i++) {

                if (debug) {
                    Debug.println("*** ConnectionRecover, sleeping " + WAIT_TIME * (i+1) + " milli secs");
                }

                Thread.sleep(WAIT_TIME);

                if ( connection.isCloseCalled ) {
                    return;
                }
            }

        } catch (InterruptedException e) {
            ;
        }
    }
    
    /**
     * log recover state.
     * @param state the current recover state.
     */
    private void logRecoverState (int state) {
    	
    	if (connLogger.isLoggable(Level.INFO)) {

			String addr = this.connection.getLastContactedBrokerAddress();

			String msg = AdministeredObject.cr.getKString(
					ClientResources.I_CONNECTION_RECOVER_STATE, STATES[state],
					addr);

			connLogger.log(Level.INFO, msg);
		}
    }

}
