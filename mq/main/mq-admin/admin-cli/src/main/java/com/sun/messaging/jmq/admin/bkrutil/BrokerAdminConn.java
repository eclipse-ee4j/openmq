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
 */ 

package com.sun.messaging.jmq.admin.bkrutil;

import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Properties;
import java.io.EOFException;
import java.net.MalformedURLException;
import javax.jms.*;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.QueueConnectionFactory;
import com.sun.messaging.jmq.ClientConstants;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.MQAddressList;

import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.event.AdminEventListener;
import com.sun.messaging.jmq.admin.event.BrokerErrorEvent;
import com.sun.messaging.jmq.admin.event.CommonCmdStatusEvent;
import com.sun.messaging.jmq.admin.util.CommonGlobals;

/**
 * This class provides the common code for the administration connection
 * to the JMQ broker.  This class is extended by individual admin tools 
 * imqcmd (BrokerAdmin), imqadmin (BrokerAdmin) and imqbridgemgr (BridgeAdmin) 
 *
 * Any individual admin tool specific protocol or class references must be
 * placed in its corresponding subclass.
 *
 * <P>
 * The information needed to create this object are:
 * <UL>
 * <LI>connection factory attributes
 * <LI>username/passwd
 * <LI>timeout (for receiving replies)
 * </UL>
 */
public abstract class BrokerAdminConn implements ExceptionListener {

    public final static String		DEFAULT_ADMIN_USERNAME	= "admin";
    public final static String		DEFAULT_ADMIN_PASSWD	= "admin";

    private static long			defaultTimeout		= 10000;
    private static int			defaultNumRetries	= 5;

    /*
     * Reconnect attributes.
     * These are used for the restart command.
     */
    public static final int 		RECONNECT_RETRIES = 5;
    public static final long 		RECONNECT_DELAY = 5000;


    private String			key = null;

    private String			username,
    					passwd;
    private int				numRetries;

    private QueueConnectionFactory	qcf;
    private QueueConnection		connection = null;
    private Queue			requestQueue;

    /********************************************************
     * Below variables are directly referenced by subclasses
     ********************************************************/
    protected long			timeout;
    private static boolean		debug = false;
    protected QueueSession	       	session;
    protected TemporaryQueue	       	replyQueue;
    protected QueueSender	       		sender;
    protected QueueReceiver	       	receiver;
    protected boolean       isConnected = false;
    /*********************************************************
     * Above variables are directly referenced by subclasses
     *********************************************************/

    private MQAddress			address = null;

    private boolean checkShutdownReply = true;

    /*
     * List of properties that we currently care about i.e. that are
     * saved out if necessary. The reconnect stuff is currently
     * not used. This is used by getBrokerAttrs() which in turn is
     * used only by the admin console.
     */
    private final static String	       	savedQCFProperties[] = {
                                ConnectionConfiguration.imqBrokerHostName,
                                ConnectionConfiguration.imqBrokerHostPort
					};

    private boolean			adminKeyUsed = false;
    private boolean			sslTransportUsed = false;

    private boolean			isInitiator = false;
    private boolean			isReconnect = false;


    private Vector eListeners = new Vector();

    private MessageAckThread		msgAckThread = null;
    private boolean			busy = false;

    /*
     * Temporary convenient constructor.
     */
    public BrokerAdminConn(String brokerHost, int brokerPort) throws BrokerAdminException  {
	this(brokerHost, brokerPort, null, null, -1, false, -1, -1);
    }

    public BrokerAdminConn(String brokerHost, int brokerPort, 
	               String username, String passwd) 
		       throws BrokerAdminException  {
	this(brokerHost, brokerPort, username, passwd, -1, false, -1, -1);
    }

    public BrokerAdminConn(String brokerHost, int brokerPort, 
	               String username, String passwd, int timeout) 
		       throws BrokerAdminException  {
	this(brokerHost, brokerPort, username, passwd, timeout, false, -1, -1);
    }

    public BrokerAdminConn(String brokerAddress,
	               String username, String passwd, int timeout, boolean useSSL) 
		       throws BrokerAdminException  {
	this(brokerAddress, username, passwd, timeout, false, -1, -1, useSSL);
    }

