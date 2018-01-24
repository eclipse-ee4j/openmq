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

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import javax.jms.ConnectionConsumer;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.Queue;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.resource.spi.ConnectionEvent;

import com.sun.messaging.jmq.jmsclient.ContextableConnection;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode;
import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.sun.messaging.jmq.jmsservice.JMSServiceReply;
import com.sun.messaging.jms.MQRuntimeException;

/**
 *  DirectConnection encapsulates JMS Connection behavior for MQ DIRECT mode
 *  operation.
 */
public class DirectConnection 
        implements javax.jms.Connection,
        javax.jms.QueueConnection,
        javax.jms.TopicConnection, ContextableConnection {
		
    /**
     *  The parent DirectConnectionFactory that created this DirectConnection
     */
    private DirectConnectionFactory dcf;

    /**
     *  The JMSService for this DirectConnection
     */
    private final JMSService jmsservice;

    /**
     *  The connectionId for this DirectConnection
     */
    private final long connectionId;

    /**
     *  Flags whether this DirectConnection is in the Application Client
     *  Container or not
     */
    private final boolean inACC;

    /**
     *  The clientID for this DirectConnection
     */
    private String clientID;

    /**
     *  The ExceptionLisrtener for this DirectConnection
     */
    private ExceptionListener exceptionListener;

    /**
     *  The ConnectionMetaData for this DirectConnection
     */
    private ConnectionMetaData connectionMetaData;

    /**
     *  Holds the DirectXAResource that will be used for this DirectConnection
     */
    private DirectXAResource xar = null;

    /**
     *  Holds the DirectSession created in this DirectConnection
     */
    private DirectSession ds = null;

    /**
     * Flags whether further creation of session is allowed
     */
    private boolean sessions_allowed = true;

    /**
     *  DirectSessions made by this DirectConnection
     */
    private transient Vector <DirectSession> sessions = null;

    /**
     *  Holds TemporaryDestinations created in this DirectConnection
     */
    private transient Vector <TemporaryDestination> tmp_destinations = null;

    /**
     *  Holds durable consumers that need to be checked for unsubscribing
     */
    private transient Vector <DirectConsumer> durable_consumers = null;

    /**
     *  The ManagedConnection associated with this DirectConnection
     */
    ManagedConnection mc = null;

	/**
     *  Indicates whether this is managed via the JavaEE ManagedConnection
     *  interface
     */
    private boolean isManaged = false;
    private boolean isPooled = false;
    private volatile boolean isEnlisted = false;

    /**
     *  Holds the closed state of this DirectConnection
     */
    private boolean isClosed;
    private boolean isClosing;

    /**
     *  Holds the stopped state of this DirectConnection
     */
    private boolean isStopped;

    /**
     *  Holds the used state of this DirectConnection
     */
    private boolean isUsed;

    /** Private sync object and sequencer for temporary destinations */
    private Object syncObj = new Object();
    private int temporaryDestinationId = 0;

    /**
     *  Logging
     */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.DirectConnection";
    private static transient final String _lgrNameOutboundConnection =
            "javax.resourceadapter.mqjmsra.outbound.connection";
    private static transient final String _lgrNameJMSConnection =
            "javax.jms.Connection.mqjmsra";
    private static transient final String _lgrNameJMSConnectionClose =
            "javax.jms.Connection.mqjmsra.close";
    private static transient final Logger _loggerOC =
            Logger.getLogger(_lgrNameOutboundConnection);
    private static transient final Logger _loggerJC =
            Logger.getLogger(_lgrNameJMSConnection);
    private static transient final Logger _loggerJCC =
            Logger.getLogger(_lgrNameJMSConnectionClose);
    private static transient final String _lgrMIDPrefix = "MQJMSRA_DC";
    private static transient final String _lgrMID_EET = _lgrMIDPrefix+"1001: ";
    private static transient final String _lgrMID_INF = _lgrMIDPrefix+"1101: ";
    private static transient final String _lgrMID_WRN = _lgrMIDPrefix+"2001: ";
    private static transient final String _lgrMID_ERR = _lgrMIDPrefix+"3001: ";
    private static transient final String _lgrMID_EXC = _lgrMIDPrefix+"4001: ";

    /** Creates a new instance of DirectConnection */
    public DirectConnection(DirectConnectionFactory cf,
            JMSService jmsservice, long connectionId, boolean inACC) {
        Object params[] = new Object[4];
        params[0] = cf;
        params[1] = jmsservice;
        params[2] = connectionId;
        params[3] = inACC;
        _loggerOC.entering(_className, "constructor()", params);     
        this.dcf = cf;
        this.jmsservice = jmsservice;
        this.connectionId = connectionId;
        this.inACC = inACC;
        this.clientID= null;
        this.exceptionListener = null;
        
        //TBD:tharakan:Need to set
        this.connectionMetaData = 
                new DirectConnectionMetaData(cf._getConfiguration());
        this.sessions = new Vector <DirectSession> ();
        this.tmp_destinations = new Vector <TemporaryDestination> ();
        this.durable_consumers = new Vector <DirectConsumer> ();
        isClosed = false;
        isClosing = false;
        isStopped = true;
        isUsed = false;
    }

    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.Connection
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Close the JMS Connection
     */
    public void close()
    throws JMSException {
    	boolean willDestroy=false;
    	close(willDestroy);
    }
    
    public void closeAndDestroy()
    throws JMSException {
    	boolean willDestroy=true;
    	close(willDestroy);
    	_destroy();
    }    
    
    /**
     * 
     * @param willDestroy whether this connection will be destroyed after this method returns
     * @throws JMSException
     */
    private void close(boolean willDestroy)
    throws JMSException {
        _loggerJCC.fine(_lgrMID_INF+"connectionId="+connectionId+":"+"close("+willDestroy+"):");
        _loggerJC.fine (_lgrMID_INF+"connectionId="+connectionId+":"+"close("+willDestroy+"):");
        synchronized (this) {
            //harmless if already closed
            if (this.isClosed){
                return;
            } else {
                this.isClosing = true;
                this._stop();
                this._closeAndClearSessions();
				if (mc!=null && mc.xaTransactionActive()){
					// there's still an uncommitted transaction: defer the call to _deleteTemporaryDestinations() until after the commit
				} else {
					 this._deleteTemporaryDestinations();
				}            
                
                this.isClosing = false;
                this.isClosed = true;
                
                if (willDestroy) {
                	return;
                }
                //If this connection is managed, and we're not about to destroy it,
                // then it is being closed from the app and we are done.
                if (this.isManaged) {
                    this.mc.sendEvent(ConnectionEvent.CONNECTION_CLOSED,
                            null, this);
                    return;
                }
            }
        }
        //If here, then we are not managed and must really close the connection
        this._destroy();
    }

    /**
     *  Create a ConnectionConsumer in this JMS Connection
     *
     *  @param  destination The name of the Destination on which to create the
     *          ConnectionConsumer
     *  @param  messageSelector The JMS Message Selector to use when creating
     *          the ConnectionConsumer
     *  @param  sessionPool The ServerSessionPool to associate when creating
     *          the ConnectionConsumer
     *  @param  maxMessages The maxmimum number of messages that can be
     *          assigned to a ServerSession at a single time
     */
	@Override
    public ConnectionConsumer createConnectionConsumer(
            Destination destination,
            String messageSelector,
            ServerSessionPool sessionPool,   
            int maxMessages)
    throws JMSException {
						
        _loggerJC.fine(_lgrMID_INF+
                "connectionId="+connectionId+":"+"createConnectionConsumer():" +
                "Destination=" + destination + ":" +
                "MessageSelector=" + messageSelector + ":" +
                "ServerSessionPool=" + sessionPool + ":" +
                "MaxMessages=" + maxMessages);
        _unsupported("createConnectionConsumer():");
        return null;
    }
    
	@Override
	public ConnectionConsumer createSharedConnectionConsumer(Topic topic, String subscriptionName,
			String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
		
        _loggerJC.fine(_lgrMID_INF+
                "connectionId="+connectionId+":"+"createSharedConnectionConsumer():" +
                "Topic=" + topic + ":" +
                "subscriptionName=" + subscriptionName + ":" +
                "MessageSelector=" + messageSelector + ":" +
                "ServerSessionPool=" + sessionPool + ":" +
                "MaxMessages=" + maxMessages);
        _unsupported("createSharedConnectionConsumer():");
        return null;
	}

	@Override
	public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic, String subscriptionName,
			String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
						
        _loggerJC.fine(_lgrMID_INF+
                "connectionId="+connectionId+":"+"createSharedDurableConnectionConsumer():" +
                "Topic=" + topic + ":" +
                "subscriptionName=" + subscriptionName + ":" +
                "MessageSelector=" + messageSelector + ":" +
                "ServerSessionPool=" + sessionPool + ":" +
                "MaxMessages=" + maxMessages);
        _unsupported("createSharedDurableConnectionConsumer():");
        return null;
	}

    /**
     *  Create a Durable ConnectionConsumer in this JMS Connection
     *
     *  @param  topic The name of the Topic on which to create the durable
     *          ConnectionConsumer
     *  @param  messageSelector The JMS Message Selector to use when creating
     *          the ConnectionConsumer
     *  @param  sessionPool The ServerSessionPool to associate when creating
     *          the ConnectionConsumer
     *  @param  maxMessages The maxmimum number of messages that can be
     *          assigned to a ServerSession at a single time
     *
     */
	@Override
    public ConnectionConsumer createDurableConnectionConsumer(
            Topic topic,
            String subscriptionName,
            String messageSelector,
            ServerSessionPool sessionPool,   
            int maxMessages)
    throws JMSException {
				
        _loggerJC.fine(_lgrMID_INF+
                "connectionId="+connectionId+":"+
                "createDurableConnectionConsumer():" +
                "Topic=" + topic + ":" +
                "SubscriptionName=" + subscriptionName + ":" +
                "MessageSelector=" + messageSelector + ":" +
                "ServerSessionPool=" + sessionPool + ":" +
                "MaxMessages=" + maxMessages);
        _unsupported("createDurableConnectionConsumer():");
        return (ConnectionConsumer) null;
    }

    /**
     *  Create a JMS Session in this JMS Connection
     *
     *  @param  isTransacted Indicates whether the Session is transacted or not.
     *  @param  acknowledgeMode The Session acknowledgement mode
     *
     *  @return The JMSSession
     */
    public Session createSession(boolean isTransacted, int acknowledgeMode)
    throws JMSException {
        _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+"createSession():" +
                "isTransacted=" + isTransacted + ":" +
                "acknowledgeMode=" + acknowledgeMode);
        _checkIfClosed("createSession():");
        checkSessionsAllowed("createSession():");
        this.setUsed();
        
        SessionAckMode ackMode;
        long sessionId;
        if (ResourceAdapter._isFixCR6760301()){
        	boolean actualTransactedArg = overrideTransacted(isTransacted);
            int actualAcknowledgeModeArg = overrideAcknowledgeMode(acknowledgeMode);
            sessionId = _createSessionId(connectionId, actualTransactedArg, actualAcknowledgeModeArg);
            ackMode = _getSessionAckModeFromSessionParams(actualTransactedArg, actualAcknowledgeModeArg);
        } else {
        	boolean isTransactedOverride = (this.isManaged() ? false : isTransacted);
          	sessionId = _createSessionId(connectionId, isTransactedOverride,acknowledgeMode);
          	ackMode = _getSessionAckModeFromSessionParams(isTransactedOverride, acknowledgeMode);
        }
        
        DirectSession ds =
                (this.isManaged() 
                ? new DirectTransactionManagedSession(this, this.jmsservice,
                    sessionId, ackMode)
                : new DirectSession(this, this.jmsservice, sessionId, ackMode)
                );
        this.addSession(ds);
        return (Session)ds;
    }
    
	/* (non-Javadoc)
	 * @see javax.jms.Connection#createSession(int)
	 */
	@Override
	public Session createSession(int sessionMode) throws JMSException {
		if (sessionMode==Session.SESSION_TRANSACTED){
			return createSession(true,sessionMode);
		} else {
			return createSession(false,sessionMode);
		}
	}

	/* (non-Javadoc)
	 * @see javax.jms.Connection#createSession()
	 */
	@Override
	public Session createSession() throws JMSException {
		return createSession(false,Session.AUTO_ACKNOWLEDGE);
	}
    
    /**
     *  Create a JMS Session in this JMS Connection on which a RAEndpoint can consume messages
     *
     *  @return The JMSSession
     */
    public Session createSessionForRAEndpoint()
    throws JMSException {
        _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+"createSessionForRAEndpoint():");
        _checkIfClosed("createSession():");
        this.setUsed();
        
        boolean actualTransactedArg = false;
        int actualAcknowledgeModeArg = Session.CLIENT_ACKNOWLEDGE;
        
        long sessionId = _createSessionId(connectionId, actualTransactedArg, actualAcknowledgeModeArg);
        SessionAckMode ackMode = _getSessionAckModeFromSessionParams(actualTransactedArg, actualAcknowledgeModeArg);
        DirectSession ds = new DirectSession(this, this.jmsservice, sessionId, ackMode);
        this.addSession(ds);
        return (Session)ds;
    }
    
	private boolean overrideTransacted(boolean suppliedTransactedArgument) {
		boolean actualTransactedArg;
		if (inACC) {
			// Direct mode is not supported in direct mode, but just in case  
			actualTransactedArg = suppliedTransactedArgument;
		} else {
			// override args to createSession() when in an EJB or web container
			// in accordance with EJB spec section 13.3.5 "Use of JMS APIs in Transactions"
			// This isn't very clear on what happens when there is no transaction,
			// but I (Nigel) have made the decision that if XA is not being used, 
			// the session should be non-transacted
			actualTransactedArg = false;
		}
		return actualTransactedArg;
	}
	
	private int overrideAcknowledgeMode(int suppliedAcknowledgeModeArgument){
		int actualAcknowledgeModeArg;
		if (inACC){
			// Direct mode is not supported in direct mode, but just in case  
			actualAcknowledgeModeArg=suppliedAcknowledgeModeArgument;
		} else {
			if (suppliedAcknowledgeModeArgument==Session.CLIENT_ACKNOWLEDGE) {
				// EJB spec section 13.3.5 "Use of JMS APIs in Transactions" says
				// "The Bean Provider should not use the JMS acknowledge method either within a transaction 
				// or within an unspecified transaction context. Message acknowledgment in an unspecified 
				// transaction context is handled by the container." 
				// 
				// The same restriction applies in web container: JavaEE Spec: "EE.6.7 Java Message Service (JMS) 1.1 Requirements" says
				// "In general, the behavior of a JMS provider should be the same in both the EJB container and the web container.
				// The EJB specification describes restrictions on the use of JMS in an EJB container, 
				// as well as the interaction of JMS with transactions in an EJB container. 
				// Applications running in the web container should follow the same restrictions.
				actualAcknowledgeModeArg=Session.AUTO_ACKNOWLEDGE;
			} else {
				// allow auto-ack and dups-OK 
				actualAcknowledgeModeArg=suppliedAcknowledgeModeArgument;
			}
		}
		return actualAcknowledgeModeArg;
	}

    /**
     *  Return the ClientID that is set on the JMS Connection
     *
     *  @return The Client Identifier that is set on this JMS Connection
     */
    public String getClientID()
    throws JMSException {
        _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+"getClientID():");
        _checkIfClosed("getClientID():");
        this.setUsed();
        return clientID;
    }

    /**
     *  Return the ExceptionListener that is set on the JMS Connection
     *
     *  @return The ExceptionListener
     */
    public ExceptionListener getExceptionListener()
    throws JMSException {
        _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+"getExceptionListener():");
        _checkIfClosed("getExceptionListener():");
        this.setUsed();
        return exceptionListener;
    }

    /**
     *  Return the ConnectionMetaData for this JMS Connection
     *
     *  @return The ConnectionMetaData
     */
    public ConnectionMetaData getMetaData()
    throws JMSException {
        _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+"getMetaData():");
        _checkIfClosed("getMetaData():");
        this.setUsed();
        return connectionMetaData;
    }

    /**
     *  Set a CientID on the JMS Connection
     *
     *  @param clientID The clientID to set on the JMS Connection
     */
    public void setClientID(String clientID)
    throws JMSException {
        String methodName = "setClientID()";
       _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+methodName);
       if (!this.inACC){
           _unsupported(methodName);
       }
       this._checkIfClosed(methodName);
       this._setClientID(clientID);
    }
    
    /**
     * Set clientID to the specified value, bypassing any checks as to whether calling setClientID is allowed at this time.
     * 
     * @param clientID
     */
	@Override
	public void _setClientIDForContext(String clientID) {
        String methodName = "_setClientIDForContext()";
       _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+methodName);
       try {
       if (!this.inACC){
           _unsupported(methodName);
       }
       this._checkIfClosed(methodName);
       this._setClientID(clientID);
       } catch (JMSException e){
    	   throw new MQRuntimeException(e);
       }
		
	}

    /**
     *  Set an ExceptionListener on the JMS Connection.<br>
     *  This method is unsupported for Direct Connections
     *
     *  @param listener The ExceptionListener to set on the JMS Connection
     */
    public void setExceptionListener(ExceptionListener listener)
    throws JMSException {
        String methodName = "setExceptionListener()";
        _loggerJC.fine(_lgrMID_INF + "connectionId=" + connectionId +
                ":" + methodName);
        if (!this.inACC) {
            _unsupported(methodName);
        }
        _checkIfClosed(methodName);
    }

    /**
     *  Start the JMS Connection
     */
    public synchronized void start()
    throws JMSException {
        _loggerJC.fine(_lgrMID_INF+"connectionId="+connectionId+":"+"start():");
        _checkIfClosed("start():");
        if (!isStopped()) {
            return;
        }
        this.setUsed();
        try {
            jmsservice.startConnection(this.connectionId);
        } catch (JMSServiceException jmsse) {
            _loggerJC.warning(_lgrMID_WRN+
                    "connectionId="+connectionId+":"+"start():"+
                    "JMSService.startConnection():"+
                    "JMSServiceException="+
                    jmsse.getMessage());
        }
        isStopped = false;
        this._startSessions();
    }

    /**
     *  Stop the JMS Connection
     */
    public synchronized void stop()
    throws JMSException {
        String methodName = "stop()";
        _loggerJC.fine(_lgrMID_INF+methodName+":connectionId="+connectionId);
        if (!this.inACC){
            _unsupported(methodName);
        }
        this._stop();
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.Connection
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.QueueConnection
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Create a ConnectionConsumer in this JMS QueueConnection
     *
     *  @param  queue The name of the Queue on which to create the
     *          ConnectionConsumer
     *  @param  messageSelector The JMS Message Selector to use when creating
     *          the ConnectionConsumer
     *  @param  sessionPool The ServerSessionPool to associate when creating
     *          the ConnectionConsumer
     *  @param  maxMessages The maxmimum number of messages that can be
     *          assigned to a ServerSession at a single time
     */
    public ConnectionConsumer createConnectionConsumer(
            Queue queue,
            String messageSelector,
            ServerSessionPool sessionPool,   
            int maxMessages)
    throws JMSException {
     	    	
        _loggerJC.fine(_lgrMID_INF+
                "connectionId="+connectionId+":"+"createConnectionConsumer():" +
                "Queue=" + queue + ":" +
                "MessageSelector=" + messageSelector + ":" +
                "ServerSessionPool=" + sessionPool + ":" +
                "MaxMessages=" + maxMessages);
        _unsupported("createConnectionConsumer():");
        return null;
    }

    /**
     *  Create a JMS QueueSession in this JMS QueueConnection
     *
     *
     */
    public QueueSession createQueueSession(boolean isTransacted,
            int acknowledgeMode)
    throws JMSException {
        _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+"createQueueSession():" +
                "isTransacted=" + isTransacted + ":" +
                "acknowledgeMode=" + acknowledgeMode);
        _checkIfClosed("createQueueSession():");
        checkSessionsAllowed("createQueueSession():");
        this.setUsed();   
        
        SessionAckMode ackMode;
        long sessionId;
        if (ResourceAdapter._isFixCR6760301()){
        	// override args
        	boolean actualTransactedArg = overrideTransacted(isTransacted);
            int actualAcknowledgeModeArg = overrideAcknowledgeMode(acknowledgeMode);
            sessionId = _createSessionId(connectionId, actualTransactedArg, actualAcknowledgeModeArg);
            ackMode = _getSessionAckModeFromSessionParams(actualTransactedArg, actualAcknowledgeModeArg);
        } else {
            boolean isTransactedOverride = (this.isManaged() ? false : isTransacted);
            sessionId = _createSessionId(connectionId, isTransactedOverride, acknowledgeMode);
            ackMode = _getSessionAckModeFromSessionParams(isTransactedOverride, acknowledgeMode);
        }
                
        DirectSession ds = new DirectQueueSession(this, this.jmsservice,
                sessionId, ackMode);
        this.addSession(ds);
        return (QueueSession)ds;
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.QueueConnection
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.TopicConnection
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Create a ConnectionConsumer in this JMS TopicConnection
     *
     *  @param  topic The name of the Topic on which to create the
     *          ConnectionConsumer
     *  @param  messageSelector The JMS Message Selector to use when creating
     *          the ConnectionConsumer
     *  @param  sessionPool The ServerSessionPool to associate when creating
     *          the ConnectionConsumer
     *  @param  maxMessages The maxmimum number of messages that can be
     *          assigned to a ServerSession at a single time
     */
    public ConnectionConsumer createConnectionConsumer(
            Topic topic,
            String messageSelector,
            ServerSessionPool sessionPool,   
            int maxMessages)
    throws JMSException {
    	    	
        _loggerJC.fine(_lgrMID_INF+
                "connectionId="+connectionId+":"+"createConnectionConsumer():" +
                "Topic=" + topic + ":" +
                "MessageSelector=" + messageSelector + ":" +
                "ServerSessionPool=" + sessionPool + ":" +
                "MaxMessages=" + maxMessages);
        _unsupported("createConnectionConsumer():");
        return (ConnectionConsumer) null;
    }

    /**
     *  Create a JMS TopicSession in this JMS QueueConnection
     *
     *
     */
    public TopicSession createTopicSession(boolean isTransacted,
            int acknowledgeMode)
    throws JMSException {
        _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+"createTopicSession():" +
                "isTransacted=" + isTransacted + ":" +
                "acknowledgeMode=" + acknowledgeMode);
        _checkIfClosed("createTopicSession():");
        checkSessionsAllowed("createTopicSession():");
        this.setUsed();

        SessionAckMode ackMode;
        long sessionId;
        if (ResourceAdapter._isFixCR6760301()){
        	// override args
        	boolean actualTransactedArg = overrideTransacted(isTransacted);
            int actualAcknowledgeModeArg = overrideAcknowledgeMode(acknowledgeMode);
            sessionId = _createSessionId(connectionId, actualTransactedArg, actualAcknowledgeModeArg);
            ackMode = _getSessionAckModeFromSessionParams(actualTransactedArg, actualAcknowledgeModeArg);
        } else {
            boolean isTransactedOverride = (this.isManaged() ? false : isTransacted);
            sessionId = _createSessionId(connectionId, isTransactedOverride, acknowledgeMode);
            ackMode = _getSessionAckModeFromSessionParams(isTransactedOverride, acknowledgeMode);
        }
        
        DirectSession ds = new DirectTopicSession(this, this.jmsservice,
                sessionId, ackMode);
        this.addSession(ds);
        return (TopicSession)ds;
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.TopicConnection
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  private support for javax.jms.Connection for RA use
    /////////////////////////////////////////////////////////////////////////
    /**
     * Return the ClientId (non-JMS)
     */
    public String _getClientID(){
        return this.clientID;
    }

    /**
     *  Set a CientID on the JMS Connection
     *
     *  @param clientID The clientID to set on the JMS Connection
     */
    public void _setClientID(String clientID)
    throws JMSException {
       _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+"_setClientID():" +
               "clientID=" + clientID);
       if (this.clientID != null){
           String error = _lgrMID_WRN+
                   "connectionId="+connectionId+":"+"_setclientID():"+
                   "Error to overwrite existing clientID:"+ this.clientID +
                   "with new clientID=" + clientID;
           _loggerJC.warning(error);
           throw new JMSException(error);
       }
       if (clientID == null || "".equals(clientID)){
           String error = _lgrMID_WRN+
                   "connectionId="+connectionId+":"+"_setclientID():"+
                   "Error to set a null or empty clientID:"+ clientID;
           _loggerJC.warning(error);
           throw new JMSException(error);
       }
       try {
           _loggerJC.finer(_lgrMID_INF+
                   "connectionId="+connectionId+":"+"_setClientID():" +
                   "jmsservice.clientId():" +"params=" +
                   clientID + ":"+ dcf.isRAClustered()+":"+dcf.getRANamespace());
           this.jmsservice.setClientId(
                   this.connectionId, clientID, 
                   dcf.isRAClustered(), dcf.getRANamespace());
           this.clientID = clientID;
       } catch (JMSServiceException jmsse) {
           //XXX:tharakan:Update w/ specific error on conflict
           _loggerJC.warning(_lgrMID_WRN+
                    "connectionId="+connectionId+":"+"_setClientID():"+
                    "JMSService.setClientId():"+
                    "JMSServiceException="+
                    jmsse.getMessage());
       }
    }

    /**
     *  Unset a CientID on the JMS Connection
     */
    public void _unsetClientID()
    throws JMSException {
       _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+"_unsetClientID():");
       if (this.clientID == null){
           return;
//           String warning = _lgrMID_WRN+
//                   "connectionId="+connectionId+":"+"_unsetclientID():"+
//                   "No existing clientID to unset";
//           _loggerJC.warning(warning);
//           throw new JMSException(warning);
       }
       try {
           _loggerJC.finer(_lgrMID_INF+
                   "connectionId="+connectionId+":"+"_unsetClientID():" +
                   "jmsservice.unclientId():");
          this.jmsservice.unsetClientId(this.connectionId);
          this.clientID = null;
       } catch (JMSServiceException jmsse) {
           _loggerJC.warning(_lgrMID_WRN+
                   "connectionId="+connectionId+":"+"_unsetClientID():"+
                   "JMSService.unsetClientId():"+
                   "JMSServiceException="+
                   jmsse.getMessage());
       }
    }

    /**
     *  Set an ExceptionListener on the JMS Connection.<br>
     *  This method is unsupported for Direct Connections
     *
     *  @param listener The ExceptionListener to set on the JMS Connection
     */
    public void _setExceptionListener(ExceptionListener listener)
    throws JMSException {
        _loggerJC.fine(_lgrMID_INF+
               "connectionId="+connectionId+":"+"_setExceptionListener()");
        this.exceptionListener = listener;
    }

    /**
     *  Get the XAResource for this DirectConnection
     */
    public synchronized DirectXAResource _getXAResource() {
        if (this.xar == null) {
            this.xar = new DirectXAResource(this, this.jmsservice, this.connectionId);
        }
        return this.xar;
    }

    /**
     *  Return the JMSService for this DirectConnection
     */
    protected JMSService _getJMSService(){
        return this.jmsservice;
    }

    protected synchronized void _activate(String clientId)
    throws JMSException {
        if (this.isManaged) {
            if (clientId != null && !"".equals(clientId)){
                this._setClientID(clientId);
            }
            this.isPooled = false;
            this.isClosed = false;
        }
    }

    protected synchronized void _cleanup()
    throws JMSException {
        //NOP if this is not a managed connection
        if (this.isManaged) {
            this.isPooled = true;
            this.isUsed = false;
            this._unsetClientID();
        }
    }
   
    protected synchronized void _destroy()
    throws JMSException {
        if (!this.isClosed) {
            _loggerJC.warning(_lgrMID_WRN+
                    "connectionId="+connectionId+":"+"_destroy():"+
                    "called on a connection that was not closed.");
        }
        //NOP if this is not a managed connection
        if (this.isManaged) {
            this.isPooled = false;    
        }
        try {
            jmsservice.destroyConnection(connectionId);
        } catch (JMSServiceException jmsse){
            _loggerJC.warning(_lgrMID_WRN+
                    "connectionId="+connectionId+":"+"_close():"+
                    "JMSService.destroyConnection():"+
                    "JMSServiceException="+
                    jmsse.getMessage());
        }
    }

    protected void _stop()
    throws JMSException {
        _checkIfClosed("stop():");
        if (isStopped) {
            //harmless if already stopped
            return;
        }
        isStopped = true;
        this._stopSessions();
        try {
            _loggerJC.finer(_lgrMID_INF+
                    "connectionId="+connectionId+":"+
                    "stop():"+"jmsservice.stopConnection():"+"connectionId=" +
                    this.connectionId);
            jmsservice.stopConnection(this.connectionId);
        } catch (JMSServiceException jmsse) {
            _loggerJC.warning(_lgrMID_WRN+
                    "connectionId="+connectionId+":"+"stop():"+
                    "JMSService.stopConnection():"+
                    "JMSServiceException="+
                    jmsse.getMessage());
        }
        //XXX:tharakan:ensure all delivery stopped - do not return till stopped
    }
    /////////////////////////////////////////////////////////////////////////
    //  end private support for javax.jms.Connection for RA use
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  MQ methods
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Create a session with the jmsservice and return a sessionId.
     *  Used by the methods implementing javax.jms.Connection,
     *  javax,jms.QueueConnection, and javax.jms.TopicConnection
     *
     *  @param  connectionId The connectionId in which the session is being
     *          created.
     *  @param  acknowledgeMode The acknowledgment mode being used
     *
     *  @return The sessionId to be used by the DirectSession object
     *          that is returned by the JMS API method
     *
     *  @throws JMSException if any JMS server error occurred
     */
    private long _createSessionId(long connectionId, boolean isTransacted,
            int acknowledgeMode)
    throws JMSException {
        JMSServiceReply reply;
        long sessionId = 0L;
        SessionAckMode ackMode = this._getSessionAckModeFromSessionParams(
                isTransacted, acknowledgeMode);
        try {
            reply = jmsservice.createSession(connectionId, ackMode);
            try {
                sessionId = reply.getJMQSessionID();                
            } catch (NoSuchFieldException nsfe){
                String exerrmsg = _lgrMID_EXC +
                        "JMSServiceException:Missing JMQSessionID";
                JMSException jmse = new JMSException(exerrmsg);
                jmse.initCause(nsfe);
                _loggerJC.severe(exerrmsg);
                throw jmse;
            }

        } catch (JMSServiceException jse) {
            JMSServiceReply.Status status = jse.getJMSServiceReply().getStatus();
            String failure_cause;
            switch (status) {
                case BAD_REQUEST:
                    failure_cause = "invalid acknowledgment mode.";
                    break;
                case NOT_ALLOWED:
                    failure_cause = "acknowledgmentmode is not allowed.";
                    break;
                default:
                    failure_cause = "unkown JMSService server error.";
            }
            String exerrmsg = 
                    "createSession on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ connectionId +
                    " due to " + failure_cause;
            _loggerJC.severe(exerrmsg);
            JMSException jmsse = new JMSException(exerrmsg);
            jmsse.initCause(jse);
            throw jmsse;
        }
        return sessionId;
    }

    /**
     *  Check if the DirectConnection is closed prior to performing an
     *  operation and throw a JMSException if the connection is closed.
     *
     *  @param methodname The name of the method from which this check is called
     *
     *  @throws JMSException if it is closed
     */
    private void _checkIfClosed(String methodname)
    throws JMSException {
        if (isClosed()) {
            String closedmsg = _lgrMID_EXC + methodname +
                    "Connection is closed:Id=" + connectionId;
            _loggerJC.warning(closedmsg);
            throw new javax.jms.IllegalStateException(closedmsg);
        }
    }

    /**
     *  Check if the DirectConnection has one active Session object
     *  and throw a JMSException if an active Session object already exists.
     *
     *  @param methodName The name of the method from which this check is called
     *
     *  @throws JMSException if an active Session object already exists for this connection
     */
    private void checkSessionsAllowed(String methodName) throws JMSException {
        if (!sessions_allowed) {
            String disallowed = _lgrMID_EXC + methodName +
                    "An active Session object already exists for this connection, " +
                    "only one active Session object allowed per connection.";
            _loggerJC.warning(disallowed);
            throw new JMSException(disallowed);
        }
    }

    /**
     *  Throw a JMSException with the appropriate message for unsupported
     *  operations.
     *
     *  @param  methodname The method name for which this unsupported
     *          exception is to be thrown.
     */
    private void _unsupported(String methodname)
    throws JMSException {
        String unsupported = _lgrMID_WRN + "Unsupported:" + methodname +
                ":inACC=" + this.inACC +
                ":connectionId=" + connectionId;
        _loggerJC.warning(unsupported);
        throw new JMSException(unsupported);
    }

    /**
     *  Set this DirectConnection as used
     */
    private synchronized void setUsed() {
        this.isUsed = true;
    }

    /**
     *  Return the connectionId for this Direct Connection
     *
     *  @return The connectionId
     */
    public long getConnectionId() {
        return this.connectionId;
    }

    /**
     *  Return the pooled / in-use state of this DirectConnection
     *
     *  @return {@code true} if this connection can be pooled;
     *          {@code false} if this connection is in-use
     */
    public synchronized boolean isPooled() {
        return this.isPooled;
    }

    /**
     *  Return the enlisted / un-enlisted state of this DirectConnection
     *
     *  @return {@code true} if this connection is enlisted in an XA transaction
     *          {@code false} if this connection is not in an XA transaction
     */
    public boolean isEnlisted() {
        return this.isEnlisted;
    }

    /**
     *  Return the managed / non-managed state of this DirectConnection
     *
     *  @return {@code true} if this connection is managed;
     *          {@code false} if this connection is non-managed
     */
    public boolean isManaged() {
        return this.isManaged;
    }

    /**
     *  Set the managed /non-managed state of this DirectConnection
     */
    public void setManaged(boolean value, ManagedConnection mc) {
        this.isManaged = value;
        this.mc = mc;
    }

    /**
     *  Set the enlisted / un-enlisted state of this DirectConnection for
     *  XA transactions
     */
    public void setEnlisted(boolean value){
        this.isEnlisted = value;
    }
    /**
     *  Return the closed state of this DirectConnection
     *
     *  @return {@code true} if this connection has been closed;
     *          {@code false} otherwise
     */
    public synchronized boolean isClosed() {
        return this.isClosed;
    }

    /**
     *  Returns the stopped state of this DirectConnection
     *
     *  @return {@code true} if this connection has been stopped;
     *          {@code false} otherwise
     */
    public synchronized boolean isStopped() {
       return this.isStopped;
    }
    public synchronized boolean isStarted() {
        return !this.isStopped;
    }

    /**
     *  Return the used state of this DirectConnection
     *
     *  @return {@code true} if this connection has been used;
     *          {@code false} otherwise
     */
    public synchronized boolean isUsed() {
        return this.isUsed;
    }

    /**
     *  Return the SessionAckMode enum value given the Session parameters
     *
     *  @param  isTransacted boolean indicates whether the Session is transacted
     *          or not
     *  @param  acknowledgeMode The acknowledgement mode for non-transacted
     *          sessions
     *
     *  @return The SessionAckMode
     *
     *  @see    com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode
     *  @see    javax.jms.Session#AUTO_ACKNOWLEDGE javax.jms.Session.AUTO_ACKNOWLEDGE
     *  @see    javax.jms.Session#CLIENT_ACKNOWLEDGE javax.jms.Session.CLIENT_ACKNOWLEDGE
     *  @see    javax.jms.Session#DUPS_OK_ACKNOWLEDGE javax.jms.Session.DUPS_OK_ACKNOWLEDGE
     *  @see    com.sun.messaging.jms.Session#NO_ACKNOWLEDGE com.sun.messaging.jms.Session.NO_ACKNOWLEDGE
     */
    protected static SessionAckMode _getSessionAckModeFromSessionParams(
            boolean isTransacted, int acknowledgeMode){
        SessionAckMode ackMode = SessionAckMode.UNSPECIFIED;
        if (isTransacted){
            ackMode = SessionAckMode.TRANSACTED;
        } else {
            if (acknowledgeMode == Session.AUTO_ACKNOWLEDGE){
                ackMode = SessionAckMode.AUTO_ACKNOWLEDGE;
            } else if (acknowledgeMode == Session.DUPS_OK_ACKNOWLEDGE) {
                ackMode = SessionAckMode.DUPS_OK_ACKNOWLEDGE;
            } else if (acknowledgeMode == Session.CLIENT_ACKNOWLEDGE) {
                ackMode = SessionAckMode.CLIENT_ACKNOWLEDGE;
            } else if (acknowledgeMode == com.sun.messaging.jms.Session.NO_ACKNOWLEDGE){
                ackMode = SessionAckMode.NO_ACKNOWLEDGE;
            } else {
            	// default
                ackMode = SessionAckMode.AUTO_ACKNOWLEDGE;
            }
        }
        return ackMode;
    }

    /**
     *  Add a Session to the list of JMS Session created by this
     *  Direct Connection.
     *
     *  @param  session The DirectSession to be added
     */
    protected void addSession(DirectSession session) {
        this.sessions.add(session);
        ds = session;
        if (!inACC) {
            // no more sessions if we are not in the application client container
            sessions_allowed = false;
        }
    }

    /**
     *  Removes a Session from the list of JMS Session created by this
     *  Direct Connection.
     *
     *  @param  session  The DirectSession to be removed
     */
    protected void removeSession(DirectSession session) {
        boolean result = this.sessions.remove(session);
        //This session *has* to be in the list else something went wrong :)
        assert (result == true);
        ds = null;
        if (!sessions_allowed && sessions.isEmpty()) {
            sessions_allowed = true;
        }
    }

    /**
     *  Add a Consumer to the list of duable consumers active in this
     *  Direct Connection.
     *
     *  @param  consumer The DirectConsumer to be added
     */
    protected void addDurableConsumer(DirectConsumer consumer) {
        this.durable_consumers.add(consumer);
    }

    /**
     *  Remove a Consumer from the list of duable consumers active in this
     *  Direct Connection.
     *
     *  @param  consumer  The DirectConsumer to be removed
     */
    protected void removeDurableConsumer(DirectConsumer consumer) {
        //boolean result = this.durable_consumers.remove(consumer);
        this.durable_consumers.remove(consumer);
    }

    /**
     *  Return the durable_consumers Vector for this DirectConnection
     *
     *  @return The durable_consumers Vector
     */
    protected Vector<DirectConsumer> _getDurableConsumers() {
        return this.durable_consumers;
    }

    /**
     *  Create a physical destination using the jmsservice
     *
     *  @param  destination The Destination object that represents the physical
     *          destination that is to be created.
     */
    protected void _createDestination(
            com.sun.messaging.jmq.jmsservice.Destination destination)
    throws JMSException {
        JMSServiceReply _reply;
        try {
            this.jmsservice.createDestination(connectionId, destination);
        } catch (JMSServiceException jse) {
            JMSServiceReply.Status status =
                    jse.getJMSServiceReply().getStatus();
            String failure_cause = null;;
            String exerrmsg = 
                    "createDestination on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ connectionId +
                    " and Destination:" + destination.getType() +
                    ":"+ destination.getName() +
                    ":due to "; 
            JMSException jmse = null;
            switch (status) {
                case FORBIDDEN:
                    failure_cause = "client unauthorized for create.";
                    exerrmsg = exerrmsg + failure_cause;
                    jmse = new JMSSecurityException(exerrmsg, jse.getJMSServiceReply().getErrorCode());
                    break;
                case ERROR:
                    failure_cause = "unkown JMSService server error.";
                    break;
                default:
                    failure_cause = "status =" + status;
                //expressly leave out CONFLICT as it's a NOP if the dest exists
            }
            if (jmse == null) {
                jmse = new JMSException(exerrmsg+failure_cause);
            }
            jmse.initCause(jse);
            if (status != JMSServiceReply.Status.CONFLICT) {
                _loggerJC.severe(exerrmsg);
                throw jmse;
            } else {
                _loggerJC.fine(exerrmsg);
            }
        }
    }

    /**
     *  Delete a TemporaryDestination using the jmsservice
     *
     *  @param  t_destination The TemporaryDestination
     *  @param  destination The jmsservice Destination
     */
    protected void _deleteDestination(TemporaryDestination t_destination,
            com.sun.messaging.jmq.jmsservice.Destination destination)
    throws JMSException {
        JMSServiceReply _reply;
        JMSException jmse = null;
        String failure_cause = null;
        try {
            this.jmsservice.destroyDestination(connectionId, destination);
        } catch (JMSServiceException jmsse) {
            JMSServiceReply.Status status =
                    jmsse.getJMSServiceReply().getStatus();
            switch (status) {
                case FORBIDDEN:
                    failure_cause = "client unauthorized for delete.";
                    break;
                case NOT_FOUND:
                    failure_cause = "destination does not exist.";
                    break;
                case CONFLICT:
                    failure_cause = "destination has producers or consumers.";
                    break;
                case ERROR:
                default:
                    failure_cause = "unkown JMSService server error.";
                    break;               
            }
            String exerrmsg = 
                    "destroyDestination on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ connectionId +
                    " and Destination:" + destination.getType() +
                    ":"+ destination.getName() +
                    ":due to " + failure_cause;
            jmse = new JMSException(exerrmsg);
            jmse.initCause(jmsse);
            _loggerJC.warning(exerrmsg);
        } finally {
            //this.removeTemporaryDestination(t_destination);
        }
        if (jmse != null){
            throw jmse;
        }
    }

    /**
     *  Start a transaction on this DirectConnection
     */
    protected long _startTransaction(String fromMethod)
    throws JMSException {
        JMSServiceReply reply = null;
        long _transactionId = 0L;
        try {
            reply = jmsservice.startTransaction(this.connectionId,
                    0L, null, 0,
                    JMSService.TransactionAutoRollback.UNSPECIFIED, 0L);
            try {
                _transactionId = reply.getJMQTransactionID();
            } catch (NoSuchFieldException nsfe){
                String exerrmsg = _lgrMID_EXC +
                        ":_startTransaction from " + fromMethod +
                        ":JMSServiceException:Missing JMQTransactionID";
                JMSException jmse = new JMSException(exerrmsg);
                jmse.initCause(nsfe);
                _loggerJC.severe(exerrmsg);
                throw jmse;
            }
        } catch (JMSServiceException jmsse){
            String exerrmsg = _lgrMID_EXC +
                    ":_startTransaction from " + fromMethod +
                    "JMSServiceException=" +
                    jmsse.getMessage();
            JMSException jmse = new JMSException(exerrmsg);
            jmse.initCause(jmsse);
            _loggerJC.severe(exerrmsg);
            throw jmse;
        }
        return _transactionId;
    }

    /**
     *  Commit a transaction on this DirectConnection
     */
    protected void _commitTransaction(String methodName, long transactionId)
    throws JMSException {
        //JMSServiceReply reply = null;
        try {
            //reply = jmsservice.commitTransaction(
            jmsservice.commitTransaction(
                    this.connectionId, transactionId, null, 0);
        } catch (JMSServiceException jmsse){
            String exerrmsg = _lgrMID_WRN+
                    "connectionId="+this.connectionId+":"+methodName+
                    ":JMSServiceException="+
                    jmsse.getMessage();
            JMSException jmse = new JMSException(exerrmsg);
            jmse.initCause(jmsse);
            _loggerJC.severe(exerrmsg);
            throw jmse;
        }
    }

    /**
     *  Rollback a transaction on this DirectConnection
     */
    protected void _rollbackTransaction(String methodName, long transactionId)
    throws JMSException {
        //JMSServiceReply reply = null;
        try {
            //reply = jmsservice.rollbackTransaction(
            jmsservice.rollbackTransaction(
                    this.connectionId, transactionId, null, true, true);
        } catch (JMSServiceException jmsse){
            String exerrmsg = _lgrMID_WRN+
                    "connectionId="+this.connectionId+":"+methodName+
                    ":JMSServiceException="+
                    jmsse.getMessage();
            JMSException jmse = new JMSException(exerrmsg);
            jmse.initCause(jmsse);
            _loggerJC.severe(exerrmsg);
            throw jmse;
        }
    }

    /**
     *  Return the next TemporaryDestinationId
     *
     *  @return The next Id for use in creating a TemporaryDestination
     */
    protected int nextTemporaryDestinationId(){
        synchronized (this.syncObj){
            this.temporaryDestinationId++;
            return this.temporaryDestinationId;
        }
    }

    /**
     *  Return the connection-dependant TemporaryDestination identifier
     *
     */
    protected String _getConnectionIdentifierForTemporaryDestination(){
        StringBuilder sb;
        try {
            sb = new StringBuilder(InetAddress.getLocalHost().getHostAddress());
        } catch (Exception ex) {
            sb = new StringBuilder("127.0.0.1");
        }
        if (this.clientID != null) {
            sb.append(":" + this.clientID);
        }
        sb.append("/" + this.connectionId);
        return sb.toString();
    }

    /**
     *  Add a TemporaryDestination to the list of Temporary Destinations
     *  created by this Direct Connection.
     *
     *  @param  t_destination The TemporaryDestination to be added
     */
    protected void addTemporaryDestination(TemporaryDestination t_destination) {
        this.tmp_destinations.add(t_destination);
    }

    /**
     *  Remove a TemporaryDestination from the list of Temporary Destinations
     *  created by this Direct Connection.
     *
     *  @param  t_destination  The TemporaryDestination to be removed
     */
    protected void removeTemporaryDestination(
            TemporaryDestination t_destination) {
        boolean result = this.tmp_destinations.remove(t_destination);
        //This t_desatination *has* to be in the list else something is wrong :)
        assert (result == true);
    }

    /**
     *  Checks whether a TemporaryDestination exists in the list of
     *  TemporaryDestination objects created by this Direct Connection.
     *
     *  @param  t_destination  The TemporaryDestination to be found
     */
    protected boolean _hasTemporaryDestination(
            TemporaryDestination t_destination) {
        return this.tmp_destinations.contains(t_destination);
    }

    /**
     *  Checks whether a TemporaryDestination has consumers in this
     *  DirectConnection.
     *
     *  @param  t_destination The TemporaryDestination to be checked for
     *          consumers
     */
    protected boolean _hasConsumers(TemporaryDestination t_destination) {
        return (t_destination._getConsumerCount() > 0);
    }

    /**
     *  Start the session in the sessions table
     */
    private void _startSessions() {
        //cycle through sessions and start them one by one
        DirectSession ds = null;
        Iterator<DirectSession> k = this.sessions.iterator();
        while (k.hasNext()) {
            ds = k.next();
            ds._start();
        }
    }

    /**
     *  Stop the session in the sessions table
     */
    private void _stopSessions() {
        //cycle through sessions and stop them one by one
        DirectSession ds = null;
        Iterator<DirectSession> k = this.sessions.iterator();
        while (k.hasNext()) {
            ds = k.next();
            ds._stop();
        }
    }

    /**
     *  Close and clear sessions table
     */
    private void _closeAndClearSessions() {
        //cycle through sessions and close them one by one
        DirectSession ds = null;
        Iterator<DirectSession> k = this.sessions.iterator();
        while (k.hasNext()) {
            ds = k.next();
            try {
                ds._close();
                k.remove();
            } catch (JMSException jmsedsc) {
                _loggerJC.warning(_lgrMID_WRN+
                        "connectionId="+this.connectionId+":"+"close_session:"+
                        "sessionId:"+ ds.getSessionId() +
                        ":JMSException="+
                        jmsedsc.getMessage());
            }
        }
        this.sessions.clear();
        sessions_allowed = true;
        this.ds = null;
    }

    /**
     *  Delete this connections temporary destinations
     */
    protected void _deleteTemporaryDestinations()
    throws JMSException {
        //cycle through tmp_destinations and delete them one by one
        TemporaryDestination td = null;
        Iterator<TemporaryDestination> k = this.tmp_destinations.iterator();
        while (k.hasNext()) {
            td = k.next();
            try {
                td._delete();
                k.remove();
            } catch (JMSException jmsetdd) {
                _loggerJC.warning(_lgrMID_WRN+
                        "connectionId="+this.connectionId+":"+
                        "delete_temporary_destination="+
                        td.getName() +
                        ":JMSException="+
                        jmsetdd.getMessage());
            }
        }
        this.tmp_destinations.clear();
    }

    /**
     *  Incrments the consumer count when a TemporaryDestination is used
     */
    protected void _incrementTemporaryDestinationUsage(
            TemporaryDestination destination)
    throws JMSException {
        destination._incrementConsumerCount();
    }

    /**
     *  Decrements the consumer count when a TemporaryDestination is used
     */
    protected void _decrementTemporaryDestinationUsage(
            TemporaryDestination destination)
    throws JMSException {
        destination._decrementConsumerCount();
    }

	/**
	 * Check whether the connection has been closed and, 
	 * if it has been, delete any temporary destinations
	 * which we could not delete until the transaction was committed
	 * 
	 * @throws JMSException
	 */
	public void deleteTemporaryDestinationsIfClosed() throws JMSException {
		if (isClosed){
	    	_deleteTemporaryDestinations();
		}
	}

}
