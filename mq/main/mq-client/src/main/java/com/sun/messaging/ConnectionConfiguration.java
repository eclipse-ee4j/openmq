/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging;

/**
 * The <code>ConnectionConfiguration</code> class contains OpenMQ specific connection configuration property names and
 * special values.
 *
 * @see com.sun.messaging.ConnectionFactory com.sun.messaging.ConnectionFactory
 */
public class ConnectionConfiguration {

    /* No public constructor needed */
    private ConnectionConfiguration() {
    }

    /**
     * This property holds the address list that will be used to connect to the OpenMQ Message Service.
     * <p>
     * <b>Message Server Address Syntax</b>
     * <p>
     * The syntax for specifying a message server address is as follows:<br>
     * </p>
     * <p>
     * <code><i>scheme</i>://<i>address_syntax</i></code><br>
     * </p>
     *
     * <p>
     * where the <code><i>scheme</i></code> and <code><i>address_syntax</i></code> are described in the folowing table.
     * </p>
     * <TABLE border="1">
     * <caption>scheme and address_syntax for a message server address</caption>
     * <TR>
     * <TH>Scheme</TH>
     * <TH>Connection Service</TH>
     * <TH>Description</TH>
     * <TH>Syntax</TH>
     * </TR>
     *
     * <TR>
     * <TD valign="top"><code>mq</code></TD>
     *
     * <TD valign="top"><code>jms<br>
     *         and <br>
     *         ssljms</code></TD>
     *
     * <TD valign="top">The MQ Port Mapper at the specified host and port will handle the connection request, dynamically
     * assigning a port based on the specified connection service. Once the port number is known, MQ makes the
     * connection.</TD>
     *
     * <TD valign="top"><code>[<i>hostName</i>][:<i>port</i>][/<i>serviceName</i>]</code> <br>
     * Defaults (for jms service only): <br>
     * <code><i>hostName</i> = localhost <br>
     *       <i>port</i> = 7676 <br>
     *       <i>serviceName</i> = jms</code></TD>
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>mqtcp</code></TD>
     *
     * <TD valign="top"><code>jms</code></TD>
     *
     * <TD valign="top">MQ makes a direct tcp connection to the specified host and port to establish a connection.</TD>
     *
     * <TD valign="top"><code><i>hostName</i>:<i>port</i>/jms</code></TD>
     *
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>mqssl</code></TD>
     *
     * <TD valign="top"><code>ssljms</code></TD>
     *
     * <TD valign="top">MQ makes a direct, secure ssl connection to the specified host and port to establish a
     * connection.</TD>
     *
     * <TD valign="top"><code><i>hostName</i>:<i>port</i>/ssljms</code></TD>
     *
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>htttp</code></TD>
     *
     * <TD valign="top"><code>httpjms</code></TD>
     *
     * <TD valign="top">MQ makes a direct HTTP connection to the specified MQ tunnel servlet URL. (The broker must be
     * configured to access the tunnel servlet.)</TD>
     *
     * <TD valign="top"><code><i>HTTPtunnelServletURL</i></code></TD>
     *
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>htttps</code></TD>
     *
     * <TD valign="top"><code>httpsjms</code></TD>
     *
     * <TD valign="top">MQ makes a direct HTTPS connection to the specified MQ tunnel servlet URL. (The broker must be
     * configured to access the tunnel servlet.)</TD>
     *
     * <TD valign="top"><code><i>HTTPStunnelServletURL</i></code></TD>
     *
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>mqws</code></TD>
     *
     * <TD valign="top"><code>wsjms</code></TD>
     *
     * <TD valign="top">MQ makes a direct WebSocket connection to the specified host and port to establish a connection.
     * </TD>
     *
     * <TD valign="top"><code><i>hostName</i>:<i>port</i>/wsjms</code></TD>
     *
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>mqwss</code></TD>
     *
     * <TD valign="top"><code>wssjms</code></TD>
     *
     * <TD valign="top">MQ makes a direct WebSocket secure connection to the specified host and port to establish a
     * connection.</TD>
     *
     * <TD valign="top"><code><i>hostName</i>:<i>port</i>/wssjms</code></TD>
     *
     * </TR>
     * </TABLE>
     * <p>
     * &nbsp;
     * </p>
     * <p>
     * The following table shows how the message server address syntax applies in some typical cases.
     * </p>
     * <TABLE border="1">
     * <caption>typical message server address syntax applications</caption>
     * <TR>
     * <TH>Connection Service</TH>
     * <TH>Broker Host</TH>
     * <TH>Port</TH>
     * <TH>Example Address</TH>
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top">Unspecified</TD>
     *
     * <TD valign="top">Unspecified</TD>
     *
     * <TD valign="top">Unspecified</TD>
     *
     * <TD valign="top">Default<br>
     * <code>(mq://localHost:7676/jms)</code></TD>
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top">Unspecified</TD>
     *
     * <TD valign="top">Specified Host</TD>
     *
     * <TD valign="top">Unspecified</TD>
     *
     * <TD valign="top"><code>myBkrHost<br>
     *       (mq://myBkrHost:7676/jms)</code></TD>
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top">Unspecified</TD>
     *
     * <TD valign="top">Unspecified</TD>
     *
     * <TD valign="top">Specified Portmapper Port</TD>
     *
     * <TD valign="top"><code>1012<br>
     *       (mq://localHost:1012/jms)</code></TD>
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>ssljms</code></TD>
     *
     * <TD valign="top">Local Host</TD>
     *
     * <TD valign="top">Default Portmapper Port</TD>
     *
     * <TD valign="top"><code>mq://localHost:7676/ssljms</code></TD>
     * </TR>
     *
     * <TR>
     * <TD valign="top"><code>ssljms</code></TD>
     *
     * <TD valign="top">Specified Host</TD>
     *
     * <TD valign="top">Default Portmapper Port</TD>
     *
     * <TD valign="top"><code>mq://myBkrHost:7676/ssljms</code></TD>
     * </TR>
     * <TR>
     * <TD valign="top"><code>ssljms</code></TD>
     *
     * <TD valign="top">Specified Host</TD>
     *
     * <TD valign="top">Specified Portmapper Port</TD>
     *
     * <TD valign="top"><code>mq://myBkrHost:1012/ssljms</code></TD>
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>jms</code></TD>
     *
     * <TD valign="top">Local Host</TD>
     *
     * <TD valign="top">Specified Service Port</TD>
     *
     * <TD valign="top"><code>mqtcp://localhost:1032/jms</code></TD>
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>ssljms</code></TD>
     *
     * <TD valign="top">Specified Host</TD>
     *
     * <TD valign="top">Specified Service Port</TD>
     *
     * <TD valign="top"><code>mqssl://myBkrHost:1034/ssljms</code></TD>
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>httpjms</code></TD>
     *
     * <TD valign="top">N/A</TD>
     *
     * <TD valign="top">N/A</TD>
     *
     * <TD valign="top"><code>http://websrvr1:8085/imq/tunnel</code></TD>
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>httpsjms</code></TD>
     *
     * <TD valign="top">N/A</TD>
     *
     * <TD valign="top">N/A</TD>
     *
     * <TD valign="top"><code>https://websrvr2:8090/imq/tunnel</code></TD>
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>wsjms</code></TD>
     *
     * <TD valign="top">Specified Host</TD>
     *
     * <TD valign="top">Specified Service Port</TD>
     *
     * <TD valign="top"><code>mqws://websrvr1:7670/wsjms</code></TD>
     * </TR>
     *
     * <TR>
     *
     * <TD valign="top"><code>wssjms</code></TD>
     *
     * <TD valign="top">Specified Host</TD>
     *
     * <TD valign="top">Specified Service Port</TD>
     *
     * <TD valign="top"><code>mqwss://websrvr2:7671/wssjms</code></TD>
     * </TR>
     * </TABLE>
     * <P>
     * &nbsp;
     * </P>
     * <p>
     * The default value of this property is <code><b>empty</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressListBehavior
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressListIterations
     */
    public static final String imqAddressList = "imqAddressList";