    /**
     * Instantiates a BrokerAdminConn object. This is a wrapper for
     * this other constructor:
     *
     *  public BrokerAdminConn(Properties, String, String, long)
     *
     * @param brokerHost	host name of the broker to administer
     * @param brokerPort 	primary port for broker
     * @param username		username used to authenticate
     * @param passwd		password used to authenticate
     * @param timeout		timeout value (in milliseconds) for receive; 
     *                          0 = never times out and the call blocks 
     *				indefinitely
     * @param reconnect		true if reconnect is enabled; false otherwise
     * @param reconnectRetries	number of reconnect retries
     * @param reconnectDelay	interval of reconnect retries in milliseconds
     */
    public BrokerAdminConn(String brokerHost, int brokerPort, 
	               String username, String passwd, long timeout,
		       boolean reconnect, int reconnectRetries, long reconnectDelay) 
		       throws BrokerAdminException  {

	Properties tmpProps = new Properties();

	if (brokerHost != null)  {
	    tmpProps.setProperty(ConnectionConfiguration.imqBrokerHostName, brokerHost);
	}

	if (brokerPort > 0)  {
	    tmpProps.setProperty(ConnectionConfiguration.imqBrokerHostPort, 
				String.valueOf(brokerPort));
	}

	if (reconnect)  {
	    tmpProps.setProperty(ConnectionConfiguration.imqReconnectEnabled, 
		String.valueOf(reconnect));
	    tmpProps.setProperty(ConnectionConfiguration.imqReconnectAttempts, 
		String.valueOf(reconnectRetries));
	    tmpProps.setProperty(ConnectionConfiguration.imqReconnectInterval, 
		String.valueOf(reconnectDelay));
	}

	if (timeout >= 0)  {
	    this.timeout = timeout;
	} else  {
	    this.timeout = defaultTimeout;
	}

	this.numRetries = defaultNumRetries;

	this.username = username;
	this.passwd = passwd;

	createFactory(tmpProps);
    }

    /**
     * Instantiates a BrokerAdminConn object. This is a wrapper for
     * this other constructor:
     *
     *  public BrokerAdminConn(Properties, String, String, long)
     *
     * @param brokerAddress 	address/url of broker
     * @param username		username used to authenticate
     * @param passwd		password used to authenticate
     * @param timeout		timeout value (in milliseconds) for receive; 
     *                          0 = never times out and the call blocks 
     *				indefinitely
     * @param reconnect		true if reconnect is enabled; false otherwise
     * @param reconnectRetries	number of reconnect retries
     * @param reconnectDelay	interval of reconnect retries in milliseconds
     * @param useSSL		Use encrypted transport via SSL
     */
    public BrokerAdminConn(String brokerAddress, 
	               String username, String passwd, 
		       long timeout,
		       boolean reconnect, int reconnectRetries, 
		       long reconnectDelay, boolean useSSL) 
		           throws BrokerAdminException  {

	Properties tmpProps = new Properties();

	if (brokerAddress == null)  {
	    brokerAddress = "";
	}

	try  {
	    if (useSSL)  {
	        address = (MQAddress)SSLAdminMQAddress.createAddress(brokerAddress);
	    } else  {
	        address = (MQAddress)AdminMQAddress.createAddress(brokerAddress);
	    }
	} catch (Exception e)  {
	    BrokerAdminException bae;
	    bae = new BrokerAdminException(BrokerAdminException.BAD_ADDR_SPECIFIED);
	    bae.setBrokerAddress(brokerAddress);
	    bae.setLinkedException(e);
            throw bae;
	}

	tmpProps.setProperty(ConnectionConfiguration.imqAddressList, 
					address.toString());

	if (reconnect)  {
	    tmpProps.setProperty(ConnectionConfiguration.imqReconnectEnabled, 
		String.valueOf(reconnect));
	    tmpProps.setProperty(ConnectionConfiguration.imqReconnectAttempts, 
		String.valueOf(reconnectRetries));
	    tmpProps.setProperty(ConnectionConfiguration.imqReconnectInterval, 
		String.valueOf(reconnectDelay));
	}

	if (timeout >= 0)  {
	    this.timeout = timeout;
	} else  {
	    this.timeout = defaultTimeout;
	}

	this.numRetries = defaultNumRetries;

	this.username = username;
	this.passwd = passwd;

	createFactory(tmpProps);
    }


    /**
     * The constructor for the class.
     *
     * @param brokerAttrs 	Properties object containing
     *				the broker attributes. This is
     *				basically what is used to create
     *				the connection factory.
     * @param username		username used to authenticate
     * @param passwd		password used to authenticate
     * @param timeout		timeout value (in milliseconds) for receive; 
     *                          0 = never times out and the call blocks 
     *				indefinitely
     */
    public BrokerAdminConn(Properties brokerAttrs,
			String username, String passwd, 
			long timeout) 
		       throws BrokerAdminException  {

	if (timeout >= 0)  {
	    this.timeout = timeout;
	} else  {
	    this.timeout = defaultTimeout;
	}
	this.numRetries = defaultNumRetries;

	this.username = username;
	this.passwd = passwd;

	createFactory(brokerAttrs);
    }


    public void setBrokerHost(String hostName) throws BrokerAdminException  {
	try  {
	    setFactoryAttr(ConnectionConfiguration.imqBrokerHostName, hostName);
	} catch (JMSException jmse)  {
	    BrokerAdminException bae;
	    bae = new BrokerAdminException(BrokerAdminException.BAD_HOSTNAME_SPECIFIED);
	    bae.setBadValue(hostName);
	    bae.setLinkedException(jmse);
            throw bae;
	}
    }

    public String getBrokerAddress()  {
	if (address != null)  {
	    return (address.toString());
	}

	return (getFactoryAttr(ConnectionConfiguration.imqAddressList));
    }

