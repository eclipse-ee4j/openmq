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
 * @(#)MQRMIServerSocketFactory.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.agent;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.File;

import java.net.ServerSocket;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;

import java.rmi.server.RMISocketFactory;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;

import javax.rmi.ssl.SslRMIServerSocketFactory;

import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.StringUtil;
import com.sun.messaging.jmq.util.Password;
import com.sun.messaging.jmq.util.log.Logger;

import com.sun.messaging.jmq.jmsserver.net.tls.DefaultTrustManager;
import com.sun.messaging.jmq.jmsserver.tlsutil.KeystoreUtil;

public class MQRMIServerSocketFactory extends SslRMIServerSocketFactory {
    private static SSLServerSocketFactory ssfactory = null;
    private static final Object classlock = new Object();

    private String jmxHostname = null;
    private int backlog = 0;
    private boolean useSSL = false;

    protected static final Logger logger = Globals.getLogger();
    protected static final BrokerResources br = Globals.getBrokerResources();

    public MQRMIServerSocketFactory(String jmxHostname, int backlog, boolean useSSL)  {
        this.jmxHostname = jmxHostname;
        this.backlog = backlog;
        this.useSSL = useSSL;
    }

    public ServerSocket createServerSocket(int port) throws IOException {
        ServerSocket serversocket = null;      

	if (useSSL)  {
            SSLServerSocketFactory ssf =
                (SSLServerSocketFactory)getSSLServerSocketFactory();

            InetSocketAddress endpoint;
	    if (jmxHostname != null && !jmxHostname.equals(Globals.HOSTNAME_ALL)) {
	        /*
	         * Scenario: SSL + multihome
	         */
                InetAddress bindAddr = Globals.getJMXInetAddress();
		endpoint = new InetSocketAddress(bindAddr, port);
                serversocket = (SSLServerSocket)ssf.createServerSocket();

		serversocket.setReuseAddress(true);

	    } else  {
	        /*
	         * Scenario: SSL
	         */
		endpoint = new InetSocketAddress(port);
                serversocket = (SSLServerSocket)ssf.createServerSocket();

		serversocket.setReuseAddress(true);
	    }
            if (Globals.getPoodleFixEnabled()) {
                Globals.applyPoodleFix(serversocket, "MQRMIServerSocketFactory");
            }
            serversocket.bind(endpoint, backlog);
	} else  {
	    if (jmxHostname != null && !jmxHostname.equals(Globals.HOSTNAME_ALL)) {
	        /*
	         * Scenario: multihome
	         */
                InetAddress bindAddr = Globals.getJMXInetAddress();
		InetSocketAddress endpoint = new InetSocketAddress(bindAddr, port);

		serversocket = new ServerSocket();

		serversocket.setReuseAddress(true);

		serversocket.bind(endpoint, backlog);
	    } else  {
		/*
		 * We shouldn't really get here since this socket factory should only be used
		 * for ssl and/or multihome support. For the normal case (no ssl, no multihome),
		 * no special socket factory should be needed.
		 */
		InetSocketAddress endpoint = new InetSocketAddress(port);
		serversocket = new ServerSocket();

		serversocket.setReuseAddress(true);

		serversocket.bind(endpoint, backlog);
	    }
	}

	return (serversocket);
    }

    public String toString()  {
	return ("jmxHostname="
		+ jmxHostname
		+ ",backlog="
		+ backlog
		+ ",useSSL="
		+ useSSL);
    }

    public boolean equals(Object obj)  {
	if (!(obj instanceof MQRMIServerSocketFactory))  {
	    return (false);
	}

	MQRMIServerSocketFactory that = (MQRMIServerSocketFactory)obj;

	if (this.jmxHostname != null)  {
	    if ((that.jmxHostname == null) || !that.jmxHostname.equals(this.jmxHostname))  {
	        return (false);
	    }
	} else  {
	    if (that.jmxHostname != null)  {
	        return (false);
	    }
	}

	if (this.backlog != that.backlog)  {
	    return (false);
	}

	if (this.useSSL != that.useSSL)  {
	    return (false);
	}

	return (true);
    }

    public int hashCode()  {
	return toString().hashCode();
    }

    private static ServerSocketFactory getSSLServerSocketFactory()
	throws IOException {

        synchronized (classlock) {

	  if (ssfactory == null) {

	    // need to get a SSLServerSocketFactory
	    try {
	    
		// set up key manager to do server authentication
		// Don't i18n Strings here.  They are key words
		SSLContext ctx;
		KeyManagerFactory kmf;
		KeyStore ks;		

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
		 *     check if password is in the property file
		 *        if not present, 
		 *            then prompt for password.
		 *
		 * If password is not set by any of the above 2 methods, then
		 * keystore cannot be opened and the program exists by 
		 * throwing an exception stating:
		 * "Keystore was tampered with, or password was incorrect"
		 *
		 */

		// Get Passphrase from property setting 

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

                FileInputStream is =  new FileInputStream(keystore_location);
                try {
		    ks.load(is, passphrase);
                } finally {
                    try {              
                    is.close();
                    } catch (Exception e) {
                    /* ignore */
                    }
                }
		kmf.init(ks, passphrase);
	    
		TrustManager[] tm = new TrustManager[1];
		tm[0] = new DefaultTrustManager();
	    
		// SHA1 random number generator
		SecureRandom random = SecureRandom.getInstance("SHA1PRNG");  
	    
		ctx.init(kmf.getKeyManagers(), null, random);

		ssfactory = ctx.getServerSocketFactory();
	    } catch (IOException e) {
        	throw e;
	    } catch (Exception e)  {
		throw new IOException(e.toString());
            }
	  } // if (ssfactory == null)

	  return ssfactory;
	}
    }
}