    /**
     * This property holds the number of times that OpenMQ will iterate through <code><b>imqAddressList</b></code> when
     * connecting to the OpenmMQ Service.
     * <p>
     * The default value of this property is <code><b>1</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressList
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressListBehavior
     */
    public static final String imqAddressListIterations = "imqAddressListIterations";

    /**
     * This property determines how OpenMQ will select entries from the <code><b>imqAddressList</b></code> property to use
     * when making a connection to the OpenMQ Message Service.
     * <p>
     * The acceptable values for this property are <code><b>PRIORITY</b></code> and <code><b>RANDOM</b></code>.
     * <p>
     * When <code><b>PRIORITY</b></code> is used, OpenMQ will start with the <i><b>first</b></i>entry in
     * <code><b>imqAddressList</b></code> when attempting to make the <b>first</b> connection to the OpenMQ Message Service.
     * <p>
     * Subsequently, when OpenMQ is attempting to re-connect to the Message Service, it will use successive entries from
     * <code><b>imqAddressList</b></code> in the order they are specified.
     * <p>
     * When <code><b>RANDOM</b></code> is used, OpenMQ will start with a <i><b>random</b></i> entry in
     * <code><b>imqAddressList</b></code> when attempting to make the <b>first</b> connection to the OpenMQ Message Service.
     * <p>
     * Subsequently, when OpenMQ is attempting to re-connect to the Message Service, it will use entries in the order they
     * are specified in <code><b>imqAddressList</b></code> starting with the initial randomly chosen entry.
     * <p>
     * The default value of this property is <code><b>PRIORITY</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressList
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressListIterations
     */
    public static final String imqAddressListBehavior = "imqAddressListBehavior";

    /**
     * This property holds the connection type used to connect to the OpenMQ Message Service.
     * <p>
     * <b>Note that this property is superseded by the </b><code>imqAddressList</code><b> property and is present only for
     * compatibility with MQ 3.0 clients. This property may be removed in the next major version of MQ. </b>
     * <p>
     * The default value of this property is <code><b>TCP</b></code>
     * <p>
     * The allowable values are <code><b>TCP</b>,<b>TLS</b>,<b>HTTP</b>,and the special value - <b>...</b></code>
     * <p>
     * When the special value of <code><b>...</b></code> is used, the classname specified in the property
     * <code><b>imqConnectionHandler</b></code> is required to handle the connection to the OpenMQ Message Service.
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressList
     */
    public static final String imqConnectionType = "imqConnectionType";

    /**
     * This property holds the name of the class that will be used to handle the connection to the OpenMQ Message Service
     * and is <b>required</b> when the value of the <code><b>imqConnectionType</b></code> property is set to
     * <code><b>...</b></code>
     * <p>
     * This property is not normally specified and will assume the classname handling the ConnectionType specified in
     * <code><b>imqConnectionType</b></code>. However, if specified, this property overrides
     * <code><b>imqConnectionType</b></code>.
     * <p>
     * The default value of this property is
     * <code><b>com.sun.messaging.jmq.jmsclient.protocol.tcp.TCPStreamHandler</b></code>
     */
    public static final String imqConnectionHandler = "imqConnectionHandler";

    /**
     * This property holds the default username that will be used to authenticate with the OpenMQ Message Service.
     * <p>
     * The default value of this property is <code><b>guest</b></code>
     */
    public static final String imqDefaultUsername = "imqDefaultUsername";

    /**
     * This property holds the default password that will be used to authenticate with the OpenMQ Message Service.
     * <p>
     * The default value of this property is <code><b>guest</b></code>
     */
    public static final String imqDefaultPassword = "imqDefaultPassword";

    /**
     * This property holds the the maximum time, in milliseconds, that a OpenMQ Client Application will wait before throwing
     * a JMSException when awaiting an acknowledgement from the OpenMQ Message Service.
     * <p>
     * A value of <code>0</code> indicates that it will wait indefinitely.
     * <p>
     * The default value for this property is <code><b>0</b></code> i.e. indefinitely.
     */
    public static final String imqAckTimeout = "imqAckTimeout";

    /**
     * This property holds the the minimum time, in milliseconds, that the MQ client runtime will wait for an asynchronous
     * send to complete before calling the CompletionListener's onException method with timed out exception
     * <p>
     * A value of <code>0</code> is not allowed.
     * <p>
     * The default value for this property is <code><b>180000</b></code>
     */
    public static final String imqAsyncSendCompletionWaitTimeout = "imqAsyncSendCompletionWaitTimeout";

    /**
     * This property indicates whether the OpenMQ Client Application will attempt to reconnect to the OpenMQ Message Service
     * upon losing its connection.
     * <p>
     * The default value for this property is <code><b>false</b></code>
     */
    public static final String imqReconnectEnabled = "imqReconnectEnabled";

