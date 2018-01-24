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

import javax.jms.*;

import javax.resource.*;
import javax.resource.spi.*;

import java.util.logging.Logger;

/**
 *  Implements the ManagedConnectionMetaData interface for the Sun MQ JMS RA
 */

public class ManagedConnectionMetaData
implements javax.resource.spi.ManagedConnectionMetaData
{

    /* Loggers */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.ManagedConnectionMetaData";
    protected static transient final String _lgrNameOutboundConnection =
            "javax.resourceadapter.mqjmsra.outbound.connection";
    protected static transient final Logger _loggerOC =
            Logger.getLogger(_lgrNameOutboundConnection);
    protected static transient final String _lgrMIDPrefix = "MQJMSRA_MM";
    protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";
 

    /** The ManagedConnection for this ManagedConnectionMetaData instance */
    private com.sun.messaging.jms.ra.ManagedConnection mc = null;

    /** Constructor */
    public ManagedConnectionMetaData(com.sun.messaging.jms.ra.ManagedConnection mc)
    {
        _loggerOC.entering(_className, "constructor()");
        this.mc = mc;
    }
    
    // ManagedConnectionMetaData interface methods //
    // 

    /** Return the Product Name
     *
     *  @return The EIS Product Name
     */
    public String
    getEISProductName()
    throws javax.resource.ResourceException
    {
        _loggerOC.entering(_className, "getEISProductName()");
        try {
            ConnectionAdapter ca = mc.getConnectionAdapter();
            return ca.getMetaData().getJMSProviderName();
        } catch (JMSException jmse) {
            ResourceException re = new EISSystemException(_lgrMID_EXC+"getEISProductName:Failed:"+jmse.getMessage());
            re.initCause(jmse);
            _loggerOC.warning(re.getMessage());
            _loggerOC.throwing(_className, "getEISProductName()", re);
            throw re;
        }
    }

    /** Return the Product Version
     *
     *  @return The EIS Product Version
     */
    public String
    getEISProductVersion()
    throws javax.resource.ResourceException
    {
        _loggerOC.entering(_className, "getEISProductVersion()");
        try {
            ConnectionAdapter ca = mc.getConnectionAdapter();
            return ca.getMetaData().getProviderVersion();
        } catch (JMSException jmse) {
            ResourceException re = new EISSystemException(_lgrMID_EXC+"getEISProductVersion:Failed:"+jmse.getMessage());
            re.initCause(jmse);
            _loggerOC.warning(re.getMessage());
            _loggerOC.throwing(_className, "getEISProductName()", re);
            throw re;
        }
    }

    /** Return the max active connections per managed connection?
     *
     *  @return The max connections
     */
    public int
    getMaxConnections()
    throws javax.resource.ResourceException
    {
        _loggerOC.entering(_className, "getMaxConnections()");
        return 1;
    }

    /** Return the User Name for this managed connection
     *
     *  @return The User Name 
     */
    public String
    getUserName()
    throws javax.resource.ResourceException
    {
        _loggerOC.entering(_className, "getUserName()");
        if (mc.isDestroyed()) {
            javax.resource.spi.IllegalStateException ise = new javax.resource.spi.IllegalStateException(
                        _lgrMID_EXC+"getUserName:Failed:ManagedConnection is destroyed");
            _loggerOC.warning(ise.getMessage());
            _loggerOC.throwing(_className, "getUserName()", ise);
            throw ise;
        } else {
            return mc.getPasswordCredential().getUserName();
        }
    }
}

