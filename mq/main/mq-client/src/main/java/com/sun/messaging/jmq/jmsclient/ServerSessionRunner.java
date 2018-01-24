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
 *  %W% %G%
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.*;
import java.util.logging.Level;
import java.io.PrintStream;
import javax.jms.*;
import com.sun.messaging.AdministeredObject;

/**
 * Provide run() method and maintain server session state for a Session
 *
 * @see com.sun.messaging.jmq.jmsclient.SessionImpl
 */

class ServerSessionRunner {

    //states corresponding to Session start/stop/close
    private static final int SERVERSESSION_RUN = 0;
    private static final int SERVERSESSION_STOP = 1;
    private static final int SERVERSESSION_CLOSE = 2;
    
    //HACC -- reset server session
    private static final int SERVERSESSION_RESET = 3;

    private SessionImpl session;

    private Vector serverSessionMessageQ = new Vector();

    private Object serverSessionSyncObj = new Object();
    private int serverSessionInProcess = 0;
    private Thread serverSessionThread = null;
    private boolean serverSessionInWait = false;
    private int serverSessionState = SERVERSESSION_RUN;
    private boolean reset = false;
    private MessageListener messageListener = null;

    protected MessageImpl currentMessage = null;

    public ServerSessionRunner(SessionImpl session, MessageListener listener) {
        this.session = session;
        messageListener = listener;
    }

    protected MessageListener
    getMessageListener() throws JMSException {
        return messageListener;
    }

    protected void
    setMessageListener(MessageListener listener) throws JMSException {
        synchronized(serverSessionSyncObj) {
            if (serverSessionInProcess > 0 ) {
                String errorString = AdministeredObject.cr.getKString(
                               AdministeredObject.cr.X_SVRSESSION_INPROGRESS);
                throw new javax.jms.IllegalStateException(errorString,
                               AdministeredObject.cr.X_SVRSESSION_INPROGRESS);
            }
            this.messageListener = listener;
        }
    }

    /**
     * called from Session.run()
     * The thread running this would be a App Server thread
     *
     * @exception RuntimeException if processing fails 
     *
     * Note that RuntimeException thrown from messageListener.onMessage() will
     * not be propagated (a stacktrace is printed to standard output), instead 
     * message delivery is continued:
     * for transacted session or client-acknowledge mode, the next message if any
     * is delivered; otherwise a one time redelivery is tried before deliver the
     * next message.
     */
    protected void run() {

        MessageImpl message;
        serverSessionProcessStart();
        currentMessage = null;
        int size = serverSessionMessageQ.size();
            
        serverSessionProcess(size);
        for (int i = 0; i < size; i++) {
        	
             try {
            	 
             this.checkState();
            	   
             serverSessionPreOnMessage();
             if (serverSessionState == SERVERSESSION_CLOSE || reset) {
                 serverSessionProcess(i-size);
                 serverSessionMessageQ.clear();
                 break;
             }
             
             SSMessage ssm = (SSMessage)serverSessionMessageQ.elementAt(0);
             message = ssm.message;
             currentMessage = message;

             if (!ssm.isDMQMessage && message._isExpired()) {
                 serverSessionMessageQ.removeElementAt(0);
                 currentMessage = null;
                 session.acknowledgeExpired(message);
             } else {

             if (ssm.serversession instanceof com.sun.messaging.jmq.jmsspi.ServerSession)
                 ((com.sun.messaging.jmq.jmsspi.ServerSession)ssm.serversession).beforeMessageDelivery(message);
             try {

             boolean delivered = onMessage(message);
             currentMessage = null;
             serverSessionMessageQ.removeElementAt(0);
             if (delivered) {
                 session.acknowledge(message);
             }

             } catch (JMSException e) {
            	 //HACC -- set transaction as rollbacl only mode.
            	 this.session.isRollbackOnly = true;
            	 this.session.rollbackCause = e;
            	 
             throw new RuntimeException(e.getMessage());
             } finally {
             if (ssm.serversession instanceof com.sun.messaging.jmq.jmsspi.ServerSession)
                 ((com.sun.messaging.jmq.jmsspi.ServerSession)ssm.serversession).afterMessageDelivery(message);
             }

             } //!_isExpired

             } catch (Throwable e) {
            	 
            	//HACC -- set transaction as rollbacl only mode.
            	 this.session.isRollbackOnly = true;
            	 this.session.rollbackCause = e;
            	 
             serverSessionProcess(i-size);
             if (e instanceof Error) throw (Error)e;
             if (e instanceof RuntimeException) throw (RuntimeException)e;
             throw new RuntimeException(e.getMessage());
             }

             serverSessionProcess(-1);
        }
    }

