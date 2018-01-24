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

package com.sun.messaging.bridge.service.stomp;

import java.io.*;
import java.util.Locale;
import java.util.Properties;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.net.URL;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset; 
import javax.jms.Message;
import javax.jms.ConnectionFactory;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.portunif.PUProtocol;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.ssl.SSLFilter; 
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import com.sun.messaging.portunif.PUService;
import com.sun.messaging.portunif.StompProtocolFinder;
import com.sun.messaging.bridge.api.BridgeContext;
import com.sun.messaging.bridge.api.MessageTransformer;
import com.sun.messaging.bridge.api.LogSimpleFormatter;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.service.stomp.resources.StompBridgeResources;

/**
 * @author amyk 
 */
public class StompServer {

    private final static String PROP_HOSTNAME_SUFFIX = ".hostname";
    private final static String PROP_TCPENABLED_SUFFIX = ".tcp.enabled";
    private final static String PROP_SSLENABLED_SUFFIX = ".tls.enabled";
    private final static String PROP_TCPPORT_SUFFIX = ".tcp.port";
    private final static String PROP_SSLPORT_SUFFIX = ".tls.port";
    private final static String PROP_SSL_REQUIRE_CLIENTAUTH_SUFFIX = ".tls.requireClientAuth";
    private final static String PROP_FLOWLIMIT_SUFFIX = ".consumerFlowLimit";
    private final static String PROP_MSGTRANSFORM_SUFFIX = ".messageTransformer";

    private final static String PROP_LOGFILE_LIMIT_SUFFIX = ".logfile.limit";
    private final static String PROP_LOGFILE_COUNT_SUFFIX = ".logfile.count";

    public final static int DEFAULT_TCPPORT = 7672;
    public final static int DEFAULT_SSLPORT = 7673;

    private static volatile StompBridgeResources _sbr = getStompBridgeResources();

    private Logger _logger = null;

    private  int TCPPORT = DEFAULT_TCPPORT;
    private  int SSLPORT = DEFAULT_SSLPORT;
    private InetAddress HOST = null;
    private String TCPHOSTNAMEPORT = null;
    private String SSLHOSTNAMEPORT = null;

    private MessageTransformer<Message, Message> _msgTransformer = null; 

    private BridgeContext _bc = null;
    private Properties jmsprop = null;
    private boolean _tcpEnabled = false;
    private boolean _sslEnabled = false;
    private boolean _inited = false;
    private TCPNIOTransport _tcpTransport = null;
    private TCPNIOTransport _sslTransport = null;
    private PUProtocol _tcppup = null;
    private PUProtocol _sslpup = null;

