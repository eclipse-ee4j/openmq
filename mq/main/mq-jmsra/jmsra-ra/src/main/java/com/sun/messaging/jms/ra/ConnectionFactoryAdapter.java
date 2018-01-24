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

import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.TopicConnection;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.resource.ResourceException;
import javax.transaction.xa.XAResource;

import com.sun.messaging.jmq.jmsclient.ContainerType;
import com.sun.messaging.jmq.jmsclient.JMSContextImpl;
 
/**
 *  Implements the JMS ConnectionFactory interface for the S1 MQ RA.
 *  An instance of this class is returned to the application server
 *  when it uses the createConnectionFactory method of the
 *  ManagedConnectionFactory.
 */
 
public class ConnectionFactoryAdapter
        extends ConnectionCreator
        implements javax.jms.ConnectionFactory,
           javax.jms.QueueConnectionFactory,
           javax.jms.TopicConnectionFactory,
           javax.resource.Referenceable,
           java.io.Serializable
{
    /** The ManagedConnectionFactory instance that created this instance */
    private com.sun.messaging.jms.ra.ManagedConnectionFactory mcf = null;

    /** The ConnectionManager instance */
    private javax.resource.spi.ConnectionManager cm = null;

    /** The Reference instance */
    private Reference reference = null;

    /* Loggers */
    private static final String _className = "com.sun.messaging.jms.ra.ConnectionFactoryAdapter";
    protected static final String _lgrNameOutboundConnection = "javax.resourceadapter.mqjmsra.outbound.connection";
    protected static final String _lgrNameJMSConnectionFactory = "javax.jms.ConnectionFactory.mqjmsra";
    protected static final Logger _loggerOC = Logger.getLogger(_lgrNameOutboundConnection);
    protected static final Logger _loggerJF = Logger.getLogger(_lgrNameJMSConnectionFactory);
    protected static final String _lgrMIDPrefix = "MQJMSRA_FA";
    protected static final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";


    /** Constructor */
    public ConnectionFactoryAdapter(com.sun.messaging.jms.ra.ManagedConnectionFactory mcf,
        javax.resource.spi.ConnectionManager cm)
    {
        Object params[] = new Object[2];
        params[0] = mcf;
        params[1] = cm;

        _loggerOC.entering(_className, "constructor()", params);

        this.mcf = mcf;
        this.cm = cm;
    } 


    // Methods implementing javax.naming.Referenceable
    //
    public void setReference(Reference ref)
    {
        this.reference = ref;
    }
 
    public Reference getReference()
    throws NamingException
    {
        //MQJMSRA doesn't create or accept Reference objects (yet); throw the correct Exception
        throw new NamingException("MQRA:CFA:getReference:NOT Supported");
    }


    // Methods that implement javax.jms.ConnectionFactory //
    /**
     * Creates a Connection with the default user identity. The default user identity
     * is defined by the <code>ConnectionFactory</code> properties
     * <code><b>imqDefaultUsername</b></code> and <code><b>imqDefaultPassword</b></code>
     *   
     * @return a newly created Connection.
     *   
     * @exception JMSException if a JMS error occurs.
     * @see com.sun.messaging.ConnectionConfiguration#imqDefaultUsername
     * @see com.sun.messaging.ConnectionConfiguration#imqDefaultPassword
     */  
    public Connection
    createConnection()
    throws JMSException
    {
        _loggerJF.entering(_className, "createConnection()");
        //return createConnection(mcf.getUserName(), mcf.getPassword());
        return createConnection(null, null);
    }

    /**
     * Creates a Connection with a specified user identity.
     *   
     * @param username the caller's user name
     * @param password the caller's password
     *   
     * @return a newly created connection.
     *   
     * @exception JMSException if a JMS error occurs.
     */  
    public Connection
    createConnection(String username, String password)
    throws JMSException
    {
        _loggerJF.entering(_className, "createConnection()", username);
        return _allocateConnection(username, password);
    }
    
	@Override
	public JMSContext createContext() {
		return new JMSContextImpl(this, getContainerType());
	}

	@Override
	public JMSContext createContext(String userName, String password) {
		return new JMSContextImpl(this, getContainerType(), userName, password);
	}

	@Override
	public JMSContext createContext(String userName, String password, int sessionMode) {
		return new JMSContextImpl(this, getContainerType(), userName, password, sessionMode);
	}

	@Override
	public JMSContext createContext(int sessionMode) {
		return new JMSContextImpl(this, getContainerType(),sessionMode);
	}  
	
	private ContainerType getContainerType(){
		if (mcf.getInAppClientContainer()){
			return ContainerType.JavaEE_ACC;
		} else {
			return ContainerType.JavaEE_Web_or_EJB;
		}
	}
    
    private Connection
    _allocateConnection(String username, String password)
    throws JMSException
    {
    	com.sun.messaging.jms.ra.ConnectionRequestInfo.ConnectionType connectionType = com.sun.messaging.jms.ra.ConnectionRequestInfo.ConnectionType.TOPIC_CONNECTION;
        javax.resource.spi.ConnectionRequestInfo crinfo =
            new com.sun.messaging.jms.ra.ConnectionRequestInfo(mcf, username, password,connectionType);

        //System.out.println("MQRA:CFA:createConnection:allocating connection");
        ConnectionAdapter ca;
        try {
            ca = (ConnectionAdapter)cm.allocateConnection(mcf, crinfo);
        } catch (javax.resource.spi.SecurityException se) {
            throw new com.sun.messaging.jms.JMSSecurityException(
                "MQRA:CFA:allocation failure:createConnection:"+se.getMessage(), se.getErrorCode(), se);
        } catch (ResourceException re) {
            throw new com.sun.messaging.jms.JMSException(
                "MQRA:CFA:allocation failure:createConnection:"+re.getMessage(), re.getErrorCode(), re);
        }
        return ca;
    }

    /**
     * Creates a Queue Connection with the default user identity. The default user identity
     * is defined by the <code>ConnectionFactory</code> properties
     * <code><b>imqDefaultUsername</b></code> and <code><b>imqDefaultPassword</b></code>
     *   
     * @return a newly created Queue Connection.
     *   
     * @exception JMSException if a JMS error occurs.
     * @see com.sun.messaging.ConnectionConfiguration#imqDefaultUsername
     * @see com.sun.messaging.ConnectionConfiguration#imqDefaultPassword
     */  
    public QueueConnection
    createQueueConnection()
    throws JMSException
    {
        _loggerJF.entering(_className, "createQueueConnection()");
        //return createQueueConnection(mcf.getUserName(), mcf.getPassword());
        return createQueueConnection(null, null);
    }

    /**
     * Creates a Queue Connection with a specified user identity.
     *   
     * @param username the caller's user name
     * @param password the caller's password
     *
     * @return a newly created queue connection.
     *
     * @exception JMSException if a JMS error occurs.
     */
    public QueueConnection
    createQueueConnection(String username, String password)
    throws JMSException
    {
        _loggerJF.entering(_className, "createQueueConnection()", username);
        return _allocateQueueConnection(username, password);
    }

    private QueueConnection
    _allocateQueueConnection(String username, String password)
    throws JMSException
    {
    	com.sun.messaging.jms.ra.ConnectionRequestInfo.ConnectionType connectionType = com.sun.messaging.jms.ra.ConnectionRequestInfo.ConnectionType.QUEUE_CONNECTION;
        javax.resource.spi.ConnectionRequestInfo crinfo =
            new com.sun.messaging.jms.ra.ConnectionRequestInfo(mcf, username, password,connectionType);

        ConnectionAdapter ca;
        try {
            ca = (ConnectionAdapter)cm.allocateConnection(mcf, crinfo);
        } catch (javax.resource.spi.SecurityException se) {
            throw new com.sun.messaging.jms.JMSSecurityException(
                "MQRA:CFA:allocation failure:createQueueConnection:"+se.getMessage(), se.getErrorCode(), se);
        } catch (ResourceException re) {
            //XXX:Fix codes
            throw new com.sun.messaging.jms.JMSException(
                "MQRA:CFA:allocation failure:createQueueConnection:"+re.getMessage(), re.getErrorCode(), re);
        }
        return (QueueConnection)ca;
    }

    /**
     * Creates a Topic Connection with the default user identity. The default user identity
     * is defined by the <code>ConnectionFactory</code> properties
     * <code><b>imqDefaultUsername</b></code> and <code><b>imqDefaultPassword</b></code>
     *   
     * @return a newly created Topic Connection.
     *   
     * @exception JMSException if a JMS error occurs.
     * @see com.sun.messaging.ConnectionConfiguration#imqDefaultUsername
     * @see com.sun.messaging.ConnectionConfiguration#imqDefaultPassword
     */  
    public TopicConnection
    createTopicConnection()
    throws JMSException
    {
        _loggerJF.entering(_className, "createTopicConnection()");
        //return createTopicConnection(mcf.getUserName(), mcf.getPassword());
        return createTopicConnection(null, null);
    }

    /**
     * Creates a Topic Connection with a specified user identity.
     *   
     * @param username the caller's user name
     * @param password the caller's password
     *
     * @return a newly created topic connection.
     *
     * @exception JMSException if a JMS error occurs.
     */
    public TopicConnection
    createTopicConnection(String username, String password)
    throws JMSException
    {
        _loggerJF.entering(_className, "createTopicConnection()", username);
        return _allocateTopicConnection(username, password);
    }
    
    private TopicConnection _allocateTopicConnection(String username,
            String password)
    throws JMSException {
    	    	
    	com.sun.messaging.jms.ra.ConnectionRequestInfo.ConnectionType connectionType = com.sun.messaging.jms.ra.ConnectionRequestInfo.ConnectionType.TOPIC_CONNECTION;
        javax.resource.spi.ConnectionRequestInfo crinfo =
            new com.sun.messaging.jms.ra.ConnectionRequestInfo(mcf,
                username, password,connectionType);

        //System.out.println("MQRA:CFA:createTopicConnection:allocating Topic Connection");
        ConnectionAdapter ca;
        try {
            ca = (ConnectionAdapter)cm.allocateConnection(mcf, crinfo);
            return (TopicConnection)ca;
        } catch (javax.resource.spi.SecurityException se) {
            throw new com.sun.messaging.jms.JMSSecurityException(
                "MQRA:CFA:allocation failure:createTopicConnection:"+se.getMessage(), se.getErrorCode(), se);
        } catch (ResourceException re) {
            //XXX:Fix exception logs
            throw new com.sun.messaging.jms.JMSException(
                "MQRA:CFA:allocation failure:createTopicConnection:"+re.getMessage(), re.getErrorCode(), re);
        }
    }

    protected javax.jms.Connection _createConnection(String un, String pw)
    throws javax.jms.JMSException {
        return (this.mcf._getXACF()).createXAConnection(un, pw);
    }
    
	@Override
	protected Connection _createQueueConnection(String un, String pw) throws JMSException {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	protected Connection _createTopicConnection(String un, String pw) throws JMSException {
		// TODO Auto-generated method stub
		return null;
	}

    protected XAResource _createXAResource(ManagedConnection mc, Object conn)
    throws JMSException {
        return (XAResource)null;
    }

    ManagedConnectionFactory getMCF() {
        return mcf;
    }

}
