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

import java.io.PrintWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.JMSException;
import javax.resource.ResourceException;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.security.PasswordCredential;
import javax.security.auth.Subject;

import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jms.ra.util.CustomTokenizer;

/**
 * Implements the ManagedConnectionFactory interface of the
 * J2EE Connector Architecture.
 * An Application Server creates and configures instances of this
 * class for use in creating ManagedConnection and ConnectionFactory
 * instances.
 */

public class ManagedConnectionFactory
implements javax.resource.spi.ManagedConnectionFactory,
           javax.resource.spi.ResourceAdapterAssociation,
           java.io.Serializable,
           GenericConnectionFactoryProperties
{
    // Serializable instance data (includes configurable attributes) //
    /** The ResourceAdapter instance associated with this ManagedConnectionFactory */
    private com.sun.messaging.jms.ra.ResourceAdapter ra = null;

    /** The XAConnectionFactory for this ManagedConnectionFactory */
    private com.sun.messaging.XAConnectionFactory xacf = null;

    // Configurable Attributes of the ManagedConnectionFactory //
    /** The AddressList  default is "localhost" */
    private String addressList = null;

    /** The UserName to be used for the Connection - default is "guest" */
    private String userName = null;

    /** The Password to be used for the Connection  - default is "guest" */
    private String password = null;

    /** The ClientId to be used for the Connection */
    private String clientId = null;
    
    private boolean useSharedSubscriptionsInClusteredContainer = true;

    /** Whether to enable Direct mode for Connections fromthis MCF */
    private boolean enableRADirect = false;
    private boolean enableAPIDirect = false;

    /* Failover controls */
    private boolean reconnectEnabled = false;
    private boolean reconnectEnabledSet = false;
    private int reconnectInterval = -1;
    private int reconnectAttempts = -1;
    private String addressListBehavior = null;
    private int addressListIterations = -1;
    
    private String options=null;
    
    //HA related configuration //
    //private boolean haRequired = false;
    //private boolean haRequiredSet = false;

    // Transient instance data //
    
    /** The PrintWriter set on this ManagedConnectionFactory */
    private transient PrintWriter logWriter = null;

    /** The identifier (unique in a VM) for this ManagedConnectionFactory */
    private transient int mcfId = 0;

    /** The uniquifier */
    private static int idCounter = 0;

    private ConnectionCreator connectionCreator = null;

    /* Loggers */
    private static final String _className = "com.sun.messaging.jms.ra.ManagedConnectionFactory";
    protected static final String _lgrNameOutboundConnection = "javax.resourceadapter.mqjmsra.outbound.connection";
    protected static final Logger _loggerOC = Logger.getLogger(_lgrNameOutboundConnection);
    protected static final String _lgrMIDPrefix = "MQJMSRA_MF";
    protected static final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";
    
    private static synchronized int incrementIdCounter(){
    	return idCounter++;
    }

    /** Public Constructor */
    public ManagedConnectionFactory()
    {
        _loggerOC.entering(_className, "constructor()");
        ra = null;
        logWriter = null;
        xacf = new com.sun.messaging.XAConnectionFactory();

        //Each instance gets its own Id
        mcfId = incrementIdCounter();

        _loggerOC.exiting(_className, "constructor()", ":Id="+mcfId+":config="+toString());
    }


    // ManagedConnectionFactory interface methods //
    // 

    /** Creates a ConnectionFactory instance using a
     *  ConnectionManager (managed case)
     *
     *  @return A JMS ConnectionFactory instance
     */
    public Object
    createConnectionFactory(javax.resource.spi.ConnectionManager cm)
    {
        _loggerOC.entering(_className, "createConnectionFactory()", cm);
        //XXX:tharakan:need RA to be direct either system property set or MCF configured
        if (this.ra._isRADirectAllowed() && this.getEnableRADirect()) {
            this.connectionCreator = new DirectConnectionFactory(this, cm);
        } else {
        	//Nigel: not sure why we're checking getEnableRADirect below
        	//Nigel: what sort of CF does embedded mode require?
            if (this.ra._isRADirect() && this.getEnableRADirect()) {
                this.connectionCreator = new DirectConnectionFactory(this, cm);
            } else {
                this.connectionCreator = new ConnectionFactoryAdapter(this, cm);
            }
        }
        return this.connectionCreator;
    }

    /** Creates a ConnectionFactory instance using a null
     *  ConnectionManager (non-managed case)
     *
     *  @return A JMS ConnectionFactory instance
     */
    public Object
    createConnectionFactory()
    {
        _loggerOC.entering(_className, "createConnectionFactory()");
        return (this.createConnectionFactory(
                (javax.resource.spi.ConnectionManager)
                com.sun.messaging.jms.ra.ResourceAdapter._getConnectionManager()));
    }

    /** Creates a ManagedConnection instance using a
     *  javax.security.auth.Subject and javax.resource.spi.ConnectionRequestInfo
     *
     *  @param subject The javax.security.auth.Subject that is to be
     *         used for credentials
     *  @param cxRequestInfo The ConnectionRequestInfo that is to be used
     *         for connection matching
     *
     *  @return A ManagedConnection instance
     */
    public javax.resource.spi.ManagedConnection
    createManagedConnection(Subject subject, javax.resource.spi.ConnectionRequestInfo cxRequestInfo)
    throws ResourceException
    {
        Object params[] = new Object[2];
        params[0] = subject;
        params[1] = cxRequestInfo;

        _loggerOC.entering(_className, "createManagedConnection()", params);
        //Handle recovery case when createConnectionFactory has not been called.
        //XXX:refactor to setResourceAdapter() later
        if (this.connectionCreator == null){
            this.createConnectionFactory(this.ra._getConnectionManager());
        }
        ManagedConnection mc = 
                new com.sun.messaging.jms.ra.ManagedConnection(this, subject,
                        (com.sun.messaging.jms.ra.ConnectionRequestInfo)cxRequestInfo, ra);
//        if (this.enableDirect) {
//            mc._setDirect(true);
//        }
        return mc;
    }

    /** Returns a ManagedConnection from the ManagedConnection set
     *  passed in that matches the Subject and ConnectionRequestInfo
     *  criteria.
     *
     *  @param connectionSet The Set of ManagedConnection instances to search
     *
     *  @param subject The javax.security.auth.Subject that is to be
     *         used for credentials
     *  @param cxRequestInfo The ConnectionRequestInfo that is to be used
     *         for connection matching
     *
     *  @return A ManagedConnection that matches the criteria
     */
    public javax.resource.spi.ManagedConnection
    matchManagedConnections(java.util.Set connectionSet,
                            javax.security.auth.Subject subject,
                            javax.resource.spi.ConnectionRequestInfo cxRequestInfo)
    throws ResourceException
    {
        Object params[] = new Object[3];
        params[0] = connectionSet;
        params[1] = subject;
        params[2] = cxRequestInfo;

        _loggerOC.entering(_className, "matchManagedConnections()", params);
        //System.out.println("MQRA:MCF:matchMC:Subject="+((subject != null) ? subject.toString() : "null"));
        //System.out.println("MQRA:MCF:matchMC:cxReqInfo="+((cxRequestInfo != null) ? cxRequestInfo.toString() : "null"));
        //System.out.println("MQRA:MCF:matchMC:thisMCF="+this.toString());
        

        PasswordCredential pc = Util.getPasswordCredential(this, subject,
                (com.sun.messaging.jms.ra.ConnectionRequestInfo)cxRequestInfo);

        Iterator it = connectionSet.iterator();
 
        while (it.hasNext()) {
            Object obj = it.next();
            if (obj instanceof com.sun.messaging.jms.ra.ManagedConnection) {
                com.sun.messaging.jms.ra.ManagedConnection mc =
                    (com.sun.messaging.jms.ra.ManagedConnection) obj;
                com.sun.messaging.jms.ra.ManagedConnectionFactory mcf =
                    mc.getManagedConnectionFactory();
                if (!mc.isDestroyed()) {
                    //System.out.println("MQRA:MCF:matchMC:comparing cred && MCFs");
                    if (Util.isPasswordCredentialEqual(mc.getPasswordCredential(), pc)) {
                        //System.out.println("MQRA:MCF:matchMC:creds are equal");
                        if (mcf.equals(this)) {
                            //System.out.println("MQRA:MCF:matchMC:MCFs are equal");
                            //System.out.println("MQRA:MCF:matchMC:got match:Id="+mc.getMCId());
                            return (javax.resource.spi.ManagedConnection)mc;
                        }
                    }
                } else {
                    //XXX: Expose this after AS checks for destroyed MCs before calling matchMC
                    //System.err.println("MQRA:MCF:matchMC:mcId="+mc.getMCId()+":isDestroyed");
                }
            }
        }
        //System.out.println("MQRA:MCF:matchMC:no match-return null");
        return null;
    }

    /** Sets the PrintWriter to be used by the ResourceAdapter for logging
     *
     *  @param out The PrintWriter to be used
     */
    public void
    setLogWriter(PrintWriter out)
    throws ResourceException
    {
        _loggerOC.entering(_className, "setLogWriter()", out);
        logWriter = out;
    }
 
    /** Returns the PrintWriter being used by the ResourceAdapter for logging
     *
     *  @return The PrintWriter being used
     */
    public PrintWriter
    getLogWriter()
    throws ResourceException
    {
        _loggerOC.entering(_className, "getLogWriter()", logWriter);
        return logWriter;
    }

    /** Returns the hash code for this ManagedConnectionFactory instance
     *
     * @return The hash code
     */
    public int
    hashCode()
    {
        //The rule here is that if two objects have the same Id
        //i.e. they are equal and the .equals method returns true
        //     then the .hashCode method *must* return the same
        //     hash code for those two objects
        //So, we can simply use the mcfId.
        //return (new Integer(mcfId)).hashCode();
        if (mcfId == 0) {
            mcfId = incrementIdCounter();
        }
        return mcfId;
    }

    /** Compares this ManagedConnectionFactory instance to one
     *  passed in for equality.
     *
     *  @return true If the two instances are equal, otherwise
     *          return false.
     */
    public boolean
    equals(java.lang.Object other)
    {
        _loggerOC.entering(_className, "equals()", other);
        if (other == null) {
            return false;
        }
        if (other instanceof com.sun.messaging.jms.ra.ManagedConnectionFactory) {
            com.sun.messaging.jms.ra.ManagedConnectionFactory otherMCF =
            (com.sun.messaging.jms.ra.ManagedConnectionFactory)other;

            if (_loggerOC.isLoggable(Level.FINER)) {
                _loggerOC.finer(_lgrMID_INF+"equals:thisMCF="+this.toString()+"\n\t\totherMCF="+otherMCF.toString());
            }
            String oMSA = otherMCF.getAddressList();
            String oUserName = otherMCF.getUserName();
            String oPassword = otherMCF.getPassword();
            String oClientId = otherMCF.getClientId();
            boolean oEnableRADirect = otherMCF.getEnableRADirect();
            boolean oEnableAPIDirect = otherMCF.getEnableAPIDirect();

            if (
                ((oMSA != null && oMSA.equals(addressList)) ||
                 (oMSA == null && addressList == null))
               &&
                ((oUserName != null && oUserName.equals(userName)) ||
                 (oUserName == null && userName == null))
               &&
                ((oPassword != null && oPassword.equals(password)) ||
                 (oPassword == null && password == null))
               &&
                ((oClientId != null && oClientId.equals(clientId)) ||
                 (oClientId == null && clientId == null))
               &&
                (oEnableRADirect == this.enableRADirect)
               &&
                (oEnableAPIDirect == this.enableAPIDirect)                
               ) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    // ResourceAdapterAssociation interface methods //
    // 

    /** Sets the Resource Adapter Javabean that is associated with this
     *  ManagedConnectionFactory instance.
     *
     *  @param ra The ResourceAdapter Javabean
     */
    public void
    setResourceAdapter(javax.resource.spi.ResourceAdapter ra)
    throws ResourceException
    {
        _loggerOC.entering(_className, "setResourceAdapter()", ra);
        //System.out.println("MQRA:MCF:setResourceAdapter()-mcfId="+mcfId+":RA-config="+
                    //((ra != null) ? ra.toString() : "null"));
        synchronized (this) {
            if (this.ra == null) {
                if (ra instanceof com.sun.messaging.jms.ra.ResourceAdapter) {
                    com.sun.messaging.jms.ra.ResourceAdapter mqra =
                            (com.sun.messaging.jms.ra.ResourceAdapter)ra;
                    this.ra = mqra;
                    if (!mqra.getInAppClientContainer()) {
                        AccessController.doPrivileged(
                            new PrivilegedAction<Object>()
                            {
                                public Object run() {
                                    System.setProperty("imq.DaemonThreads", "true");
                                    return null;
                                }
                            }
                        );
                        //System.setProperty("imq.DaemonThreads", "true");
                    }
                    if (this.ra._isRADirectAllowed()){
                        this.setEnableRADirect(true);
                    }
                    if (this.ra._isAPIDirectAllowed()){
                        this.setEnableAPIDirect(true);
                    }                    
                    //Update MCF with addressList default from the RA. if it was not set directly on this MCF
                    if (addressList == null) {
                        try {
                            _setAddressList(mqra._getEffectiveConnectionURL());
                        } catch (IllegalArgumentException iae) {
                            //Leave addressList null - default will be used
                            _loggerOC.info(_lgrMID_INF+"setResourceAdapter:Using default addressList due to setAddressList Exception="+iae.getMessage());
                        }
                    }
                    //Update MCF with other defaults from the RA. if they were not set directly on this MCF
                    if (userName == null) {
                        try {
                            _setUserName(mqra.getUserName());
                        } catch (IllegalArgumentException iae) {
                            _loggerOC.info(_lgrMID_INF+"setResourceAdapter:Using default userName due to setUserName Exception="+iae.getMessage());
                        }
                    }
                    if (password == null) {
                        try {
                            _setPassword(mqra.getPassword());
                        } catch (IllegalArgumentException iae) {
                            _loggerOC.info(_lgrMID_INF+"setResourceAdapter:Using default password due to setPassword Exception="+iae.getMessage());
                        }
                    }
                    if (!reconnectEnabledSet) {
                        try {
                            setReconnectEnabled(mqra.getReconnectEnabled());
                        } catch (IllegalArgumentException iae) {
                            //Leave alone - default will be used
                        }
                    }
                    if (reconnectInterval == -1) {
                        try {
                            setReconnectInterval(mqra.getReconnectInterval());
                        } catch (IllegalArgumentException iae) {
                            //Leave alone - default will be used
                        }
                    }
                    if (reconnectAttempts == -1) {
                        try {
                            setReconnectAttempts(mqra.getReconnectAttempts());
                        } catch (IllegalArgumentException iae) {
                            //Leave alone - default will be used
                        }
                    }
                    if (addressListIterations == -1) {
                        try {
                            setAddressListIterations(mqra.getAddressListIterations());
                        } catch (IllegalArgumentException iae) {
                            //Leave alone - default will be used
                        }
                    }
                    if (addressListBehavior == null) {
                        try {
                            setAddressListBehavior(mqra.getAddressListBehavior());
                        } catch (IllegalArgumentException iae) {
                            //Leave alone - default will be used
                        }
                    }
                    //Future release
                    //if (!haRequiredSet) {
                    //    try {
                    //        setHARequired(mqra.getHARequired());
                    //    } catch (IllegalArgumentException iae) {
                    //        //Leave alone - default will be used
                    //    }
                    //}
                } else {
                    throw new ResourceException("MQRA:MCF:associating unkown resource adapter class - "+
                        ra.getClass());
                }
            } else {
                throw new ResourceException("MQRA:MCF:illegal to change resource adapter association");
            }
        }
    }
 
    /** Gets the Resource Adapter Javabean that is associated with this
     *  ManagedConnectionFactory instance.
     *
     *  @return The ResourceAdapter Javabean
     */
    public javax.resource.spi.ResourceAdapter
    getResourceAdapter()
    {
        _loggerOC.entering(_className, "getResourceAdapter()", ra);
        return (javax.resource.spi.ResourceAdapter)ra;
    }


    // Public Methods //
    //

    /** Return the ManagedConnectionFactory ID for this instance.
     *  This Id is unique for a single VM
     *
     *  @return The ManagedConnectionFactory ID
     */
    public int
    _getMCFId()
    {
        if (mcfId == 0) {
            mcfId = incrementIdCounter();
        }
        return mcfId;
    }


    // ManagedConnectionFactory Javabean configuration Methods //
    // These Methods can throw java.lang.RuntimeException or subclasses //

    /** Sets the Message Service Address List for this ManagedConnectionFactory instance */
    public void
    _setMessageServiceAddressList(String messageServiceAddressList)
    {
        _loggerOC.entering(_className, "_setMessageServiceAddressList()", messageServiceAddressList);
        setAddressList(messageServiceAddressList);
    }

    /** Gets the Message Service Address List for this ManagedConnectionFactory instance */
    public String
    _getMessageServiceAddressList()
    {
        _loggerOC.entering(_className, "_getMessageServiceAddressList()", addressList);
        return addressList;
    }

    /** Sets the Message Service Address List for this ManagedConnectionFactory instance */
    public void
    setAddressList(String addressList)
    {
        //XXX: work around this Logger API stripping the String after the 1st 'space'
        String tAddressList = addressList;
        _loggerOC.entering(_className, "setAddressList()", tAddressList);
        //Ignore set of a default value that is in ra.xml if called *before* setResourceAdapter
        if ("localhost".equals(addressList) && (ra == null)) {
            _loggerOC.fine(_lgrMID_INF+"setAddressList:NOT setting default value="+addressList);
            return;
        }
        _setAddressList(addressList);
        if ((addressList != null) && !"".equals(addressList)){
            this.setEnableRADirect(false);
            this.setEnableAPIDirect(false);
        }
    }

    //This private method is called from setAddressList and setResourceAdapter
    //
    private void
    _setAddressList(String addressList)
    {

        try {
            xacf.setProperty(ConnectionConfiguration.imqAddressList, addressList);
            this.addressList = addressList;
        } catch (JMSException jmse) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setAddressList:setProperty Exception for value="+addressList);
            iae.initCause(jmse); 
            _loggerOC.warning(iae.getMessage());
            _loggerOC.throwing(_className, "setAddressList()", iae);
            throw iae;
        }
    }

    /** Gets the Message Service Address List for this ManagedConnectionFactory instance */
    public String
    getAddressList()
    {
        _loggerOC.entering(_className, "getMessageServiceAddressList()", addressList);
        return addressList;
    }


    /** Sets the UserName for this ManagedConnectionFactory instance
     *   
     *  @param userName The UserName
     */  
    public void
    setUserName(String userName)
    {
        _loggerOC.entering(_className, "setUserName()", userName);
        //Ignore set of a default value that is in ra.xml if called *before* setResourceAdapter
        if ("guest".equals(userName) && (ra == null)) {
            _loggerOC.fine(_lgrMID_INF+"setUserName:NOT setting default value="+userName);
            return;
        }
        _setUserName(userName);
    }

    //This method is called from setUserName and setResourceAdapter
    private void
    _setUserName(String userName)
    {
        this.userName = userName;
        try {
            xacf.setProperty(ConnectionConfiguration.imqDefaultUsername, userName);
        } catch (JMSException jmse) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setUserName:setProperty Exception for value="+userName);
            iae.initCause(jmse); 
            _loggerOC.warning(iae.getMessage());
            _loggerOC.throwing(_className, "setUserName()", iae);
            throw iae;
        }
    }
 
    /** Return the UserName for this ManagedConnectionFactory instance
     *   
     *  @return The UserName
     */
    public String
    getUserName()
    {
        _loggerOC.entering(_className, "getUserName()", userName);
        if (userName != null)
            return userName;
        else if (ra != null)
            return ra.getUserName();
        else
            return null;
    }

    /** Sets the Password for this ManagedConnectionFactory instance
     *
     *  @param password The Password
     */
    public void
    setPassword(String password)
    {
        _loggerOC.entering(_className, "setPassword()");
        //Ignore set of a default value that is in ra.xml if called *before* setResourceAdapter
        if ("guest".equals(password) && (ra == null)) {
            _loggerOC.fine(_lgrMID_INF+"setPassword:NOT setting default value");
            return;
        }
        _setPassword(password);
    }

    //This method is called from setPassword and setResourceAdapter
    private void
    _setPassword(String password)
    {
        this.password = password;
        try {
            xacf.setProperty(ConnectionConfiguration.imqDefaultPassword, password);
        } catch (JMSException jmse) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setPassword:setProperty Exception");
            iae.initCause(jmse); 
            _loggerOC.warning(iae.getMessage());
            _loggerOC.throwing(_className, "setPassword()", iae);
            throw iae;
        }
    }

    /** Return the Password for this ManagedConnectionFactory instance
     *
     *  @return The Password
     */
    public String
    getPassword()
    {
        _loggerOC.entering(_className, "getPassword()");
        if (password != null)
            return password;
        else if (ra != null)
            return ra.getPassword();
        else
            return null;
    }
 
    /** Sets the ClientId for this ManagedConnectionFactory instance
     *
     *  @param clientId The ClientId
     */
    public void
    setClientId(String clientId)
    {
        _loggerOC.entering(_className, "setClientId()", clientId);
        //ClientId is handled by the mcf and not pushed on to the xacf
        if ((clientId == null) || ("".equals(clientId))) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setClientId:NULL or empty disallowed");
            _loggerOC.warning(iae.getMessage());
            _loggerOC.throwing(_className, "setClientId()", iae);
            throw iae;
        }
        this.clientId = clientId;
    }

    /** Return the ClientId for this ManagedConnectionFactory instance
     *
     *  @return The ClientId
     */
    public String
    getClientId()
    {
        _loggerOC.entering(_className, "getClientId()", clientId);
        return clientId;
    }
    
	public void setUseSharedSubscriptionInClusteredContainer(boolean b) {
		useSharedSubscriptionsInClusteredContainer = b;
	}   
	
	public boolean isUseSharedSubscriptionInClusteredContainer(){
		return useSharedSubscriptionsInClusteredContainer;
	}

    /** Sets the enableRADirect behavior for this ManagedConnectionFactory instance
    *
    *  @param flag if true, enables RADirect mode behavior
    */
   public void
   setEnableRADirect(boolean flag)
   {
       _loggerOC.entering(_className, "setEnableRADirect()", Boolean.toString(flag));
       if ((this.ra != null) && this.ra._isRADirectAllowed() && (flag == true)){
           this.enableRADirect = true;
       } else {
           this.enableRADirect = false;
       }
   }
   
   /** Sets the enableAPIDirect behavior for this ManagedConnectionFactory instance
   *
   *  @param flag if true, enables APIDirect mode behavior
   */
  public void
  setEnableAPIDirect(boolean flag)
  {
      _loggerOC.entering(_className, "setEnableAPIDirect()", Boolean.toString(flag));
      if ((this.ra != null) && this.ra._isAPIDirectAllowed() && (flag == true)){
          this.enableAPIDirect = true;
      } else {
          this.enableRADirect = false;
      }
  }
    
  /** Return the enableRADirect behavior for this ManagedConnectionFactory instance
  *
  *  @return the enableRADirect behavior for this ManagedConnectionFactory instance
  */
 public boolean
 getEnableRADirect()
 {
     _loggerOC.entering(_className, "getEnableRADirect()",
             Boolean.toString(this.enableRADirect));
     return this.enableRADirect;
 }
 
 /** Return the enableAPIDirect behavior for this ManagedConnectionFactory instance
 *
 *  @return the enableAPIDirect behavior for this ManagedConnectionFactory instance
 */
