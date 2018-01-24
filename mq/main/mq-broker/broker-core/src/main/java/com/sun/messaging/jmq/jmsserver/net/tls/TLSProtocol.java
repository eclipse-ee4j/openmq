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
 * @(#)TLSProtocol.java	1.50 09/11/07
 */ 

package com.sun.messaging.jmq.jmsserver.net.tls;

import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;

import javax.net.*;
import javax.net.ssl.*;

import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.jmsserver.net.tcp.TcpProtocol;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.net.MQServerSocketFactory;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.license.LicenseBase;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.tlsutil.KeystoreUtil;

/**
 * This class handles a TLS (SSL) type of protocol.
 */

public class TLSProtocol extends TcpProtocol {

    private static boolean DEBUG = false;

    private static final int defaultPort    = 11001;

    // generated once when the first server socket is needed
    private static ServerSocketFactory ssfactory = null;

    private static Logger logger = Globals.getLogger();
    private static BrokerResources br = Globals.getBrokerResources();

    // needed for inprocess
    public static void init() {
        logger = Globals.getLogger();
        br = Globals.getBrokerResources();
    }

    // needed for inprocess
    public static void destroy() {
        ssfactory = null;
        Logger logger = null;
        br = null;
    }

    /*
     * Empty Constructor
     */

    public TLSProtocol() {	
        canChangeBlocking = false; // dont let us block
        port = defaultPort;
    }


    public ProtocolStreams accept()  throws IOException {
	if (serversocket == null)  {	 
	    throw new IOException(Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,
                "Unable to accept on un-opened protocol"));
	}
	SSLSocket s = (SSLSocket)serversocket.accept();

    try {
    s.setTcpNoDelay(nodelay);
    } catch (SocketException e) {
    Globals.getLogger().log(Logger.WARNING, getClass().getSimpleName()+
    ".accept(): ["+s.toString()+"]setTcpNoDelay("+nodelay+"): "+ e.toString(), e);
    }

	TLSStreams streams = createConnection(s);
	return streams;
	
    }
    
 

    public String toString() {
        return "SSL/TLS [ " + port + "," + backlog + "]";
    }

    protected ServerSocket createSocket(String hostname, int port,
                            int backlog, boolean blocking, boolean useChannel) 
	throws IOException  { 
        //ignore blocking and useChannel (they wont work)

	ServerSocketFactory ssf = getServerSocketFactory();	
        if (hostname != null && !hostname.equals(Globals.HOSTNAME_ALL)) {
            InetAddress endpoint = InetAddress.getByName(hostname);

	    serversocket = ssf.createServerSocket(port, backlog, endpoint);
        } else {    
	    serversocket = ssf.createServerSocket(port, backlog);
        }
        if (Globals.getPoodleFixEnabled()) {
            Globals.applyPoodleFix(serversocket, "TLSProtocol");
        }

        if (DEBUG && serversocket != null) {
                logger.log(Logger.DEBUG,
                "TLSProtocol: " + serversocket + " " +
                MQServerSocketFactory.serverSocketToString(serversocket) +
                ", backlog=" + backlog +
                "");
        }
	    
	return serversocket;
    }
    
    protected TLSStreams createConnection(SSLSocket socket)  
        throws IOException
    {
        return new TLSStreams((SSLSocket)socket,
            inputBufferSize, outputBufferSize);
    }

    public static ServerSocketFactory getServerSocketFactory()
	throws IOException {

        synchronized (classlock) {

            if (ssfactory != null) {
                return ssfactory;
            }

            // need to get a SSLServerSocketFactory
            try {
	    
		// set up key manager to do server authentication
		// Don't i18n Strings here.  They are key words
		SSLContext ctx;
		KeyManagerFactory kmf;
		KeyStore ks;		

		// Get Keystore location
		String keystore_location = KeystoreUtil.getKeystoreLocation();

		// Got Keystore full filename 

		// Check if the keystore exists.  If not throw exception.
		// This is done first as if the keystore does not exist, then
		// there is no point in going further.
	    	   
		File kf = new File(keystore_location);	
		if (kf.exists()) {
		    // nothing to do for now.		
		} else {
		    throw new IOException(
			br.getKString(BrokerResources.E_KEYSTORE_NOT_EXIST,
					keystore_location));
		}	

		/*
		 * Get passphrase
		 */
		String pass_phrase = KeystoreUtil.getKeystorePassword();

		// Got Passphrase. 
 	    
		if (pass_phrase == null) {
		    // In reality we should never reach this stage, but, 
		    // just in case, a check		
		    pass_phrase = "";
		    logger.log(Logger.ERROR, br.getKString(
					BrokerResources.E_PASS_PHRASE_NULL));
		}
	    
		char[] passphrase = pass_phrase.toCharArray();

		// Magic key to select the TLS protocol needed by JSSE
		// do not i18n these key strings.
		ctx = SSLContext.getInstance("TLS");
                try {
		    kmf = KeyManagerFactory.getInstance("SunX509");  // Cert type
                } catch (NoSuchAlgorithmException e) {
                    String defaultAlg = KeyManagerFactory.getDefaultAlgorithm();
                    logger.log(logger.INFO,
                        br.getKString(br.I_KEYMGRFACTORY_USE_DEFAULT_ALG,
                        e.getMessage(),defaultAlg));

                    kmf = KeyManagerFactory.getInstance(defaultAlg);
                }
		ks = KeyStore.getInstance("JKS");  // Keystore type

                try (FileInputStream fis = new FileInputStream(keystore_location)) {
                    ks.load(fis, passphrase);
                }
		kmf.init(ks, passphrase);
	    
		TrustManager[] tm = new TrustManager[1];
		tm[0] = new DefaultTrustManager();
	    
		// SHA1 random number generator
		SecureRandom random = SecureRandom.getInstance("SHA1PRNG");  
	    
		ctx.init(kmf.getKeyManagers(), tm, random);

		//ssfactory = ctx.getServerSocketFactory();
                ssfactory = MQServerSocketFactory.wrapFactory(
                                ctx.getServerSocketFactory());
	    } catch (IOException e) {
        	throw e;
            } catch (Exception ex) {
		logger.logStack(Logger.ERROR, 
                    br.getKString(BrokerResources.X_GET_SSL_SOCKET_FACT), ex);
                throw new IOException(ex.getMessage());
	    }			    
            return ssfactory;
	}
    }
    
    private static final Object classlock = new Object();
}