    public synchronized void init(BridgeContext bc) throws Exception {
        _bc = bc;

        Properties props = bc.getConfig();

        String domain = props.getProperty(BridgeContext.BRIDGE_PROP_PREFIX);

        String cn = props.getProperty(domain+PROP_MSGTRANSFORM_SUFFIX);
        if (cn != null ) {
            _msgTransformer = (MessageTransformer<Message, Message>)
                                     Class.forName(cn).newInstance();
        }

        jmsprop =  new Properties();
        String flowlimit = props.getProperty(domain+PROP_FLOWLIMIT_SUFFIX);
        if (flowlimit != null) {
            jmsprop.setProperty(
                    com.sun.messaging.ConnectionConfiguration.imqConsumerFlowLimit,
                                String.valueOf(Integer.parseInt(flowlimit)));
        }

        _logger = Logger.getLogger(domain);
        if (bc.isSilentMode()) {
            _logger.setUseParentHandlers(false);
        }

        String var = bc.getRootDir();
        File dir =  new File(var);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("File.mkdirs("+var+")");
            }
        }
        String logfile = var+File.separator+"stomp%g.log";

        int limit = 0, count = 1;
        String limits = props.getProperty(domain+PROP_LOGFILE_LIMIT_SUFFIX);
        if (limits != null) {
            limit = Integer.parseInt(limits);
        }
        String counts = props.getProperty(domain+PROP_LOGFILE_COUNT_SUFFIX);
        if (counts != null) {
            count = Integer.parseInt(counts);
        }

        FileHandler h = new FileHandler(logfile, limit, count, true);
        h.setFormatter(new LogSimpleFormatter(_logger));  
        _logger.addHandler(h);

        _logger.log(Level.INFO, getStompBridgeResources().getString(
                    StompBridgeResources.I_LOG_DOMAIN, domain));
        _logger.log(Level.INFO, getStompBridgeResources().getString(
                    StompBridgeResources.I_LOG_FILE, logfile)+"["+limit+","+count+"]");

        String v = props.getProperty(domain+PROP_TCPENABLED_SUFFIX, "true");
        if (v != null && Boolean.valueOf(v).booleanValue()) {
            String p = props.getProperty(domain+PROP_TCPPORT_SUFFIX, String.valueOf(DEFAULT_TCPPORT));
            TCPPORT = Integer.parseInt(p);
            _tcpEnabled = true;
        }

        v = props.getProperty(domain+PROP_SSLENABLED_SUFFIX, "false");
        if (v != null && Boolean.valueOf(v).booleanValue()) {
            String p = props.getProperty(domain+PROP_SSLPORT_SUFFIX, String.valueOf(DEFAULT_SSLPORT));
            SSLPORT = Integer.parseInt(p);
            _sslEnabled = true;
        }

        if (!_tcpEnabled && !_sslEnabled) {
            throw new IllegalArgumentException(getStompBridgeResources().
                      getKString(StompBridgeResources.X_NO_PROTOCOL));
        }

        v = props.getProperty(domain+PROP_HOSTNAME_SUFFIX);
        if (v == null || v.length() == 0) {
            v = bc.getBrokerHostName();
        }
        String hn = null;
        if (v != null && v.length() > 0) {
            hn = v;
            HOST = InetAddress.getByName(v);
        } else {
            hn = InetAddress.getLocalHost().getCanonicalHostName();
        }
        URL u = new URL("http", hn, TCPPORT, "");
        TCPHOSTNAMEPORT = u.getHost()+":"+TCPPORT;
        u = new URL("http", hn, SSLPORT, "");
        SSLHOSTNAMEPORT = u.getHost()+":"+SSLPORT;

        int major = Grizzly.getMajorVersion();
        //int minor = Grizzly.getMinorVersion();
        if (major < 2) {
            String[] params = { String.valueOf(major), Grizzly.getDotedVersion(), String.valueOf(1)};
            String emsg = getStompBridgeResources().getKString(
                          StompBridgeResources.X_INCOMPATIBLE_GRIZZLY_MAJOR_VERSION, params);
            _logger.log(Level.SEVERE, emsg);
            throw new UnsupportedOperationException(emsg);
        } 
        _logger.log(Level.INFO, getStompBridgeResources().getString(
                    StompBridgeResources.I_INIT_GRIZZLY, Grizzly.getDotedVersion()));

        PUService pu = null;
        if (_bc.doBind() && (_tcpEnabled || _sslEnabled))  {
            pu = (PUService)bc.getPUService();
            if (pu == null) { 
                if (_tcpEnabled) {
                    FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
                    filterChainBuilder.add(new TransportFilter());
                    filterChainBuilder.add(new StompMessageFilter(this));
                    filterChainBuilder.add(new StompMessageDispatchFilter(this));

                    _tcpTransport = TCPNIOTransportBuilder.newInstance().build();
                    _tcpTransport.setProcessor(filterChainBuilder.build());
                    InetSocketAddress saddr = (HOST == null ? 
                                               new InetSocketAddress(TCPPORT):
                                               new InetSocketAddress(HOST, TCPPORT));
                    _tcpTransport.bind(saddr);
                }
                if (_sslEnabled) {
                    final SSLEngineConfigurator serverConfig = initializeSSL(_bc, domain, props, _logger);
                    final SSLEngineConfigurator clientConfig = serverConfig.copy().setClientMode(true);
                    FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
                    filterChainBuilder.add(new TransportFilter());
                    filterChainBuilder.add(new SSLFilter(serverConfig, clientConfig));
                    filterChainBuilder.add(new StompMessageFilter(this));
                    filterChainBuilder.add(new StompMessageDispatchFilter(this));

                    _sslTransport = TCPNIOTransportBuilder.newInstance().build();
                    _sslTransport.setProcessor(filterChainBuilder.build());
                    InetSocketAddress saddr = (HOST == null ? 
                                               new InetSocketAddress(SSLPORT):
                                               new InetSocketAddress(HOST, SSLPORT));
                    _sslTransport.bind(saddr);
                }
            } else {
                if (_tcpEnabled) {
                    final FilterChain puProtocolFilterChain =
                                  pu.getPUFilterChainBuilder()
                                  .add(new StompMessageFilter(this))
                                  .add(new StompMessageDispatchFilter(this))
                                  .build();
                     StompProtocolFinder pf = new StompProtocolFinder();
                    _tcppup = new PUProtocol(pf, puProtocolFilterChain);
                }
                if (_sslEnabled) {
                    Properties sslprops = bc.getDefaultSSLContextConfig();
                    boolean reqcauth = false;         
                    v = props.getProperty(domain+PROP_SSL_REQUIRE_CLIENTAUTH_SUFFIX, "false");
                    if (v != null && Boolean.valueOf(v).booleanValue()) {
                        reqcauth = true;
                    }
                    if (!pu.initializeSSL(sslprops, reqcauth, null,
                             _bc.getPoodleFixEnabled(), 
                             _bc.getKnownSSLEnabledProtocols())) {
                        if (pu.getSSLClientAuthRequired() != reqcauth) {
                            _logger.log(Level.WARNING, getStompBridgeResources().getString(
                                StompBridgeResources.W_PROPERTY_SETTING_OVERRIDE_BY_BROKER,
                                domain+PROP_SSL_REQUIRE_CLIENTAUTH_SUFFIX+"="+reqcauth,
                                domain+PROP_SSL_REQUIRE_CLIENTAUTH_SUFFIX+"="+pu.getSSLClientAuthRequired()));
                        }
                    }
                    final FilterChain puProtocolFilterChain =
                                  pu.getSSLPUFilterChainBuilder()
                                  .add(new StompMessageFilter(this))
                                  .add(new StompMessageDispatchFilter(this))
                                  .build();
                    StompProtocolFinder pf = new StompProtocolFinder();
                    _sslpup = new PUProtocol(pf, puProtocolFilterChain);
                }
            }
        }

        if (_bc.doBind() && _tcpEnabled && pu == null) {
            _bc.registerService("stomp[TCP]", "stomp", TCPPORT, null);
        }
        if (_bc.doBind() && _sslEnabled && pu == null) {
            _bc.registerService("stomp[SSL/TLS]", "stomp", SSLPORT, null);
        }
        _inited = true;
     }

     BridgeContext getBridgeContext() {
         return _bc;
     }

     Properties getJMSConfig() {
         return jmsprop;
     }

     private static SSLEngineConfigurator initializeSSL(
         BridgeContext bc, String domain, Properties props, Logger logger)
         throws Exception {

         logger.log(Level.INFO, getStompBridgeResources().
                     getString(StompBridgeResources.I_INIT_SSL));

         Properties sslprops = bc.getDefaultSSLContextConfig();
         SSLContextConfigurator sslcf = new SSLContextConfigurator();
         sslcf.setKeyManagerFactoryAlgorithm(sslprops.getProperty(bc.KEYSTORE_ALGORITHM));
         sslcf.setKeyStoreFile(sslprops.getProperty(bc.KEYSTORE_FILE));
         sslcf.setKeyStorePass(sslprops.getProperty(bc.TRUSTSTORE_PASSWORD));
         sslcf.setKeyStoreType(sslprops.getProperty(bc.KEYSTORE_TYPE));

         sslcf.setTrustManagerFactoryAlgorithm(sslprops.getProperty(bc.TRUSTSTORE_ALGORITHM));
         sslcf.setTrustStoreFile(sslprops.getProperty(bc.TRUSTSTORE_FILE));
         sslcf.setTrustStorePass(sslprops.getProperty(bc.TRUSTSTORE_PASSWORD));
         sslcf.setTrustStoreType(sslprops.getProperty(bc.TRUSTSTORE_TYPE));

         sslcf.setSecurityProtocol(sslprops.getProperty(bc.SECURESOCKET_PROTOCOL));

         boolean reqcauth = false;         
         String v = props.getProperty(domain+PROP_SSL_REQUIRE_CLIENTAUTH_SUFFIX, "false");
         if (v != null && Boolean.valueOf(v).booleanValue()) {
             reqcauth = true;
         }

         SSLEngineConfigurator ec = new SSLEngineConfigurator(
                                        sslcf.createSSLContext(),
                                        false, reqcauth, reqcauth);
         if (bc.getPoodleFixEnabled()) {
             PUService.applyPoodleFix(ec, 
                 bc.getKnownSSLEnabledProtocols(), 
                 "StompServer");
         }
         return ec; 
     }

    public synchronized void start() throws Exception {

        if (!_inited || 
            (_bc.doBind() && _bc.getPUService() == null && 
              _tcpTransport == null && _sslTransport == null) ||
            (_bc.doBind() && _bc.getPUService() != null && _tcppup == null && _sslpup == null)) {
            String emsg = getStompBridgeResources().getKString(
                          StompBridgeResources.X_STOMP_SERVER_NO_INIT);
            _logger.log(Level.SEVERE, emsg);
            throw new IllegalStateException(emsg);
        }
        if (!_bc.doBind()) { //to be implemented
            return;
        }
        PUService pu = (PUService)_bc.getPUService();
        if (pu != null) {
            try {
                if (_tcpEnabled) {
                    pu.register(_tcppup, null);
                    _logger.log(Level.INFO, getStompBridgeResources().getString(
                        StompBridgeResources.I_START_TRANSPORT, "TCP" , pu.getBindSocketAddress()));
                }
                if (_sslEnabled) {
                    pu.registerSSL(_sslpup, null);
                    _logger.log(Level.INFO, getStompBridgeResources().getString(
                        StompBridgeResources.I_START_TRANSPORT, "SSL/TLS" , pu.getBindSocketAddress()));
                }

            } catch (Exception e) {
                _logger.log(Level.SEVERE, e.getMessage(), e);
                try {
                stop();
                } catch (Exception ee) {}
                throw e;
            }
        } else {
            try {
                if (_tcpEnabled) {
                    _tcpTransport.start(); 
                    _logger.log(Level.INFO, getStompBridgeResources().getString(
                        StompBridgeResources.I_START_TRANSPORT, "TCP" , TCPHOSTNAMEPORT));
                }
                if (_sslEnabled) {
                    _sslTransport.start(); 
                    _logger.log(Level.INFO, getStompBridgeResources().getString(
                        StompBridgeResources.I_START_TRANSPORT, "SSL/TLS" , SSLHOSTNAMEPORT));
                }
            } catch (Exception e) {
                _logger.log(Level.SEVERE, e.getMessage(), e);
                try {
                stop();
                } catch (Exception ee) {}
                throw e;
            }
        }
    }

    public synchronized void stop() throws Exception {

        if (!_inited || 
            (_bc.doBind() && _bc.getPUService() == null && 
              _tcpTransport == null && _sslTransport == null) ||
            (_bc.doBind() && _bc.getPUService() != null && _tcppup == null && _sslpup == null)) {
            String emsg = getStompBridgeResources().getKString(StompBridgeResources.X_STOMP_SERVER_NO_INIT);
            _logger.log(Level.SEVERE, emsg);
            throw new IllegalStateException(emsg);
        }
        if (!_bc.doBind()) { //to be implemented
            return;
        }

        _logger.log(Level.INFO, getStompBridgeResources().getString(
                                StompBridgeResources.I_STOP_STOMP_SERVER));

        PUService pu =  (PUService)_bc.getPUService();
        if (pu != null) {
            Exception e = null;  
            if (_tcpEnabled) {
                try {
                pu.deregister(_tcppup);
                } catch (Exception ee) {
                e = ee;
                }
            }
            if (_sslEnabled) {
                try {
                pu.deregisterSSL(_sslpup);
                } catch (Exception ee) {
                if (e == null) {
                e = ee;
                }
                }
            }
            if (e != null) {
                _logger.log(Level.SEVERE, e.getMessage(), e);
                throw e;
            }
        } else {
            Exception e = null;  
            if (_tcpEnabled) {
                try {
                _tcpTransport.stop(); 
                } catch (Exception ee) {
                e = ee;
                }
            }
            if (_sslEnabled) {
                try {
                _sslTransport.stop();
                } catch (Exception ee) {
                if (e == null) {
                e = ee;
                }
                }
            }
            if (e != null) {
               _logger.log(Level.SEVERE, e.getMessage(), e);
               throw e;
            }
        }

        _logger.log(Level.INFO, getStompBridgeResources().getString(
                                StompBridgeResources.I_STOMP_SERVER_STOPPED));
    }

    protected Logger getLogger() {
        return _logger;
    }

    protected MessageTransformer<Message, Message> getMessageTransformer() {
        return _msgTransformer;
    }

    public static StompBridgeResources getStompBridgeResources() {
        if (_sbr == null) {
            synchronized(StompServer.class) {
                if (_sbr == null) {
                    _sbr = StompBridgeResources.getResources(Locale.getDefault());
                }
            }
        }
        return _sbr;
    }
}