    public String getBrokerHost()  {
	if (address != null)  {
	    return (address.getHostName());
	}

	return (getFactoryAttr(ConnectionConfiguration.imqBrokerHostName));
    }

    public void setBrokerPort(String port) throws BrokerAdminException  {
	try  {
	    setFactoryAttr(ConnectionConfiguration.imqBrokerHostPort, port);
	} catch (JMSException jmse)  {
	    BrokerAdminException bae;
	    bae = new BrokerAdminException(BrokerAdminException.BAD_PORT_SPECIFIED);
	    bae.setBadValue(port);
	    bae.setLinkedException(jmse);
            throw bae;
	}
    }

    public String getBrokerPort()  {
	if (address != null)  {
	    return ((Integer.toString(address.getPort())));
	}

	return (getFactoryAttr(ConnectionConfiguration.imqBrokerHostPort));
    }

    public static void setDefaultTimeout(long defaultTimeout)  {
	BrokerAdminConn.defaultTimeout = defaultTimeout;
	if (debug)  {
	    CommonGlobals.stdOutPrintln("BrokerAdminConn defaultTimeout set to: " + BrokerAdminConn.defaultTimeout);
	}
    }
    public static long getDefaultTimeout()  {
	return (defaultTimeout);
    }

    public long getTimeout()  {
	return (timeout);
    }

    public static void setDefaultNumRetries(int defaultNumRetries)  {
	BrokerAdminConn.defaultNumRetries = defaultNumRetries;
	if (debug)  {
	    CommonGlobals.stdOutPrintln("BrokerAdminConn defaultNumRetries set to: "
		+ BrokerAdminConn.defaultNumRetries);
	}
    }
    public static long getDefaultNumRetries()  {
	return (defaultNumRetries);
    }

    public void setNumRetries(int numRetries)  {
	this.numRetries = numRetries;
	if (debug)  {
	    CommonGlobals.stdOutPrintln("BrokerAdminConn num retries set to: " + this.numRetries);
        }
    }

    public int getNumRetries() {
        return this.numRetries;
    }

    public void setUserName(String userName)  {
	this.username = userName;
    }
    public String getUserName()  {
	return (username);
    }

    public void setPassword(String passwd)  {
	this.passwd = passwd;
    }
    public String getPassword()  {
	return (passwd);
    }

    protected void setBusy(boolean b)  {
	busy = b;
	if (debug)  {
	    CommonGlobals.stdOutPrintln("***** BrokerAdminConn.setBusy(): " + b);
	}

	if (!b && (msgAckThread != null))  {
	    /*
	     * If we are going from busy -> not busy,
	     * nullify the ack thread.
	     */
	    msgAckThread = null;
	}
    }
    public boolean isBusy()  {
	return(busy);
    }

    protected void checkIfBusy() throws BrokerAdminException  {
    if (isBusy())  {
            BrokerAdminException    bae;

            bae = new BrokerAdminException(
            BrokerAdminException.BUSY_WAIT_FOR_REPLY);
        bae.setBrokerAdminConn(this);
            throw bae;
    }
    }

    /*
     * Support for private "-adminkey" option.
     * This is used for authentication when shutting down the
     * broker via the NT service's "Stop" command.
     */
    public void setAdminKeyUsed(boolean b)  {
	this.adminKeyUsed = b;
    }
    public boolean getAdminKeyUsed()  {
	return (adminKeyUsed);
    }


    /*
     * Sets a string that will be used to uniquely identify this
     * broker instance.
     */
    public void setKey(String key)  {
    this.key = key;
    }

    /**
     * Returns a unique key of this broker instance.
     * If the key field is not set, host:port is returned
     */
    public String getKey() {
    if (key != null)  {
        return (key);
    }

        return (getBrokerHost() + ":" + getBrokerPort());
    }


    /*
     * Support for "-ssl" option.
     * This is used to indicate that SSL transport
     * will be used.
     */
    public void setSSLTransportUsed(boolean b)  {
	this.sslTransportUsed = b;
    }
    public boolean getSSLTransportUsed()  {
	return (sslTransportUsed);
    }

    /*
     * Set when this BrokerAdminConn has initiated the
     * shutdown operation.
     */
    public void setInitiator(boolean b)  {
        this.isInitiator = b;
    }
    private boolean isInitiator()  {
        return (isInitiator);
    }

    /*
     * Set this to false if the admin process does not
     * do shutdown operation of the broker
     */
    public void setCheckShutdownReply(boolean b)  {
        this.checkShutdownReply = b;
    }

    /*
     * Set when this BrokerAdminConn is restarted.
     */
    public void setReconnect(boolean b)  {
        this.isReconnect = b;
    }
    public boolean isReconnect()  {
        return (isReconnect);
    }