    /**
     * This property specifies the interval, in milliseconds, between successive reconnect attempts made by the MQ Client
     * Application to the OpenMQ Message Service.
     * <p>
     * The default value for this property is <code><b>30000</b></code> milliseconds.
     */
    public static final String imqReconnectInterval = "imqReconnectInterval";

    /**
     * This property holds the number of reconnect attempts for each address in the <code>imqAddressList</code> property
     * that the OpenMQ Client Application will make before moving on the the next address in <code>imqAddressList</code>.
     * will make in trying to reconnect to the OpenMQ Message Service. A value of <code><b>-1</b></code> indicates that the
     * MQ Client Application will make an unlimited number of reconnect attempts.
     * <p>
     * Note that this property is only applicable when <code><b>imqReconnectEnabled</b></code> is set to
     * <code><b>true</b></code>.
     * <p>
     * The default value for this property is <code><b>0</b></code> - i.e. No reconnect attempts.
     */
    public static final String imqReconnectAttempts = "imqReconnectAttempts";

    /**
     * This property specifies the 'ping' interval, in <b>seconds</b>, between successive attempts made by an MQ Connection
     * to verify that its physical connection to the MQ broker is still functioning properly. The MQ Client Runtime uses
     * this value to set up the frequency of 'pings' made by MQ Connections that originate from this ConnectionFactory.
     * Valid values include all positive integers indicating the number of seconds to delay between successive 'pings'. A
     * value of <code><b>0</b></code>, or <code><b>-1</b></code> indicates that the MQ Connection will disable this 'ping'
     * functionality.
     * <p>
     * The default value for this property is <code><b>30</b></code> - i.e. 30 seconds between 'pings'.
     */
    public static final String imqPingInterval = "imqPingInterval";

    /**
     * When <code>imqAbortOnPingAckTimeout</code> is true, this property specifies maximum time, in milliseconds, that MQ
     * Client Runtime will wait for a ping reply or any data sent from the MQ broker since last 'ping'. This property is
     * ignored if <code>imqAbortOnPingAckTimeout</code> is false.
     * <p>
     * A value of <code>0</code> indicates no timeout
     * <p>
     * The default value for this property is <code><b>0</b></code>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAbortOnPingAckTimeout
     */
    public static final String imqPingAckTimeout = "imqPingAckTimeout";

    /**
     * This property defines the socket timeout, in milliseconds, used when a TCP connection is made to the broker. A
     * timeout of zero is interpreted as an infinite timeout. The connection will then block until established or an error
     * occurs. This property is used when connecting to the port mapper as well as when connecting to the required service.
     */
    public static final String imqSocketConnectTimeout = "imqSocketConnectTimeout";

    /**
     * This property defines port mapper client socket read timeout, in milliseconds.
     * <p>
     * A value of 0 indicates no timeout
     * <p>
     * The value of this property is not set by default.
     * <p>
     */
    public static final String imqPortMapperSoTimeout = "imqPortMapperSoTimeout";

    /**
     * This property specifies the key store location
     * <p>
     * The value of this property is not set by default.
     * <p>
     */
    public static final String imqKeyStore = "imqKeyStore";

    /**
     * This property specifies the key store password. Set this property to a non-null value will make the connection
     * capable of SSL client authenication if requested by broker. If it is set to empty string, Java system property
     * javax.net.ssl.keyStorePassword will be used if set
     * <p>
     * The value of this property is not set by default.
     * <p>
     */
    public static final String imqKeyStorePassword = "imqKeyStorePassword";

    /**
     * If this property is set to true, the MQ Client Runtime will abort the connection to the MQ broker when timeout, as
     * specified by <code>
     * imqPingAckTimeout</code>, in waiting for a 'ping' reply or any data sent from the MQ broker since last 'ping', then
     * the MQ Client Runtime will perform actions as if the connection is broken.
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqPingAckTimeout
     */
    public static final String imqAbortOnPingAckTimeout = "imqAbortOnPingAckTimeout";

    /**
     * This property indicates whether OpenMQ should set the JMSXAppID property on produced messages.
     * <p>
     * The default value of this property is <code><b>false</b></code>
     */
    public static final String imqSetJMSXAppID = "imqSetJMSXAppID";

    /**
     * This property indicates whether OpenMQ should set the JMSXUserID property on produced messages.
     * <p>
     * The default value of this property is <code><b>false</b></code>
     */
    public static final String imqSetJMSXUserID = "imqSetJMSXUserID";

    /**
     * This property indicates whether OpenMQ should set the JMSXProducerTXID property on produced messages.
     * <p>
     * The default value of this property is <code><b>false</b></code>
     */
    public static final String imqSetJMSXProducerTXID = "imqSetJMSXProducerTXID";

    /**
     * This property indicates whether OpenMQ should set the JMSXConsumerTXID property on consumed messages.
     * <p>
     * The default value of this property is <code><b>false</b></code>
     */
    public static final String imqSetJMSXConsumerTXID = "imqSetJMSXConsumerTXID";

    /**
     * This property indicates whether OpenMQ should set the JMSXRcvTimestamp property on consumed messages.
     * <p>
     * The default value of this property is <code><b>false</b></code>
     */
    public static final String imqSetJMSXRcvTimestamp = "imqSetJMSXRcvTimestamp";

    /**
     * This property holds the hostname that will be used to connect to the OpenMQ Message Service using the TCP and TLS
     * ConnectionHandler classes provided with OpenMQ.
     * <p>
     * <b>Note that this property is superseded by the </b><code>imqAddressList</code><b> property and is present only for
     * compatibility with MQ 3.0 clients. This property may be removed in the next major version of MQ. </b>
     * <p>
     * The default value of this property is <code><b>localhost</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressList
     */
    public static final String imqBrokerHostName = "imqBrokerHostName";

    /**
     * This property holds the <b>Primary Port</b> number that will be used to connect to the OpenMQ Message Service using
     * the TCP and TLS ConnectionHandler classes provided with OpenMQ.
     * <p>
     * <b>Note that this property is superseded by the </b><code>imqAddressList</code><b> property and is present only for
     * compatibility with MQ 3.0 clients. This property may be removed in the next major version of MQ. </b>
     * <p>
     * The OpenMQ Client uses this port to communicate with the <b>Port Mapper Service</b> to determine the actual port
     * number of the connection service that it wishes to use.
     * <p>
     * The default value of this property is <code><b>7676</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressList
     */
    public static final String imqBrokerHostPort = "imqBrokerHostPort";

