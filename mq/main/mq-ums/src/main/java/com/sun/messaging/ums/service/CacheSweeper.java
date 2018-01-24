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

import java.util.Properties;

import com.sun.messaging.ums.common.Constants;
import com.sun.messaging.ums.resources.UMSResources;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheSweeper extends Thread {

    //private JMSCache cache = null;
    //private ClientPool cache = null;
    //sweep thread check this flag
    
    private boolean isRunning = true;
    //cache object TTL, 7 minutes
    private static final long CACHE_TTL = 7 * 60 * 1000;
    
    //sweep interval, default 2 minutes
    private long sweepInterval = 2 * 60 * 1000;
    
    private long cacheDuration = CACHE_TTL;
    
    private Logger logger = null;
    
    private static final String myName = "CacheSweper";
    
    private Properties props = null;
    
    //private CachedConnectionPool ccpool = null;
    
    //private String provider = null;

    private ArrayList<ClientPool> cacheList = new ArrayList<ClientPool>();
    
    public CacheSweeper(Properties p) {
        
        
        logger = Logger.getLogger(this.getClass().getName());
        
        this.props = p;

        super.setName(myName);

        init();
    }

    private void init() {

        try {

            String tmp = props.getProperty(Constants.CACHE_DURATION);
            if (tmp != null) {
                this.cacheDuration = Long.parseLong(tmp);
            }

            tmp = props.getProperty(Constants.SWEEP_INTERVAL);
            if (tmp != null) {
                this.sweepInterval = Long.parseLong(tmp);
            }

            //this.ccpool = cache.getConnectionPool();

            //logger.info ("CacheSweeper created ...., sweep interval=" + this.sweepInterval + ", cache duration = " + this.cacheDuration);

            String msg = UMSResources.getResources().getKString(UMSResources.UMS_SWEEPER_INIT, this.sweepInterval, this.cacheDuration);
            logger.info(msg);
            
        } catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }
    
    public void addClientPool (ClientPool cache) {
        this.cacheList.add(cache);
    }
    
    public void removeClientPool (ClientPool cache) {
        this.cacheList.remove(cache);
    }

    /**
     * check clientId time stamps.  
     * close/remove session/producer/consumer if unused for
     * more than 7 minutes.
     */
    private void sweep() {
        
        int size = this.cacheList.size();
        
        for (int index = 0; index < size; index++) {
            
            ClientPool cache = this.cacheList.get(index);
            CachedConnectionPool ccpool = cache.getConnectionPool();
            
            try {
                
                if (UMSServiceImpl.getDebug()) {
                    logger.info ("CacheSweeper sweeping client cache ...." + cache);
                }
                
                cache.sweep(cacheDuration);
            } catch (Exception e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }

            try {
                
                if (UMSServiceImpl.getDebug()) {
                    logger.info ("CacheSweeper sweeping connection cache ...." + ccpool);
                }
                
                ccpool.sweep(cacheDuration);
            } catch (Exception e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
    }

    /**
     * wake up every 30 secs and close/remove unused 
     * sessions/producers/consumers
     */
    public void run() {

        while (isRunning = true) {

            try {

                //this.debugLog("CacheSweeper running ...." + this);

                synchronized (this) {
                    wait(sweepInterval);
                    sweep();
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
    }

    /**
     * close sweep thread
     */
    public void close() {
        synchronized (this) {
            this.isRunning = false;
            notifyAll();
        }
    }

    public String toString() {
        return this.getClass().getName() + ", sweep interval=" + this.sweepInterval + ", cacheDuration=" + this.cacheDuration + ", is running=" + this.isRunning;
    }
}
