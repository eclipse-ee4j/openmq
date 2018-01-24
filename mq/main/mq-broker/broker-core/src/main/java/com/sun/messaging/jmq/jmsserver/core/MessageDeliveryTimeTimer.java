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

package com.sun.messaging.jmq.jmsserver.core;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.Collection;
import java.util.LinkedHashSet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.jmsserver.util.lists.RemoveReason;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.timer.WakeupableTimer;
import com.sun.messaging.jmq.util.timer.TimerEventHandler;

public class MessageDeliveryTimeTimer implements TimerEventHandler {

     protected static boolean DEBUG = getDEBUG();

     private Logger logger = Globals.getLogger();
     private BrokerResources br = Globals.getBrokerResources();

     private SortedSet<MessageDeliveryTimeInfo> messages = null;
     private WakeupableTimer mytimer = null;
     private String startLogString = null;
     private String exitLogString = null;
     private Destination destination = null;
     private DestinationList DL = Globals.getDestinationList();

     private boolean destroyed = false;

     private static boolean getDEBUG() {
         if (Destination.DEBUG ||
             Globals.getLogger().getLevel() <= Logger.DEBUG) {
             return true;
         }
         return false;
     }

     public MessageDeliveryTimeTimer(Destination d) {
         this.destination = d;
         this.messages = new TreeSet<MessageDeliveryTimeInfo>(
                             MessageDeliveryTimeInfo.getComparator());

         this.startLogString = br.getKString(br.I_MSG_DELIVERY_TIME_TIMER_START,
                                   d.getDestinationUID());
         this.exitLogString = br.getKString(br.I_MSG_DELIVERY_TIME_TIMER_EXIT,
                                  d.getDestinationUID());
     }

     public String toString() {
         return "[DeliveryDelayTimer]"+destination.getDestinationUID();
     }

     public void addMessage(MessageDeliveryTimeInfo di) {
         if (DEBUG) {
             logger.log(logger.INFO, "DeliveryTimeTimer.addMessage("+di+")");
         }
         long dtime = di.getDeliveryTime();
         boolean notify = di.isDeliveryReady();
         if (!notify) {
             di.setDeliveryReadyListener(this);
         }
         di.setOnTimerState();
         notify = di.isDeliveryReady();
         synchronized(this) {
             if (destroyed) {
                 return;
             }
             if (notify && messages.size() > 0) {
                 MessageDeliveryTimeInfo first = messages.first();
                 if (dtime > first.getDeliveryTime()) {
                     notify = false;
                 }
             }
             messages.add(di);
             if (mytimer == null) {
                 addTimer();
             }
             if (notify) {
                 mytimer.wakeup(dtime);
             }
         }
     }

     protected void deliveryReady(MessageDeliveryTimeInfo di) {
         boolean notify = true;
         long dtime = di.getDeliveryTime(); 
         synchronized(this) {
             if (destroyed) {
                 return;
             }
             if (messages.size() > 0) {
                 MessageDeliveryTimeInfo first = messages.first();
                 if (dtime > first.getDeliveryTime()) {
                     notify = false;
                 }
             }
             if (notify) {
                 mytimer.wakeup(dtime);
             }
         }
     }

     public synchronized void removeMessage(MessageDeliveryTimeInfo di) {
         boolean b = messages.remove(di);
         if (DEBUG && b) {
             logger.log(logger.INFO, 
             "Removed message "+di+" from delivery delay timer "+this);
         }

     }

     public synchronized void destroy() {
         if (mytimer != null) {
             removeTimer();
         }
         messages.clear();            
         destroyed = true;
     }
 
     public int getSizeInfo(Set msgset, DestinationInfo dinfo) {
         Set<MessageDeliveryTimeInfo> s = null;
         synchronized(this) {
             if (messages.size() == 0) {
                 return 0;
             }
             s = new HashSet(messages);
         }
         List<MessageDeliveryTimeInfo> indelays = 
              new ArrayList<MessageDeliveryTimeInfo>();
         int cnt = 0;
         MessageDeliveryTimeInfo di = null;
         Iterator<MessageDeliveryTimeInfo> itr = s.iterator();
         while (itr.hasNext()) {
             di = itr.next();
             if (di.getOnTimerState() == Boolean.TRUE) {
                 cnt++;
                 if (msgset != null) {
                     indelays.add(di);
                     continue;
                 }
                 if (dinfo != null) {
                     dinfo.nInDelayMessages++;
                 }
             }
         }
         if (msgset == null) {
             return cnt;
         }

         cnt = 0;
         PacketReference ref = null;
         Iterator itr1 = msgset.iterator();
         while (itr1.hasNext()) {
             ref = (PacketReference)itr1.next();
             if (indelays.contains(new MessageDeliveryTimeInfo(
                                   ref.getSysMessageID(), 1L))) {
                 cnt++;
                 if (dinfo != null) {
                     dinfo.nInDelayMessages++;
                     dinfo.nInDelayMessageBytes += ref.getSize();
                 }
             }
         }
         return cnt;
     }

