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
 * @(#)MBeanResources.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans.resources;

import java.util.ResourceBundle;
import java.util.Locale;
import com.sun.messaging.jmq.util.MQResourceBundle;

/**
 * This class wraps a PropertyResourceBundle, and provides constants
 * to use as message keys. The reason we use constants for the message
 * keys is to provide some compile time checking when the key is used
 * in the source.
 */

public class MBeanResources extends MQResourceBundle {

    private static MBeanResources resources = null;

    public static MBeanResources getResources() {
        return getResources(null);
    }

    public static synchronized MBeanResources getResources(Locale locale) {

        if (locale == null) {
            locale = Locale.getDefault();
        }

	if (resources == null || !locale.equals(resources.getLocale())) { 
	    ResourceBundle prb =
                ResourceBundle.getBundle(
		"com.sun.messaging.jmq.jmsserver.management.mbeans.resources.MBeanResources",
		locale);
            resources = new MBeanResources(prb);
	}
	return resources;
    }

    private MBeanResources(ResourceBundle rb) {
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
     */

    // 0-999     Miscellaneous messages
    final public static String M_DUMMY	 		= "MB0000";

    // 1000-1999 Informational Messages
    final public static String I_CLS_CFG_DESC				= "MB1000";
    final public static String I_CLS_ATTR_BROKER_ID_DESC		= "MB1001";
    final public static String I_CLS_ATTR_CONFIG_FILE_URL_DESC		= "MB1002";
    final public static String I_CLS_ATTR_CLUSTER_ID_DESC		= "MB1003";
    final public static String I_CLS_ATTR_HIGHLY_AVAILABLE_DESC		= "MB1004";
    final public static String I_CLS_ATTR_LOCAL_BROKER_INFO_DESC	= "MB1005";
    final public static String I_CLS_ATTR_MASTER_BROKER_INFO_DESC	= "MB1006";
    final public static String I_CLS_CFG_OP_GET_BROKER_ADDRESSES_DESC	= "MB1007";
    final public static String I_CLS_CFG_OP_GET_BROKER_IDS_DESC		= "MB1008";
    final public static String I_CLS_CFG_OP_GET_BROKER_INFO_DESC	= "MB1009";
    final public static String I_CLS_OP_GET_BROKER_INFO_BY_ADDRESS_DESC		= "MB1010";
    final public static String I_CLS_OP_GET_BROKER_INFO_BY_ADDRESS_PARAM_ADDR_DESC	= "MB1011";
    final public static String I_CLS_OP_GET_BROKER_INFO_BY_ID_DESC	= "MB1012";
    final public static String I_CLS_OP_GET_BROKER_INFO_BY_ID_PARAM_ID_DESC		= "MB1013";
    final public static String I_CLS_CFG_OP_RELOAD_DESC			= "MB1014";

    final public static String I_CLS_MON_DESC				= "MB1020";
    final public static String I_CLS_MON_OP_GET_BROKER_ADDRESSES_DESC	= "MB1021";
    final public static String I_CLS_MON_OP_GET_BROKER_IDS_DESC		= "MB1022";
    final public static String I_CLS_MON_OP_GET_BROKER_INFO_DESC	= "MB1023";
    final public static String I_CLS_NOTIFICATIONS			= "MB1024";

    final public static String I_ATTR_CHANGE_NOTIFICATION		= "MB1025";

    final public static String I_BKR_NOTIFICATIONS			= "MB1026";
    final public static String I_BKR_CFG_DESC				= "MB1027";
    final public static String I_BKR_ATTR_BKR_ID			= "MB1028";
    final public static String I_BKR_ATTR_EMBEDDED			= "MB1029";
    final public static String I_BKR_ATTR_INSTANCE_NAME			= "MB1030";
    final public static String I_BKR_ATTR_PORT				= "MB1031";
    final public static String I_BKR_ATTR_VERSION			= "MB1032";

    final public static String I_BKR_OP_QUIESCE_DESC			= "MB1033";
    final public static String I_BKR_OP_RESET_METRICS_DESC		= "MB1034";
    final public static String I_BKR_OP_RESTART_DESC			= "MB1035";
    final public static String I_BKR_OP_SHUTDOWN_DESC			= "MB1036";
    final public static String I_BKR_OP_TAKEOVER_DESC			= "MB1037";
    final public static String I_BKR_OP_UNQUIESCE_DESC			= "MB1038";

    final public static String I_BKR_OP_SHUTDOWN_PARAM_NO_FAILOVER_DESC	= "MB1039";
    final public static String I_BKR_OP_SHUTDOWN_PARAM_TIME_DESC	= "MB1040";
    final public static String I_BKR_OP_TAKEOVER_PARAM_BROKER_ID_DESC	= "MB1041";

    final public static String I_BKR_MON_DESC				= "MB1042";

    final public static String I_CXN_CFG_DESC				= "MB1043";

    final public static String I_CXN_ATTR_CLIENT_ID			= "MB1044";
    final public static String I_CXN_ATTR_CLIENT_PLATFORM		= "MB1045";
    final public static String I_CXN_ATTR_CXN_ID			= "MB1046";
    final public static String I_CXN_ATTR_HOST				= "MB1047";
    final public static String I_CXN_ATTR_NUM_CONSUMERS			= "MB1048";
    final public static String I_CXN_ATTR_NUM_PRODUCERS			= "MB1049";
    final public static String I_CXN_ATTR_PORT				= "MB1050";
    final public static String I_CXN_ATTR_SERVICE_NAME			= "MB1051";
    final public static String I_CXN_ATTR_USER				= "MB1052";

    final public static String I_CXN_OP_GET_CONSUMER_IDS_DESC		= "MB1053";
    final public static String I_CXN_OP_GET_PRODUCER_IDS_DESC		= "MB1054";
    final public static String I_CXN_OP_GET_SERVICE_DESC		= "MB1055";
    final public static String I_CXN_OP_GET_TEMP_DESTINATIONS_DESC	= "MB1056";

    final public static String I_CXN_MON_DESC				= "MB1057";

    final public static String I_CXN_MGR_CFG_DESC			= "MB1058";
    final public static String I_CXN_MGR_ATTR_NUM_CONNECTIONS		= "MB1059";
    final public static String I_CXN_MGR_ATTR_NUM_CONNECTIONS_OPENED	= "MB1060";
    final public static String I_CXN_MGR_ATTR_NUM_CONNECTIONS_REJECTED	= "MB1061";

    final public static String I_CXN_MGR_OP_DESTROY_DESC		= "MB1062";
    final public static String I_CXN_MGR_CFG_OP_GET_CONNECTIONS_DESC	= "MB1063";
    final public static String I_CXN_MGR_MON_OP_GET_CONNECTIONS_DESC	= "MB1064";
    final public static String I_CXN_MGR_OP_DESTROY_PARAM_CXN_ID_DESC	= "MB1065";

    final public static String I_CXN_MGR_MON_DESC			= "MB1066";
    final public static String I_CXN_NOTIFICATIONS			= "MB1067";

    final public static String I_CON_MGR_CFG_DESC			= "MB1068";
    final public static String I_CON_MGR_ATTR_NUM_CONSUMERS		= "MB1069";

    final public static String I_CON_MGR_OP_GET_CONSUMER_IDS_DESC	= "MB1070";
    final public static String I_CON_MGR_OP_GET_CONSUMER_INFO_DESC	= "MB1071";
    final public static String I_CON_MGR_OP_GET_CONSUMER_INFO_BY_ID_DESC= "MB1072";
    final public static String I_CON_MGR_OP_PURGE_DESC			= "MB1073";
    final public static String I_CON_MGR_OP_PARAM_CON_ID_DESC		= "MB1074";

    final public static String I_CON_MGR_MON_DESC			= "MB1075";

    final public static String I_DST_CFG_DESC				= "MB1076";
    final public static String I_DST_ATTR_CONSUMER_FLOW_LIMIT		= "MB1077";
    final public static String I_DST_ATTR_LOCAL_ONLY			= "MB1078";
    final public static String I_DST_ATTR_LIMIT_BEHAVIOR		= "MB1079";
    final public static String I_DST_ATTR_LOCAL_DELIVERY_PREFERRED	= "MB1080";
    final public static String I_DST_ATTR_MAX_BYTES_PER_MSG		= "MB1081";
    final public static String I_DST_ATTR_MAX_NUM_ACTIVE_CONSUMERS	= "MB1082";
    final public static String I_DST_ATTR_MAX_NUM_BACKUP_CONSUMERS	= "MB1083";
    final public static String I_DST_ATTR_MAX_NUM_MSGS			= "MB1084";
    final public static String I_DST_ATTR_MAX_NUM_PRODUCERS		= "MB1085";
    final public static String I_DST_ATTR_MAX_TOTAL_MSG_BYTES		= "MB1086";
    final public static String I_DST_ATTR_NAME				= "MB1087";
    final public static String I_DST_ATTR_TYPE				= "MB1088";
    final public static String I_DST_ATTR_USE_DMQ			= "MB1089";
    final public static String I_DST_ATTR_AVG_NUM_ACTIVE_CONSUMERS	= "MB1090";
    final public static String I_DST_ATTR_AVG_NUM_BACKUP_CONSUMERS	= "MB1091";
    final public static String I_DST_ATTR_AVG_NUM_CONSUMERS		= "MB1092";
    final public static String I_DST_ATTR_AVG_NUM_MSGS			= "MB1093";
    final public static String I_DST_ATTR_AVG_TOTAL_MSG_BYTES		= "MB1094";
    final public static String I_DST_ATTR_CONNECTION_ID			= "MB1095";
    final public static String I_DST_ATTR_CREATED_BY_ADMIN		= "MB1096";
    final public static String I_DST_ATTR_DISK_RESERVED			= "MB1097";
    final public static String I_DST_ATTR_DISK_USED			= "MB1098";
    final public static String I_DST_ATTR_DISK_UTILIZATION_RATIO	= "MB1099";
    final public static String I_DST_ATTR_MSG_BYTES_IN			= "MB1100";
    final public static String I_DST_ATTR_MSG_BYTES_OUT			= "MB1101";
    final public static String I_DST_ATTR_NUM_ACTIVE_CONSUMERS		= "MB1102";
    final public static String I_DST_ATTR_NUM_BACKUP_CONSUMERS		= "MB1103";
    final public static String I_DST_ATTR_NUM_CONSUMERS			= "MB1104";
    final public static String I_DST_ATTR_NUM_MSGS			= "MB1105";
    final public static String I_DST_ATTR_NUM_MSGS_HELD_IN_TRANSACTION	= "MB1106";
    final public static String I_DST_ATTR_NUM_MSGS_IN			= "MB1107";
    final public static String I_DST_ATTR_NUM_MSGS_OUT			= "MB1108";
    final public static String I_DST_ATTR_NUM_MSGS_PENDING_ACKS		= "MB1109";
    final public static String I_DST_ATTR_NUM_PRODUCERS			= "MB1110";
    final public static String I_DST_ATTR_PEAK_MSG_BYTES		= "MB1111";
    final public static String I_DST_ATTR_PEAK_NUM_ACTIVE_CONSUMERS	= "MB1112";
    final public static String I_DST_ATTR_PEAK_NUM_BACKUP_CONSUMERS	= "MB1113";
    final public static String I_DST_ATTR_PEAK_NUM_CONSUMERS		= "MB1114";
    final public static String I_DST_ATTR_PEAK_NUM_MSGS			= "MB1115";
    final public static String I_DST_ATTR_PEAK_TOTAL_MSG_BYTES		= "MB1116";
    final public static String I_DST_ATTR_STATE				= "MB1117";
    final public static String I_DST_ATTR_STATE_LABEL			= "MB1118";
    final public static String I_DST_ATTR_TEMPORARY			= "MB1119";
    final public static String I_DST_ATTR_TOTAL_MSG_BYTES		= "MB1120";
    final public static String I_DST_ATTR_TOTAL_MSG_BYTES_HELD_IN_TRANSACTION= "MB1121";

    final public static String I_DST_OP_COMPACT				= "MB1122";
    final public static String I_DST_OP_PAUSE_ALL			= "MB1123";
    final public static String I_DST_OP_PAUSE				= "MB1124";
    final public static String I_DST_OP_PAUSE_PARAM_PAUSE_TYPE		= "MB1125";
    final public static String I_DST_OP_PURGE				= "MB1126";
    final public static String I_DST_OP_RESUME				= "MB1127";
    final public static String I_DST_OP_GET_ACTIVE_CONSUMER_IDS		= "MB1128";
    final public static String I_DST_OP_GET_BACKUP_CONSUMER_IDS		= "MB1129";
    final public static String I_DST_OP_GET_CONNECTION			= "MB1130";
    final public static String I_DST_OP_GET_CONSUMER_IDS		= "MB1131";
    final public static String I_DST_OP_GET_PRODUCER_IDS		= "MB1132";

    final public static String I_DST_NOTIFICATIONS			= "MB1133";
    final public static String I_DST_MON_DESC				= "MB1134";

    final public static String I_LOG_CFG_DESC				= "MB1135";
    final public static String I_LOG_ATTR_LEVEL				= "MB1136";
    final public static String I_LOG_ATTR_ROLL_OVER_BYTES		= "MB1137";
    final public static String I_LOG_ATTR_ROLL_OVER_SECS		= "MB1138";
    final public static String I_LOG_MON_DESC				= "MB1139";
    final public static String I_LOG_NOTIFICATIONS			= "MB1140";

    final public static String I_PRD_MGR_CFG_DESC			= "MB1141";
    final public static String I_PRD_MGR_ATTR_NUM_PRODUCERS		= "MB1142";
    final public static String I_PRD_MGR_OP_GET_PRODUCER_IDS		= "MB1143";
    final public static String I_PRD_MGR_OP_GET_PRODUCER_INFO		= "MB1144";
    final public static String I_PRD_MGR_OP_GET_PRODUCER_INFO_BY_ID	= "MB1145";
    final public static String I_PRD_MGR_OP_PARAM_PRD_ID		= "MB1146";
    final public static String I_PRD_MGR_MON_DESC			= "MB1147";

    final public static String I_JVM_MON_DESC				= "MB1148";
    final public static String I_JVM_ATTR_FREE_MEMORY			= "MB1149";
    final public static String I_JVM_ATTR_INIT_MEMORY			= "MB1150";
    final public static String I_JVM_ATTR_MAX_MEMORY			= "MB1151";
    final public static String I_JVM_ATTR_TOTAL_MEMORY			= "MB1152";

    final public static String I_TXN_MGR_CFG_DESC			= "MB1153";
    final public static String I_TXN_MGR_ATTR_NUM_TRANSACTIONS		= "MB1154";
    final public static String I_TXN_MGR_ATTR_NUM_TRANSACTIONS_COMMITTED= "MB1155";
    final public static String I_TXN_MGR_ATTR_NUM_TRANSACTIONS_ROLLBACK	= "MB1156";
    final public static String I_TXN_MGR_OP_COMMIT			= "MB1157";
    final public static String I_TXN_MGR_OP_GET_TRANSACTION_IDS		= "MB1158";
    final public static String I_TXN_MGR_OP_GET_TRANSACTION_INFO	= "MB1159";
    final public static String I_TXN_MGR_OP_GET_TRANSACTION_INFO_BY_ID	= "MB1160";
    final public static String I_TXN_MGR_OP_ROLLBACK			= "MB1161";
    final public static String I_TXN_MGR_OP_PARAM_TXN_ID		= "MB1162";
    final public static String I_TXN_MGR_MON_DESC			= "MB1163";
    final public static String I_TXN_NOTIFICATIONS			= "MB1164";

    final public static String I_DST_MGR_CFG_DESC			= "MB1165";
    final public static String I_DST_MGR_ATTR_AUTO_CREATE_QUEUES	= "MB1166";
    final public static String I_DST_MGR_ATTR_AUTO_CREATE_QUEUE_MAX_NUM_ACTIVE_CONSUMERS	
									= "MB1167";
    final public static String I_DST_MGR_ATTR_AUTO_CREATE_QUEUE_MAX_NUM_BACKUP_CONSUMERS	
									= "MB1168";
    final public static String I_DST_MGR_ATTR_AUTO_CREATE_TOPICS	= "MB1169";
    final public static String I_DST_MGR_ATTR_DMQ_TRUNCATE_BODY		= "MB1170";
    final public static String I_DST_MGR_ATTR_LOG_DEAD_MSGS		= "MB1171";
    final public static String I_DST_MGR_ATTR_MAX_BYTES_PER_MSG		= "MB1172";
    final public static String I_DST_MGR_ATTR_MAX_NUM_MSGS		= "MB1173";
    final public static String I_DST_MGR_ATTR_MAX_TOTAL_MSG_BYTES	= "MB1174";
    final public static String I_DST_MGR_ATTR_NUM_DESTINATIONS		= "MB1175";
    final public static String I_DST_MGR_ATTR_NUM_MSGS			= "MB1176";
    final public static String I_DST_MGR_ATTR_NUM_MSGS_IN_DMQ		= "MB1177";
    final public static String I_DST_MGR_ATTR_TOTAL_MSG_BYTES		= "MB1178";
    final public static String I_DST_MGR_ATTR_TOTAL_MSG_BYTES_IN_DMQ	= "MB1179";

    final public static String I_DST_MGR_OP_CREATE			= "MB1180";
    final public static String I_DST_MGR_OP_COMPACT			= "MB1181";
    final public static String I_DST_MGR_OP_DESTROY			= "MB1182";
    final public static String I_DST_MGR_CFG_OP_GET_DESTINATIONS	= "MB1183";
    final public static String I_DST_MGR_OP_PAUSE_ALL			= "MB1184";
    final public static String I_DST_MGR_OP_PAUSE			= "MB1185";
    final public static String I_DST_MGR_OP_RESUME			= "MB1186";
    final public static String I_DST_MGR_OP_PARAM_DEST_TYPE		= "MB1187";
    final public static String I_DST_MGR_OP_PARAM_DEST_NAME		= "MB1188";
    final public static String I_DST_MGR_OP_PARAM_DEST_ATTRS		= "MB1189";

    final public static String I_DST_MGR_MON_OP_GET_DESTINATIONS	= "MB1190";
    final public static String I_DST_MGR_MON_DESC			= "MB1191";

    final public static String I_SVC_CFG_DESC				= "MB1192";
    final public static String I_SVC_ATTR_MAX_THREADS			= "MB1193";
    final public static String I_SVC_ATTR_MIN_THREADS			= "MB1194";
    final public static String I_SVC_ATTR_NAME				= "MB1195";
    final public static String I_SVC_CFG_ATTR_PORT			= "MB1196";
    final public static String I_SVC_ATTR_THREAD_POOL_MODEL		= "MB1197";
    final public static String I_SVC_ATTR_MSG_BYTES_IN			= "MB1198";
    final public static String I_SVC_ATTR_MSG_BYTES_OUT			= "MB1199";
    final public static String I_SVC_ATTR_NUM_ACTIVE_THREADS		= "MB1200";
    final public static String I_SVC_ATTR_NUM_CONNECTIONS		= "MB1201";
    final public static String I_SVC_ATTR_NUM_CONNECTIONS_OPENED	= "MB1202";
    final public static String I_SVC_ATTR_NUM_CONNECTIONS_REJECTED	= "MB1203";
    final public static String I_SVC_ATTR_NUM_CONSUMERS			= "MB1204";
    final public static String I_SVC_ATTR_NUM_MSGS_IN			= "MB1205";
    final public static String I_SVC_ATTR_NUM_MSGS_OUT			= "MB1206";
    final public static String I_SVC_ATTR_NUM_PKTS_IN			= "MB1207";
    final public static String I_SVC_ATTR_NUM_PKTS_OUT			= "MB1208";
    final public static String I_SVC_ATTR_NUM_PRODUCERS			= "MB1209";
    final public static String I_SVC_ATTR_PKT_BYTES_IN			= "MB1210";
    final public static String I_SVC_ATTR_PKT_BYTES_OUT			= "MB1211";
    final public static String I_SVC_ATTR_STATE				= "MB1212";
    final public static String I_SVC_ATTR_STATE_LABEL			= "MB1213";

    final public static String I_SVC_OP_PAUSE				= "MB1214";
    final public static String I_SVC_OP_RESUME				= "MB1215";
    final public static String I_SVC_OP_GET_CONNECTIONS			= "MB1216";
    final public static String I_SVC_OP_GET_CONSUMER_IDS		= "MB1217";
    final public static String I_SVC_OP_GET_PRODUCER_IDS		= "MB1218";

    final public static String I_SVC_MON_DESC				= "MB1219";
    final public static String I_SVC_NOTIFICATIONS			= "MB1220";
    final public static String I_SVC_MON_ATTR_PORT			= "MB1221";

    final public static String I_SVC_MGR_CFG_DESC			= "MB1222";
    final public static String I_SVC_MGR_ATTR_MAX_THREADS		= "MB1223";
    final public static String I_SVC_MGR_ATTR_MIN_THREADS		= "MB1224";
    final public static String I_SVC_MGR_ATTR_MSG_BYTES_IN		= "MB1225";
    final public static String I_SVC_MGR_ATTR_MSG_BYTES_OUT		= "MB1226";
    final public static String I_SVC_MGR_ATTR_NUM_ACTIVE_THREADS	= "MB1227";
    final public static String I_SVC_MGR_ATTR_NUM_MSGS_IN		= "MB1228";
    final public static String I_SVC_MGR_ATTR_NUM_MSGS_OUT		= "MB1229";
    final public static String I_SVC_MGR_ATTR_NUM_PKTS_IN		= "MB1230";
    final public static String I_SVC_MGR_ATTR_NUM_PKTS_OUT		= "MB1231";
    final public static String I_SVC_MGR_ATTR_NUM_SERVICES		= "MB1232";
    final public static String I_SVC_MGR_ATTR_PKT_BYTES_IN		= "MB1233";
    final public static String I_SVC_MGR_ATTR_PKT_BYTES_OUT		= "MB1234";

    final public static String I_SVC_MGR_CFG_OP_GET_SERVICES		= "MB1235";
    final public static String I_SVC_MGR_OP_PAUSE			= "MB1236";
    final public static String I_SVC_MGR_OP_RESUME			= "MB1237";
    final public static String I_SVC_MGR_MON_OP_GET_SERVICES		= "MB1238";

    final public static String I_SVC_MGR_MON_DESC			= "MB1239";

    final public static String I_BKR_OP_GET_PROPERTY_DESC		= "MB1240";
    final public static String I_BKR_OP_GET_PROPERTY_PARAM_PROP_NAME_DESC
									= "MB1241";
    final public static String I_BKR_ATTR_RESOURCE_STATE		= "MB1242";

    final public static String I_DST_ATTR_NUM_WILDCARDS			= "MB1243";
    final public static String I_DST_ATTR_NUM_WILDCARD_CONSUMERS	= "MB1244";
    final public static String I_DST_ATTR_NUM_WILDCARD_PRODUCERS	= "MB1245";
    final public static String I_DST_OP_GET_WILDCARDS			= "MB1246";
    final public static String I_DST_OP_GET_CONSUMER_WILDCARDS		= "MB1247";
    final public static String I_DST_OP_GET_NUM_WILDCARD_CONSUMERS	= "MB1248";
    final public static String I_DST_OP_GET_PRODUCER_WILDCARDS		= "MB1249";
    final public static String I_DST_OP_GET_NUM_WILDCARD_PRODUCERS	= "MB1250";
    final public static String I_BKR_OP_WILDCARD_CONSUMERS_DESC		= "MB1251";
    final public static String I_BKR_OP_WILDCARD_PRODUCERS_DESC		= "MB1252";
    final public static String I_CON_MGR_ATTR_NUM_WILDCARD_CONSUMERS	= "MB1253";
    final public static String I_CON_MGR_OP_GET_CONSUMER_WILDCARDS	= "MB1254";
    final public static String I_CON_MGR_OP_GET_NUM_WILDCARD_CONSUMERS	= "MB1255";
    final public static String I_PRD_MGR_ATTR_NUM_WILDCARD_PRODUCERS	= "MB1256";
    final public static String I_PRD_MGR_OP_GET_PRODUCER_WILDCARDS	= "MB1257";
    final public static String I_PRD_MGR_OP_GET_NUM_WILDCARD_PRODUCERS	= "MB1258";
    final public static String I_DST_ATTR_VALIDATE_XML_SCHEMA_ENABLED	= "MB1259";
    final public static String I_DST_ATTR_XML_SCHEMA_URI_LIST		= "MB1260";
    final public static String I_DST_ATTR_RELOAD_XML_SCHEMA_ON_FAILURE	= "MB1261";
    final public static String I_DST_ATTR_NUM_MSGS_REMOTE		= "MB1262";
    final public static String I_DST_ATTR_TOTAL_MSG_BYTES_REMOTE	= "MB1263";
    final public static String I_CON_ATTR_TOTAL_MSGS= "MB1264";
    final public static String I_CON_ATTR_NEXT_MESSAGE_ID= "MB1265";
    final public static String I_DST_ATTR_NEXT_MESSAGE_ID= "MB1266";
    final public static String I_CXN_ATTR_CXN_CREATION_TIME		= "MB1267";
    final public static String I_BKR_ATTR_HOST				= "MB1268";
    final public static String I_CLS_OP_CHANGE_MASTER_BROKER = "MB1269";
    final public static String I_CLS_OP_CHANGE_MASTER_BROKER_PARAM_OLDMASTERBROKER_DESC		= "MB1270";
    final public static String I_CLS_OP_CHANGE_MASTER_BROKER_PARAM_NEWMASTERBROKER_DESC		= "MB1271";
    final public static String I_CLS_ATTR_USE_SHARED_DATABASE_FOR_CONFIG_RECORD_DESC        = "MB1272";
    final public static String I_DST_ATTR_NUM_MSGS_IN_DELAY_DELIVERY  = "MB1273";
    
    final public static String I_LOG_ATTR_LOG_DIRECTORY		= "MB1274";
    final public static String I_LOG_ATTR_LOG_FILE_NAME		= "MB1275";

    // 2000-2999 Warning Messages
    final public static String W_DUMMY	 		= "MB2000";

    // 3000-3999 Error Messages
    final public static String E_DUMMY 			= "MB3000";

    // 4000-4999 Exception Messages
    final public static String X_DUMMY 			= "MB4000";

    /***************** End of message key constants *******************/

}
