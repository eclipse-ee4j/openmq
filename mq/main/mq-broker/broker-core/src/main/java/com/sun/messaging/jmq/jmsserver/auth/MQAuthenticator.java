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
 * @(#)MQAuthenticator.java	1.14 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth;

import java.util.List;
import java.util.Hashtable;
import java.util.Properties;
import java.security.AccessControlException;
import javax.security.auth.Subject;
import javax.security.auth.Refreshable;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.auth.api.FailedLoginException;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.auth.api.server.*;

/**
 * This class is to be used to shut-circus client connection authentication.
 * An object of this class should only be used for 1 connection
 */

public class MQAuthenticator {

    private static boolean DEBUG = false; 

    private String serviceName = null;
    //private int serviceType;
    private String serviceTypeStr = null;

    private Hashtable handlers = new Hashtable();
    private AuthCacheData authCacheData = new AuthCacheData();
    private AccessController ac = null;

    public MQAuthenticator(String serviceName, int serviceType) 
                                      throws BrokerException {

        this.serviceName = serviceName;
        //this.serviceType = serviceType;
        this.serviceTypeStr = ServiceType.getServiceTypeString(serviceType);
        this.ac = AccessController.getInstance(serviceName, serviceType); 
    }

    public void authenticate(String username, String password) 
           throws BrokerException, LoginException, AccessControlException {
        authenticate(username, password, true);
    }

    public void authenticate(String username, String password, boolean logout) 
           throws BrokerException, LoginException, AccessControlException {

        String authType = ac.getAuthType();
        com.sun.messaging.jmq.auth.api.client.AuthenticationProtocolHandler hd
                                               = getClientAuthHandler(authType);
        if (!hd.getType().equals(authType)) {
            String[] args = {authType, hd.getType(), hd.getClass().getName()};
            throw new BrokerException(Globals.getBrokerResources().getKString(
                                    BrokerResources.X_AUTHTYPE_MISMATCH, args));
        }
        hd.init(username, password, null /* props */);

        int seq = 0;
        byte[] req = ac.getChallenge(seq, 
                                     new Properties(), 
                                     getAuthCacheData().getCacheData(), 
                                     null /* overrideType */);
        do {
            req = ac.handleResponse(hd.handleRequest(req, seq++), seq);
        } while (req != null);
        authCacheData.setCacheData(ac.getCacheData());

        ac.checkConnectionPermission(serviceName, serviceTypeStr); 

        //Subject sj = ac.getAuthenticatedSubject();
	if (logout)  {
            ac.logout();
	}
    }

    public void logout()  {
	if (ac != null)  {
            ac.logout();
	}
    }

    public AuthCacheData getAuthCacheData() {
        return authCacheData;
    }

    public AccessController getAccessController() {
        return ac;
    }

    private com.sun.messaging.jmq.auth.api.client.AuthenticationProtocolHandler 
                                           getClientAuthHandler(String authType) 
                                                         throws BrokerException {

        com.sun.messaging.jmq.auth.api.client.AuthenticationProtocolHandler hd =
           (com.sun.messaging.jmq.auth.api.client.AuthenticationProtocolHandler)
                                                          handlers.get(authType);
        if (hd != null) return hd;

        if (authType.equals(AccessController.AUTHTYPE_BASIC)) {
            hd = new com.sun.messaging.jmq.auth.handlers.BasicAuthenticationHandler();
            handlers.put(authType, hd);
        } else if (authType.equals(AccessController.AUTHTYPE_DIGEST)) {
            hd = new com.sun.messaging.jmq.auth.handlers.DigestAuthenticationHandler();
            handlers.put(authType, hd);
        } else {

            /* 
             * Currently not supported, client side uses the following property to
             * indicate a plugin
             *
             * "JMQAuthClass" + "." + authType
             */
        
            throw new BrokerException(Globals.getBrokerResources().getKString(
                         BrokerResources.X_UNSUPPORTED_AUTHTYPE, authType));
        }
        return hd;
    }

    public static final String CMDUSER_PROPERTY = Globals.IMQ + ".imqcmd.user";
    public static final String CMDUSER_PWD_PROPERTY = Globals.IMQ + ".imqcmd.password";
    public static final String CMDUSER_SVC_PROPERTY = Globals.IMQ + ".imqcmd.service";

    public static boolean authenticateCMDUserIfset() {
        BrokerConfig bcfg = Globals.getConfig();
        String cmduser = bcfg.getProperty(CMDUSER_PROPERTY);
        if (cmduser == null)  return true;

        Logger logger = Globals.getLogger();
        BrokerResources rb = Globals.getBrokerResources();
        if (cmduser.trim().length() == 0) {
            logger.log(Logger.FORCE, rb.X_BAD_PROPERTY_VALUE,
                                     CMDUSER_PROPERTY+ "=" + cmduser);
            return false;
        }
            /*
            if (!bcfg.getBooleanProperty(Globals.KEYSTORE_USE_PASSFILE_PROP)) {
                logger.log(Logger.FORCE, rb.E_AUTH_CMDUSER_PASSFILE_NOT_ENABLED,
                           Globals.KEYSTORE_USE_PASSFILE_PROP, cmduserProp);
                return false;
            }
            */
        String cmdpwd = bcfg.getProperty(CMDUSER_PWD_PROPERTY);
        if (cmdpwd == null) {
            logger.log(Logger.FORCE, rb.X_PASSWORD_NOT_PROVIDED,
                                     CMDUSER_PROPERTY+"="+cmduser);
            return false;
        }
        String cmdsvc = bcfg.getProperty(CMDUSER_SVC_PROPERTY);
        if (cmdsvc == null) cmdsvc = "admin";
        List activesvcs= ServiceManager.getAllActiveServiceNames();
        if (activesvcs == null || !activesvcs.contains(cmdsvc)) {
            logger.log(Logger.FORCE, rb.E_NOT_ACTIVE_SERVICE, cmdsvc, 
                                     CMDUSER_PROPERTY+"="+cmduser);
            return false;
        }
        String str = ServiceManager.getServiceTypeString(cmdsvc);
        if (str == null || ServiceType.getServiceType(str) != ServiceType.ADMIN) {
            String args[] = {cmdsvc, str, CMDUSER_PROPERTY+"="+cmduser};
            logger.log(Logger.FORCE, rb.E_NOT_ADMIN_SERVICE, args);
            return false;
        }
        try {
            MQAuthenticator a = new MQAuthenticator(cmdsvc, ServiceType.ADMIN);
            a.authenticate(cmduser, cmdpwd);
            if (DEBUG) {
            logger.log(Logger.FORCE, rb.I_AUTH_OK, 
                       CMDUSER_PROPERTY+"="+cmduser, cmdsvc);
            }
            return true;
        } catch (Exception e) {
            if (DEBUG) {
            logger.logStack(Logger.FORCE, rb.W_AUTH_FAILED, cmduser, cmdsvc, e);
            } else {
            logger.log(Logger.FORCE, rb.W_AUTH_FAILED, cmduser, cmdsvc);
            }
            return false; 
        }
    }
}
