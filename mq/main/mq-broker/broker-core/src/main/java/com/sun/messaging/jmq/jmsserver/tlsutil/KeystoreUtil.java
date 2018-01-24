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
 * @(#)KeystoreUtil.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.tlsutil;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.Properties;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.net.tls.DefaultTrustManager;
import com.sun.messaging.jmq.util.StringUtil;
import com.sun.messaging.jmq.util.Password;

public class KeystoreUtil implements SSLPropertyMap {
    public final static String
        KEYSTORE_DIR_PROP          = Globals.IMQ + ".keystore.file.dirpath",
        KEYSTORE_FILE_PROP         = Globals.IMQ + ".keystore.file.name",
        KEYSTORE_PASSWORD_PROP     = Globals.IMQ + ".keystore.password";

    private static String keystore_location = null;
    private static String pass_phrase = null;

    protected static final BrokerResources br = Globals.getBrokerResources();

    public static void clear() {
        keystore_location = null;
        String pass_phrase = null;
    }

    public static String getKeystoreLocation() throws IOException {
	if (keystore_location == null)  {
	    BrokerConfig bcfg;
	    bcfg = Globals.getConfig();	    	    

            // Get Keystore Location and  Passphrase here .....
	    
            String dir, file, value, pf_dir, pf_file, pf_value, file_sep;
	    
            file_sep = System.getProperty("file.separator");
	    
            // Get Keystore location by getting the directory and the name
            // of the keystore file.

            if ((value = bcfg.getProperty(KEYSTORE_DIR_PROP)) != null) {
                value = StringUtil.expandVariables(value, bcfg);
                dir = value;
            } else {
                dir = bcfg.getProperty(Globals.IMQ + ".varhome") +
                file_sep + "security";
            }
	    
            keystore_location = dir + file_sep
			+ bcfg.getProperty(KEYSTORE_FILE_PROP);
	
	}

	return (keystore_location);
    }

    public static String getKeystorePassword() throws IOException {
	if (pass_phrase == null)  {
	    BrokerConfig bcfg;
       	    Password pw = null;

	    bcfg = Globals.getConfig();	    	    

            // Get Passphrase from property setting 
            pass_phrase = bcfg.getProperty(KEYSTORE_PASSWORD_PROP);
            // if passphrase is null then get it thro' user interaction
            int retry = 0;
            pw = new Password();
            if (pw.echoPassword()) {
                System.err.println(Globals.getBrokerResources().
                    getString(BrokerResources.W_ECHO_PASSWORD));
            }
            while ((pass_phrase == null || pass_phrase.equals("")) &&
                retry <= 5) {

                System.err.print(br.getString(
                    BrokerResources.M_ENTER_KEY_PWD,
                    getKeystoreLocation()));
                    System.err.flush();

                if (Broker.getBroker().background) {
                    // We're running in the background and can't
                    // read the password. We still prompt for it
                    // so it's more obvious what's going on
                    // (instead of just silently failing)
                    // See 4451214
                    System.err.print("\n");
                    break;
                }

                pass_phrase = pw.getPassword();
    
                // Limit the number of times we try reading the passwd.
                // If the VM is run in the background the readLine()
                // will always return null and we'd get stuck
                // in the loop
                retry++;
            }
        }
	
	return (pass_phrase);
    }

    public static final String KEYSTORE_FILE = "javax.net.ssl.keyStore";
    public static final String KEYSTORE_PASSWORD = "javax.net.ssl.keyStorePassword";
    public static final String KEYSTORE_TYPE = "javax.net.ssl.keyStoreType";

    public static final String TRUSTSTORE_FILE = "javax.net.ssl.trustStore";
    public static final String TRUSTSTORE_PASSWORD = "javax.net.ssl.trustStorePassword";
    public static final String TRUSTSTORE_TYPE = "javax.net.ssl.trustStoreType";

    public static final String KEYSTORE_ALGORITHM = "ssl.KeyManagerFactory.algorithm";
    public static final String TRUSTSTORE_ALGORITHM = "ssl.TrustManagerFactory.algorithm";
    public static final String SECURESOCKET_PROTOCOL = "securesocket.protocol";

    public String mapSSLProperty(String prop) {
        return prop;
    }

    /**
     * Get default SSLContext configuration properties
     */
    public static Properties getDefaultSSLContextConfig(String caller,
                                                        SSLPropertyMap pm) 
                                                        throws Exception {
        if (pm == null) pm = new KeystoreUtil();

        Properties props = new Properties();
        String keystoreloc = getKeystoreLocation();
        File kf = new File(keystoreloc);
        if (!kf.exists()) {
            throw new IOException(Globals.getBrokerResources().getKString(
                                  BrokerResources.E_KEYSTORE_NOT_EXIST, keystoreloc));
        }
        props.setProperty(pm.mapSSLProperty(KEYSTORE_FILE), keystoreloc);
        props.setProperty(pm.mapSSLProperty(TRUSTSTORE_FILE), keystoreloc);

        String keystorepwd = getKeystorePassword();
        if (keystorepwd == null) {
            throw new IOException(Globals.getBrokerResources().getKString(
                                  BrokerResources.E_PASS_PHRASE_NULL));
        }
        props.setProperty(pm.mapSSLProperty(KEYSTORE_PASSWORD), keystorepwd);
        props.setProperty(pm.mapSSLProperty(TRUSTSTORE_PASSWORD), keystorepwd);
        props.setProperty(pm.mapSSLProperty(KEYSTORE_TYPE), "JKS");
        props.setProperty(pm.mapSSLProperty(TRUSTSTORE_TYPE), "JKS");

        String alg = "SunX509"; 
        try {
             KeyManagerFactory.getInstance("SunX509");
        } catch (NoSuchAlgorithmException e) {
            alg = KeyManagerFactory.getDefaultAlgorithm();
            Globals.getLogger().log(Globals.getLogger().INFO, caller+":"+e.getMessage()+
                                ", use default KeyManagerFactory algorithm "+alg);
        }
        props.setProperty(pm.mapSSLProperty(KEYSTORE_ALGORITHM), alg);

        alg = "SunX509";
        try {
             TrustManagerFactory.getInstance("SunX509");
        } catch (NoSuchAlgorithmException e) {
             alg = TrustManagerFactory.getDefaultAlgorithm();
             Globals.getLogger().log(Globals.getLogger().INFO, caller+":"+e.getMessage()+
                                 ", use default TrustManagerFactory algorithm "+alg);
        }
        props.setProperty(pm.mapSSLProperty(TRUSTSTORE_ALGORITHM), alg);

        props.setProperty(pm.mapSSLProperty(SECURESOCKET_PROTOCOL), "TLS");

        return props;
    }

    private static SSLContext getDefaultSSLContext(
        String caller, boolean trustAll)
        throws Exception {

        KeyStore ks = null;
        KeyManagerFactory kmf = null;
        if (!trustAll) {
            String keystorepwd = getKeystorePassword();
            if (keystorepwd == null) {
                throw new IOException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.E_PASS_PHRASE_NULL));
            }
            char[] keystorepwdc = keystorepwd.toCharArray();
            ks = getKeyStore(keystorepwdc);
            kmf = getKeyManagerFactory(ks, keystorepwdc, caller);
        }

        TrustManager[] tm = null;
        if (!trustAll) {
            TrustManagerFactory tmf = null;
            try {
                tmf = TrustManagerFactory.getInstance("SunX509");
            } catch (NoSuchAlgorithmException e) {
                String alg = TrustManagerFactory.getDefaultAlgorithm();
                Globals.getLogger().log(Globals.getLogger().INFO, caller+":"+e.getMessage()+
                                    ", use default TrustManagerFactory algorithm "+alg);
                tmf = TrustManagerFactory.getInstance(alg);
            }
            tmf.init(ks);
            tm = tmf.getTrustManagers();
        } else {
            tm = new TrustManager[1];
            tm[0] = new DefaultTrustManager();
        }

       SSLContext ctx = SSLContext.getInstance("TLS");
       SecureRandom random = null;
       if (!trustAll) {
           random = SecureRandom.getInstance("SHA1PRNG");
       }
       ctx.init((kmf == null ? null:kmf.getKeyManagers()), tm, random);

       return ctx;
    }

    private static KeyStore getKeyStore(char[] keystorepwdc) throws Exception {
        String keystoreloc = getKeystoreLocation();
        File kf = new File(keystoreloc);
        if (!kf.exists()) {
            throw new IOException(Globals.getBrokerResources().getKString(
                          BrokerResources.E_KEYSTORE_NOT_EXIST, keystoreloc));
        }
        KeyStore ks = KeyStore.getInstance("JKS");
        FileInputStream is = new FileInputStream(keystoreloc);
        try {
            ks.load(is, keystorepwdc);
        } finally {
            try {
            is.close();
            } catch (Exception e) {
            /* ignore */
            }
        }
        return ks;
    }

    private static KeyManagerFactory getKeyManagerFactory(
        KeyStore ks, char[] keystorepwdc, String caller) throws Exception {
        KeyManagerFactory kmf;
        try {
             kmf = KeyManagerFactory.getInstance("SunX509");
        } catch (NoSuchAlgorithmException e) {
            String alg = KeyManagerFactory.getDefaultAlgorithm();
            Globals.getLogger().log(Globals.getLogger().INFO, caller+":"+e.getMessage()+
                                    ", use default KeyManagerFactory algorithm "+alg);
            kmf = KeyManagerFactory.getInstance(alg);
        }
        kmf.init(ks, keystorepwdc);
        return kmf;
    }

    public static String[] getKnownSSLEnabledProtocols(String caller) {
        try {
            SSLContext sc = getDefaultSSLContext(caller, true);
            SSLEngine se = sc.createSSLEngine();
            return se.getEnabledProtocols();
        } catch (Exception e) {}
        return new String[]{"TLSv1"};
    }
}
