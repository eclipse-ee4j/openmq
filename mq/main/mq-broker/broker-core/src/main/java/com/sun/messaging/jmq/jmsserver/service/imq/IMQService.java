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
 * @(#)IMQService.java	1.56 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import java.io.*;

import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.auth.AuthCacheData;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import java.util.*;




public abstract class IMQService implements Service
{

    protected static boolean DEBUG = false;

    /**
     * the list of connections which this service knows about
     */
    protected ConnectionManager connectionList = Globals.getConnectionManager();

    private boolean serviceRunning = false;
    private boolean shuttingDown = false;

    private int state = ServiceState.UNINITIALIZED;
    private int type = ServiceType.NORMAL;

    protected Logger logger = Globals.getLogger();

    protected String name = null;
    private AuthCacheData authCacheData = new AuthCacheData();

    protected static final long DESTROY_WAIT_DEFAULT = 30000;

    private long serviceDestroyWait = DESTROY_WAIT_DEFAULT;
 
    HashMap serviceprops = null;

    private HashSet serviceress =  new HashSet();
    private ArrayList serviceressListeners =  new ArrayList();


    public IMQService(String name, int type)
    {
        this.name = name;
        this.type = type;
    }

    protected boolean getDEBUG() { 
        return DEBUG;
    }

    protected void addServiceProp(String name, String value) {
        if (serviceprops == null)
            serviceprops = new HashMap();
        serviceprops.put(name, value);
    }

    public void resetCounters()
    {
        List cons = connectionList.getConnectionList(this);
        Iterator itr = cons.iterator();
        while (itr.hasNext()) {
            ((IMQConnection)itr.next()).resetCounters();
        }
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("name", name);
        ht.put("state", ServiceState.getString(state));
        ht.put("shuttingDown", String.valueOf(isShuttingDown()));
        if (serviceprops != null)
            ht.put("props", new Hashtable(serviceprops));
        ht.put("connections", connectionList.getDebugState(this));
        return ht; 
    }
    public Hashtable getPoolDebugState() {
        return (new Hashtable());
    }


    /*
    public void dumpPool()  {
        pool.debug();
    }
    */

    public int size() {
        List list = connectionList.getConnectionList(this);
        return list.size();
    }
    public List getConsumers() {
        ArrayList list = new ArrayList();
        List cons = connectionList.getConnectionList(this);
        Iterator itr = cons.iterator();
        while (itr.hasNext()) {
            List newList = ((IMQConnection)itr.next()).getConsumers();
            list.addAll(newList);
        }
        return list;
    }

    public List getProducers() {
        ArrayList list = new ArrayList();
        List cons = connectionList.getConnectionList(this);
        Iterator itr = cons.iterator();
        while (itr.hasNext()) {
            List newList = ((IMQConnection)itr.next()).getProducers();
            list.addAll(newList);
        }
        return list;
    }

    public Protocol getProtocol() {
        return (null);
    }

    public String getName() {
        return name;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }
    
    public int getServiceType() {
        return type;
    }

    public synchronized int getMinThreadpool() {
        return (0);
    }

    public synchronized int getMaxThreadpool() {
        return (0);
    }

    public synchronized int getActiveThreadpool() {
        return (0);
    }

    public void setPriority(int priority) {
    }

    /**
     * @return int[0] min; int[1] max; -1 or null no change
     */
    public synchronized int[] setMinMaxThreadpool(int min, int max) {
        return null;
    }

    public void setDestroyWaitTime(long value) {
        serviceDestroyWait = value;
    }

    public long getDestroyWaitTime() {
        return(serviceDestroyWait);
    }

    public void setServiceRunning(boolean value)  {
	serviceRunning = value;
    }

    public boolean isServiceRunning()  {
	return(serviceRunning);
    }

    public void setShuttingDown(boolean value)  {
	shuttingDown = value;
    }

    public boolean isShuttingDown()  {
	return(shuttingDown);
    }

    public void stopNewConnections() 
        throws IOException, IllegalStateException
    {
        if (state != ServiceState.RUNNING) {
            throw new IllegalStateException(
               Globals.getBrokerResources().getKString(
                   BrokerResources.X_CANT_STOP_SERVICE));
        }
        state = ServiceState.QUIESCED;
    }

    public void startNewConnections() 
        throws IOException
    {
        if (state != ServiceState.QUIESCED && state != ServiceState.PAUSED) {
            throw new IllegalStateException(
               Globals.getBrokerResources().getKString(
                   BrokerResources.X_CANT_START_SERVICE));
        }

        synchronized (this) {
            setState(ServiceState.RUNNING);
            this.notifyAll();
        }
    }

    public void destroyService() {
        if (getState() < ServiceState.STOPPED)
            stopService(true);
        synchronized (this) {
            setState(ServiceState.DESTROYED);
            this.notifyAll();
        }

    }

    public void updateService(int port, int min, int max) 
    throws IOException, PropertyUpdateException, BrokerException {
    }

    public AuthCacheData getAuthCacheData() {
        return authCacheData;     
    }  

    public void removeConnection(ConnectionUID uid, int reason, String str) {
         connectionList.removeConnection(uid,
             true, reason, str);
    }

    public HashMap getServiceProperties() {
        return serviceprops;
    }

    public boolean isDirect() {
        return (false);
    }

    public String toString() {
        return getName();
    }

    public void addServiceRestriction(ServiceRestriction sr) {
        synchronized(serviceress) {
            serviceress.add(sr);
            notifyServiceRestrictionChanged();
        }
    }

    public void removeServiceRestriction(ServiceRestriction sr) {
        synchronized(serviceress) {
            serviceress.remove(sr);
            notifyServiceRestrictionChanged();
        }
    }

    public ServiceRestriction[] getServiceRestrictions() {
        synchronized(serviceress) {
            return (ServiceRestriction[])serviceress.toArray(
                   new ServiceRestriction[serviceress.size()]);
        }
    }

    public void addServiceRestrictionListener(ServiceRestrictionListener l) {
        synchronized(serviceress) {
            serviceressListeners.add(l);
        }
    }

    public void removeServiceRestrictionListener(ServiceRestrictionListener l) {
        synchronized(serviceress) {
            serviceressListeners.remove(l);
        }
    }

    private void notifyServiceRestrictionChanged() {
        synchronized(serviceress) {
            Iterator itr = serviceressListeners.iterator();
            ServiceRestrictionListener l = null;
            while (itr.hasNext()) {
                l = (ServiceRestrictionListener)itr.next();
                l.serviceRestrictionChanged(this);
            }
        }
    }

}

