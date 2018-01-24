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
 * @(#)AccessController.java	1.38 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth;

import java.util.List;
import java.util.Properties;
import java.util.List;
import java.util.Iterator;
import java.security.Principal;
import java.security.Policy;
import java.security.AccessControlException;
import javax.security.auth.Subject;
import javax.security.auth.Refreshable;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.auth.api.FailedLoginException;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.auth.acl.JAASAccessControlModel;
import com.sun.messaging.jmq.util.StringUtil;
import com.sun.messaging.jmq.auth.api.server.*;

/**
 * A AccessController associates with a Connection.  It encapsulates
 * AuthenticationProtocolHandler and AccessControlContext and delegates
 * authentication and authorization to them respectively.
 *
 * AuthenticationProtocolHandler works with client-side counter part.
 */

public class AccessController 
{
    public static final String PROP_SERVICE_NAME = Globals.IMQ + ".servicename";
    public static final String PROP_SERVICE_TYPE = Globals.IMQ + ".servicetype";

    public static final String PROP_AUTHENTICATION_AREA = "authentication";
    public static final String PROP_ACCESSCONTROL_AREA = "accesscontrol";
    public static final String PROP_USER_REPOSITORY_AREA = "user_repository"; 

    public static final String PROP_AUTHENTICATION_PREFIX =
                                        Globals.IMQ + ".authentication.";
    public static final String PROP_ACCESSCONTROL_PREFIX =
                                        Globals.IMQ + ".accesscontrol.";
    public static final String PROP_USER_REPOSITORY_PREFIX = 
                                        Globals.IMQ + ".user_repository.";
    public static final String PROP_USER_REPOSITORY_SUFFIX = ".user_repository";

    public static final String PROP_USER_PRINCIPAL_CLASS_SUFFIX = ".userPrincipalClass";
    public static final String PROP_GROUP_PRINCIPAL_CLASS_SUFFIX = ".groupPrincipalClass";
    public static final String PROP_CLIENTIP = PROP_AUTHENTICATION_PREFIX+"clientip";

    public static final String PROP_AUTHENTICATION_TYPE =
                                        Globals.IMQ + ".authentication.type";
    public static final String PROP_AUTHENTICATION_TYPE_SUFFIX = ".authentication.type";
    public static final String PROP_ACCESSCONTROL_TYPE = 
                                        Globals.IMQ + ".accesscontrol.type";
    public static final String PROP_ACCESSCONTROL_TYPE_SUFFIX = ".accesscontrol.type";
    public static final String PROP_ACCESSCONTROL_ENABLED =
                                        Globals.IMQ + ".accesscontrol.enabled";
    public static final String PROP_ACCESSCONTROL_ENABLED_SUFFIX = ".accesscontrol.enabled";
    public static final String PROP_SERVICE_PREFIX = Globals.IMQ + ".";

    public static final String PROPERTIES_DIRPATH = "dirpath";

    public static final String PROP_ADMINKEY = Globals.IMQ + ".adminkey";

    public static final String AUTHTYPE_BASIC     = "basic";
    public static final String AUTHTYPE_DIGEST    = "digest";
    public static final String AUTHTYPE_JMQADMINKEY = "jmqadminkey";
    public static final String BAD_AUTHTYPE = "client";

    private String authType = AUTHTYPE_BASIC;
    private String accesscontrolType = "";
    private String userRepository = "";

    private boolean accessControlEnabled = true;

    private Properties authprops = new Properties();

    private Logger logger = Globals.getLogger();

    private AuthenticationProtocolHandler aph = null;
    private AccessControlContext acc = null;

    private String serviceName = null;
    private int serviceType = ServiceType.NORMAL;
    private String clientIP = null;

    private AccessController() { }

    private void init() throws BrokerException {
        acc = null;

        if (authType.equals(AUTHTYPE_BASIC)) {
        aph = new com.sun.messaging.jmq.jmsserver.auth.JMQBasicAuthenticationHandler();
        return;
        }

        if (authType.equals(AUTHTYPE_DIGEST)) {
        aph = new com.sun.messaging.jmq.jmsserver.auth.JMQDigestAuthenticationHandler();
        return;
        }

        if (authType.equals(AUTHTYPE_JMQADMINKEY)) {
        aph = new com.sun.messaging.jmq.jmsserver.auth.JMQAdminKeyAuthenticationHandler();
        return;
        }

        String c = authprops.getProperty(PROP_AUTHENTICATION_PREFIX+authType+".class");
        if (c == null) {  
        throw new BrokerException(Globals.getBrokerResources().getKString(
                             BrokerResources.X_UNDEFINED_AUTHTYPE, authType));
        }
        try {
        aph = (AuthenticationProtocolHandler)Class.forName(c).newInstance();
        if (!aph.getType().equals(authType)) {
        String[] args = {authType, aph.getType(), c};
        throw new BrokerException(Globals.getBrokerResources().getKString(
                                  BrokerResources.X_AUTHTYPE_MISMATCH, args));
        }

        } catch (BrokerException e) {
        throw e;
        } catch (Throwable w) {
        throw new BrokerException(Globals.getBrokerResources().getKString(
                           BrokerResources.X_UNSUPPORTED_AUTHTYPE, authType)
                           + " - "+w.getMessage());
        }
    }

    public boolean isAuthenticated() {
        return (acc != null);
    }

    public String getAuthType() {
        return authType;
    }

    private void setAuthType(String authType) {
        this.authType = authType;
    }

    private void setAccessControlType(String t) {
        this.accesscontrolType = t;
    }

    public String getAccessControlType() {
        return accesscontrolType;
    }

    public String getUserRepository() {
        return userRepository;
    }

    private void setUserRepository(String v) {
        userRepository = v;
    }

    public boolean isAccessControlEnabled() {
        return accessControlEnabled;
    }

    private void setAccessControlEnabled(boolean enabled) {
        this.accessControlEnabled = enabled;
    }

    public Properties getAuthProperties() {
        return authprops;
    }

    public AccessControlContext getAccessControlContext() {
        return acc;
    }

    /*
    private void setAccessControlContext(AccessControlContext acc) {
        this.acc = acc;
    }
    */

    private void setServiceName(String sn) {
        serviceName =sn;
    }
    private String getServiceName() {
        return serviceName;
    }
    private void setServiceType(int st) {
        serviceType =st;
    }
    private int getServiceType() {
        return serviceType;
    }
    public void setClientIP(String clientip) {
        clientIP = clientip;
    }
    private String getClientIP() {
        return clientIP;
    }

    public boolean isRestrictedAdmin() {
        return authType.equals(AUTHTYPE_JMQADMINKEY);
    }

    public static AccessController getInstance(String serviceName, int serviceType) 
                                   throws BrokerException {
        return (getInstance(serviceName, serviceType, false));
    }

    public static AccessController getInstance(String serviceName, int serviceType, boolean forceBasic) 
                                   throws BrokerException {

        AccessController ac = new AccessController();
        ac.setServiceName(serviceName);
        ac.setServiceType(serviceType);

        String value = null;
        BrokerConfig config = Globals.getConfig();

        value = config.getProperty(PROP_ACCESSCONTROL_ENABLED);
        if (value != null && value.equals("false")) {
            ac.setAccessControlEnabled(false);
        }
        value = config.getProperty(PROP_SERVICE_PREFIX+serviceName+
                                   PROP_ACCESSCONTROL_ENABLED_SUFFIX);
        if (value != null && !value.trim().equals("")) {
            if (value.equals("false")) {
                ac.setAccessControlEnabled(false);
            }
            else {
                ac.setAccessControlEnabled(true);
            }
        }
        ac.getAuthProperties().setProperty(PROP_ACCESSCONTROL_ENABLED,
                     (ac.isAccessControlEnabled() ? "true":"false"));

        value = config.getProperty(PROP_SERVICE_PREFIX+serviceName+
                                   PROP_AUTHENTICATION_TYPE_SUFFIX);
        if (value == null || value.trim().equals("")) {
            value = config.getProperty(PROP_AUTHENTICATION_TYPE);
        }
        if (value != null && !value.trim().equals("")) {
            ac.setAuthType(value);
        }

	if (forceBasic)  {
            ac.setAuthType(AUTHTYPE_BASIC);
	}

        if (ac.getAuthType().equals(AUTHTYPE_JMQADMINKEY)
            || ac.getAuthType().equals(BAD_AUTHTYPE)) {
        throw new BrokerException(Globals.getBrokerResources().getKString(
                           BrokerResources.X_UNSUPPORTED_AUTHTYPE, ac.getAuthType()));
        }
        ac.getAuthProperties().setProperty(PROP_AUTHENTICATION_TYPE, ac.getAuthType());

        loadProps(ac);
        return ac;
    }

    private static void loadProps(AccessController ac) throws BrokerException {
        BrokerConfig config = Globals.getConfig();
        String serviceName = ac.getServiceName();

        ac.getAuthProperties().setProperty(PROP_SERVICE_NAME, ac.getServiceName());
        ac.getAuthProperties().setProperty(PROP_SERVICE_TYPE, 
                                 ServiceType.getServiceTypeString(ac.getServiceType()));

        //first get system wide properties
        getProps(ac.getAuthProperties(), 
                     PROP_AUTHENTICATION_PREFIX, ac.getAuthType(), null, null);
        //get service instance properties - instance override system
        getProps(ac.getAuthProperties(), 
                 PROP_AUTHENTICATION_PREFIX, ac.getAuthType(),
                 PROP_AUTHENTICATION_AREA, serviceName);

        String value = config.getProperty(PROP_SERVICE_PREFIX+serviceName+
                                          PROP_ACCESSCONTROL_TYPE_SUFFIX);
        if (value == null || value.trim().equals("")) {
        value = config.getProperty(PROP_ACCESSCONTROL_TYPE);
        }
        if (value == null || value.trim().equals("")) {
           if (ac.isAccessControlEnabled()) {
           throw new BrokerException(Globals.getBrokerResources().getKString(
                            BrokerResources.X_ACCESSCONTROL_TYPE_NOT_DEFINED));
           }
        }
        else {
            ac.setAccessControlType(value);
            ac.getAuthProperties().setProperty(PROP_ACCESSCONTROL_TYPE, value);
            getProps(ac.getAuthProperties(), PROP_ACCESSCONTROL_PREFIX, value, null, null);
            getProps(ac.getAuthProperties(), 
                     PROP_ACCESSCONTROL_PREFIX, value,
                     PROP_ACCESSCONTROL_AREA, serviceName);
        }

        value =ac.getAuthProperties().getProperty(
                     PROP_AUTHENTICATION_PREFIX + ac.getAuthType()+
                                         PROP_USER_REPOSITORY_SUFFIX);
        if (value != null && !value.trim().equals("")) {
            ac.setUserRepository(value);
            getProps(ac.getAuthProperties(), PROP_USER_REPOSITORY_PREFIX, value, null, null);
            getProps(ac.getAuthProperties(), 
                     PROP_USER_REPOSITORY_PREFIX, value,
                     PROP_USER_REPOSITORY_AREA, serviceName);
        }

        ac.init();
    }

    private static List getPropNames(String prefix, String type)
    {
        return Globals.getConfig().getList(prefix + type + ".properties");
    }

    private static void getProps(Properties props, String prefix,
                        String type, String area, String servicename)
    {
        String realprefix = prefix;
        if (servicename != null) {
            realprefix = PROP_SERVICE_PREFIX+servicename+"." + area + ".";
        }

        //eg. jmq.accesscontrol.file.properties=class,filename
        List propnames = getPropNames(prefix, type);

        if (propnames == null) return;

        String pname, pvalue;
        int size = propnames.size();
        for (int i = 0; i < size; i++) {
            pname = (String)propnames.get(i);
            pvalue = Globals.getConfig().getProperty(realprefix+type+"."+pname);
            if (pvalue !=  null) {
                if (pname.equals(PROPERTIES_DIRPATH)) {
                    pvalue = StringUtil.expandVariables(pvalue, Globals.getConfig());
                }
                props.setProperty((prefix+type+"."+pname), pvalue);
                props.setProperty((realprefix+type+"."+pname), pvalue);
            }
        }
    }

    public synchronized byte[] getChallenge(int seq, Properties props,
                               Refreshable cacheData, String overrideType)
                               throws BrokerException, LoginException {
        if (aph == null) throw new LoginException(
               Globals.getBrokerResources().getKString(
                         BrokerResources.X_CONNECTION_LOGGEDOUT));

        acc = null;   //set to unauthenticated 

        if (overrideType != null) {

        if (!overrideType.equals(AUTHTYPE_JMQADMINKEY) 
                    || serviceType != ServiceType.ADMIN) {
            String[] args = {overrideType, serviceName, 
                             ServiceType.getServiceTypeString(serviceType)};
            throw new LoginException(Globals.getBrokerResources().getKString(
                                     BrokerResources.X_AUTHTYPE_OVERRIDE, args));
        }
        String adminkey = Globals.getConfig().getProperty(PROP_ADMINKEY);
        if (adminkey == null) {
            throw new LoginException(Globals.getBrokerResources().getKString(
                                        BrokerResources.X_ADMINKEY_NOT_EXIST));
        }
        authprops = new Properties();
        setAuthType(AUTHTYPE_JMQADMINKEY);
        authprops.setProperty(PROP_AUTHENTICATION_TYPE, AUTHTYPE_JMQADMINKEY);

        setAccessControlEnabled(false);
        authprops.setProperty(PROP_ACCESSCONTROL_ENABLED, "false");

        authprops.setProperty(PROP_ADMINKEY, adminkey);

        loadProps(this);

        }

        Properties p = (Properties)getAuthProperties().clone();
        p.putAll(props);
        if (getClientIP() != null) p.setProperty(PROP_CLIENTIP, getClientIP());
        return aph.init(seq, p, cacheData);
    }

    /**
     * handle client authentication response
     */
    public byte[] handleResponse(byte[] authResponse, int sequence) throws LoginException {
        if (aph == null) throw new LoginException(
               Globals.getBrokerResources().getKString(
                         BrokerResources.X_CONNECTION_LOGGEDOUT));
        byte[] request =  aph.handleResponse(authResponse, sequence);
        if (request == null) {
            acc = aph.getAccessControlContext();
        }
        return request;
    }
    /**
     * should only be called when handleResponse successfully complete
     */
    public synchronized Refreshable getCacheData() {
        if (isAuthenticated()) {
            if (aph != null) {
            return aph.getCacheData();
            }
        }
        return null;
    }

    /**
     * canbe called from admin shutdown broker
     */
    public synchronized void logout() {
        try {
        acc = null;
        if (aph != null) {
            aph.logout();
        }

        } catch (LoginException e) {
            logger.log(Logger.WARNING, "Logout exception : "+e.getMessage(), e);
        }

    }
    
    public synchronized Subject getAuthenticatedSubject() throws BrokerException {
        if (isAuthenticated()) {
            return ((JMQAccessControlContext)acc).getSubject();
        }
        throw new BrokerException(Globals.getBrokerResources().getKString(
                           BrokerResources.X_CONNECTION_NOT_AUTHENTICATED));
    }

    /**
     * @exception BrokerException if not authenticated
     */
    public synchronized Principal getAuthenticatedName() throws BrokerException {
        if (isAuthenticated()) {
            return acc.getClientUser();
        }
        throw new BrokerException(Globals.getBrokerResources().getKString(
                           BrokerResources.X_CONNECTION_NOT_AUTHENTICATED));
    } 

    public synchronized void checkConnectionPermission(String serviceName,
                                          String serviceType) 
                                          throws AccessControlException {
        if (!isAuthenticated()) {
            throw new AccessControlException(
                        Globals.getBrokerResources().getKString(
                           BrokerResources.X_CONNECTION_NOT_AUTHENTICATED));
        }
        if (!isAccessControlEnabled() && !serviceType.equals("ADMIN")) {
            return; 
        }
        acc.checkConnectionPermission(serviceName, serviceType);
    }

    public synchronized void checkDestinationPermission(String serviceName,
                                           String serviceType,
                                           String operation,
                                           String destination,
                                           String destinationType)
                                           throws AccessControlException {
        if (!isAuthenticated()) {
            throw new AccessControlException(
                        Globals.getBrokerResources().getKString(
                           BrokerResources.X_CONNECTION_NOT_AUTHENTICATED));
        }
        if (!isAccessControlEnabled()) {
            return; 
        }
        acc.checkDestinationPermission(serviceName, serviceType, operation,
                                       destination, destinationType);
    }

    //private static final String DEFAULT_POLICY_FILENAME = "broker.policy";

    public static void setSecurityManagerIfneed() 
                       throws SecurityException, BrokerException {

        boolean need = false;
        String svcname = null;
        String svctype = null;
        AccessController ac = null;
        //BrokerConfig bcfg = Globals.getConfig();
        Logger logger = Globals.getLogger();

        String pp = null, svcpp = null;
        List activesvcs= ServiceManager.getAllActiveServiceNames();
        Iterator itr = activesvcs.iterator();
        while (itr.hasNext()) {
            svcname = (String)itr.next();
            svctype = ServiceManager.getServiceTypeString(svcname);
            if (svctype == null) { 
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_SERVICE_TYPE_NOT_FOUND_FOR_SERVICE, svcname));
            }
           
            ac = AccessController.getInstance(svcname, ServiceType.getServiceType(svctype));
            if (!ac.isAccessControlEnabled()) continue;
            if (ac.getAccessControlType().equals(JAASAccessControlModel.TYPE)) {
                need = true; 
                svcpp = ac.getAuthProperties().getProperty(
                                  AccessController.PROP_ACCESSCONTROL_PREFIX+
                                  JAASAccessControlModel.PROP_POLICY_PROVIDER) ;
                if (pp == null) {
                    pp = svcpp; 
                    continue;
                }
                if (svcpp == null) continue;
                if (!pp.equals(svcpp)) {
                    throw new BrokerException("XI18N - Multiple Java policy providers is not allowed:"
                                               +pp+", "+svcpp);
                }                              
            }
        }
        if (!need) return;

        Policy ppc = null; 
        if (pp != null) {
            try {
                ppc = (Policy)Class.forName(pp).newInstance();
            } catch (Exception e) {
                throw new BrokerException(e.getClass().getName()+": "+ 
                      e.getMessage()+" - "+
                      AccessController.PROP_ACCESSCONTROL_PREFIX+
                      JAASAccessControlModel.PROP_POLICY_PROVIDER+"="+pp);
            }
        }

        synchronized(System.class) {
            if (System.getSecurityManager() == null) {
                String val = System.getProperty("java.security.policy");
                if (val == null) {
                    /*
                    logger.log(logger.INFO, "Set java.security.policy to MQ default policy file");
                    System.setProperty("java.security.policy", 
                    "file:"+Globals.getInstanceEtcDir()+File.separator+DEFAULT_POLICY_FILENAME);
                    */
                } else {
                    logger.log(logger.INFO, "java.security.policy="+val);
                }
                System.setSecurityManager(new SecurityManager());
                logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                                  BrokerResources.I_SET_DEFAULT_SECURITY_MANAGER));
            }
        }
        if (ppc != null) {
            logger.log(logger.INFO, AccessController.PROP_ACCESSCONTROL_PREFIX+
                                    JAASAccessControlModel.PROP_POLICY_PROVIDER+"="+pp);
            Policy.setPolicy(ppc);
            logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                    BrokerResources.I_SET_JAVA_POLICY_PROVIDER, ppc.getClass().getName()));
        }
    }
}
