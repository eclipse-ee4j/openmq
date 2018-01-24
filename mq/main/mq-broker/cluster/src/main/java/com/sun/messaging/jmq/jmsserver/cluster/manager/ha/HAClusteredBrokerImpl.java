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

package com.sun.messaging.jmq.jmsserver.cluster.manager.ha;

import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.cluster.manager.ClusterReason;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.TakeoverStoreInfo;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

public class HAClusteredBrokerImpl implements HAClusteredBroker {

       protected Logger logger = Globals.getLogger();

        /**
         * Is this the local (in this vm) broker.
         */
        boolean local = false;

        /**
         * Current status of the broker.
         */
        Integer status = Integer.valueOf(BrokerStatus.BROKER_UNKNOWN);

        /**
         * Current state of the broker.
         */
        BrokerState state = BrokerState.INITIALIZING;

        /**
         * Name associated with this broker. This name is
         * unique across the cluster.
         */
        String brokerid = null;

        /**
         * The portmapper for this broker.
         */
        MQAddress address = null; 

        /**
         * The instance name for this broker. can be null
         */
        transient String instanceName = null;


        /** 
         * Protocol version of this broker.
         */
        Integer version = Integer.valueOf(0);

        /**
         * The unique ID associated with this instance of a store
         * (used when takeover occurs).
         */
        protected UID session = null;

        /**
         * The last time (in milliseconds) the heartbeat on the database
         * was updated.
         */
        long heartbeat = 0;

        /**
         * The brokerid associated with the broker (if any) who took over
         * this brokers store.
         */
        String takeoverBroker = null;


        /**
         * The uid associated with broker session
         */
        UID brokerSessionUID;
       
        protected HAClusterManagerImpl parent = null;

        protected HAClusteredBrokerImpl() {}

        /** 
         * Create a instace of HAClusteredBrokerImpl from a map of 
         * data (called when store.getAllBrokerInfoByState() is used
         * to retrieve a set of brokers).
         *
         * @param brokerid is the id associated with this broker
         * @param m is the fields associated with the broker's entry in the
         *               jdbc store.
         * @throws BrokerException if something is wrong during loading
         */
        public HAClusteredBrokerImpl(String brokerid, HABrokerInfo m, HAClusterManagerImpl parent)
            throws BrokerException
        {
             this.parent = parent;
             this.brokerid = brokerid;
             this.status = Integer.valueOf(BrokerStatus.BROKER_UNKNOWN);
             String urlstr = m.getUrl();
             try {
                 address = BrokerMQAddress.createAddress(urlstr);
             } catch (Exception ex) {
                 throw new BrokerException(
                       Globals.getBrokerResources().getKString(
                           BrokerResources.E_INTERNAL_BROKER_ERROR,
                           "invalid URL stored on disk " + urlstr, ex));
             }
             version = Integer.valueOf(m.getVersion());
             state = BrokerState.getState(m.getState());
             session = new UID(m.getSessionID());
             takeoverBroker = m.getTakeoverBrokerID();
             heartbeat = m.getHeartbeat();
        }

        /** 
         * Create a <i>new</i> instace of HAClusteredBrokerImpl  and
         * stores it into the database.
         *
         * @param brokerid is the id associated with this broker
         * @param url is the portmapper address
         * @param version is the cluster version of the broker
         * @param state is the current state of the broker
         * @param session is this broker's current store session.
         * @throws BrokerException if something is wrong during creation
         */
        public HAClusteredBrokerImpl(String brokerid,
                 MQAddress url, int version, BrokerState state,
                 UID session, HAClusterManagerImpl parent)
            throws BrokerException
        {
             this.parent = parent;
             this.brokerid = brokerid;
             this.status = Integer.valueOf(BrokerStatus.BROKER_UNKNOWN);
             this.address = url;
             this.version = Integer.valueOf(version);
             this.state = state;
             this.session = session;
             this.takeoverBroker = "";
             this.brokerSessionUID = new UID();

             Store store = Globals.getStore();
             store.addBrokerInfo(brokerid, address.toString(), state,
                    this.version.intValue(), session.longValue(),
                    heartbeat);

             this.heartbeat = store.getBrokerHeartbeat(brokerid);
        }

