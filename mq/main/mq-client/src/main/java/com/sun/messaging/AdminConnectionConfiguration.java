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
 * @(#)AdminConnectionConfiguration.java	1.4 06/28/07
 */ 

package com.sun.messaging;

/**
 * The <code>AdminConnectionConfiguration</code> class contains property names 
 * and special values for configuring the <CODE>AdminConnectionFactory</CODE> object.
 * <P>
 * Other property names and information related to security is TBD.
 *
 * @see         com.sun.messaging.AdminConnectionFactory com.sun.messaging.AdminConnectionFactory
 */
public class AdminConnectionConfiguration {

    /* No public constructor needed */
    private AdminConnectionConfiguration(){}

    /**
     * This property holds the address that will be used by management clients to
     * connect to the MQ Message Service.
     * <p>
     * <b>Message Server Address Syntax</b>
     * <p>The syntax for specifying a message server address is as follows:<BR>
     *  </p>  
     * <p><code><i>scheme</i>://<i>address_syntax</i></code><br>
     *   </p> 
     *  
     * <P>
     * This syntax is similar to the one used by JMS clients to configure JMS
     * ConnectionFactory objects. However, the address syntax includes an MQ broker
     * JMX connector name (instead of a connection service name).
     *
     * <p><code><i>scheme</i></code> and <code><i>address_syntax</i></code> are described in the folowing 
     *   table.</p>
     * <TABLE columns="4" border="1">
     * 	<TR>
     * 		<TH>Scheme</TH>
     * 		<TH>JMX Connector Name</TH>
     * 		<TH>Description</TH>
     * 		<TH>Syntax</TH>
     * 	</TR>
     * 
     * 	<TR>
     * 		<TD valign="top"><code>mq</code></TD>
     * 		
     *       <TD valign="top">
     *       <code>jmxrmi<br>
     *         and <br>
     *         jmxsslrmi</code>
     *       </TD>
     * 		
     * 	<TD valign="top">The MQ Port Mapper at the specified host and port 
     *      will handle the connection request, and determine the JMXServiceURL
     *      for the connector that is specified. Once this is known, MQ makes the 
     *	    connection.</TD>
     * 		
     *     <TD valign="top"><code>[<i>hostName</i>][:<i>port</i>]/<i>connectorName</i></code> <br>
     *       Defaults: <br>
     *       <code><i>hostName</i> = localhost <br>
     *       <i>port</i> = 7676</code><br>
     *       A connector name must be specified.
     *     </TD>
     * 	</TR>
     * 
     * </TABLE>
     * <p>&nbsp;</p>
     * <p>The following table shows how the message server address syntax applies in 
     *   some typical cases.</p>
     * <TABLE columns="4" border="1">
     * 	<TR>
     * 		<TH>Connector Name</TH>
     * 		<TH>Broker Host</TH>
     * 		<TH>Port</TH>
     * 		<TH>Example Address</TH>
     * 	</TR>
     * 
     * 	<TR>
     * 		
     *     <TD valign="top">jmxrmi</TD>
     * 		
     *     <TD valign="top">Unspecified</TD>
     * 	    
     *     <TD valign="top">Unspecified</TD>
     * 		
     *     <TD valign="top"><code>mq:///jmxrmi<br>
     *       (mq://localhost:7676/jmxrmi)</code></TD>	
     * 	</TR>
     * 	
     * 	<TR>
     * 		
     *     <TD valign="top">jmxrmi</TD>
     * 		
     *     <TD valign="top">Specified Host</TD>
     * 	    
     *     <TD valign="top">Unspecified</TD>
     * 		
     *     <TD valign="top"><code>mq://myBkrHost/jmxrmi<br>
     *       (mq://myBkrHost:7676/jmxrmi)</code></TD>	
     * 	</TR>
     * 	
     * 	<TR>
     * 		
     *     <TD valign="top">jmxrmi</TD>
     * 		
     *     <TD valign="top">Unspecified</TD>
     * 	    
     *     <TD valign="top">Specified Portmapper Port</TD>
     * 		
     *     <TD valign="top"><code>mq://:1012/jmxrmi<br>
     *       (mq://localHost:1012/jmxrmi)</code></TD>	
     * 	</TR>
     * 	
     * 	<TR>
     * 		
     *     <TD valign="top"><code>jmxsslrmi</code></TD>
     * 		
     *     <TD valign="top">Local Host</TD>
     * 	    
     *     <TD valign="top">Default Portmapper Port</TD>
     * 		
     *     <TD valign="top"><code>mq://localHost:7676/jmxsslrmi</code></TD>	
     * 	</TR>
     * 	
     * 	<TR>
     * 	 <TD valign="top"><code>jmxsslrmi</code></TD>
     * 		
     *     <TD valign="top">Specified Host</TD>
     * 	    
     *     <TD valign="top">Default Portmapper Port</TD>
     * 		
     *     <TD valign="top"><code>mq://myBkrHost:7676/jmxsslrmi</code></TD>	
     * 	</TR>
     * 	<TR>
     * 	 <TD valign="top"><code>jmxsslrmi</code></TD>
     * 		
     *     <TD valign="top">Specified Host</TD>
     * 	    
     *     <TD valign="top">Specified Portmapper Port</TD>
     * 		
     *     <TD valign="top"><code>mq://myBkrHost:1012/jmxsslrmi</code></TD>	
     * 	</TR>
     * 	
     * </TABLE>
     * <P>&nbsp;</P>
     * <p>
     * The default value of this property is <code><b>mq://localhost:7676/jmxrmi</b></code>
     * <p>
     */
    public static final String imqAddress = "imqAddress";

    /**
     * This property holds the default administrator username that will be used
     * to authenticate with the MQ Administration Service.
     * <p>
     * The default value of this property is <code><b>admin</b></code>
     */
    public static final String imqDefaultAdminUsername = "imqDefaultAdminUsername";

    /**
     * This property holds the default administrator password that will be used
     * to authenticate with the MQ Administration Service.
     * <p>
     * The default value of this property is <code><b>admin</b></code>
     */
    public static final String imqDefaultAdminPassword = "imqDefaultAdminPassword";

}