    /**
     * This property holds the connection service name that will be used to connect to the OpenMQ Message Service using the
     * TCP and TLS ConnectionHandler classes provided with OpenMQ.
     * <p>
     * <b>Note that this property is superseded by the </b><code>imqAddressList</code><b> property and is present only for
     * compatibility with MQ 3.0 clients. This property may be removed in the next major version of MQ. </b>
     * <p>
     * By default, when the value of this property is <code><b>null</b></code>, the MQ Client uses the <b>Primary Port</b>
     * to connect to the MQ Message Service and subsequently uses the first connection service that corresponds to
     * <b>imqConnnectionType</b>.
     * <p>
     * When the value of this property is non-null, the MQ Client will first connect to the <b>Port Mapper Service</b> and
     * then use the connection service that matches <b><code>imqBrokerServiceName</code></b> ensuring that it corresponds to
     * the right <b><code>imqConnectionType</code></b>.
     * <p>
     * The default value of this property is <code><b>null</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressList
     */
    public static final String imqBrokerServiceName = "imqBrokerServiceName";

    /**
     * This property holds the actual port number that will be used to connect to the OpenMQ Message Service using the TCP
     * and TLS ConnectionHandler classes provided with OpenMQ.
     * <p>
     * <b>Note that this property is superseded by the </b><code>imqAddressList</code><b> property and is present only for
     * compatibility with MQ 3.0 clients. This property may be removed in the next major version of MQ. </b>
     * <p>
     * By default, when the value of this property is <code><b>0</b></code>, the MQ Client uses the <b>Primary Port</b> to
     * connect to the MQ Message Service and subsequently determines the actual port number of the connection service that
     * it wishes to use.
     * <p>
     * When the value of this property is non-zero, the MQ Client will use this port number to directly connect to the
     * connection service that it wishes to use without first communicating with the <b>Port Mapper Service</b>
     * <p>
     * The default value of this property is <code><b>0</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressList
     */
    public static final String imqBrokerServicePort = "imqBrokerServicePort";

    /**
     * This property holds the TLS Provider Classname that will be used when connecting to the OpenMQ Message Service using
     * the TLS ConnectionHandler class provided with OpenMQ.
     * <p>
     * The default value of this property is <code><b>com.sun.net.ssl.internal.ssl.Provider</b></code>
     */
    public static final String imqSSLProviderClassname = "imqSSLProviderClassname";

    /**
     * This property indicates whether the host is trusted when connecting to the OpenMQ Message Service using the TLS
     * ConnectionHandler class provided with OpenMQ.
     * <p>
     * If this value is set to <code>false</code>, then the Root Certificate provided by the Certificate Authority must be
     * available to the MQ Client Application when it connects to the MQ Message Service.
     * <p>
     * The default value of this property is <code><b>false</b></code>
     */
    public static final String imqSSLIsHostTrusted = "imqSSLIsHostTrusted";

    /**
     * This property holds the URL that will be used when connecting to the OpenMQ Message Service using the HTTP
     * ConnectionHandler class provided with OpenMQ.
     * <p>
     * <b>Note that this property is superseded by the </b><code>imqAddressList</code><b> property and is present only for
     * compatibility with MQ 3.0 clients. This property may be removed in the next major version of MQ. </b>
     * <p>
     * Since the URL may be shared by multiple message service instances, the optional <code> ServerName </code> attribute
     * can be used to identify a specific message service address. The <code> ServerName </code> attribute is specified
     * using the standard query string syntax. For example -
     * <code><b>http://localhost/imq/tunnel?ServerName=localhost:imqbroker</b></code>
     * <p>
     * The default value of this property is <code><b>http://localhost/imq/tunnel</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAddressList
     */
    public static final String imqConnectionURL = "imqConnectionURL";

    /**
     * This property indicates whether the OpenMQ Client Application is prevented from changing the ClientID using the
     * <code>setClientID()</code> method in <code>jakarta.jms.Connection</code>.
     * <p>
     * The default value of this property is <code><b>false</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqConfiguredClientID
     * @see jakarta.jms.Connection#setClientID(java.lang.String) Connection.setClientID(clientID)
     * @see jakarta.jms.Connection#getClientID() Connection.getClientID()
     */
    public static final String imqDisableSetClientID = "imqDisableSetClientID";

    /**
     * This property holds the value of an administratively configured ClientID.
     * <p>
     * The default value of this property is <code><b><i>null</i></b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqDisableSetClientID
     * @see jakarta.jms.Connection#setClientID(java.lang.String) Connection.setClientID(clientID)
     * @see jakarta.jms.Connection#getClientID() Connection.getClientID()
     */
    public static final String imqConfiguredClientID = "imqConfiguredClientID";

    /**
     * This property indicates whether the client identifier requested and used by this connection is to be acquired in
     * 'shared' mode.
     * <p>
     * When a client identifier is used in 'shared' mode, other connections are also allowed to use the same client
     * identifier.
     * <p>
     * If a client identifier is already in use in 'unique' mode, then a request to set a client identifier in 'shared' mode
     * will fail. Likewise, if a client identifier is already in use in 'shared' mode, then a request to set a client
     * identifier in 'unique' mode will fail.
     * <p>
     * The default value of this property is <code><b>false</b></code> - i.e. a client identifier will be requested in
     * 'unique' mode.
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqConfiguredClientID
     * @see com.sun.messaging.ConnectionConfiguration#imqDisableSetClientID
     * @see jakarta.jms.Connection#setClientID(java.lang.String) Connection.setClientID(clientID)
     * @see jakarta.jms.Connection#getClientID() Connection.getClientID()
     */
    public static final String imqEnableSharedClientID = "imqEnableSharedClientID";