        public boolean equals(Object o) {
            if (! (o instanceof ClusteredBroker)) 
                return false;
            return this.getBrokerName().equals(((ClusteredBroker)o).getBrokerName());
        }

        public int hashCode() {
             return this.getBrokerName().hashCode();
        }

        public void update(HABrokerInfo m) {
             MQAddress oldaddr = address;

             synchronized (this) {

             this.brokerid = m.getId();
             String urlstr = m.getUrl();
             try {
                 address = BrokerMQAddress.createAddress(urlstr);
             } catch (Exception ex) {
                 logger.logStack(logger.WARNING, ex.getMessage(), ex);
                 address = oldaddr;
             }
             version = Integer.valueOf(m.getVersion());
             state = BrokerState.getState(m.getState());
             session = new UID(m.getSessionID());
             takeoverBroker = m.getTakeoverBrokerID();
             heartbeat = m.getHeartbeat();

             }

             if (!oldaddr.equals(address)) {
                 parent.brokerChanged(ClusterReason.ADDRESS_CHANGED,
                     this.getBrokerName(), oldaddr, this.address, null, null);
             }
         }

        /**
         * Sets if this broker is local or not/
         * @param local true if the broker is running in the current vm
         * @see #isLocalBroker
         */

        void setIsLocal(boolean local)
        {
            this.local = local;
        }

        /** 
         * String representation of this broker.
         */
        public String toString() {
            if (!local)
                return "-" +brokerid + "@" + address + ":" + state +
                        "[StoreSession:" + session + ", BrokerSession:"+brokerSessionUID+"]"+  ":"+
                        BrokerStatus.toString( status.intValue());
             return "*" +brokerid + "@" + address + ":" + state + 
                        "[StoreSession:" + session + ", BrokerSession:"+brokerSessionUID+"]"+  ":"+
                        BrokerStatus.toString( status.intValue());
                   
        }

        /**
         * a unique identifier assigned to the broker
         * (randomly assigned)<P>
         *
         * This name is only unique to this broker. The
         * broker at this URL may be assigned a different name
         * on another broker in the cluster.
         *
         * @return the name of the broker
         */
        public String getBrokerName()
        {
             return brokerid;
        }
    
        /**
         * the URL to the portmapper of this broker
         * @return the URL of this broker
         */
        public MQAddress getBrokerURL()
        {             
             return address;
        }
 
        /**
         * @return The instance name of this broker, can be null
         */
        public String getInstanceName()
        {
             return instanceName;
        }

        /**
         * @param instName The instance name of this broker, can be null
         */
        public void setInstanceName(String instName)
        {
            instanceName = instName;
        }

        /**
         * the URL to the portmapper of this broker
         */
        public void setBrokerURL(MQAddress address) throws Exception
        {
             if (!local) {
                 throw new UnsupportedOperationException(
                    "Only the local broker can have its url changed");
             }

             MQAddress oldaddress = this.address;
             try {
                 updateEntry(HABrokerInfo.UPDATE_URL, null, address.toString());
                 this.address = address;
             } catch (Exception ex) {
                 logger.logStack(logger.ERROR, 
                     ex.getMessage()+"["+oldaddress+", "+address+"]"+brokerid, ex);
                 throw ex;
             }
             parent.brokerChanged(ClusterReason.ADDRESS_CHANGED, 
                  this.getBrokerName(), oldaddress, this.address, null, null);

        }


