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
 * %W% %G%
 */ 

package com.sun.messaging.jmq.jmsserver.plugin.spi;

import java.util.*;

import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.util.lists.Limitable;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.util.CacheHashMap;

/**
 */

public abstract class ProducerSpi {
    
    protected static boolean DEBUG=false;

    protected transient Logger logger = Globals.getLogger();

    // record information about the last 20 removed
    // producers
    protected final static CacheHashMap cache = new CacheHashMap(20);

    private boolean valid = true;
    
    protected final static Map<ProducerUID, ProducerSpi> allProducers =
        Collections.synchronizedMap(new HashMap<ProducerUID, ProducerSpi>());

    protected static final Set wildcardProducers = Collections.synchronizedSet(new HashSet());

    protected transient Map lastResumeFlowSizes = Collections.synchronizedMap(new HashMap());

    private ConnectionUID connection_uid;
    
    protected DestinationUID destination_uid;
    //protected transient Set destinations = null;

    protected ProducerUID uid;
    
    //private long creationTime;

    private int pauseCnt = 0;
    private int resumeCnt = 0;
    private int msgCnt = 0;

    protected transient String creator = null;

    public String toString() {
        return "Producer["+ uid + "," + destination_uid + "," +
               connection_uid + "]";
    }
    
    public static Hashtable getAllDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("TABLE", "AllProducers");
        Vector v = new Vector();
        synchronized (cache) {
            Iterator itr = cache.keySet().iterator();
            while (itr.hasNext()) {
                v.add(String.valueOf(((ProducerUID)itr.next()).longValue()));
            }
            
        }
        ht.put("cache", v);
        HashMap<ProducerUID, ProducerSpi> tmp = null;
        synchronized(allProducers) {
            tmp = new HashMap<ProducerUID, ProducerSpi>(allProducers);
        }
        Hashtable producers = new Hashtable();
        Iterator<Map.Entry<ProducerUID, ProducerSpi>> itr = tmp.entrySet().iterator();
         Map.Entry<ProducerUID, ProducerSpi> pair = null;
        while(itr.hasNext()) {
            pair = itr.next();
            ProducerUID p = pair.getKey();
            ProducerSpi producer = pair.getValue();
            producers.put(String.valueOf(p.longValue()),
                  producer.getDebugState());
        }
        ht.put("producersCnt", Integer.valueOf(allProducers.size()));
        ht.put("producers", producers);
        return ht;
            
    }

    public synchronized void pause() {
        pauseCnt ++;
    }

    public synchronized void addMsg()
    {
        msgCnt ++;
    }

    public synchronized int getMsgCnt()
    {
        return msgCnt;
    }
    public synchronized boolean isPaused()
    {
        return pauseCnt > resumeCnt;
    }

    public synchronized void resume() { 
        resumeCnt ++;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("TABLE", "Producer["+uid.longValue()+"]");
        ht.put("uid", String.valueOf(uid.longValue()));
        ht.put("valid", String.valueOf(valid));
        ht.put("pauseCnt", String.valueOf(pauseCnt));
        ht.put("resumeCnt", String.valueOf(resumeCnt));
        if (connection_uid != null)
            ht.put("connectionUID", String.valueOf(connection_uid.longValue()));
        if (destination_uid != null)
            ht.put("destination", destination_uid.toString());
        return ht;
    }

    /** 
     */
    protected ProducerSpi(ConnectionUID cuid, DestinationUID duid, String id) {
        uid = new ProducerUID();
        this.connection_uid = cuid;
        this.destination_uid = duid;
        this.creator = id;
        logger.log(Logger.DEBUG,"Creating new Producer " + uid + " on "
             + duid + " for connection " + cuid);
    }

    public ProducerUID getProducerUID() {
        return uid;
    } 

    public ConnectionUID getConnectionUID() {
        return connection_uid;
    }

    public DestinationUID getDestinationUID() {
        return destination_uid;
    }

    /**
     */
    public static void clearProducers() {
        cache.clear();
        allProducers.clear();
        wildcardProducers.clear();
    }

	public abstract boolean isWildcard(); 

    public static Iterator getWildcardProducers() {
        return (new ArrayList(wildcardProducers)).iterator();
    }

    public static int getNumWildcardProducers() {
        return (wildcardProducers.size());
    }


    public static String checkProducer(ProducerUID uid)
    {
        String str = null;

        synchronized (cache) {
            str = (String)cache.get(uid);
        }
        if (str == null) {
             return " pid " + uid + " not of of last 20 removed";
        }
        return "Producer[" +uid + "]:" + str;
    }

    public static void updateProducerInfo(ProducerUID uid, String str)
    {
        synchronized (cache) {
            cache.put(uid, System.currentTimeMillis() + ":" + str);
        }
    }

    public static Iterator getAllProducers() {
        return (new ArrayList(allProducers.values())).iterator();
    }

    public static int getNumProducers() {
        return (allProducers.size());
    }

    public static ProducerSpi getProducer(ProducerUID uid) {
        return (ProducerSpi)allProducers.get(uid);
    }

    /** 
     */
    public static ProducerSpi destroyProducer(ProducerUID uid, String info) {
        ProducerSpi p = allProducers.remove(uid);
        updateProducerInfo(uid, info);
        if (p == null) {
            return p;
        }
        p.destroyProducer();
        return p;
    }

    protected abstract void destroyProducer();

    public synchronized void destroy() {
        valid = false;
    }

    public synchronized boolean isValid() {
        return valid;
    }

    public static ProducerSpi getProducer(String creator)
    {
        if (creator == null) return null;

        synchronized(allProducers) {
            Iterator<ProducerSpi> itr = allProducers.values().iterator();
            while (itr.hasNext()) {
                ProducerSpi c = itr.next();
                if (creator.equals(c.creator))
                    return c;
            }
        }
        return null;
    }

    public abstract Set getDestinations(); 

    static class ResumeFlowSizes {
         int size = 0;
         long bytes = 0;
         long mbytes = 0;

         public ResumeFlowSizes(int s, long b, long mb) {
             size = s;
             bytes = b;
             mbytes = mb;
         }
   }

    public void sendResumeFlow(DestinationUID duid, int maxbatch) {
        resume();
        sendResumeFlow(duid, 0, 0, 0, ("Resuming " + this), true, maxbatch);
        logger.log(Logger.DEBUGHIGH,"Producer.sendResumeFlow("+duid+") resumed: "+this);
    }

    public void sendResumeFlow(DestinationUID duid,
                               int size, long bytes, long mbytes,
                               String reason, boolean uselast, int maxbatch) {

        ResumeFlowSizes rfs = null;
        if (uselast) {
            rfs = (ResumeFlowSizes)lastResumeFlowSizes.get(duid);
            if (rfs == null) {
                rfs = new ResumeFlowSizes(maxbatch, -1, Limitable.UNLIMITED_BYTES);
                lastResumeFlowSizes.put(duid, rfs);
            }
        } else {
            rfs = new ResumeFlowSizes(size, bytes, mbytes);
            lastResumeFlowSizes.put(duid, rfs);
        }

        ConnectionUID cuid = getConnectionUID();
        if (cuid == null) {
            logger.log(Logger.DEBUG,"cant resume flow[no con_uid] " + this);
            return;
        }

        IMQConnection con =(IMQConnection)Globals.getConnectionManager()
                                                 .getConnection(cuid);

        if (reason == null) reason = "Resuming " + this;

        Hashtable hm = new Hashtable();
        hm.put("JMQSize", rfs.size);
        hm.put("JMQBytes", Long.valueOf(rfs.bytes));
        hm.put("JMQMaxMsgBytes", Long.valueOf(rfs.mbytes));
        if (con != null) {
            Packet pkt = new Packet(con.useDirectBuffers());
            pkt.setPacketType(PacketType.RESUME_FLOW);
            hm.put("JMQProducerID", Long.valueOf(getProducerUID().longValue()));
            hm.put("JMQDestinationID", duid.toString());
            hm.put("Reason", reason);
            pkt.setProperties(hm);
            con.sendControlMessage(pkt);
        }
    }

}
