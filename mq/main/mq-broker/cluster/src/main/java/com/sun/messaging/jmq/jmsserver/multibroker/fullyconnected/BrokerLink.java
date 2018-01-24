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
 * @(#)BrokerLink.java	1.61 07/08/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.multibroker.BrokerInfo;
import com.sun.messaging.jmq.jmsserver.cluster.manager.ClusterManagerImpl;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterBrokerInfoReply;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterFirstInfoInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.HandshakeInProgressException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.io.*;

import javax.net.ssl.*;


/**
 * This class represents a connection between a pair of brokers.
 * It handles the connection state management and I/O.
 */
public class BrokerLink extends Thread {
    private static boolean DEBUG = false;

    private boolean connected;
    private boolean expectBrokerInfoPkt = true;
    private boolean expectBrokerInfoReplyPkt = false;

    private Object handshakeLock = new Object();
    private boolean handshakeSent = false;

    private Socket conn;
    private InputStream is;
    private OutputStream os;
    private BrokerLinkWriter writer;

    private BrokerAddressImpl self;
    private BrokerAddressImpl remote;
    // Note: Initially we don't know the remote instance name.
    // Hence the initial value of 'remote' is usually -
    //     "<hostName>:???:<portNo>"
    // After the connection is established, it is replaced by
    // the correct value -
    //     "<hostName>:<instName>:<portNo>"

    private ClusterImpl parent = null;

    private boolean linkInitDone = false;
    private Object linkInitWaitObject = null;

    private boolean autoConnect = false;

    // protects on adding/removing this link to/from parent's brokerlist
    private BrokerListLock brokerListLock = new BrokerListLock();

    private static Logger logger = Globals.getLogger();
    private static final BrokerResources br = Globals.getBrokerResources();

    private long createLinkFailures = 0;
    private static Hashtable waitingMasterLogs = new Hashtable();
    private boolean readActive = true;

    protected static final long DEFAULT_INIT_WAIT_TIME = 180*1000L;
    protected static final long DEFAULT_INIT_WAIT_INTERVAL = 15*1000L;
    private static final long initWaitTimeSeconds = 
        Globals.getConfig().getLongProperty(Globals.IMQ +
            ".cluster.waitConnectionInitTimeout", DEFAULT_INIT_WAIT_TIME/1000L);
    protected static final long INIT_WAIT_TIME = initWaitTimeSeconds * 1000L;

    protected static final long RECONNECT_INTERVAL = 5000L;

    private boolean firstInfoSent = false;
    private boolean firstReceive = true;
    private AtomicBoolean pingLogging = new AtomicBoolean(false);

    private static final FaultInjection fi = FaultInjection.getInjection();

    private String oomStr = null;

    /**
     * Create a broker-to-broker link and start processing I/O.
     *
     * @param self Address of this broker.
     * @param remote Address of the remote broker.
     * @param parent Consumes the packets received on this connection.
     */
    public BrokerLink(BrokerAddressImpl self, BrokerAddressImpl remote,
        ClusterImpl parent) {
        connected = false;

        conn = null;
        is = null;
        os = null;
        writer = null;

        this.self = self;
        this.remote = remote;
        oomStr = br.getKString(br.M_LOW_MEMORY_CLUSTER)+" ["+getRemoteString()+"]";
        setName("BrokerLink:" + getRemoteString());

        this.parent = parent;

        linkInitDone = false;
        linkInitWaitObject = new Object();

        setDaemon(true);
    }

    public void setAutoConnect(boolean autoConnect) {
        this.autoConnect = autoConnect;
    }

    public boolean getAutoConnect() {
        return autoConnect;
    }

    private void setRemote(BrokerAddressImpl remote) {
        this.remote = remote;
        setName("BrokerLink:" + getRemoteString());
    }

    protected BrokerAddressImpl getRemote() {
        return remote;
    }

    protected String getRemoteString() {
        return getRemoteString(conn, remote);
    }

    protected static String getRemoteString(Socket s, BrokerAddressImpl remoteBroker) {
        String rs = getRealRemoteString(s);
        if (rs == null) return remoteBroker.toString();
        return remoteBroker.toString()+"["+rs+"]";
    }

    private static String getRealRemoteString(Socket s) {
        if (s == null || s.isClosed()) return null;
        return s.getRemoteSocketAddress().toString();
    }

    /**
     * Wait for at least one connect attempt on this link.
     */
    public void waitLinkInit(long initWaitTime) {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.waitLinkInit : " + this);
        }

        long endtime = System.currentTimeMillis() + initWaitTime;
        long waittime = initWaitTime;
        if (waittime > DEFAULT_INIT_WAIT_INTERVAL) {
            waittime = DEFAULT_INIT_WAIT_INTERVAL;
        }

        synchronized (linkInitWaitObject) {
            while (! linkInitDone) {
                try {
                    logger.log(Logger.INFO, br.I_CLUSTER_WAIT_LINKINIT, getRemoteString());
                    linkInitWaitObject.wait(waittime);
                } catch (Exception e) {}

                if (linkInitDone) break;
                long curtime = System.currentTimeMillis();
                if (curtime >= endtime)  {
                    logger.log(Logger.WARNING,
                               br.getKString(br.W_CLUSTER_WAIT_LINKINIT_TIMEOUT,
                                  String.valueOf(initWaitTimeSeconds), getRemoteString()));
                    break;
                }
                waittime = endtime - curtime;
                if (waittime > DEFAULT_INIT_WAIT_INTERVAL) {
                    waittime = DEFAULT_INIT_WAIT_INTERVAL;
                }
            }
        }

        if (DEBUG) {
            logger.log(logger.DEBUG, "Returning from BrokerLink.waitLinkInit : " + this);
        }
    }

    /**
     * Set the flow control for an outgoing packet stream.
     */
    public synchronized void setFlowControl(boolean enabled) {
        if (writer == null) return;

        writer.setFlowControl(enabled);
    }

    protected synchronized boolean isModified(Object o) {
        if (o == writer) {
            return false;
        }
        return true;
    }

    /**
     * Writes a packet to this connection's <code>OutputStream</code>
     *
     * @param gp packet to be sent.
     * @return an opaque object 
     */
    public synchronized Object sendPacket(GPacket gp) throws IOException {
        return sendPacket(gp, false, false);
    }

    public synchronized Object sendPacket(GPacket gp, boolean close, boolean urgent)
    throws IOException {

        int type = gp.getType();

        if (DEBUG) {
            logger.log(logger.INFO, "BrokerLink.sendPacket("+ProtocolGlobals.getPacketTypeString(type)+")");
        }
        

        if (writer == null) {
            // This exception is never displayed, does not need to be
            // localized
            throw new IOException(
                "Packet send failed. Broker unreachable : " + getRemoteString());
        }

        synchronized(handshakeLock) {
            if (type != ProtocolGlobals.G_BROKER_INFO_REPLY) {
                if (fi.FAULT_INJECTION) {
                    synchronized(fi) {
                    if (fi.checkFault(fi.FAULT_CLUSTER_LINK_HANDSHAKE_INPROGRESS_EX, null)) {
                        fi.unsetFault(fi.FAULT_CLUSTER_LINK_HANDSHAKE_INPROGRESS_EX);
                        throw new HandshakeInProgressException(
                            br.getKString(br.W_CLUSTER_LINK_IN_HANDSHAKE,
                            ProtocolGlobals.getPacketTypeDisplayString(type), this));
                    }
                    }
                }
                if (!handshakeSent) {
                    throw new HandshakeInProgressException(
                    br.getKString(br.W_CLUSTER_LINK_IN_HANDSHAKE,
                    ProtocolGlobals.getPacketTypeDisplayString(type), this));
                }
            }
        }
 
        if (type != ProtocolGlobals.G_BROKER_INFO_REPLY) {
            if (!firstInfoSent) { 
                if (type == ProtocolGlobals.G_FIRST_INFO) { 
                    firstInfoSent = true;
                } else {
                    GPacket p = parent.getFirstInfoPacket();
                    firstInfoSent = true;
                    if (p != null) {
                        sendPacket(p);
                    }
                }
            } else if (type == ProtocolGlobals.G_FIRST_INFO) {
                return writer;
            }
        }

        if (ClusterManagerImpl.isDEBUG_CLUSTER_PACKET() ||
            (ClusterManagerImpl.isDEBUG_CLUSTER_PING() &&
             (type == ProtocolGlobals.G_PING || type == ProtocolGlobals.G_PING_REPLY)) ||
            ClusterManagerImpl.isDEBUG_CLUSTER_ALL()) {
            logger.log(Logger.INFO, "SENDING PACKET : "+this+"\n" + gp.toLongString());
            if (logger.getLevel() <= Logger.DEBUG  && gp.getPayload() != null) {
                byte[] buf = gp.getPayload().array();
                logger.log(Logger.DEBUG, "Payload : " + Packet.hexdump(buf, Integer.MAX_VALUE));
            }
        }

        writer.sendPacket(gp, close, urgent);
        return writer;
    }

    /**
     * Writes a packet to this connection's <code>OutputStream</code>
     *
     * @param p packet to be sent.
     */
    public synchronized void sendPacket(Packet p) throws IOException {
        sendPacket(p, false);
    }

    public synchronized void sendPacket(Packet p, boolean close)
        throws IOException {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.sendPacket(Packet)");
        }

        if (writer == null) {
            // This exception is never displayed, does not need to be
            // localized
            throw new IOException(
                "Packet send failed. Broker unreachable : " + getRemoteString());
        }

        if (ClusterManagerImpl.isDEBUG_CLUSTER_PACKET() || 
            (ClusterManagerImpl.isDEBUG_CLUSTER_PING() && p.getPacketType() == Packet.PING) ||
            ClusterManagerImpl.isDEBUG_CLUSTER_ALL()) {
            logger.log(Logger.INFO, "SENDING PACKET : "+this+"\nPacket = " + p + "\n");
        }

        writer.sendPacket(p, close);
    }

    /**
     * Marks this connection as closed.
     */
    private void linkDown() {
        linkDown(false);
    }

    private void linkDown(boolean broken) {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.linkDown()");
        }

        if (ClusterManagerImpl.isDEBUG_CLUSTER_ALL() || 
            ClusterManagerImpl.isDEBUG_CLUSTER_CONN()) {
            logger.log(Logger.INFO,
                "Link down" +
                "\n\tRemote BrokerAddress = " + getRemoteString() +
                "\n\tRemote IP = " + conn.getInetAddress() +
                "\n\tRemote Port = " + conn.getPort() +
                "\n\tLocal IP = " + conn.getLocalAddress() +
                "\n\tLocal Port = " + conn.getLocalPort());
        }

        if (DEBUG) {
            logger.log(Logger.DEBUGMED, "Cluster connection closed.");
        }

        brokerListLock.lock();
        try {

        synchronized (this) {
            if (writer == null) {
                // Wait for 5 seconds before retrying the connection.
                try {
                    Thread.sleep(5000);
                }
                catch (Exception e) {
                    // we were interrupted, give up
                }
                connected = false;
                return;
            }

            writer.shutdown();
            writer = null;

            try {
                is.close();
                os.close();
                conn.close();
            }
            catch (Exception e) { /* Ignored */ }

            connected = false;
            is = null;
            os = null;
        } // synchronized this

        parent.removeBroker(remote, this, broken);

        } finally {
        brokerListLock.unlock();
        }

        // Wait for 5 seconds before retrying the connection.
        try {
            Thread.sleep(5000);
        }
        catch (Exception e) {}
    }

    private static SSLSocketFactory factory = null;
    private static synchronized SSLSocketFactory getTrustSocketFactory()
        throws Exception {
        if (factory == null) {
            SSLContext ctx;
            ctx = SSLContext.getInstance("TLS");

            TrustManager[] tm = new TrustManager[1];
            tm[0] = new DefaultTrustManager();

            ctx.init(null, tm, null);

            factory = ctx.getSocketFactory();
        }

        return factory;
    }

    public static Socket makeSocket(BrokerAddressImpl remoteBroker,
                                    ClusterImpl parent, boolean configure, Map props)
                                    throws Exception {
        Socket socket = null;

        PortMapperEntry pme = getRealRemotePort(remoteBroker);
        if (pme == null) {
            throw new BrokerException(br.getKString(
            br.X_CLUSTER_CANNOT_GET_REMOTE_SERVICE_PORT, getRemoteString(null, remoteBroker)));
        }
        if (!pme.getProtocol().equalsIgnoreCase(parent.getTransport())) {
            throw new BrokerException(br.getKString(BrokerResources.X_CLUSTER_TRANSPORT_MISMATCH,
                                      parent.getTransport(), pme.getProtocol()));
        }
        int remotePort = pme.getPort();
        String h = pme.getProperty("hostaddr");
        InetAddress laddr = parent.getListenHost();
        boolean nodelay = parent.getTCPNodelay();

        boolean ssl = false;
        if (pme.getProtocol().equalsIgnoreCase("ssl")) {
            nodelay = parent.getSSLNodelay();
            ssl = true;
            props.put("ssl", "true");
        }

        if (laddr == null) {
            if (ssl) {
                socket = makeSSLSocket((h == null ? remoteBroker.getHost():InetAddress.getByName(h)),
                                       remotePort, null, 0);
            } else {
                socket = new Socket((h == null ? remoteBroker.getHost():InetAddress.getByName(h)),
                                    remotePort);
            }
        } else {
            if (ssl) {
                socket = makeSSLSocket((h == null ? remoteBroker.getHost():InetAddress.getByName(h)),
                                       remotePort, laddr, 0);
            } else {
                socket = new Socket((h == null ? remoteBroker.getHost():InetAddress.getByName(h)),
                                    remotePort, laddr, 0);
            }
        }

        if (configure) {
            try {
                socket.setTcpNoDelay(nodelay);
            } catch (SocketException e) {
                logger.log(Logger.WARNING, "BrokerLink.makeSocket("+remoteBroker+")."+
                           "setTcpNoDelay("+nodelay+"): "+ e.toString());
            }
        }
        return socket;
    }

    private static Socket makeSSLSocket(
        InetAddress host, int port,
        InetAddress localhost, int localport
    ) throws Exception {

        Socket sock;
        if (Globals.getConfig().getBooleanProperty(
            Globals.IMQ + ".cluster.trust_all", true)) {
            SSLSocketFactory sslFactory = getTrustSocketFactory();
            if (localhost == null) {
              sock = sslFactory.createSocket(host, port);
            } else {
              sock = sslFactory.createSocket(host, port, localhost, localport);
            }
            
        } else {
            if (localhost == null) {
                sock = SSLSocketFactory.getDefault().createSocket(host, port);
            } else {
                sock = SSLSocketFactory.getDefault().createSocket(host, port, localhost, localport);
            }
        }
        if (Globals.getPoodleFixEnabled()) {
            Globals.applyPoodleFix(sock, "BrokerLink");
        }
        return sock;
    }

    /**
     * Contacts the remote portmapper and resolves the port number.
     */
    private static PortMapperEntry getRealRemotePort(BrokerAddressImpl remoteBroker) throws Exception {
        String version =
            String.valueOf(PortMapperTable.PORTMAPPER_VERSION) + "\n";
        PortMapperTable pt = new PortMapperTable();

        Socket s = new Socket(remoteBroker.getHost(), remoteBroker.getPort());

        InputStream is = s.getInputStream();
        OutputStream os = s.getOutputStream();

        try {
            os.write(version.getBytes());
            os.flush();
        } catch (IOException e) {
            // This can sometimes fail if the server already wrote
            // the port table and closed the connection
            // Ignore...
        }
        pt.read(is);

        is.close();
        os.close();
        s.close();

        return pt.get(ClusterImpl.SERVICE_NAME);
    }

    /**
     * Attempts to initiate a broker-to-broker connection.
     */
    private void createLink() {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.createLink()");
        }

        brokerListLock.lock();
        try {

        BrokerAddressImpl ba = null;
        synchronized(this) {

        if (autoConnect == false)
            return;

        if (connected == true)
            return;

        // We have to initiate the connection.
        try {
            Map props = new HashMap();
            conn = makeSocket(remote, parent, true, props);

            boolean ssl = (props.get("ssl") != null);
            initNewConn(false, ssl);
            connected = true;

            ba = (BrokerAddressImpl)consumeLinkInit(conn, this, parent, false);
        } catch (Exception e) {
           if (connected ==  false) {
             if (conn != null) {
                try { 
                   conn.close();
                } catch (Exception ce) {/* Ignore */}
             }
             if (writer != null) {
                writer.shutdown();
                writer = null;
             }
           }
           if (createLinkFailures%40 == 0) {
             String msg = e.getMessage();
             if (msg == null) msg = e.getClass().getName();
             logger.log(Logger.WARNING, BrokerResources.W_CLUSTER_LINKINIT_EXCEPTION,
                        getRemoteString(), msg);
             logger.logStack(Logger.DEBUG, "BrokerLink.createLink() failed", e);
           }
           createLinkFailures++;
            // If the first connection attempt fails, it is safe to
            // assume that the link is initialized but the remote
            // broker is down...
            synchronized (linkInitWaitObject) {
                if (! linkInitDone) {
                    linkInitDone = true;
                    linkInitWaitObject.notifyAll();
                }
            }
        } catch (OutOfMemoryError oom) {
            try {
                if (conn != null) {
                    try {
                    conn.close();
                    } catch (Exception e) {}
                }
                linkDown();
            } finally {
                logger.log(Logger.WARNING, oomStr);
            }
        }

        } // synchronized this

        if (ba != null) {
            if (parent.addBroker(remote, this) == false) {
                closeConn();
            }
        }

        } finally {
        brokerListLock.unlock();
        }

        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.createLink() finished.");
        }
    }

    /**
     * Process the LINK_INIT packet for connections
     * initiated by this BrokerLink thread..
     * @param returns BrokerAddressImpl for link request
     *        else LinkInfo if allowNonLinkRequest true
     */
    protected static Object consumeLinkInit(Socket s,
                     BrokerLink l, ClusterImpl cl, boolean allowNonLinkRequest)
                     throws IOException {
        if (DEBUG) {
            logger.log(logger.INFO, "BrokerLink.consumeLinkInit("+allowNonLinkRequest+")");
        }
        BrokerAddressImpl b = null;

        InputStream is = s.getInputStream();
        Packet p = new Packet();
        p.readPacket(is);

        if (ClusterManagerImpl.isDEBUG_CLUSTER_PACKET() ||
            (ClusterManagerImpl.isDEBUG_CLUSTER_PING() && p.getPacketType() == Packet.PING) || 
            ClusterManagerImpl.isDEBUG_CLUSTER_PACKET()) {
            logger.log(Logger.INFO, "RECEIVING PACKET : "+(l == null ? "from "+s.getInetAddress():l)+"\nPacket = " + p);
        }

        if (p.getPacketType() != Packet.LINK_INIT) {
            if (DEBUG) {
            logger.log(logger.INFO, ((l == null) ? ("Socket="+s.getInetAddress()):"Link="+l)
                                     +", expect LINK_INIT but got:" + p.getPacketType());
            }
            s.close();
            return null;
        }

        LinkInfo li = null;
        try {
            li = ClusterImpl.processLinkInit(p);
            b = li.getAddress();
            if (l != null && b.getMQAddress().equals(l.self.getMQAddress())) {
                String args[] = { b.getHost().toString(), 
                                  s.getInetAddress().toString(),
                                  l.self.toString() + " <---> " + b.toString() };
                throw new BrokerException(
                      br.getString(br.E_MBUS_BAD_ADDRESS, args));
            }
            if (l != null && b.equals(l.self)) {
                String args[] = { b.toShortString(),
                                  s.getInetAddress().toString(),
                                  l.self.toString() + " <---> " + b.toString() };
                logger.log(Logger.ERROR, br.getKString(br.E_MBUS_SAME_ADDRESS_AS_ME, args));
                Broker.getBroker().exit(1,
                       br.getString(br.E_MBUS_SAME_ADDRESS_AS_ME, args),
                       BrokerEvent.Type.FATAL_ERROR, null, false, true, false);
                throw new BrokerException(
                      br.getString(br.E_MBUS_SAME_ADDRESS_AS_ME, args));
            }
        } catch (BrokerException e) {
            logger.log(Logger.ERROR, br.getKString(
                   br.E_CLUSTER_BAD_LINK_INIT, 
                   s.getInetAddress().toString()), e);
            if (l == null)  s.close(); else l.shutdown(); 
            return null;
        } catch (Exception e) {
            if (DEBUG) {
            logger.logStack(Logger.ERROR, s.getInetAddress().toString(), e);
            }
            s.close();
            return null;
        }

        if (DEBUG) {
            logger.log(logger.INFO, "Return from processLinkInit()");
        }

        if (!li.isLinkRequest()) {
            if (!allowNonLinkRequest) {
                logger.log(Logger.ERROR, br.getKString(
                    br.E_CLUSTER_UNEXPECTED_PACKET_FROM, 
                    "LINK_INIT["+li.getAddress().getClusterVersion()+"]",
                    s.getInetAddress().toString()));
                if (l == null)  s.close(); else l.shutdown(); 
                return null;
            }
            return li ;
        }

        if (l != null) l.setRemote(b);

        if (cl.checkConfigServer(b) == false) {
            BrokerAddressImpl master = (BrokerAddressImpl)cl.getConfiguredConfigServer();
            if (master != null) { 

            Integer lognum = (Integer)waitingMasterLogs.get(b.getMQAddress());
            if (lognum == null || lognum.intValue()%30 == 0) {
                logger.log(Logger.INFO, br.I_CLUSTER_WAITING_MASTER, b, master);
            } else {
                logger.log(Logger.DEBUG, br.I_CLUSTER_WAITING_MASTER, b, master);
            }
            synchronized(waitingMasterLogs) {
                Integer num = (Integer)waitingMasterLogs.get(b.getMQAddress());
                if (num == null) waitingMasterLogs.put(b.getMQAddress(), Integer.valueOf(1));
                else waitingMasterLogs.put(b.getMQAddress(), Integer.valueOf(num.intValue()+1));
            }

            } else { //should never happen
            logger.log(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR,
             "No master broker. Closing cluster connection with "+ b);
            }
            // Not ready yet.
            // Need to sync with config server first.
            s.close();
            return null;
        }

        /*
         * Make sure that remote broker is using the
         * same 'config server'. If not, something is
         * very wrong with the configuration. Log an
         * error and stop talking to that broker (i.e.
         * kill this BrokerLink).
         */
        try {
            BrokerAddress mymaster = cl.getConfigServer();
            BrokerAddress remotemaster = li.getConfigServer();
            
            if (((mymaster == null || remotemaster == null) && 
                 mymaster != remotemaster)
                 ||
                ((mymaster != null && remotemaster != null) &&
                 !(mymaster.getMQAddress().equals(
                                        remotemaster.getMQAddress())))) {

                String[] args = new String[] { b.toString(),
                    (mymaster == null ? "null":mymaster.getMQAddress().toString()),
                    (remotemaster == null ? "null":remotemaster.getMQAddress().toString()) };
                logger.log(Logger.ERROR, br.getKString(
                           BrokerResources.E_MBUS_CONFIG_MISMATCH1, args));

                if (l == null)  s.close(); else l.shutdown();
                return null;
            }
        }
        catch (Exception e) {
            logger.logStack(Logger.DEBUG, e.getMessage() 
                   + ((l == null) ? ("Socket "+s.getInetAddress()) :("Link " + l)) , e);
            if (l == null)  s.close(); else l.linkDown();
            return null;
        }

        if (DEBUG) {
            logger.log(Logger.INFO, "remote.matchProps = " + li.getMatchProps());
            logger.log(Logger.INFO, "local.matchProps = " + cl.getMatchProps());
        }
        Properties remoteProps = li.getMatchProps();
        Properties myProps = cl.getMatchProps();

        String diff = BrokerLink.compareProps(myProps, remoteProps);
        if (diff != null) {
            logger.log(Logger.ERROR, br.E_MBUS_CONFIG_MISMATCH2, b, diff);

            if (l == null)  s.close(); else l.shutdown();
            return null;
        }

        if (DEBUG) {
            logger.log(logger.INFO, "BrokerLink.consumeLinkInit() finished");
        }

        assert (b != null );
        return b; 

    }

    /**
     * Accepts a broker-to-broker connection.
     *
     * There is one common 'ClusterListener' object per broker that
     * accepts the actual TCP connections. After reading the first
     * packet (LINK_INIT), this method is invoked on the correct
     * BrokerLink object...
     */
    public boolean acceptConnection(BrokerAddressImpl remote, Socket remoteConn, boolean ssl) {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.acceptConnection()");
        }

        brokerListLock.lock();
        try {

        synchronized(this) {

        setRemote(remote);

        if (connected) {
            if (DEBUG)
                logger.log(Logger.DEBUG, "Already connected!");

            try {
                remoteConn.close();
            }
            catch (Exception e) { /* Ignored */ }
            return false;
        }

        if (parent.addBroker(remote, this) == false) {
            try {
                remoteConn.close();
            } catch (Exception e) { /* Ignored */ }
            return false;
        }

        this.conn = remoteConn;

        try {
            initNewConn(true, ssl);
        }
        catch (Exception e) {
            return true;
        }

        connected = true;

        } //synchronized this


        } finally {
        brokerListLock.unlock();
        }

        return true;
    }

    private void initNewConn(boolean accepted, boolean ssl) throws IOException {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.initNewconn()");
        }

        if (ClusterManagerImpl.isDEBUG_CLUSTER_ALL() || 
            ClusterManagerImpl.isDEBUG_CLUSTER_CONN()) {
            String s = (accepted ? "Accepted" : "Established");
            logger.log(Logger.INFO,
                "Connection " + s +
                "\n\tRemote BrokerAddress = " + getRemoteString() +
                "\n\tRemote IP = " + conn.getInetAddress() +
                "\n\tRemote Port = " + conn.getPort() +
                "\n\tLocal IP = " + conn.getLocalAddress() +
                "\n\tLocal Port = " + conn.getLocalPort());
        }

        // LINK_INFO has already been consumed. The next packet must
        // be BROKER_INFO...
        expectBrokerInfoPkt = true;

        synchronized(handshakeLock) {
            handshakeSent = false;
        }
        firstInfoSent = false;
        firstReceive = true;

        int inbufsize = parent.getTCPInputBufferSize();
        int outbufsize = parent.getTCPOutputBufferSize();
        if (ssl) { 
            inbufsize = parent.getSSLInputBufferSize();
            outbufsize = parent.getSSLOutputBufferSize();
        }
        is = conn.getInputStream();
        if (inbufsize > 0) {
            is = new BufferedInputStream(is, inbufsize);
        }
        os = conn.getOutputStream();
        if (outbufsize > 0) {
            os = new BufferedOutputStream(os, outbufsize);
        }

        writer = new BrokerLinkWriter(this);
        writer.startWriterThread(os);

        Packet linkInitPkt = parent.getLinkInitPkt();
        Packet brokerInfoPkt = parent.getBrokerInfoPkt();

        if (DEBUG) {
            logger.log(Logger.DEBUGMED,
                "Cluster connection established: {0}", this);
        }

        sendPacket(linkInitPkt);
        sendPacket(brokerInfoPkt);

        parent.sendFlowControlUpdate(remote);
    }

    public void closeConn() {
        closeConn(false);
    }

    public void closeConn(boolean force) {
        closeConn(false, force);
    }

    protected void closeConn(boolean soft, boolean force) {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.closeConn()");
        }

        if (! connected && autoConnect == false) {
            try {
            interrupt();
            } catch (Exception e) {
            logger.log(logger.DEBUG, "BrokerLink.closeConn(): interrupt thread failed: "+e.getMessage());
            }
        }
        
        try {
            if (soft) {
                conn.shutdownOutput();
                return;
            }
            if (force) {
                conn.close();
                return;
            }
            if (!writer.isOutputShutdown()) {
                conn.close();
                return;
            }
        }
        catch (Exception e) { /* Ignored */ }
    }

    protected boolean isOutputShutdown() {
        if (conn == null) return false;
        return conn.isOutputShutdown();
    }

    public void shutdown() {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.shutdown()");
        }

        autoConnect = false;
        closeConn();
    }

    private static String compareProps(Properties p1, Properties p2) {
        StringBuffer ret = new StringBuffer();

        for (Enumeration e = p1.propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            String v1 = p1.getProperty(name);
            String v2 = p2.getProperty(name);

            if (v1 == null && v2 == null)
                continue;

            if ((v1 == null && v2 != null) ||
                (v2 == null && v1 != null)) {
                ret.append("\t" + name+"="+v1+","+v2+"\n");
                continue;
            }

            if (! v1.equals(v2)) {
                ret.append("\t" + name + "="+v1+","+v2+"\n");
                continue;
            }
        }

        if (ret.length() == 0) {
            return null;
        }
        return ret.toString();
    }

    private Packet tryReadPacket(boolean handleOom) throws IOException {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.tryReadPacket()");
        }

        Packet p = null;
        try {
            p = new Packet();
            p.readPacket(is);
            readActive = true;
        }
        catch (OutOfMemoryError oom) {
            if (handleOom == false)
                throw oom;

            // First, try to free some space.
            Globals.handleGlobalError(oom,
                br.getKString(br.M_LOW_MEMORY_CLUSTER));

            // Now try to read the packet again.
            p = tryReadPacket(false);
        }
        return p;
    }

    private void consumeBrokerInfoPkt() throws Exception {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.consumeBrokerInfoPkt()");
        }
        expectBrokerInfoReplyPkt = false;

        Packet p = new Packet();
        p.readPacket(is);

        if (ClusterManagerImpl.isDEBUG_CLUSTER_PACKET() ||
            (ClusterManagerImpl.isDEBUG_CLUSTER_PING() && p.getPacketType() == Packet.PING) || 
            ClusterManagerImpl.isDEBUG_CLUSTER_PACKET()) {
            logger.log(Logger.INFO, "RECEIVING PACKET : "+this+"\nPacket = " + p);
        }

        if (p.getPacketType() != Packet.BROKER_INFO) {
            logger.log(logger.DEBUG, "Link = " + this +
                ", Missed BROKER_INFO : " + p.getPacketType());

            conn.close();
            return;
        }

        BrokerInfo bi = (BrokerInfo)parent.receivePacket(remote, p, getRealRemoteString(conn), this);
        if (bi == null) {
            logger.log(logger.DEBUG, "Link = " + this + ", BROKER_INFO rejected");
            throw new IOException("BrokerInfo rejected");
        }

        expectBrokerInfoPkt = false;

        Integer v = bi.getClusterProtocolVersion();
        if (v != null && v.intValue() >= ProtocolGlobals.VERSION_400) {
            com.sun.messaging.jmq.jmsserver.core.BrokerAddress configServer = null;
            try {
                configServer = parent.getConfigServer();
            } catch (Exception e) {
                conn.close();
                logger.log(logger.DEBUG, 
                "Exception in getConfigServer: "+e.getMessage()+", link "+this);
                return;
            }
            if (parent.waitForConfigSync() && 
                !configServer.equals(bi.getBrokerAddr())) {
                if (ClusterManagerImpl.isDEBUG_CLUSTER_CONN() ||
                    ClusterManagerImpl.isDEBUG_CLUSTER_PACKET() || DEBUG) {
                logger.log(logger.INFO, "Waiting for sync with master broker "+configServer+", Please retry  "+this);
                }
                conn.close();
                return;
            }
            ClusterBrokerInfoReply cbi = parent.getBrokerInfoReply(bi);
            GPacket gp = cbi.getGPacket();
            boolean shutdownOutput = cbi.sendAndClose();
            sendPacket(gp, shutdownOutput, false);
            expectBrokerInfoReplyPkt = true;
            if (shutdownOutput) return;

        } 
        synchronized(handshakeLock) {
            handshakeSent = true;
        }

        // Assume that the link is ready after the
        // BROKER_INFO packet has been consumed.
        synchronized (linkInitWaitObject) {
            if (parent.isConfigServerResolved()) {
                linkInitDone = true;
                linkInitWaitObject.notifyAll();
            }
        }
    }

    protected void handshakeSent() {
        handshakeSent = true;
    }

    private void consumeBrokerInfoReplyPkt() throws Exception {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.consumeBrokerInfoReplyPkt()");
        }

        GPacket gp = GPacket.getInstance();
        gp.read(is);

        if (gp.getType() != ProtocolGlobals.G_BROKER_INFO_REPLY) {
            logger.log(logger.DEBUG, "Link = " + this +
                ", Missed BROKER_INFO_REPLY : " + gp.getType());

            conn.close();
            return;
        }
        parent.receivePacket(remote, gp, getRealRemoteString(conn));
        gp = parent.getFirstInfoPacket();
        if (gp != null) {
            sendPacket(gp);
        }
        
    }


    private void consumePacket() throws IOException {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.consumePacket()");
        }

        Packet p = new Packet();

        try {
            p = tryReadPacket(true);
        }
        catch (OutOfMemoryError oom) {
            logger.log(Logger.ERROR, br.E_MBUS_LOW_MEMORY_FAILED);
            Broker.getBroker().exit(Globals.getBrokerStateHandler().getRestartCode(),
                  br.getString(br.E_MBUS_LOW_MEMORY_FAILED),
                  BrokerEvent.Type.ERROR);
        }

        if (ClusterManagerImpl.isDEBUG_CLUSTER_PACKET() ||
            (ClusterManagerImpl.isDEBUG_CLUSTER_PING() && p.getPacketType() == Packet.PING) || 
            ClusterManagerImpl.isDEBUG_CLUSTER_PACKET()) {
            logger.log(Logger.INFO, "RECEIVING PACKET : "+this+"\nPacket = " + p);
        }

        try {
            parent.receivePacket(remote, p, getRealRemoteString(conn), this);
        }
        catch (Exception e) {
            logger.logStack(Logger.ERROR, br.W_MBUS_RCVPKT_ERROR,
                p, e);
        }
    }

    private void consumeGPacket() throws IOException {
        if (DEBUG) {
            logger.log(logger.DEBUG, "BrokerLink.consumeGPacket()");
        }

        GPacket gp = GPacket.getInstance();
        gp.read(is);
        readActive = true;

        if (ClusterManagerImpl.isDEBUG_CLUSTER_PACKET() || 
            (ClusterManagerImpl.isDEBUG_CLUSTER_PING() && 
             (gp.getType() == ProtocolGlobals.G_PING || gp.getType() == ProtocolGlobals.G_PING_REPLY)) ||
            ClusterManagerImpl.isDEBUG_CLUSTER_ALL()) {
            logger.log(Logger.INFO, "RECEIVING PACKET : "+this+"\nPacket = " + gp.toLongString());
            if (logger.getLevel() <= Logger.DEBUG && gp.getPayload() != null) {
                byte[] buf = gp.getPayload().array();
                logger.log(Logger.DEBUG, "Payload : " + Packet.hexdump(buf, Integer.MAX_VALUE));
            }
        }
        if (firstReceive) {
            firstReceive = false;
            if (gp.getType() == ProtocolGlobals.G_FIRST_INFO) {
                parent.processFirstInfoPacket(gp, this);
                return;
            }
        }

        try {
            parent.receivePacket(remote, gp, null);
        }
        catch (Exception e) {
            logger.logStack(Logger.ERROR, br.W_MBUS_RCVPKT_ERROR, gp, e);
        }
    }

    boolean isIOActive() { 
        boolean writeActive = isWriteActive();
        return readActive || writeActive;
    }

    private boolean isWriteActive() {
        boolean writeActive = false;
        try {
        if (writer != null) writeActive = writer.isWriteActive();
        } catch (Exception e) {/* Ignore */
            logger.log(Logger.DEBUGHIGH,"Ignoring exception on isIOActive", e);
        }
        return writeActive;
    }

    void enablePingLogging() {
        pingLogging.set(true);
    }

    void logIOActiveInPingInterval(int pingInterval) {
        if (pingLogging.get()) {
            logger.log(Logger.INFO,  br.getKString(
                BrokerResources.I_BROKER_LINK_IOACTIVE_IN_PING_INTERVAL, 
                this+"["+(readActive ? "r":"")+(isWriteActive() ? "w":"")+"]", 
                String.valueOf(pingInterval)+" seconds"));
            pingLogging.set(false);
        }
    }

    protected void clearIOActiveFlag() {
        readActive = false;
        try {
        if (writer != null) writer.clearWriteActiveFlag();
        } catch (Exception e) {/* Ignore */
            logger.log(Logger.DEBUGHIGH,"Ignoring exception on clearIOActiveFlag",
                e);
        }
    }

    public void run() {
        while (true) {
            if (connected == false) {
                if (autoConnect == false)
                    break;

                createLink();
                if (! connected) {
                    try {
                        Thread.sleep(5000);
                    }
                    catch (Exception e) {}
                }
                continue;
            }

            try {
                if (expectBrokerInfoPkt) {
                    if (DEBUG) {
                        logger.log(logger.DEBUG,
                            "Waiting for BROKER_INFO...");
                    }

                    consumeBrokerInfoPkt();
                   
                    if (expectBrokerInfoReplyPkt) consumeBrokerInfoReplyPkt();

                    if (DEBUG) {
                        logger.log(logger.DEBUG, "Received BROKER_INFO...");
                    }
                }

                try {
                    if (parent.useGPackets) {
                        consumeGPacket();
                    } else {
                        consumePacket();
                    }
                } catch (IOException e) {
                    logger.log(logger.INFO, "IOException on link "+this);
                    linkDown(true);
                }
            } catch (OutOfMemoryError oom) {
                logger.log(Logger.ERROR, br.E_MBUS_LOW_MEMORY_FAILED);
                Broker.getBroker().exit(Globals.getBrokerStateHandler().getRestartCode(),
                      br.getString(br.E_MBUS_LOW_MEMORY_FAILED),
                      BrokerEvent.Type.ERROR);
            } catch (Exception e) {
                logger.logStack(Logger.DEBUG, "Link Down " + this , e);
                linkDown();
            }
        }

        // Thread exiting. Don't wait for this link.
        synchronized (linkInitWaitObject) {
            linkInitDone = true;
            linkInitWaitObject.notifyAll();
        }

        // Tell the parent that this BrokerLink thread is going down...
        parent.handleBrokerLinkShutdown(remote);
    }

    public String toString() {
        String clistener = parent.getServerSocketString(); 
        if (clistener == null)  {
            return self.toString() + " <---> " + getRemoteString();
        }
        return self.toString()+"["+clistener+"]"+ " <---> " + getRemoteString();
    }
}

class BrokerListLock {
    
    private Thread owner = null;
    private boolean locked = false;

    public BrokerListLock() {};

    public synchronized void lock() {
        Thread me = Thread.currentThread();

        while (locked && owner != me) {
            try {
            wait();
            } catch (InterruptedException e) {}
        }
        assert (locked == false && owner == null) || (locked == true && owner == me);

        locked = true;
        owner = me;
    }

    public synchronized void unlock() {
        Thread me = Thread.currentThread();

        assert locked == true;
        assert owner == me;

        locked = false;
        owner = null;
        notifyAll();
    }
}

/*
 * EOF
 */