    /**
     * This property is used to control the reliability of every message that is produced by a MessageProducer.
     * <p>
     * The value of this property is used only when it is set. If the property is left unset, (the default behavior), the
     * OpenMQ Client will ensure that every PERSISTENT message that is produced has been received by the OpenMQ Message
     * Service before returning from the <code>send()</code> and <code>publish()</code> methods.
     * <p>
     * If this property is set to <code><b>true</b></code>, then the MQ Client <b>will always wait</b> until it ensures that
     * the message has been reecived by the MQ Message Service before returning from the <code>send()</code> and
     * <code>publish()</code> methods.
     * <p>
     * If this property is set to <code><b>false</b></code>, then the MQ Client <b>will not wait</b> until it ensures that
     * the message has been reecived by the MQ Message Service before returning from the <code>send()</code> and
     * <code>publish()</code> methods.
     * <p>
     * When this property is used, it applies to all messages and not just the PERSISTENT messages.
     * <p>
     * The value of this property is not set by default.
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAckOnAcknowledge
     */
    public static final String imqAckOnProduce = "imqAckOnProduce";

    /**
     * This property is used to control the reliability of message acknowledgement for every message that is consumed by a
     * MessageConsumer.
     * <p>
     * The value of this property is used only when it is set. If the property is left unset, (the default behavior), the
     * OpenMQ Client will ensure that the acknowledgment for every PERSISTENT message that is consumed, has been received by
     * the OpenMQ Message Service before the appropriate method returns - i.e. either the <code>Message.acknowledge()</code>
     * method in the <code>CLIENT_ACKNOWLEDGE</code> mode or the <code>receive()</code> and/or <code>onMessage()</code>
     * methods in the <code>AUTO_ACKNOWLEDGE</code> and <code>DUPS_OK_ACKNOWLEDGE</code> modes and transacted Sessions.
     * <p>
     * If this property is set to <code><b>true</b></code>, then the MQ Client <b>will always wait</b> until it ensures that
     * the acknowledgement for the message has been reecived by the MQ Message Service before returning from the
     * <code>send()</code> and <code>publish()</code> methods.
     * <p>
     * If this property is set to <code><b>false</b></code>, then the MQ Client <b>will not wait</b> until it ensures that
     * the acknowledgement for the message has been reecived by the MQ Message Service before returning from the appropriate
     * method that is responsible for sending the acknowledgement - i.e. either the <code>Message.acknowledge()</code>
     * method in the <code>CLIENT_ACKNOWLEDGE</code> mode or the <code>receive()</code> and/or <code>onMessage()</code>
     * methods in the <code>AUTO_ACKNOWLEDGE</code> and <code>DUPS_OK_ACKNOWLEDGE</code> modes and transacted Sessions.
     * <p>
     * The value of this property is not set by default.
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqAckOnProduce
     */
    public static final String imqAckOnAcknowledge = "imqAckOnAcknowledge";

    /**
     * This property specifies the upper limit of the number of messages per consumer that will be delivered and buffered in
     * the MQ client. When the number of JMS messages delivered to a consumer reaches this limit, message delivery for that
     * consumer stops. Message delivery is only resumed when the number of unconsumed messages for that consumer drops below
     * the value determined using <code>imqConsumerFlowThreshold</code> property.
     * <p>
     * When a consumer is created, JMS messages for that consumer will begin to flow to the connection on which the consumer
     * has been established. When the number of messages delivered to the consumer reaches the limit set by
     * <code>imqConsumerFlowLimit</code>, delivery of messages is stopped. As messages are consumed, the number of
     * unconsumed messages buffered in the MQ client drops.
     * <p>
     * When the number of unconsumed messages drops below the number determined by using
     * <code>imqConsumerFlowThreshold</code>, as a percentage of <code>imqConsumerFlowLimit</code>, MQ resumes delivery of
     * messages to that consumer until the number of messages delivered to that consumer reaches the limit set by
     * <code>imqConsumerFlowLimit</code>.
     * <p>
     * Note that the actual value of the message flow limit used by MQ for each consumer is the lower of two values - the
     * <code>imqConsumerFlowLimit</code> set on the ConnectionFactory and the <code>consumerFlowLimit</code> value set on
     * the physical destination that the consumer is established on.
     * <p>
     * The default value for this property is <code><b>1000</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqConsumerFlowThreshold
     */
    public static final String imqConsumerFlowLimit = "imqConsumerFlowLimit";

    /**
     * This property controls when JMS message delivery will resume to consumers that have had their message delivery
     * stopped due to the number of messages buffered in the MQ client exceeding the limit set by
     * <code>imqConsumerFlowLimit</code> and is expressed as a percentage of <code>imqConsumerFlowLimit</code> .
     * <p>
     * See the description for the <code>imqConsumerFlowLimit</code> property for how the two properties are used together
     * to meter the flow of JMS messages to individual consumers.
     * <p>
     * The default value for this property is <code><b>50</b></code> (percent).
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqConsumerFlowLimit
     */
    public static final String imqConsumerFlowThreshold = "imqConsumerFlowThreshold";

    /**
     * When this property is set to false, there will be no prefetch next message (except the first message) from broker to
     * a consumer, and consumer flow control properties <code>imqConsumerFlowLimit</code> and <code>imqConsumerFlowThreshold
     * </code> will be ignored. When it is set to true, the default, prefetch will take place as defined by consumer flow
     * control properties.
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqConsumerFlowLimit
     * @see com.sun.messaging.ConnectionConfiguration#imqConsumerFlowThreshold
     */
    public static final String imqConsumerFlowLimitPrefetch = "imqConsumerFlowLimitPrefetch";

    /**
     * This property manages the number of JMS messages that will flow from the OpenMQ Message Service to the MQ Client
     * between each 'resume flow' notification from the Client to the Message Service to receive additional JMS messages.
     * <p>
     * If the count is set to <code><b>0</b></code> then the Message Service will <b>not</b> restrict the number of JMS
     * messages sent to the Client.
     * <p>
     * After the Message Service has sent the number of JMS messages specified by this property, it will await a 'resume
     * flow' notification from the Client before sending the next set of JMS messages. A non-zero value allows the Client to
     * receive and process control messages from the Message Service even if there is a large number of JMS messages being
     * held by the Message Service for delivery to this Client.
     * <p>
     * The MQ Client further has the ability to limit this JMS message flow using the property
     * <code>imqConnectionFlowEnabled</code>
     * <p>
     * The default value for this property is <code><b>100</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqConnectionFlowLimitEnabled
     * @see com.sun.messaging.ConnectionConfiguration#imqConnectionFlowLimit
     */
    public static final String imqConnectionFlowCount = "imqConnectionFlowCount";

