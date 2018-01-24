/*
 * Copyright (c) 1997, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsclient;

import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;

import javax.transaction.xa.XAException;

import com.sun.messaging.jmq.util.XidImpl;

/**
 * This class keeps track of all the XAResourceImpl objects which are party to a given transaction branch
 * 
 * This class maintains a static Map
 * whose key is the transaction branch XID (a XidImpl)
 * and whose corresponding value is a Set of all the XAResourceImpl objects
 * which are being used in this transaction branch
 * 
 * A resource should be registered with this class by calling register(),
 * whenever XAResource.start(xid,TMNOFLAGS) or XAResource.start(xid,TMJOIN) is called
 * and removed from this class when the xid is committed or rolled back.
 * 
 * register() should never be called for XAResource.start(xid,TMRESUME) - this will cause an XAException
 * 
 * Note that the XA spec requires that when a second resource is added to an existing
 * xid, XAStart(xid,flags) is called with the join flag set
 * 
 */

public class XAResourceMap {

    private static final int DEFAULT_MAXROLLBACKS = 12;
    public static final int MAXROLLBACKS = getMAXROLLBACKS();

    private static final boolean DEFAULT_DMQ_ON_MAXROLLBACKS = false;
    public static final boolean DMQ_ON_MAXROLLBACKS = getDMQ_ON_MAXROLLBACKS(); 

    private static int getMAXROLLBACKS() {
        try {
            return Integer.parseInt(System.getProperty(
                "imq.maxConsecutiveRollbacks", String.valueOf(DEFAULT_MAXROLLBACKS)));
        } catch (Exception e) {
            e.printStackTrace();
            return DEFAULT_MAXROLLBACKS;
        }
    }

    private static boolean getDMQ_ON_MAXROLLBACKS() {
        return Boolean.valueOf(System.getProperty(
            "imq.dmqOnMaxConsecutiveRollbacks", String.valueOf(DEFAULT_DMQ_ON_MAXROLLBACKS)));
    }

    private static HashMap<XidImpl, Set<XAResourceImpl>> resourceMap = new HashMap<XidImpl, Set<XAResourceImpl>>();

    /**
     * A list of ConnectionConsumers that are interested to be notified
     * when a XAResource is unregistered 
     */
    private static ArrayList<ConnectionConsumerImpl> unregisterListenerCC = new ArrayList<ConnectionConsumerImpl>();
	
 /**
  * 
  * @param xid
  * @param xar
  * @param isJoin
  * @throws XAException
  */
    public synchronized static void register(XidImpl xid, XAResourceImpl xar, boolean isJoin) throws XAException{
		Set<XAResourceImpl> resources = resourceMap.get(xid);
		if (resources == null){
			// xid not found: check we are not doing a join
			if (isJoin) {
				XAException xae = new XAException("Trying to add an XAResource using the JOIN flag when no existing XAResource has been added with this XID");
				xae.errorCode = XAException.XAER_INVAL;
				throw xae;
			}
			resources = new HashSet<XAResourceImpl>();
			resourceMap.put(xid, resources);
		} else {
			// map already contains an entry for this xid: check we are doing a JOIN
			if (!isJoin){
				XAException xae = new XAException("Trying to add an XAResource to an existing xid without using the JOIN flag");
				xae.errorCode = XAException.XAER_DUPID;
				throw xae;
			}
		}	
		resources.add(xar);
	}	
	
	/**
	 * Unregister the specified transaction branch and all its resources
	 * 
	 * This should be called when the transaction is committed or rolled back
	 * 
	 * @param xid Transaction branch XID
	 */
	public synchronized static void unregister(XidImpl xid){
		
		// note that xid won't exist in the map if we obtained this xid using XAResource.recover(), 
		// so it is not an error if xid is not found
		
		resourceMap.remove(xid);

        if (unregisterListenerCC.size() > 0) {
            Iterator<ConnectionConsumerImpl> itr = unregisterListenerCC.iterator();
            ConnectionConsumerImpl cc = null;
            while (itr.hasNext()) {
                cc = itr.next();
                cc.unregisteredXAResource();
            }
        }
	}
	
