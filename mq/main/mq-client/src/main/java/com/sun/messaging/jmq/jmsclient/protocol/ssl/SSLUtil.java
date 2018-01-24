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
 */ 
package com.sun.messaging.jmq.jmsclient.protocol.ssl;

import java.io.*;
import java.util.logging.Logger;
import com.sun.messaging.jmq.util.MQResourceBundle;
import java.net.*;
import javax.net.ssl.*;
import java.security.*;
import javax.security.cert.X509Certificate;
import javax.jms.*;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.*;
import com.sun.messaging.jmq.jmsclient.resources.*;
import com.sun.messaging.jmq.jmsclient.protocol.SocketConnectionHandler;


 /**
  */
public class SSLUtil {

    public static SSLSocket makeSSLSocket(String host, int port,
        boolean isHostTrusted, String keystore, String keystorepwd,
        Logger logger, ClientResources cr)
        throws Exception {

        SSLSocketFactory sslFactory;
        if (keystorepwd != null) { 
            SSLContext ctx = getDefaultSSLContext(keystore,
                             keystorepwd, isHostTrusted, logger, cr);
            sslFactory = ctx.getSocketFactory();
        } else {
            if (isHostTrusted) {
                SSLContext ctx = getTrustSSLContext();
                sslFactory = ctx.getSocketFactory();
                if (Debug.debug) {
                    Debug.println("Broker is trusted ...");
                }
            } else {
                sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }
        }

        //This is here for QA to verify that SSL is used ...
        //XXX chiaming REMOVE
        if (Debug.debug) {
            Debug.println ("Create connection using SSL protocol ...");
            Debug.println ("Broker Host: " + host);
            Debug.println ("Broker Port: " + port);
        }

        SSLSocket sslSocket = null;
        if (host == null) {
            sslSocket = (SSLSocket)sslFactory.createSocket();
        } else {
            sslSocket = (SSLSocket)sslFactory.createSocket(host, port);
        }

        //tcp no delay flag
        boolean tcpNoDelay = true;
        String prop = System.getProperty("imqTcpNoDelay", "true");
        if ( prop.equals("false") ) {
            tcpNoDelay = false;
        } else {
            sslSocket.setTcpNoDelay(tcpNoDelay);
        }

        return sslSocket;
    }

    private static SSLContext getTrustSSLContext()
    throws Exception {

        SSLContext ctx = SSLContext.getInstance("TLS");
        TrustManager[] tm = new TrustManager [1];
        tm[0] = new DefaultTrustManager();
        ctx.init(null, tm, null);
        return ctx;
    }


    private static SSLContext getDefaultSSLContext(
        String keystoreloc, String keystorepwd, boolean isHostTrusted,
        Logger logger, ClientResources cr)
        throws Exception {

        if (keystorepwd == null) {
            if (cr != null) {
                throw new IOException(cr.getKString(cr.X_NO_KEYSTORE_PASSWORD));
            } else {
                throw new IOException("No key store password provided");
            }
        }
        String kpwd = keystorepwd;
        if (kpwd.equals("")) {
            kpwd = System.getProperty("javax.net.ssl.keyStorePassword");
        }
        if (kpwd == null) {
            if (cr != null) {
                throw new IOException(cr.getKString(cr.X_NO_KEYSTORE_PASSWORD));
            } else {
                throw new IOException("No key store password provided");
            }
        }

        String kloc = keystoreloc;
        if (kloc == null) {
            kloc = System.getProperty("javax.net.ssl.keyStore");
        }
        File f = new File(kloc);
        if (!f.exists()) {
            if (cr != null) {
                throw new IOException(cr.getKString(cr.X_FILE_NOT_FOUND, kloc));
            } else {
                throw new IOException("File not found: "+kloc);
            }
        }
        char[] kpwdc = kpwd.toCharArray();
        String ktype = System.getProperty("javax.net.ssl.keyStoreType");
        if (ktype == null) {
            ktype = "JKS";        
        }
        KeyStore kstore = KeyStore.getInstance(ktype);
        kstore.load(new FileInputStream(kloc), kpwdc);

        String kalg = "SunX509";
        KeyManagerFactory kmf = null;
        try {
             kmf = KeyManagerFactory.getInstance(kalg);
        } catch (NoSuchAlgorithmException e) {
            kalg = KeyManagerFactory.getDefaultAlgorithm();
            kmf = KeyManagerFactory.getInstance(kalg);
        }
        kmf.init(kstore, kpwdc);

        String talg = "SunX509";
        TrustManager[] tm = null;
        if (!isHostTrusted) {
            TrustManagerFactory tmf = null;
            try {
                tmf = TrustManagerFactory.getInstance(talg);
            } catch (NoSuchAlgorithmException e) {
                talg = TrustManagerFactory.getDefaultAlgorithm();
                tmf = TrustManagerFactory.getInstance(talg);
            }
            tmf.init(kstore);
            tm = tmf.getTrustManagers();
        } else {
            tm = new TrustManager[1];
            tm[0] = new DefaultTrustManager();
        }
        if (cr != null) {
            String[] args = {ktype, kalg, talg, kloc};
            String logmsg = cr.getKString(cr.I_USE_KEYSTORE, args);
            logger.info(logmsg);
        } else {
            System.out.println("Use "+ktype+" key store, "+kalg+
            " key manager factory and "+talg+" trust manager factory for "+kloc);
        }

        SSLContext ctx = SSLContext.getInstance("TLS");
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        ctx.init(kmf.getKeyManagers(), tm, random);
        return ctx;
    }

    public static String[] getKnownSSLEnabledProtocols() { 
        try {
            SSLContext sc = getTrustSSLContext();
            SSLEngine se = sc.createSSLEngine();
            return se.getEnabledProtocols();
        } catch (Exception e) {}

        return new String[]{"TLSv1"};
    }
}