    /*
     * Return a properties object containing the queue
     * connection configuration properties.
     * We restrict the props to those listed in the
     * savedQCFProperties array - otherwise we'll get
     * the whole load - most of which are defaults.
     */
    public Properties getBrokerAttrs()  {
	Properties tmpProps = new Properties();

	if (qcf == null)  {
	    return (tmpProps);
	}

	for (int i = 0; i < savedQCFProperties.length; ++i)  {
	    String	propName = savedQCFProperties[i],
			propVal = getFactoryAttr(propName);
	    
	    tmpProps.setProperty(propName, propVal);
	}

	return (tmpProps);
    }

    public void connect() throws BrokerAdminException  {
        // We need a flag since username / password can be null.
        connect(null, null, false);
    }

    public void connect(String tempUsername, String tempPasswd) 
	throws BrokerAdminException {
        // We need a flag since username / password can be null.
        connect(tempUsername, tempPasswd, true);
    }

    // REVISIT: should this be synchronized to make sure isConnected is properly set?
    private void connect(String tempUsername, String tempPasswd, boolean useTempValues) 
	throws BrokerAdminException  {
	BrokerAdminException	bae;

        try {
	    if (adminKeyUsed)  {
		/*
		 * turn on using password as special admin key
		 */
		qcf.setConnectionType(ClientConstants.CONNECTIONTYPE_ADMINKEY);
	    }

	    if (sslTransportUsed)  {
		/*
		 * Set connection transport to be SSL
		 */
		try  {
		    qcf.setProperty(ConnectionConfiguration.imqConnectionType, "SSL");
		} catch (Exception e)  {
		}

	        try  {
	            setFactoryAttr(ConnectionConfiguration.imqConnectionType, "SSL");
	        } catch (JMSException jmse)  {
	            bae = new BrokerAdminException(BrokerAdminException.PROB_SETTING_SSL);
	            bae.setBrokerHost(getBrokerHost());
	            bae.setBrokerPort(getBrokerPort());
	            bae.setLinkedException(jmse);
                    throw bae;
	        }
	    }

	    if (debug)  {
		printObjProperties(qcf);
	    }

            if (useTempValues) {
                connection = qcf.createQueueConnection(tempUsername, tempPasswd);
            } else {
                connection = qcf.createQueueConnection(username, passwd);
            }
	    connection.setExceptionListener(this);
	    connection.start();
	    if (debug) CommonGlobals.stdOutPrintln("***** Creating queue connection");

	    session = 
	        connection.createQueueSession(false, Session.CLIENT_ACKNOWLEDGE);
	   
	    if (debug) CommonGlobals.stdOutPrintln
	    ("***** Creating queue session: not transacted, auto ack");

	    requestQueue = session.createQueue(getAdminQueueDest());
	    if (debug) 
	    CommonGlobals.stdOutPrintln("***** Created requestQueue: " + requestQueue);

	    replyQueue = session.createTemporaryQueue();
	    if (debug) CommonGlobals.stdOutPrintln("***** Created replyQueue: " + replyQueue);

	    sender = session.createSender(requestQueue);
	    // making the message delivery mode non-persistent
	    sender.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
	    if (debug) CommonGlobals.stdOutPrintln("***** Created a sender: " + sender);

      	    receiver = session.createReceiver(replyQueue);
	    if (debug) CommonGlobals.stdOutPrintln("***** Created a receiver: " + receiver);

	} catch (JMSException jmse) {
	    if (jmse instanceof JMSSecurityException) {
		String errorCode = jmse.getErrorCode();
		if ((AdministeredObject.cr.X_INVALID_LOGIN).equals(errorCode))
	    	    bae = new BrokerAdminException(BrokerAdminException.INVALID_LOGIN);
		else
	    	    bae = new BrokerAdminException(BrokerAdminException.SECURITY_PROB);
	    } else {
	        bae = new BrokerAdminException(BrokerAdminException.CONNECT_ERROR);
	    }
	    bae.setBrokerHost(getBrokerHost());
	    bae.setBrokerPort(getBrokerPort());
	    bae.setLinkedException(jmse);
            throw bae;
	    
	} catch (Exception e) {
	    bae = new BrokerAdminException(BrokerAdminException.CONNECT_ERROR);
	    bae.setLinkedException(e);
	    bae.setBrokerHost(getBrokerHost());
	    bae.setBrokerPort(getBrokerPort());
            throw bae;
	}
    }

    public abstract String getAdminQueueDest();
    public abstract String getAdminMessagePropNameMessageType();
    public abstract String getAdminMessagePropNameErrorString();
    public abstract String getAdminMessagePropNameStatus();
    public abstract int getAdminMessageStatusOK();
    public abstract int getAdminMessageTypeSHUTDOWN_REPLY();
    public abstract CommonCmdStatusEvent newCommonCmdStatusEvent(int type);
    public abstract CommonCmdStatusEvent getCurrentStatusEvent();
    public abstract void clearStatusEvent();