    /**
     * This property indicates whether the OpenMQ Client should limit the flow of JMS messages from the OpenMQ Message
     * Service using the number of messages specified in <code>imqConnectionFlowLimit</code>.
     * <p>
     * This property will only be active if the property <code>imqConnectionFlowCount</code> has been set to a non-zero
     * value.
     * <p>
     * If this property is set to <code>true</code> the Client will use the property <code>imqConnectionFlowLimit</code> as
     * described below. Flow control will not be limited if this property is set to <code>false</code>.
     * <p>
     * The default value for this property is <code><b>false</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqConnectionFlowCount
     * @see com.sun.messaging.ConnectionConfiguration#imqConnectionFlowLimit
     */
    public static final String imqConnectionFlowLimitEnabled = "imqConnectionFlowLimitEnabled";

    /**
     * This property specifies the number of uncomsumed JMS messages that a OpenMQ Client can hold, above which the MQ
     * Client will refrain from sending a 'resume flow' notification to the OpenMQ Message Service. This property is active
     * only when the <code>imqFlowControlEnabled</code> property is set to <code>true</code> and the
     * <code>imqFlowControlCount</code> property is set to a non-zero value.
     * <p>
     * The MQ Client will notify the OpenMQ Message Service ('resume flow') and receive JMS message (in chunks determined by
     * <code>imqFlowControlCount</code>) until the number of received and uncomsumed JMS messages exceeds the value of this
     * property. At this point, the Client will 'pause' until the JMS messages are consumed and when the unconsumed JMS
     * message count drops below this value, the Client once again notifys the Message Service and the flow of JMS messages
     * to the Client is resumed.
     * <p>
     * The default value for this property is <code><b>1000</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqConnectionFlowLimitEnabled
     * @see com.sun.messaging.ConnectionConfiguration#imqConnectionFlowCount
     */
    public static final String imqConnectionFlowLimit = "imqConnectionFlowLimit";

    /**
     * For AUTO_ACKNOWLEDGE and DUPS_OK_ACKNOWLEDGE, maximum redelivery attempts (&gt;= 1) when a
     * jakarta.jms.MessageListener.onMessage() throws RuntimeException. The message will be sent to DMQ if this maximum
     * atttempts (&gt; 1) reached.
     * <p>
     * This property is ignored if the MessageListener is Message Driven Bean
     * <p>
     * The default value for this property is <code><b>1</b></code>.
     * <p>
     */
    public static final String imqOnMessageExceptionRedeliveryAttempts = "imqOnMessageExceptionRedeliveryAttempts";

    /**
     * For AUTO_ACKNOWLEDGE and DUPS_OK_ACKNOWLEDGE, the interval in milliseconds for each subsequent redelivery attempt
     * after first attempt when a jakarta.jms.MessageListener.onMessage() throws RuntimeException.
     * <p>
     * This property is ignored if the MessageListener is Message Driven Bean
     * <p>
     * The default value for this property is <code><b>500</b></code>milliseconds.
     * <p>
     */
    public static final String imqOnMessageExceptionRedeliveryIntervals = "imqOnMessageExceptionRedeliveryIntervals";

    /**
     * This property holds the the maximum time, in milliseconds, that a OpenMQ Client Application will wait before throwing
     * a NoSuchElementException when retrieving elements from a QueueBrowser Enumeration.
     * <p>
     * The default value for this property is <code><b>60000</b></code> milliseconds.
     * <p>
     *
     * @see java.util.Enumeration
     * @see java.util.Enumeration#nextElement()
     * @see java.util.NoSuchElementException
     * @see jakarta.jms.QueueBrowser jakarta.jms.QueueBrowser
     */
    public static final String imqQueueBrowserRetrieveTimeout = "imqQueueBrowserRetrieveTimeout";

    /**
     * This property holds the the maximum number of messages that will be retrieved at one time when a OpenMQ Client
     * Application is enumerating through the messages on a Queue using a QueueBrowser.
     * <p>
     * The default value for this property is <code><b>1000</b></code>
     * <p>
     *
     * @see java.util.Enumeration
     * @see java.util.Enumeration#nextElement()
     * @see jakarta.jms.QueueBrowser jakarta.jms.QueueBrowser
     */
    public static final String imqQueueBrowserMaxMessagesPerRetrieve = "imqQueueBrowserMaxMessagesPerRetrieve";

    /**
     * This property indicates how the OpenMQ ConnectionConsumer should load messages into a ServerSession's JMS Session.
     * <p>
     * When set to <code>true</code>, OpenMQ will load as many messages as are available, upto the limit of the
     * <code>maxMessages</code> parameter used when creating the ConnectionConsumer, using the
     * <code>createConnectionConsumer()</code> API, into a ServerSession's JMS Session for processing.
     * <p>
     * When set to <code>false</code>, OpenMQ will only load a single message into a ServerSession's JMS Session for
     * processing.
     * <p>
     * The default value of this property is <code><b>true</b></code>
     * <p>
     *
     * @see jakarta.jms.ConnectionConsumer jakarta.jms.ConnectionConsumer
     * @see jakarta.jms.QueueConnection#createConnectionConsumer(jakarta.jms.Queue, java.lang.String,
     * jakarta.jms.ServerSessionPool, int) QueueConnection.createConnectionConsumer(Queue, messageSelector, ServerSessionPool,
     * maxMessages)
     * @see jakarta.jms.TopicConnection#createConnectionConsumer(jakarta.jms.Topic, java.lang.String,
     * jakarta.jms.ServerSessionPool, int) TopicConnection.createConnectionConsumer(Topic, messageSelector, ServerSessionPool,
     * maxMessages)
     * @see jakarta.jms.TopicConnection#createDurableConnectionConsumer(jakarta.jms.Topic, java.lang.String, java.lang.String,
     * jakarta.jms.ServerSessionPool, int) TopicConnection.createDurableConnectionConsumer(Topic, subscriptionName,
     * messageSelector, ServerSessionPool, maxMessages)
     */
    public static final String imqLoadMaxToServerSession = "imqLoadMaxToServerSession";

