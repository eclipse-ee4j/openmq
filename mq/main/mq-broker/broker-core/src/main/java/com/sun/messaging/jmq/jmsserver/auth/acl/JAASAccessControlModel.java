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
 * @(#)JAASAccessControlModel.java	1.7 06/28/07
 */ 
 
package com.sun.messaging.jmq.jmsserver.auth.acl;

import java.util.Map;
import java.util.Properties;
import java.lang.reflect.InvocationTargetException;
import java.security.Principal;
import java.security.Permission;
import java.security.AccessControlException;
import java.security.Policy;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.auth.jaas.*;
import com.sun.messaging.jmq.auth.api.server.*;
import com.sun.messaging.jmq.auth.api.server.model.*;

/**
 * JAASAccessControlModel 
 */

public class JAASAccessControlModel implements AccessControlModel {

    public static final String TYPE = "jaas";

    public static final String PROP_PERMISSION_FACTORY = TYPE + ".permissionFactory";
    public static final String PROP_PERMISSION_FACTORY_PRIVATE = TYPE + ".permissionFactoryPrivate";
    public static final String PROP_POLICY_PROVIDER = TYPE + ".policyProvider";

    //private static boolean DEBUG = false;
    private Logger logger = Globals.getLogger();

    //private String type;
    private Properties authProps;

    private PermissionFactory permFactory = null;
    private String permFactoryPrivate = null;
    //private Policy policyProvider = null;

    public String getType() {
        return TYPE;
    }

    /**
     * This method is called immediately after this AccessControlModel
     * has been instantiated and prior to any calls to its other public
     * methods.
     *
	 * @param type the jmq.accesscontrol.type
     * @param authProperties broker auth properties
     */
    public void initialize(String type, Properties authProperties)
                                     throws AccessControlException {
        //this.type = type;
        if (!type.equals(TYPE)) {
            String[] args = {type, TYPE, this.getClass().getName()};
            String emsg = Globals.getBrokerResources().getKString(
                       BrokerResources.X_ACCESSCONTROL_TYPE_MISMATCH, args);
            logger.log(Logger.ERROR, emsg);
            throw new AccessControlException(emsg);
        }
        authProps = authProperties;

        String pfclass = authProps.getProperty(
                         AccessController.PROP_ACCESSCONTROL_PREFIX+
                         PROP_PERMISSION_FACTORY);
        String ppclass = authProps.getProperty(
                         AccessController.PROP_ACCESSCONTROL_PREFIX+
                         PROP_POLICY_PROVIDER); 
        try {
            if (pfclass != null) {
                permFactory = (PermissionFactory)Class.forName(pfclass).newInstance();
            }
            //if (ppclass != null) policyProvider = (Policy)Class.forName(ppclass).newInstance();
            if (ppclass != null) {
                Class.forName(ppclass).newInstance();
            }
        } catch (Exception e) {
            logger.logStack(Logger.ERROR, e.getMessage(), e);
            throw new AccessControlException(e.getClass().getName()+": "+e.getMessage());
        }

        permFactoryPrivate = (String)authProps.getProperty(
                         AccessController.PROP_ACCESSCONTROL_PREFIX+
                         PROP_PERMISSION_FACTORY_PRIVATE); 

        load();
    }

    public void load() throws AccessControlException {

        try {
            Policy.getPolicy().refresh(); 
        } catch (SecurityException e) {
           AccessControlException ace = new AccessControlException(e.toString());
           ace.initCause(e);
           throw ace;
        }
    }

   /**
    *
    * Check connection permission 
    *
    * @param clientUser The Principal represents the client user that is
    *                   associated with the subject
    * @param serviceName the service instance name  (eg. "broker", "admin")
    * @param serviceType the service type for the service instance 
    *                    ("NORMAL" or "ADMIN")
    * @param subject the authenticated subject
    *
    * @exception AccessControlException 
    */
    public void checkConnectionPermission(Principal clientUser, 
                                          String serviceName, 
                                          String serviceType,
                                          Subject subject) 
                                          throws AccessControlException {

       Permission perm;
       try {
           perm = permFactory.newPermission(permFactoryPrivate, 
                                 PermissionFactory.CONN_RESOURCE_PREFIX+
                                 serviceType, (String)null, (Map)null);
       } catch (Exception e) {
           logger.logStack(Logger.ERROR, e.toString(), e);
           AccessControlException ace = new AccessControlException(e.toString());
           ace.initCause(e);
           throw ace;
       }
       try {
           checkPermission(subject, perm); 
       } catch (AccessControlException e) {
           AccessControlException ace = new AccessControlException(e.getMessage()+": "+
		                clientUser+" ["+subject.getPrincipals()+"]");
           ace.initCause(e);
           throw ace;
       }
    }

   /**
    * Check permission for an operation on a destination for this role
    *
    * @param clientUser The Principal represents the client user that is
    *                   associated with the subject
    * @param serviceName the service instance name  (eg. "broker", "admin")
    * @param serviceType the service type for the service instance 
    *                    ("NORMAL" or "ADMIN")
    * @param subject the authenticated subject
    * @operation the operaction 
    * @destination the destination
    *
    * @exception AccessControlException 
    */
    public void checkDestinationPermission(Principal clientUser,
                                           String serviceName,
                                           String serviceType,
                                           Subject subject,
                                           String operation,
                                           String destination,
                                           String destinationType)
                                           throws AccessControlException {
       Permission perm;
       try {
           if (operation.equals(PacketType.AC_DESTCREATE)) {
           perm = permFactory.newPermission(permFactoryPrivate, 
                                 PermissionFactory.AUTO_RESOURCE_PREFIX+
                                 PermissionFactory.DEST_QUEUE, (String)null, (Map)null);
           } else {
           perm = permFactory.newPermission(permFactoryPrivate, 
                                 PermissionFactory.DEST_RESOURCE_PREFIX+
                                 PermissionFactory.DEST_QUEUE_PREFIX+destination,
                                 operation, (Map)null);
           }
       } catch (Exception e) {
           logger.logStack(Logger.ERROR, e.toString(), e);
           AccessControlException ace = new AccessControlException(e.toString());
           ace.initCause(e);
           throw ace;
       }
       try {
           checkPermission(subject, perm); 
       } catch (AccessControlException e) {
           AccessControlException ace = new AccessControlException(e.getMessage()+": "+
		                clientUser+" ["+subject.getPrincipals()+"]");
           ace.initCause(e);
           throw ace;
       }
    }

    private void checkPermission(Subject subject, Permission p) 
                                     throws AccessControlException {

        final Permission perm = p;
        Subject.doAsPrivileged(subject, new PrivilegedAction() {
                public Object run() {
                    java.security.AccessController.checkPermission(perm);
                    return null; // nothing to return
                }
        }, null);
       
    }

}