    private void printObjProperties(AdministeredObject obj) {
        CommonGlobals.stdOutPrintln("Connection Factory Object properties:");

        /*
         * Print the properties of the object.
         */
        Properties props = obj.getConfiguration();
        for (Enumeration e = obj.enumeratePropertyNames(); e.hasMoreElements();) {
            String propName = (String)e.nextElement();
            String value = props.getProperty(propName);
            String propLabel;
            try  {
                propLabel = obj.getPropertyLabel(propName);
            } catch (Exception ex)  {
                propLabel = "UNKNOWN";
            }
            String printLabel = propName + " [" + propLabel + "]";

            CommonGlobals.stdOutPrintln("\t" + printLabel + " = " + value);
        }
        CommonGlobals.stdOutPrintln("");
    }

    /**
     * Returns true if this instance is connected to the broker.
     * Returns false otherwise.
     */
    public boolean isConnected() {
	return (isConnected);
    }

    /**
     * Sets the value of isConnected value.
     */
    public void setIsConnected(boolean b) {
        this.isConnected = b;
    }

    private String getErrorMessage(Message mesg) {
	String error = null;
	
	try {
	    error = mesg.getStringProperty(getAdminMessagePropNameErrorString());
	} catch (JMSException jmse) {
	    if (debug)  {
	        CommonGlobals.stdErrPrint("Failed to retrieve the error message: ");
                CommonGlobals.stdErrPrintln(jmse.getMessage());
	        jmse.printStackTrace();
	    }
        } catch (Exception e) {
	    if (debug)  {
                CommonGlobals.stdErrPrintln("Exception caught: " + e.getMessage());
                e.printStackTrace();
	    }
        }

	return error;
    }

    /**
     * This method is used to force the connection close when shutdown is
     * performed on the broker.  This way the connection is closed immediately
     * and we rely on the client to do all the cleanup for us.  This is
     * called immediately as to avoid any unnecessary reconnect retries.
     */
    public void forceClose() {
	try {
            if (connection != null) {
	        connection.close();
            }
	    isConnected = false;
	    if (msgAckThread != null)  {
		msgAckThread.stop();
		msgAckThread = null;
	        if (debug) 
		    CommonGlobals.stdOutPrintln("***** Stopped msgAckThread thread...");
	    }

	} catch (JMSException jmse) {
	    CommonGlobals.stdErrPrintln("JMSException caught: " + jmse.getMessage());
	    jmse.printStackTrace();
	}
    }

    // REVISIT: should this be synchronized to make sure isConnected is properly set?
    public void close() {
	if (!isConnected())  {
	    return;
	}

	try {
	    if (msgAckThread != null)  {
		msgAckThread.stop();
		msgAckThread = null;
	        if (debug) 
		    CommonGlobals.stdOutPrintln("***** Stopped msgAckThread thread...");
	    }

	    if (debug) CommonGlobals.stdOutPrintln("***** Closing sender and receiver...");
	    sender.close();
	    if (debug) CommonGlobals.stdOutPrintln("***** Closed sender.");
	    receiver.close();
	    if (debug) CommonGlobals.stdOutPrintln("***** Closed receiver.");

      	    if (debug)
	    CommonGlobals.stdOutPrintln("***** Closing queue session and queue connection...");
	    session.close();
	    if (debug) CommonGlobals.stdOutPrintln("***** Closed session.");
        } catch (JMSException jmse) {
            CommonGlobals.stdErrPrintln("JMSException caught: " + jmse.getMessage());
            jmse.printStackTrace();

        } catch (Exception e) {
            CommonGlobals.stdErrPrintln("Exception caught: " + e.getMessage());
            e.printStackTrace();
        }

	try {
            if (connection != null) {
	        connection.close();
	        if (debug) CommonGlobals.stdOutPrintln("***** Closed connection.");
            }

	    isConnected = false;
	    setBusy(false);

        } catch (JMSException jmse) {
	    if (sslTransportUsed)  {
	        isConnected = false;
	        setBusy(false);
	    } else  {
                CommonGlobals.stdErrPrintln("JMSException caught: " + jmse.getMessage());
                jmse.printStackTrace();
	    }
        } catch (Exception e) {
	    if (sslTransportUsed)  {
	        isConnected = false;
	        setBusy(false);
	    } else  {
                CommonGlobals.stdErrPrintln("Exception caught: " + e.getMessage());
                e.printStackTrace();
	    }
        }
    }

    private void setFactoryAttr(String propName, String propVal) 
				throws JMSException  {
	if (qcf == null)  {
	    return;
	}

	qcf.setProperty(propName, propVal);
    }

    private String getFactoryAttr(String propName)  {
	if (qcf == null)  {
	    return (null);
	}

	String s;

	try  {
	    s = qcf.getProperty(propName);
	} catch (JMSException jmse)  {
	    s = null;
	}

	return (s);
    }