     private void addTimer() {
         try {
             mytimer = new WakeupableTimer("MessageDeliveryTimeTimer",  
                               this, 0L, 0L, startLogString, exitLogString);
         } catch (Exception ex) {
            logger.logStack(Logger.ERROR, br.getKString(
                br.X_MSG_DELIVERY_TIME_TIMER_START_FAIL,
                destination.getDestinationUID()), ex);
         }
     }

     private void removeTimer() {
         try {
             if (mytimer != null) {
                 mytimer.cancel();
             }
         } catch (IllegalStateException ex) {
             logger.logStack(Logger.DEBUG, "Exception on cancel "+this, ex);
         }
     }

     protected void routeTransactedMessage(PacketReference ref)
     throws BrokerException {

         MessageDeliveryTimeInfo di = ref.getDeliveryTimeInfo();
         try {
             destination.routeNewMessageWithDeliveryDelay(ref);
             di.setDeliveryReady();
         } catch (Exception e) {
             String emsg = br.getKString(
                 br.X_ROUTE_DELIVERY_TIME_ARRIVED_COMMITTED_MSG,
                 ref, destination.getDestinationUID());
             logger.logStack(logger.ERROR, emsg, e);
             throw new BrokerException(emsg, e);
         }
     }

     protected void consumerClosed(Consumer c) {
         if (destination.isQueue()) {
             return;
         }
         if (!(c instanceof Subscription) && 
             c.getSubscription() != null) {
             return;
         }
         if (DEBUG) {
             logger.log(logger.INFO, "Processing delivery delayed messages in destination "+
                        destination.getDestinationUID()+" on closing consumer "+c);
         }
         TreeSet<MessageDeliveryTimeInfo> s = null;
         synchronized(this) {
             s = new TreeSet(messages);
         }
         int cnt = 0;
         PacketReference ref = null;
         MessageDeliveryTimeInfo di = null;
         Iterator<MessageDeliveryTimeInfo> itr = s.iterator();
         while (itr.hasNext()) {
             di = itr.next();
             ref = DL.get(destination.getPartitionedStore(), di.getSysMessageID());
             if (ref == null || ref.isExpired()) {
                 continue;
             }
             if (!di.setInProcessing(true)) {
                 continue;
             }
             try {
                 if (ref.removeConsumerForDeliveryDelayed(c)) {
                     try {

                     if (DEBUG) {
                         logger.log(logger.INFO, 
                         "Removing message "+di+ " in destination "+
                         destination.getDestinationUID()+" on closing consumer ["+
                         c.getConsumerUID()+":"+c.getStoredConsumerUID()+"]");
                     }
                     destination.removeMessage(ref.getSysMessageID(), 
                                               RemoveReason.REMOVED_OTHER);
                     } finally {
                         ref.postAcknowledgedRemoval();
                     }
                     cnt++;
                 }
             } catch (Exception e) {
                 Object[] args = { ref, destination.getDestinationUID(),
                                   "["+c.getConsumerUID()+":"+c.getStoredConsumerUID()+"]" };
                 logger.logStack(logger.WARNING, br.getKString(
                     br.X_PROCESSING_DELIVERY_DELAYED_MSG_ON_CONSUMER_CLOSE, args), e);
             } finally {
                 di.setInProcessing(false);
             }
         }
         if (cnt > 0) {
             Object[] args = { String.valueOf(cnt), destination.getDestinationUID(),
                               "["+c.getConsumerUID()+":"+c.getStoredConsumerUID()+"]" };
             logger.log(logger.INFO, br.getKString(
                 br.I_RM_DELIVERY_DELAYED_MSGS_ON_CONSUMER_CLOSE, args));
         }
     }