    /**
     * This property indicates whether OpenMQ should override the JMS Message Header <b><code>JMSDeliveryMode</code></b>
     * which can be set using the JMS APIs.
     * <p>
     * When this property is set to <code>true</code> OpenMQ will set the Message Header <b><code>JMSDeliveryMode</code></b>
     * to the value of the property <b><code>imqJMSDeliveryMode</code></b> for all messages produced by the Connection
     * created using this Administered Object. If <b><code>imqJMSDeliveryMode</code></b> has an invalid value, then the
     * default value of the JMS Message Header <b><code>JMSDeliveryMode</code></b> will be used instead. value.
     * <p>
     * The default value of this property is <code><b>false</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqJMSDeliveryMode
     * @see jakarta.jms.MessageProducer#setDeliveryMode(int) MessageProducer.setDeliveryMode(deliveryMode)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Message, int, int, long) QueueSender.send(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Queue, jakarta.jms.Message, int, int, long) QueueSender.send(Queue, Message, deliveryMode,
     * priority, timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Message, int, int, long) QueueSender.publish(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Topic, jakarta.jms.Message, int, int, long) QueueSender.publish(Topic, Message,
     * deliveryMode, priority, timeToLive)
     */
    public static final String imqOverrideJMSDeliveryMode = "imqOverrideJMSDeliveryMode";

    /**
     * This property holds the administratively configured value of the JMS Message Header
     * <b><code>JMSDeliveryMode</code></b>.
     * <p>
     * When <b><code>imqOverrideJMSDeliveryMode</code></b> is set to <code>true</code>, all messages produced to
     * <b><i>non-Temporary Destinations</i></b> by the Connection created using this Administered Object will have their JMS
     * Message Header <b><code>JMSDeliveryMode</code></b> set to the value of this property.
     * <p>
     * In addition, when <b><code>imqOverrideJMSHeadersToTemporaryDestinations</code></b> is set to <code>true</code>, all
     * messages produced to <b><i>Temporary Destinations</i></b> will also contain the overridenvalue of this JMS Message
     * Header.
     * <p>
     * Setting an invalid value on this property will result in the Message Header being set to the default value of
     * <b><code>JMSDeliveryMode</code></b> as defined by the JMS Specification.
     * <p>
     * The default value of this property is <code><b><i>null</i></b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqOverrideJMSDeliveryMode
     * @see com.sun.messaging.ConnectionConfiguration#JMSDeliveryMode_PERSISTENT
     * @see com.sun.messaging.ConnectionConfiguration#JMSDeliveryMode_NON_PERSISTENT
     * @see jakarta.jms.Message#DEFAULT_DELIVERY_MODE
     * @see jakarta.jms.DeliveryMode#PERSISTENT
     * @see jakarta.jms.DeliveryMode#NON_PERSISTENT
     * @see jakarta.jms.MessageProducer#setDeliveryMode(int) MessageProducer.setDeliveryMode(deliveryMode)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Message, int, int, long) QueueSender.send(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Queue, jakarta.jms.Message, int, int, long) QueueSender.send(Queue, Message, deliveryMode,
     * priority, timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Message, int, int, long) QueueSender.publish(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Topic, jakarta.jms.Message, int, int, long) QueueSender.publish(Topic, Message,
     * deliveryMode, priority, timeToLive)
     */
    public static final String imqJMSDeliveryMode = "imqJMSDeliveryMode";

    /**
     * This constant is used to set the value of <code>imqJMSDeliveryMode</code> to <b>PERSISTENT</b> when the
     * <b>JMSDeliveryMode</b> JMS Message Header is administratively configured.
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqOverrideJMSDeliveryMode
     * @see com.sun.messaging.ConnectionConfiguration#imqJMSDeliveryMode
     */
    public static final String JMSDeliveryMode_PERSISTENT = "PERSISTENT";

    /**
     * This constant is used to set the value of <code>imqJMSDeliveryMode</code> to <b>NON_PERSISTENT</b> when the
     * <b>JMSDeliveryMode</b> JMS Message Header is administratively configured.
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqOverrideJMSDeliveryMode
     * @see com.sun.messaging.ConnectionConfiguration#imqJMSDeliveryMode
     */
    public static final String JMSDeliveryMode_NON_PERSISTENT = "NON_PERSISTENT";

    /**
     * This property indicates whether OpenMQ should override the JMS Message Header <b><code>JMSExpiration</code></b> which
     * can be set using the JMS APIs.
     * <p>
     * When this property is set to <code>true</code> OpenMQ will set the Message Header <b><code>JMSExpiration</code></b>
     * to the value of the property <b><code>imqJMSExpiration</code></b> for all messages produced by the Connection created
     * using this Administered Object. If <b><code>imqJMSExpiration</code></b> has an invalid value, then the default value
     * of the JMS Message Header <b><code>JMSExpiration</code></b> will be used instead. value.
     * <p>
     * The default value of this property is <code><b>false</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqJMSExpiration
     * @see jakarta.jms.MessageProducer#setTimeToLive(long) MessageProducer.setTimeToLive(timeToLive)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Message, int, int, long) QueueSender.send(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Queue, jakarta.jms.Message, int, int, long) QueueSender.send(Queue, Message, deliveryMode,
     * priority, timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Message, int, int, long) QueueSender.publish(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Topic, jakarta.jms.Message, int, int, long) QueueSender.publish(Topic, Message,
     * deliveryMode, priority, timeToLive)
     */
    public static final String imqOverrideJMSExpiration = "imqOverrideJMSExpiration";

    /**
     * This property holds the administratively configured value of the JMS Message Header
     * <b><code>JMSExpiration</code></b>.
     * <p>
     * When <b><code>imqOverrideJMSExpiration</code></b> is set to <code>true</code>, all messages produced to
     * <b><i>non-Temporary Destinations</i></b> by the Connection created using this Administered Object will have their JMS
     * Message Header <b><code>JMSExpiration</code></b> set to the value of this property.
     * <p>
     * In addition, when <b><code>imqOverrideJMSHeadersToTemporaryDestinations</code></b> is set to <code>true</code>, all
     * messages produced to <b><i>Temporary Destinations</i></b> will also contain the overridenvalue of this JMS Message
     * Header.
     * <p>
     * Setting an invalid value on this property will result in the Message Header being set to the default value of
     * <b><code>JMSExpiration</code></b> as defined by the JMS Specification.
     * <p>
     * The JMS Specification defines <b><code>JMSExpiration</code></b> as the time in milliseconds from its dispatch time
     * that a produced message should be retained by the message service. An value of <code><b>0</b></code> indicates that
     * the message is retained indefinitely. The default value of <b><code>JMSExpiration</code></b> is
     * <code><b>0</b></code>.
     * <p>
     * The default value of this property is <code><b><i>null</i></b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqOverrideJMSExpiration
     * @see jakarta.jms.Message#DEFAULT_TIME_TO_LIVE
     * @see jakarta.jms.MessageProducer#setTimeToLive(long) MessageProducer.setTimeToLive(timeToLive)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Message, int, int, long) QueueSender.send(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Queue, jakarta.jms.Message, int, int, long) QueueSender.send(Queue, Message, deliveryMode,
     * priority, timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Message, int, int, long) QueueSender.publish(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Topic, jakarta.jms.Message, int, int, long) QueueSender.publish(Topic, Message,
     * deliveryMode, priority, timeToLive)
     */
    public static final String imqJMSExpiration = "imqJMSExpiration";

