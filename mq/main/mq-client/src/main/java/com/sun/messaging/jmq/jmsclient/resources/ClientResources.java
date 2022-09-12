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
 * @(#)ClientResources.java	1.93 06/27/07
 */

package com.sun.messaging.jmq.jmsclient.resources;

import java.util.ResourceBundle;
import java.util.Locale;
import com.sun.messaging.jmq.util.MQResourceBundle;

/**
 * This class wraps a PropertyResourceBundle, and provides constants to use as message keys. The reason we use constants
 * for the message keys is to provide some compile time checking when the key is used in the source.
 */

public class ClientResources extends MQResourceBundle {

    public static final String CLIENT_RESOURCE_BUNDLE_NAME = "com.sun.messaging.jmq.jmsclient.resources.ClientResources";

    private static ClientResources resources = null;

    public static ClientResources getResources() {
        return getResources(null);
    }

    public static synchronized ClientResources getResources(Locale locale) {

        if (locale == null) {
            locale = Locale.getDefault();
        }

        if (resources == null || !locale.equals(resources.getLocale())) {
            ResourceBundle prb = ResourceBundle.getBundle(CLIENT_RESOURCE_BUNDLE_NAME, locale);
            resources = new ClientResources(prb);
        }

        return resources;
    }

    private ClientResources(ResourceBundle rb) {
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
    // 500- Labels for AdministeredObject configurables - ConnectionFactories
    public static final String L_JMQCONNECTION_TYPE = "L0500";
    public static final String L_JMQCONNECTION_HANDLER_CLASSNAME = "L0501";
    public static final String L_JMQBROKER_HOST_NAME = "L0502";
    public static final String L_JMQBROKER_HOST_PORT = "L0503";
    public static final String L_JMQSSL_PROVIDER_CLASSNAME = "L0504";
    public static final String L_JMQSSL_IS_HOST_TRUSTED = "L0505";
    public static final String L_JMQHTTP_URL = "L0506";

    public static final String L_JMQACK_TIMEOUT = "L0507";
    public static final String L_JMQRECONNECT = "L0508";
    public static final String L_JMQRECONNECT_DELAY = "L0509";
    public static final String L_JMQRECONNECT_RETRIES = "L0510";
    public static final String L_JMQDEFAULT_USERNAME = "L0511";
    public static final String L_JMQDEFAULT_PASSWORD = "L0512";
    public static final String L_JMQDISABLE_SETCLIENTID = "L0513";
    public static final String L_JMQCONFIGURED_CLIENTID = "L0514";
    public static final String L_JMQSET_JMSXAPPID = "L0515";
    public static final String L_JMQSET_JMSXUSERID = "L0516";
    public static final String L_JMQSET_JMSXPRODUCERTXID = "L0517";
    public static final String L_JMQSET_JMSXCONSUMERTXID = "L0518";
    public static final String L_JMQSET_JMSXRCVTIMESTAMP = "L0519";
    public static final String L_JMQACK_ON_PRODUCE = "L0520";
    public static final String L_JMQACK_ON_ACKNOWLEDGE = "L0521";
    public static final String L_JMQFLOWCONTROL_COUNT = "L0522";
    public static final String L_JMQFLOWCONTROL_ISLIMITED = "L0523";
    public static final String L_JMQFLOWCONTROL_LIMIT = "L0524";
    public static final String L_JMQQBROWSERRETR_TIMEOUT = "L0525";
    public static final String L_JMQQBROWSERMAXMSGS_PERRETR = "L0526";
    public static final String L_JMQLOAD_MAX_TO_SERVERSESSION = "L0527";
    public static final String L_JMQBROKER_SERVICE_NAME = "L0528";
    public static final String L_JMQBROKER_SERVICE_PORT = "L0529";
    public static final String L_JMQMESSAGE_SERVER_ADDRESS = "L0531";
    public static final String L_JMQDEFAULT_ADMIN_USERNAME = "L0532";
    public static final String L_JMQDEFAULT_ADMIN_PASSWORD = "L0533";
    public static final String L_JMQASYNCSEND_COMPLETION_WAIT_TIMEOUT = "L0536";

    // 550- Labels for AdministeredObject configurables - Message Hdr Overrides
    public static final String L_JMQOVERRIDEJMSDELIVERYMODE = "L0550";
    public static final String L_JMQJMSDELIVERYMODE = "L0551";
    public static final String L_JMQOVERRIDEJMSEXPIRATION = "L0552";
    public static final String L_JMQJMSEXPIRATION = "L0553";
    public static final String L_JMQOVERRIDEJMSPRIORITY = "L0554";
    public static final String L_JMQJMSPRIORITY = "L0555";
    public static final String L_JMQOVERRIDETEMPDESTS = "L0556";

    // 560
    public static final String L_CONSUMER_FLOWLIMIT = "L0560";
    public static final String L_CONSUMER_FLOWTHRESHOLD = "L0561";
    public static final String L_CONSUMER_FLOWLIMIT_PREFETCH = "L0563";
    public static final String L_ONMESSAGE_EX_REDELIVER_ATTEMPTS = "L0566";
    public static final String L_ONMESSAGE_EX_REDELIVER_INTERVALS = "L0567";

    // 570- Labels for AdministeredObject configurables - Destination
    public static final String L_JMQDESINTATION_NAME = "L0570";
    public static final String L_JMQDESINTATION_DESC = "L0571";

    // 575- Labels for AdministeredObject configurables - Endpoint
    public static final String L_JMQENDPOINT_NAME = "L0575";
    public static final String L_JMQENDPOINT_DESC = "L0576";
    public static final String L_JMQSOAPENDPOINT_LIST = "L0577";

    // 590- Groups for AdministeredObject configurables
    public static final String L_GROUP1 = "L0599";
    public static final String L_GROUP2 = "L0598";
    public static final String L_GROUP3 = "L0597";
    public static final String L_GROUP4 = "L0596";
    public static final String L_GROUP5 = "L0595";
    public static final String L_GROUP6 = "L0594";

    // 950- Common 'labels'
    public static final String L_QUEUE = "L0950";
    public static final String L_TOPIC = "L0951";

    // 1000-1999 Informational Messages

    // 2000-2999 Warning Messages
    public static final String W_UNKNOWN_PACKET = "W2000";
    public static final String W_PACKET_NOT_PROCESSED = "W2001";
    public static final String W_WARNING = "W2002";
    public static final String W_WAITING_FOR_RESPONSE = "W2003";
    public static final String W_RM_CONSUMER_EVENT_LISTENER = "W2004";
    public static final String W_REDELIVERY_ATTEMPTS_LIMIT = "W2010";
    public static final String W_MOVETO_DMQ_FAILED = "W2011";
    public static final String W_WEBSOCKET_CLOSE_FAILED = "W2012";

    // 3000-3999 Error Messages

    // 4000-4999 Exception Messages
    public static final String X_NET_ACK = "C4000";
    public static final String X_NET_WRITE_PACKET = "C4001";
    public static final String X_NET_READ_PACKET = "C4002";
    public static final String X_NET_CREATE_CONNECTION = "C4003";
    public static final String X_NET_CLOSE_CONNECTION = "C4004";
    public static final String X_PACKET_GET_PROPERTIES = "C4005";
    public static final String X_PACKET_SET_PROPERTIES = "C4006";
    public static final String X_DURABLE_INUSE = "C4007";
    public static final String X_MESSAGE_READ_ONLY = "C4008";
    public static final String X_MESSAGE_WRITE_ONLY = "C4009";
    public static final String X_MESSAGE_READ = "C4010";
    public static final String X_MESSAGE_WRITE = "C4011";
    public static final String X_MESSAGE_RESET = "C4012";
    public static final String X_MESSAGE_READ_EOF = "C4013";
    public static final String X_MESSAGE_SERIALIZE = "C4014";
    public static final String X_MESSAGE_DESERIALIZE = "C4015";
    public static final String X_MESSAGE_ACK = "C4016";
    public static final String X_MESSAGE_FORMAT = "C4017";
    public static final String X_MESSAGE_REDELIVER = "C4018";
    public static final String X_DESTINATION_NOTFOUND = "C4019";
    public static final String X_TEMP_DESTINATION_INVALID = "C4020";
    public static final String X_CONSUMER_NOTFOUND = "C4021";
    public static final String X_SELECTOR_INVALID = "C4022";
    public static final String X_CLIENT_ACK_LIMIT = "C4023";
    public static final String X_NON_TRANSACTED = "C4024";
    public static final String X_TRANSACTED = "C4025";
    public static final String X_COMMIT_LIMIT = "C4026";
    public static final String X_TRANSACTION_ID_INVALID = "C4027";
    public static final String X_TRANSACTION_ID_INUSE = "C4028";
    public static final String X_SVRSESSION_INVALID = "C4029";
    public static final String X_SVRSESSION_MAXMESSAGES = "C4030";
    public static final String X_SVRSESSION_MESSAGECONSUMER = "C4031";
    public static final String X_SYNC_ASYNC_RECEIVER = "C4032";
    public static final String X_AUTHTYPE_MISMATCH = "C4033";
    public static final String X_AUTHSTATE_ILLEGAL = "C4034";
    public static final String X_FORBIDDEN = "C4035";
    public static final String X_SERVER_ERROR = "C4036";
    public static final String X_SERVER_UNAVAILABLE = "C4037";
    public static final String X_CAUGHT_EXCEPTION = "C4038";
    public static final String X_DELETE_DESTINATION = "C4039";
    public static final String X_BAD_PROPERTY_OBJECT_TYPE = "C4040";
    public static final String X_PROPERTYNAME_RESERVED = "C4041";
    public static final String X_BAD_PROPERTY_STARTCHAR = "C4042";
    public static final String X_BAD_PROPERTY_PARTCHAR = "C4043";
    public static final String X_BROWSER_TIMEOUT = "C4044";
    public static final String X_BROWSER_END = "C4045";
    public static final String X_BROWSER_CLOSED = "C4046";
    public static final String X_INTERRUPTED = "C4047";
    public static final String X_SVRSESSION_INPROGRESS = "C4048";
    public static final String X_ILLEGAL_STATE = "C4049";
    public static final String X_INVALID_DESTINATION_NAME = "C4050";
    public static final String X_INVALID_DELIVERY_PARAM = "C4051";
    public static final String X_CLIENT_ID_INUSE = "C4052";
    public static final String X_INVALID_CLIENT_ID = "C4053";
    public static final String X_SET_CLIENT_ID = "C4054";
    public static final String X_CONFLICT = "C4055";
    public static final String X_BROKER_GOODBYE = "C4056";
    public static final String X_NO_USERNAME_PASSWORD = "C4057";
    public static final String X_CLIENT_ACKNOWLEDGE = "C4058";
    public static final String X_SESSION_CLOSED = "C4059";
    public static final String X_INVALID_LOGIN = "C4060";
    public static final String X_CONNECT_RECOVER = "C4061";
    public static final String X_CONNECTION_CLOSED = "C4062";
    public static final String X_CONSUMER_CLOSED = "C4063";
    public static final String X_PRODUCER_CLOSED = "C4064";
    public static final String X_VERSION_MISMATCH = "C4065";
    public static final String X_INVALID_DURABLE_NAME = "C4066";
    public static final String X_INVALID_ACKNOWLEDGE_MODE = "C4067";
    public static final String X_INVALID_DESTINATION_CLASS = "C4068";
    public static final String X_COMMIT_ROLLBACK_XASESSION = "C4069";
    public static final String X_ERROR_FOREIGN_CONVERSION = "C4070";
    public static final String X_ILLEGAL_METHOD_FOR_DOMAIN = "C4071";
    public static final String X_BAD_PROPERTY_NAME = "C4072";
    public static final String X_DESTINATION_CONSUMER_LIMIT_EXCEEDED = "C4073";
    public static final String X_TRANSACTION_FAILOVER_OCCURRED = "C4074";
    public static final String X_CLIENT_ACK_FAILOVER_OCCURRED = "C4075";
    public static final String X_ADD_PRODUCER_DENIED = "C4076";
    public static final String X_CREATE_DESTINATION_DENIED = "C4077";
    public static final String X_SEND_DENIED = "C4078";
    public static final String X_ADD_CONSUMER_DENIED = "C4079";
    public static final String X_DELETE_CONSUMER_DENIED = "C4080";
    public static final String X_UNSUBSCRIBE_DENIED = "C4081";
    public static final String X_VERIFY_DESTINATION_DENIED = "C4082";
    public static final String X_BROWSE_DESTINATION_DENIED = "C4083";
    public static final String X_AUTHENTICATE_DENIED = "C4084";
    public static final String X_DELETE_CONSUMER_NOTFOUND = "C4085";
    public static final String X_UNSUBSCRIBE_NOTFOUND = "C4086";
    public static final String X_SET_CLIENTID_INVALID = "C4087";
    public static final String X_DESTINATION_PRODUCER_LIMIT_EXCEEDED = "C4088";
    public static final String X_JVM_ERROR = "C4089";
    public static final String X_BROKER_PAUSED = "C4090";
    public static final String X_NO_ACKNOWLEDGE_RECOVER = "C4091";
    public static final String X_BROKER_NOT_SUPPORT_NO_ACK_MODE = "C4092";
    // received ack failed: received type != expected type.
    public static final String X_NET_ACK_TYPE = "C4093";
    // send error code - not found status
    public static final String X_SEND_NOT_FOUND = "C4094";
    public static final String X_SEND_TOO_LARGE = "C4095";
    public static final String X_SEND_RESOURCE_FULL = "C4096";
    // fail over not supported
    public static final String X_FAILOVER_NOT_SUPPORTED = "C4097";

    // service not supported
    public static final String X_UNKNOWN_BROKER_SERVICE = "C4098";

    // HA error code
    // take over in process.
    public static final String X_TAKE_OVER_IN_PROCESS = "C4099";
    // broker moved
    public static final String X_MOVE_PERMANENTLY = "C4100";

    public static final String X_TRANSACTION_PREPARE_FAILED = "C4101";
    public static final String X_UNEXPECTED_TRANSACTION_STATE = "C4102";
    public static final String X_TRANSACTION_INVALIDATED_FAILOVER = "C4103";
    public static final String X_TRANSACTION_END_FAILED = "C4104";
    public static final String X_TRANSACTION_START_FAILED = "C4105";
    public static final String X_SESSION_INVALID_CLIENTACK = "C4106";

    // ack failed due to remote broker failure
    public static final String X_AUTO_ACK_FAILED_REMOTE = "C4107";

    public static final String X_CLIENT_ACK_FAILED_REMOTE = "C4108";

    public static final String X_COMMIT_FAILED_REMOTE = "C4109";

    public static final String X_ACK_FAILED_REMOTE = "C4110";

    public static final String X_TEMP_DESTINATION_DELETED = "C4111";

    // 4200 - JAXM
    public static final String X_NO_FACTORY_CLASS = "C4200";
    public static final String X_MESSAGEFACTORY_ERROR = "C4201";
    public static final String X_NO_JAXMSERVLET_LISTENER = "C4202";
    public static final String X_JAXM_POST_FAILED = "C4203";
    public static final String X_MALFORMED_URL_LIST = "C4204";
    public static final String X_MALFORMED_URL = "C4205";
    public static final String X_BAD_ENDPOINT = "C4206";
    public static final String X_SOAP_CALL_FAILED = "C4207";

    public static final String X_ADD_CONSUMER_EVENT_LISTENER = "C4300";
    public static final String X_NO_EVENT_LISTENER_REGISTERED = "C4301";
    public static final String X_CONSUMER_EVENT_LISTENER_NOTFOUND = "C4302";
    public static final String X_EXPIRE_MSG_TO_DMQ = "C4303";
    public static final String X_UNDELIVERABLE_MSG_TO_DMQ = "C4304";
    public static final String X_BROKER_TXN_PREPARE_FAILED = "C4305";
    public static final String X_FORBIDDEN_IN_JAVAEE_WEB_EJB = "C4306";
    public static final String X_JMSCONTEXT_CLOSED = "C4307";
    public static final String X_JMSCONSUMER_CLOSED = "C4308";
    public static final String X_INVALID_SESSION_MODE = "C4309";
    public static final String X_MESSAGE_IS_NULL = "C4310";
    public static final String X_ASYNC_SEND_XA_TXN = "C4311";
    public static final String X_CONNECTION_FAILOVER = "C4312";
    public static final String X_PRODUCER_CLOSING = "C4313";
    public static final String X_ASYNC_SEND_COMPLETION_WAIT_TIMEOUT = "C4314";
    public static final String X_NO_ASYNC_SEND_LISTENER_PROCESSOR_THREAD = "C4315";
    public static final String X_INVALID_SHARED_SUBSCRIPTION_NAME = "C4316";
    public static final String X_MESSAGE_HAS_NO_BODY = "C4320";
    public static final String X_BODY_CLASS_INVALID = "C4321";
    public static final String X_MESSAGE_TYPE_NOT_SUPPORTED = "C4322";
    public static final String X_FILE_NOT_FOUND = "C4323";
    public static final String X_NO_KEYSTORE_PASSWORD = "C4324";
    public static final String X_WEBSOCKET_SESSION_CLOSED = "C4325";
    public static final String X_WEBSOCKET_PROCESS_PKT = "C4326";
    public static final String X_WEBSOCKET_CLOSE_ONERROR = "C4327";
    public static final String X_WEBSOCKET_OPEN_FAILED = "C4328";
    public static final String X_WEBSOCKET_OPEN_TIMEOUT = "C4329";
    public static final String X_SET_CLIENTID_TIMEOUT = "C4330";
    public static final String X_BROKER_JMS2_SHARED_SUB_NO_SUPPORT = "C4331";
    public static final String X_BROKER_DURA_SUB_NO_CLIENTID_NO_SUPPORT = "C4332";

    // MQ event codes

    public static final String E_CONNECTION_CLOSING_ADMIN = "E101";

    /**
     * Connection closed event code - admin requested shutdown
     */
    public static final String E_CONNECTION_CLOSED_SHUTDOWN = "E201";

    /**
     * Connection closed event code - admin requested restart
     */
    public static final String E_CONNECTION_CLOSED_RESTART = "E202";

    /**
     * Connection closed event code - server error, e.g. out of memory.
     */
    public static final String E_CONNECTION_CLOSED_ERROR = "E203";

    /**
     * Connection closed event code - admin killed connection.
     */
    public static final String E_CONNECTION_CLOSED_KILL = "E204";

    /**
     * Connection closed event code - broker crash.
     */
    public static final String E_CONNECTION_CLOSED_BROKER_DOWN = "E205";

    public static final String E_CONNECTION_CLOSED_LOST_CONNECTION = "E206";

    /**
     * Connection closed because broker is non-responsive
     */
    public static final String E_CONNECTION_CLOSED_NON_RESPONSIVE = "E207";

    /**
     * Connection reconnect event code
     */
    public static final String E_CONNECTION_RECONNECTED = "E301";

    /**
     * Connection reconnect failed event code.
     */
    public static final String E_CONNECTION_RECONNECT_FAILED = "E401";

    public static final String E_CONNECTION_EXIT = "E500";

    public static final String E_CONNECTION_ADDRESS_LIST_CHANGED = "E600";

    public static final String E_CONSUMER_READY = "E700";
    public static final String E_CONSUMER_NOT_READY = "E701";

    public static final String E_ASYNC_SEND_CALLBACK_THREAD_EXIT = "E702";
    public static final String E_CONNECTION_BROKEN = "E703";

    /**
     * Logging message keys/codes
     */
    public static final String I_CONNECTION_CREATED = "I100";

    // connection closed by the application
    public static final String I_CONNECTION_CLOSED = "I101";

    // connection stopped by the application
    public static final String I_CONNECTION_STOPPED = "I102";

    public static final String I_FLOW_CONTROL_PAUSED = "I103";

    public static final String I_FLOW_CONTROL_RESUME = "I104";

    public static final String I_READ_PACKET = "I105";

    public static final String I_WRITE_PACKET = "I106";

    public static final String I_CONNECTION_RECOVER_STATE = "I107";

    public static final String I_MOVED_PERMANENTLY = "I108";

    public static final String I_TIME_OUT = "I109";

    public static final String I_WAITING_FOR_CONNECTION_RECOVER = "I110";

    public static final String I_MQ_AUTO_RECONNECT_IS_DISABLED = "I112";

    public static final String I_CONNECTION_RECOVER_ABORTED = "I113";

    public static final String I_SESSION_CREATED = "I200";

    // connection closed by the application
    public static final String I_SESSION_CLOSED = "I201";

    public static final String I_CONSUMER_CREATED = "I300";

    // consumer closed by the application
    public static final String I_CONSUMER_CLOSED = "I301";

    public static final String I_CONSUMER_MESSAGE_DELIVERED = "I302";

    public static final String I_EXPIRED_MSG_BEFORE_DELIVER_TO_CONSUMER = "I303";
    public static final String I_UNDELIVERABLE_MSG = "I304";

    public static final String I_PRODUCER_CREATED = "I400";

    // producer closed by the application
    public static final String I_PRODUCER_CLOSED = "I401";

    public static final String I_PRODUCER_SENT_MESSAGE = "I402";
    public static final String I_PRODUCER_ASYNC_SENDING_MESSAGE = "I403";

    public static final String I_CAUGHT_JVM_EXCEPTION = "I500";
    public static final String I_THROW_JMS_EXCEPTION = "I501";
    public static final String I_ASYNC_SEND_LISTENER_PROCESSOR_THREAD_START = "I502";
    public static final String I_ASYNC_SEND_LISTENER_PROCESSOR_THREAD_EXIT = "I503";
    public static final String I_WAIT_ASYNC_SENDS_COMPLETE_PRODUCER = "I504";
    public static final String I_WAIT_ASYNC_SENDS_COMPLETE_SESSION = "I505";
    public static final String I_USE_KEYSTORE = "I506";

    /***************** End of message key constants *******************/

}
