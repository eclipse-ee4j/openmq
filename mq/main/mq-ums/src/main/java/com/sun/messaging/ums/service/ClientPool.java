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

//import com.sun.messaging.ums.UMSConnectionFactory;
//import java.util.Date;
import com.sun.messaging.ums.common.Constants;
import com.sun.messaging.ums.common.MessageUtil;
import com.sun.messaging.ums.resources.UMSResources;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Logger;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.xml.soap.SOAPMessage;

public class ClientPool implements Sweepable {

    private String provider = null;
    
    private String serviceName = UMSServiceImpl.SERVICE_NAME;
    
    private Properties props = null;
    private Logger logger = UMSServiceImpl.logger;
    
    private Hashtable<String, Client> clients = new Hashtable<String, Client>();
    //private ClientTable clients = new ClientTable();
    
    private CachedConnectionPool ccpool = null;
    //private Lock locks = null;
    //used to create destinations
    private Client destService = null;
    /**
     * table contains (name, JMSTopicDest)
     */
    private Hashtable<String, UMSDestination> topicTable =
            new Hashtable<String, UMSDestination>();
    /**
     * table contains (name, JMSQueueDest)
     */
    private Hashtable<String, UMSDestination> queueTable =
            new Hashtable<String, UMSDestination>();

    public ClientPool(String provider, Properties p) throws JMSException {

        this.provider = provider;

        //this.serviceName = serviceName;
        this.props = p;

        //this.locks = locks;

        this.ccpool = new CachedConnectionPool(provider, props);

        init();
    }

    private void init() throws JMSException {
        
        String sid = ccpool.nextSid();
        
        this.destService = this.createInternalClient(sid);
    }

    public CachedConnectionPool getConnectionPool() {
        return this.ccpool;
    }

    protected String authenticate(String user, String password, boolean transacted) throws JMSException {
        
        String sid = this.ccpool.authenticate(user, password);
        
        //Client client = this.createNewClient(sid, transacted);
        this.createNewClient(sid, transacted);
        
        return sid;
    }

    protected void authenticateSid(String sid) throws JMSException {
        
        //this.ccpool.authenticateSid(sid);
        
        if (clients.containsKey(sid) == false) {
            throw new JMSException("sid is not authenticated.  Use login to get a new sid, expired/invalid sid=" + sid);
        }
        
    }
    
    protected Client getClient (String sid, Map map) throws UMSServiceException {
        
        Client client = null;
        
        String user = null;
        String pass = null;
        boolean transacted = false;
        
        try {
            
            if (sid == null) {
                
                String[] ua = (String[]) map.get(Constants.USER);

                if (ua != null && ua.length == 1) {
                    user = ua[0];
                }

                String[] pa = (String[]) map.get(Constants.PASSWORD);
                if (pa != null && pa.length == 1) {
                    pass = pa[0];
                }
                
                //String[] tmp = (String[]) map.get(Constants.TRANSACTED);
                //if (tmp != null && tmp.length == 1) {
                //     transacted = Boolean.valueOf(tmp[0]);
                //}
            }
            
            client = getClient(sid, user, pass, transacted);
            
            return client;
            
        } catch (Exception e) {
            UMSServiceException umse = new UMSServiceException (e);
            
            throw umse;
        }
    }
    
    protected Client getClient (SOAPMessage sm) throws UMSServiceException {
        
        Client client = null;
        
        String user = null;
        
        String pass = null;
        
        boolean transacted = false;
        try {

            String sid = MessageUtil.getServiceClientId(sm);

            if (sid == null) {
                
                user = MessageUtil.getServiceAttribute(sm, Constants.USER);
                pass = MessageUtil.getServiceAttribute(sm, Constants.PASSWORD);
                
                //must login to create a transacted client.
                
                //String tmp = MessageUtil.getServiceAttribute(sm, Constants.TRANSACTED);
                //transacted = Boolean.valueOf(tmp);
            }
            
            client = getClient(sid, user, pass, transacted);
            
            return client;
            
         } catch (Exception e) {
            UMSServiceException umse = new UMSServiceException (e);
            
            throw umse;
        } 
    }