    /**
     * This property indicates whether OpenMQ should override the JMS Message Header <b><code>JMSPriority</code></b> which
     * can be set using the JMS APIs.
     * <p>
     * When this property is set to <code>true</code> OpenMQ will set the Message Header <b><code>JMSPriority</code></b> to
     * the value of the property <b><code>imqJMSPriority</code></b> for all messages produced by all messages produced to
     * <b><i>non-Temporary Destinations</i></b> by the Connection created using this Administered Object. If
     * <b><code>imqJMSPriority</code></b> has an invalid value, then the default value of the JMS Message Header
     * <b><code>JMSPriority</code></b> will be used instead. value.
     * <p>
     * The default value of this property is <code><b>false</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqJMSPriority
     * @see jakarta.jms.MessageProducer#setPriority(int) MessageProducer.setPriority(priority)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Message, int, int, long) QueueSender.send(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Queue, jakarta.jms.Message, int, int, long) QueueSender.send(Queue, Message, deliveryMode,
     * priority, timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Message, int, int, long) QueueSender.publish(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Topic, jakarta.jms.Message, int, int, long) QueueSender.publish(Topic, Message,
     * deliveryMode, priority, timeToLive)
     * @see jakarta.jms.DeliveryMode#PERSISTENT
     * @see jakarta.jms.DeliveryMode#NON_PERSISTENT
     */
    public static final String imqOverrideJMSPriority = "imqOverrideJMSPriority";

    /**
     * This property holds the administratively configured value of the JMS Message Header <b><code>JMSPriority</code></b>.
     * <p>
     * When <b><code>imqOverrideJMSPriority</code></b> is set to <code>true</code>, all messages produced to
     * <b><i>non-Temporary Destinations</i></b> by the Connection created using this Administered Object will have their JMS
     * Message Header <b><code>JMSPriority</code></b> set to the value of this property.
     * <p>
     * In addition, when <b><code>imqOverrideJMSHeadersToTemporaryDestinations</code></b> is set to <code>true</code>, all
     * messages produced to <b><i>Temporary Destinations</i></b> will also contain the overridenvalue of this JMS Message
     * Header.
     * <p>
     * Setting an invalid value on this property will result in the Message Header being set to the default value of
     * <b><code>JMSPriority</code></b> as defined by the JMS Specification.
     * <p>
     * The JMS Specification defines a 10 level value for <b><code>JMSPriority</code></b>, the priority with which produced
     * messages are delivered by the message service, with 0 as the lowest and 9 as the highest. Clients should consider 0-4
     * as gradients of normal priority and 5-9 as gradients of expedited priority. <b><code>JMSPriority</code></b> is set to
     * 4, by default.
     * <p>
     * The default value of this property is <code><b><i>null</i></b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqOverrideJMSPriority
     * @see jakarta.jms.Message#DEFAULT_PRIORITY
     * @see jakarta.jms.MessageProducer#setPriority(int) MessageProducer.setPriority(priority)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Message, int, int, long) QueueSender.send(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Queue, jakarta.jms.Message, int, int, long) QueueSender.send(Queue, Message, deliveryMode,
     * priority, timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Message, int, int, long) QueueSender.publish(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Topic, jakarta.jms.Message, int, int, long) QueueSender.publish(Topic, Message,
     * deliveryMode, priority, timeToLive)
     */
    public static final String imqJMSPriority = "imqJMSPriority";

    /**
     * This property indicates whether OpenMQ should override the JMS Message Headers on Messages that are sent to Temporary
     * Destinations.
     * <p>
     * When <b><code>imqOverrideJMSHeadersToTemporaryDestinations</code></b> is set to <code>true</code>, imq will override
     * the JMS Message Headers listed below on Messages sent to Temporary Destinations providing the corresponding property
     * enabling the JMS Message Header override on Messages sent to non-temporary Destinations is also set to
     * <code>true</code>.
     * <p>
     * <b><code>JMSPriority</code></b>
     * <p>
     * <b><code>JMSExpiration</code></b>
     * <p>
     * <b><code>JMSDeliveryMode</code></b>
     * <p>
     * The default value of this property is <code><b>false</b></code>
     * <p>
     *
     * @see com.sun.messaging.ConnectionConfiguration#imqOverrideJMSDeliveryMode
     * @see com.sun.messaging.ConnectionConfiguration#imqJMSDeliveryMode
     * @see com.sun.messaging.ConnectionConfiguration#imqOverrideJMSExpiration
     * @see com.sun.messaging.ConnectionConfiguration#imqJMSExpiration
     * @see com.sun.messaging.ConnectionConfiguration#imqOverrideJMSPriority
     * @see com.sun.messaging.ConnectionConfiguration#imqJMSPriority
     * @see jakarta.jms.MessageProducer#setDeliveryMode(int) MessageProducer.setDeliveryMode(deliveryMode)
     * @see jakarta.jms.MessageProducer#setTimeToLive(long) MessageProducer.setTimeToLive(timeToLive)
     * @see jakarta.jms.MessageProducer#setPriority(int) MessageProducer.setPriority(priority)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Message, int, int, long) QueueSender.send(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.QueueSender#send(jakarta.jms.Queue, jakarta.jms.Message, int, int, long) QueueSender.send(Queue, Message, deliveryMode,
     * priority, timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Message, int, int, long) QueueSender.publish(Message, deliveryMode, priority,
     * timeToLive)
     * @see jakarta.jms.TopicPublisher#publish(jakarta.jms.Topic, jakarta.jms.Message, int, int, long) QueueSender.publish(Topic, Message,
     * deliveryMode, priority, timeToLive)
     * @see jakarta.jms.DeliveryMode#PERSISTENT
     * @see jakarta.jms.DeliveryMode#NON_PERSISTENT
     */
    public static final String imqOverrideJMSHeadersToTemporaryDestinations = "imqOverrideJMSHeadersToTemporaryDestinations";

}
