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

package com.sun.messaging.ums.service;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.Semaphore;

import javax.jms.Connection;
import javax.jms.JMSException;

import com.sun.messaging.ums.common.Constants;
import java.util.logging.Logger;

public class CachedConnection {

    private Connection conn = null;
    private ArrayList<Client> clients = new ArrayList<Client>();
    private Properties props = null;
    private static final String MAX_CLIENTS = "100";
    private int maxClients = Integer.parseInt(MAX_CLIENTS);
    private boolean isClosed = false;
    private Semaphore available = null;
    private Logger logger = UMSServiceImpl.logger;
    private long timestamp = 0;

    public CachedConnection(Connection conn, Properties props) {
        this.conn = conn;
        this.props = props;

        init();
    }

    private void init() {

        try {

            String tmp = props.getProperty(Constants.MAX_CLIENT_PER_CONNECTION, MAX_CLIENTS);
            maxClients = Integer.parseInt(tmp);

        } catch (Exception e) {
            e.printStackTrace();
        }

        available = new Semaphore(maxClients, true);

    }

    public Connection getConnection() {
        checkClosed();
        return conn;
    }

    public synchronized void add(Client client) {
        checkClosed();
        clients.add(client);
    }

    public synchronized void remove(Client client) {
        clients.remove(client);
    }

    public synchronized int size() {
        return clients.size();
    }

    /**
     * Called by CachedConnectionPool.
     * @return
     */
    protected boolean reachedMaxCapacity() {
        return (available.availablePermits() == 0);
    }

    /**
     * Called by CachedConnectionPool.
     */
    protected void acquire() {
        
        if (UMSServiceImpl.debug) {
            logger.info ("acquiring semaphore permit, available#: " + available.availablePermits());
        }
        
        available.acquireUninterruptibly();
        this.setTimestamp();
    }

    /**
     * Called by CachedConnectionPool.
     */
    protected void release() {

        available.release();
        this.setTimestamp();
        
        if (UMSServiceImpl.debug) {
            logger.info ("released semaphore, available#: " + available.availablePermits());
        }
    }

    protected boolean inUse() {
        
        boolean isInuse = (available.availablePermits() < this.maxClients);
        
        if (UMSServiceImpl.debug) {
            logger.info ("is inuse = " + isInuse + ", available permits=" + available.availablePermits() + " , max capacity=" + maxClients);
        }
        
        return isInuse;
    }

    /**
     * This should be called from the pool.
     * 
     * @throws JMSException
     */
    protected synchronized void close() throws JMSException {

        if (this.available.availablePermits() != this.maxClients) {

            throw new RuntimeException(
                    "Attemp to close a connection with Clients associate with it.");
        }

        this.isClosed = true;

        //XXX remove from pool

        conn.close();
    }

    private synchronized void checkClosed() {

        if (isClosed) {
            throw new UMSServiceException("Cached connection is closed.");
        }
    }

    private synchronized void setTimestamp() {
        this.timestamp = System.currentTimeMillis();
    }

    public synchronized long getTimestamp() {
        return this.timestamp;
    }

    public String toString() {
        return "CachedConnection, conn=" + this.conn + ", available permits=" + this.available.availablePermits() + ", max capacity=" + this.maxClients;
    }
}
