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
 * @(#)ConnectionInitiator.java	1.32 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import com.sun.messaging.*;
import java.net.MalformedURLException;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

import java.util.StringTokenizer;
import java.util.logging.Level;

//import java.util.logging.*;

/**
 * This class encapsulates the connection establishment, reconnect and
 * failover logic.
 */
public class ConnectionInitiator {
    private ConnectionImpl connection = null;
    //private int next = 0;
    private MQAddressList addrList = null;

    private String addrListString = null;

    private boolean useAddressList = false;
    private int nextStart = 0;

    private int reconnectDelay = 3000;
    private int reconnectRetries = 0;
    private int addressListIterations = 0;
    private boolean debug = Debug.debug;

    private static final String PRIORITY = "PRIORITY";

    private static final String RANDOM = "RANDOM";

    private static final String JMS_SERVICE_NAME = "jms";

    private String defaultService = JMS_SERVICE_NAME;

    private static final String SSLJMS_SERVICE_NAME = "ssljms";

    //ha reconnect delay
    public static final int HA_RECONNECT_DELAY = 3000;

    //set to true if not to use the list returned in HELLO_REPLY.
    private boolean useStaticAddressList = false;

    private boolean isJMSService = true;

    //this flag is set to true by ReadChannel when HELLO_REPLY status is "301".
    private volatile boolean shouldRedirect = false;

    private boolean isRedirected = false;

    private String redirectURL = null;

    //private Logger rootLogger = Logger.getLogger(ConnectionImpl.ROOT_LOGGER_NAME);

    public ConnectionInitiator(ConnectionImpl connection) throws JMSException,
        MalformedURLException {

        this.connection = connection;

        init();
    }

    private void init() throws JMSException, MalformedURLException {

        if (debug) {
            Debug.println("In ConnectionInitiator.init()");
        }

        useStaticAddressList = Boolean.getBoolean("imq.useStaticAddressList");

        String prop = connection.getTrimmedProperty(
            ConnectionConfiguration.imqReconnectInterval);

        if (prop != null) {
            reconnectDelay = Integer.parseInt(prop);

            if ( connection.isConnectedToHABroker ) {
                //if HA connection and the reconnect delay value is less than
                //the HA_RECONNECT_DELAY value, use the HA value.
                if (reconnectDelay < HA_RECONNECT_DELAY) {
                    reconnectDelay = HA_RECONNECT_DELAY;
                }
            }

        } else {
            if ( connection.isConnectedToHABroker ) {
                reconnectDelay = HA_RECONNECT_DELAY;
            }
        }

        prop = connection.getTrimmedProperty(
            ConnectionConfiguration.imqReconnectAttempts);

        if (prop != null) {
            reconnectRetries = Integer.parseInt(prop);
        }

        prop = connection.getTrimmedProperty(
            ConnectionConfiguration.imqAddressListIterations);

        if (prop != null) {
            addressListIterations = Integer.parseInt(prop);
        }

        prop =
            connection.getTrimmedProperty(ConnectionConfiguration.
                                          imqAddressList);

        addrList = this.createAddressList(prop);

        setDefaultService(addrList);
    }

    protected ConnectionHandler createConnection() throws JMSException {
        return createConnection(false);
    }

    protected ConnectionHandler reconnect() throws JMSException,
        MalformedURLException {

        ConnectionHandler handler = null;

        if (debug) {
            Debug.println("In ConnectionInitiator.reconnect()");
        }

        if ( connection.isConnectedToHABroker ) {
            if ( reconnectDelay < HA_RECONNECT_DELAY ) {
                reconnectDelay = HA_RECONNECT_DELAY;
            }
        }

        if (shouldRedirect) {
            handler = redirect();
        } else {

            Debug.println("*** Old broker list: " +
                          connection.savedJMQBrokerList);
            Debug.println("*** New broker list: " + connection.JMQBrokerList);

            if (connection.shouldUpdateAddressList()) {

                if (debug) {
                    Debug.println("*** updating broker address list: " +
                                  connection.JMQBrokerList);
                }

                resetAddressList(connection.JMQBrokerList);

                connection.savedJMQBrokerList = connection.JMQBrokerList;
            }

            handler = createConnection(true);
        }

        return handler;
    }

    private ConnectionHandler createConnection(boolean isReconnect) throws
        JMSException {

        ConnectionHandler ch = null;

        if (useAddressList) {
            ch = createConnectionNew(isReconnect); // TBD: Rename the method.
        } else {
            ch = createConnectionOld(isReconnect); // TBD: Rename the method.
        }

        if ( ch == null ) {

            if ( debug ) {
                Debug.println("*** ConnectionInitiator.createConnection() returning null ConnectionHandler ...");
            }
        }

        return ch;
    }

    private ConnectionHandler createConnectionNew(boolean isReconnect) throws
        JMSException {
        if (debug) {
            Debug.println("In ConnectionInitiator.createConnectionNew()");
        }
        
        //for bug id 6517341 - HA: Client runtime needs to improve reconnect 
        //logic if imqReconnectEnabled property is set to true.
        if ( isReconnect ) {
        	if (this.connection.isConnectedToHABroker) {
        		//we are trying forever until successful or closed
        		this.addressListIterations = -1;
        	}
        }
        
        // elist keeps track of the last lower level exception for
        // each address in the addrList.
        Exception elist[] = new Exception[addrList.size()];
        String alist[] = new String[addrList.size()];
        //Exception lastException = null;

        for (int i = 0; addressListIterations <= 0 ||
                     i < addressListIterations; i++) {

            for (int j = 0; j < addrList.size(); j++) {

                int currentIndex = (nextStart + j) % addrList.size();
                MQAddress addr = (MQAddress) addrList.get(currentIndex);

                try {

                    ConnectionHandler connHandler = this.createConnection(addr);
                    nextStart = this.getNextStartIndex(isReconnect,
                        currentIndex);

                    return connHandler;

                } catch (Exception e) {

                	if ( connection.isCloseCalled ) { //CCC
                		//connection is closed.  quit now.
                		if ( e instanceof JMSException ) {
                			throw ((JMSException) e);
                		} else {
                			ExceptionHandler.handleConnectException (e, null);
                		}
                	}
                	
                    if (debug) {
                        Debug.printStackTrace(e);
                    }

                    elist[j] = e;
                    alist[j] = addr.toString();
                }

                if (j != addrList.size() - 1) {
                    try {
                    	// sleep before trying next address
                        Thread.sleep(reconnectDelay);
                    } catch (Exception se) {}
                }
            }

            if (i != addressListIterations - 1) {
                try {
                    Thread.sleep(reconnectDelay);
                } catch (Exception se) {}
            }
        }
        if (elist.length == 1) {

            MQAddress addr = (MQAddress) addrList.get(0);

            String url = addr.getURL();

            ExceptionHandler.handleConnectException(
                elist[0], url);

            // This statement is never executed because
            // handleException() method always throws an exception...
            return null;
        } else {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_NET_CREATE_CONNECTION,
                "[" + addrListString + "]");
            
            ConnectException ce = new ConnectException(errorString,
            		ClientResources.X_NET_CREATE_CONNECTION,
            		elist, alist);

            ExceptionHandler.handleConnectException(
                ce, "");

            // This statement is never executed because
            // handleException() method always throws an exception...
            return null;
        }
    }

    private ConnectionHandler
        createConnectionOld(boolean isReconnect) throws JMSException {

        if (debug) {
            Debug.println("In ConnectionInitiator.createConnectionOld()");
        }

        int count = 0;
        while (true) {
            // If the connection is closed at this time
            // just return with false status
            //bug 6189645 -- general blocking issues.

            if (connection.isCloseCalled) {

                String errstr =
                    AdministeredObject.cr.getKString(ClientResources.X_CONNECTION_CLOSED);

                JMSException jmse = new com.sun.messaging.jms.JMSException
                (errstr, ClientResources.X_CONNECTION_CLOSED);

                ExceptionHandler.throwJMSException(jmse);
            }

            count++;

            try {

                if (isReconnect) {
                    sleep(reconnectDelay);
                }

                //bug 6189645 -- general blocking issues.
                //synchronized (connection) {
                //String handler = connection.getProperty(
                //    ConnectionConfiguration.imqConnectionHandler);
                //StreamHandler sh =
                //    StreamHandlerFactory.getStreamHandler(handler);
                //return sh.openConnection(connection);

                return this.openConnection();

            } catch (JMSException jmse) {

                //logCaughtException(jmse);

                if (isReconnect == false) {
                    //log throwing an exception
                    //rootLogger.throwing("ConnectionInitiator", "createConnectionOld", jmse);

                    throw jmse;
                } else {
                    triggerConnectionReconnectFailedEvent(jmse);
                }

                if (reconnectRetries > 0 && count >= reconnectRetries) {
                    //break;

                    //rootLogger.throwing("ConnectionInitiator", "createConnectionOld", jmse);

                    throw jmse;
                }
            }
        }

        //throw new JMSException("Unable to connect.");
    }

    private void sleep (long time) {
        try {
            Thread.sleep(time);
        } catch (Exception e) {
            ExceptionHandler.logCaughtException(e);
        }
    }

    /**
     * called from createConnectionOld.
     * @return ConnectionHandler
     * @throws JMSException
     */
    private ConnectionHandler openConnection () throws JMSException {

        try {

            String handler = connection.getProperty(
                    ConnectionConfiguration.imqConnectionHandler);
            StreamHandler sh =
                StreamHandlerFactory.getStreamHandler(handler);

            return sh.openConnection(connection);

        } catch (Exception e) {

            JMSException jmse = null;

            if (e instanceof JMSException) {
                jmse = (JMSException) e;
            } else {
                jmse = ExceptionHandler.getJMSException(e, ClientResources.X_CAUGHT_EXCEPTION, false);
                jmse.setLinkedException(e);
            }

            //throw jmse;
            ExceptionHandler.throwJMSException(jmse);
        }

        return null;
    }

    /**
     * Get if this connection uses address list.
     * @return
     */
    public boolean getUseAddressList() {
        return this.useAddressList;
    }

    /**
     * Get address list size used in this connection.
     * @return
     */
    public int getAddrListSize() {
        int size = 0;

        if (this.addrList != null) {
            size = addrList.size();
        }

        return size;
    }

    /**
     * Hawk development.
     * @param aList MQAddressList
     * @throws JMSException
     */
    private void setBehavior(MQAddressList aList) throws JMSException {

        useAddressList = false;

        if (aList != null && aList.size() > 0) {

            useAddressList = true;

            String prop = connection.getProperty(
                ConnectionConfiguration.imqAddressListBehavior);

            if (PRIORITY.equalsIgnoreCase(prop)) {
                aList.setBehavior(MQAddressList.PRIORITY);
            } else if (RANDOM.equalsIgnoreCase(prop)) {
                aList.setBehavior(MQAddressList.RANDOM);
            } else {
                JMSException jmse = new com.sun.messaging.jms.JMSException (
                    "Bad imqAddressListBehavior value : " + prop);

                ExceptionHandler.throwJMSException(jmse);
            }

            if (debug) {
                Debug.println("Address list : \n" + aList);
            }
        }

    }

    /**
     * This validates if the address list fulfils HAWK HA requirement.
     * @param aList MQAddressList
     * @throws JMSException
     */
    private void validate(MQAddressList aList) throws JMSException {
        //validate if all MQAddress use the same service.
    }

    /**
     * Create an AddressList object from the address list string
     * @param addrString String
     * @return MQAddressList
     * @throws JMSException
     * @throws MalformedURLException
     */
    private MQAddressList createAddressList(String addrString) throws
        JMSException, MalformedURLException {

        MQAddressList aList = null;

        addrListString = addrString;

        if (addrString != null && !addrString.equals("")) {
            aList = MQAddressList.createMQAddressList(addrString);
            validate(addrList);
        }

        setBehavior(aList);

        //reinit next start counter.
        this.nextStart = 0;

        return aList;
    }

    private void setDefaultService(MQAddressList aList) {

        Debug.println("*** set default service with address list: "
                          + aList);

        if (aList != null && aList.size() > 0) {
            //get the first service name
            MQAddress addr = (MQAddress) aList.get(0);
            defaultService = addr.getServiceName();
        } else {
            defaultService = connection.getTrimmedProperty (
                ConnectionConfiguration.imqBrokerServiceName);

            if (defaultService == null) {
                defaultService = JMS_SERVICE_NAME;
            }
        }

        if (JMS_SERVICE_NAME.equalsIgnoreCase(this.defaultService)) {
            this.isJMSService = true;
        } else {
            this.isJMSService = false;
        }

        if (debug) {
            Debug.println("*** default service name: " + defaultService);
        }

    }

    public String getDefaultServiceName() {
        return this.defaultService;
    }

    public void resetAddressList(String alString) throws JMSException,
        MalformedURLException {

        boolean resetAddr = false;

        if (useStaticAddressList == true) {
            return;
        }

        //min check only -- if null or at least start with "mq://"
        if (alString == null || alString.length() < 5) {
            return;
        }

        Debug.println("*** isJMSService: " + isJMSService);
        Debug.println("*** defaultService: " + defaultService);

        if (isJMSService) {
            addrListString = alString;
            addrList = createAddressList(alString);

            resetAddr = true;
        } else {
            //if ssljms service, construct new addr list string
            if (SSLJMS_SERVICE_NAME.equalsIgnoreCase(defaultService)) {
                //append ssljms service name to each address in the list.
                String newAddrList =
                    appendServiceName(alString, SSLJMS_SERVICE_NAME);

                addrListString = newAddrList;

                addrList = createAddressList(newAddrList);

                resetAddr = true;
            }
        }

        if (debug) {
            if (resetAddr) {
                Debug.println("**** address list reset: " + addrListString);
            }
        }

    }

    /**
     * This method only support addrString in the following syntax:
     * "mq://host:port" or "mq://host:port/".
     * @param addrString String
     * @param serviceName String
     * @return String
     */
    public static String appendServiceName(String addrString,
                                           String serviceName) {

        StringBuffer sb = new StringBuffer();
        StringTokenizer st = new StringTokenizer(addrString, " ,");

        while (st.hasMoreTokens()) {

            String s = st.nextToken();

            sb.append(s);

            //search first '/' after "mq://"
            if (s.indexOf('/', 5) < 0) {
                sb.append('/');
            }

            sb.append(serviceName);

            if (st.hasMoreTokens()) {
                sb.append(", ");
            }
        }

        return sb.toString();
    }

    private ConnectionHandler redirect() throws JMSException {

        ConnectionHandler connHandler = null;

        try {
            connHandler = doRedirect();
        } catch (Exception e) {
            connHandler = createConnectionNew(true);
        }

        return connHandler;
    }

    /**
     * Reconnect to the specified broker address.
     *
     */
    private ConnectionHandler doRedirect() throws JMSException {

        ConnectionHandler connHandler = null;
        String newaddr = redirectURL;

        try {

            //1. get MQAddress.
            //2. connect to the MQ address.
            if (this.isJMSService == false) {
                StringBuffer sb = new StringBuffer();

                //construct MQAddress with ssljms service name.
                sb.append(redirectURL);

                //search first '/' after "mq://"
                if (redirectURL.indexOf('/', 5) < 0) {
                    sb.append('/');
                }

                sb.append(SSLJMS_SERVICE_NAME);

                newaddr = sb.toString();
            }

            if (debug) {
                Debug.info("**** ConnectionInitiator: redirecting connection: " +
                           newaddr);
            }

            MQAddress mqaddr = MQAddress.createMQAddress(newaddr);

            String handler = mqaddr.getHandlerClass();
            StreamHandler sh = StreamHandlerFactory.getStreamHandler(handler);
            connHandler = sh.openConnection(mqaddr, connection);

            this.isRedirected = true;

            if (debug) {
                Debug.info("**** ConnectionInitiator: conn redirected: " +
                           newaddr);
            }

        } catch (JMSException jmse) {
            throw jmse;
        } catch (Exception e) {
            //JMSException je = new JMSException(e.getMessage());
            //je.setLinkedException(e);
            //throw je;
            ExceptionHandler.handleConnectException(e,connection.getLastContactedBrokerAddress());

        } finally {
            this.shouldRedirect = false;
        }

        return connHandler;
    }

    /**
     *
     */
    public void setRedirectURL(String url) {
        this.shouldRedirect = true;
        this.redirectURL = url;
        
        String msg = "RedirectURL=" + url;
        ConnectionImpl.connectionLogger.log(Level.INFO, msg);
    }

    public boolean isBrokerRedirected() {

        if (this.isRedirected) {
            this.isRedirected = false;
            return true;
        } else {
            return false;
        }
    }

    private ConnectionHandler
        createConnection(MQAddress address) throws JMSException {

        ConnectionHandler connHandler = null;

        if (debug) {
            Debug.println("Create connection with MQ address: " + address);
        }

        if (debug) {
            Debug.println("Reconnect retries: " + this.reconnectRetries);
        }

        boolean keepTrying = true;
        int ct = 0;

        while (keepTrying) {
            // If the connection is closed at this time
            // just return with false status
            //bug 6189645 -- general blocking issues.
            if (connection.isCloseCalled) {
                if (debug) {
                    Debug.println("#### connection.isClosed = true");
                }

                String errstr =
                    AdministeredObject.cr.getKString(ClientResources.X_CONNECTION_CLOSED);

                JMSException jmse = new com.sun.messaging.jms.JMSException
                (errstr, ClientResources.X_CONNECTION_CLOSED);

                ExceptionHandler.throwJMSException(jmse);
            }

            try {

                if (debug) {
                    Debug.println("#### Connecting to :" + address +
                                  "  counter: " + ct);
                }

                String handler = address.getHandlerClass();
                StreamHandler sh =
                    StreamHandlerFactory.getStreamHandler(handler);

                connHandler = sh.openConnection(address, connection);

                //break out of the loop.
                return connHandler;

            } catch (Exception e) {

                ct++;

                if (debug) {
                    Debug.println("\nConnection Attempt failed.\n" +
                                  ", Address = " + address +
                                  ", attempt# = " + ct);
                    Debug.printStackTrace(e);
                }

                JMSException jmse = this.getJMSException(e);

                this.triggerConnectionReconnectFailedEvent(jmse);

                //logCaughtException (jmse);

                if ( e instanceof JMSException == false ) {
                    ExceptionHandler.logCaughtException(e);
                }

                if (this.reconnectRetries < 0 || ct < this.reconnectRetries) {
                    this.sleepReconnectDelay();
                } else {
                    //throw jmse;
                    ExceptionHandler.throwJMSException(jmse);
                }
            }
        }

        if (connHandler == null) {
            Debug.info("**** error: Connection handler is null ****");
        }

        return connHandler;

    }

    private void sleepReconnectDelay() {
        try {
            Thread.sleep(this.reconnectDelay);
        } catch (Exception e) {
            ;
        }
    }

    private JMSException getJMSException(Exception e) {

        if (e instanceof JMSException) {
            return (JMSException) e;
        } else {
            JMSException jmse = ExceptionHandler.getJMSException(e, ClientResources.X_CAUGHT_EXCEPTION, false);
            jmse.setLinkedException(e);

            return jmse;
        }

    }

    private void triggerConnectionReconnectFailedEvent(JMSException jmse) {
        connection.triggerConnectionReconnectFailedEvent(jmse);
    }

    private int getNextStartIndex(boolean isReconnect, int currentIndex) {

        int next = currentIndex;

        if (reconnectRetries > 0) {
            next = currentIndex;
        } else {
            if (isReconnect) {
                if (connection.failoverEnabled) {
                    next = (currentIndex + 1) % addrList.size();
                } else {
                    next = currentIndex;
                }
            } else {
                next = (currentIndex + 1) % addrList.size();
            }
        }

        return next;
    }

    /**
     * log the linked exception from the caught JMSException.
     * @param jmse JMSException
     */
    //private static void logException (JMSException jmse) {

    //    Throwable throwable = jmse.getLinkedException();

    //    ExceptionHandler.logCaughtException(throwable);
    //}

}
