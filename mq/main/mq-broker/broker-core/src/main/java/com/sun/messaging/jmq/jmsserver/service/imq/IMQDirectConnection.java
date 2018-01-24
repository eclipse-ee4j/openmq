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
 * @(#)IMQDirectConnection.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;

import javax.security.auth.login.LoginException;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.auth.MQAuthenticator;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.MetricManager;
import com.sun.messaging.jmq.util.net.IPAddress;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.util.lists.EventType;
import com.sun.messaging.jmq.util.lists.Reason;
import com.sun.messaging.jmq.util.log.Logger;

public class IMQDirectConnection extends IMQConnection 
{
    private Object timerLock = new Object();

    public boolean METRICS_ON = MetricManager.isEnabled();

    Object ctrlEL = null;

    private MQAuthenticator authenticator = null;

    /**
     * constructor
     */
    public IMQDirectConnection(Service svc) throws BrokerException  {
        super(svc);

    InetAddress ia = null;
	try  {
	    ia = InetAddress.getLocalHost();
	    if (ia != null) {
	        this.setRemoteIP(ia.getAddress());
	    }
	} catch(UnknownHostException e)  {
	    throw new BrokerException(
		Globals.getBrokerResources().getString(BrokerResources.E_NO_LOCALHOST));
	}

        setConnectionState(Connection.STATE_CONNECTED);

	try  {
	    authenticator = new MQAuthenticator(svc.getName(), svc.getServiceType());
	} catch(Exception e)  {
	    String errStr = "Authenticator initialization failed for IMQDirectService: " + e;
	    logger.log(Logger.WARNING, errStr);
	    throw new BrokerException(errStr);
	}

        accessController = authenticator.getAccessController();
        if (ia != null) {
            accessController.setClientIP(ia.getHostAddress());
        }

        pstore = Globals.getDestinationList().
            assignStorePartition(svc.getServiceType(), getConnectionUID(), null);

        setConnectionState(Connection.STATE_INITIALIZED);
    }

    public void authenticate(String username, String password) 
				throws BrokerException, LoginException {
	if (authenticator != null)  {
            setConnectionState(Connection.STATE_AUTH_REQUESTED);
            authenticator.authenticate(username, password, false);
	    accessController = authenticator.getAccessController();
            setConnectionState(Connection.STATE_AUTH_RESPONSED);
            setConnectionState(Connection.STATE_AUTHENTICATED);
	}
    }


// -------------------------------------------------------------------------
//   General connection information and metrics
// -------------------------------------------------------------------------
   public boolean isBlocking()  {
	return (false);
   }

    public int getLocalPort() {
	return 0;
    }

    public boolean useDirectBuffers()  {
	return(false);
    }

// -------------------------------------------------------------------------
//  Object Methods (hashCode, toString, etc)
// -------------------------------------------------------------------------
    /**
     * default toString method, sub-classes should override
     */
    public String toString() {
        return "IMQDirectConn[" +getConnectionStateString(state) 
                   +","+getRemoteConnectionString() + "," 
                   + localServiceString() +"]";
    }

    String remoteHostString = null;
    public String remoteHostString() {
	if (remoteHostString == null) {
	    try {
		InetAddress inetaddr = InetAddress.getByAddress(remoteIP);
		remoteHostString = inetaddr.getHostName();
	    } catch (Exception e) {
		remoteHostString=IPAddress.rawIPToString(remoteIP, true, true);
	    }
	}
	return remoteHostString;
    }


    String remoteConString = null;

    public String getRemoteConnectionString() {
        if (remoteConString != null)
            return remoteConString;

        boolean userset = false;

        String remotePortString = "0";
        String userString = "???";

        if (state >= Connection.STATE_AUTHENTICATED) {
            try {
                Principal principal = getAuthenticatedName();
                if (principal != null) {
                    userString = principal.getName();
                    userset = true;
                }
            } catch (BrokerException e) { 
                if (DEBUG)
                    logger.log(Logger.DEBUG,"Exception getting authentication name "
                        + conId, e );
                        
            }
        }

        String retstr = userString + "@" +
            IPAddress.rawIPToString(remoteIP, true, true) + ":" +
            remotePortString;
        if (userset) remoteConString = retstr;
        return retstr;
    }


    String localsvcstring = null;
    protected String localServiceString() {
        if (localsvcstring != null)
            return localsvcstring;
        String localPortString = "0";
        localsvcstring = service.getName() + ":" + localPortString;
        return localsvcstring;
    }

// -------------------------------------------------------------------------
//   Basic Connection Management
// -------------------------------------------------------------------------
    public synchronized void closeConnection(
            boolean force, int reason, String reasonStr) 
    { 
        if (state >= Connection.STATE_CLOSED)  {
             logger.log(Logger.DEBUG,"Requested close of already closed connection:"
                    + this);
             return;
        }
        state = Connection.STATE_CLOSED;
        notifyConnectionClosed();
        stopConnection();
	/*
        if (Globals.getMemManager() != null)
             Globals.getMemManager().removeMemoryCallback(this);
        if (force) { // we are shutting it down, say goodbye
            sayGoodbye(false, reason, reasonStr);
            flushControl(1000);
        }
	*/
        // clean up everything 
	/*
        this.control.removeEventListener(ctrlEL);
	*/
        cleanup(reason == GoodbyeReason.SHUTDOWN_BKR);
        // OK - we are done with the flush, we dont need to be
        // notified anymore
	/*
        if (ninfo != null)
            ninfo.destroy(reasonStr);
	*/

        if (reason == GoodbyeReason.SHUTDOWN_BKR) {
            cleanupConnection(); // OK if we do it twice
        } else {
            cleanup(false);
        }
    }

    protected void cleanupControlPackets(boolean shutdown) {
    }

    int destroyRecurse = 0;
    /**
     * destroy the connection to the client
     * clearing out messages, etc
     */
    public void destroyConnection(boolean force, int reason, String reasonstr) { 
        int oldstate = 0;
        boolean destroyOK = false;
        try {

            synchronized (this) {
                oldstate = state;
                if (state >= Connection.STATE_DESTROYING)
                    return;
    
                if (state < Connection.STATE_CLOSED) {
                     closeConnection(force, reason, reasonstr);
                }
    
                setConnectionState(Connection.STATE_DESTROYING);
            }
            Globals.getConnectionManager().removeConnection(getConnectionUID(),
                   force, reason, reasonstr);
    
            if (accessController.isAuthenticated()) {
                authenticator.logout();
            }

            // The connection is going away. Deposit our metric totals
            // with the metric manager
            MetricManager mm = Globals.getMetricManager();
            if (mm != null) {
                mm.depositTotals(service.getName(), counters);
            }

            // Clear, just in case we are called twice
            counters.reset();

	    /*
            synchronized (timerLock) {

                if (stateWatcher != null) {
                    try {
                        stateWatcher.cancel();
                    } catch (IllegalStateException ex) {
                        logger.log(Logger.DEBUG,"Error destroying "+
                            " connection "  + this + " to state " +
                            state, ex);
                    }
                    stateWatcher = null;
                }
            }
	    */

            logConnectionInfo(true, reasonstr);

            setConnectionState(Connection.STATE_DESTROYED);
            destroyOK = true;
            wakeup();
        } finally {
            if (!destroyOK && reason != GoodbyeReason.SHUTDOWN_BKR 
                    &&  (Globals.getMemManager() == null 
                    || Globals.getMemManager().getCurrentLevel() > 0)) {

                state = oldstate;
                if (destroyRecurse < 2) {
                    destroyRecurse ++;
                    destroyConnection(force, reason, reasonstr);
                }
            } 
                
            // free the lock
            Globals.getClusterBroadcast().connectionClosed(
                getConnectionUID(), isAdminConnection());
        }
    }

    /**
     * sets the connection state 
     * @return false if connection being destroyed
     */
    public boolean setConnectionState(int state) { 
        synchronized (timerLock) {
            this.state = state;
            if (this.state >= Connection.STATE_CLOSED) {
		/*
                if (stateWatcher != null) {
                    try {
                        stateWatcher.cancel();
                    } catch (IllegalStateException ex) {
                        logger.log(Logger.DEBUG,"Error setting state on "+
                            " connection "  + this + " to state " +
                            state, ex);
                    }
                    stateWatcher = null;
                }
		*/
                wakeup();
		return false;
            } else if (state == Connection.STATE_CONNECTED) {
		/*
                interval = Globals.getConfig().getIntProperty(
                   Globals.IMQ + ".authentication.client.response.timeout",
                   DEFAULT_INTERVAL);
                JMQTimer timer = Globals.getTimer();
                stateWatcher = new StateWatcher(Connection.STATE_INITIALIZED, this);
                try {
                    timer.schedule(stateWatcher, interval*1000);
                } catch (IllegalStateException ex) {
                    logger.log(Logger.DEBUG,"InternalError: timer canceled ", ex);
                }
		*/

            } else if (state == Connection.STATE_INITIALIZED 
                   || state == Connection.STATE_AUTH_REQUESTED
                   || state == Connection.STATE_AUTH_RESPONSED) {
		/*
                if (stateWatcher != null) {
                    try {
                        stateWatcher.cancel();
                    } catch (IllegalStateException ex) {
                        logger.log(Logger.DEBUG,"Error setting state on "+
                            " connection "  + this + " to state " +
                            state, ex);
                    }
                    stateWatcher = null;
                }
		*/
                // if next state not from client, return 
                if (state == Connection.STATE_INITIALIZED) {
                    return true;
                }
                if (state == Connection.STATE_AUTH_RESPONSED) {
                    return true;
                }

		/*
                JMQTimer timer = Globals.getTimer();
                stateWatcher = new StateWatcher(
                        Connection.STATE_AUTH_RESPONSED, this);
                try {
                    timer.schedule(stateWatcher, interval*1000);
                } catch (IllegalStateException ex) {
                    logger.log(Logger.DEBUG,"InternalError: timer canceled ", ex);
                }
		*/
            } else if (state >= Connection.STATE_AUTHENTICATED 
                    || state == Connection.STATE_UNAVAILABLE) 
            {
		/*
                if (stateWatcher != null) {
                    try {
                        stateWatcher.cancel();
                    } catch (IllegalStateException ex) {
                        logger.log(Logger.DEBUG,"Error setting state on "+
                            " connection "  + this + " to state " +
                            state, ex);
                    }
                    stateWatcher = null;
                }
		*/
                if (state == Connection.STATE_AUTHENTICATED) {
                    logConnectionInfo(false);
                }
            }
        }
        return true;
    }

    public void logConnectionInfo(boolean closing) {
        this.logConnectionInfo(closing,"Unknown");
    }

    public void logConnectionInfo(boolean closing, String reason) {

        String[] args = {
            getRemoteConnectionString(),
            localServiceString(),
            Integer.toString(Globals.getConnectionManager().size()),
            reason,
	    /*
            String.valueOf(control.size()),
	    */
	    "0",
            Integer.toString(service.size())
        };

        if (!closing) {
            logger.log(Logger.INFO, BrokerResources.I_ACCEPT_CONNECTION, args);
        } else {
            logger.log(Logger.INFO, BrokerResources.I_DROP_CONNECTION, args);
        }
    }


// -------------------------------------------------------------------------
//   Queuing Messages
// -------------------------------------------------------------------------
    /**
     * send a control (reply) message back to the client
     *
     * @param msg message to send back to the client
     */
    public void sendControlMessage(Packet msg) {
    }

    void dumpConnectionInfo() {
	/*
        if (ninfo != null) {
            logger.log(Logger.INFO,"Connection Information [" +this +
              "]" + ninfo.getStateInfo());
        }
	*/
    }

    protected void sayGoodbye(int reason, String reasonStr)  {
    }

    protected void sendConsumerInfo(int requestType, String destName,
                                    int destType, int infoType)  {
    //XXX todo 
    }

// -------------------------------------------------------------------------
//   Sending Messages
// -------------------------------------------------------------------------

    protected void checkState() {
        assert Thread.holdsLock(stateLock);                 
    }

// ---------------------------------------
//     Abstract Connection methods
// ---------------------------------------

    protected void sayGoodbye(boolean force, int reason, String reasonStr)  {
    }

    protected void checkConnection() {
    }

    protected void flushConnection(long timeout) {
    }

    /**
     * called when either the session or the
     * control message is busy 
     */
    public void eventOccured(EventType type,  Reason r,
            Object target, Object oldval, Object newval, 
            Object userdata) 
    {
    /*

        // LKS - at this point, we are in a write lock
        // only one person can change the values
        // at a time

        synchronized (stateLock) {
            if (type == EventType.EMPTY) {
    
                // this can only be from the control queue
                assert target == control;
                assert newval instanceof Boolean;
                assert newval != null;
    
            } else if (type == 
                    EventType.BUSY_STATE_CHANGED) {
                assert target instanceof Session;
                assert newval instanceof Boolean;
                assert newval != null;
    
                Session s = (Session) target;
    
                synchronized(busySessions) {
                    synchronized (s.getBusyLock()) {
                        if (s.isBusy()) {
                            busySessions.add(s);
                        }
                    }
                }
                
            }
            checkState();
        }
    */
    }

}