    /**
     * called from run() to call messageListener.onMessage()
     *
     * @return true if messageListener.onMessage() is successfully called
     * @exception JMSException failure other than onMessage() RuntimeException
     */
    private boolean onMessage(Message message) throws JMSException {
        boolean delivered = true;
        try {
            messageListener.onMessage(message);
        }
        catch (RuntimeException e1) {
            
        	//Debug.printStackTrace(e1);
            
        	ExceptionHandler.rootLogger.log(Level.WARNING, e1.getMessage(), e1);
        	
            delivered = false;
            /* AppServer maintains invocation state of MDB
            if (session.getTransacted() == false && 
                session.acknowledgeMode != Session.CLIENT_ACKNOWLEDGE) {
                message.setJMSRedelivered(true);
                try {
                messageListener.onMessage(message);
                } catch (RuntimeException e2) {
                delivered = false;
                Debug.printStackTrace(e2);
                }                    
            }
            */
        }
        return delivered;
    }

    /**
     * mark server session in process 
     * only one thread can run a server session at a time
     */
    private void serverSessionProcessStart() {
        synchronized(serverSessionSyncObj) {
            if (serverSessionInProcess > 0) {
                String errorString = AdministeredObject.cr.getKString(
                             AdministeredObject.cr.X_SVRSESSION_INPROGRESS);
                throw new java.lang.IllegalStateException(errorString);
            }
            serverSessionInProcess = 1;
            serverSessionThread = Thread.currentThread();
        }
    }

    /**
     * controls server session in process duration.
     * it must always be called after serverSessionProcessStart()
     */
    private void serverSessionProcess(int size) {
        synchronized(serverSessionSyncObj) {
            if (size < 0) {
                serverSessionInProcess += size;
            }
            else {
                serverSessionInProcess = size;
                if (messageListener == null) {
                    serverSessionInProcess = 0;
                }
            }
            if (serverSessionInProcess <= 0) {
                serverSessionInProcess = 0;
                serverSessionThread = null;
                currentMessage = null;
                serverSessionSyncObj.notifyAll();
            }
            if (messageListener == null) {
                String errorString = AdministeredObject.cr.getKString(
                               AdministeredObject.cr.X_SVRSESSION_INVALID);
                throw new java.lang.IllegalStateException(errorString);
            }
        }
    }

    /**
     * before each call messageListener.onMessage()
     * check whether not should stop
     */
    private void serverSessionPreOnMessage() {
        synchronized(serverSessionSyncObj) {
             while (serverSessionState == SERVERSESSION_STOP && !reset) {
                 try {
                     serverSessionInWait = true;
                     serverSessionSyncObj.notifyAll();
                     serverSessionSyncObj.wait();
                 } catch (InterruptedException e) {
                     String errorString = AdministeredObject.cr.getKString(
                                           AdministeredObject.cr.X_INTERRUPTED);
                     throw new java.lang.RuntimeException(errorString);
                 } finally {
                     serverSessionInWait = false;
                 }
             }
        }
    }

    /**
     * called from session start()
     */
    protected void serverSessionRun() {
        synchronized(serverSessionSyncObj) {
            serverSessionState = SERVERSESSION_RUN;
            serverSessionSyncObj.notifyAll();
        }
    }

    /**
     * called from session stop()
     */
    protected void serverSessionStop() throws JMSException {
        synchronized(serverSessionSyncObj) {
            if (messageListener == null) {
                serverSessionState = SERVERSESSION_STOP;
                return;
            }
            if (serverSessionThread != Thread.currentThread()) {
                serverSessionState = SERVERSESSION_STOP;
                try {

                long waittime = 0;
                while (serverSessionInProcess > 0 && !serverSessionInWait
                       && serverSessionState == SERVERSESSION_STOP) { 
                     if (waittime%15000 == 0) {
                         waittime = 0;
                         session.sessionLogger.log(Level.INFO, "Waiting for ServerSession runner"+this+" to stop ...");
                     }
                     serverSessionSyncObj.wait(1000);
                     waittime += 1000;
                }

                } catch (InterruptedException e) {
                    if (serverSessionInProcess > 0 && !serverSessionInWait
                        && serverSessionState == SERVERSESSION_STOP) {
                        ExceptionHandler.handleException(e,
                                         AdministeredObject.cr.X_INTERRUPTED);
                    }
                }
            }
            else {
                String errorString = AdministeredObject.cr.getKString(
                               AdministeredObject.cr.X_SVRSESSION_INPROGRESS);
                throw new javax.jms.IllegalStateException(errorString,
                               AdministeredObject.cr.X_SVRSESSION_INPROGRESS);
            }
        }
    }