        /**
         * resets the takeover broker
         */
         public void resetTakeoverBrokerReadyOperating() throws Exception
         {
             if (!local) {
                 throw new UnsupportedOperationException(
                 "Only the local broker can have its takeover broker reset");
             }
             getState();
             if (state == BrokerState.FAILOVER_PENDING ||
                 state == BrokerState.FAILOVER_STARTED) {
                 String otherb = getTakeoverBroker();
                 throw new IllegalStateException(
                     Globals.getBrokerResources().getKString(
                     BrokerResources.X_A_BROKER_TAKINGOVER_THIS_BROKER,
                     (otherb == null ? "":otherb), this.toString()));
             }
             try {
                 UID curssid = updateEntry(
                               HABrokerInfo.RESET_TAKEOVER_BROKER_READY_OPERATING,
                               state, getBrokerSessionUID());
                 if (curssid != null) {
                     this.session = curssid; 
                     parent.localSessionUID = curssid;
                 }
             } catch (Exception ex) {
                 logger.logStack(logger.ERROR, ex.getMessage()+"["+brokerid+"]", ex);
                 throw ex;
             }
         }

    
        /**
         * Is this the address of the broker running in this
         * VM
         * @return true if this is the broker running in the
         *         current vm
         */
        public boolean isLocalBroker()
        {
            return local;
        }
    
        /**
         * gets the status of the broker.
         *
         * @see BrokerStatus
         * @return the status of the broker
         */
        public synchronized int getStatus() {
            return status.intValue();
        } 

        /**
         * gets the protocol version of the broker .
         * @return the current cluster protocol version (if known)
         *        or 0 if not known
         */
        public synchronized int getVersion()
        {
            return (version == null ? 0 : version.intValue());
        }  
    
    
        /**
         * sets the protocol version of the broker .
         * @param version the current cluster protocol version (if known)
         *        or 0 if not known
         * @throws UnsupportedOperationException if this change
         *         can not be made on this broker
         */
        public synchronized void setVersion(int version) throws Exception

        {
            Integer oldversion = this.version; 
            Integer newVersion = Integer.valueOf(version);
            if (local) {
                 try {
                     updateEntry(HABrokerInfo.UPDATE_VERSION, this.version, newVersion);
                     this.version = newVersion;
                 } catch (Exception ex) {
                     logger.logStack(logger.WARNING,
                         ex.getMessage()+"["+oldversion+", "+version+"]"+brokerid, ex);
                     throw ex;
                 }
            }
            parent.brokerChanged(ClusterReason.VERSION_CHANGED, 
                  this.getBrokerName(), oldversion, this.version, null,  null);
        }  

    
        /**
         * sets the status of the broker (and notifies listeners).
         *
         * @param newstatus the status to set
         * @param userData optional user data
         * @see ConfigListener
         */
        public void setStatus(int newstatus, Object userData)
        {
            UID uid = null;
            Integer oldstatus = null;
            synchronized (this) {
                if (this.status.intValue() == newstatus)
                    return;
                uid = getBrokerSessionUID();
                oldstatus = this.status;
                this.status = Integer.valueOf(newstatus);
            }
            // notify
            if (! oldstatus.equals(this.status))
                parent.brokerChanged(ClusterReason.STATUS_CHANGED, 
                  this.getBrokerName(), oldstatus, this.status, uid, userData);

        }