    /**
     * Get a client instance based on its clientId.
     * @param clientId
     * @return
     * @throws JMSException
     */
    private Client getClient(String sid, String user, String password, boolean transacted) throws JMSException {

        String authSid = null;
        boolean noCache = true;

        //logger.info("*** getting client ....");

        if (sid != null) {
            
            if (UMSServiceImpl.debug) {
                logger.info("*** authenticating sid ...." + sid);
            }
            
            //if clientId is provided, we validate it.
            this.authenticateSid(sid);

            authSid = sid;
            noCache = false;

            //logger.info("*** authenticating sid ...., sid = " + uuid);
        } else {

            if (UMSServiceImpl.debug) {
                logger.info("*** authenticating user = " + user);
            }
            
            authSid = this.authenticate(user, password, transacted);
            noCache = true;
            
            if (UMSServiceImpl.debug) {
                logger.info("*** got sid=" + authSid + ", no cache mode=true");
            }
        }

        //Object lock = locks.getLock(uuid);
        Client client = null;

        synchronized (clients) {

            client = clients.get(authSid);

            client.setNoCache(noCache);
            client.setInuse(true);
        }

        return client;
    }

    private Client createInternalClient(String sid) throws JMSException {

        //Object lock = locks.getLock(sid);
        
        //this is to create destinations, no transact mode
        Client client = createNewClient(sid, false);

        client.setInuse(true);

        return client;
    }
    
    /**
     * The is called when finished using the client instance.
     * 
     * @param client
     */
    public void returnClient(Client client) {
    	
    	// client may be null if an exception was thrown previously
    	if (client==null) return;

        Object lock = client.getLock();

        synchronized (lock) {
            
            client.setInuse(false);
            client.setTimestamp();

            if (client.getNoCache()) {
                this.closeClient(client);
            }
        }

    }

    synchronized Client createNewClient(String sid, boolean transacted) throws JMSException {

        Client client = new Client(sid, ccpool, transacted);

        clients.put(sid, client);

        int index= sid.indexOf("-");
        String seq = sid.substring(0, index);
        
        String msg = UMSResources.getResources().getKString(UMSResources.UMS_NEW_CLIENT_CREATED, seq, clients.size());
        
        logger.info(msg);
        
        //logger.info("New client created, provider=" + this.provider + ", sid=" + sid + ", clientTable size=" + this.clients.size());

        return client;
    }

    public void close() {

        Iterator it = clients.values().iterator();

        while (it.hasNext()) {
            Client client = (Client) it.next();
            client.close();
        }

        clients.clear();

        //close cc pool
        this.ccpool.close();
    }

    public void closeClient(Client client) {

        String sid = client.getId();

        Object lock = client.getLock();

        synchronized (lock) {

            this.clients.remove(sid);

            //this.locks.removeLock(sid);

            client.close();    
        }
        
        int index= sid.indexOf("-");
        String seq = sid.substring(0, index);

        String msg = UMSResources.getResources().getKString(UMSResources.UMS_CLIENT_CLOSED, seq, clients.size());
        
        logger.info(msg);
        //logger.info("Client closed, provider=" + provider + ", sid=" + sid + ", size=" + clients.size());
        
    }

    public void closeClient(String sid) {

        Client client = clients.get(sid);

        if (client != null) {
            this.closeClient(client);
        } //else {
            //boolean removed = this.ccpool.removeSid(sid);
            //logger.info("provider = " + provider + ", removed sid from client cache, sid=" + sid + ", clientTable size=" + clients.size());
        //}

    }

    public Destination getJMSDestination(String name, boolean isTopic)
            throws JMSException {

        Destination jmsDest = null;
        UMSDestination umsDestination = null;
        
        Object lock = this.destService.getLock();
        
        synchronized (lock) {


            if (isTopic) {
                //jmsDest = (Destination) this.topicTable.get(name);
                
                umsDestination = (UMSDestination) this.topicTable.get(name);
                
                if (umsDestination != null) {
                    jmsDest = umsDestination.getJMSDestination();
                }
                
            } else {
                //jmsDest = (Destination) this.queueTable.get(name);
                
                umsDestination = (UMSDestination) this.queueTable.get(name);
                
                if (umsDestination != null) {
                    jmsDest = umsDestination.getJMSDestination();
                }
            }

            if (jmsDest == null) {

                if (isTopic) {
                    jmsDest = this.destService.getSession().createTopic(name);
                    
                    umsDestination = new UMSDestination (jmsDest);
                    
                    //topicTable.put(name, jmsDest);
                    topicTable.put(name, umsDestination);
                } else {
                    jmsDest = this.destService.getSession().createQueue(name);
                    
                    umsDestination = new UMSDestination (jmsDest);
                    
                    //queueTable.put (name, jmsDest);
                    queueTable.put (name, umsDestination);
                }

            }
        }
        
        if (UMSServiceImpl.debug) {
            logger.info("topic table size = " + this.topicTable.size());
            logger.info("queue table size = " + this.queueTable.size());
        }
        
        return jmsDest;
    }

