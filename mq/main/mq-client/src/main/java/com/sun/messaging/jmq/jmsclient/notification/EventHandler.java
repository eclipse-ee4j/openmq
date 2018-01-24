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
 * @(#)EventHandler.java	1.11 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient.notification;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import javax.jms.JMSException;
import javax.jms.ExceptionListener;

import com.sun.messaging.Destination;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.jmsclient.ConnectionImpl;
import com.sun.messaging.jmq.jmsclient.ProtocolHandler;
import com.sun.messaging.jmq.jmsclient.SequentialQueue;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

import com.sun.messaging.jmq.jmsclient.Debug;
import com.sun.messaging.jms.notification.*;

import com.sun.messaging.ConnectionConfiguration;

/**
 * MQ Client Runtime event handler.
 *
 * The event handler receives event from the client runtime and notifies the
 * connectiion's event listener.
 * <p>
 */
public class EventHandler implements Runnable {

    /**
     * consumer info types (protocol) for consumer event
     */
    private static final int CONSUMER_NOT_READY = 0;
    private static final int CONSUMER_READY     = 1;

    private ConnectionImpl connection = null;

    private Thread handlerThread = null;
    
    protected static final String iMQEventHandler = "iMQEventHandler-";

    private SequentialQueue eventQueue = null;

    private boolean isClosed = false;

    private HashMap consumerEventListeners = new HashMap(); 

    /**
     * This flag is used to prevent duplicate delivery of ConnectionClosedEvent.
     * This is set to true after each delivery for the closed event.  It is
     * set to false after reconnected.
     */
    private boolean closedEventdelivered = false;

    private ExceptionListener exlistener = null;

    public static final long WAIT_TIMEOUT = 2 * 60 * 1000; // 2 minutes.

    private boolean debug = Debug.debug;

    private static  boolean debugEvent =
        Boolean.getBoolean("imq.debug.notification");

    public EventHandler (ConnectionImpl conn) {
        this.connection = conn;

        init();
    }

    private void init() {
        eventQueue = new SequentialQueue (2);
    }

    public synchronized void addConsumerEventListener(
                                        Destination dest, 
                                        EventListener listener)
                                        throws JMSException {

        if (isClosed) {
            throw new JMSException("Event handler is closed");
        }
        consumerEventListeners.put(dest, listener); 
    }

    public synchronized void removeConsumerEventListener(com.sun.messaging.Destination dest) throws JMSException {
        if (consumerEventListeners.get(dest) == null) {
            throw new JMSException("XXXI18N -Consumer event listener for destination "+dest+" not found");
        } 
        consumerEventListeners.remove(dest);
    }

    private synchronized void onEvent (Event event) {

        if ( debugEvent ) {
            Debug.getPrintStream().println(new Date() +
        "-- event triggerred, code = " + event.getEventCode() +
        ", msg = " + event.getEventMessage() );
        }

        if ( isClosed ) {
            return;
        }

        eventQueue.enqueue(event);

        if (handlerThread == null) {
            createHandlerThread();
        }

        notifyAll();
    }

    public synchronized void close() {
        isClosed = true;
        notifyAll();
        consumerEventListeners.clear();
    }

    private void createHandlerThread() {

        synchronized (this) {

            if (handlerThread == null) {

                handlerThread = new Thread(this);
                
                if ( connection.hasDaemonThreads() ) {
                	handlerThread.setDaemon(true);
                }
                
                handlerThread.setName(iMQEventHandler + connection.getLocalID());
                
                handlerThread.start();
            }
        }

    }

    public void run() {

        boolean timeoutExit = false;
        boolean keepRunning = true;

        while ( keepRunning ) {

            timeoutExit = false;

            synchronized (this) {

                if ( shouldWait() ) {
                    try {
                        wait(WAIT_TIMEOUT);
                    } catch (InterruptedException inte) {
                        ;
                    }
                }
            }

            if ( isClosed ) {
                /**
                 * when close() is called, we simply exit the handler.
                 */
                return;
            } else if (eventQueue.isEmpty()) {
                //timeout occurred.
                timeoutExit = true;
            } else {
                /**
                 * get the event from the queue.
                 */
                Event event = (Event) eventQueue.dequeue();

                if ( event instanceof ConsumerEvent ) {
                     deliverConsumerEvent((ConsumerEvent)event);

                } else if ( event instanceof ConnectionExitEvent ) {
                   deliverException (event);
                } else {
                    /**
                     * regular connection event.
                     */
                    deliverConnectionEvent(event);
                }
            }

            /**
             * check if we need to continue.
             */
            keepRunning = shouldContinue( timeoutExit);
        }
    }

    private void deliverException (Event event) {

        try {

            if (exlistener != null  && isClosed == false) {

                ConnectionExitEvent exitEvent = (ConnectionExitEvent) event;

                JMSException jmse = exitEvent.getJMSException();

                exlistener.onException(jmse);

                if ( debugEvent ) {
                   Debug.getPrintStream().println (new Date() +
                   " Exception is delivered to the listener: " + jmse);
                }


            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isClosed = true;
        }
    }

    private boolean shouldWait() {

        if ( eventQueue.isEmpty() && 
             (connection.getEventListener() != null || consumerEventListeners.size() > 0)
             && (isClosed == false) ) {
            return true;
        } else {
            return false;
        }
    }

    private synchronized boolean shouldContinue (boolean timeoutExit) {

        boolean keepRunning = true;
        //exit if closed or timeout.
        if ( isClosed || (timeoutExit && eventQueue.isEmpty()) ) {

            this.handlerThread = null;

            keepRunning = false;

        }

        return keepRunning;
    }

    private void deliverConnectionEvent(Event event) {

        EventListener listener = connection.getEventListener();

        try {

            if ( shouldDeliver (listener, event) ) {
                listener.onEvent(event);

                if ( debugEvent ) {
                   Debug.getPrintStream().println( new Date() +
                   "*** Delivered event, code = " + event.getEventCode() +
                   ", msg = " + event.getEventMessage() );
                }

            }

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            postEventProcess(event);
        }
    }

    private void deliverConsumerEvent(ConsumerEvent event) {

        com.sun.messaging.Destination dest = (com.sun.messaging.Destination)event.getDestination();
       
        EventListener listener = null;

        synchronized (consumerEventListeners) {
		    listener = (EventListener)consumerEventListeners.get(dest);
        }

        try {

            if ( shouldDeliver (listener, event) ) {
                listener.onEvent(event);

                if ( debugEvent ) {
                   Debug.getPrintStream().println( new Date() +
                   "*** Delivered event, code = " + event.getEventCode() +
                   ", msg = " + event.getEventMessage() );
                }

            }

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            postEventProcess(event);
        }
    }

    private boolean shouldDeliver (EventListener listener, Event event) {

        boolean canDeliver = true;

        if ( listener == null || isClosed ) {
            canDeliver = false;
        } else {
            if ( event instanceof ConnectionClosedEvent && closedEventdelivered ) {
               canDeliver = false;
            }
        }

        return canDeliver;
    }

    /**
     * Perform action based on event type and connection state.
     */
    private void postEventProcess (Event event) {
        //String eid = event.getEventCode();

        if ( event instanceof ConnectionReconnectedEvent ) {
            //wake up waiting threads on reconnecting
            connection.setReconnecting(false);
            //reset flag
            closedEventdelivered = false;

            startConnection();

        } else if ( event instanceof ConnectionClosedEvent ) {
            //set delivered flag
            closedEventdelivered = true;
        }
    }

    private void startConnection() {

        try {
            //if the connection is not in stop mode, restart the connection
            if ( connection.getIsStopped() == false ) {
                connection.getProtocolHandler().start();
            }

        } catch (Exception e) {

            if ( this.debug ) {
               e.printStackTrace( Debug.getPrintStream() );
            }
        }
    }

    public void triggerConnectionClosedEvent
        (String evCode, JMSException jmse) {

        if ( connection.getEventListener() == null ) {
            return;
        }

        String evMessage = ClientResources.getResources().getKString(evCode,
                           connection.getLastContactedBrokerAddress());

        if ( evCode.equals(ClientResources.E_CONNECTION_CLOSED_NON_RESPONSIVE)) {
            evMessage = evMessage + ", "  +
                        ConnectionConfiguration.imqPingAckTimeout + ": " +
                        connection.getPingAckTimeout();
        }

        ConnectionClosedEvent event =
        new ConnectionClosedEvent (connection, evCode, evMessage, jmse);

        this.onEvent(event);
    }

    public void triggerConnectionClosingEvent
        (String evCode, long timePeriod) {

        if ( connection.getEventListener() == null ) {
            return;
        }

        String millisecs = String.valueOf(timePeriod),
               secs = String.valueOf(timePeriod/1000);

	Object params[] = new Object[ 3 ];
	params[0] = secs;
	params[1] = millisecs;
	params[2] = connection.getLastContactedBrokerAddress();

        String evMessage = ClientResources.getResources().getKString
                           (evCode, params);

        ConnectionClosingEvent event =
        new ConnectionClosingEvent (connection, evCode, evMessage, timePeriod);

        this.onEvent(event);
    }


    public void triggerConnectionReconnectFailedEvent (JMSException jmse, String brokerAddr) {

        if ( connection.getEventListener() == null ) {
            return;
        }

        String evCode =
        ConnectionReconnectFailedEvent.CONNECTION_RECONNECT_FAILED;

        String evMessage =
        ClientResources.getResources().getKString(evCode, brokerAddr);

        ConnectionReconnectFailedEvent event =
        new ConnectionReconnectFailedEvent (connection, evCode, evMessage, jmse);

        this.onEvent(event);
    }

    public void triggerConnectionReconnectedEvent () {

        if ( connection.getEventListener() == null ) {
            return;
        }

        String brokerAddr = connection.getBrokerAddress();

        String evCode =
        ConnectionReconnectedEvent.CONNECTION_RECONNECTED;

        String evMessage =
        ClientResources.getResources().getKString(evCode, brokerAddr);

        ConnectionReconnectedEvent event =
        new ConnectionReconnectedEvent (connection, evCode, evMessage);

        this.onEvent(event);
    }

    public void triggerConnectionExitEvent(JMSException jmse, ExceptionListener listener) {

        try {

            if ( connection.getEventListener() == null ) {
                return;
            }

            this.exlistener = listener;

            //this event is for MQ internal use only.  This triggered the event
            //handler to call connection exception handler.
            ConnectionExitEvent event =
            new ConnectionExitEvent(connection, ConnectionExitEvent.CONNECTION_EXIT,
                                        jmse.getMessage(), jmse);

            this.onEvent(event);

        } catch (Exception ex) {
            ex.printStackTrace( Debug.getPrintStream() );
        }

    }

    public void triggerConnectionAddressListChangedEvent (String addrList) {

        if ( connection.getEventListener() == null ) {
            return;
        }

        String evCode =
        BrokerAddressListChangedEvent.CONNECTION_ADDRESS_LIST_CHANGED;

        String evMessage =
        ClientResources.getResources().getKString(evCode, addrList);

        BrokerAddressListChangedEvent event =
        new BrokerAddressListChangedEvent (connection, evCode, evMessage, addrList);

        this.onEvent(event);
    }

    public void triggerConsumerEvent (int infoType, String destName, int destType) {

        String evCode = null;
        switch (infoType) {
            case CONSUMER_NOT_READY: 
            evCode = ConsumerEvent.CONSUMER_NOT_READY;
            break;

            case CONSUMER_READY: 
            evCode = ConsumerEvent.CONSUMER_READY;
            break;

            default:
            Debug.println ("Received unknown consumer event: "+infoType+" on destination "+destName); 
            return;
        }

        String evMessage = ClientResources.getResources().getKString(evCode, 
                                           (DestType.isQueue(destType) ? 
                                            DestType.toString(DestType.DEST_TYPE_QUEUE):
                                            DestType.toString(DestType.DEST_TYPE_TOPIC))+":"+destName);

        synchronized(consumerEventListeners) {
            Iterator itr = consumerEventListeners.keySet().iterator(); 
            com.sun.messaging.Destination d = null;
            while (itr.hasNext()) {
                d = (com.sun.messaging.Destination)itr.next();
                if (d.getName().equals(destName) && 
                    d.isQueue() == DestType.isQueue(destType) &&
                    d.isTemporary() == DestType.isTemporary(destType)) {

                    this.onEvent((new ConsumerEvent(d, connection, evCode, evMessage)));
                    return;
                }
            }
        }
        Debug.println ("Listener not found for consumer INFO: "+evMessage);
    }

    public void resendConsumerInfoRequests(ProtocolHandler ph) throws JMSException {
        synchronized(consumerEventListeners) {
            Iterator itr = consumerEventListeners.keySet().iterator(); 
            com.sun.messaging.Destination d = null;
            while (itr.hasNext()) {
                d = (com.sun.messaging.Destination)itr.next();
                ph.requestConsumerInfo(d, false);
            }
        }
    }

}
