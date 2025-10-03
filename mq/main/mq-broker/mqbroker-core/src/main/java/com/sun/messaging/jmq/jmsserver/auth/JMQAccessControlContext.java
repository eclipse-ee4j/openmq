/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.auth;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.Set;
import java.security.Principal;
import java.security.AccessControlException;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.auth.jaas.MQUser;
import com.sun.messaging.jmq.auth.api.server.*;
import com.sun.messaging.jmq.auth.api.server.model.AccessControlModel;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.Globals;

/**
 * JMQ AccessControlContext uses AccessControlModel interface for permission checks agaist a access control model
 */
@SuppressWarnings({
    "deprecation", "removal" // java.security.AccessControlException in java.security has been deprecated and marked for removal
})
public class JMQAccessControlContext implements AccessControlContext {
    private MQUser mquser;
    private Subject subject;
    private Properties authProps;
    private AccessControlModel acs = null;

    public JMQAccessControlContext(MQUser mquser, Subject subject, Properties authProperties) throws LoginException {
        this.mquser = mquser;
        this.subject = subject;
        authProps = authProperties;
        String acEnabled = authProps.getProperty(AccessController.PROP_ACCESSCONTROL_ENABLED);
        if (acEnabled != null && acEnabled.equals("false")) {
            return;
        }
        try {
            loadAccessControlModel();
        } catch (AccessControlException e) {
            throw new LoginException(e.getMessage());
        }
    }

    /** @throws AccessControlException */
    private void loadAccessControlModel() {
        String type = authProps.getProperty(AccessController.PROP_ACCESSCONTROL_PREFIX + "type");
        if (type == null || type.trim().equals("")) {
            throw new AccessControlException(Globals.getBrokerResources().getKString(BrokerResources.X_ACCESSCONTROL_TYPE_NOT_DEFINED));
        }
        String cn = authProps.getProperty(AccessController.PROP_ACCESSCONTROL_PREFIX + type + ".class");
        if (cn == null) {
            throw new AccessControlException(Globals.getBrokerResources().getKString(BrokerResources.X_ACCESSCONTROL_CLASS_NOT_DEFINED, type));
        }
        try {
            acs = (AccessControlModel) Class.forName(cn).getDeclaredConstructor().newInstance();
            acs.initialize(type, authProps);
        } catch (ClassNotFoundException e) {
            throw new AccessControlException(
                    Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "ClassNotFoundException: " + e.getMessage()));
        } catch (InstantiationException e) {
            throw new AccessControlException(
                    Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "InstantiationExcetpion: " + e.getMessage()));
        } catch (IllegalAccessException e) {
            throw new AccessControlException(
                    Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "IllegalAccessException: " + e.getMessage()));
        } catch (ClassCastException e) {
            throw new AccessControlException(
                    Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "ClassCastException: " + e.getMessage()));
        } catch (NoSuchMethodException e) {
            throw new AccessControlException(
                    Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "NoSuchMethodException: " + e.getMessage()));
        } catch (InvocationTargetException e) {
            throw new AccessControlException(
                    Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "InvocationTargetException: " + e.getMessage()));
        }
    }

    @Override
    public Principal getClientUser() {
        return mquser;
    }

    public Subject getSubject() {
        return subject;
    }

    /**
     * This method is always called for ADMIN service regardless jmq.accesscontrol
     *
     * @throws AccessControlException
     */
    @Override
    public void checkConnectionPermission(String serviceName, String serviceType) {
        if (serviceType.equals("ADMIN")) {
            String acEnabled = authProps.getProperty(AccessController.PROP_ACCESSCONTROL_ENABLED);
            if (acEnabled != null && acEnabled.equals("false")) {
                Class mqadminc = null;
                try {
                    mqadminc = Class.forName("com.sun.messaging.jmq.auth.jaas.MQAdminGroup");
                } catch (ClassNotFoundException e) {
                    throw new AccessControlException(
                            Globals.getBrokerResources().getKString(BrokerResources.X_INTERNAL_EXCEPTION, "ClassNotFoundException: " + e.getMessage()));
                }
                Set s = subject.getPrincipals(mqadminc);

                if (s == null || s.size() == 0) {
                    throw new AccessControlException(Globals.getBrokerResources().getKString(BrokerResources.X_NOT_ADMINISTRATOR, mquser.getName()));
                }
                return;
            }
        }
        if (acs == null) {
            loadAccessControlModel();
        }
        acs.checkConnectionPermission(mquser, serviceName, serviceType, subject);
    }

    /** @throws AccessControlException */
    @Override
    public void checkDestinationPermission(String serviceName, String serviceType, String operation, String destination, String destinationType) {
        if (acs == null) {
            loadAccessControlModel();
        }
        acs.checkDestinationPermission(mquser, serviceName, serviceType, subject, operation, destination, destinationType);
    }

}