    /**
     * called from session close()
     */
    protected void serverSessionClose() throws JMSException {
        synchronized(serverSessionSyncObj) {
            if (messageListener == null) {
                serverSessionState = SERVERSESSION_CLOSE;
                return;
            }
            serverSessionStop();
            serverSessionState = SERVERSESSION_CLOSE;
            serverSessionSyncObj.notifyAll();
        }
    }

    /**
     * called from session loadMessageToServerSession
     */
    protected void loadMessage(MessageImpl message, ServerSession ss, boolean isDMQMessage) {
    	
    	//HACC -- we check if reset is required.
    	//After fail over, we need to reset the Q before
    	//we load new messages.
    	if ( this.isReset() ) {	
    		this.clear();
    	}
    	
    	serverSessionMessageQ.addElement(new SSMessage(message, ss, isDMQMessage));
    }

    /**
     * @return current runner thread
     */
    protected Thread getCurrentThread() {
        return serverSessionThread;
    }

    protected void dump(PrintStream ps) {

        ps.println ("------ ServerSessionRunner dump ------");
        ps.println ("session ID: " + session.sessionId);
        ps.println ("messageListener: " + messageListener);
        ps.println ("serverSessionInProcess: " + serverSessionInProcess);
        ps.println ("serverSessionThread: " + serverSessionThread);
        ps.println ("serverSessionState: " + serverSessionState);
        ps.println ("serverSessionInWait: " + serverSessionInWait);
        ps.println ("message queue size: " + serverSessionMessageQ.size()); 

        Enumeration enum2 = serverSessionMessageQ.elements();
        while (enum2.hasMoreElements()) {
            ((Traceable)enum2.nextElement()).dump(ps);
        }
    }
    
    /**
     * Clear the messages in the queue.  This muse be called
     * after a fail-over occurred.
     * 
     * This is now called from ?
     * 
     * HACC -- work for HA connection consumer
     */
     protected void clear() {

        synchronized (serverSessionSyncObj) {
			
            this.serverSessionMessageQ.clear();

            if (this.serverSessionState == SERVERSESSION_RESET) {
                serverSessionState = this.SERVERSESSION_RUN;
            }
            reset = false;
        }

    }
    
    /**
	 * HACC -- reset server session. called by SessionImpl.reset()
	 */
    protected void reset() {
        reset(true);
    }

    /**
     */
    protected void reset(boolean resetState) {
    	
    	synchronized(serverSessionSyncObj) {
            if (resetState) {
    		    this.serverSessionState = SERVERSESSION_RESET;
    		    serverSessionSyncObj.notifyAll();
                return;
            }

            if (serverSessionState == SERVERSESSION_CLOSE) return;

            reset = true;
            if (serverSessionState == SERVERSESSION_RESET ||
                serverSessionState == SERVERSESSION_STOP) {
    		    serverSessionSyncObj.notifyAll();
                return;
            }
            try {
                serverSessionStop();
            } catch (Throwable t) {}
            if (serverSessionState == SERVERSESSION_CLOSE) return;

            serverSessionRun();
        }
      
    }
    
    /**
     * The serverSessionState is set to SERVERSESSION_RESET when
     * SessionImpl.reset() is called.
     * 
     */
    private void checkState() throws Exception {
    	
    	if ( isReset() ) {

            if (reset) return;

    		//this will be caught eventually by Session.run()
    		//and the states of the server session will be cleared.

    		throw new RuntimeException ("Fail-over occurred, server session must be reset.");
    	}
    }
    
    private boolean isReset() {
    	return (serverSessionState == SERVERSESSION_RESET || reset);
    }

    public Hashtable getDebugState(boolean verbose) {
        java.util.Hashtable ht = new Hashtable();

        ht.put("serverSessionState", String.valueOf(serverSessionState));
        ht.put("serverSessionInProcess", String.valueOf(serverSessionInProcess));
        ht.put("serverSessionInWait", String.valueOf(serverSessionInWait));
        ht.put("reset", String.valueOf(reset));

        try {
            Thread th = serverSessionThread; 
            ht.put("serverSessionThread",  (th == null ? "null":th.toString()));

            MessageListener ml = null;
            ht.put("messageListener",  (ml == null ? "null":ml.toString()));

            Message msg =  currentMessage;
            ht.put("currentMessage",  (msg == null ? "null":msg.getJMSMessageID()));
        } catch (Throwable t) {
            ExceptionHandler.logCaughtException(t);
            t.printStackTrace();
        }

        return ht;
    }
}

class SSMessage {
     MessageImpl message;
     ServerSession serversession;
     boolean isDMQMessage = false;

     public SSMessage(MessageImpl message, ServerSession ss, boolean isDMQMessage) {
     this.message = message;
     this.serversession = ss;
     this.isDMQMessage = isDMQMessage;
     }
}