	/**
	 * Unregister the specified resource from the specified transaction branch
	 * 
	 * This should be called when an individual session is closed
	 * but a transaction is still pending
	 * 
	 * @param xid Transaction branch XID
	 * @param xar Resource
	 */
	public synchronized static void unregisterResource(XAResourceImpl xar, XidImpl xid){

        if (unregisterListenerCC.size() > 0) {
            ConnectionConsumerImpl cc = xar.getConnectionConsumer();
            if (cc != null) {
                if (unregisterListenerCC.contains(cc)) {
                    cc.unregisteredXAResource();
                }
            }
        }
		Set<XAResourceImpl> resources = resourceMap.get(xid);
		if (resources!=null){
			resources.remove(xar);
			if (resources.size()==0){
				resourceMap.remove(xid);
			}
		}
	}
	
	/**
	 * Returns a Set of resources associated with the specified transaction branch
	 * @param xid Transaction branch XID
	 * @param throwExceptionIfNotFound Whether to throw an exception if XID not found
	 *        (typically set to true on a commit, false on a rollback, 
	 *        since a commit legitimately be followed by a rollback)
	 * @return Resources associated with the specified transaction branch
	 * @throws XAException Unknown XID (only thrown if throwExceptionIfNotFound=true)
	 */
	public synchronized static XAResourceImpl[] getXAResources(XidImpl xid, boolean throwExceptionIfNotFound) throws XAException{
		Set<XAResourceImpl> resources = resourceMap.get(xid);
		if (resources==null){
			if (throwExceptionIfNotFound){
				XAException xae = new XAException("Unknown XID (was start() called?");
				throw xae;	
			} else {
				return new XAResourceImpl[0];
			}
		
		}
		return resources.toArray(new XAResourceImpl[resources.size()]);
	}
	
	/**
	 * Return whether the resources map is empty
	 * 
	 * This is for use by tests
	 * 
	 * @return
	 */
	public static boolean isEmpty(){
		return resourceMap.isEmpty();
	}

    /**
     * check to see if any unregistered XAResources associated to a ConnectionConsumer
     *
     * @param cc the ConnectionConsumer to check for
     * @return number of registered XAResources associated to cc
     *
     */
    public static int hasXAResourceForCC(ConnectionConsumerImpl cc) {
        return hasXAResourceForCC(cc, true);
    }

    /**
     * check to see if any unregistered XAResources associated to a ConnectionConsumer
     *
     * @param cc the ConnectionConsumer to check for
     * @param listen if true register cc as a XAResource unregister-listener
     *
     * @return number of registered XAResources associated to cc
     *
     */
    public static int hasXAResourceForCC(ConnectionConsumerImpl cc, boolean listen) {
        XidImpl[] keys = null;
        synchronized(XAResourceMap.class) {
		    keys = (XidImpl[])resourceMap.keySet().toArray(
                               new XidImpl[resourceMap.size()]);
        }
        ArrayList<XAResourceImpl> allxars = new ArrayList<XAResourceImpl>();
        Set<XAResourceImpl> xarss = null;
        for (int i = 0; i < keys.length; i++) { 
            synchronized(XAResourceMap.class) {
                xarss = resourceMap.get(keys[i]);
                if (xarss == null) continue;
                allxars.addAll(xarss);
            }
        }
         
        XAResourceImpl[] xars = (XAResourceImpl[])allxars.toArray(new XAResourceImpl[0]);  
        int cnt = 0;
        XAResourceImpl xar = null;
        ConnectionConsumerImpl xcc = null;
        for (int i = 0; i < xars.length; i++) {
            xar = xars[i];
            xcc = xar.getConnectionConsumer();
            if (xcc != null && xcc == cc) {
                cnt++;
            }
        }

        if (!listen) {
            return cnt;
        }

        if (cnt > 0) {
            addUnregisterListenerCC(cc);
        } else {
            removeUnregisterListenerCC(cc);
        }
        return cnt;
    }

    /**
     * add a ConnectionConsumer XAResource unregister-listener
     *
     * @param cc the ConnectionConsumer to add to the unregister-listeners 
     *
     */
    public static synchronized void addUnregisterListenerCC(ConnectionConsumerImpl cc) {
        unregisterListenerCC.add(cc);
    }

    /**
     * remove a ConnectionConsumer XAResource unregister-listener
     *
     * @param cc the ConnectionConsumer to remove from the unregister-listeners 
     *
     */
    public static synchronized void removeUnregisterListenerCC(ConnectionConsumerImpl cc) {
        unregisterListenerCC.remove(cc);
    }

}