    private void createFactory(Properties brokerAttrs) throws BrokerAdminException  {
	BrokerAdminException	bae;

	qcf = new QueueConnectionFactory();

        try {
	    qcf.setConnectionType(ClientConstants.CONNECTIONTYPE_ADMIN);

	    for (Enumeration e = brokerAttrs.propertyNames(); 
				e.hasMoreElements(); ) {
	        String propName = (String)e.nextElement();
	        String value  = brokerAttrs.getProperty(propName);

	        if (value != null)  {
		    qcf.setProperty(propName, value);
	        }
	    }
	} catch (JMSException jmse) {
	    bae = new BrokerAdminException(BrokerAdminException.CONNECT_ERROR);
	    bae.setLinkedException(jmse);
	    bae.setBrokerHost(getBrokerHost());
	    bae.setBrokerPort(getBrokerPort());
            throw bae;
	    
	} catch (Exception e) {
	    bae = new BrokerAdminException(BrokerAdminException.CONNECT_ERROR);
	    bae.setLinkedException(e);
	    bae.setBrokerHost(getBrokerHost());
	    bae.setBrokerPort(getBrokerPort());
            throw bae;
	}

	if (debug)  {
	    CommonGlobals.stdOutPrintln("***** BrokerAdminConn instance: " + getKey());
	    CommonGlobals.stdOutPrintln("BrokerAdminConn created with timeout set to: "
			+ (this.timeout/1000)
			+ " seconds");
	    CommonGlobals.stdOutPrintln("BrokerAdminConn created with num retries set to: " + this.numRetries);
	}

    }

    protected Message receiveCheckMessageTimeout(boolean isShutdownReply) 
				throws BrokerAdminException  {
        return (receiveCheckMessageTimeout(isShutdownReply, true));
    }

    /*
     * Convenience method used to check the received mesg
     * if it is null.
     *
     * If it is null, this means that the receive() method timed out.
     * An appropriate number of retries is attempted after which
     * a BrokerAdminException is thrown. Each receive retry is made 
     * with a timeout that is a multiple of the original timeout.
     */
    protected Message receiveCheckMessageTimeout(boolean isShutdownReply, 
			boolean waitForResponse) 
				throws BrokerAdminException  {
	Message mesg = null;
        BrokerAdminException	bae;
	long incrTimeout = timeout;
	int localNumRetries = 0;

	try  {
	    while (localNumRetries <= numRetries)  {
                mesg = (ObjectMessage)receiver.receive(incrTimeout);

                /* REVISIT: There is a timing problem in the protocol.  
                   The GOODBYE message could be processed before the SHUTDOWN_REPLY
                   message and therefore could be sending null as a value for 'mesg'
                   when receive() returns.  We will assume that the SHUTDOWN operation
                   was successful when we receive status == 200 or mesg == null.
                 */
	        if (mesg != null)  {
		    break;
	        } else  {
	            if (isShutdownReply) {
                        isConnected = false;
			break;
	            } else {
			localNumRetries++;
			incrTimeout += timeout;

			if (localNumRetries <= numRetries)  {
			    /*
			     * Send a status event so that something like
			     * the following can be printed:
			     *
			     *  Broker not responding, 
			     *	  retrying [1 of 2 attempts, timeout=20 seconds]
			     */
	                    CommonCmdStatusEvent cse = newCommonCmdStatusEvent(CommonCmdStatusEvent.BROKER_BUSY);
			    cse.setNumRetriesAttempted(localNumRetries);
			    cse.setMaxNumRetries(numRetries);
			    cse.setRetryTimeount((incrTimeout/1000));
			    fireAdminEventDispatched(cse);
			} else {
		            /*
		             * It looks like we timed out waiting for a reply
			     * even after several retries.
		             * We should:
		             *	- fire off a thread to continue waiting for the reply.
		             *	   (this will mark this BrokerAdminConn as 'busy').
		             *	- throw an exception saying the reply was not received.
		             */
    		            msgAckThread = new MessageAckThread(this);
    		            msgAckThread.start();

		            if (waitForResponse)
                                bae = new BrokerAdminException(
			            BrokerAdminException.REPLY_NOT_RECEIVED);
	 	            else
                                bae = new BrokerAdminException(
			            BrokerAdminException.IGNORE_REPLY_IF_RCVD);
                            throw bae;
	                }
	            }
	        }
	    }
        } catch (Exception e) {
	    handleReceiveExceptions(e);
        }

	return (mesg);
    }

