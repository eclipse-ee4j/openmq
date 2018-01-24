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
 * @(#)mqerrors.h	1.32 10/23/07
 */ 

#ifndef MQ_ERROR_CODES_H
#define MQ_ERROR_CODES_H

/*
 * defines the error codes 
 */

#ifdef __cplusplus
extern "C" {
#endif
    
#include "mqtypes.h"

#define MQ_SUCCESS                       ((MQError)(0))
#define MQ_OK                            MQ_SUCCESS
#define MQ_BASE_ERROR_CODE               ((MQError)(1000))

#define MQ_INTERNAL_ERROR                ((MQError)(MQ_BASE_ERROR_CODE + 1))

#define MQ_NULL_PTR_ARG                  ((MQError)(MQ_BASE_ERROR_CODE + 100))
#define MQ_WRONG_ARG_BUFFER_SIZE         ((MQError)(MQ_BASE_ERROR_CODE + 101))
#define MQ_OUT_OF_MEMORY                 ((MQError)(MQ_BASE_ERROR_CODE + 102))
#define MQ_FILE_OUTPUT_ERROR             ((MQError)(MQ_BASE_ERROR_CODE + 103))
#define MQ_NOT_FOUND                     ((MQError)(MQ_BASE_ERROR_CODE + 104))
#define MQ_BAD_VECTOR_INDEX              ((MQError)(MQ_BASE_ERROR_CODE + 105))
#define MQ_VECTOR_TOO_BIG                ((MQError)(MQ_BASE_ERROR_CODE + 106))
#define MQ_UNEXPECTED_NULL               ((MQError)(MQ_BASE_ERROR_CODE + 107))
#define MQ_INVALID_ITERATOR              ((MQError)(MQ_BASE_ERROR_CODE + 108))
#define MQ_STRING_NOT_NUMBER             ((MQError)(MQ_BASE_ERROR_CODE + 109))
#define MQ_NUMBER_NOT_UINT16             ((MQError)(MQ_BASE_ERROR_CODE + 110))
#define MQ_OBJECT_NOT_CLONEABLE          ((MQError)(MQ_BASE_ERROR_CODE + 112))
#define MQ_HASH_VALUE_ALREADY_EXISTS     ((MQError)(MQ_BASE_ERROR_CODE + 113))
#define MQ_HASH_TABLE_ALLOCATION_FAILED  ((MQError)(MQ_BASE_ERROR_CODE + 114))
#define MQ_INCOMPATIBLE_LIBRARY          ((MQError)(MQ_BASE_ERROR_CODE + 115))
#define MQ_CONCURRENT_ACCESS             ((MQError)(MQ_BASE_ERROR_CODE + 116))
#define MQ_CONCURRENT_DEADLOCK           ((MQError)(MQ_BASE_ERROR_CODE + 117))
#define MQ_CONCURRENT_NOT_OWNER          ((MQError)(MQ_BASE_ERROR_CODE + 118))

#define MQ_NOT_IPV4_ADDRESS              ((MQError)(MQ_BASE_ERROR_CODE + 200))

#define MQ_UNINITIALIZED_STREAM          ((MQError)(MQ_BASE_ERROR_CODE + 300))
#define MQ_END_OF_STREAM                 ((MQError)(MQ_BASE_ERROR_CODE + 301))
#define MQ_INPUT_STREAM_ERROR            ((MQError)(MQ_BASE_ERROR_CODE + 302))

#define MQ_SERIALIZE_NOT_CLASS_DEF       ((MQError)(MQ_BASE_ERROR_CODE + 400))
#define MQ_SERIALIZE_BAD_CLASS_UID       ((MQError)(MQ_BASE_ERROR_CODE + 401))
#define MQ_SERIALIZE_BAD_MAGIC_NUMBER    ((MQError)(MQ_BASE_ERROR_CODE + 402))
#define MQ_SERIALIZE_BAD_VERSION         ((MQError)(MQ_BASE_ERROR_CODE + 403))
#define MQ_SERIALIZE_NOT_HASHTABLE       ((MQError)(MQ_BASE_ERROR_CODE + 404))
#define MQ_SERIALIZE_UNEXPECTED_BYTES    ((MQError)(MQ_BASE_ERROR_CODE + 405))
#define MQ_SERIALIZE_UNRECOGNIZED_CLASS  ((MQError)(MQ_BASE_ERROR_CODE + 406))
#define MQ_SERIALIZE_BAD_SUPER_CLASS     ((MQError)(MQ_BASE_ERROR_CODE + 407))
#define MQ_SERIALIZE_BAD_HANDLE          ((MQError)(MQ_BASE_ERROR_CODE + 408))
#define MQ_SERIALIZE_NOT_CLASS_HANDLE    ((MQError)(MQ_BASE_ERROR_CODE + 409))
#define MQ_SERIALIZE_NOT_OBJECT_HANDLE   ((MQError)(MQ_BASE_ERROR_CODE + 410))
#define MQ_SERIALIZE_STRING_TOO_BIG      ((MQError)(MQ_BASE_ERROR_CODE + 411))
#define MQ_SERIALIZE_CANNOT_CLONE        ((MQError)(MQ_BASE_ERROR_CODE + 413))
#define MQ_SERIALIZE_NO_CLASS_DESC       ((MQError)(MQ_BASE_ERROR_CODE + 414))
#define MQ_SERIALIZE_CORRUPTED_HASHTABLE ((MQError)(MQ_BASE_ERROR_CODE + 415))
#define MQ_SERIALIZE_TEST_ERROR          ((MQError)(MQ_BASE_ERROR_CODE + 416))
#define MQ_SERIALIZE_STRING_CONTAINS_NULL ((MQError)(MQ_BASE_ERROR_CODE + 417))

#define MQ_PROPERTY_NULL                 ((MQError)(MQ_BASE_ERROR_CODE + 500))
#define MQ_PROPERTY_WRONG_VALUE_TYPE     ((MQError)(MQ_BASE_ERROR_CODE + 501))
#define MQ_INVALID_TYPE_CONVERSION       ((MQError)(MQ_BASE_ERROR_CODE + 502))
#define MQ_NULL_STRING                   ((MQError)(MQ_BASE_ERROR_CODE + 503))
#define MQ_TYPE_CONVERSION_OUT_OF_BOUNDS ((MQError)(MQ_BASE_ERROR_CODE + 504))
#define MQ_PROPERTY_FILE_ERROR           ((MQError)(MQ_BASE_ERROR_CODE + 505))
#define MQ_FILE_NOT_FOUND                ((MQError)(MQ_BASE_ERROR_CODE + 506))
#define MQ_BASIC_TYPE_SIZE_MISMATCH      ((MQError)(MQ_BASE_ERROR_CODE + 507))

#define MQ_TCP_INVALID_PORT              ((MQError)(MQ_BASE_ERROR_CODE + 600))
#define MQ_TCP_CONNECTION_CLOSED         ((MQError)(MQ_BASE_ERROR_CODE + 601))
#define MQ_TCP_ALREADY_CONNECTED         ((MQError)(MQ_BASE_ERROR_CODE + 602))

#define MQ_PORTMAPPER_INVALID_INPUT      ((MQError)(MQ_BASE_ERROR_CODE + 700))
#define MQ_PORTMAPPER_WRONG_VERSION      ((MQError)(MQ_BASE_ERROR_CODE + 701))
#define MQ_PORTMAPPER_ERROR              ((MQError)(MQ_BASE_ERROR_CODE + 702))

#define MQ_INVALID_PACKET                ((MQError)(MQ_BASE_ERROR_CODE + 800))
#define MQ_INVALID_PACKET_FIELD          ((MQError)(MQ_BASE_ERROR_CODE + 801))

/*
 * Application should close the connection when receive this error
 */
#define MQ_PACKET_OUTPUT_ERROR           ((MQError)(MQ_BASE_ERROR_CODE + 802))

#define MQ_UNRECOGNIZED_PACKET_TYPE      ((MQError)(MQ_BASE_ERROR_CODE + 803))
#define MQ_UNSUPPORTED_MESSAGE_TYPE      ((MQError)(MQ_BASE_ERROR_CODE + 804))
#define MQ_BAD_PACKET_MAGIC_NUMBER       ((MQError)(MQ_BASE_ERROR_CODE + 805))
#define MQ_UNSUPPORTED_PACKET_VERSION    ((MQError)(MQ_BASE_ERROR_CODE + 806))

#define MQ_COULD_NOT_CONNECT_TO_BROKER   ((MQError)(MQ_BASE_ERROR_CODE + 900))  
#define MQ_BROKER_CONNECTION_CLOSED      ((MQError)(MQ_BASE_ERROR_CODE + 901))
#define MQ_UNEXPECTED_ACKNOWLEDGEMENT    ((MQError)(MQ_BASE_ERROR_CODE + 902))
#define MQ_ACK_STATUS_NOT_OK             ((MQError)(MQ_BASE_ERROR_CODE + 903))
#define MQ_COULD_NOT_CREATE_THREAD       ((MQError)(MQ_BASE_ERROR_CODE + 904))
#define MQ_INVALID_AUTHENTICATE_REQUEST  ((MQError)(MQ_BASE_ERROR_CODE + 905))
#define MQ_ADMIN_KEY_AUTH_MISMATCH       ((MQError)(MQ_BASE_ERROR_CODE + 906))
#define MQ_NO_AUTHENTICATION_HANDLER     ((MQError)(MQ_BASE_ERROR_CODE + 907))
#define MQ_UNSUPPORTED_AUTH_TYPE         ((MQError)(MQ_BASE_ERROR_CODE + 908))
#define MQ_INVALID_CLIENTID              ((MQError)(MQ_BASE_ERROR_CODE + 909))
#define MQ_CLIENTID_IN_USE               ((MQError)(MQ_BASE_ERROR_CODE + 910))

#define MQ_REUSED_CONSUMER_ID            ((MQError)(MQ_BASE_ERROR_CODE + 1000))
#define MQ_INVALID_CONSUMER_ID           ((MQError)(MQ_BASE_ERROR_CODE + 1001))

#define MQ_SOCKET_ERROR                  ((MQError)(MQ_BASE_ERROR_CODE + 1100))
#define MQ_NEGATIVE_AMOUNT               ((MQError)(MQ_BASE_ERROR_CODE + 1101))
#define MQ_POLL_ERROR                    ((MQError)(MQ_BASE_ERROR_CODE + 1102))
#define MQ_TIMEOUT_EXPIRED               ((MQError)(MQ_BASE_ERROR_CODE + 1103))
#define MQ_INVALID_PORT                  ((MQError)(MQ_BASE_ERROR_CODE + 1104))
#define MQ_SOCKET_CONNECT_FAILED         ((MQError)(MQ_BASE_ERROR_CODE + 1105))
#define MQ_SOCKET_READ_FAILED            ((MQError)(MQ_BASE_ERROR_CODE + 1106))
#define MQ_SOCKET_WRITE_FAILED           ((MQError)(MQ_BASE_ERROR_CODE + 1107))
#define MQ_SOCKET_SHUTDOWN_FAILED        ((MQError)(MQ_BASE_ERROR_CODE + 1108))
#define MQ_SOCKET_CLOSE_FAILED           ((MQError)(MQ_BASE_ERROR_CODE + 1109))
#define MQ_SSL_INIT_ERROR                ((MQError)(MQ_BASE_ERROR_CODE + 1110))
#define MQ_SSL_SOCKET_INIT_ERROR         ((MQError)(MQ_BASE_ERROR_CODE + 1111))
#define MQ_SSL_CERT_ERROR                ((MQError)(MQ_BASE_ERROR_CODE + 1112))
#define MQ_SSL_ERROR                     ((MQError)(MQ_BASE_ERROR_CODE + 1113))
#define MQ_SSL_ALREADY_INITIALIZED       ((MQError)(MQ_BASE_ERROR_CODE + 1114))
#define MQ_SSL_NOT_INITIALIZED           ((MQError)(MQ_BASE_ERROR_CODE + 1115))

#define MQ_MD5_HASH_FAILURE              ((MQError)(MQ_BASE_ERROR_CODE + 1200))
#define MQ_BASE64_ENCODE_FAILURE         ((MQError)(MQ_BASE_ERROR_CODE + 1201))

#define MQ_BROKER_BAD_REQUEST            ((MQError)(MQ_BASE_ERROR_CODE + 1300))
#define MQ_BROKER_UNAUTHORIZED           ((MQError)(MQ_BASE_ERROR_CODE + 1301))
#define MQ_BROKER_FORBIDDEN              ((MQError)(MQ_BASE_ERROR_CODE + 1302))
#define MQ_BROKER_NOT_FOUND              ((MQError)(MQ_BASE_ERROR_CODE + 1303))
#define MQ_BROKER_NOT_ALLOWED            ((MQError)(MQ_BASE_ERROR_CODE + 1304))
#define MQ_BROKER_TIMEOUT                ((MQError)(MQ_BASE_ERROR_CODE + 1305))
#define MQ_BROKER_CONFLICT               ((MQError)(MQ_BASE_ERROR_CODE + 1306))
#define MQ_BROKER_GONE                   ((MQError)(MQ_BASE_ERROR_CODE + 1307))
#define MQ_BROKER_PRECONDITION_FAILED    ((MQError)(MQ_BASE_ERROR_CODE + 1308))
#define MQ_BROKER_INVALID_LOGIN          ((MQError)(MQ_BASE_ERROR_CODE + 1309))
#define MQ_BROKER_ERROR                  ((MQError)(MQ_BASE_ERROR_CODE + 1310))
#define MQ_BROKER_NOT_IMPLEMENTED        ((MQError)(MQ_BASE_ERROR_CODE + 1311))
#define MQ_BROKER_UNAVAILABLE            ((MQError)(MQ_BASE_ERROR_CODE + 1312))
#define MQ_BROKER_BAD_VERSION            ((MQError)(MQ_BASE_ERROR_CODE + 1313))
#define MQ_BROKER_RESOURCE_FULL          ((MQError)(MQ_BASE_ERROR_CODE + 1314))
#define MQ_BROKER_ENTITY_TOO_LARGE       ((MQError)(MQ_BASE_ERROR_CODE + 1315))

#define MQ_PROTOCOL_HANDLER_GOODBYE_FAILED      ((MQError)(MQ_BASE_ERROR_CODE + 1400))
#define MQ_PROTOCOL_HANDLER_START_FAILED        ((MQError)(MQ_BASE_ERROR_CODE + 1401))
#define MQ_PROTOCOL_HANDLER_STOP_FAILED         ((MQError)(MQ_BASE_ERROR_CODE + 1402))
#define MQ_PROTOCOL_HANDLER_AUTHENTICATE_FAILED ((MQError)(MQ_BASE_ERROR_CODE + 1403))
#define MQ_PROTOCOL_HANDLER_UNEXPECTED_REPLY    ((MQError)(MQ_BASE_ERROR_CODE + 1404))
#define MQ_PROTOCOL_HANDLER_WRITE_ERROR         ((MQError)(MQ_BASE_ERROR_CODE + 1405))
#define MQ_PROTOCOL_HANDLER_READ_ERROR          ((MQError)(MQ_BASE_ERROR_CODE + 1406))
#define MQ_PROTOCOL_HANDLER_ERROR               ((MQError)(MQ_BASE_ERROR_CODE + 1407))
#define MQ_PROTOCOL_HANDLER_SET_CLIENTID_FAILED ((MQError)(MQ_BASE_ERROR_CODE + 1408))
#define MQ_PROTOCOL_HANDLER_DELETE_DESTINATION_FAILED ((MQError)(MQ_BASE_ERROR_CODE + 1409))
#define MQ_PROTOCOL_HANDLER_HELLO_FAILED        ((MQError)(MQ_BASE_ERROR_CODE + 1410))
#define MQ_PROTOCOL_HANDLER_RESUME_FLOW_FAILED  ((MQError)(MQ_BASE_ERROR_CODE + 1411))

#define MQ_READ_CHANNEL_DISPATCH_ERROR          ((MQError)(MQ_BASE_ERROR_CODE + 1500))

#define MQ_READQTABLE_ERROR                     ((MQError)(MQ_BASE_ERROR_CODE + 1600))

#define MQ_UNSUPPORTED_ARGUMENT_VALUE           ((MQError)(MQ_BASE_ERROR_CODE + 1700))

#define MQ_SESSION_CLOSED                       ((MQError)(MQ_BASE_ERROR_CODE + 1800))
#define MQ_CONSUMER_NOT_IN_SESSION              ((MQError)(MQ_BASE_ERROR_CODE + 1801))
#define MQ_PRODUCER_NOT_IN_SESSION              ((MQError)(MQ_BASE_ERROR_CODE + 1802))
#define MQ_QUEUE_CONSUMER_CANNOT_BE_DURABLE     ((MQError)(MQ_BASE_ERROR_CODE + 1803))
#define MQ_CANNOT_UNSUBSCRIBE_ACTIVE_CONSUMER   ((MQError)(MQ_BASE_ERROR_CODE + 1804))
#define MQ_RECEIVE_QUEUE_CLOSED                 ((MQError)(MQ_BASE_ERROR_CODE + 1805))
#define MQ_RECEIVE_QUEUE_ERROR                  ((MQError)(MQ_BASE_ERROR_CODE + 1806))
#define MQ_NO_CONNECTION                        ((MQError)(MQ_BASE_ERROR_CODE + 1807))
#define MQ_CONNECTION_CLOSED                    ((MQError)(MQ_BASE_ERROR_CODE + 1808))
#define MQ_INVALID_ACKNOWLEDGE_MODE             ((MQError)(MQ_BASE_ERROR_CODE + 1809))
#define MQ_INVALID_DESTINATION_TYPE             ((MQError)(MQ_BASE_ERROR_CODE + 1810))
#define MQ_INVALID_RECEIVE_MODE                 ((MQError)(MQ_BASE_ERROR_CODE + 1811))
#define MQ_NOT_SYNC_RECEIVE_MODE                ((MQError)(MQ_BASE_ERROR_CODE + 1812))
#define MQ_NOT_ASYNC_RECEIVE_MODE               ((MQError)(MQ_BASE_ERROR_CODE + 1813))
#define MQ_TRANSACTED_SESSION                   ((MQError)(MQ_BASE_ERROR_CODE + 1814))
#define MQ_NOT_TRANSACTED_SESSION               ((MQError)(MQ_BASE_ERROR_CODE + 1815))
#define MQ_SESSION_NOT_CLIENT_ACK_MODE          ((MQError)(MQ_BASE_ERROR_CODE + 1816))
#define MQ_TRANSACTION_ID_IN_USE                ((MQError)(MQ_BASE_ERROR_CODE + 1817))
#define MQ_INVALID_TRANSACTION_ID               ((MQError)(MQ_BASE_ERROR_CODE + 1818))
#define MQ_THREAD_OUTSIDE_XA_TRANSACTION        ((MQError)(MQ_BASE_ERROR_CODE + 1819))
#define MQ_XA_SESSION_NO_TRANSATION             ((MQError)(MQ_BASE_ERROR_CODE + 1820))
#define MQ_XA_SESSION_IN_PROGRESS               ((MQError)(MQ_BASE_ERROR_CODE + 1821))
#define MQ_SHARED_SUBSCRIPTION_NOT_TOPIC        ((MQError)(MQ_BASE_ERROR_CODE + 1822))

  
#define MQ_MESSAGE_NO_DESTINATION               ((MQError)(MQ_BASE_ERROR_CODE + 1900))
#define MQ_DESTINATION_NO_CLASS                 ((MQError)(MQ_BASE_ERROR_CODE + 1901))
#define MQ_DESTINATION_NO_NAME                  ((MQError)(MQ_BASE_ERROR_CODE + 1902))
#define MQ_NO_REPLY_TO_DESTINATION              ((MQError)(MQ_BASE_ERROR_CODE + 1903))

#define MQ_PRODUCER_NO_DESTINATION              ((MQError)(MQ_BASE_ERROR_CODE + 2000))
#define MQ_PRODUCER_HAS_DESTINATION             ((MQError)(MQ_BASE_ERROR_CODE + 2001))
#define MQ_INVALID_DELIVERY_MODE                ((MQError)(MQ_BASE_ERROR_CODE + 2002))
#define MQ_INVALID_PRIORITY                     ((MQError)(MQ_BASE_ERROR_CODE + 2003))
#define MQ_PRODUCER_CLOSED                      ((MQError)(MQ_BASE_ERROR_CODE + 2004))
#define MQ_SEND_NOT_FOUND                       ((MQError)(MQ_BASE_ERROR_CODE + 2005))
#define MQ_SEND_TOO_LARGE                       ((MQError)(MQ_BASE_ERROR_CODE + 2006))
#define MQ_SEND_RESOURCE_FULL                   ((MQError)(MQ_BASE_ERROR_CODE + 2007))

#define MQ_CONSUMER_NO_DURABLE_NAME             ((MQError)(MQ_BASE_ERROR_CODE + 2100))
#define MQ_CONSUMER_NOT_INITIALIZED             ((MQError)(MQ_BASE_ERROR_CODE + 2101))
#define MQ_CONSUMER_EXCEPTION                   ((MQError)(MQ_BASE_ERROR_CODE + 2102))
#define MQ_CONSUMER_NO_SESSION                  ((MQError)(MQ_BASE_ERROR_CODE + 2103))
#define MQ_MESSAGE_NOT_IN_SESSION               ((MQError)(MQ_BASE_ERROR_CODE + 2104))
#define MQ_NO_MESSAGE                           ((MQError)(MQ_BASE_ERROR_CODE + 2105))
#define MQ_CONSUMER_CLOSED                      ((MQError)(MQ_BASE_ERROR_CODE + 2106))
#define MQ_INVALID_MESSAGE_SELECTOR             ((MQError)(MQ_BASE_ERROR_CODE + 2107))
#define MQ_CONSUMER_NOT_FOUND                   ((MQError)(MQ_BASE_ERROR_CODE + 2108))
#define MQ_DESTINATION_CONSUMER_LIMIT_EXCEEDED  ((MQError)(MQ_BASE_ERROR_CODE + 2109))
#define MQ_CONSUMER_DESTINATION_NOT_FOUND       ((MQError)(MQ_BASE_ERROR_CODE + 2110))
#define MQ_NOLOCAL_DURABLE_CONSUMER_NO_CLIENTID ((MQError)(MQ_BASE_ERROR_CODE + 2111))
#define MQ_CONSUMER_NO_SUBSCRIPTION_NAME        ((MQError)(MQ_BASE_ERROR_CODE + 2112))

#define MQ_CONNECTION_START_ERROR               ((MQError)(MQ_BASE_ERROR_CODE + 2200))
#define MQ_CONNECTION_CREATE_SESSION_ERROR      ((MQError)(MQ_BASE_ERROR_CODE + 2201))
#define MQ_CONNECTION_OPEN_ERROR                ((MQError)(MQ_BASE_ERROR_CODE + 2202))
#define MQ_CONNECTION_UNSUPPORTED_TRANSPORT     ((MQError)(MQ_BASE_ERROR_CODE + 2203))

#define MQ_HANDLED_OBJECT_INVALID_HANDLE_ERROR  ((MQError)(MQ_BASE_ERROR_CODE + 2300))
#define MQ_HANDLED_OBJECT_IN_USE                ((MQError)(MQ_BASE_ERROR_CODE + 2301))
#define MQ_HANDLED_OBJECT_NO_MORE_HANDLES       ((MQError)(MQ_BASE_ERROR_CODE + 2302))

#define MQ_REFERENCED_FREED_OBJECT_ERROR        ((MQError)(MQ_BASE_ERROR_CODE + 2400))

#define MQ_DESTINATION_NOT_TEMPORARY                ((MQError)(MQ_BASE_ERROR_CODE + 2500))
#define MQ_TEMPORARY_DESTINATION_NOT_IN_CONNECTION  ((MQError)(MQ_BASE_ERROR_CODE + 2501))

#define MQ_CALLBACK_RUNTIME_ERROR               ((MQError)(MQ_BASE_ERROR_CODE + 2600))

#define MQ_STATUS_INVALID_HANDLE                ((MQError)(MQ_BASE_ERROR_CODE + 5000))
#define MQ_NO_MESSAGE_PROPERTIES                ((MQError)(MQ_BASE_ERROR_CODE + 5001))
#define MQ_STATUS_NULL_LOGGER                   ((MQError)(MQ_BASE_ERROR_CODE + 5002))
#define MQ_STATUS_CONNECTION_NOT_CLOSED         ((MQError)(MQ_BASE_ERROR_CODE + 5003))
#define MQ_NOT_XA_CONNECTION                    ((MQError)(MQ_BASE_ERROR_CODE + 5004))
#define MQ_ILLEGAL_CLOSE_XA_CONNECTION          ((MQError)(MQ_BASE_ERROR_CODE + 5005))


#ifdef __cplusplus
}
#endif

#endif  /* MQ_ERROR_CODES_H */