     public long runTask() {
         LinkedHashSet<MessageDeliveryTimeInfo> dues = 
                       new LinkedHashSet<MessageDeliveryTimeInfo>();
         //DestinationUID duid = destination.getDestinationUID();
         MessageDeliveryTimeInfo di = null;
         int count = 0;
         synchronized(this) {
             Iterator<MessageDeliveryTimeInfo> itr = messages.iterator();
             while (itr.hasNext()) {
                 di = itr.next();
                 if (!di.isDeliveryReady()) {
                     continue;
                 }
                 if (!di.isDeliveryDue() || 
                     count > destination.getMaxPrefetch()) {
                     break;
                 }
                 if (!di.setInProcessing(true)) {
                     continue;
                 }
                 dues.add(di);
                 count++;
             }
         }
         if (count > 0) {
             logger.log(logger.INFO, br.getKString(
                 br.I_MSGS_DELIVERY_TIME_ARRIVED, Integer.valueOf(count), 
                     destination.getDestinationUID()));
         }

         count = 0;
         Iterator<MessageDeliveryTimeInfo> itr = dues.iterator();
         PacketReference ref = null;
         while (itr.hasNext()) {
             di = itr.next();
             synchronized(this) {
                 messages.remove(di);
             }
             di.setOffTimerState();
             ref = DL.get(destination.getPartitionedStore(), di.getSysMessageID());
             if (ref == null || ref.isDestroyed() || ref.isInvalid()) {
                 continue;
             }
             HashSet<ConsumerUID> s = new HashSet<ConsumerUID>();
             try {
                 Collection cuids = ref.getAllConsumerUIDForDeliveryDelayed();
                 if (DEBUG) {
                     logger.log(logger.INFO, 
                         "Delivery time arrived message "+ref+ "["+di+"] in destination "+
                          destination.getDestinationUID()+" had consumers: "+cuids);
                 }
                 Iterator<ConsumerUID> itr1 = cuids.iterator();
                 ConsumerUID cuid = null;
                 while (itr1.hasNext()) {
                     cuid = itr1.next();
                     if (cuid == PacketReference.getQueueUID()) {
                         continue;
                     }
                     s.add(cuid);
                 }
                 if (DEBUG) {
                     logger.log(logger.INFO, 
                         "Forward delivery time arrived message "+ref+"["+di+
                         "] in destination "+destination.getDestinationUID()+
                          (destination.isQueue() ? "":" to consumers "+s));
                 }
                 destination.forwardDeliveryDelayedMessage(s, ref);
             } catch (Exception e) {
                 logger.logStack(logger.ERROR, br.getKString(
                     br.X_FORWARD_DELIVERY_TIME_ARRIVED_MSG,
                     ref, destination.getDestinationUID())+"["+s+"]", e);
             }
         }
         dues.clear();
         di = null;
         int msize = 0;
         synchronized(this) {
             msize = messages.size();
             itr = messages.iterator();
             while (itr.hasNext()) {
                 di = itr.next();
                 if (di.isDeliveryReady()) {
                     break;
                 }
             }
         }
         long ret = 0L; 
         if (di != null) {
             ret = di.getDeliveryTime();
         }
         if (DEBUG) {
             logger.log(logger.INFO,  "MessageDeliveryTimeTimer:runTask() return "+ret+
             " , next ready message "+di+", destination "+destination.getDestinationUID()+
             " with current delivery delay messages "+msize);
         }
         return ret;
     }

     public void handleOOMError(Throwable e) {
         Globals.handleGlobalError(e, "OOM:MessageDeliveryTimeTimer");
     }

     public void handleLogInfo(String msg) {
         logger.log(Logger.INFO, msg);
     }

     public void handleLogWarn(String msg, Throwable e) {
         if (e == null) {
             logger.log(Logger.WARNING, msg);
         } else {
             logger.logStack(Logger.WARNING, msg, e);
         }
     }

     public void handleLogError(String msg, Throwable e) {
         if (e == null) {
             logger.log(Logger.ERROR, msg);
         } else {
             logger.logStack(Logger.ERROR, msg, e);
         }
     }

     public void handleTimerExit(Throwable e) {
         if (!destination.isValid() || mytimer == null) {
             return;
         }
         String emsg = exitLogString+": "+e.getMessage();
         Broker.getBroker().exit(
             Globals.getBrokerStateHandler().getRestartCode(),
             emsg, BrokerEvent.Type.RESTART, e, false, true, false);
    }
}