    protected void checkReplyTypeStatus(Message mesg, int msgType, String msgTypeString) 
					throws BrokerAdminException  {
        BrokerAdminException	bae;
	int			actualMsgType,
				actualReplyStatus;

        /* REVISIT: There is a timing problem in the protocol.  
           The GOODBYE message could be processed before the SHUTDOWN_REPLY
           message and therefore could be sending null as a value for 'mesg'
           when receive() returns.  We will assume that the SHUTDOWN operation
           was successful when we receive status == 200 or mesg == null.
         */
	if (mesg == null)  {
	    if (checkShutdownReply && (msgType == getAdminMessageTypeSHUTDOWN_REPLY())) {
                isConnected = false;
                return;
	    }
	}

	/*
	 * Fetch reply message type
	 */
	try  {
            actualMsgType = mesg.getIntProperty(getAdminMessagePropNameMessageType());
	} catch (JMSException jmse)  {
            bae = new BrokerAdminException(
			BrokerAdminException.PROB_GETTING_MSG_TYPE);
	    bae.setLinkedException(jmse);
            throw bae;
	}
        
	/*
	 * Fetch reply status code
	 */
	try  {
            actualReplyStatus = mesg.getIntProperty(getAdminMessagePropNameStatus());
	} catch (JMSException jmse)  {
            bae = new BrokerAdminException(
			BrokerAdminException.PROB_GETTING_STATUS);
	    bae.setLinkedException(jmse);
            throw bae;
	}

	if (debug)  {
	    CommonGlobals.stdOutPrintln("\tReplyMsgType="
			+ actualMsgType
			+ "(expecting "
			+ msgType
			+ "["
			+ msgTypeString
			+ "]), ReplyStatus="
			+ actualReplyStatus);
	}

	/*
	 * Both values must be correct
	 */
	if ((msgType == actualMsgType) && (actualReplyStatus == getAdminMessageStatusOK())) {
            if (msgType == getAdminMessageTypeSHUTDOWN_REPLY())
                isConnected = false;

	    return;
	}

	/*
	 * Otherwise, report an error
	 */
	String	errorStr = getErrorMessage(mesg);

	if (debug)  {
	    CommonGlobals.stdOutPrintln("\tJMQ_ERROR_STRING=" + errorStr);
	}

        bae = new BrokerAdminException(BrokerAdminException.MSG_REPLY_ERROR);
	bae.setBrokerErrorStr(errorStr);
	bae.setReplyMsgType(actualMsgType);
	bae.setReplyStatus(actualReplyStatus);
    bae.setReplyMsg(mesg);
        throw bae;
    }


    protected void handleSendExceptions(Exception e) 
				throws BrokerAdminException  {
	BrokerAdminException bae;

	if (e instanceof BrokerAdminException)  {
	    throw ((BrokerAdminException)e);
	} else if (e instanceof JMSException)  {
	    /*
	     * Handled separately from regular Exceptions in case we know
	     * enough to report them in a more useful way.
	     */
	    bae = new BrokerAdminException(BrokerAdminException.MSG_SEND_ERROR);
	    bae.setLinkedException(e);
            throw bae;
	} else {
	    bae = new BrokerAdminException(BrokerAdminException.MSG_SEND_ERROR);
	    bae.setLinkedException(e);
            throw bae;
	}
    }

    protected void handleReceiveExceptions(Exception e) 
				throws BrokerAdminException  {
	BrokerAdminException bae;

	if (e instanceof BrokerAdminException)  {
	    /*
	     * Could be thrown by checkReplyTypeStatus()
	     */
	    throw ((BrokerAdminException)e);
	} else if (e instanceof JMSException)  {
	    /*
	     * Handled separately from regular Exceptions in case we know
	     * enough to report them in a more useful way.
	     */
	    bae = new BrokerAdminException(BrokerAdminException.MSG_REPLY_ERROR);
	    bae.setLinkedException(e);
            throw bae;
	} else {
	    bae = new BrokerAdminException(BrokerAdminException.MSG_REPLY_ERROR);
	    bae.setLinkedException(e);
            throw bae;
	}
    }

    /**
     * Add an admin event listener.
     * @param l admin event listener to add.
     */
    public void addAdminEventListener(AdminEventListener l)  {
        eListeners.addElement(l);
    }

    /**
     * Remove an admin event listener.
     * @param l admin event listener to remove.
     */
    public void removeAdminEventListener(AdminEventListener l)  {
        eListeners.removeElement(l);
    }

    /**
     * Remove all admin event listeners.
     */
    public void removeAllAdminEventListeners()  {
	eListeners.removeAllElements();
    }

    /**
     * Fire off/dispatch an admin event to all the listeners.
     * @param ae AdminEvent to dispatch to event listeners.
     */
    public void fireAdminEventDispatched(AdminEvent ae)  {
        for (int i = 0; i < eListeners.size(); i++) {
            ((AdminEventListener)eListeners.elementAt(i)).
		adminEventDispatched(ae);
        }
    }