        /**
         * Updates the BROKER_UP bit flag on status.
         * 
         * @param userData optional user data
         * @param up setting for the bit flag (true/false)
         */
        public void setBrokerIsUp(boolean up, UID brokerSession, Object userData)
        {
        
            UID uid = brokerSession;
            Integer oldstatus = null;
            Integer newstatus = null;
            synchronized (this) {
                if (!up && !uid.equals(getBrokerSessionUID())) {
                    logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                               BrokerResources.I_DOWN_STATUS_ON_BROKER_SESSION,
                               "[BrokerSession:"+uid+"]", this.toString()));
                    oldstatus = Integer.valueOf(BrokerStatus.BROKER_INDOUBT);
                    newstatus = BrokerStatus.setBrokerIsDown(oldstatus);

                } else {

                    oldstatus = this.status;
                    int newStatus = 0;
                    if (up) {
                        newStatus = BrokerStatus.setBrokerIsUp
                                        (this.status.intValue());
                    } else {
                        // ok, we CANT have INDOUBT and BrokerDown
                        newStatus = BrokerStatus.setBrokerIsDown
                                        (this.status.intValue());
                        newStatus = BrokerStatus.setBrokerNotInDoubt
                                        (newStatus);
                    }
                    uid = getBrokerSessionUID();
                    this.status = Integer.valueOf(newStatus);
                    newstatus = this.status;
                }
            }
            // notify
            if (! oldstatus.equals(this.status))
                parent.brokerChanged(ClusterReason.STATUS_CHANGED, 
                  this.getBrokerName(), oldstatus, newstatus, uid, userData);

        }

        /**
         * Updates the BROKER_LINK_UP bit flag on status.
         * 
         * @param userData optional user data
         * @param up setting for the bit flag (true/false)
         */
        public void setBrokerLinkUp(boolean up, Object userData)
        {
        
            UID uid = null;
            Integer oldstatus = null;
            synchronized (this) {
                oldstatus = this.status;

                int newStatus = 0;
                uid = getBrokerSessionUID();
                if (up) {
                   newStatus = BrokerStatus.setBrokerLinkIsUp
                        (this.status.intValue());
                } else {
                   newStatus = BrokerStatus.setBrokerLinkIsDown
                        (this.status.intValue());
                }
                this.status = Integer.valueOf(newStatus);
            }
            // notify
            if (! oldstatus.equals(this.status))
                parent.brokerChanged(ClusterReason.STATUS_CHANGED, 
                      this.getBrokerName(), oldstatus, this.status, uid, userData);

        }


        /**
         * Updates the BROKER_INDOUBT bit flag on status.
         * 
         * @param userData optional user data
         * @param up setting for the bit flag (true/false)
         */
        public void setBrokerInDoubt(boolean up, Object userData)
        {
        
            UID uid = (UID)userData;
            Integer oldstatus = null;
            Integer newstatus = null;
            synchronized (this) {
                if (up && !uid.equals(getBrokerSessionUID())) {
                    logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                               BrokerResources.I_INDOUBT_STATUS_ON_BROKER_SESSION,
                               "[BrokerSession:"+uid+"]", this.toString()));
                    oldstatus = Integer.valueOf(BrokerStatus.ACTIVATE_BROKER);
                    newstatus = BrokerStatus.setBrokerInDoubt(oldstatus);

                } else {

                    oldstatus = this.status;
                    int newStatus = 0;
                    if (up) {
                        newStatus = BrokerStatus.setBrokerInDoubt
                                        (this.status.intValue());
                    } else {
                        newStatus = BrokerStatus.setBrokerNotInDoubt
                                         (this.status.intValue());
                    }
                    uid = getBrokerSessionUID();
                    this.status = Integer.valueOf(newStatus);
                    newstatus = this.status;
                }
            }
            // notify
            if (! oldstatus.equals(this.status))
                parent.brokerChanged(ClusterReason.STATUS_CHANGED, 
                      this.getBrokerName(), oldstatus, newstatus, uid, userData);

        }

        /**
         * marks this broker as destroyed. This is equivalent to setting
         * the status of the broker to DOWN.
         *
         * @see BrokerStatus#setBrokerIsDown
         */
        public void destroy() {
            synchronized (this) {
                status = Integer.valueOf(BrokerStatus.setBrokerIsDown(
                              status.intValue()));
            }
        }


        /**
         * Gets the UID associated with the store session.
         *
         * @return the store session uid (if known)
         */
        public synchronized UID getStoreSessionUID()
        {
            return session;
        }

        /**
         * Gets the UID associated with the broker session.
         *
         * @return the broker session uid (if known)
         */
        public synchronized UID getBrokerSessionUID()
        {
            return brokerSessionUID;
        }

    
        /**
         * Sets the UID associated with the broker session.
         *
         * @param uid the new broker session uid 
         */
        public synchronized void setBrokerSessionUID(UID uid)
        {
             brokerSessionUID = uid;
        }

        public boolean isBrokerIDGenerated()
        {
            return false;
        }


      /**
       * Retrieves the id of the broker who has taken over this broker's store.
       *
       * @return the broker id of the takeover broker (or null if there is not
       *      a takeover broker).
       */
        public synchronized String getTakeoverBroker() throws BrokerException
        {
            HABrokerInfo bkrInfo = Globals.getStore().getBrokerInfo(brokerid);
            if (bkrInfo == null) {
                logger.log(Logger.ERROR,
                    BrokerResources.E_BROKERINFO_NOT_FOUND_IN_STORE, brokerid);
                return null;
            }
            takeoverBroker = bkrInfo.getTakeoverBrokerID();
            return takeoverBroker;
        }
    

       /**
       * Returns the heartbeat timestamp associated with this broker.
       * <b>Note:</b> the heartbeat is always retrieved from the store
       * before it is returned (so its current).
       * 
       *  @return the heartbeat in milliseconds
       *  @throws BrokerException if the heartbeat can not be retrieve.
       */
        public long getHeartbeat() 
            throws BrokerException
        {
            heartbeat = Globals.getStore().getBrokerHeartbeat(brokerid);
            return heartbeat;
        }

        public synchronized long updateHeartbeat() throws BrokerException {
            return updateHeartbeat(false);
        }

        /**
         *  Update the timestamp associated with this broker.
         *  @return the updated heartbeat in milliseconds
         * @throws BrokerException if the heartbeat can not be set or retrieve.
         */
        public synchronized long updateHeartbeat(boolean reset)
            throws BrokerException
        {
            // this one always accesses the backing store
            Store store = Globals.getStore();
            Long newheartbeat  = null; 

            if (reset) {
                if ((newheartbeat = store.updateBrokerHeartbeat(brokerid)) == null) {
                    throw new BrokerException(Globals.getBrokerResources().getKString(
                          BrokerResources.X_UPDATE_HEARTBEAT_TS_2_FAILED, brokerid,
                          "Failed to reset heartbeat timestamp."));
                }
            } else {

            if ((newheartbeat = store.updateBrokerHeartbeat(brokerid, heartbeat)) == null) {
                // TS is out of sync so log warning msg and force update
                logger.log(Logger.WARNING,
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_UPDATE_HEARTBEAT_TS_2_FAILED, brokerid,
                    "Reset heartbeat timestamp due to synchronization problem." ) );

                if ((newheartbeat = store.updateBrokerHeartbeat(brokerid)) == null) {
                    // We really have a problem
                    throw new BrokerException(
                        Globals.getBrokerResources().getKString(
                        BrokerResources.X_UPDATE_HEARTBEAT_TS_2_FAILED, brokerid,
                        "Failed to reset heartbeat timestamp."));
                }
            }
            }

            heartbeat = newheartbeat.longValue();
            return heartbeat;
        }
    
        /**
         * updates the broker entry (this may be just timestamp or
         * state, sessionid or brokerURL may be changed)
         *
         * @param updateType update type
         * @param oldValue old value depending on updateType
         * @param newValue new value depending on updateType
         * @return current store session UID if requested by updateType
         * @throws IllegalStateException if the information stored
         *               with the broker does not match what we expect
         * @throws IllegalAccessException if the broker does not have
         *               permission to change the broker (e.g. one broker
         *               is updating anothers timestamp).
         */
        protected synchronized UID updateEntry(int updateType, 
                                             Object oldValue, Object newValue)
                                             throws Exception
        {
             if (!local) {
                 throw new IllegalAccessException(
                 "Can not update entry " + " for broker " + brokerid);
             }

             Store store = Globals.getStore();
             UID ssid = store.updateBrokerInfo(brokerid, updateType, oldValue, newValue);
             try {
                 heartbeat = store.getBrokerHeartbeat(brokerid);
             } catch (Exception ex) {
                 logger.logStack(logger.WARNING, ex.getMessage()+"["+brokerid+"]", ex);
             }
             return ssid;
        }


    
        /**
         * gets the state of the broker .
         * <b>Note:</b> the state is always retrieved from the store
         * before it is returned (so its current).
         * 
         *
         * @throws BrokerException if the state can not be retrieve
         * @return the current state
         */
        public BrokerState getState()
            throws BrokerException
        {
//          this should always retrieve the state on disk (I think)
            BrokerState oldState = state;
            state =  Globals.getStore().getBrokerState(brokerid);
            if (oldState != state && state != BrokerState.FAILOVER_PENDING) {
                 parent.brokerChanged(ClusterReason.STATE_CHANGED, 
                      this.getBrokerName(), oldState, this.state, null,  null);
            }
            return state;
 
        }

        public void setStateFailoverProcessed(UID storeSession) throws Exception {
            if (local) {
                throw new IllegalAccessException(
                "Cannot update self state to "+BrokerState.FAILOVER_PROCESSED);
            }
            BrokerState newstate = BrokerState.FAILOVER_PROCESSED;
            try {
                BrokerState oldState = getState();
                synchronized(this) {
                    if (storeSession.equals(session)) {
                        state = newstate;
                    } else {
                        oldState = BrokerState.FAILOVER_COMPLETE;
                    }
                }

                parent.brokerChanged(ClusterReason.STATE_CHANGED, 
                    this.getBrokerName(), oldState, newstate, storeSession,  null);

            } catch (Exception ex) {
                IllegalStateException e = 
                     new IllegalStateException("Failed to update state "+
                             BrokerState.FAILOVER_COMPLETE+" for " + brokerid);
                 e.initCause(ex);
                 throw e;
            }
        }

        public void setStateFailoverFailed(UID brokerSession) throws Exception {
            if (local) {
                throw new IllegalAccessException(
                "Cannot update self state to "+BrokerState.FAILOVER_FAILED);
            }
            BrokerState newstate = BrokerState.FAILOVER_FAILED;
            try {
                BrokerState oldState = getState();
                synchronized(this) {
                    if (brokerSessionUID.equals(brokerSession)) {
                        state = newstate;
                    } else {
                        oldState = BrokerState.OPERATING;
                    }
                }

                parent.brokerChanged(ClusterReason.STATE_CHANGED, 
                    this.getBrokerName(), oldState, newstate, brokerSession,  null);

            } catch (Exception ex) {
                IllegalStateException e = 
                     new IllegalStateException("Failed to update state to "+
                             BrokerState.FAILOVER_FAILED+ " for " + brokerid);
                 e.initCause(ex);
                 throw e;
            }
        }

    
        /**
         * sets the persistent state of the broker 
         * @throws IllegalAccessException if the broker does not have
         *               permission to change the broker (e.g. one broker
         *               is updating anothers state).
         * @throws IllegalStateException if the broker state changed
         *               unexpectedly.
         * @throws IndexOutOfBoundsException if the state value is not allowed.
         */
        public void setState(BrokerState newstate)
             throws IllegalAccessException, IllegalStateException,
                    IndexOutOfBoundsException
        {
             if (!local && newstate != BrokerState.FAILOVER_PROCESSED
                 && newstate != BrokerState.FAILOVER_STARTED
                 && newstate != BrokerState.FAILOVER_COMPLETE
                 && newstate != BrokerState.FAILOVER_FAILED) {
                 // a remote broker should only be updated during failover
                 // FAILOVER_PENDING is set with specific method
                 throw new IllegalAccessException("Cannot update state "
                      + " for broker " + brokerid);
             }

             try {
                 BrokerState oldState = getState();
                 if (newstate != BrokerState.FAILOVER_PENDING
                     && newstate != BrokerState.FAILOVER_PROCESSED
                     && newstate != BrokerState.FAILOVER_FAILED) {
                     if (!Globals.getStore().updateBrokerState(brokerid, newstate, state, local))
                     throw new IllegalStateException(
                     "Could not update broker state from "+oldState+" to state "+newstate+ " for " + brokerid);
                 }
                 state = newstate;
                 parent.brokerChanged(ClusterReason.STATE_CHANGED, 
                      this.getBrokerName(), oldState, this.state, null,  null);
             } catch (BrokerException ex) {
                 IllegalStateException e = 
                     new IllegalStateException(
                           Globals.getBrokerResources().getKString(
                               BrokerResources.E_INTERNAL_BROKER_ERROR,
                               "Failed to update state for " + brokerid));
                 e.initCause(ex);
                 throw e;
             }
        }
        public static void checkCanTakeoverState(BrokerState stateToCheck, String brokerID)
        throws BrokerException {
            if (stateToCheck == BrokerState.INITIALIZING || 
                stateToCheck == BrokerState.SHUTDOWN_STARTED ||
                stateToCheck == BrokerState.SHUTDOWN_COMPLETE ) {
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                        BrokerResources.X_STATE_NOT_ALLOW_TAKEOVER, brokerID, stateToCheck),
                        Status.NOT_ALLOWED);
            }
            if (stateToCheck == BrokerState.FAILOVER_PENDING ||
                stateToCheck == BrokerState.FAILOVER_STARTED ||
                stateToCheck == BrokerState.FAILOVER_COMPLETE ||
                stateToCheck == BrokerState.FAILOVER_PROCESSED) { 
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                        BrokerResources.I_NOT_TAKEOVER_BKR, brokerID),
                        Status.CONFLICT);
            }
        }

        /**
         * attempt to take over the persistent state of the broker
         * 
         * @throws IllegalStateException if this broker can not takeover.
         * @return data associated with previous broker
         */
        public TakeoverStoreInfo takeover(boolean force, Object extraInfo,
                                          TakingoverTracker tracker)
                                          throws BrokerException
        {
            int delay = Globals.getConfig().getIntProperty(
                Globals.IMQ + ".cluster.takeover.delay.interval", 0);
            if (delay > 0) {
                try {
                    Thread.sleep(delay*1000L);
                } catch (InterruptedException e) {}
            }             

            boolean gotLock = false;
             boolean sucessful = false;
             BrokerState curstate = getState();

             if (!force) {
                 checkCanTakeoverState(curstate, brokerid);
             }

             long newtime = System.currentTimeMillis();
             BrokerState newstate = BrokerState.FAILOVER_PENDING;
             Globals.getStore().getTakeOverLock(parent.getLocalBrokerName(),
                         brokerid, tracker.getLastHeartbeat(),
                         curstate, newtime, newstate, force, tracker);
             gotLock = true;
             state = newstate;
             logger.log(Logger.DEBUG,"state = FAILOVER_PENDING " + brokerid);
             parent.brokerChanged(ClusterReason.STATE_CHANGED, 
                      brokerid, curstate, newstate , null,  null);

             TakeoverStoreInfo o = null;
             try {
                 // OK, explicitly retrieve old state from disk
                logger.log(Logger.DEBUG,"state = FAILOVER_STARTED " + brokerid);
                setState(BrokerState.FAILOVER_STARTED);
                o = Globals.getStore().takeOverBrokerStore(parent.getLocalBrokerName(),
                                                           brokerid, tracker);
                logger.log(Logger.DEBUG,"state = FAILOVER_COMPLETE " + brokerid);
                 // fix for bug 6319711
                 // higher level processing needs to set 
                 // failover complete AFTER routing is finished
                 //REMOTE: setState(BrokerState.FAILOVER_COMPLETE);

                 sucessful = true;
             } catch (IllegalAccessException ex) {
                 throw new RuntimeException("Internal error, shouldnt happen", ex);
             } finally {
                 if (gotLock && !sucessful) {
                     try {
                         setStateFailoverFailed(tracker.getBrokerSessionUID());
                     } catch (Exception ex) {
                         logger.log(logger.INFO,
                         "Unable to set state to failed for broker "
                          +this+": "+ex.getMessage(), ex);
                     }
                     logger.log(Logger.WARNING,"Failed to takeover :"
                              + brokerid + " state expected is " + curstate );
                 }
             }
             heartbeat = newtime;
             parent.addSupportedStoreSessionUID(session);
             takeoverBroker=parent.getLocalBrokerName();
             return o;
        }

        /**
         * Is the broker static or dynmically configured
         */
        public boolean isConfigBroker()
        {
             return true;
        }
    
        /**
         * used by replicated BDB
         */
        public String getNodeName() throws BrokerException {
            throw new UnsupportedOperationException(
            "Unexpected call"+getClass().getName()+".getNodeName()");
        }


   }