public boolean
getEnableAPIDirect()
{
    _loggerOC.entering(_className, "getEnableAPIDirect()",
            Boolean.toString(this.enableAPIDirect));
    return this.enableAPIDirect;
}
    
    
    /** Sets the Reconnect behavior for this ManagedConnectionFactory instance
     *
     *  @param flag if true, enables reconnect behavior
     */
    public void
    setReconnectEnabled(boolean flag)
    {
        _loggerOC.entering(_className, "setReconnectEnabled()", Boolean.toString(flag));
        try {
        	
        	//mqraha -- RA setting over ride this. 
        	//if (ra != null && ra._isHAEnabled()) {
        	//		xacf.setProperty(ConnectionConfiguration.imqReconnectEnabled, Boolean.toString(true));
        	//		this.reconnectEnabled = true;
        	//} else {
        	xacf.setProperty(ConnectionConfiguration.imqReconnectEnabled, Boolean.toString(flag));
        	this.reconnectEnabled = flag;
        	//}
        	//mqraha ended
        	
            this.reconnectEnabledSet = true;
        } catch (JMSException jmse) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setReconnectEnabled:setProperty Exception for value "+flag);
            iae.initCause(jmse); 
            _loggerOC.warning(iae.getMessage());
            _loggerOC.throwing(_className, "setReconnectEnabled()", iae);
            throw iae;
        }
    }

    /** Returns the Reconnect behavior for this ManagedConnectionFactory instance
     *
     *  @return the Reconnect behavior for this ManagedConnectionFactory instance
     */
    public boolean
    getReconnectEnabled()
    {
        _loggerOC.entering(_className, "getReconnectEnabled()", Boolean.toString(reconnectEnabled));
        return reconnectEnabled;
    }

    /** Sets the Reconnect interval for this ManagedConnectionFactory instance
     *   
     *  @param reconnectInterval The reconnect interval in milliseconds
     *                  when reconnect is enabled
     */  
    public void
    setReconnectInterval(int reconnectInterval)
    {
        _loggerOC.entering(_className, "setReconnectInterval()", Integer.toString(reconnectInterval));
        this.reconnectInterval = reconnectInterval;
        try {
            xacf.setProperty(ConnectionConfiguration.imqReconnectInterval, Integer.toString(reconnectInterval));
        } catch (JMSException jmse) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setReconnectInterval:setProperty Exception for value "+reconnectInterval);
            iae.initCause(jmse); 
            _loggerOC.warning(iae.getMessage());
            _loggerOC.throwing(_className, "setReconnectInterval()", iae);
            throw iae;
        }
    }

    /** Returns the Reconnect interval for this ManagedConnectionFactory instance
     *
     *  @return the Reconnect interval for this ManagedConnectionFactory instance
     */
    public int
    getReconnectInterval()
    {
        _loggerOC.entering(_className, "getReconnectInterval()", Integer.toString(reconnectInterval));
        return reconnectInterval;
    }

    /** Sets the reconnectAttempts for this ManagedConnectionFactory instance
     *   
     *  @param reconnectAttempts The number of reconnect attempts
     *                  when reconnect is enabled
     */  
    public void
    setReconnectAttempts(int reconnectAttempts)
    {
        _loggerOC.entering(_className, "setReconnectAttempts()", Integer.toString(reconnectAttempts));
        this.reconnectAttempts = reconnectAttempts;
        try {
            xacf.setProperty(ConnectionConfiguration.imqReconnectAttempts, Integer.toString(reconnectAttempts));
        } catch (JMSException jmse) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setReconnectAttempts:setProperty Exception for value "+reconnectAttempts);
            iae.initCause(jmse); 
            _loggerOC.warning(iae.getMessage());
            _loggerOC.throwing(_className, "setReconnectAttempts()", iae);
            throw iae;
        }
    }

    /** Returns the Reconnect attempts for this ManagedConnectionFactory instance
     *   
     *  @return the reconnectAttempts for this ManagedConnectionFactory instance
     */
    public int
    getReconnectAttempts()
    {
        _loggerOC.entering(_className, "getReconnectAttempts()", Integer.toString(reconnectAttempts));
        return reconnectAttempts;
    }

    /** Sets the addressListBehavior for this ManagedConnectionFactory instance
     *   
     *   
     *  @param addressListBehavior The behavior of addressList
     *                             on connection establishment
     */  
    public void
    setAddressListBehavior(String addressListBehavior)
    {
        _loggerOC.entering(_className, "setAddressListBehavior()", addressListBehavior);
        if ("RANDOM".equalsIgnoreCase(addressListBehavior)) {
            this.addressListBehavior = "RANDOM";;
        } else {
            this.addressListBehavior = "PRIORITY";
        }
        try {
            xacf.setProperty(ConnectionConfiguration.imqAddressListBehavior, this.addressListBehavior);
        } catch (JMSException jmse) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setAddressListBehavior:setProperty Exception for value "+addressListBehavior);
            iae.initCause(jmse); 
            _loggerOC.warning(iae.getMessage());
            _loggerOC.throwing(_className, "setAddressListBehavior()", iae);
            throw iae;
        }
    }
 
    /** Returns the addressListBehavior for this ManagedConnectionFactory instance
     *   
     *
     *  @return the addressListBehavior for this ManagedConnectionFactory instance
     *   
     */
    public String
    getAddressListBehavior()
    {
        _loggerOC.entering(_className, "getAddressListBehavior()", addressListBehavior);
        return addressListBehavior;
    }

    /** Sets the addressListIterations for this ManagedConnectionFactory instance
     *   
     *  @param addressListIterations The number of iterations on
     *         addressList to be attempted on connection establishment
     */  
    public void
    setAddressListIterations(int addressListIterations)
    {
        _loggerOC.entering(_className, "setAddressListIterations()", Integer.toString(addressListIterations));
        if (addressListIterations < 1) {
            _loggerOC.warning(_lgrMID_WRN+"setAddressListIterations:Invalid value:"+addressListIterations+":Setting to 1");
            this.addressListIterations = 1;
        } else {
            this.addressListIterations = addressListIterations;
        }
        try {
            xacf.setProperty(ConnectionConfiguration.imqAddressListIterations, Integer.toString(this.addressListIterations));
        } catch (JMSException jmse) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setAddressListIterations:setProperty Exception for value "+addressListIterations);
            iae.initCause(jmse); 
            _loggerOC.warning(iae.getMessage());
            _loggerOC.throwing(_className, "setAddressListIterations()", iae);
            throw iae;
        }
    }
 
    /** Returns the addressListIterations for this ManagedConnectionFactory instance
     *
     *  @return the addressListIterations for this ManagedConnectionFactory instance
     */
    public int
    getAddressListIterations()
    {
        _loggerOC.entering(_className, "getAddressListIterations()", Integer.toString(addressListIterations));
        return addressListIterations;
    }

    //Future release 
    /** Sets the HA requirement for this ManagedConnectionFactory instance
     *
     *  @param flag if true, requires HA functionality
    public void
    setHARequired(boolean flag)
    {
        _loggerOC.entering(_className, "setHARequired()", Boolean.toString(flag));
        this.haRequired = flag;
        this.haRequiredSet = true;
    }
     */

    //Future release 
    /** Returns the HA requirement for this ManagedConnectionFactory instance
     *
     *  @return the HA requirement for this ManagedConnectionFactory instance
    public boolean
    getHARequired()
    {
        _loggerOC.entering(_className, "getHARequired()", Boolean.toString(haRequired));
        return haRequired;
    }
     */

    /** Sets a general property on the embedded connection factory
     *
     *  @param name The property name
     *  @param value The property value
     */
    public void
    setProperty(String name, String value)
    {
        _loggerOC.entering(_className, "setProperty()", "Prop name="+name+":Prop value="+value);
        try {
            if ("MessageServiceAddressList".equalsIgnoreCase(name))
            {
                xacf.setProperty(ConnectionConfiguration.imqAddressList, value);
            }
            else
            {
                xacf.setProperty(name, value);
            }
        } catch (JMSException jmse) {
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+"setProperty: exception setting property "+name+" to "+value);
            iae.initCause(jmse); 
            _loggerOC.warning(iae.getMessage());
            _loggerOC.throwing(_className, "setProperty()", iae);
            throw iae;
        }
    }
     
    /**
     * Set additional arbitrary connection factory properties
     * 
     * The properties must be specified as a String containing a comma-separated list of name=value pairs
     * e.g. prop1=value1,prop2=value2
     * If a value contains a = or , you can either 
     * place the whole value between quotes (prop1="val=ue") or 
     * use \ as an escape character (prop1=val\,ue)
     * 
     * This method cannot be used to set properties which are configured internally or which have their own setter methods. These are:
     * imqReconnectEnabled, imqReconnectAttempts, imqReconnectInterval, imqDefaultUsername, 
     * imqDefaultPassword, imqAddressList, imqAddressListIterations, imqAddressListBehavior
     * Any values specified for those properties will be ignored
     *
     * @param props connection factory properties as a comma-separated list of name=value pairs
     */
    public void setOptions(String stringProps){
    	 _loggerOC.entering(_className, "setOptions()", "stringProps="+stringProps);
    	
    	Hashtable <String, String> props=null;
    	try {
			props=CustomTokenizer.parseToProperties(stringProps);
		} catch (InvalidPropertyException ipe) {
			// syntax error in properties
			String message="ManagedConnectionFactory property options has invalid value " + stringProps + " error is: "+ipe.getMessage(); 
			 _loggerOC.warning(_lgrMID_WRN+message);
            IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_EXC+message); 
            iae.initCause(ipe);
            throw iae;
		}
		
		for (Enumeration<String> keysEnum = props.keys(); keysEnum.hasMoreElements();) {
			String thisPropertyName = (String) keysEnum.nextElement();
			
			// don't allow properties that are, or can be, set elsewhere to be overridden here as this might have unexpected results
			if (thisPropertyName.equals(ConnectionConfiguration.imqReconnectInterval) ||
				thisPropertyName.equals(ConnectionConfiguration.imqDefaultUsername) ||
				thisPropertyName.equals(ConnectionConfiguration.imqDefaultPassword) ||
				thisPropertyName.equals(ConnectionConfiguration.imqAddressList) ||
				thisPropertyName.equals(ConnectionConfiguration.imqReconnectEnabled) ||
				thisPropertyName.equals(ConnectionConfiguration.imqAddressListIterations) ||
				thisPropertyName.equals(ConnectionConfiguration.imqReconnectAttempts) ||
				thisPropertyName.equals(ConnectionConfiguration.imqAddressListBehavior)) {
				_loggerOC.warning(_lgrMID_WRN+"Cannot use ManagedConnectionFactory property options to set property "+thisPropertyName+": ignoring");
				continue;
			} 
						
			setProperty(thisPropertyName, props.get(thisPropertyName));
		}
		
		options=stringProps;
		
    }

    public String getOptions() {
		return options;
	}
    
    /** Return the value of a general property on the connection factory
     *
     *  @param name The property name
     *  @return The property value
     */
    public String
    getProperty(String name)
    {
        String propval = null;
        try {
            propval = xacf.getProperty(name);
        } catch (JMSException jmse) {
            IllegalArgumentException iae = new IllegalArgumentException("MQRA:MCF-Error getting property named-"
                        + name);
            iae.initCause(jmse);
            throw iae;
        }
        return propval;
    }

    protected com.sun.messaging.XAConnectionFactory
    _getXACF()
    {
        return xacf;
    }
    
    protected ConnectionCreator getConnectionCreator() {
        return (ConnectionCreator) this.connectionCreator;
    }

    public String toString()
    {
        return ("ManagedConnectionFactory configuration=\n"+
            "\tMCFId                               ="+mcfId+"\n"+
            "\tAddressList                         ="+addressList+"\n"+
            "\tUserName                            ="+userName+"\n"+
            "\tClientId                            ="+clientId+"\n"+
            //"\thaRequired                          ="+haRequired+"\n"+
            "\tReconnectEnabled                    ="+reconnectEnabled+"\n"+
            "\tReconnectInterval                   ="+reconnectInterval+"\n"+
            "\tReconnectAttempts                   ="+reconnectAttempts+"\n"+
            "\tAddressListBehavior                 ="+addressListBehavior+"\n"+
            "\tAddressListIterations               ="+addressListIterations+"\n"+
            "\toptions                             ="+options+"\n");
    }

	public boolean getInAppClientContainer() {
		return ra.getInAppClientContainer();
	}




}
