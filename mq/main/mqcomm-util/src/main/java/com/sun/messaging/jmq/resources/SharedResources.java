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

package com.sun.messaging.jmq.resources;

import java.util.ResourceBundle;
import java.util.Locale;
import com.sun.messaging.jmq.util.MQResourceBundle;

/**
 * This class wraps a PropertyResourceBundle, and provides constants to use as message keys. The reason we use constants
 * for the message keys is to provide some compile time checking when the key is used in the source.
 */

public class SharedResources extends MQResourceBundle {

    private static SharedResources resources = null;

    public static synchronized SharedResources getResources() {
        return getResources(null);
    }

    public static synchronized SharedResources getResources(Locale locale) {

        if (locale == null) {
            locale = Locale.getDefault();
        }

        if (resources == null || !locale.equals(resources.getLocale())) {
            ResourceBundle prb = ResourceBundle.getBundle("com.sun.messaging.jmq.resources.SharedResources", locale);
            resources = new SharedResources(prb);
        }
        return resources;
    }

    private SharedResources(ResourceBundle rb) {
        super(rb);
    }

    /*****************
     * Start of message key constants ******************* We use numeric values as the keys because the we have a
     * requirement that each error message have an associated error code (for documentation purposes). We use numeric
     * Strings instead of primitive integers because that is what ListResourceBundles support. We could write our own
     * ResourceBundle to support integer keys, but since we'd just be converting them back to strings (to display them) it's
     * unclear if that would be a big win. Also the performance of ListResourceBundles under Java 2 is pretty good.
     *
     *
     * Note To Translators: Do not copy these message key String constants into the locale specific resource bundles. They
     * are only required in this default resource bundle.
     */

    // 0-999 Miscellaneous messages
    public static final String M_ERROR = "S0000";
    public static final String M_WARNING = "S0001";

    // 1000-1999 Informational Messages
    public static final String I_BANNER_LINE = "S1000";
    public static final String I_VERSION = "S1001";
    public static final String I_COMPILE = "S1002";
    public static final String I_RIGHTS = "S1003";
    public static final String I_VERSION_INFO = "S1004";
    public static final String I_IMPLEMENTATION = "S1005";
    public static final String I_PROTOCOL_VERSION = "S1006";
    public static final String I_TARGET_JMS_VERSION = "S1007";
    public static final String I_RSA_CREDIT = "S1008";
    public static final String I_PATCHES = "S1009";
    public static final String I_PATCH_INDENT = "S1010";
    /*
     * final public static String I_SHORT_COPYRIGHT = "S1011";
     */

    // 2000-2999 Warning Messages
    public static final String W_BAD_NFORMAT = "S2000";
    public static final String W_BAD_LOGLEVELSTR = "S2001";
    public static final String W_BAD_LOGSTREAM = "S2002";
    public static final String W_BAD_LOGCONFIG = "S2003";
    public static final String W_LOGCHANNEL_DISABLED = "S2004";
    public static final String W_SET_UNCAUGHT_EX_HANDLER_FAIL = "S2005";
    public static final String W_SCHEDULE_UNCAUGHT_EX_HANDLER_TASK_FAIL = "S2006";

    // 3000-3999 Error Messages
    public static final String E_BAD_LOGFILE = "S3000";
    public static final String E_BAD_LOGDEVICE = "S3001";
    public static final String E_LOGMESSAGE = "S3002";
    public static final String E_NO_LOGHANDLERLIST = "S3003";
    public static final String E_NO_LOGHANDLER = "S3004";
    public static final String E_BAD_LOGHANDLERCLASS = "S3005";
    public static final String E_VERSION_PROPS = "S3006";
    public static final String E_VERSION_LOAD = "S3007";
    public static final String E_VERSION_INFO = "S3008";
    public static final String E_CANNOT_COMPACT_ON_OPENED_FILE = "S3009";
    public static final String E_VRFILE_NOT_OPEN = "S3010";
    public static final String E_RENAME_TO_BACKUP_FILE_FAILED = "S3011";
    public static final String E_RENAME_TO_BACKING_FILE_FAILED = "S3012";
    public static final String E_DELETE_BACKUP_FILE_FAILED = "S3013";
    public static final String E_BAD_FILE_MAGIC_NUMBER = "S3014";
    public static final String E_BAD_VRFILE_VERSION = "S3015";
    public static final String E_UNRECOGNIZED_VRECORD = "S3016";
    public static final String E_UNRECOGNIZED_VRFILE_FORMAT = "S3017";
    public static final String E_BAD_APPLICATION_COOKIE = "S3018";
    public static final String E_UNCAUGHT_EX_IN_THREAD = "S3019";

    // 4000-4999 Exception Messages
    public static final String X_DIR_CREATE = "S4000";
    public static final String X_FILE_WRITE = "S4001";
    public static final String X_DIR_NOT_FILE = "S4002";
    public static final String X_FILE_WRITE_TIMESTAMP = "S4003";
    public static final String X_FILE_READ_TIMESTAMP = "S4004";
    public static final String X_BAD_PROPERTY = "S4005";
    public static final String X_BAD_PORTMAPPER_VERSION = "S4006";

    public static final String X_PORTMAPPER_SOCKET_CLOSED_UNEXPECTEDLY = "S4011";

    /***************** End of message key constants *******************/
}
