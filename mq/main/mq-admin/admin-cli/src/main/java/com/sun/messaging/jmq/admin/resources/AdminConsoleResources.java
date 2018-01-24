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
 * @(#)AdminConsoleResources.java	1.106 06/28/07
 */ 

package com.sun.messaging.jmq.admin.resources;

import java.util.ResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import com.sun.messaging.jmq.util.MQResourceBundle;

/**
 * This class wraps a PropertyResourceBundle, and provides constants
 * to use as message keys. The reason we use constants for the message
 * keys is to provide some compile time checking when the key is used
 * in the source.
 */

public class AdminConsoleResources extends MQResourceBundle {

    private static AdminConsoleResources resources = null;

    public static AdminConsoleResources getResources() {
        return getResources(null);
    }

    public static AdminConsoleResources getResources(Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }

        if (resources == null || !locale.equals(resources.getLocale())) {
            ResourceBundle prb =
                ResourceBundle.getBundle(
                "com.sun.messaging.jmq.admin.resources.AdminConsoleResources",
                locale);
            resources = new AdminConsoleResources(prb);
        }

	return resources;
    }

    private AdminConsoleResources(ResourceBundle rb) {
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
     * Note to iMQ engineers: Remove the sample entries e.g. I_SAMPLE_MESSAGE
     * when you add entries for that category.
     */

    // 0-999     Miscellaneous messages
    final public static String M_SAMPLE_MESSAGE		= "A0000";

    // 1000-1999 Informational Messages

    /*
     * Labels for menus and menu items
     */

    // Console menu
    final public static String I_MENU_CONSOLE		= "A1000";
    final public static String I_MENU_PREFERENCES	= "A1001";
    final public static String I_MENU_EXIT		= "A1002";

    // Edit menu
    final public static String I_MENU_EDIT		= "A1003";

    // Actions menu
    final public static String I_MENU_ACTIONS		= "A1004";
    final public static String I_MENU_PROPERTIES	= "A1005";

    // View menu
    final public static String I_MENU_VIEW		= "A1006";
    final public static String I_MENU_EXPAND_ALL	= "A1007";
    final public static String I_MENU_COLLAPSE_ALL	= "A1008";
    final public static String I_MENU_REFRESH		= "A1009";

    // Help menu
    final public static String I_MENU_HELP		= "A1010";
    final public static String I_MENU_ABOUT		= "A1011";

    // The "Add" item dynamically changes depending
    // on what is selected
    final public static String I_MENU_ADD		= "A1012";
    final public static String I_MENU_ADD_OBJSTORE	= "A1013";
    final public static String I_MENU_ADD_OBJSTORE_DEST	= "A1014";
    final public static String I_MENU_ADD_OBJSTORE_CF	= "A1015";
    final public static String I_MENU_ADD_BROKER	= "A1016";
    final public static String I_MENU_ADD_BROKER_DEST	= "A1017";

    // The "Delete" item dynamically changes depending
    // on what is selected
    final public static String I_MENU_DELETE		= "A1018";
    final public static String I_MENU_DELETE_OBJSTORE	= "A1019";
    final public static String I_MENU_DELETE_OBJSTORE_DEST	= "A1020";
    final public static String I_MENU_DELETE_OBJSTORE_CF	= "A1021";
    final public static String I_MENU_DELETE_BROKER		= "A1022";
    final public static String I_MENU_DELETE_BROKER_DEST	= "A1023";

    // The "Connect" item dynamically changes depending
    // on what is selected
    final public static String I_MENU_CONNECT		= "A1024";
    final public static String I_MENU_CONNECT_OBJSTORE	= "A1025";
    final public static String I_MENU_CONNECT_BROKER	= "A1026";

    // The "Disconnect" item dynamically changes depending
    // on what is selected
    final public static String I_MENU_DISCONNECT	= "A1027";
    final public static String I_MENU_DISCONNECT_OBJSTORE	= "A1028";
    final public static String I_MENU_DISCONNECT_BROKER	= "A1029";

    // The "Pause" item dynamically changes depending
    // on what is selected
    final public static String I_MENU_PAUSE		= "A1030";
    final public static String I_MENU_PAUSE_BROKER	= "A1031";
    final public static String I_MENU_PAUSE_SERVICE	= "A1032";

    // The "Resume" item dynamically changes depending
    // on what is selected
    final public static String I_MENU_RESUME		= "A1033";
    final public static String I_MENU_RESUME_BROKER	= "A1034";
    final public static String I_MENU_RESUME_SERVICE	= "A1035";

    // The "Shutdown" item
    final public static String I_MENU_SHUTDOWN_BROKER	= "A1036";

    // The "Restart" item
    final public static String I_MENU_RESTART_BROKER	= "A1037";

    // The "Purge" item
    final public static String I_MENU_PURGE_BROKER_DEST	= "A1038";
    /*
     * End of Labels for menus and menu items
     */

    /*
     * Start of menu/menu item mnemonics
     */
    // Console menu
    final public static String I_CONSOLE_MNEMONIC	= "A1039";
    final public static String I_PREFERENCES_MNEMONIC	= "A1040";
    final public static String I_EXIT_MNEMONIC		= "A1041";

    // Edit menu
    final public static String I_EDIT_MNEMONIC		= "A1042";

    // Actions menu
    final public static String I_ACTIONS_MNEMONIC	= "A1043";
    final public static String I_PROPERTIES_MNEMONIC	= "A1044";

    // View menu
    final public static String I_VIEW_MNEMONIC		= "A1045";
    final public static String I_EXPAND_ALL_MNEMONIC	= "A1046";
    final public static String I_COLLAPSE_ALL_MNEMONIC	= "A1047";
    final public static String I_REFRESH_MNEMONIC	= "A1048";

    // Help menu
    final public static String I_HELP_MNEMONIC		= "A1049";
    final public static String I_ABOUT_MNEMONIC		= "A1050";

    // The "Add" item
    final public static String I_ADD_MNEMONIC		= "A1051";

    // The "Delete" item
    final public static String I_DELETE_MNEMONIC	= "A1052";

    // The "Connect" item
    final public static String I_CONNECT_MNEMONIC	= "A1053";

    // The "Disconnect" item
    final public static String I_DISCONNECT_MNEMONIC	= "A1054";

    // The "Pause" item
    final public static String I_PAUSE_MNEMONIC		= "A1055";

    // The "Resume" item
    final public static String I_RESUME_MNEMONIC	= "A1056";

    // The "Shutdown" item
    final public static String I_SHUTDOWN_MNEMONIC	= "A1057";

    // The "Restart" item
    final public static String I_RESTART_MNEMONIC	= "A1058";

    // The "Purge" item
    final public static String I_PURGE_MNEMONIC		= "A1059";
    /*
     * End of menu/menu item mnemonics
     */


    /*
     * Start of menu/menu item keyboard accelerators
     * NOTE: No entry in content table yet since we currently
     * don't implement accelerators.
     */
    // Console menu items
    final public static String I_PREFERENCES_KBD_XCEL	= "A1060";
    final public static String I_EXIT_KBD_XCEL		= "A1061";

    // Actions menu items
    final public static String I_PROPERTIES_KBD_XCEL	= "A1062";

    // View menu items
    final public static String I_EXPAND_ALL_KBD_XCEL	= "A1063";
    final public static String I_COLLAPSE_ALL_KBD_XCEL	= "A1064";
    final public static String I_REFRESH_KBD_XCEL	= "A1065";

    // Help menu items
    final public static String I_ABOUT_KBD_XCEL		= "A1066";

    // The "Add" item
    final public static String I_ADD_KBD_XCEL		= "A1067";

    // The "Delete" item
    final public static String I_DELETE_KBD_XCEL	= "A1068";

    // The "Connect" item
    final public static String I_CONNECT_KBD_XCEL	= "A1069";

    // The "Disconnect" item
    final public static String I_DISCONNECT_KBD_XCEL	= "A1070";

    // The "Pause" item
    final public static String I_PAUSE_KBD_XCEL		= "A1071";

    // The "Resume" item
    final public static String I_RESUME_KBD_XCEL	= "A1072";

    // The "Shutdown" item
    final public static String I_SHUTDOWN_KBD_XCEL	= "A1073";

    // The "Restart" item
    final public static String I_RESTART_KBD_XCEL	= "A1074";

    // The "Purge" item
    final public static String I_PURGE_KBD_XCEL		= "A1075";
    /*
     * End of menu/menu item keyboard accelerators
     */


    /*
     * Start of basic admin dialog button labels
     */
    final public static String I_DIALOG_OK		= "A1076";
    final public static String I_DIALOG_APPLY		= "A1077";
    final public static String I_DIALOG_CLEAR		= "A1078";
    final public static String I_DIALOG_RESET		= "A1079";
    final public static String I_DIALOG_CANCEL		= "A1080";
    final public static String I_DIALOG_CLOSE		= "A1081";
    final public static String I_DIALOG_HELP		= "A1082";
    final public static String I_DIALOG_ADD 		= "A1083";
    final public static String I_DIALOG_DELETE		= "A1084";
    final public static String I_DIALOG_CHANGE		= "A1085";
    final public static String I_DIALOG_DO_NOT_SHOW_AGAIN = "A1086";
    /*
     * End of basic admin dialog button labels
     */

    /*
     * Start of object type labels.
     * These are labels of various objects (or collections
     * of objects) that can be manipulated in the admin console.
     * Example:
     *	"iMQ Object Stores"
     *	"Destinations"
     *	"Logs"
     */
    final public static String I_OBJSTORE_LIST		= "A1087";
    final public static String I_OBJSTORE		= "A1088";
    final public static String I_OBJSTORE_DEST_LIST	= "A1089";
    final public static String I_OBJSTORE_DEST		= "A1090";
    final public static String I_OBJSTORE_CF_LIST	= "A1091";
    final public static String I_OBJSTORE_CF		= "A1092";
    final public static String I_BROKER_LIST		= "A1093";
    final public static String I_BROKER			= "A1094";
    final public static String I_BROKER_SVC_LIST	= "A1095";
    final public static String I_BROKER_SVC		= "A1096";
    final public static String I_BROKER_DEST_LIST	= "A1097";
    final public static String I_BROKER_DEST		= "A1098";
    final public static String I_BROKER_LOG_LIST	= "A1099";
    final public static String I_BROKER_LOG		= "A1100";
    final public static String I_PURGE_MESSAGES	        = "A1101";
    final public static String I_OBJSTORE_REFRESH       = "A1102";
    final public static String I_OBJSTORE_REFRESH_DEST  = "A1103";
    final public static String I_OBJSTORE_REFRESH_CF    = "A1104";
    final public static String I_BROKER_REFRESH    	= "A1105";
    final public static String I_MENU_OVERVIEW 		= "A1106";
    final public static String I_BROKER_NAME2		= "A1107";
    final public static String I_BROKER_HOST2		= "A1108";
    final public static String I_PRIMARY_PORT		= "A1109";
    final public static String I_CONN_STATUS 		= "A1110";
    final public static String I_SVC_NAME    		= "A1111";
    final public static String I_PORT_NUMBER  		= "A1112";
    final public static String I_SVC_STATE    		= "A1113";
    final public static String I_PURGE    		= "A1114";

    /*
     * Pause/Resume destination label
     */
    final public static String I_MENU_PAUSE_DEST		= "A1115";
    final public static String I_MENU_RESUME_DEST		= "A1116";
    final public static String I_MENU_PAUSE_ALL_DESTS	= "A1117";
    final public static String I_MENU_RESUME_ALL_DESTS	= "A1118";

    /*
     * End of object type labels.
     */

    /*
     * Start of 'action' labels for non-menu usage. 
     *
     * We used to use the same strings for some menu items and other 
     * things like tooltips and dialog titles. The localization centers 
     * requested we separate them because the menu items need to have 
     * extra characters in parantheses to support mnemonics. This is due
     * to the fact that mnemonics need to be 'simple' characters and not
     * a complex Asian character. For example, the menu item may look
     * like:
     *       XXX (A)
     * and 'A' would be the assigned mnemonic.
     *
     * The above menu string makes an ugly dialog title or tooltip - which 
     * is why we * don't share such strings any more. Now we will have 
     * separate keys for menu item labels and strings that can be used in 
     * places like dialog titles. The menu item labels will have keys 
     * named
     *     I_MENU_*
     *
     * For example:
     *  I_MENU_PAUSE_DEST
     *
     * The other labels that have similar meaning but not for menus will
     * have similar named keys but without 'MENU' in it.
     *
     * For example:
     *  I_PAUSE_DEST
     *
     * The existing menu related keys in this file are converted to 
     * I_MENU_* (because the were originally created for menu usage).
     * The (new) equivalent keys for non-menu usage are (re)created 
     * below.
     *
     * The bug ID relevant to this work is: 5029191
     */
    final public static String I_PREFERENCES		= "A1120";
    final public static String I_EXIT			= "A1121";
    final public static String I_PROPERTIES		= "A1122";
    final public static String I_EXPAND_ALL		= "A1123";
    final public static String I_COLLAPSE_ALL		= "A1124";
    final public static String I_REFRESH		= "A1125";
    final public static String I_ABOUT			= "A1126";
    final public static String I_ADD			= "A1127";
    final public static String I_ADD_OBJSTORE		= "A1128";
    final public static String I_ADD_OBJSTORE_DEST	= "A1129";
    final public static String I_ADD_OBJSTORE_CF	= "A1130";
    final public static String I_ADD_BROKER		= "A1131";
    final public static String I_ADD_BROKER_DEST	= "A1132";
    final public static String I_DELETE			= "A1133";
    final public static String I_DELETE_OBJSTORE	= "A1134";
    final public static String I_DELETE_OBJSTORE_DEST	= "A1135";
    final public static String I_DELETE_OBJSTORE_CF	= "A1136";
    final public static String I_DELETE_BROKER		= "A1137";
    final public static String I_DELETE_BROKER_DEST	= "A1138";
    final public static String I_CONNECT		= "A1139";
    final public static String I_CONNECT_OBJSTORE	= "A1140";
    final public static String I_CONNECT_BROKER		= "A1141";
    final public static String I_DISCONNECT		= "A1142";
    final public static String I_DISCONNECT_OBJSTORE	= "A1143";
    final public static String I_DISCONNECT_BROKER	= "A1144";
    final public static String I_PAUSE			= "A1145";
    final public static String I_PAUSE_BROKER		= "A1146";
    final public static String I_PAUSE_SERVICE		= "A1147";
    final public static String I_RESUME			= "A1148";
    final public static String I_RESUME_BROKER		= "A1149";
    final public static String I_RESUME_SERVICE		= "A1150";
    final public static String I_SHUTDOWN_BROKER	= "A1151";
    final public static String I_RESTART_BROKER		= "A1152";
    final public static String I_PURGE_BROKER_DEST	= "A1153";
    final public static String I_OVERVIEW 		= "A1154";
    final public static String I_PAUSE_DEST		= "A1155";
    final public static String I_RESUME_DEST		= "A1156";
    final public static String I_PAUSE_ALL_DESTS	= "A1157";
    final public static String I_RESUME_ALL_DESTS	= "A1158";
    final public static String I_QUERY_BROKER       	= "A1159";

    /*
     * End of menu item labels.
     */

    /* 
     * Start of some general admin console labels.
     */
    final public static String I_ADMIN_CONSOLE 		= "A1200";
    final public static String I_QUEUE                  = "A1201";
    final public static String I_TOPIC                  = "A1202";
    final public static String I_QCF                    = "A1203";
    final public static String I_TCF                    = "A1204";
    final public static String I_NAME                   = "A1205";
    final public static String I_VALUE                  = "A1206";
    final public static String I_CONTENTS               = "A1207";
    final public static String I_COUNT                  = "A1208";
    final public static String I_CONNECT_UPON_ADDING    = "A1209";
    final public static String I_CONNECT_AFTER_UPDATES  = "A1210";
    final public static String I_CONNECTED		= "A1211";
    final public static String I_DISCONNECTED		= "A1212";

    /*
     * some old/unused items were removed:
    final public static String I_VERSION                = "A1213";
    final public static String I_COPYRIGHT1             = "A1214";
    final public static String I_COPYRIGHT2             = "A1215";
     */

    final public static String I_OTHER_ITEM		= "A1216";
    final public static String I_FOR_EXAMPLE		= "A1217";
    final public static String I_ERROR_CODE    		= "A1218";
    final public static String I_MEGABYTES    		= "A1219";
    final public static String I_KILOBYTES    		= "A1220";
    final public static String I_BYTES    		= "A1221";
    final public static String I_NO_HELP    		= "A1222";
    final public static String I_HELP_TEXT    		= "A1223";
    final public static String I_QUIT_ACCELERATOR 	= "A1224";
    final public static String I_ADD_ACCELERATOR 	= "A1225";
    final public static String I_MILLISECONDS 		= "A1226";
    final public static String I_SECONDS 		= "A1227";
    final public static String I_MINUTES 		= "A1228";
    final public static String I_HOURS	 		= "A1229";
    final public static String I_DAYS	 		= "A1230";
    final public static String I_INFORMATION_CODE 	= "A1231";

    final public static String I_XAQCF                  = "A1232";
    final public static String I_XATCF                  = "A1233";
    final public static String I_XACF                   = "A1234";
    final public static String I_CF                     = "A1235";

    /* 
     * End of some Dialog labels shared by both obj store and broker.
     */

    /* 
     * Start of Object Store Dialog labels
     */
    final public static String I_OBJSTORE_DEST_PROPS    = "A1300";
    final public static String I_OBJSTORE_CF_PROPS      = "A1301";
    final public static String I_OBJSTORE_LOOKUP_NAME   = "A1302";
    final public static String I_OBJSTORE_FACTORY_TYPE  = "A1303";
    final public static String I_OBJSTORE_JNDI_INFO1    = "A1304";
    final public static String I_OBJSTORE_JNDI_INFO2    = "A1305";
    final public static String I_OBJSTORE_JNDI_INFO3    = "A1306";
    final public static String I_OBJSTORE_DEST_TYPE     = "A1307";
    final public static String I_OBJSTORE_DEST_NAME     = "A1308";
    final public static String I_OBJSTORE_PROPS         = "A1309";
    final public static String I_OBJSTORE_NAME          = "A1310";
    final public static String I_OBJSTORE_PROVIDER_URL  = "A1311";
    final public static String I_OBJSTORE_JNDI_PROPS    = "A1312";
    final public static String I_OBJSTORE_CONN_STATUS   = "A1313";
    final public static String I_OBJSTORE_LABEL 	= "A1314";
    final public static String I_READONLY       	= "A1315";
    /* 
     * End of Object Store Dialog labels
     */

    /* 
     * Start of some Broker labels
     */
    final public static String I_BROKER_NAME      	= "A1401";
    final public static String I_BROKER_USE_HOST_PORT   = "A1402";
    final public static String I_BROKER_HOST   		= "A1403";
    final public static String I_BROKER_PORT   		= "A1404";
    final public static String I_BROKER_USERNAME	= "A1405";
    final public static String I_BROKER_PASSWD		= "A1406";
    final public static String I_SAVE_USERNAME_PASSWD	= "A1407";
    final public static String I_BROKER_DEST_PROPS      = "A1408";
    final public static String I_BROKER_SVC_PROPS       = "A1409";
    /* 
     * End of some Broker labels
     */

    /* 
     * Start of Broker labels: Broker Props Dialog
     * Note: I_BROKER_PORT already exists
     */
    final public static String I_BROKER_PROPS       			= "A1410";
    final public static String I_BROKER_INSTANCE_NAME			= "A1411";
    final public static String I_BROKER_ACREATE_TOPICS			= "A1412";
    final public static String I_BROKER_ACREATE_QUEUES			= "A1413";
    final public static String I_BROKER_LOG_LEVEL			= "A1414";
    final public static String I_BROKER_LOG_ROLLOVER_SIZE		= "A1415";
    final public static String I_BROKER_LOG_ROLLOVER_INTERVAL		= "A1416";
    final public static String I_BROKER_METRIC_INTERVAL			= "A1417";
    final public static String I_BROKER_MAX_MSGS_IN_MEM_DSK		= "A1420";
    final public static String I_BROKER_MAX_TTL_SIZE_MSGS_IN_MEM_DSK	= "A1421";
    final public static String I_BROKER_MAX_MSG_SIZE			= "A1422";
    final public static String I_BROKER_LABEL         			= "A1423";
    /* 
     * End of Broker labels: Broker Props Dialog
     */

    /* 
     * Start of Broker labels: Add Broker Destination Dialog
     */
    final public static String I_BROKER_DEST_NAME		= "A1424";
    final public static String I_BROKER_DEST_TYPE		= "A1425";
    final public static String I_BROKER_MSG_DELIVERY_MODEL	= "A1426";
    final public static String I_BROKER_SINGLE			= "A1427";
    final public static String I_BROKER_ROUNDROBIN		= "A1428";
    final public static String I_BROKER_FAILOVER		= "A1429";
    final public static String I_BROKER_MAX_TTL_SIZE_MSGS	= "A1430";
    final public static String I_BROKER_MAX_NUM_MSGS		= "A1431";
    final public static String I_BROKER_MAX_SIZE_PER_MSG	= "A1432";
    final public static String I_BROKER_UNLIMITED		= "A1433";
    /* 
     * End of Broker labels: Add Broker Destination Dialog
     */

    /* 
     * Start of Broker labels: Broker Properties Dialog
     */
    final public static String I_BROKER_DEST_NUM_CONSUMERS	= "A1434";
    final public static String I_BROKER_DEST_NUM_MSGS		= "A1435";
    final public static String I_BROKER_DEST_TTL_SIZE_MSGS	= "A1436";
    final public static String I_BROKER_UNLIMITED_WITH_ARG	= "A1437";
    /* 
     * End of Broker labels: Broker Properties Dialog
     */

    /* 
     * Start of Broker labels: About iMQ Admin Console Dialog
     */
    final public static String I_JAVA_VERSION		= "A1438";
    final public static String I_JAVA_CLASSPATH		= "A1439";
    final public static String I_VERSION    		= "A1440";
    final public static String I_COMPILE    		= "A1441";
    final public static String I_RIGHTS     		= "A1442";
    final public static String I_VERSION_INFO		= "A1443";
    final public static String I_IMPLEMENTATION		= "A1444";
    final public static String I_PROTOCOL_VERSION	= "A1445";
    final public static String I_TARGET_JMS_VERSION    	= "A1446";
    final public static String I_RSA_CREDIT	    	= "A1447";
    final public static String I_PATCHES   	    	= "A1448";
    /*
    final public static String I_SHORT_COPYRIGHT	= "A1449";
    */

    final public static String BLOCK_OFF		= "A1450";
    /* 
     * End of Broker labels: About iMQ Admin Console Dialog
     */

    /* 
     * Start of Broker labels: Service Properties Admin Console Dialog
     */
    final public static String I_DYNAMIC_CAP        	= "A1451";
    final public static String I_STATIC_CAP        	= "A1452";
    /* 
     * End of Broker labels: Service Properties Admin Console Dialog
     */

    /*
     * Start of Misc labels in dialogs
     */
    final public static String I_BROKER_OFF		= "A1453";
    final public static String I_BROKER_TAB_BASIC	= "A1454";
    final public static String I_BROKER_TAB_LOGS	= "A1455";
    final public static String I_BROKER_TAB_MSG_CAPACITY= "A1456";
    /*
     * End of Misc labels in dialogs
     */

    /* 
     * Start of Broker labels: Broker Props Dialog (part 2)
     */
    final public static String I_BROKER_VERSION_STR	= "A1457";
    final public static String I_BROKER_VERSION_NOT_AVAIL	= "A1458";
    final public static String I_BROKER_ACTIVE_CONSUMER   = "A1459";
    final public static String I_BROKER_FAILOVER_CONSUMER = "A1460";
    final public static String I_BROKER_AUTOCREATED_ACTIVE_CONSUMER = "A1461";
    final public static String I_BROKER_AUTOCREATED_FAILOVER_CONSUMER = "A1462";
    final public static String I_BROKER_DEST_STATE = "A1463";
    // "Current Number of Active Consumers" - for queues
    final public static String I_BROKER_CUR_NUM_ACTIVE = "A1464";
    final public static String I_BROKER_CUR_NUM_FAILOVER = "A1465";
    final public static String I_BROKER_MAX_PRODUCERS = "A1466";
    final public static String I_BROKER_DEST_NUM_PRODUCERS = "A1467";
    // "Current Number of Consumers" - for topics
    final public static String I_BROKER_CUR_NUM_CONSUMERS = "A1468";

    // Limit Behavior - on dest props dialog
    final public static String I_BROKER_LIMIT_BEHAVIOR		= "A1469";
    // Use Dead Message Queue - on dest props dialog
    final public static String I_BROKER_USE_DMQ			= "A1470";
    /* 
     * End of Broker labels: Broker Props Dialog (part 2)
     */


    /*
     * Start of additional error dialog titles
     */
    final public static String I_REFRESH_SVCLIST        = "A1500";
    final public static String I_REFRESH_DESTLIST       = "A1501";
    final public static String I_BROKER_UPDATE       	= "A1502";
    /*
     * End of additional error dialog titles
     */

    /*
     * Start of tab titles
     */
    final public static String I_DEST_PROP_BASIC       	= "A1503";
    final public static String I_DEST_PROP_SUB       	= "A1504";
    /*
     * End of tab titles
     */

    final public static String I_MENU_QUERY_BROKER      = "A1505";
    final public static String I_QUERY_BROKER_MNEMONIC	= "A1506";

    final public static String I_BROKER_ALT_SHUTDOWN	= "A1507";

    /*
     * Cmdline parsing, usage help
     */
    final public static String I_USAGE_HELP		= "A1508";
    final public static String I_ARG_EXPECTED		= "A1509";
    final public static String I_UNRECOGNIZED_OPT	= "A1510";

    final public static String I_STATUS_RECV		= "A1511";
    final public static String I_UNKNOWN_STATUS		= "A1512";
    final public static String I_BUSY_WAIT_FOR_REPLY	= "A1513";
    final public static String I_DELETE_DURABLE		= "A1514";
    final public static String I_PURGE_DURABLE		= "A1515";
    final public static String I_WARNING_CODE           = "A1516";

    /*
     * Informational strings related to loading of 
     * broker/objstore list property files at startup.
     */
    final public static String I_LOAD_BKR_LIST		= "A1520";
    final public static String I_LOAD_OBJSTORE_LIST	= "A1521";

    /*
     * Title for online help initialization error dialog
     */
    final public static String I_ONLINE_HELP_INIT	= "A1522";

    // 2000-2999 Warning Messages
    final public static String W_SAVE_AS_CLEAR_TEXT	= "A2000";
    final public static String W_OS_NOT_EDITABLE_TEXT	= "A2001";
    final public static String W_BKR_NOT_EDITABLE_TEXT	= "A2002";
    final public static String W_INCOMPATIBLE_OBJ       = "A2003";
    final public static String W_PROVIDER_URL           = "A2004";

    // 3000-3999 Error Messages
    final public static String E_NO_LOOKUP_NAME		= "A3000";
    final public static String E_NO_PROP_VALUE		= "A3001";
    final public static String E_OBJSTORE_NAME_IN_USE	= "A3002";
    final public static String E_PROP_VALUE_EXISTS   	= "A3003";
    final public static String E_NO_OBJSTORE_NAME    	= "A3004";
    final public static String E_NO_PROVIDER_URL     	= "A3005";
    final public static String E_OBJSTORE_NOT_CONNECTED = "A3006";
    final public static String E_OBJSTORE_LIST        	= "A3007";
    final public static String E_INSUFFICIENT_INFO      = "A3008";
    final public static String E_OS_ALREADY_CONNECTED   = "A3009";
    final public static String E_OS_ALREADY_DISCONNECTED = "A3010";
    final public static String E_OS_UNABLE_CONNECT      = "A3011";
    final public static String E_OS_UNABLE_DISCONNECT   = "A3012";
    final public static String E_DELETE_DEST_OBJ        = "A3013";
    final public static String E_LOAD_OBJSTORE_LIST     = "A3014";
    final public static String E_SAVE_OBJSTORE_LIST     = "A3015";
    final public static String E_INVALID_VALUE 		= "A3016";
    final public static String E_DELETE_CF_OBJ          = "A3017";
    final public static String E_NO_BROKER_NAME         = "A3018";
    final public static String E_NO_BROKER_HOST_PORT    = "A3019";
    final public static String E_NO_BROKER_DEST_NAME    = "A3020";
    final public static String E_NO_JNDI_PROPERTY_VALUE = "A3021";
    final public static String E_CANNOT_INSTANTIATE     = "A3022";
    final public static String E_OS_PROCESS             = "A3023";
    final public static String E_PASSWORD	        = "A3024";
    final public static String E_BROKER_EXISTS		= "A3025";
    final public static String E_RECONNECT              = "A3026";
    final public static String E_SERVICE_PAUSE 	        = "A3027";
    final public static String E_SERVICE_RESUME 	= "A3028";
    final public static String E_BROKER_PAUSE   	= "A3029";
    final public static String E_BROKER_RESUME  	= "A3030";
    final public static String E_BROKER_SHUTDOWN 	= "A3031";
    final public static String E_REFRESH_SVCLIST        = "A3032";
    final public static String E_REFRESH_DESTLIST       = "A3033";
    final public static String E_RETRIEVE_SVC           = "A3034";
    final public static String E_RETRIEVE_DEST          = "A3035";
    final public static String E_RETRIEVE_DUR           = "A3036";
    final public static String E_RETRIEVE_OBJECT        = "A3037";
    final public static String E_INVALID_PORT        	= "A3038";
    final public static String E_INVALID_HOSTNAME      	= "A3039";
    final public static String E_INVALID_LOGIN      	= "A3040";
    final public static String E_LOGIN_FORBIDDEN      	= "A3041";
    final public static String E_INVALID_PROP_NAME 	= "A3042";
    final public static String E_INVALID_PROP_VALUE 	= "A3043";
    final public static String E_NO_STATIC_PORT 	= "A3044";
    final public static String E_BROKER_CONNECT         = "A3045";
    final public static String E_BROKER_NOT_CONNECTED   = "A3046";
    final public static String E_BROKER_ERR_SHUTDOWN    = "A3047";
    final public static String E_BROKER_CONN_ERROR      = "A3048";
    final public static String E_BROKER_NO_MORE_DEST	= "A3049";
    final public static String E_UNKNOWN_ERROR		= "A3050";
    final public static String E_BROKER_ADD_BROKER      = "A3051";
    final public static String E_BROKER_QUERY      	= "A3052";
    final public static String E_SERVICE_QUERY      	= "A3053";
    final public static String E_ALL_SERVICES_QUERY   	= "A3054";
    final public static String E_DEST_QUERY   		= "A3055";
    final public static String E_LOAD_BKR_LIST   	= "A3056";
    final public static String E_SAVE_BKR_LIST   	= "A3057";
    final public static String E_BROKER_DEST_DELETE	= "A3058";
    final public static String E_REPLY_NOT_RECEIVED	= "A3059";
    final public static String E_BROKER_DEST_PURGE	= "A3060";
    final public static String E_BROKER_DESTROY_DUR	= "A3061";
    final public static String E_BAD_RECV_TIMEOUT_VAL	= "A3062";
    final public static String E_CONNECT_BROKER		= "A3063";
    final public static String E_UPDATE_BROKER		= "A3064";
    final public static String E_UPDATE_SERVICE		= "A3065";
    final public static String E_RECONNECT_BROKER	= "A3066";
    final public static String E_SHUTDOWN_BROKER	= "A3067";
    final public static String E_RESTART_BROKER		= "A3068";
    final public static String E_ADD_DEST_BROKER	= "A3069";
    final public static String E_DISCONNECT_BROKER_NOT_POSSIBLE	= "A3070";
    final public static String E_ADMIN_MAX_THREAD       = "A3071";
    final public static String E_UPDATE_DEST            = "A3072";
    final public static String E_NO_REPLY_GIVEUP        = "A3073";
    final public static String E_BROKER_PURGE_DUR	= "A3074";
    final public static String E_BAD_NUM_RETRIES_VAL	= "A3075";
    final public static String E_DEST_PAUSE 	        = "A3076";
    final public static String E_DEST_RESUME 	        = "A3077";
    final public static String E_DEST_ALL_PAUSE 	= "A3078";
    final public static String E_DEST_ALL_RESUME        = "A3079";

    /*
     * Error strings related to loading of broker/objstore 
     * list property files at startup.
     */
    final public static String E_BAD_INT_BKR_LIST_VER		= "A3080";
    final public static String E_BAD_FILE_BKR_LIST_VER		= "A3081";
    final public static String E_BAD_INT_OBJSTORE_LIST_VER	= "A3082";
    final public static String E_BAD_FILE_OBJSTORE_LIST_VER	= "A3083";

    /*
     * Error string displayed when online help initialization fails
     */
    final public static String E_ONLINE_HELP_INIT_FAILED	= "A3084";

    // 4000-4999 Exception Messages
    final public static String X_SAMPLE_EXCEPTION	= "A4000";

    // 5000-5999 Question Messages
    final public static String Q_OBJSTORE_DELETE	= "A5000";
    final public static String Q_DEST_OBJ_DELETE	= "A5001";
    final public static String Q_LOOKUP_NAME_EXISTS	= "A5002";
    final public static String Q_CF_OBJ_DELETE		= "A5003";
    final public static String Q_BROKER_DELETE 	        = "A5004";
    final public static String Q_SERVICE_PAUSE 	        = "A5005";
    final public static String Q_SERVICE_RESUME	        = "A5006";
    final public static String Q_BROKER_RESUME	        = "A5007";
    final public static String Q_BROKER_SHUTDOWN 	= "A5008";
    final public static String Q_BROKER_RESTART	        = "A5009";
    final public static String Q_BROKER_DELETE_DEST     = "A5010";
    final public static String Q_BROKER_PAUSE           = "A5011";
    final public static String Q_BROKER_PURGE_DEST      = "A5012";
    final public static String Q_BROKER_DELETE_DUR      = "A5013";
    final public static String Q_SET_MAX_THREAD_ZERO    = "A5014";
    final public static String Q_BROKER_PURGE_DUR       = "A5015";
    final public static String Q_DEST_PAUSE       	= "A5016";
    final public static String Q_DEST_RESUME       	= "A5017";
    final public static String Q_DEST_PAUSE_ALL       	= "A5018";
    final public static String Q_DEST_RESUME_ALL       	= "A5019";

    // 6000-6999 Status Area Messages
    final public static String S_OBJSTORE_ADD 		= "A6000";
    final public static String S_OBJSTORE_UPDATE 	= "A6001";
    final public static String S_OBJSTORE_CONNECT	= "A6002";
    final public static String S_OBJSTORE_DISCONNECT	= "A6003";
    final public static String S_OBJSTORE_DELETE	= "A6004";
    final public static String S_OBJSTORE_DELETE_DEST	= "A6005";
    final public static String S_OBJSTORE_UPDATE_DEST	= "A6006";
    final public static String S_OBJSTORE_ADD_DEST	= "A6007";
    final public static String S_OBJSTORE_ADD_CF  	= "A6008";
    final public static String S_OBJSTORE_UPDATE_CF	= "A6009";
    final public static String S_OBJSTORE_DELETE_CF  	= "A6010";
    final public static String S_BROKER_REFRESH_SVCLIST = "A6011";
    final public static String S_BROKER_REFRESH_DESTLIST= "A6012";
    final public static String S_SERVICE_PAUSE          = "A6013";
    final public static String S_SERVICE_RESUME         = "A6014";
    final public static String S_BROKER_PAUSE           = "A6015";
    final public static String S_BROKER_RESUME          = "A6016";
    final public static String S_BROKER_SHUTDOWN 	= "A6017";
    final public static String S_BROKER_RESTART 	= "A6018";
    final public static String S_BROKER_DEST_ADD	= "A6019";
    final public static String S_BROKER_DEST_DELETE     = "A6020";
    final public static String S_BROKER_DEST_PURGE      = "A6021";
    final public static String S_BROKER_CONNECT	        = "A6022";
    final public static String S_BROKER_DISCONNECT	= "A6023";
    final public static String S_BROKER_UPDATE		= "A6024";
    final public static String S_BROKER_DESTROY_DUR	= "A6025";
    final public static String S_BROKER_UPDATE_SVC	= "A6026";
    final public static String S_BROKER_UPDATE_DEST     = "A6027";
    final public static String S_OS_REFRESH             = "A6028";
    final public static String S_OS_DESTLIST_REFRESH    = "A6029";
    final public static String S_OS_CFLIST_REFRESH      = "A6030";
    final public static String S_OS_DEST_REFRESH        = "A6031";
    final public static String S_OS_CF_REFRESH          = "A6032";
    final public static String S_BROKER_ENTRY_UPDATE    = "A6033";
    final public static String S_BROKER_REFRESH    	= "A6034";
    final public static String S_BROKER_PURGE_DUR	= "A6035";
    final public static String S_DEST_PAUSE          	= "A6036";
    final public static String S_DEST_RESUME          	= "A6037";
    final public static String S_DEST_ALL_PAUSE        	= "A6038";
    final public static String S_DEST_ALL_RESUME       	= "A6039";

    /***************** End of message key constants *******************/

}
