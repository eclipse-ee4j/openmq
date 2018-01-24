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

/**
 *  ConnectionRequestInfo encapsulates the S1 MQ RA
 *  per connection information that is needed to
 *  create and match connections.
 */

public class ConnectionRequestInfo implements javax.resource.spi.ConnectionRequestInfo
{
    /** The ManagedConnectionFactory for this instance */
    private com.sun.messaging.jms.ra.ManagedConnectionFactory mcf = null;

    /** The XAConnection for this instance 
    private XAConnectionImpl xac = null; */

    /** The XASession for this instance 
    private XASessionImpl xas = null; */

    /** The User Name parameter for this instance */
    private String userName = null;

    /** The Password parameter for this instance */
    private String password = null;

    /** The clientID for this instance
    private String clientId = null; */
    
    /** The required connection type */
    private ConnectionType connectionType;

	/** The identifier (unique) for this instance */
    private transient int criId = 0;
 
    /** The uniquifier */
    private static int idCounter = 0;
    
    private static synchronized int incrementIdCounter(){
    	return ++idCounter;
	}
     
    public ConnectionRequestInfo(com.sun.messaging.jms.ra.ManagedConnectionFactory mcf,
        String userName, String password,ConnectionType connectionType)
    {
        criId = incrementIdCounter();
        this.mcf = mcf;
        this.userName = userName;
        this.password = password;
        this.connectionType=connectionType;
    }

    /** Compares this ConnectionRequestInfo instance to one
     *  passed in for equality.
     *   
     *  @return true If the two instances are equal, otherwise
     *          return false.
     */  
    public boolean
    equals(java.lang.Object other)
    {
        if (other == null) {
            return false;
        }
        if (other instanceof com.sun.messaging.jms.ra.ConnectionRequestInfo) {
            com.sun.messaging.jms.ra.ConnectionRequestInfo otherCRI =
                (com.sun.messaging.jms.ra.ConnectionRequestInfo)other;

            String oUserName = otherCRI.getUserName();
            String oPassword = otherCRI.getPassword();
            ConnectionType oConnectionType = otherCRI.getConnectionType();
            com.sun.messaging.jms.ra.ManagedConnectionFactory oMCF = otherCRI.getMCF();

            if (
                ((oUserName != null && oUserName.equals(userName)) ||
                 (oUserName == null && userName == null))
               &&
                ((oPassword != null && oPassword.equals(password)) ||
                 (oPassword == null && password == null))
               &&
                (oConnectionType==connectionType)
               &&
                ((oMCF != null && oMCF.equals(mcf)) ||
                 (oMCF == null && mcf == null))
               ){
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }      
    }
 
    /** Returns the hash code for this ConnectionRequestInfo instance
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
        //So, we can simply use the criId.

        //Concat data
        String hashStr = "" + userName + password + criId + connectionType.ordinal();
        return hashStr.hashCode();
    }

    public com.sun.messaging.jms.ra.ManagedConnectionFactory
    getMCF()
    {
        return mcf;
    }

    public String
    getUserName()
    {
        return userName;
    }

    public String
    getPassword()
    {
        return password;
    }

    public int
    getCRIId()
    {
        return criId;
    }
    
    public ConnectionType getConnectionType() {
		return connectionType;
	}

    public String toString()
    {
        return ("ConnectionRequestInfo configuration=\n"+
            "\tcriId                               ="+criId+"\n"+
            "\tUserName                            ="+userName+"\n"+
            "\tPassword                            ="+password+"\n"+
            "\tconnectionType                      ="+connectionType+"\n"+
            "\tMCF configuration                   ="+(mcf !=null ? mcf.toString() : "NULL" )+"\n");
            //"\tClientId                            ="+clientId+"\n");
    }

    public enum ConnectionType {UNIFIED_CONNECTION,QUEUE_CONNECTION,TOPIC_CONNECTION}
}

