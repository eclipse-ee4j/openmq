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

package com.sun.messaging.bridge.admin.bridgemgr.resources;

import java.util.ResourceBundle;
import java.util.Locale;
import com.sun.messaging.jmq.util.MQResourceBundle;
import com.sun.messaging.bridge.api.BridgeCmdSharedResources;

/**
 * This class wraps a PropertyResourceBundle, and provides constants
 * to use as message keys. The reason we use constants for the message
 * keys is to provide some compile time checking when the key is used
 * in the source.
 */

public class BridgeAdminResources extends MQResourceBundle {

    private static BridgeAdminResources resources = null;

    public static BridgeAdminResources getResources() {
        return getResources(null);
    }

    public static synchronized BridgeAdminResources getResources(Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }

        if (resources == null || !locale.equals(resources.getLocale())) {
            ResourceBundle prb =
                ResourceBundle.getBundle(
                "com.sun.messaging.bridge.admin.bridgemgr.resources.BridgeAdminResources",
                locale);
            resources = new BridgeAdminResources(prb);
        }

	return resources;
    }

    private BridgeAdminResources(ResourceBundle rb) {
        super(rb);
    }


    /***************** Start of message key constants *******************
     * We use numeric values as the keys because the Broker has a requirement
     * that each error message have an associated error code (for 
     * documentation purposes). We use numeric Strings instead of primitive
     * integers because that is what ListResourceBundles support. We could
     * write our own ResourceBundle to support integer keys, but since
     * we'd just be converting them back to strings (to display them)
     * it's unclear if that would be a big win. Also the performance of
     * ListResourceBundles under Java 2 is pretty good.
     * 
     *
     * Note To Translators: Do not copy these message key String constants
     * into the locale specific resource bundles. They are only required
     * in this default resource bundle.
     *
     */

    // 0-999     Miscellaneous messages

    /*********************************************************************
     * 1000-1999 Informational Messages
     ********************************************************************/ 
    final public static String I_BGMGR_HELP_USAGE                = "BA1000";
    final public static String I_BGMGR_HELP_SUBCOMMANDS          = "BA1001";
    final public static String I_BGMGR_HELP_OPTIONS              = "BA1002";
    final public static String I_BGMGR_HELP_EXAMPLES1            = "BA1006";
    final public static String I_BGMGR_HELP_EXAMPLES2            = "BA1007";
    final public static String I_BGMGR_HELP_EXAMPLES3            = "BA1008";
    final public static String I_BGMGR_HELP_EXAMPLES4            = "BA1009";
    final public static String I_BGMGR_HELP_EXAMPLES5            = "BA1010";
    final public static String I_BGMGR_HELP_EXAMPLES6            = "BA1011";
    final public static String I_BGMGR_HELP_EXAMPLES7            = "BA1012";

    final public static String I_BGMGR_HELP_ATTRIBUTES4          = "BA1013";
    final public static String I_BGMGRHELP_ATTRIBUTES5          = "BA1014";

    final public static String I_BGMGR_HELP_EXAMPLES8            = "BA1015";
    final public static String I_BGMGR_HELP_EXAMPLES9            = "BA1016";
    final public static String I_BGMGR_HELP_EXAMPLES10           = "BA1017";
    final public static String I_BGMGR_HELP_EXAMPLES11           = "BA1018";
    final public static String I_BGMGR_HELP_EXAMPLES12           = "BA1019";

    final public static String I_BGMGR_VALID_VALUES              = "BA1020";

    final public static String I_BGMGR_HELP_EXAMPLES13           = "BA1021";
    final public static String I_BGMGR_HELP_EXAMPLES14           = "BA1022";
    final public static String I_BGMGR_HELP_EXAMPLES15           = "BA1023";
    final public static String I_BGMGR_HELP_EXAMPLES16           = "BA1024";
    /**
     * removed BA1050 - BA1059 inclusive from here and BridgeAdminResources.properties
     * no longer used
     */
    final public static String I_BGMGR_ALL_BRIDGES_CMD_ON_BKR = "BA1060";
    final public static String I_BGMGR_ALL_TYPE_BRIDGES_CMD = "BA1061";
	final public static String I_BGMGR_SPECIFY_BKR = "BA1062";
    final public static String I_BGMGR_BRIDGE_CMD = "BA1063";
    final public static String I_BGMGR_BRIDGES_CMD_SUC = "BA1064";
    final public static String I_BGMGR_BRIDGE_CMD_SUC = "BA1065";
	final public static String I_BGMGR_BRIDGES_CMD_FAIL = "BA1066";
    final public static String I_BGMGR_BRIDGES_CMD_NOOP = "BA1067";
	final public static String I_BGMGR_BRIDGE_CMD_FAIL = "BA1068";
    final public static String I_BGMGR_BRIDGE_CMD_NOOP = "BA1069";
    final public static String I_BGMGR_LINK_CMD = "BA1070";
    final public static String I_BGMGR_LINK_CMD_SUC = "BA1071";
	final public static String I_BGMGR_LINK_CMD_FAIL = "BA1072";
    final public static String I_BGMGR_LINK_CMD_NOOP = "BA1073";

    final public static String I_BGMGR_CMD_list  = "BA1074";
	final public static String I_BGMGR_CMD_start = "BA1075";
    final public static String I_BGMGR_CMD_stop = "BA1076";
	final public static String I_BGMGR_CMD_resume = "BA1077";
	final public static String I_BGMGR_CMD_pause  = "BA1078";
    final public static String I_UNRECOGNIZED_RES = "BA1079";
    final public static String I_ERROR_MESG = "BA1080";

	final public static String I_BGMGR_BRIDGE_NAME = "BA1081";
	final public static String I_BGMGR_BRIDGE_TYPE = "BA1082";
	final public static String I_BGMGR_LINK_NAME = "BA1083";
	final public static String I_BGMGR_BRIDGE_ASYNC_STARTED = "BA1084";
	final public static String I_BGMGR_BRIDGES_ASYNC_STARTED = "BA1085";
	final public static String I_BGMGR_LINK_ASYNC_STARTED = "BA1086";

    // 2000-2999 Warning Messages

    // 3000-3999 Error Messages
    final public static String E_BRIDGE_NAME_NOT_SPEC          = "BA3000";
    final public static String E_LINK_NAME_NOT_SPEC            = "BA3001";
    final public static String E_OPTION_NOT_ALLOWED_FOR_CMDARG = "BA3002";

    // 4000-4999 Exception Messages

    // 5000-5999 Question Messages
    /**
     * removed Q_BRIDGES_CMD_OK = "BA5000";
     * from here and BridgeAdminResources.properties, not used
     */
    final public static String Q_BRIDGE_CMD_OK = "BA5001";
    final public static String Q_LINK_CMD_OK = "BA5002";

    final public static String Q_RESPONSE_YES_SHORT = "BA5003";
    final public static String Q_RESPONSE_YES = "BA5004";
    final public static String Q_RESPONSE_NO_SHORT = "BA5005";
    final public static String Q_RESPONSE_NO = "BA5006";

    /***************** End of message key constants *******************/
}