    protected void sendStatusEvent(Message mesg, Exception ex)  {
	if (getCurrentStatusEvent() != null)  {
	    /*
	     * Set success flag to true/false before
	     * firing off status event.
	     */

	    if (mesg == null)  {
		/*
		 * If mesg is null, ex should hold an exception
		 * that caused the failure
		 */
		getCurrentStatusEvent().setSuccess(false);
		getCurrentStatusEvent().setLinkedException(ex);
	    } else  {
		/*
		 * If mesg is not null, check for reply/status code
		 */
	        try  {
                    checkReplyTypeStatus(mesg, getCurrentStatusEvent().getReplyType(), 
					getCurrentStatusEvent().getReplyTypeString());

		    if (mesg instanceof ObjectMessage) {
			try {
		            getCurrentStatusEvent().setReturnedObject(
				((ObjectMessage)mesg).getObject());
			} catch (JMSException jmse) {
			    /*
			     * Problems retrieving the associated object.
			     * Report an error.
			     */
                	    getCurrentStatusEvent().setSuccess(false);
                	    getCurrentStatusEvent().setLinkedException(jmse);
			}
		    }
		    getCurrentStatusEvent().setSuccess(true);

	        } catch (BrokerAdminException bae)  {
		    getCurrentStatusEvent().setSuccess(false);
		    getCurrentStatusEvent().setLinkedException(bae);
	        }
	    }

            fireAdminEventDispatched(getCurrentStatusEvent());
            clearStatusEvent();
	}
    }

    /*
     * BEGIN INTERFACE ExceptionListener
     */
    public void onException(JMSException jmse) {
	BrokerErrorEvent bee = null;
        isConnected = false;

	/*
	 * Broker initiated connection close.
	 */
        if (ClientResources.X_BROKER_GOODBYE == jmse.getErrorCode()) {
	    /*
	     * Check to see if this BrokerAdminConn has initiated the
	     * shutdown.  Only propagate the error message if it is
	     * NOT initiated by this BrokerAdminConn.
	     */
	    if (!isInitiator()) {
                bee = new BrokerErrorEvent(this, BrokerErrorEvent.ALT_SHUTDOWN);
                bee.setBrokerHost(getBrokerHost());
                bee.setBrokerPort(getBrokerPort());
                bee.setBrokerName(getKey());
	    }
	/*
	 * Broker unexpectedly shutdown.
	 */
        } else if (jmse.getLinkedException() instanceof EOFException) {
            bee = new BrokerErrorEvent(this, BrokerErrorEvent.UNEXPECTED_SHUTDOWN);
	    bee.setBrokerHost(getBrokerHost());
	    bee.setBrokerPort(getBrokerPort());
	    bee.setBrokerName(getKey());
	/*
	 * Other misc. connection problems.
	 */
        } else {
            bee = new BrokerErrorEvent(this, BrokerErrorEvent.CONNECTION_ERROR);
            bee.setBrokerHost(getBrokerHost());
            bee.setBrokerPort(getBrokerPort());
            bee.setBrokerName(getKey());
	}
	if (bee != null)
            fireAdminEventDispatched(bee);
	removeAllAdminEventListeners();
    }
    /*
     * END INTERFACE ExceptionListener
     */
    
    public static void setDebug(boolean b)  {
	debug = b;
    }

    public static boolean getDebug()  {
	return (debug);
    }
}

class MessageAckThread implements Runnable  {
    private Thread		ackThread = null;
    private BrokerAdminConn		ba;
    private boolean		msgReceived = false;
    private long		timeout = 30000;
    private boolean		debug = false,
				stopRequested = false;

    public MessageAckThread(BrokerAdminConn ba)  {
	debug = BrokerAdminConn.getDebug();

	if (debug)  {
            CommonGlobals.stdOutPrintln("***** Created MessageAckThread");
	}
	this.ba = ba;
    }

    public synchronized void start()  {
	if (ackThread == null)  {
	    ackThread = new Thread(this, "JMQ Administration MessageAckThread");
	    ackThread.start();
	    if (debug)  {
                CommonGlobals.stdOutPrintln("***** Started MessageAckThread");
	    }
	}
    }

    public synchronized void stop()  {
	stopRequested = true;
    }

    public void run()  {
        Message mesg = null;

	ba.setBusy(true);

	while (!msgReceived && ackThread != null && !stopRequested) {
	    try  {
	        mesg = ba.receiver.receive(timeout);

		if (mesg != null)  {
		    if (debug)  {
		        CommonGlobals.stdOutPrintln("***** MessageAckThread: received reply message !");
		    }
		    msgReceived = true;
		    if (debug)  {
		        CommonGlobals.stdOutPrintln("***** MessageAckThread: acknowledging reply message.");
		    }
                    mesg.acknowledge();
		    synchronized (this) {
		        ba.setBusy(false);
		        ba.sendStatusEvent(mesg, null);
		    }
		} else  {
		    synchronized (this)  {
			if (stopRequested)  {
		            if (debug)  {
		                CommonGlobals.stdOutPrintln("***** MessageAckThread: receive() timed out. Not retrying (stop requested).");
		            }
			    stopRequested = false;
			    ackThread = null;
			    return;
			}
		    }

		    if (debug)  {
		        CommonGlobals.stdOutPrintln("***** MessageAckThread: receive() timed out. Retrying...");
		    }
		}
	    } catch (Exception e)  {
		/*
	         * Caught exception while waiting for reply message.
		 * Report error in status event.
		 */
		synchronized (this) {
		    ba.setBusy(false);
		    ba.sendStatusEvent(null, e);
		}
	    }
	}

	ba.setBusy(false);
    }
}
