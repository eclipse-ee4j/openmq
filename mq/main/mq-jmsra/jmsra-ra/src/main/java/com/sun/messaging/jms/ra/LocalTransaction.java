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

import javax.resource.ResourceException;
import javax.resource.spi.EISSystemException;

import com.sun.messaging.jmq.jmsclient.ConnectionImpl;

/**
 *  Implements the LocalTransaction interface for the Sun MQ RA
 */

public class LocalTransaction
implements javax.resource.spi.LocalTransaction
{
    /** The connection event listener list */
    //private Vector listeners = null;

    /** The ManagedConnection associated with this LocalTransaction */
    private com.sun.messaging.jms.ra.ManagedConnection mc = null;
    private ConnectionImpl xac = null;

    private long transactionID = -1L;
 
    protected boolean started = false;
    protected boolean active = false;
 


    /** Constructor */
    public LocalTransaction(com.sun.messaging.jms.ra.ManagedConnection mc, ConnectionImpl xac)
    {
        //System.out.println("MQRA:LT:Constr");
        this.mc = mc;
        this.xac = xac;
    }

    /** Begin a local transaction */
    public synchronized void
    begin()
    throws ResourceException
    {
        //System.out.println("MQRA:LT:begin()");
        try {
            if (!xac._isClosed()) {
                transactionID = xac.getProtocolHandler().startTransaction(transactionID, -1, null);
            } else {
                ResourceException re = new EISSystemException("MQRA:LT:startTransaction exception:Connection is closed");
                throw re;
            }
            //mc.getConnectionAdapter().getSessionAdapter().startLocalTransaction();
        } catch (Exception ex) {
            ResourceException re = new EISSystemException("MQRA:LT:startTransaction exception:"+
                ex.getMessage());
            re.initCause(ex);
            throw re;
        }
        started = true;
        active = true;
        mc.setLTActive(true);
    }

    /** Commit a local transaction */
    public synchronized void
    commit()
    throws ResourceException
    {
        //System.out.println("MQRA:LT:commit()");
        try {
            if (!xac._isClosed()) {
                xac.getProtocolHandler().commit(transactionID, -1, null);
            } else {
                ResourceException re = new EISSystemException("MQRA:LT:commitTransaction exception:Connection is closed");
                throw re;
            }
        } catch (Exception ex) {
            ResourceException re = new EISSystemException("MQRA:LT:commit exception:"+
                ex.getMessage());
            re.initCause(ex);
            throw re;
        } finally {
            mc.setLTActive(false);
            started = false;
            active = false;
        }
    }

    /** Rollback a local transaction */
    public synchronized void
    rollback()
    throws ResourceException
    {
        //System.out.println("MQRA:LT:rollback()");
        try {
            if (!xac._isClosed()) {
                xac.getProtocolHandler().rollback(transactionID, null);
            } else {
                ResourceException re = new EISSystemException("MQRA:LT:rollbackTransaction exception:Connection is closed");
                throw re;
            }
        } catch (Exception ex) {
            ResourceException re = new EISSystemException("MQRA:LT:rollback exception:"+
                ex.getMessage());
            re.initCause(ex);
            throw re;
        } finally {
            mc.setLTActive(false);
            started = false;
            active = false;
        }
    }

    public synchronized long getTransactionID() {
        return transactionID;
    }

    public boolean started() {
        return started;
    }

    public synchronized boolean isActive() {
        return active;
    }
}

