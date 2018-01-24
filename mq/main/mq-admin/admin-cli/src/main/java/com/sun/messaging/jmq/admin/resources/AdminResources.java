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
 * @(#)AdminResources.java	1.155 06/28/07
 */ 

package com.sun.messaging.jmq.admin.resources;

import java.util.ResourceBundle;
import java.util.Locale;
import com.sun.messaging.jmq.util.MQResourceBundle;

/**
 * This class wraps a PropertyResourceBundle, and provides constants
 * to use as message keys. The reason we use constants for the message
 * keys is to provide some compile time checking when the key is used
 * in the source.
 */

public class AdminResources extends MQResourceBundle {

    private static AdminResources resources = null;

    public static AdminResources getResources() {
        return getResources(null);
    }

    public static AdminResources getResources(Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }

        if (resources == null || !locale.equals(resources.getLocale())) {
            ResourceBundle prb =
                ResourceBundle.getBundle(
                "com.sun.messaging.jmq.admin.resources.AdminResources",
                locale);
            resources = new AdminResources(prb);
        }

	return resources;
    }

    private AdminResources(ResourceBundle rb) {
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
     * Note to JMQ engineers: Remove the sample entries e.g. I_SAMPLE_MESSAGE
     * when you add entries for that category.
     */

    // 0-999     Miscellaneous messages
    final public static String M_SAMPLE_MESSAGE		= "A0000";

    // 1000-1999 Informational Messages
    final public static String I_WARNING_MESG 		= "A1000";
    final public static String I_ERROR_MESG 		= "A1001";
    final public static String I_INTERNAL_ERROR_MESG 	= "A1002";
    final public static String I_D_FAILED_MESG 		= "A1003";
    final public static String I_Q_FAILED_MESG 		= "A1004";
    final public static String I_U_FAILED_MESG 		= "A1005";

    final public static String I_OBJ_ADDED 		= "A1020";
    final public static String I_OBJ_DELETED 		= "A1021";
    final public static String I_OBJ_UPDATED 		= "A1022";
    final public static String I_OBJ_NOT_ADDED 		= "A1023";
    final public static String I_OBJ_NOT_DELETED	= "A1024";
    final public static String I_OBJ_NOT_UPDATED	= "A1025";
    final public static String I_UNRECOGNIZED_RES	= "A1026";
    final public static String I_OBJ_ADD_FAILED		= "A1027";
    final public static String I_OBJ_DELETE_FAILED	= "A1028";
    final public static String I_OBJ_QUERIED		= "A1029";
    final public static String I_OBJ_QUERY_FAILED	= "A1030";
    final public static String I_OBJ_UPDATE_FAILED	= "A1031";
    final public static String I_OBJ_LISTED		= "A1032";
    final public static String I_OBJ_LIST_FAILED	= "A1033";
    final public static String I_OBJ_NOT_QUERIED 	= "A1034";
    final public static String I_OBJ_NOT_LISTED  	= "A1035";
    final public static String I_OBJ_PREV_UPDATE_FAILED = "A1036";
    final public static String I_OBJ_PREV_ADD_FAILED    = "A1037";

    final public static String I_LIST_TOPIC		= "A1040";
    final public static String I_LIST_QUEUE		= "A1041";
    final public static String I_LIST_TCF		= "A1042";
    final public static String I_LIST_QCF		= "A1043";
    final public static String I_JNDI_LOOKUPNAME	= "A1044";
    final public static String I_OBJ_CLASS_NAME		= "A1045";
    final public static String I_TOPIC_ATTRS_HDR	= "A1046";
    final public static String I_QUEUE_ATTRS_HDR	= "A1047";
    final public static String I_TOPIC_CF_ATTRS_HDR	= "A1048";
    final public static String I_QUEUE_CF_ATTRS_HDR	= "A1049";

    final public static String I_OBJMGR_HELP_USAGE		= "A1050";
    final public static String I_OBJMGR_HELP_SUBCOMMANDS	= "A1051";
    final public static String I_OBJMGR_HELP_OPTIONS    	= "A1052";
    final public static String I_OBJMGR_HELP_ATTRIBUTES1   	= "A1054";
    final public static String I_OBJMGR_HELP_ATTRIBUTES2   	= "A1055";
    final public static String I_OBJMGR_HELP_EXAMPLES1     	= "A1056";
    final public static String I_OBJMGR_HELP_EXAMPLES2     	= "A1057";
    final public static String I_OBJMGR_HELP_EXAMPLES3     	= "A1058";
    final public static String I_OBJMGR_HELP_EXAMPLES4     	= "A1059";
    final public static String I_OBJMGR_HELP_EXAMPLES5     	= "A1060";
    final public static String I_OBJMGR_HELP_EXAMPLES6     	= "A1061";
    final public static String I_OBJMGR_HELP_EXAMPLES7     	= "A1062";
    final public static String I_OBJMGR_HELP_EXAMPLES8     	= "A1063";
    final public static String I_OBJMGR_HELP_EXAMPLES9     	= "A1064";
    final public static String I_JAVA_VERSION     		= "A1065";
    final public static String I_JAVA_CLASSPATH     		= "A1066";
    final public static String I_PREVIEW_ON			= "A1067";
    final public static String I_PROMPT_ON			= "A1068";
    final public static String I_PROMPT_OFF			= "A1069";
    final public static String I_PREVIEW_ADD			= "A1070";
    final public static String I_PREVIEW_DELETE			= "A1071";
    final public static String I_PREVIEW_QUERY 			= "A1072";
    final public static String I_PREVIEW_LIST_TYPE 		= "A1073";
    final public static String I_PREVIEW_LIST      		= "A1074";
    final public static String I_PREVIEW_UPDATE_TYPE  		= "A1075";
    final public static String I_PREVIEW_UPDATE  		= "A1076";
    final public static String I_WITH_LOOKUP_NAME		= "A1077";
    final public static String I_XATOPIC_CF_ATTRS_HDR		= "A1078";
    final public static String I_XAQUEUE_CF_ATTRS_HDR		= "A1079";
    final public static String I_XA_CF_ATTRS_HDR		= "A1080";
    final public static String I_CF_ATTRS_HDR			= "A1081";

    /*
     * These strings are used when imqobjmgr displays
     * output informing the user what is about to
     * be done. For each command (e.g. 'add'), the
     * strings that are displayed can roughly be
     * categorized into:
     *
     *	intro e.g.
     *	"Adding a Topic Connection Factory object 
     *	with the following attributes:"
     *
     *	lookup name e.g.
     *	"Using the the following lookup name:"
     *
     *	objstore e.g.
     *	"To the object store specified by:"
     */
    final public static String I_ADD_CMD_DESC_INTRO    	= "A1100";
    final public static String I_ADD_CMD_DESC_LOOKUP   	= "A1101";
    final public static String I_ADD_CMD_DESC_STORE   	= "A1102";
    final public static String I_DELETE_CMD_DESC_INTRO  = "A1103";
    final public static String I_DELETE_CMD_DESC_STORE  = "A1104";
    final public static String I_QUERY_CMD_DESC_INTRO	= "A1105";
    final public static String I_QUERY_CMD_DESC_STORE	= "A1106";
    final public static String I_LIST_CMD_DESC_INTRO	= "A1107";
    final public static String I_LIST_CMD_DESC_INTRO_TYPE	= "A1108";
    final public static String I_UPDATE_CMD_DESC_INTRO	= "A1109";
    final public static String I_UPDATE_CMD_DESC_INTRO_TYPE	= "A1110";
    final public static String I_UPDATE_CMD_DESC_LOOKUP	= "A1111";
    final public static String I_UPDATE_CMD_DESC_STORE	= "A1112";
    final public static String I_READONLY             	= "A1113";

    /*
     * Messages printed out for imqcmd usage help
     */
    final public static String I_BROKERCMD_HELP_USAGE		= "A1150";
    final public static String I_BROKERCMD_HELP_SUBCOMMANDS	= "A1151";
    final public static String I_BROKERCMD_HELP_OPTIONS    	= "A1152";
    final public static String I_BROKERCMD_HELP_ATTRIBUTES1   	= "A1153";
    final public static String I_BROKERCMD_HELP_ATTRIBUTES2   	= "A1154";
    final public static String I_BROKERCMD_HELP_ATTRIBUTES3   	= "A1155";
    final public static String I_BROKERCMD_HELP_EXAMPLES1     	= "A1156";
    final public static String I_BROKERCMD_HELP_EXAMPLES2     	= "A1157";
    final public static String I_BROKERCMD_HELP_EXAMPLES3     	= "A1158";
    final public static String I_BROKERCMD_HELP_EXAMPLES4     	= "A1159";
    final public static String I_BROKERCMD_HELP_EXAMPLES5     	= "A1160";
    final public static String I_BROKERCMD_HELP_EXAMPLES6     	= "A1161";
    final public static String I_BROKERCMD_HELP_EXAMPLES7     	= "A1162";
    final public static String I_BROKERCMD_HELP_ATTRIBUTES4    	= "A1163";
    final public static String I_BROKERCMD_HELP_ATTRIBUTES5    	= "A1164";
    final public static String I_BROKERCMD_HELP_EXAMPLES8     	= "A1165";
    final public static String I_BROKERCMD_HELP_EXAMPLES9     	= "A1166";
    final public static String I_BROKERCMD_HELP_EXAMPLES10     	= "A1167";
    final public static String I_BROKERCMD_HELP_EXAMPLES11     	= "A1168";
    final public static String I_BROKERCMD_HELP_EXAMPLES12     	= "A1169";
    final public static String I_BROKERCMD_VALID_VALUES     	= "A1170";
    final public static String I_BROKERCMD_HELP_EXAMPLES13     	= "A1171";
    final public static String I_BROKERCMD_HELP_EXAMPLES14     	= "A1172";
    final public static String I_BROKERCMD_HELP_EXAMPLES15     	= "A1173";
    final public static String I_BROKERCMD_HELP_ATTR_CREATE_ONLY= "A1174";
    final public static String I_BROKERCMD_HELP_DEST_UNLIMITED	= "A1175";
    final public static String I_BROKERCMD_HELP_BKR_UNLIMITED	= "A1176";

    /*
     * These strings are used when imqcmd displays
     * output informing the user what is about to
     * be done. For each command (e.g. 'create'), the
     * strings that are displayed can roughly be
     * categorized into:
     *
     *	intro
     *
     *	broker info
     *  target info
     *
     *  result
     */
    final public static String I_JMQCMD_SPECIFY_BKR    	= "A1200";

    final public static String I_JMQCMD_LIST_DST     	= "A1201";
    final public static String I_JMQCMD_LIST_DST_SUC  	= "A1202";
    final public static String I_JMQCMD_LIST_DST_FAIL 	= "A1203";

    final public static String I_JMQCMD_LIST_SVC     	= "A1204";
    final public static String I_JMQCMD_LIST_SVC_SUC  	= "A1205";
    final public static String I_JMQCMD_LIST_SVC_FAIL 	= "A1206";

    final public static String I_JMQCMD_PAUSE_BKR     	= "A1207";
    final public static String I_JMQCMD_PAUSE_BKR_SUC  	= "A1208";
    final public static String I_JMQCMD_PAUSE_BKR_FAIL 	= "A1209";
    final public static String I_JMQCMD_PAUSE_BKR_NOOP 	= "A1210";

    final public static String I_JMQCMD_PAUSE_SVC     	= "A1211";
    final public static String I_JMQCMD_PAUSE_SVC_SUC  	= "A1212";
    final public static String I_JMQCMD_PAUSE_SVC_FAIL 	= "A1213";
    final public static String I_JMQCMD_PAUSE_SVC_NOOP 	= "A1214";

    final public static String I_JMQCMD_RESUME_BKR     	= "A1215";
    final public static String I_JMQCMD_RESUME_BKR_SUC  = "A1216";
    final public static String I_JMQCMD_RESUME_BKR_FAIL = "A1217";
    final public static String I_JMQCMD_RESUME_BKR_NOOP = "A1218";

    final public static String I_JMQCMD_RESUME_SVC     	= "A1219";
    final public static String I_JMQCMD_RESUME_SVC_SUC  = "A1220";
    final public static String I_JMQCMD_RESUME_SVC_FAIL = "A1221";
    final public static String I_JMQCMD_RESUME_SVC_NOOP = "A1222";

    final public static String I_JMQCMD_SHUTDOWN_BKR   		= "A1223";
    final public static String I_JMQCMD_SHUTDOWN_BKR_SUC	= "A1224";
    final public static String I_JMQCMD_SHUTDOWN_BKR_FAIL 	= "A1225";
    final public static String I_JMQCMD_SHUTDOWN_BKR_NOOP 	= "A1226";

    final public static String I_JMQCMD_RESTART_BKR   		= "A1227";
    final public static String I_JMQCMD_RESTART_BKR_SUC		= "A1228";
    final public static String I_JMQCMD_RESTART_BKR_FAIL	= "A1229";
    final public static String I_JMQCMD_RESTART_BKR_NOOP	= "A1230";

    final public static String I_JMQCMD_CREATE_DST   		= "A1231";
    final public static String I_JMQCMD_CREATE_DST_SUC		= "A1232";
    final public static String I_JMQCMD_CREATE_DST_FAIL		= "A1233";

    final public static String I_JMQCMD_DESTROY_DST   		= "A1234";
    final public static String I_JMQCMD_DESTROY_DST_SUC		= "A1235";
    final public static String I_JMQCMD_DESTROY_DST_FAIL	= "A1236";
    final public static String I_JMQCMD_DESTROY_DST_NOOP	= "A1237";

    final public static String I_JMQCMD_PURGE_DST   		= "A1238";
    final public static String I_JMQCMD_PURGE_DST_SUC		= "A1239";
    final public static String I_JMQCMD_PURGE_DST_FAIL		= "A1240";
    final public static String I_JMQCMD_PURGE_DST_NOOP		= "A1241";

    final public static String I_JMQCMD_QUERY_DST   		= "A1242";
    final public static String I_JMQCMD_QUERY_DST_SUC		= "A1243";
    final public static String I_JMQCMD_QUERY_DST_ERROR		= "A1244";
    final public static String I_JMQCMD_QUERY_DST_FAIL		= "A1245";

    final public static String I_JMQCMD_QUERY_SVC   		= "A1246";
    final public static String I_JMQCMD_QUERY_SVC_SUC		= "A1247";
    final public static String I_JMQCMD_QUERY_SVC_ERROR		= "A1248";
    final public static String I_JMQCMD_QUERY_SVC_FAIL		= "A1249";


    final public static String I_JMQCMD_LIST_TXN   		= "A1250";
    final public static String I_JMQCMD_LIST_TXN_SUC		= "A1251";
    final public static String I_JMQCMD_LIST_TXN_NONE		= "A1252";
    final public static String I_JMQCMD_LIST_TXN_FAIL		= "A1253";

    final public static String I_JMQCMD_QUERY_TXN   		= "A1254";
    final public static String I_JMQCMD_QUERY_TXN_SUC		= "A1255";
    final public static String I_JMQCMD_QUERY_TXN_FAIL		= "A1256";

    final public static String I_JMQCMD_COMMIT_TXN   		= "A1257";
    final public static String I_JMQCMD_COMMIT_TXN_SUC		= "A1258";
    final public static String I_JMQCMD_COMMIT_TXN_FAIL		= "A1259";

    final public static String I_JMQCMD_ROLLBACK_TXN   		= "A1260";
    final public static String I_JMQCMD_ROLLBACK_TXN_SUC	= "A1261";
    final public static String I_JMQCMD_ROLLBACK_TXN_FAIL	= "A1262";

    /*
     * Various attributes of a transaction.
     */
    final public static String I_JMQCMD_TXN_ID			= "A1263";
    final public static String I_JMQCMD_TXN_CLIENT_ID		= "A1264";
    final public static String I_JMQCMD_TXN_CONNECTION		= "A1265";
    final public static String I_JMQCMD_TXN_TIMESTAMP		= "A1266";
    final public static String I_JMQCMD_TXN_NUM_ACKS		= "A1267";
    final public static String I_JMQCMD_TXN_NUM_MSGS		= "A1268";
    final public static String I_JMQCMD_TXN_STATE		= "A1269";
    final public static String I_JMQCMD_TXN_USERNAME		= "A1270";
    final public static String I_JMQCMD_TXN_XID			= "A1271";
    // This is a combined field/attribute
    final public static String I_JMQCMD_TXN_NUM_MSGS_ACKS	= "A1272";

    final public static String I_JMQCMD_PAUSE_DST     		= "A1273";
    final public static String I_JMQCMD_PAUSE_DST_SUC  		= "A1274";
    final public static String I_JMQCMD_PAUSE_DST_FAIL 		= "A1275";
    final public static String I_JMQCMD_PAUSE_DST_NOOP 		= "A1276";

    /*
     * Additional attribute of transaction: connection ID
     */
    final public static String I_JMQCMD_TXN_CONNECTION_ID	= "A1277";

    /*
     * Strings displayed during commit/rollback
     */
    final public static String I_JMQCMD_ROLLBACK_TXN_NOOP	= "A1278";
    final public static String I_JMQCMD_COMMIT_TXN_NOOP		= "A1279";

    /*
     * Values for transaction state.
     */
    final public static String I_JMQCMD_TXN_STATE_CREATED	= "A1280";
    final public static String I_JMQCMD_TXN_STATE_STARTED	= "A1281";
    final public static String I_JMQCMD_TXN_STATE_FAILED	= "A1282";
    final public static String I_JMQCMD_TXN_STATE_INCOMPLETE	= "A1283";
    final public static String I_JMQCMD_TXN_STATE_COMPLETE	= "A1284";
    final public static String I_JMQCMD_TXN_STATE_PREPARED	= "A1285";
    final public static String I_JMQCMD_TXN_STATE_COMMITTED	= "A1286";
    final public static String I_JMQCMD_TXN_STATE_ROLLEDBACK	= "A1287";
    final public static String I_JMQCMD_TXN_STATE_UNKNOWN	= "A1288";

    /*
     * New strings for listing topic/queue destinations
     */
    final public static String I_JMQCMD_LIST_TOPIC_DST		= "A1290";
    final public static String I_JMQCMD_LIST_QUEUE_DST		= "A1291";

    final public static String I_JMQCMD_RESUME_DST     		= "A1292";
    final public static String I_JMQCMD_RESUME_DST_SUC  	= "A1293";
    final public static String I_JMQCMD_RESUME_DST_FAIL 	= "A1294";
    final public static String I_JMQCMD_RESUME_DST_NOOP 	= "A1295";

    final public static String I_JMQCMD_METRICS_DST_FAIL	= "A1296";
    final public static String I_JMQCMD_METRICS_DST		= "A1297";
    final public static String I_JMQCMD_METRICS_SVC_SUC		= "A1298";
    final public static String I_JMQCMD_METRICS_BKR_SUC		= "A1299";
    final public static String I_JMQCMD_METRICS_DST_SUC		= "A1300";

    /*
     * Strings for compacting destination(s)
     */
    final public static String I_JMQCMD_COMPACT_DST		= "A1301";
    final public static String I_JMQCMD_COMPACT_DSTS		= "A1302";
    final public static String I_JMQCMD_COMPACT_DST_SUC		= "A1303";
    final public static String I_JMQCMD_COMPACT_DSTS_SUC	= "A1304";
    final public static String I_JMQCMD_COMPACT_DST_FAIL	= "A1305";
    final public static String I_JMQCMD_COMPACT_DSTS_FAIL	= "A1306";
    final public static String I_JMQCMD_COMPACT_DST_NOOP	= "A1307";
    final public static String I_JMQCMD_COMPACT_DSTS_NOOP	= "A1308";

    /*
     * Strings for pausing destinations (plural)
     */
    final public static String I_JMQCMD_PAUSE_DSTS     		= "A1309";
    final public static String I_JMQCMD_PAUSE_DSTS_SUC  	= "A1310";
    final public static String I_JMQCMD_PAUSE_DSTS_FAIL 	= "A1311";
    final public static String I_JMQCMD_PAUSE_DSTS_NOOP 	= "A1312";

    /*
     * Strings for resuming destinations (plural)
     */
    final public static String I_JMQCMD_RESUME_DSTS     	= "A1313";
    final public static String I_JMQCMD_RESUME_DSTS_SUC  	= "A1314";
    final public static String I_JMQCMD_RESUME_DSTS_FAIL 	= "A1315";
    final public static String I_JMQCMD_RESUME_DSTS_NOOP 	= "A1316";

    /*
     * String for displaying a single attribute
     */
    final public static String I_JMQCMD_USING_ATTR 		= "A1317";

    /*
     * Strings for listing connections
     */
    final public static String I_JMQCMD_LIST_CXN	     	= "A1318";
    final public static String I_JMQCMD_LIST_CXN_FOR_SVC     	= "A1319";
    final public static String I_JMQCMD_LIST_CXN_SUC 	 	= "A1320";
    final public static String I_JMQCMD_LIST_CXN_FAIL 		= "A1321";
    final public static String I_JMQCMD_LIST_CXN_NONE 		= "A1322";

    /*
     * Connection property labels
     */
    final public static String I_JMQCMD_CXN_CXN_ID 		= "A1323";
    final public static String I_JMQCMD_CXN_CLIENT_ID 		= "A1324";
    final public static String I_JMQCMD_CXN_HOST 		= "A1325";
    final public static String I_JMQCMD_CXN_PORT 		= "A1326";
    final public static String I_JMQCMD_CXN_USER 		= "A1327";
    final public static String I_JMQCMD_CXN_NUM_PRODUCER	= "A1328";
    final public static String I_JMQCMD_CXN_NUM_CONSUMER	= "A1329";
    final public static String I_JMQCMD_CXN_CLIENT_PLATFORM	= "A1330";
    final public static String I_JMQCMD_CXN_SERVICE		= "A1331";

    /*
     * Strings for querying a connection
     */
    final public static String I_JMQCMD_QUERY_CXN		= "A1332";
    final public static String I_JMQCMD_QUERY_CXN_SUC		= "A1333";
    final public static String I_JMQCMD_QUERY_CXN_FAIL		= "A1334";

    /*
     * General strings to indicate incorrect/bad data returned
     * from broker.
     */
    final public static String I_JMQCMD_INCORRECT_DATA_RET	= "A1335";

    /*
     * ID for this string:
     * Broker not responding, retrying [1 of 5 attempts, timeout=20 seconds]
     */
    final public static String I_JMQCMD_BROKER_BUSY		= "A1350";

    final public static String I_JMQCMD_DST_NAME		= "A1400";
    final public static String I_JMQCMD_DST_TYPE		= "A1401";
    final public static String I_JMQCMD_DST_FLAVOR		= "A1402";
    final public static String I_JMQCMD_DST_CUR_CON		= "A1403";
    final public static String I_JMQCMD_DST_CUR_MSG		= "A1404";
    final public static String I_JMQCMD_DST_CUR_MSG_BYTES	= "A1405";
    final public static String I_JMQCMD_DST_MAX_MSG_BYTES_ALLOW	= "A1406";
    final public static String I_JMQCMD_DST_MAX_MSG_ALLOW	= "A1407";
    final public static String I_JMQCMD_DST_MAX_BYTES_PER_MSG_ALLOW
								= "A1408";

    final public static String I_JMQCMD_DST_MAX_FAILOVER_CONSUMER_COUNT	
								= "A1409";
    final public static String I_JMQCMD_DST_MAX_ACTIVE_CONSUMER_COUNT	
								= "A1410";
    final public static String I_JMQCMD_DST_IS_LOCAL_DEST	= "A1411";
    final public static String I_JMQCMD_DST_LIMIT_BEHAVIOUR	= "A1412";
    final public static String I_JMQCMD_DST_LOCAL_DELIVERY_PREF	= "A1413";
    final public static String I_JMQCMD_DST_CONS_FLOW_LIMIT	= "A1414";
    final public static String I_JMQCMD_DST_MAX_PRODUCERS	= "A1415";
    final public static String I_JMQCMD_DST_CUR_ACTIVE_CONS	= "A1416";
    final public static String I_JMQCMD_DST_CUR_FAILOVER_CONS	= "A1417";
    final public static String I_JMQCMD_DST_MAX_FAILOVER_CONSUMER_COUNT_SHORT
								= "A1418";
    final public static String I_JMQCMD_DST_MAX_ACTIVE_CONSUMER_COUNT_SHORT
								= "A1419";

    final public static String I_JMQCMD_SVC_NAME		= "A1420";
    final public static String I_JMQCMD_SVC_PORT		= "A1421";
    final public static String I_JMQCMD_SVC_STATE 		= "A1422";
    final public static String I_JMQCMD_SVC_MIN_THREADS		= "A1423";
    final public static String I_JMQCMD_SVC_MAX_THREADS		= "A1424";
    final public static String I_JMQCMD_SVC_CUR_THREADS		= "A1425";
    final public static String I_JMQCMD_SVC_NUM_CXN		= "A1426";

    final public static String I_JMQCMD_DST_CREATED_ADMIN	= "A1427";
    final public static String I_JMQCMD_DST_CUR_PRODUCERS	= "A1428";
    final public static String I_JMQCMD_DST_STATE		= "A1429";

    final public static String I_JMQCMD_DUR_NAME		= "A1430";
    final public static String I_JMQCMD_CLIENT_ID		= "A1431";
    final public static String I_JMQCMD_SUB_NUM_MSG		= "A1432";
    final public static String I_JMQCMD_SUB_STATE		= "A1433";
    final public static String I_JMQCMD_SUB_NAME                = "A1434";
    final public static String I_JMQCMD_DURABLE                 = "A1435";

    final public static String I_JMQCMD_BKR_HOST 		= "A1440";
    final public static String I_JMQCMD_PRIMARY_PORT 		= "A1441";
    final public static String I_JMQCMD_PAUSE_DST_TYPE 		= "A1442";

    final public static String I_JMQCMD_DST_NAME_SHORT		= "A1443";
    final public static String I_JMQCMD_DST_TYPE_SHORT		= "A1444";
    final public static String I_JMQCMD_DST_STATE_SHORT		= "A1445";

    /*
     * New destination attr - 'Use Dead Message Queue'
     */
    final public static String I_JMQCMD_DST_USE_DMQ		= "A1446";

    final public static String I_JMQCMD_DST_VALIDATE_XML_SCHEMA_ENABLED		
								= "A1447";
    final public static String I_JMQCMD_DST_XML_SCHEMA_URI_LIST	= "A1448";
    final public static String I_JMQCMD_DST_RELOAD_XML_SCHEMA_ON_FAILURE		
								= "A1449";

    final public static String I_TOPIC				= "A1500";
    final public static String I_QUEUE				= "A1501";
    final public static String I_UNKNOWN			= "A1502";
    final public static String I_SINGLE				= "A1503";
    final public static String I_RROBIN				= "A1504";
    final public static String I_FAILOVER			= "A1505";

    final public static String I_ACTIVE				= "A1510";
    final public static String I_INACTIVE			= "A1511";

    final public static String I_DYNAMIC			= "A1520";
    final public static String I_STATIC				= "A1521";

    final public static String I_JMQCMD_QUERY_BKR		= "A1522";
    final public static String I_JMQCMD_QUERY_BKR_FAIL		= "A1523";
    final public static String I_JMQCMD_QUERY_BKR_SUC		= "A1524";
    final public static String I_BKR_INSTANCE_NAME		= "A1525";
    final public static String I_AUTO_CREATE_TOPICS		= "A1526";
    final public static String I_AUTO_CREATE_QUEUES		= "A1527";
    final public static String I_LOG_LEVEL			= "A1528";
    final public static String I_LOG_ROLLOVER_SIZE		= "A1529";
    final public static String I_LOG_ROLLOVER_INTERVAL		= "A1530";
    final public static String I_METRIC_INTERVAL		= "A1531";
    final public static String I_MAX_MSGS_IN_MEM		= "A1532";
    final public static String I_MAX_BYTES_IN_MEM		= "A1533";
    final public static String I_MAX_MSGS_IN_BROKER		= "A1534";
    final public static String I_MAX_BYTES_IN_BROKER		= "A1535";
    final public static String I_MAX_MSG_SIZE			= "A1536";

    final public static String I_UNLIMITED			= "A1537";

    final public static String I_JMQCMD_UPDATE_BKR_FAIL		= "A1538";
    final public static String I_JMQCMD_UPDATE_BKR		= "A1539";
    final public static String I_JMQCMD_UPDATE_BKR_NOOP		= "A1540";
    final public static String I_JMQCMD_UPDATE_BKR_SUC		= "A1541";

    final public static String I_JMQCMD_UPDATE_SVC_FAIL		= "A1542";
    final public static String I_JMQCMD_UPDATE_SVC		= "A1543";
    final public static String I_JMQCMD_UPDATE_SVC_NOOP		= "A1544";
    final public static String I_JMQCMD_UPDATE_SVC_SUC		= "A1545";

    final public static String I_JMQCMD_UPDATE_DEST_FAIL	= "A1546";
    final public static String I_JMQCMD_UPDATE_DEST_Q		= "A1547";
    final public static String I_JMQCMD_UPDATE_DEST_T		= "A1548";
    final public static String I_JMQCMD_UPDATE_DEST_NOOP	= "A1549";
    final public static String I_JMQCMD_UPDATE_DEST_SUC		= "A1550";

    final public static String I_JMQCMD_LIST_SUB	 =     "A1551";
    final public static String I_JMQCMD_LIST_SUB_SUC		= "A1552";
    final public static String I_JMQCMD_LIST_SUB_FAIL		= "A1553";

    final public static String I_JMQCMD_DESTROY_DUR             = "A1554";
    final public static String I_JMQCMD_DESTROY_DUR_SUC         = "A1555";
    final public static String I_JMQCMD_DESTROY_DUR_FAIL        = "A1556";
    final public static String I_JMQCMD_DESTROY_DUR_NOOP        = "A1557";

    /** REMOVED A1558 I_OFF from here and AdminResources.properties  
     */

    final public static String I_JMQCMD_METRICS_SVC_FAIL	= "A1559";
    final public static String I_JMQCMD_METRICS_SVC		= "A1560";
    final public static String I_JMQCMD_METRICS_BKR_FAIL	= "A1561";
    final public static String I_JMQCMD_METRICS_BKR		= "A1562";

    final public static String I_JMQCMD_RELOAD_CLS   		= "A1569";
    final public static String I_JMQCMD_RELOAD_CLS_FAIL		= "A1570";
    final public static String I_JMQCMD_RELOAD_CLS_SUC		= "A1571";

    final public static String I_CLS_CONFIGD_BROKERLIST		= "A1572";
    final public static String I_CLS_ACTIVE_BROKERLIST		= "A1573";
    final public static String I_CLS_CONFIG_SERVER		= "A1574";
    final public static String I_CLS_URL			= "A1575";

    /****************
     * NO LONGER USED:
     * Start of title strings for imqcmd metrics.
     * A1576 - A1607 inclusive - removed from here and AdminResources.properties 
     */

    final public static String I_METRICS_CON_NUM_CON1		= "A1608";
    final public static String I_METRICS_CON_NUM_CON2		= "A1609";

    /****************
     * NO LONGER USED:
     * A1610 - A1619 inclusive removed from here and AdminResources.properties 
     */

    /*
     * End of title strings for imqcmd metrics.
     */

    final public static String I_JMQCMD_USERNAME	= "A1620";
    final public static String I_JMQCMD_PASSWORD	= "A1621";

    final public static String I_JMQCMD_BYTE_VALUES	= "A1622";
    final public static String I_VALID_PROPNAMES 	= "A1623";

    final public static String I_QCF 			= "A1624";
    final public static String I_TCF 			= "A1625";

    final public static String I_BKR_VERSION_STR	= "A1626";
    final public static String I_BKR_VERSION_NOT_AVAILABLE	= "A1627";

    final public static String I_CUR_MSGS_IN_BROKER	= "A1628";
    final public static String I_CUR_BYTES_IN_BROKER	= "A1629";

    final public static String I_AUTOCREATED_QUEUE_DELIVERY_POLICY = "A1630";
    final public static String I_TEMPORARY            = "A1631";

    /*
     * Strings for new XA Connection Factory types
     */
    final public static String I_XQCF 			= "A1632";
    final public static String I_XTCF 			= "A1633";

    /* 
     * Strings for Purge Durable Subscription
     */
    final public static String I_JMQCMD_PURGE_DUR_FAIL  = "A1634";
    final public static String I_JMQCMD_PURGE_DUR       = "A1635";
    final public static String I_JMQCMD_PURGE_DUR_SUC   = "A1636";
    final public static String I_JMQCMD_PURGE_DUR_NOOP  = "A1637";

    /* 
     * More Administered Object types
     */
    final public static String I_CF 			= "A1638";
    final public static String I_XCF 			= "A1639";

    /*
     * Destination metrics
     */
    final public static String I_METRICS_MSGS_COUNT		= "A1643";
    final public static String I_METRICS_DST_MSGS_BYTES		= "A1644";
    final public static String I_METRICS_DST_MSGS_LARGEST1	= "A1645";
    final public static String I_METRICS_DST_MSGS_LARGEST2	= "A1646";
    final public static String I_METRICS_TOTAL			= "A1647";
    final public static String I_METRICS_RATE			= "A1648";
    final public static String I_METRICS_CURRENT		= "A1649";
    final public static String I_METRICS_PEAK			= "A1650";
    final public static String I_METRICS_AVERAGE		= "A1651";
    final public static String I_METRICS_DST_CON_ACTIVE_CONSUMERS	= "A1652";
    final public static String I_METRICS_DST_CON_BACKUP_CONSUMERS	= "A1653";

    /*
     * New Broker attribute labels
     * Auto Created Queue Max Number of Active Consumers
     * Auto Created Queue Max Number of Failover Consumers
     */
    final public static String I_AUTOCREATED_QUEUE_MAX_ACTIVE_CONS	= "A1654";
    final public static String I_AUTOCREATED_QUEUE_MAX_FAILOVER_CONS	= "A1655";

    /*
     * Destination metric labels - for metric type 'dsk'
     */
    final public static String I_METRICS_DSK_RESERVED	= "A1656";
    final public static String I_METRICS_DSK_USED	= "A1657";
    final public static String I_METRICS_DSK_UTIL_RATIO	= "A1658";

    /*
     * General labels used for metrics - added after reformatting new
     * and existing metric output
     */
    final public static String I_METRICS_MSGS			= "A1659";
    final public static String I_METRICS_MSG_BYTES		= "A1660";
    final public static String I_METRICS_PKTS			= "A1661";
    final public static String I_METRICS_PKT_BYTES		= "A1662";
    final public static String I_METRICS_MSGS_PER_SEC		= "A1663";
    final public static String I_METRICS_MSG_BYTES_PER_SEC	= "A1664";
    final public static String I_METRICS_PKTS_PER_SEC		= "A1665";
    final public static String I_METRICS_PKT_BYTES_PER_SEC	= "A1666";
    final public static String I_METRICS_IN			= "A1667";
    final public static String I_METRICS_OUT			= "A1668";
    final public static String I_METRICS_JVM_HEAP_BYTES		= "A1669";
    final public static String I_METRICS_THREADS		= "A1670";
    final public static String I_METRICS_FREE			= "A1671";
    final public static String I_METRICS_ACTIVE			= "A1672";
    final public static String I_METRICS_LOW			= "A1673";
    final public static String I_METRICS_HIGH			= "A1674";

    /*
     * New strings for 'list dst' output.
     */
    final public static String I_JMQCMD_DST_NUM_PRODUCER	= "A1675";
    final public static String I_JMQCMD_DST_NUM_CONSUMER	= "A1676";
    final public static String I_JMQCMD_DST_MSGS		= "A1677";
    final public static String I_JMQCMD_DST_CONSUMERS_ACTIVE	= "A1678";
    final public static String I_JMQCMD_DST_CONSUMERS_BACKUP	= "A1679";
    final public static String I_JMQCMD_DST_MSGS_TOTAL_COUNT	= "A1680";
    final public static String I_JMQCMD_DST_MSGS_UNACK_COUNT	= "A1681";
    final public static String I_JMQCMD_DST_MSGS_AVG_SIZE	= "A1682";

    /*
     * Labels for 
     *  - new broker attribute - 'Log Dead Messages'
     *  - msg total/sizes of DMQ
     */
    final public static String I_BKR_LOG_DEAD_MSGS		= "A1683";
    final public static String I_CUR_MSGS_IN_DMQ		= "A1684";
    final public static String I_CUR_BYTES_IN_DMQ		= "A1685";
    final public static String I_BKR_DMQ_TRUNCATE_MSG_BODY	= "A1686";

    /*
     * Label for 'imqcmd metrics dst -m con' when topics are 
     * specified. 'Active Consumers' was a bit confusing since
     * it implied *only* active durable consumers were accounted
     * for which is not true.
     */
    final public static String I_METRICS_DST_CON_CONSUMERS	= "A1687";

    /*
     * Label for 'imqcmd query dst' when topics are specified.
     * 'Current Number of Active Consumers' was a bit confusing
     * since it implied *only* active durable consumers were 
     * accounted for which is not true.
     */
    final public static String I_JMQCMD_DST_CUR_CONS		= "A1688";

    /*
     * More strings for 'imqcmd list dst'.
     */
    final public static String I_JMQCMD_DST_CONSUMERS_TOTAL	= "A1689";
    final public static String I_JMQCMD_DST_PRODUCERS_TOTAL	= "A1690";
    final public static String I_JMQCMD_DST_WILDCARD		= "A1691";
    final public static String I_JMQCMD_DST_CUR_NUM_PRODUCERS_WILDCARD = "A1692";
    final public static String I_JMQCMD_DST_CUR_NUM_CONSUMERS_WILDCARD = "A1693";
    final public static String I_JMQCMD_DST_MSGS_INDELAY_COUNT	= "A1694";

    /*
     * Strings for 'imqcmd quiesce bkr'
     */
    final public static String I_JMQCMD_QUIESCE_BKR		= "A1700";
    final public static String I_JMQCMD_QUIESCE_BKR_FAIL	= "A1701";
    final public static String I_JMQCMD_QUIESCE_BKR_SUC		= "A1702";
    final public static String I_JMQCMD_QUIESCE_BKR_NOOP	= "A1703";

    /*
     * Strings for 'imqcmd takeover bkr'
     */
    final public static String I_JMQCMD_TAKEOVER_BKR		= "A1704";
    final public static String I_JMQCMD_TAKEOVER_BKR_FAIL	= "A1705";
    final public static String I_JMQCMD_TAKEOVER_BKR_SUC	= "A1706";
    final public static String I_JMQCMD_TAKEOVER_BKR_NOOP	= "A1707";
    final public static String I_JMQCMD_BKR_PERFORMING_TAKEOVER	= "A1708";

    /*
     * Additional strings for 'imqcmd query bkr' to show cluster information
     */
    final public static String I_CLS_CLUSTER_ID			= "A1709";
    final public static String I_CLS_IS_HA			= "A1710";
    final public static String I_CLS_BROKER_ID			= "A1711";

    /*
     * Strings for 'imqcmd list bkr'
     */
    final public static String I_JMQCMD_LIST_BKR		= "A1712";
    final public static String I_JMQCMD_LIST_BKR_FAIL		= "A1713";
    final public static String I_JMQCMD_LIST_BKR_SUC		= "A1714";
    final public static String I_JMQCMD_LIST_BKR_NONE		= "A1715";
    final public static String I_JMQCMD_CLS_BROKER_ID		= "A1716";
    final public static String I_JMQCMD_CLS_ADDRESS		= "A1717";
    final public static String I_JMQCMD_CLS_BROKER_STATE	= "A1718";
    final public static String I_JMQCMD_CLS_NUM_MSGS		= "A1719";
    final public static String I_JMQCMD_CLS_TAKEOVER_ID1	= "A1720";
    final public static String I_JMQCMD_CLS_TIME_SINCE_TIMESTAMP1= "A1721";

    /*
     * Strings for 'imqcmd list jmx'
     */
    final public static String I_JMQCMD_LIST_JMX		= "A1722";
    final public static String I_JMQCMD_LIST_JMX_FAIL		= "A1723";
    final public static String I_JMQCMD_LIST_JMX_SUC		= "A1724";
    final public static String I_JMQCMD_LIST_JMX_NONE		= "A1725";
    final public static String I_JMQCMD_JMX_NAME		= "A1726";
    final public static String I_JMQCMD_JMX_ACTIVE		= "A1727";
    final public static String I_JMQCMD_JMX_URL			= "A1728";

    /*
     * Strings for 'imqcmd destroy cxn'
     */
    final public static String I_JMQCMD_DESTROY_CXN		= "A1729";
    final public static String I_JMQCMD_DESTROY_CXN_FAIL	= "A1730";
    final public static String I_JMQCMD_DESTROY_CXN_SUC		= "A1731";
    final public static String I_JMQCMD_DESTROY_CXN_NOOP	= "A1732";

    /*
     * Additional string for query bkr output 
     *  - "Broker is Embedded"
     */
    final public static String I_JMQCMD_BKR_IS_EMBEDDED		= "A1733";

    /*
     * Additional strings for query dst to indicate
     * actual msg/bytes vs those held in transaction
     */
    final public static String I_JMQCMD_DST_HELD_IN_TXN		= "A1734";
    final public static String I_JMQCMD_DST_ACTUAL		= "A1735";

    /*
     * Additional strings for 'imqcmd list bkr'
     */
    final public static String I_JMQCMD_CLS_TAKEOVER_ID2	= "A1736";
    final public static String I_JMQCMD_CLS_TIME_SINCE_TIMESTAMP2= "A1737";

    /*
     * String for list/query dst to indicate msgs (count or size) that
     * are on a remote broker (eg in a cluster)
     */
    final public static String I_JMQCMD_DST_REMOTE		= "A1738";

    /*
     * messages in delay delivery
     */
    final public static String I_JMQCMD_DST_INDELAY		= "A1739";

    /*
     * Strings for 'imqcmd unquiesce bkr'
     */
    final public static String I_JMQCMD_UNQUIESCE_BKR		= "A1750";
    final public static String I_JMQCMD_UNQUIESCE_BKR_FAIL	= "A1751";
    final public static String I_JMQCMD_UNQUIESCE_BKR_SUC	= "A1752";
    final public static String I_JMQCMD_UNQUIESCE_BKR_NOOP	= "A1753";

    /*
     * String for successful admin sent broker shutdown msg.
     * This is displayed in MQ 4.0 for cases where imqcmd
     * will not wait for the broker to shutdown before
     * returning.
     */
    final public static String I_JMQCMD_SENT_SHUTDOWN_BKR_SUC	= "A1760";

    /*
     * String indicating that imqcmd is waiting for the broker to shut
     * down.
     */
    final public static String I_JMQCMD_WAITING_FOR_SHUTDOWN	= "A1761";

    /*
     * License label
     */
    final public static String I_JMQCMD_LICENSE			= "A1762";

    /*
     * 'IMQ_VARHOME' label
     */
    final public static String I_JMQCMD_CONFIG_DATA_ROOT_DIR	= "A1763";

    /*
     * Strings for 'imqcmd reset bkr'
     */
    final public static String I_JMQCMD_RESET_BKR     		= "A1764";
    final public static String I_JMQCMD_RESET_BKR_SUC  		= "A1765";
    final public static String I_JMQCMD_RESET_BKR_FAIL 		= "A1766";
    final public static String I_JMQCMD_RESET_BKR_NOOP 		= "A1767";
    final public static String I_JMQCMD_RESET_BKR_TYPE 		= "A1768";

    /*
     * Additional strings for 'imqcmd takeover bkr'
     */
    final public static String I_JMQCMD_BKR_STORE_TAKEOVER	= "A1769";
    
    /**
     * New string for listing all durables
     */
    final public static String I_JMQCMD_LIST_ALL_SUB		= "A1780";
    
    
    /*
     * Strings for 'imqcmd checkpoint bkr'
     */
    final public static String I_JMQCMD_CHECKPOINT_BKR     		= "A1781";
    final public static String I_JMQCMD_CHECKPOINT_BKR_SUC  	= "A1782";
    final public static String I_JMQCMD_CHECKPOINT_BKR_FAIL 	= "A1783";
    final public static String I_JMQCMD_CHECKPOINT_BKR_NOOP 	= "A1784";

    /*
     * String for global bkr useDMQ flag 
     */
    final public static String I_BKR_AUTOCREATE_DESTINATION_USE_DMQ    = "A1785";
    
    /*
     * Strings for ServiceState'
     */
    final public static String I_SERVICE_STATE_UNINITIALIZED = "A1790";
    final public static String I_SERVICE_STATE_INITIALIZED = "A1791";
    final public static String I_SERVICE_STATE_STARTED = "A1792";
    final public static String I_SERVICE_STATE_RUNNING = "A1793";
    final public static String I_SERVICE_STATE_PAUSED = "A1794";
    final public static String I_SERVICE_STATE_SHUTTINGDOWN = "A1795";
    final public static String I_SERVICE_STATE_STOPPED = "A1796";
    final public static String I_SERVICE_STATE_DESTROYED = "A1797";
    final public static String I_SERVICE_STATE_QUIESCED = "A1798";
    final public static String I_SERVICE_STATE_UNKNOWN = "A1799";
    
    /*
     * Strings for DestState'
     */
    final public static String I_DEST_STATE_RUNNING = "A1800";
    final public static String I_DEST_STATE_CONSUMERS_PAUSED = "A1801";
    final public static String I_DEST_STATE_PRODUCERS_PAUSED = "A1802";
    final public static String I_DEST_STATE_PAUSED = "A1803";
      
    final public static String I_JMQCMD_CHANGEMASTER_CLS    = "A1804";
    final public static String I_JMQCMD_CHANGEMASTER_CLS_FAIL     = "A1805";
    final public static String I_JMQCMD_CHANGEMASTER_CLS_SUC      = "A1806";
    final public static String I_JMQCMD_CHANGEMASTER_NOOP         = "A1807";

    /*
     * Strings for 'imqcmd migratestore bkr'
     */
    final public static String I_JMQCMD_MIGRATESTORE_BKR    = "A1808";
    final public static String I_JMQCMD_MIGRATESTORE_BKR_FAIL_NOT_MIGRATED = "A1809";
    final public static String I_JMQCMD_MIGRATESTORE_BKR_SUC  = "A1810";
    final public static String I_JMQCMD_MIGRATESTORE_BKR_NOOP = "A1811";
    final public static String I_JMQCMD_MIGRATESTORE_BKR_TO = "A1812";
    final public static String I_JMQCMD_MIGRATESTORE_BKR_FAIL = "A1813";
    final public static String I_JMQCMD_MIGRATESTORE_BKR_FAIL_STATUS = "A1814";
    final public static String I_JMQCMD_MIGRATE_PARTITION_FAIL_NOT_MIGRATED = "A1815";
    final public static String I_JMQCMD_MIGRATE_PARTITION = "A1816";
    final public static String I_JMQCMD_MIGRATE_PARTITION_TO = "A1817";
    final public static String I_JMQCMD_NUM_PARTITION            = "A1818";
    final public static String I_JMQCMD_MIGRATE_PARTITION_SUC    = "A1819";
    final public static String I_JMQCMD_MIGRATE_PARTITION_FAIL        = "A1820";
    final public static String I_JMQCMD_MIGRATE_PARTITION_FAIL_STATUS = "A1821";
    final public static String I_JMQCMD_MIGRATE_PARTITION_NOOP = "A1822";


    // 2000-2999 Warning Messages
    final public static String W_OBJ_ALREADY_EXISTS	= "A2000";
    final public static String W_ADD_OBJ_BE_OVERWRITTEN	= "A2001";
    final public static String W_JNDI_PROPERTY_WARNING 	= "A2002";
    final public static String W_SET_MAX_THREAD_ZERO 	= "A2003";
    final public static String W_INCOMPATIBLE_OBJ       = "A2004";

    final public static String W_DST_QDP_DEPRECATED		= "A2005";
    final public static String W_DST_QDP_DEPRECATED_CONV	= "A2006";
    final public static String W_DST_QDP_DEPRECATED_IGNORE	= "A2007";

    final public static String W_BKR_QDP_DEPRECATED		= "A2008";
    final public static String W_BKR_QDP_DEPRECATED_CONV	= "A2009";
    final public static String W_BKR_QDP_DEPRECATED_IGNORE	= "A2010";

    /*
     * Strings used to indicate to the user that '-1' is
     * the preferred value for unlimited (and not '0').
     */
    final public static String W_ZERO_UNLIMITED_SPECIFIED	= "A2011";
    final public static String W_NEW_UNLIMITED_VALUE		= "A2012";
    final public static String W_CONVERTED_UNLIMITED_VALUE	= "A2013";

    /*
     * Warning message to let users know that the password option
     * is deprecated in imqcmd.
     */
    final public static String W_PASSWD_OPTION_DEPRECATED	= "A2014";
    final public static String W_ECHO_PASSWORD	= "A2015";
    final public static String W_UNENCODED_ENTRY_IN_PASSFILE    = "A2016";

    // 3000-3999 Error Messages
    // option parsing errors
    final public static String E_OPTION_PARSE_ERROR	= "A3000";
    final public static String E_UNRECOG_OPTION		= "A3001";
    final public static String E_INVALID_BASE_PROPNAME	= "A3002";
    final public static String E_INVALID_HARDCODED_VAL	= "A3003";
    final public static String E_MISSING_ARG		= "A3004";
    final public static String E_BAD_NV_ARG		= "A3005";
    final public static String E_PASSWD_OPTION_NOT_SUPPORTED = "A3006";

    // option validating errors
    final public static String E_OPTION_VALID_ERROR	= "A3020";
    final public static String E_BAD_COMMAND_SPEC	= "A3021";
    final public static String E_NO_COMMAND_SPEC	= "A3022";
    final public static String E_NO_OBJ_TYPE_SPEC	= "A3023";
    final public static String E_INVALID_OBJ_TYPE	= "A3024";
    final public static String E_NO_LOOKUP_NAME		= "A3025";
    final public static String E_NO_DEST_NAME		= "A3026";
    final public static String E_INVALID_READONLY_VALUE = "A3027";

    // imqobjmgr errors
    final public static String E_NO_OBJ_CREATOR		= "A3050";
    final public static String E_CANNOT_LOC_OBJ		= "A3051";
    final public static String E_CANNOT_LOC_TREE	= "A3052";
    final public static String E_INVALID_UN_OR_PASSWD	= "A3053";
    final public static String E_NONSUPPORTED_AUTH_TYPE	= "A3054";
    final public static String E_NO_PERMISSION		= "A3055";
    final public static String E_NO_COMMUNICATION	= "A3056";
    final public static String E_INVALID_SYNTAX		= "A3057";
    final public static String E_OBJ_TYPES_NOT_SAME	= "A3058";
    final public static String E_UNSUPP_VER_NUMBER	= "A3059";
    final public static String E_MISSING_VER_NUMBER	= "A3060";
    final public static String E_GEN_OP_FAILED		= "A3061";

    // property validating errors
    final public static String E_INVALID_PROPNAME	= "A3070";
    final public static String E_CANT_MOD_READONLY	= "A3071";
    final public static String E_INVALID_PROP_VALUE	= "A3072";

    final public static String E_PROB_LOADING_PROP_FILE	= "A3080";
    final public static String E_PROB_GETTING_USR_INPUT	= "A3081";

    // imqcmd errors
    final public static String E_TARGET_NAME_NOT_SPEC	= "A3100";
    final public static String E_BAD_CMDARG_SPEC1	= "A3101";
    final public static String E_BAD_CMDARG_SPEC2	= "A3102";
    final public static String E_DEST_NAME_NOT_SPEC	= "A3103";
    final public static String E_TARGET_ATTRS_NOT_SPEC	= "A3104";
    final public static String E_DEST_TYPE_NOT_SPEC	= "A3105";
    final public static String E_FLAVOUR_TYPE_INVALID   = "A3106";
    final public static String E_INVALID_INTEGER_VALUE  = "A3107";
    final public static String E_INVALID_DEST_TYPE      = "A3108";
    final public static String E_CANNOT_PAUSE_SVC      	= "A3109";
    final public static String E_CANNOT_RESUME_SVC     	= "A3110";
    final public static String E_DEST_NOT_TOPIC     	= "A3111";
    final public static String E_CLIENT_ID_NOT_SPEC    	= "A3112";
    final public static String E_VALID_VALUES		= "A3113";
    final public static String E_INVALID_BOOLEAN_VALUE  = "A3114";
    final public static String E_INVALID_LOG_LEVEL_VALUE= "A3115";
    final public static String E_MAX_RECONNECT_REACHED	= "A3116";
    final public static String E_INVALID_BYTE_VALUE	= "A3117";
    final public static String E_INVALID_RECV_TIMEOUT_VALUE= "A3118";
    final public static String E_INVALID_PAUSETYPE_VALUE= "A3119";

    // imqcmd exception errors
    final public static String E_JMQCMD_CONNECT_ERROR      	= "A3120";
    final public static String E_JMQCMD_MSG_SEND_ERROR      	= "A3121";
    final public static String E_JMQCMD_MSG_REPLY_ERROR      	= "A3122";
    final public static String E_JMQCMD_CLOSE_ERROR      	= "A3123";
    final public static String E_JMQCMD_PROB_GETTING_MSG_TYPE	= "A3124";
    final public static String E_JMQCMD_PROB_GETTING_STATUS	= "A3125";
    final public static String E_JMQCMD_REPLY_NOT_RECEIVED	= "A3126";
    final public static String E_JMQCMD_INVALID_OPERATION	= "A3127";
    final public static String E_JMQCMD_INVALID_PORT_VALUE	= "A3128";

    // imqcmd attribute checking error messages
    final public static String E_BAD_ATTR_SPEC    		= "A3129";
    final public static String E_BAD_ATTR_SPEC2    		= "A3130";
    final public static String E_BAD_ATTR_SPEC_CREATE_QUEUE    	= "A3131";
    final public static String E_BAD_ATTR_SPEC_CREATE_TOPIC    	= "A3132";
    final public static String E_BAD_ATTR_SPEC_UPDATE_BKR    	= "A3133";
    final public static String E_BAD_ATTR_SPEC_UPDATE_QUEUE    	= "A3134";
    final public static String E_BAD_ATTR_SPEC_UPDATE_TOPIC    	= "A3135";
    final public static String E_BAD_ATTR_SPEC_UPDATE_SVC    	= "A3136";
    final public static String E_INVALID_METRIC_INTERVAL    	= "A3137";
    final public static String E_INVALID_METRIC_TYPE    	= "A3138";
    final public static String E_VERIFY_BROKER      		= "A3139";

    final public static String E_INVALID_LOGIN      		= "A3140";
    final public static String E_LOGIN_FORBIDDEN      		= "A3141";

    /*
     * Error msgs for property file loading/saving
     */
    final public static String E_PROPFILE_NOT_READABLE		= "A3142";
    final public static String E_FAILED_TO_OPEN_PROPFILE	= "A3143";
    final public static String E_PROPFILE_NOT_WRITEABLE		= "A3144";
    final public static String E_CANNOT_CREATE_PROPFILE		= "A3145";
    final public static String E_FAILED_TO_WRITE_PROPFILE	= "A3146";

    /*
     * More error msgs for imqcmd
     */
    final public static String E_ADMIN_MAX_THREAD		= "A3147";
    final public static String E_SINGLE_TARGET_ATTR_NOT_SPEC	= "A3148";
    final public static String E_PORT_NOT_ALLOWED_TO_CHANGE	= "A3149";
    final public static String E_INVALID_TXN_ID			= "A3150";
    
    /*
     * Errors for input file version property.
     */
    final public static String E_BAD_INPUTFILE_VERSION		= "A3151";
    final public static String E_UNPARSABLE_INPUTFILE_VERSION	= "A3152";
    final public static String E_NOT_SUP_INPUTFILE_VERSION	= "A3153";

    /*
     * Error when using SSL as admin transport
     */
    final public static String E_PROB_SETTING_SSL		= "A3154";

    /*
     * Errors for imqcmd (contd)
     */
    final public static String E_BAD_ATTR_SPEC_PAUSE_DST    	= "A3155";
    final public static String E_INVALID_METRIC_DST_TYPE    	= "A3156";
    final public static String E_INVALID_METRIC_SAMPLES    	= "A3157";

    /*
     * Invalid number of retries value (imqcmd)
     */
    final public static String E_INVALID_NUM_RETRIES_VALUE	= "A3158";

    /*
     * Invalid connection ID (imqcmd)
     */
    final public static String E_INVALID_CXN_ID			= "A3159";

    /*
     * Generic invalid attr value msg (imqcmd)
     */
    final public static String E_INVALID_ATTR_VALUE		= "A3160";

    /*
     * Error string displayed when an error occurs while
     * reading the passfile specified to imqcmd.
     */
    final public static String E_READ_PASSFILE_FAIL		= "A3161";

    /*
     * Error string displayed when a passfile was specified
     * for imqcmd but the corresponding property for imqcmd's
     * passwd was missing from it.
     */
    final public static String E_PASSFILE_PASSWD_PROPERTY_NOT_FOUND	= "A3162";

    /*
     * Generic message used in SPI to indicate physical destination
     * creation failed.
     */
    final public static String E_SPI_DEST_CREATION_FAILED	= "A3163";

    /*
     * Error string used in SPI - invalid type used (in Map)
     * when specifying physical destination attributes. 
     * Only String types are used there.
     */
    final public static String E_SPI_ATTR_TYPE_NOT_STRING	= "A3164";

    /*
     * Error string printed when a bad value is passed in for
     * '-time'.
     */
    final public static String E_INVALID_TIME_VALUE		= "A3165";

    /*
     * Error string printed when a bad value is passed in for
     * '-rst'.
     */
    final public static String E_INVALID_RESETTYPE_VALUE	= "A3166";

    /*
     * Error encountered when use bad broker address for imqcmd
     */
    final public static String E_JMQCMD_BAD_ADDRESS		= "A3167";

    /*
     * Error encountered when a create only attr is specified for
     * update.
     */
    final public static String E_UPDATE_ATTR_SPEC_CREATE_ONLY	= "A3168";

    /*
     * Errors related to 'imqcmd takeover bkr'
     */
    final public static String E_FAILED_TO_OBTAIN_CLUSTER_INFO	= "A3169";
    final public static String E_CANNOT_FIND_BROKERID		= "A3170";
    final public static String E_BROKER_NO_TAKEOVER_SUPPORT = "A3171";
    final public static String E_BROKER_NO_STORE_MIGRATION_SUPPORT = "A3172";
    final public static String E_MIGRATE_PARTITION_NO_TARGET_BROKER = "A3173";
    final public static String E_MIGRATE_PARTITION_NO_SUPPORT = "A3174";


    // 4000-4999 Exception Messages
    final public static String X_GENERAL_EXCEPTION	= "A4000";
    final public static String X_JMS_EXCEPTION		= "A4001";

    /*
     * Exception messages for JNDI Object Stores
     */
    final public static String X_JNDI_NAME_ALREADY_BOUND	= "A4002";
    final public static String X_JNDI_AUTH_ERROR		= "A4003";
    final public static String X_JNDI_AUTH_TYPE_NOT_SUPPORTED	= "A4004";
    final public static String X_JNDI_NO_PERMISSION		= "A4005";
    final public static String X_JNDI_CANNOT_COMMUNICATE	= "A4006";
    final public static String X_JNDI_CANNOT_CREATE_INIT_CTX	= "A4007";
    final public static String X_JNDI_SCHEMA_VIOLATION		= "A4008";
    final public static String X_JNDI_NAME_NOT_EXIST		= "A4009";
    final public static String X_JNDI_NAME_ALREADY_EXISTS	= "A4010";
    final public static String X_JNDI_NOT_CONTEXT		= "A4011";
    final public static String X_JNDI_INVALID_ATTRS		= "A4012";
    final public static String X_JNDI_GENERAL_NAMING_EXCEPTION	= "A4013";
    final public static String X_JMSSPI_INVALID_PORT = "A4014";
    final public static String X_JMSSPI_INVALID_DOMAIN_TYPE = "A4015";
    final public static String X_JMSSPI_INVALID_OBJECT_TYPE = "A4016";
    final public static String X_JMSSPI_NO_DESTINATION_NAME = "A4017";

    /*
     * Exception msgs from SPI deleteProviderInstance() method
     */
    final public static String X_JMSSPI_DELETE_INST_INT_PREM		= "A4018";
    final public static String X_JMSSPI_DELETE_INST_NOT_EXIST		= "A4019";
    final public static String X_JMSSPI_DELETE_INST_BEING_USED		= "A4020";
    final public static String X_JMSSPI_DELETE_INST_NO_PERM		= "A4021";
    final public static String X_JMSSPI_DELETE_INST_PROB_RM_STORE	= "A4022";
    final public static String X_JMSSPI_DELETE_INST_IOEXCEPTION		= "A4023";
    final public static String X_JMSSPI_DELETE_INST_UNKNOWN		= "A4024";


    // 5000-5999 Question Messages
    final public static String Q_OVERWRITE_OK		= "A5000";
    final public static String Q_DELETE_OK		= "A5001";
    final public static String Q_UPDATE_OK		= "A5002";
    final public static String Q_DESTROY_DST_OK		= "A5003";
    final public static String Q_PURGE_DST_OK		= "A5004";
    final public static String Q_PAUSE_BKR_OK		= "A5005";
    final public static String Q_PAUSE_SVC_OK		= "A5006";
    final public static String Q_RESUME_BKR_OK		= "A5007";
    final public static String Q_RESUME_SVC_OK		= "A5008";
    final public static String Q_SHUTDOWN_BKR_OK	= "A5009";
    final public static String Q_RESTART_BKR_OK		= "A5010";
    final public static String Q_UPDATE_BKR_OK		= "A5011";
    final public static String Q_UPDATE_SVC_OK		= "A5012";
    final public static String Q_UPDATE_DEST_OK		= "A5013";
    final public static String Q_DESTROY_DUR_OK		= "A5014";

    final public static String Q_RESPONSE_YES_SHORT	= "A5015";
    final public static String Q_RESPONSE_YES		= "A5016";
    final public static String Q_RESPONSE_NO_SHORT	= "A5017";
    final public static String Q_RESPONSE_NO		= "A5018";

    final public static String Q_ENTER_VALUE		= "A5019";
    final public static String Q_PURGE_DUR_OK		= "A5020";
    final public static String Q_PAUSE_DST_OK		= "A5021";
    final public static String Q_RESUME_DST_OK		= "A5022";
    final public static String Q_COMPACT_DST_OK		= "A5023";
    final public static String Q_COMPACT_DSTS_OK	= "A5024";
    final public static String Q_PAUSE_DSTS_OK		= "A5025";
    final public static String Q_RESUME_DSTS_OK		= "A5026";
    final public static String Q_QUIESCE_BKR_OK		= "A5027";
    final public static String Q_TAKEOVER_BKR_OK	= "A5028";
    final public static String Q_DESTROY_CXN_OK		= "A5029";
    final public static String Q_UNQUIESCE_BKR_OK	= "A5030";
    final public static String Q_RESET_BKR_OK		= "A5031";
    final public static String Q_ROLLBACK_TXN_OK	= "A5032";
    final public static String Q_COMMIT_TXN_OK		= "A5033";
    final public static String Q_UPDATE_DEST_XML_SCHEMA_OK = "A5034";
    final public static String Q_CHECKPOINT_BKR_OK	= "A5035";
    final public static String Q_CHANGEMASTER_OK = "A5036";
    final public static String Q_MIGRATESTORE_BKR_OK = "A5037";
    final public static String Q_MIGRATE_PARTITION_OK = "A5038";

    /***************** End of message key constants *******************/
}
