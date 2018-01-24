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

package com.sun.messaging.ums.resources;

import com.sun.messaging.jmq.util.MQResourceBundle;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 *
 * @author chiaming
 */
public class UMSResources extends MQResourceBundle {

    public static final String UMS_RESOURCE_BUNDLE_NAME =
        "com.sun.messaging.ums.resources.UMSResources";

    private static UMSResources resources = null;

    public static UMSResources getResources() {
        return getResources(null);
    }

    public static synchronized UMSResources getResources(Locale locale) {

        if (locale == null) {
            locale = Locale.getDefault();
        }

    if (resources == null || !locale.equals(resources.getLocale())) {
        ResourceBundle prb =
                ResourceBundle.getBundle(UMS_RESOURCE_BUNDLE_NAME, locale);
        resources = new UMSResources(prb);
    }

    return resources;
    }

    private UMSResources(ResourceBundle rb) {
        super(rb);
    }
    
    
    final public static String UMS_NEW_CLIENT_CREATED = "UMS1000";
    
    final public static String UMS_CLIENT_CLOSED = "UMS1001";
    
    final public static String UMS_SWEEPER_INIT = "UMS1002";
    
    final public static String UMS_PROVIDER_INIT = "UMS1003";
    
    final public static String UMS_DEST_SERVICE_INIT = "UMS1004";
   
    final public static String UMS_AUTH_BASE64_ENCODE = "UMS1005";
    
    final public static String UMS_LOGGER_INIT = "UMS1006";
    
    final public static String UMS_DEFAULT_RECEIVE_TIMEOUT = "UMS1007";
    
    final public static String UMS_CONFIG_INIT = "UMS1008";
    
    final public static String UMS_SERVICE_STARTED = "UMS1009";
}
