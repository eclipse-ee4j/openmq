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
 * @(#)Topic.java	1.57 11/16/07
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.jmsserver.util.FeatureUnavailableException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.lists.*;
import com.sun.messaging.jmq.jmsserver.util.lists.*;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.util.selector.*;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.util.DestMetricsCounters;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;


/**
 * This class represents a topic destination
 */
public class Topic extends Destination
{

    static final long serialVersionUID = -5748515630523651753L;

    private static boolean DEBUG = false;


    private transient Map selectorToInterest;
    private transient List selectors;

    private transient Map remoteConsumers;
    private static int TOPIC_DEFAULT_PREFETCH = Globals.getConfig().
                  getIntProperty(Globals.IMQ+
                 ".autocreate.topic.consumerFlowLimit", 1000);

    private boolean hasNoLocalConsumers = false;

    int maxSharedConsumers = 0;
    int sharedPrefetch = 0;

    public static final String MAX_SHARE_CONSUMERS = "max_shared_consumers";
    public static final String SHARED_PREFETCH = "sharedPrefetch";


    public static final int AUTO_MAX_SHARED_CONSUMER_LIMIT =
            Globals.getConfig().getIntProperty(
                Globals.IMQ + ".autocreate.topic.maxNumSharedConsumers",
                -1);

    public static final int AUTO_MAX_SHARED_FLOW_LIMIT =
            Globals.getConfig().getIntProperty(
                Globals.IMQ + ".autocreate.topic.sharedConsumerFlowLimit",
                5);
    public static final int ADMIN_MAX_SHARED_CONSUMER_LIMIT =
            Globals.getConfig().getIntProperty(
                Globals.IMQ + ".admincreate.topic.maxNumSharedConsumers",
                -1);

    public static final int ADMIN_MAX_SHARED_FLOW_LIMIT =
            Globals.getConfig().getIntProperty(
                Globals.IMQ + ".admincreate.topic.sharedConsumerFlowLimit",
                5);

    public Hashtable getDebugState() {
        Hashtable ht = super.getDebugState();
        Hashtable sel = new Hashtable();
        synchronized(selectorToInterest) {
            Iterator itr = selectorToInterest.keySet().iterator();
            while (itr.hasNext()) {
               Selector selector = (Selector)itr.next();
               Set s = (Set)selectorToInterest.get(selector);
               Vector v = new Vector();
               synchronized (s) {
                   Iterator itr1 = s.iterator();
                   while (itr1.hasNext()) {
                       Consumer c =(Consumer)itr1.next();
                       v.add(String.valueOf(c.getConsumerUID().longValue()));
                   }
               }
               sel.put((selector == null ? "no selector" : selector.toString()), v);
            }
         }
         ht.put("selectorInfo", sel);
         ht.put(MAX_SHARE_CONSUMERS, Integer.valueOf(maxSharedConsumers));
         ht.put(SHARED_PREFETCH, Integer.valueOf(sharedPrefetch));
         return ht;
    }
   
    // used only as a space holder when deleting corrupted destinations 
    protected Topic(DestinationUID uid)
    {
        super(uid);
    }           
        
    protected Topic(String destination, int type, 
           boolean store, ConnectionUID id, boolean autocreate,  DestinationList dl) 
        throws FeatureUnavailableException, BrokerException, IOException
    {
        super(destination,type,store, id, autocreate, dl);

        maxPrefetch = TOPIC_DEFAULT_PREFETCH;

        if (autocreate) {
            maxSharedConsumers=AUTO_MAX_SHARED_CONSUMER_LIMIT;
            sharedPrefetch=AUTO_MAX_SHARED_FLOW_LIMIT;
        } else {
            maxSharedConsumers=ADMIN_MAX_SHARED_CONSUMER_LIMIT;
            sharedPrefetch=ADMIN_MAX_SHARED_FLOW_LIMIT;
        }
    }

    protected void initVar() {
       selectorToInterest = new HashMap();
       selectors = new ArrayList();
       remoteConsumers = new HashMap();
    }

    public void unload(boolean refs) {
        super.unload(refs);
        if (refs) {
            // remove the refs
            Iterator i = consumers.values().iterator();
            while (i.hasNext()) {
                Consumer con = (Consumer)i.next();
                con.unloadMessages();
            }
        }
    } 

    public int getUnackSize() {
        return getUnackSize(null);
    }
    public int getUnackSize(Set msgset) {
        Set s = msgset;
        if (s == null) {
            synchronized (destMessages) {
                s = new HashSet(destMessages.values());
            }
        }
        int counter = 0;
        Iterator itr = s.iterator();
        while (itr.hasNext()) {
            PacketReference ref = (PacketReference)itr.next();
            if (!ref.isInvalid() && !ref.isDestroyed() &&
                 (ref.getDeliverCnt() -
                  ref.getCompleteCnt() > 0)) {
                counter ++;
            }
        }
        return counter;
    }

    public Set routeAndMoveMessage(PacketReference oldRef, 
                  PacketReference newRef)
	throws IOException, BrokerException
    {
        throw new RuntimeException("XXX not implemented");
    }


    public boolean queueMessage(PacketReference pkt, boolean trans) 
        throws BrokerException {

        // if there are no consumers, throw it away
        if (!trans && consumers.size() == 0) {
            return false;
        }
        return super.queueMessage(pkt, trans);
    }


    /**
     * handles transient data when class is deserialized
     */
    private void readObject(java.io.ObjectInputStream ois)
        throws IOException, ClassNotFoundException
    {
        initVar();
        ois.defaultReadObject();
    }


    public void eventOccured(EventType type,  Reason reason,
            Object source, Object OrigValue, Object NewValue, 
            Object userdata)
    {
        super.eventOccured(type,reason, source, 
              OrigValue, NewValue, userdata);
    }

    /**
     * @deprecated
     */
    public void routeNewMessage(SysMessageID  id) 
         throws BrokerException, SelectorFormatException
    {
        PacketReference ref = (PacketReference)destMessages.get(id);
        Set s = routeNewMessage(ref);
        forwardMessage((Set<Consumer>)s, ref);
    }

    public int getConsumerCount() {
        int count =  super.getConsumerCount();
        // get 3.0 remote consumer count
        synchronized(remoteConsumers) {
            Iterator itr = remoteConsumers.values().iterator();
            while (itr.hasNext()) {
                RemoteConsumer rc = (RemoteConsumer)itr.next();
                count += rc.getConsumerCount();
           }
        }
        return count;
    }

    /*
    private Set matchRemoteConsumers(PacketReference msg, Set s)
         throws BrokerException, SelectorFormatException
    {
       
        synchronized(remoteConsumers) {
            if (remoteConsumers.isEmpty()) {
                return s;
            }
            Set newset = new HashSet(s);
            Iterator itr = remoteConsumers.values().iterator();
            while (itr.hasNext()) {
                RemoteConsumer rc = (RemoteConsumer)itr.next();
                if (rc.match(msg, newset)) {
                    s.add(rc);
                }
                
            }
            return newset;
        }
    }
    */

    protected ConsumerUID[] routeLoadedTransactionMessage(
           PacketReference msg)
         throws BrokerException, SelectorFormatException
    {
        Set matching = new HashSet();

        Map props = null;
        Map headers = null;

        synchronized(selectorToInterest) {
            Iterator itr = selectorToInterest.keySet().iterator();
            while (itr.hasNext()) {
               Selector selector = (Selector)itr.next();
            
               if (selector != null) {
                   if (props == null && selector.usesProperties()) {
                       try {
                           props = msg.getProperties();
                       } catch (ClassNotFoundException ex) {
                           logger.logStack(Logger.ERROR,"INTERNAL ERROR", ex);
                           props = new HashMap();
                       }
                   }
                   if (headers == null && selector.usesFields()) {
                       headers = msg.getHeaders();
                   }
               }
                   
               boolean matches = (selector == null) ||
                   selector.match(props, headers);
               if (matches) {
                   if (DEBUG) {
                       logger.log(Logger.INFO,"Selector " + selector 
                          + " Matches " + msg.getSysMessageID());
                   }
                   Set s = (Set)selectorToInterest.get(selector);
                   if (s == null) continue;
                   synchronized (s) {
                       matching.addAll(s);
                   }
               }
            }
        }

        // deal w/ isLocal

        // OK .. the logic is a little different than previous releases
        //
        // If we are a subscription and have a ClientID ... compare clientID's
        // otherwise
        // Compare ConsumerUIDs

        HashSet hs = new HashSet();
        Iterator nlitr = matching.iterator();
        ConnectionUID pcuid = msg.getProducingConnectionUID();
        String clientid = msg.getClientID();
        while (nlitr.hasNext()) {
            Consumer c = (Consumer)nlitr.next();
            if (c.getNoLocal()) {
                // fix for 5025241 Durable subscriber with noLocal=true 
                //         receives self-published msgs
                if (c instanceof Subscription && clientid != null &&
                    ((Subscription)c).getClientID() != null &&
                    ((Subscription)c).getClientID().equals(clientid)) {
                    // check subscription clientID case
                    nlitr.remove();
                } else if (c.getConsumerUID().getConnectionUID() ==
                       pcuid) {
                    nlitr.remove();
                } else {
                    hs.add(c.getConsumerUID());
                }
            } else {
                hs.add(c.getConsumerUID());
            }
        }
        return (ConsumerUID[])hs.toArray(new ConsumerUID[hs.size()]);
    }
    
    
    public void unrouteLoadedTransactionAckMessage(PacketReference ref,
			ConsumerUID consumerId) throws BrokerException {
		logger.log(Logger.DEBUG,
				"unrouteLoadedTransactionAckMessage num consumers = "
						+ consumers.size());
		// do nothing as consumers not attatched yet?
		// need to check this
		// what happens when consumer attatches?
		Consumer consumer = getConsumer(consumerId);
		if (consumer == null) {
			logger.log(Logger.DEBUG, "could not find consumer for "
					+ consumerId);
		} else
			consumer.unrouteMessage(ref);
	}

    public void routeNewMessageWithDeliveryDelay(PacketReference  msg) 
    throws BrokerException, SelectorFormatException {
        routeNewMessage(msg);
    }

    public Set routeNewMessage(PacketReference  msg) 
         throws BrokerException, SelectorFormatException
    {
        Set matching = new HashSet();

        Map props = null;
        Map headers = null;

        for (int i=0; i < selectors.size(); i ++) {
            Selector selector = null;
            try {
                //LKS-XXX NOTE: don't need selectors !!!
                selector = (Selector)selectors.get(i);
            } catch (Exception ex) {
                continue; // selector was removed
            }
            if (selector == null ) {
                Set s = (Set)selectorToInterest.get(null);
                if (s == null) continue;
                synchronized (s) {
                    matching.addAll(s);
                }
            } else {
                if (props == null && selector.usesProperties()) {
                   try {
                       props = msg.getProperties();
                   } catch (ClassNotFoundException ex) {
                       logger.logStack(Logger.ERROR,"INTERNAL ERROR", ex);
                       props = new HashMap();
                   }
                }
                if (headers == null && selector.usesFields()) {
                    headers = msg.getHeaders();
                }
                if (selector.match(props, headers)) {
                       Set s = (Set)selectorToInterest.get(selector);
                       if (s != null) {
                           synchronized(s) {
                               matching.addAll(s);
                           }
                       }
                }
           }
        }

        // deal w/ isLocal
        if (hasNoLocalConsumers) {
            Iterator nlitr = matching.iterator();
            ConnectionUID pcuid = msg.getProducingConnectionUID();
            String clientid = msg.getClientID();
            while (nlitr.hasNext()) {
                Consumer c = (Consumer)nlitr.next();
                if (c.getNoLocal()) {
                    // fix for 5025241 Durable subscriber with noLocal=true 
                    //         receives self-published msgs
                    if (c instanceof Subscription && clientid != null &&
                        ((Subscription)c).getClientID() != null &&
                        ((Subscription)c).getClientID().equals(clientid)) {
                        // check subscription clientID case
                        nlitr.remove();
                    } else if (c.getConsumerUID().getConnectionUID() ==
                       pcuid) {
                        nlitr.remove();
                    }
                }
             }
        }
        if (matching.isEmpty()) {
            removeMessage(msg.getSysMessageID(), RemoveReason.ACKNOWLEDGED); 
            return null;
        } else {
            msg.store(matching);
        }

        return matching;
   }
    
    /**
     * @param msg
     * @param forStoreOnly specifies the routing info is for storage only, 
     * so only need to apply selectors for consumers that  
     * @return
     * @throws BrokerException
     * @throws SelectorFormatException
     */
    public Set routeMessage(PacketReference msg, boolean forStoreOnly) throws BrokerException,
			SelectorFormatException {
		Set matching = new HashSet();

		Map props = null;
		Map headers = null;

		for (int i = 0; i < selectors.size(); i++) {
			Selector selector = null;
			try {
				// LKS-XXX NOTE: don't need selectors !!!
				selector = (Selector) selectors.get(i);
			} catch (Exception ex) {
				continue; // selector was removed
			}
			if (selector == null) {
				Set s = (Set) selectorToInterest.get(null);
				if (s == null)
					continue;
				synchronized (s) {
					matching.addAll(s);
				}
			} else {
				
				
				if (props == null && selector.usesProperties()) {
					try {
						props = msg.getProperties();
					} catch (ClassNotFoundException ex) {
						logger.logStack(Logger.ERROR, "INTERNAL ERROR", ex);
						props = new HashMap();
					}
				}
				if (headers == null && selector.usesFields()) {
					headers = msg.getHeaders();
				}
				Set s = (Set) selectorToInterest.get(selector);
				if(s==null)
					continue;
				
				if (forStoreOnly) {
					// optimisation...
					// with this option, no need to match selector if no
					// consumers need storing

					boolean needsStoring = false;
					synchronized (s) {
						Iterator iter = s.iterator();
						while (iter.hasNext()) {
							Consumer consumer = (Consumer) iter.next();
							if (consumer != null
									&& consumer.getStoredConsumerUID().shouldStore) {
								// at least one of the consumers needs storing,
								// so
								// we will need to check the selector
								needsStoring = true;
								break;
							}
						}
						if (!needsStoring) {
							continue;
						}
					}
				}
				
				if (selector.match(props, headers)) {					
					synchronized (s) {
						matching.addAll(s);
					}
					
				}
			}
		}

		// deal w/ isLocal
		if (hasNoLocalConsumers) {
			Iterator nlitr = matching.iterator();
			ConnectionUID pcuid = msg.getProducingConnectionUID();
			String clientid = msg.getClientID();
			while (nlitr.hasNext()) {
				Consumer c = (Consumer) nlitr.next();
				if (c.getNoLocal()) {
					// fix for 5025241 Durable subscriber with noLocal=true
					// receives self-published msgs
					if (c instanceof Subscription
							&& clientid != null
							&& ((Subscription) c).getClientID() != null
							&& ((Subscription) c).getClientID()
									.equals(clientid)) {
						// check subscription clientID case
						nlitr.remove();
					} else if (c.getConsumerUID().getConnectionUID() == pcuid) {
						nlitr.remove();
					}
				}
			}
		}		
		return matching;
	}
    
    public ConsumerUID[] calculateStoredInterests(PacketReference ref)
			throws BrokerException, SelectorFormatException {
		// this will return null if message is not persistent

		ConsumerUID[] storedInterests = null;
		try {

			Collection allConsumers = routeMessage(ref, true);
			storedInterests = ref.getRoutingForStore(allConsumers);

		} catch (BrokerException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			throw new BrokerException(ex.toString(), ex);
		}
		return storedInterests;
	}
    
    public void forwardOrphanMessages(Collection refs,
                  ConsumerUID consumer)
         throws BrokerException
    {
        BrokerException ex = new BrokerException("INTERNAL ERROR: Unexpected call");
        logger.logStack(Logger.ERROR, ex.getMessage(), ex);
        throw ex;
    }

    /* called from transaction code */
    public void forwardOrphanMessage(PacketReference ref,
                  ConsumerUID consumer)
         throws BrokerException
    {
        Consumer c = getConsumer(consumer);
        if (c == null) {
            // no more consumer -> ack us
        	 logger.log(Logger.DEBUG, "Could not find " + consumer + " in " + getName() + "size="+consumers.size());
        	 Iterator iter = consumers.keySet().iterator();
        	 while (iter.hasNext())
        	 {
        		 Object v = iter.next();
        		 logger.log(Logger.DEBUG, "consumer= " + v);
             	
        	 }
             
            logger.log(Logger.DEBUG,"Dumping orphan message " + ref);
            try {
                if (ref.acknowledged(consumer,
                        consumer, false, false)) {
                    try {
                        removeMessage(ref.getSysMessageID(), 
                                      RemoveReason.ACKNOWLEDGED);
                    } finally {
                        ref.postAcknowledgedRemoval();
                    }
                }
            } catch (Exception ex) {
                logger.logStack(Logger.DEBUG,"Error forwarding orphan", ex);
            }
        }
  
        Set<Consumer> matches = new HashSet<Consumer>();
        matches.add(c);
        forwardMessage(matches, ref, false, (ref.getOrder() != null));
    }


   public void forwardMessage(Set matching, PacketReference msg)
   throws BrokerException { 
       forwardMessage((Set<Consumer>)matching, msg, false, false);
   }

   public void forwardDeliveryDelayedMessage(
                      Set<ConsumerUID> matching,
                      PacketReference msg)
                      throws BrokerException { 

       Set<Consumer> targets = null;

       if (matching != null) {
           Consumer c = null;
           ConsumerUID cuid = null;
           Iterator<ConsumerUID> itr = matching.iterator();
           while (itr.hasNext()) {
               cuid = itr.next();
               c = getConsumer(cuid);
               if (c == null || !c.isValid()) {
                   continue;
               }
               if (targets == null) {
                   targets = new HashSet<Consumer>();
               }
               targets.add(c);
           }
       }
       forwardMessage(targets, msg, false, false);
   }

   private void forwardMessage(Set<Consumer> matching, PacketReference msg,
                               boolean toFront, boolean ordered)
                               throws BrokerException { 

        Set remote = null;

        if (matching == null || matching.isEmpty()) {
            removeMessage(msg.getSysMessageID(), RemoveReason.ACKNOWLEDGED); 
        } else {
            Iterator<Consumer> con_itr = matching.iterator();
            while (con_itr.hasNext()) {
                   Consumer c = con_itr.next();
                   // OK .. this is ugly
                   // I really wanted to treat the remote consumers the
                   // same as local consumers (merging topics)
                   // but I dont want to send multiple copies of the
                   // message for 3.0 clients PLUS there looks like
                   // there is a timing hole in 3.0 where a message
                   // arriving late (e.g. durable) could be removed
                   // without tracking acks IF it was received just
                   // as the last consumer was acking it
                   // when we only send over 1 interest to deal w/ the
                   // ack flood, this should improve (maybe raptor
                   // but probably 3.6)
                   //
                   // For now .. I'm handling the non-durable clients seperately
                   //
                   if (c.isFalconRemote()) { // message to a remote broker
                       if (remote == null) {
                           remote = new HashSet();
                       }
                       remote.add(c);
                   } else {
                     
                       if (!c.routeMessage(msg, false, ordered)) {
                           boolean acked = false;
                           try {
                               ConsumerUID cid = c.getConsumerUID();
                               acked = msg.acknowledged(cid, 
                                     c.getStoredConsumerUID(),
                                     !cid.isUnsafeAck(), true);
                               if (acked) {
                                   try {
                                       removeMessage(msg.getSysMessageID(), null);
                                   } finally {
                                       msg.postAcknowledgedRemoval();
                                   }
                               }
                           } catch (IOException ex) {
                              //XXX ?? 
                           }
                       }
                   }
           }
        }
        if (remote != null && !remote.isEmpty()) {
            // send the messages directly to the remote broker
            // do not pass go, do not collect $200, do not
            // deal w/ flow control
            Globals.getClusterBroadcast().forwardMessage(msg, remote);
        }
    }       

    public Consumer addConsumer(Consumer c, boolean notify, Connection conn) 
        throws BrokerException, SelectorFormatException {
        return addConsumer(c, notify, conn, true);
    }

    public Consumer addConsumer(Consumer c, boolean notify, 
        Connection conn, boolean loadIfActive) 
        throws BrokerException, SelectorFormatException {

        if (c instanceof Subscription) {
            if (consumers.get(c.getConsumerUID()) != null) {
                return null;
            }
        }
        super.addConsumer(c, notify, conn, loadIfActive);
        hasNoLocalConsumers |= c.getNoLocal();

        Selector selector = c.getSelector();
        Set s = null;

        synchronized (selectorToInterest) {
            s = (Set)selectorToInterest.get(selector);
            if ( s == null) {
                s = new HashSet();
                selectorToInterest.put(selector,s);
                selectors.add(selector);
            }
        }
        synchronized (s) {
            s.add(c);
        }

        if (!(c instanceof Subscription)) {
            notifyConsumerAdded(c, conn);
        }

        return null; 
    }

    public void removeConsumer(ConsumerUID interest, boolean notify)
        throws BrokerException {
        removeConsumer(interest, null, false, notify);
    }

    public void removeConsumer(ConsumerUID interest, Map remotePendings, 
                               boolean remoteCleanup, boolean notify)
                               throws BrokerException 
    {
        Consumer c = (Consumer)consumers.get(interest);
        if (c == null) {
            return; 
        }

        Set s = null;
        synchronized (selectorToInterest) {
            s = (Set)selectorToInterest.get(c.getSelector());
            if (s != null) {
                synchronized (s) {
                    s.remove(c);
                    if (s.isEmpty()) {
                        selectorToInterest.remove(c.getSelector());
                        selectors.remove(c.getSelector());
                    }
                }
            }
        }
        super.removeConsumer(interest, remotePendings, remoteCleanup, notify);

        if (!(c instanceof Subscription)) {
            notifyConsumerRemoved();
        }
    }


    public void sort(Comparator c) {
    }
 

    protected void getDestinationProps(Map m) {
        super.getDestinationProps(m);
        m.put(MAX_SHARE_CONSUMERS,Integer.valueOf(maxSharedConsumers));
        m.put(SHARED_PREFETCH, Integer.valueOf(sharedPrefetch));
    }

    @Override
    public void setDestinationProperties(Map m) 
    throws BrokerException {

        super.setDestinationProperties(m);
        if (m.get(MAX_SHARE_CONSUMERS) != null) {
           try {
               setMaxSharedConsumers(((Integer)m.get(
                     MAX_SHARE_CONSUMERS)).intValue());
           } catch (Exception ex) {
               logger.logStack(Logger.WARNING, "setMaxSharedConsumers()", ex);
           }

        }
        if (m.get(SHARED_PREFETCH) != null) {
           try {
               setSharedFlowLimit(((Integer)m.get(
                     SHARED_PREFETCH)).intValue());
           } catch (Exception ex) {
               logger.logStack(Logger.WARNING, "setSharedFlowLimit()", ex);
           }

        }
     }
    public DestMetricsCounters getMetrics() {
        DestMetricsCounters dmc = super.getMetrics();
        return dmc;

        // TBD add MAX_SHARE_CONSUMERS, SHARED_PREFETCH
    }

    
    @Override
    public void setMaxSharedConsumers(int max) {
       maxSharedConsumers = max;
    }
    public void setSharedFlowLimit(int prefetch) {
       sharedPrefetch = prefetch;
    }

    @Override
    public int getMaxNumSharedConsumers() {
        return maxSharedConsumers;
    }
    public int getSharedConsumerFlowLimit() {
        return sharedPrefetch;
    }

}
