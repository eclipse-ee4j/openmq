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

package com.sun.messaging.portunif;

import java.util.Set;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.io.IOException;
import java.util.Properties;
import java.net.SocketAddress;
import org.glassfish.grizzly.portunif.PUFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection; 
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.portunif.finders.SSLProtocolFinder;
import org.glassfish.grizzly.portunif.PUProtocol;
import org.glassfish.grizzly.filterchain.FilterChain; 

public class PUService {

    private PUFilter rootpuf = null;
    private PUFilter sslpuf = null;
    private TCPNIOTransport puTransport = null; 
    private SocketAddress bindAddr = null;
    private boolean sslClientAuthRequired = false;
    private PUProtocol endPUProtocol = null;
    private PUProtocol endPUProtocolSSL = null;
    private TCPNIOServerConnection serverConn = null;

    public PUService() {
        rootpuf = new PUFilter();
        final FilterChainBuilder puFilterChainBuilder =
                                   FilterChainBuilder.stateless()
                                   .add(new TransportFilter())
                                   .add(rootpuf);
        puTransport = TCPNIOTransportBuilder.newInstance().build();
        puTransport.setProcessor(puFilterChainBuilder.build());

    }

    public synchronized void bind(SocketAddress saddr, int backlog)
    throws IOException {

        if (puTransport == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        if (bindAddr == null) {
            serverConn = puTransport.bind(saddr, backlog);
            bindAddr = saddr;
        } else if (!bindAddr.equals(saddr)) {
            puTransport.stop();
            serverConn = puTransport.bind(saddr, backlog);
            bindAddr = saddr;
        }
    }

    public synchronized void rebind(SocketAddress saddr, int backlog)
    throws IOException {
        if (puTransport == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        if (!saddr.equals(bindAddr)) {
            puTransport.stop();
            serverConn = puTransport.bind(saddr, backlog);
            bindAddr = saddr;
        }
    }

    public synchronized SocketAddress start() throws IOException {
        if (puTransport == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        puTransport.start();
        return bindAddr;
    }

    public synchronized SocketAddress getBindSocketAddress() 
    throws IOException {
        if (puTransport == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        if (bindAddr == null) {
            throw new IOException("PUService not bound yet");
        }
        return bindAddr;
    }

    public synchronized void setBacklog(int backlog) throws IOException {
        if (puTransport == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        puTransport.setServerConnectionBackLog(backlog);
    }

    private synchronized void preRegister(PUServiceCallback cb)
    throws IOException {
        if (endPUProtocol == null) {
            endPUProtocol = new PUProtocol(new EndProtocolFinder(cb),
                            rootpuf.getPUFilterChainBuilder().build());
        }
        rootpuf.deregister(endPUProtocol);
    }

    private synchronized void postRegister()
    throws IOException {
        rootpuf.register(endPUProtocol);
    }

    private synchronized void preRegisterSSL(PUServiceCallback cb)
    throws IOException {
        if (endPUProtocolSSL == null) {
            endPUProtocolSSL = new PUProtocol(new EndProtocolFinder(cb),
                               sslpuf.getPUFilterChainBuilder().build());
        }
        sslpuf.deregister(endPUProtocolSSL);
    }

    private synchronized void postRegisterSSL()
    throws IOException {
        sslpuf.register(endPUProtocolSSL);
    }

    public synchronized void register(PUProtocol pp, PUServiceCallback cb)
    throws IOException {
        if (rootpuf == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        preRegister(cb);
        try {
            rootpuf.register(pp);
        } finally {
            postRegister();
        }
    }

    public synchronized void deregister(PUProtocol pp) throws IOException {
        if (rootpuf == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        rootpuf.deregister(pp);
    }

    public synchronized void registerSSL(PUProtocol pp, PUServiceCallback cb)
    throws IOException {
        if (rootpuf == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        if (sslpuf == null) {
            throw new IOException("Illegal call: PUService SSL not initialized");
        }
        preRegisterSSL(cb);
        try {
            sslpuf.register(pp);
        } finally { 
            postRegisterSSL();
        }
    }

    public synchronized void deregisterSSL(PUProtocol pp) throws IOException {
       if (rootpuf == null) {
           throw new IOException("Illegal call: PUService not initialized");
       }
       if (sslpuf == null) {
           throw new IOException("Illegal call: PUService SSL not initialized");
       }
       sslpuf.deregister(pp);
    }

    public synchronized void stop() throws IOException {
        if (puTransport == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        puTransport.stop();
    }

    public synchronized void destroy() throws IOException {
        if (puTransport != null) {
            puTransport.stop();
            puTransport = null;
            rootpuf = null;
        }
    }

    public synchronized FilterChainBuilder getPUFilterChainBuilder()
    throws IOException {
        if (rootpuf == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        return rootpuf.getPUFilterChainBuilder();
    }

    public synchronized FilterChainBuilder getSSLPUFilterChainBuilder()
    throws IOException {
        if (rootpuf == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        if (sslpuf == null) {
            throw new IOException("Illegal call: PUService SSL not initialized");
        }
        return sslpuf.getPUFilterChainBuilder();
    }

    /**
     */
    public synchronized boolean getSSLClientAuthRequired() {
        return sslClientAuthRequired;
    }

    /**
     */
    public synchronized boolean initializeSSL(
        Properties props, boolean clientAuthRequired, 
        PUServiceCallback cb, boolean poodleFixEnabled, 
        String[] knownSSLEnabledProtocols)
        throws IOException { 

        if (rootpuf == null) {
            throw new IOException("Illegal call: PUService not initialized");
        }
        if (sslpuf != null) { 
            return false;
        }

        SSLContextConfigurator sslcf = createSSLContextConfigrattor(props);
        if (!sslcf.validateConfiguration(true)) {
            throw new IOException("Invalid SSL context configuration:"+sslcf);
        }
        SSLEngineConfigurator clientc = new SSLEngineConfigurator(sslcf.createSSLContext());
        SSLEngineConfigurator serverc = new SSLEngineConfigurator(sslcf.createSSLContext(),
                                            false, clientAuthRequired, clientAuthRequired);
        if (poodleFixEnabled) {
            applyPoodleFix(clientc, knownSSLEnabledProtocols, "PUService");
            applyPoodleFix(serverc, knownSSLEnabledProtocols, "PUService");
        }

        sslpuf = new PUFilter();
        FilterChain sslProtocolFilterChain = rootpuf.getPUFilterChainBuilder()
                               .add(new SSLFilter(serverc, clientc))
                               .add(sslpuf).build();
        PUProtocol pu = new PUProtocol(new SSLProtocolFinder(serverc),
                                       sslProtocolFilterChain);
        try {
            register(pu, cb);
            this.sslClientAuthRequired = clientAuthRequired;
            return true;
        } catch (Exception e) { 
            sslpuf = null;
            this.sslClientAuthRequired = false;
            if (e instanceof IOException) {
                throw (IOException)e;
            }
            throw new IOException(e.toString(), e);
        }
    }

    public static SSLContextConfigurator 
    createSSLContextConfigrattor(Properties props) {

        SSLContextConfigurator sslcf = new SSLContextConfigurator();
        sslcf.setKeyManagerFactoryAlgorithm(props.getProperty(KEYSTORE_ALGORITHM));
        sslcf.setKeyStoreType(props.getProperty(KEYSTORE_TYPE));
        sslcf.setKeyStoreFile(props.getProperty(KEYSTORE_FILE));
        sslcf.setKeyStorePass(props.getProperty(TRUSTSTORE_PASSWORD));

        sslcf.setTrustManagerFactoryAlgorithm(props.getProperty(TRUSTSTORE_ALGORITHM));
        sslcf.setTrustStoreType(props.getProperty(TRUSTSTORE_TYPE));
        sslcf.setTrustStoreFile(props.getProperty(TRUSTSTORE_FILE));
        sslcf.setTrustStorePass(props.getProperty(TRUSTSTORE_PASSWORD));

        sslcf.setSecurityProtocol(props.getProperty(SECURESOCKET_PROTOCOL));
        return sslcf;
    }

    public static void applyPoodleFix(SSLEngineConfigurator ec, 
        String[] knownSSLEnabledProtocols, String caller) {

        String[] protocols = ec.getEnabledProtocols();
        if (protocols == null) {
            protocols = knownSSLEnabledProtocols;
        }
        String orig = Arrays.toString(protocols);

        Set<String> set = new LinkedHashSet<String>();
        for (String s : protocols) {
            if (s.equals("SSLv3")) {
                continue;
            }
            set.add(s);
        }
        protocols = set.toArray(new String[set.size()]);
        System.out.println("["+caller+"]: ["+orig+
            "], setEnabledProtocols:["+Arrays.toString(protocols)+"]");
        ec.setEnabledProtocols(protocols);
        return;
    }

    public static final String KEYSTORE_ALGORITHM = "ssl.KeyManagerFactory.algorithm";
    public static final String KEYSTORE_TYPE = "javax.net.ssl.keyStoreType";
    public static final String KEYSTORE_FILE = "javax.net.ssl.keyStore";
    public static final String KEYSTORE_PASSWORD = "javax.net.ssl.keyStorePassword";


    public static final String TRUSTSTORE_ALGORITHM = "ssl.TrustManagerFactory.algorithm";
    public static final String TRUSTSTORE_TYPE = "javax.net.ssl.trustStoreType";
    public static final String TRUSTSTORE_FILE = "javax.net.ssl.trustStore";
    public static final String TRUSTSTORE_PASSWORD = "javax.net.ssl.trustStorePassword";

    public static final String SECURESOCKET_PROTOCOL = "securesocket.protocol";
}