    public void sweep(long duration) {
        
        this.sweepClient(duration);
        
        if (UMSServiceImpl.getDebug()) {
            logger.info("sweeping queue destination cache, duration (milli secs): " + duration);
        }
        
        this.sweepDestination(queueTable, duration);
        
        if (UMSServiceImpl.getDebug()) {
            logger.info("sweeping topic destination cache, duration (milli secs): " + duration);
        }
        
        this.sweepDestination(topicTable, duration);
    }
    
    private void sweepClient (long duration) {

        if (UMSServiceImpl.getDebug()) {
            logger.info("sweeping  client cache, duration (milli secs): " + duration);
        }

        Vector v = new Vector();

        Enumeration enum2 = this.clients.keys();

        long now = System.currentTimeMillis();

        while (enum2.hasMoreElements()) {

            String id = (String) enum2.nextElement();

            Client client = (Client) clients.get(id);

            if (UMSServiceImpl.debug) {
                logger.info("Got client: " + client);
            }

            if (client.getInUse() == false) {

                long timestamp = client.getTimestamp();

                if ((now - timestamp) > duration) {

                    // add to list
                    v.add(id);

                    if (UMSServiceImpl.debug) {
                        logger.info("added client to clean list: " + id);
                    }
                }
            }

            Thread.yield();
        }

        if (v.size() > 0) {
            this.removeFromClientTable(v);
        } else {
            if (UMSServiceImpl.debug) {
                logger.info("provider=" + provider + ", no client needs to be removed from cache ..., cache size: " + clients.size());
            }
        }

    }

    /**
     * XXX - sync
     * 
     * @param list
     */
    private void removeFromClientTable(List list) {

        int size = list.size();

        if (UMSServiceImpl.debug) {
            logger.info("removing client Id from clientTable, size=" + size);
        }
        
        for (int i = 0; i < size; i++) {

            String id = (String) list.get(i);

            Client client = clients.get(id);
            Object lock = client.getLock();

            synchronized (lock) {

                if (client.getInUse() == false) {
                    //this.clients.remove(id);
                    //client.close();
                    this.closeClient(client);
                }
            }
            
            if (UMSServiceImpl.debug) {
                logger.info("provider=" + this.provider + ", removed client Id from clientTable, client = " + client + ", size=" + clients.size());
            }
        }
    }
    
    private void sweepDestination (Hashtable destTable, long duration) {

        Vector v = new Vector();

        Enumeration enum2 = destTable.keys();

        long now = System.currentTimeMillis();

        while (enum2.hasMoreElements()) {

            String destname = (String) enum2.nextElement();

            UMSDestination umsdest = (UMSDestination) destTable.get(destname);

            if (UMSServiceImpl.debug) {
                logger.info("Got umsDestination: " + umsdest);
            }

            long timestamp = umsdest.getTimestamp();

            if ((now - timestamp) > duration) {

                // add to list
                v.add(destname);

                if (UMSServiceImpl.debug) {
                    logger.info("added destination to clean list: " + destname);
                }
            }

            Thread.yield();
        }

        if (v.size() > 0) {
            this.removeFromDestTable(v, destTable);
        } else {
            if (UMSServiceImpl.debug) {
                logger.info("provider=" + provider + ", no destination needs to be removed from cache ..., cache size: " + destTable.size());
            }
        }

    }
    
    private void removeFromDestTable(List list, Hashtable destTable) {

        int size = list.size();

        if (UMSServiceImpl.debug) {
            logger.info("removing dest from destTable, size=" + size);
        }
        
        for (int i = 0; i < size; i++) {

            String destname = (String) list.get(i);

            //Client client = clients.get(id);
            destTable.remove(destname);
             
            if (UMSServiceImpl.debug) {
                logger.info("provider=" + this.provider + ", removed destination from Table, dest = " + destname + ", size=" + destTable.size());
            }
        }
    }

    public String toString() {
        return this.getClass().getName() + ", provider=" + this.provider + ", service=" + this.serviceName + ", #clients=" + this.clients.size();
    }
}
