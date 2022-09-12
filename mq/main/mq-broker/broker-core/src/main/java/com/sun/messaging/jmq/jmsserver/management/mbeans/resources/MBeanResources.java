/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 Contributors to Eclipse Foundation. All rights reserved.
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
 * This class wraps a PropertyResourceBundle, and provides constants to use as message keys. The reason we use constants
 * for the message keys is to provide some compile time checking when the key is used in the source.
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
            ResourceBundle prb = ResourceBundle.getBundle("com.sun.messaging.jmq.jmsserver.management.mbeans.resources.MBeanResources", locale);
            resources = new MBeanResources(prb);
        }
        return resources;
    }

    private MBeanResources(ResourceBundle rb) {
        super(rb);
    }

    /*****************
     * Start of message key constants ******************* We use numeric values as the keys because the Broker has a
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
    public static final String M_DUMMY = "MB0000";

    // 1000-1999 Informational Messages
    public static final String I_CLS_CFG_DESC = "MB1000";
    public static final String I_CLS_ATTR_BROKER_ID_DESC = "MB1001";
    public static final String I_CLS_ATTR_CONFIG_FILE_URL_DESC = "MB1002";
    public static final String I_CLS_ATTR_CLUSTER_ID_DESC = "MB1003";
    public static final String I_CLS_ATTR_HIGHLY_AVAILABLE_DESC = "MB1004";
    public static final String I_CLS_ATTR_LOCAL_BROKER_INFO_DESC = "MB1005";
    public static final String I_CLS_ATTR_MASTER_BROKER_INFO_DESC = "MB1006";
    public static final String I_CLS_CFG_OP_GET_BROKER_ADDRESSES_DESC = "MB1007";
    public static final String I_CLS_CFG_OP_GET_BROKER_IDS_DESC = "MB1008";
    public static final String I_CLS_CFG_OP_GET_BROKER_INFO_DESC = "MB1009";
    public static final String I_CLS_OP_GET_BROKER_INFO_BY_ADDRESS_DESC = "MB1010";
    public static final String I_CLS_OP_GET_BROKER_INFO_BY_ADDRESS_PARAM_ADDR_DESC = "MB1011";
    public static final String I_CLS_OP_GET_BROKER_INFO_BY_ID_DESC = "MB1012";
    public static final String I_CLS_OP_GET_BROKER_INFO_BY_ID_PARAM_ID_DESC = "MB1013";
    public static final String I_CLS_CFG_OP_RELOAD_DESC = "MB1014";

    public static final String I_CLS_MON_DESC = "MB1020";
    public static final String I_CLS_MON_OP_GET_BROKER_ADDRESSES_DESC = "MB1021";
    public static final String I_CLS_MON_OP_GET_BROKER_IDS_DESC = "MB1022";
    public static final String I_CLS_MON_OP_GET_BROKER_INFO_DESC = "MB1023";
    public static final String I_CLS_NOTIFICATIONS = "MB1024";

    public static final String I_ATTR_CHANGE_NOTIFICATION = "MB1025";

    public static final String I_BKR_NOTIFICATIONS = "MB1026";
    public static final String I_BKR_CFG_DESC = "MB1027";
    public static final String I_BKR_ATTR_BKR_ID = "MB1028";
    public static final String I_BKR_ATTR_EMBEDDED = "MB1029";
    public static final String I_BKR_ATTR_INSTANCE_NAME = "MB1030";
    public static final String I_BKR_ATTR_PORT = "MB1031";
    public static final String I_BKR_ATTR_VERSION = "MB1032";

    public static final String I_BKR_OP_QUIESCE_DESC = "MB1033";
    public static final String I_BKR_OP_RESET_METRICS_DESC = "MB1034";
    public static final String I_BKR_OP_RESTART_DESC = "MB1035";
    public static final String I_BKR_OP_SHUTDOWN_DESC = "MB1036";
    public static final String I_BKR_OP_TAKEOVER_DESC = "MB1037";
    public static final String I_BKR_OP_UNQUIESCE_DESC = "MB1038";

    public static final String I_BKR_OP_SHUTDOWN_PARAM_NO_FAILOVER_DESC = "MB1039";
    public static final String I_BKR_OP_SHUTDOWN_PARAM_TIME_DESC = "MB1040";
    public static final String I_BKR_OP_TAKEOVER_PARAM_BROKER_ID_DESC = "MB1041";

    public static final String I_BKR_MON_DESC = "MB1042";

    public static final String I_CXN_CFG_DESC = "MB1043";

    public static final String I_CXN_ATTR_CLIENT_ID = "MB1044";
    public static final String I_CXN_ATTR_CLIENT_PLATFORM = "MB1045";
    public static final String I_CXN_ATTR_CXN_ID = "MB1046";
    public static final String I_CXN_ATTR_HOST = "MB1047";
    public static final String I_CXN_ATTR_NUM_CONSUMERS = "MB1048";
    public static final String I_CXN_ATTR_NUM_PRODUCERS = "MB1049";
    public static final String I_CXN_ATTR_PORT = "MB1050";
    public static final String I_CXN_ATTR_SERVICE_NAME = "MB1051";
    public static final String I_CXN_ATTR_USER = "MB1052";

    public static final String I_CXN_OP_GET_CONSUMER_IDS_DESC = "MB1053";
    public static final String I_CXN_OP_GET_PRODUCER_IDS_DESC = "MB1054";
    public static final String I_CXN_OP_GET_SERVICE_DESC = "MB1055";
    public static final String I_CXN_OP_GET_TEMP_DESTINATIONS_DESC = "MB1056";

    public static final String I_CXN_MON_DESC = "MB1057";

    public static final String I_CXN_MGR_CFG_DESC = "MB1058";
    public static final String I_CXN_MGR_ATTR_NUM_CONNECTIONS = "MB1059";
    public static final String I_CXN_MGR_ATTR_NUM_CONNECTIONS_OPENED = "MB1060";
    public static final String I_CXN_MGR_ATTR_NUM_CONNECTIONS_REJECTED = "MB1061";

    public static final String I_CXN_MGR_OP_DESTROY_DESC = "MB1062";
    public static final String I_CXN_MGR_CFG_OP_GET_CONNECTIONS_DESC = "MB1063";
    public static final String I_CXN_MGR_MON_OP_GET_CONNECTIONS_DESC = "MB1064";
    public static final String I_CXN_MGR_OP_DESTROY_PARAM_CXN_ID_DESC = "MB1065";

    public static final String I_CXN_MGR_MON_DESC = "MB1066";
    public static final String I_CXN_NOTIFICATIONS = "MB1067";

    public static final String I_CON_MGR_CFG_DESC = "MB1068";
    public static final String I_CON_MGR_ATTR_NUM_CONSUMERS = "MB1069";

    public static final String I_CON_MGR_OP_GET_CONSUMER_IDS_DESC = "MB1070";
    public static final String I_CON_MGR_OP_GET_CONSUMER_INFO_DESC = "MB1071";
    public static final String I_CON_MGR_OP_GET_CONSUMER_INFO_BY_ID_DESC = "MB1072";
    public static final String I_CON_MGR_OP_PURGE_DESC = "MB1073";
    public static final String I_CON_MGR_OP_PARAM_CON_ID_DESC = "MB1074";

    public static final String I_CON_MGR_MON_DESC = "MB1075";

    public static final String I_DST_CFG_DESC = "MB1076";
    public static final String I_DST_ATTR_CONSUMER_FLOW_LIMIT = "MB1077";
    public static final String I_DST_ATTR_LOCAL_ONLY = "MB1078";
    public static final String I_DST_ATTR_LIMIT_BEHAVIOR = "MB1079";
    public static final String I_DST_ATTR_LOCAL_DELIVERY_PREFERRED = "MB1080";
    public static final String I_DST_ATTR_MAX_BYTES_PER_MSG = "MB1081";
    public static final String I_DST_ATTR_MAX_NUM_ACTIVE_CONSUMERS = "MB1082";
    public static final String I_DST_ATTR_MAX_NUM_BACKUP_CONSUMERS = "MB1083";
    public static final String I_DST_ATTR_MAX_NUM_MSGS = "MB1084";
    public static final String I_DST_ATTR_MAX_NUM_PRODUCERS = "MB1085";
    public static final String I_DST_ATTR_MAX_TOTAL_MSG_BYTES = "MB1086";
    public static final String I_DST_ATTR_NAME = "MB1087";
    public static final String I_DST_ATTR_TYPE = "MB1088";
    public static final String I_DST_ATTR_USE_DMQ = "MB1089";
    public static final String I_DST_ATTR_AVG_NUM_ACTIVE_CONSUMERS = "MB1090";
    public static final String I_DST_ATTR_AVG_NUM_BACKUP_CONSUMERS = "MB1091";
    public static final String I_DST_ATTR_AVG_NUM_CONSUMERS = "MB1092";
    public static final String I_DST_ATTR_AVG_NUM_MSGS = "MB1093";
    public static final String I_DST_ATTR_AVG_TOTAL_MSG_BYTES = "MB1094";
    public static final String I_DST_ATTR_CONNECTION_ID = "MB1095";
    public static final String I_DST_ATTR_CREATED_BY_ADMIN = "MB1096";
    public static final String I_DST_ATTR_DISK_RESERVED = "MB1097";
    public static final String I_DST_ATTR_DISK_USED = "MB1098";
    public static final String I_DST_ATTR_DISK_UTILIZATION_RATIO = "MB1099";
    public static final String I_DST_ATTR_MSG_BYTES_IN = "MB1100";
    public static final String I_DST_ATTR_MSG_BYTES_OUT = "MB1101";
    public static final String I_DST_ATTR_NUM_ACTIVE_CONSUMERS = "MB1102";
    public static final String I_DST_ATTR_NUM_BACKUP_CONSUMERS = "MB1103";
    public static final String I_DST_ATTR_NUM_CONSUMERS = "MB1104";
    public static final String I_DST_ATTR_NUM_MSGS = "MB1105";
    public static final String I_DST_ATTR_NUM_MSGS_HELD_IN_TRANSACTION = "MB1106";
    public static final String I_DST_ATTR_NUM_MSGS_IN = "MB1107";
    public static final String I_DST_ATTR_NUM_MSGS_OUT = "MB1108";
    public static final String I_DST_ATTR_NUM_MSGS_PENDING_ACKS = "MB1109";
    public static final String I_DST_ATTR_NUM_PRODUCERS = "MB1110";
    public static final String I_DST_ATTR_PEAK_MSG_BYTES = "MB1111";
    public static final String I_DST_ATTR_PEAK_NUM_ACTIVE_CONSUMERS = "MB1112";
    public static final String I_DST_ATTR_PEAK_NUM_BACKUP_CONSUMERS = "MB1113";
    public static final String I_DST_ATTR_PEAK_NUM_CONSUMERS = "MB1114";
    public static final String I_DST_ATTR_PEAK_NUM_MSGS = "MB1115";
    public static final String I_DST_ATTR_PEAK_TOTAL_MSG_BYTES = "MB1116";
    public static final String I_DST_ATTR_STATE = "MB1117";
    public static final String I_DST_ATTR_STATE_LABEL = "MB1118";
    public static final String I_DST_ATTR_TEMPORARY = "MB1119";
    public static final String I_DST_ATTR_TOTAL_MSG_BYTES = "MB1120";
    public static final String I_DST_ATTR_TOTAL_MSG_BYTES_HELD_IN_TRANSACTION = "MB1121";

    public static final String I_DST_OP_COMPACT = "MB1122";
    public static final String I_DST_OP_PAUSE_ALL = "MB1123";
    public static final String I_DST_OP_PAUSE = "MB1124";
    public static final String I_DST_OP_PAUSE_PARAM_PAUSE_TYPE = "MB1125";
    public static final String I_DST_OP_PURGE = "MB1126";
    public static final String I_DST_OP_RESUME = "MB1127";
    public static final String I_DST_OP_GET_ACTIVE_CONSUMER_IDS = "MB1128";
    public static final String I_DST_OP_GET_BACKUP_CONSUMER_IDS = "MB1129";
    public static final String I_DST_OP_GET_CONNECTION = "MB1130";
    public static final String I_DST_OP_GET_CONSUMER_IDS = "MB1131";
    public static final String I_DST_OP_GET_PRODUCER_IDS = "MB1132";

    public static final String I_DST_NOTIFICATIONS = "MB1133";
    public static final String I_DST_MON_DESC = "MB1134";

    public static final String I_LOG_CFG_DESC = "MB1135";
    public static final String I_LOG_ATTR_LEVEL = "MB1136";
    public static final String I_LOG_ATTR_ROLL_OVER_BYTES = "MB1137";
    public static final String I_LOG_ATTR_ROLL_OVER_SECS = "MB1138";
    public static final String I_LOG_MON_DESC = "MB1139";
    public static final String I_LOG_NOTIFICATIONS = "MB1140";

    public static final String I_PRD_MGR_CFG_DESC = "MB1141";
    public static final String I_PRD_MGR_ATTR_NUM_PRODUCERS = "MB1142";
    public static final String I_PRD_MGR_OP_GET_PRODUCER_IDS = "MB1143";
    public static final String I_PRD_MGR_OP_GET_PRODUCER_INFO = "MB1144";
    public static final String I_PRD_MGR_OP_GET_PRODUCER_INFO_BY_ID = "MB1145";
    public static final String I_PRD_MGR_OP_PARAM_PRD_ID = "MB1146";
    public static final String I_PRD_MGR_MON_DESC = "MB1147";

    public static final String I_JVM_MON_DESC = "MB1148";
    public static final String I_JVM_ATTR_FREE_MEMORY = "MB1149";
    public static final String I_JVM_ATTR_INIT_MEMORY = "MB1150";
    public static final String I_JVM_ATTR_MAX_MEMORY = "MB1151";
    public static final String I_JVM_ATTR_TOTAL_MEMORY = "MB1152";

    public static final String I_TXN_MGR_CFG_DESC = "MB1153";
    public static final String I_TXN_MGR_ATTR_NUM_TRANSACTIONS = "MB1154";
    public static final String I_TXN_MGR_ATTR_NUM_TRANSACTIONS_COMMITTED = "MB1155";
    public static final String I_TXN_MGR_ATTR_NUM_TRANSACTIONS_ROLLBACK = "MB1156";
    public static final String I_TXN_MGR_OP_COMMIT = "MB1157";
    public static final String I_TXN_MGR_OP_GET_TRANSACTION_IDS = "MB1158";
    public static final String I_TXN_MGR_OP_GET_TRANSACTION_INFO = "MB1159";
    public static final String I_TXN_MGR_OP_GET_TRANSACTION_INFO_BY_ID = "MB1160";
    public static final String I_TXN_MGR_OP_ROLLBACK = "MB1161";
    public static final String I_TXN_MGR_OP_PARAM_TXN_ID = "MB1162";
    public static final String I_TXN_MGR_MON_DESC = "MB1163";
    public static final String I_TXN_NOTIFICATIONS = "MB1164";

    public static final String I_DST_MGR_CFG_DESC = "MB1165";
    public static final String I_DST_MGR_ATTR_AUTO_CREATE_QUEUES = "MB1166";
    public static final String I_DST_MGR_ATTR_AUTO_CREATE_QUEUE_MAX_NUM_ACTIVE_CONSUMERS = "MB1167";
    public static final String I_DST_MGR_ATTR_AUTO_CREATE_QUEUE_MAX_NUM_BACKUP_CONSUMERS = "MB1168";
    public static final String I_DST_MGR_ATTR_AUTO_CREATE_TOPICS = "MB1169";
    public static final String I_DST_MGR_ATTR_DMQ_TRUNCATE_BODY = "MB1170";
    public static final String I_DST_MGR_ATTR_LOG_DEAD_MSGS = "MB1171";
    public static final String I_DST_MGR_ATTR_MAX_BYTES_PER_MSG = "MB1172";
    public static final String I_DST_MGR_ATTR_MAX_NUM_MSGS = "MB1173";
    public static final String I_DST_MGR_ATTR_MAX_TOTAL_MSG_BYTES = "MB1174";
    public static final String I_DST_MGR_ATTR_NUM_DESTINATIONS = "MB1175";
    public static final String I_DST_MGR_ATTR_NUM_MSGS = "MB1176";
    public static final String I_DST_MGR_ATTR_NUM_MSGS_IN_DMQ = "MB1177";
    public static final String I_DST_MGR_ATTR_TOTAL_MSG_BYTES = "MB1178";
    public static final String I_DST_MGR_ATTR_TOTAL_MSG_BYTES_IN_DMQ = "MB1179";

    public static final String I_DST_MGR_OP_CREATE = "MB1180";
    public static final String I_DST_MGR_OP_COMPACT = "MB1181";
    public static final String I_DST_MGR_OP_DESTROY = "MB1182";
    public static final String I_DST_MGR_CFG_OP_GET_DESTINATIONS = "MB1183";
    public static final String I_DST_MGR_OP_PAUSE_ALL = "MB1184";
    public static final String I_DST_MGR_OP_PAUSE = "MB1185";
    public static final String I_DST_MGR_OP_RESUME = "MB1186";
    public static final String I_DST_MGR_OP_PARAM_DEST_TYPE = "MB1187";
    public static final String I_DST_MGR_OP_PARAM_DEST_NAME = "MB1188";
    public static final String I_DST_MGR_OP_PARAM_DEST_ATTRS = "MB1189";

    public static final String I_DST_MGR_MON_OP_GET_DESTINATIONS = "MB1190";
    public static final String I_DST_MGR_MON_DESC = "MB1191";

    public static final String I_SVC_CFG_DESC = "MB1192";
    public static final String I_SVC_ATTR_MAX_THREADS = "MB1193";
    public static final String I_SVC_ATTR_MIN_THREADS = "MB1194";
    public static final String I_SVC_ATTR_NAME = "MB1195";
    public static final String I_SVC_CFG_ATTR_PORT = "MB1196";
    public static final String I_SVC_ATTR_THREAD_POOL_MODEL = "MB1197";
    public static final String I_SVC_ATTR_MSG_BYTES_IN = "MB1198";
    public static final String I_SVC_ATTR_MSG_BYTES_OUT = "MB1199";
    public static final String I_SVC_ATTR_NUM_ACTIVE_THREADS = "MB1200";
    public static final String I_SVC_ATTR_NUM_CONNECTIONS = "MB1201";
    public static final String I_SVC_ATTR_NUM_CONNECTIONS_OPENED = "MB1202";
    public static final String I_SVC_ATTR_NUM_CONNECTIONS_REJECTED = "MB1203";
    public static final String I_SVC_ATTR_NUM_CONSUMERS = "MB1204";
    public static final String I_SVC_ATTR_NUM_MSGS_IN = "MB1205";
    public static final String I_SVC_ATTR_NUM_MSGS_OUT = "MB1206";
    public static final String I_SVC_ATTR_NUM_PKTS_IN = "MB1207";
    public static final String I_SVC_ATTR_NUM_PKTS_OUT = "MB1208";
    public static final String I_SVC_ATTR_NUM_PRODUCERS = "MB1209";
    public static final String I_SVC_ATTR_PKT_BYTES_IN = "MB1210";
    public static final String I_SVC_ATTR_PKT_BYTES_OUT = "MB1211";
    public static final String I_SVC_ATTR_STATE = "MB1212";
    public static final String I_SVC_ATTR_STATE_LABEL = "MB1213";

    public static final String I_SVC_OP_PAUSE = "MB1214";
    public static final String I_SVC_OP_RESUME = "MB1215";
    public static final String I_SVC_OP_GET_CONNECTIONS = "MB1216";
    public static final String I_SVC_OP_GET_CONSUMER_IDS = "MB1217";
    public static final String I_SVC_OP_GET_PRODUCER_IDS = "MB1218";

    public static final String I_SVC_MON_DESC = "MB1219";
    public static final String I_SVC_NOTIFICATIONS = "MB1220";
    public static final String I_SVC_MON_ATTR_PORT = "MB1221";

    public static final String I_SVC_MGR_CFG_DESC = "MB1222";
    public static final String I_SVC_MGR_ATTR_MAX_THREADS = "MB1223";
    public static final String I_SVC_MGR_ATTR_MIN_THREADS = "MB1224";
    public static final String I_SVC_MGR_ATTR_MSG_BYTES_IN = "MB1225";
    public static final String I_SVC_MGR_ATTR_MSG_BYTES_OUT = "MB1226";
    public static final String I_SVC_MGR_ATTR_NUM_ACTIVE_THREADS = "MB1227";
    public static final String I_SVC_MGR_ATTR_NUM_MSGS_IN = "MB1228";
    public static final String I_SVC_MGR_ATTR_NUM_MSGS_OUT = "MB1229";
    public static final String I_SVC_MGR_ATTR_NUM_PKTS_IN = "MB1230";
    public static final String I_SVC_MGR_ATTR_NUM_PKTS_OUT = "MB1231";
    public static final String I_SVC_MGR_ATTR_NUM_SERVICES = "MB1232";
    public static final String I_SVC_MGR_ATTR_PKT_BYTES_IN = "MB1233";
    public static final String I_SVC_MGR_ATTR_PKT_BYTES_OUT = "MB1234";

    public static final String I_SVC_MGR_CFG_OP_GET_SERVICES = "MB1235";
    public static final String I_SVC_MGR_OP_PAUSE = "MB1236";
    public static final String I_SVC_MGR_OP_RESUME = "MB1237";
    public static final String I_SVC_MGR_MON_OP_GET_SERVICES = "MB1238";

    public static final String I_SVC_MGR_MON_DESC = "MB1239";

    public static final String I_BKR_OP_GET_PROPERTY_DESC = "MB1240";
    public static final String I_BKR_OP_GET_PROPERTY_PARAM_PROP_NAME_DESC = "MB1241";
    public static final String I_BKR_ATTR_RESOURCE_STATE = "MB1242";

    public static final String I_DST_ATTR_NUM_WILDCARDS = "MB1243";
    public static final String I_DST_ATTR_NUM_WILDCARD_CONSUMERS = "MB1244";
    public static final String I_DST_ATTR_NUM_WILDCARD_PRODUCERS = "MB1245";
    public static final String I_DST_OP_GET_WILDCARDS = "MB1246";
    public static final String I_DST_OP_GET_CONSUMER_WILDCARDS = "MB1247";
    public static final String I_DST_OP_GET_NUM_WILDCARD_CONSUMERS = "MB1248";
    public static final String I_DST_OP_GET_PRODUCER_WILDCARDS = "MB1249";
    public static final String I_DST_OP_GET_NUM_WILDCARD_PRODUCERS = "MB1250";
    public static final String I_BKR_OP_WILDCARD_CONSUMERS_DESC = "MB1251";
    public static final String I_BKR_OP_WILDCARD_PRODUCERS_DESC = "MB1252";
    public static final String I_CON_MGR_ATTR_NUM_WILDCARD_CONSUMERS = "MB1253";
    public static final String I_CON_MGR_OP_GET_CONSUMER_WILDCARDS = "MB1254";
    public static final String I_CON_MGR_OP_GET_NUM_WILDCARD_CONSUMERS = "MB1255";
    public static final String I_PRD_MGR_ATTR_NUM_WILDCARD_PRODUCERS = "MB1256";
    public static final String I_PRD_MGR_OP_GET_PRODUCER_WILDCARDS = "MB1257";
    public static final String I_PRD_MGR_OP_GET_NUM_WILDCARD_PRODUCERS = "MB1258";
    public static final String I_DST_ATTR_VALIDATE_XML_SCHEMA_ENABLED = "MB1259";
    public static final String I_DST_ATTR_XML_SCHEMA_URI_LIST = "MB1260";
    public static final String I_DST_ATTR_RELOAD_XML_SCHEMA_ON_FAILURE = "MB1261";
    public static final String I_DST_ATTR_NUM_MSGS_REMOTE = "MB1262";
    public static final String I_DST_ATTR_TOTAL_MSG_BYTES_REMOTE = "MB1263";
    public static final String I_CON_ATTR_TOTAL_MSGS = "MB1264";
    public static final String I_CON_ATTR_NEXT_MESSAGE_ID = "MB1265";
    public static final String I_DST_ATTR_NEXT_MESSAGE_ID = "MB1266";
    public static final String I_CXN_ATTR_CXN_CREATION_TIME = "MB1267";
    public static final String I_BKR_ATTR_HOST = "MB1268";
    public static final String I_CLS_OP_CHANGE_MASTER_BROKER = "MB1269";
    public static final String I_CLS_OP_CHANGE_MASTER_BROKER_PARAM_OLDMASTERBROKER_DESC = "MB1270";
    public static final String I_CLS_OP_CHANGE_MASTER_BROKER_PARAM_NEWMASTERBROKER_DESC = "MB1271";
    public static final String I_CLS_ATTR_USE_SHARED_DATABASE_FOR_CONFIG_RECORD_DESC = "MB1272";
    public static final String I_DST_ATTR_NUM_MSGS_IN_DELAY_DELIVERY = "MB1273";

    public static final String I_LOG_ATTR_LOG_DIRECTORY = "MB1274";
    public static final String I_LOG_ATTR_LOG_FILE_NAME = "MB1275";

    // 2000-2999 Warning Messages
    public static final String W_DUMMY = "MB2000";

    // 3000-3999 Error Messages
    public static final String E_DUMMY = "MB3000";

    // 4000-4999 Exception Messages
    public static final String X_DUMMY = "MB4000";

    /***************** End of message key constants *******************/

}
