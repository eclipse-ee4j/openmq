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
 * @(#)ErrorCodes.cpp	1.32 10/23/07
 */ 

#include "ErrorCodes.h"
#include "../util/UtilityMacros.h"
#include "prerr.h"
#include "secerr.h"
#include "sslerr.h"

/*
 *
 */
const char * 
errorStr(const MQError errorCode)
{
  switch((PRInt32)errorCode) {
  case MQ_SUCCESS:                        return "Success";

  case MQ_INTERNAL_ERROR:                 return "Generic internal error";

  case MQ_NULL_PTR_ARG:                   return "NULL pointer passed to method";
  case MQ_WRONG_ARG_BUFFER_SIZE:          return "Buffer is the wrong size";
  case MQ_OUT_OF_MEMORY:                  return "Out of memory";
  case MQ_FILE_OUTPUT_ERROR:              return "File output error";
  case MQ_NOT_FOUND:                      return "Not found";
  case MQ_BAD_VECTOR_INDEX:               return "Bad vector index";
  case MQ_VECTOR_TOO_BIG:                 return "Vector too big";
  case MQ_UNEXPECTED_NULL:                return "Unexpected NULL";
  case MQ_INVALID_ITERATOR:               return "Invalid iterator";
  case MQ_STRING_NOT_NUMBER:              return "String not a number";
  case MQ_NUMBER_NOT_UINT16:              return "Number not a UINT16";
  case MQ_OBJECT_NOT_CLONEABLE:           return "The object cannot be cloned";
  case MQ_HASH_VALUE_ALREADY_EXISTS:      return "The hash value already exists in the hash table";
  case MQ_HASH_TABLE_ALLOCATION_FAILED:   return "The hash table could not be allocated";
  case MQ_INCOMPATIBLE_LIBRARY:           return "The library is incompatible";
  case MQ_CONCURRENT_ACCESS:              return "Concurrent access";
  case MQ_CONCURRENT_DEADLOCK:            return "Operation may cause deadlock";
  case MQ_CONCURRENT_NOT_OWNER:           return "Concurrent access not owner";

                                             
  case MQ_NOT_IPV4_ADDRESS:               return "Not an IPv4 Address";

  case MQ_UNINITIALIZED_STREAM:           return "Uninitialized stream";
  case MQ_END_OF_STREAM:                  return "End of stream";
  case MQ_INPUT_STREAM_ERROR:             return "Input stream error";

  case MQ_SERIALIZE_NOT_CLASS_DEF:        return "Serialize not class definition";
  case MQ_SERIALIZE_BAD_CLASS_UID:        return "Serialize bad class UID";
  case MQ_SERIALIZE_BAD_MAGIC_NUMBER:     return "Serialize bad magic number";
  case MQ_SERIALIZE_BAD_VERSION:          return "Serialize bad version";
  case MQ_SERIALIZE_NOT_HASHTABLE:        return "Serialize not a hashtable";
  case MQ_SERIALIZE_UNEXPECTED_BYTES:     return "Serialize unexpected bytes";
  case MQ_SERIALIZE_UNRECOGNIZED_CLASS:   return "Serialize unrecognized class";
  case MQ_SERIALIZE_BAD_SUPER_CLASS:      return "Serialize bad super class";
  case MQ_SERIALIZE_BAD_HANDLE:           return "Serialize bad handle";
  case MQ_SERIALIZE_NOT_CLASS_HANDLE:     return "Serialize not a class object";
  case MQ_SERIALIZE_NOT_OBJECT_HANDLE:    return "Serialize not a handle object";
  case MQ_SERIALIZE_STRING_TOO_BIG:       return "Serialize string too big";
  case MQ_SERIALIZE_CANNOT_CLONE:         return "Serialize cannot clone";
  case MQ_SERIALIZE_NO_CLASS_DESC:        return "Serialize no class description";
  case MQ_SERIALIZE_CORRUPTED_HASHTABLE:  return "Serialize corrupted hashtable";
  case MQ_SERIALIZE_TEST_ERROR:           return "Serialize testing error";
  case MQ_SERIALIZE_STRING_CONTAINS_NULL: return "Serialize string contains NULL";

  case MQ_PROPERTY_NULL:                  return "Property is NULL";
  case MQ_PROPERTY_WRONG_VALUE_TYPE:      return "Property has the wrong value type";
  case MQ_INVALID_TYPE_CONVERSION:        return "The object could not be converted to the requested type";
  case MQ_NULL_STRING:                    return "The string is NULL";
  case MQ_TYPE_CONVERSION_OUT_OF_BOUNDS:  return "The object conversion failed because the value is out of bounds";
  case MQ_PROPERTY_FILE_ERROR:            return "There was an error reading from the property file";
  case MQ_FILE_NOT_FOUND:                 return "The property file could not be found";
  case MQ_BASIC_TYPE_SIZE_MISMATCH:       return "MQ basic type size mismatch";

  case MQ_TCP_INVALID_PORT:               return "Invalid TCP port";
  case MQ_TCP_CONNECTION_CLOSED:          return "TCP connection is closed";
  case MQ_TCP_ALREADY_CONNECTED:          return "TCP already connected";

  case MQ_PORTMAPPER_INVALID_INPUT:       return "Portmapper returned invalid input";
  case MQ_PORTMAPPER_WRONG_VERSION:       return "Portmapper is the wrong version";
  case MQ_PORTMAPPER_ERROR:               return "Portmapper error";

  case MQ_INVALID_PACKET:                 return "Invalid packet";
  case MQ_INVALID_PACKET_FIELD:           return "Invalid packet field";
  case MQ_PACKET_OUTPUT_ERROR:            return "Packet output error";
  case MQ_UNRECOGNIZED_PACKET_TYPE:       return "The packet type was unrecognized";
  case MQ_UNSUPPORTED_MESSAGE_TYPE:       return "The JMS message type is not supported";
    
  case MQ_COULD_NOT_CONNECT_TO_BROKER:    return "Could not connect to broker";
  case MQ_BROKER_CONNECTION_CLOSED:       return "Broker connection is closed";
  case MQ_UNEXPECTED_ACKNOWLEDGEMENT:     return "Received an unexpected acknowledgement";
  case MQ_ACK_STATUS_NOT_OK:              return "Acknowledgement status is not OK";
  case MQ_COULD_NOT_CREATE_THREAD:        return "Could not create thread";
  case MQ_INVALID_AUTHENTICATE_REQUEST:   return "Invalid authenticate request";
  case MQ_ADMIN_KEY_AUTH_MISMATCH:        return "Admin key authorization mismatch";
  case MQ_NO_AUTHENTICATION_HANDLER:      return "No authentication handler";
  case MQ_UNSUPPORTED_AUTH_TYPE:          return "Unsupported authentication type";
  case MQ_INVALID_CLIENTID:               return "Invalid client id";
  case MQ_CLIENTID_IN_USE:                return "Client id already in use";

  case MQ_REUSED_CONSUMER_ID:             return "Reused consumer id";
  case MQ_INVALID_CONSUMER_ID:            return "Invalid consumer id";

  case MQ_SOCKET_ERROR:                   return "Socket error";
  case MQ_NEGATIVE_AMOUNT:                return "Negative amount";
  case MQ_POLL_ERROR:                     return "Poll error";
  case MQ_TIMEOUT_EXPIRED:                return "Timeout expired";
  case MQ_INVALID_PORT:                   return "Invalid port";
  case MQ_SOCKET_CONNECT_FAILED:          return "Could not connect socket to the host";
  case MQ_SOCKET_READ_FAILED:             return "Could not read from the socket";
  case MQ_SOCKET_WRITE_FAILED:            return "Could not write to the socket";
  case MQ_SOCKET_SHUTDOWN_FAILED:         return "Could not shutdown the socket";
  case MQ_SOCKET_CLOSE_FAILED:            return "Could not close the socket";
  case MQ_SSL_INIT_ERROR:                 return "SSL initialization error";
  case MQ_SSL_SOCKET_INIT_ERROR:          return "SSL socket initialization error";
  case MQ_SSL_CERT_ERROR:                 return "SSL certificate error";
  case MQ_SSL_ERROR:                      return "SSL error";
  case MQ_SSL_ALREADY_INITIALIZED:        return "SSL has already been initialized";
  case MQ_SSL_NOT_INITIALIZED:            return "SSL not initialized";
    
  case MQ_MD5_HASH_FAILURE:               return "MD5 Hash failure";
  case MQ_BASE64_ENCODE_FAILURE:          return "Base64 encode failure";

  case MQ_BROKER_BAD_REQUEST:             return "Broker: bad request";
  case MQ_BROKER_UNAUTHORIZED:            return "Broker: unauthorized";
  case MQ_BROKER_FORBIDDEN:               return "Broker: forbidden";
  case MQ_BROKER_NOT_FOUND:               return "Broker: not found";
  case MQ_BROKER_NOT_ALLOWED:             return "Broker: not allowed";
  case MQ_BROKER_TIMEOUT:                 return "Broker: timeout";
  case MQ_BROKER_CONFLICT:                return "Broker: conflict";
  case MQ_BROKER_GONE:                    return "Broker: gone";
  case MQ_BROKER_PRECONDITION_FAILED:     return "Broker: precondition failed";
  case MQ_BROKER_INVALID_LOGIN:           return "Broker: invalid login";
  case MQ_BROKER_ERROR:                   return "Broker: error";
  case MQ_BROKER_NOT_IMPLEMENTED:         return "Broker: not implemented";
  case MQ_BROKER_UNAVAILABLE:             return "Broker: unavailable";
  case MQ_BROKER_BAD_VERSION:             return "Broker: bad version";
  case MQ_BROKER_RESOURCE_FULL:           return "Broker: resource full";
  case MQ_BROKER_ENTITY_TOO_LARGE:        return "Broker: entity too large";

  case MQ_PROTOCOL_HANDLER_GOODBYE_FAILED:      return "Error saying goodbye to broker.";
  case MQ_PROTOCOL_HANDLER_START_FAILED:        return "Starting broker connection failed.";
  case MQ_PROTOCOL_HANDLER_STOP_FAILED:         return "Stopping broker connection failed.";
  case MQ_PROTOCOL_HANDLER_AUTHENTICATE_FAILED: return "Authenticating to the broker failed.";
  case MQ_PROTOCOL_HANDLER_UNEXPECTED_REPLY:    return "Received an unexpected reply from the broker.";
  case MQ_PROTOCOL_HANDLER_WRITE_ERROR:         return "Writing a packet to the broker failed.";
  case MQ_PROTOCOL_HANDLER_READ_ERROR:          return "Reading a packet from the broker failed.";
  case MQ_PROTOCOL_HANDLER_ERROR:               return "ProtocolHandler error";
  case MQ_PROTOCOL_HANDLER_SET_CLIENTID_FAILED: return "Setting client id failed";
  case MQ_PROTOCOL_HANDLER_DELETE_DESTINATION_FAILED: return "Deleting destination failed";
  case MQ_PROTOCOL_HANDLER_HELLO_FAILED:        return "Error saying hello to broker";
  case MQ_PROTOCOL_HANDLER_RESUME_FLOW_FAILED:  return "Error resume flow from broker";

  case MQ_READ_CHANNEL_DISPATCH_ERROR:          return "Read channel couldn't dispatch packet";

  case MQ_READQTABLE_ERROR:                     return "ReadQTable error";

  case MQ_UNSUPPORTED_ARGUMENT_VALUE:           return "Unsupported argument value";

  case MQ_SESSION_CLOSED:                       return "Session closed";
  case MQ_CONSUMER_NOT_IN_SESSION:              return "The consumer is not part of this session";
  case MQ_PRODUCER_NOT_IN_SESSION:              return "The producer is not part of this session";
  case MQ_QUEUE_CONSUMER_CANNOT_BE_DURABLE:     return "A queue consumer cannot be durable";
  case MQ_CANNOT_UNSUBSCRIBE_ACTIVE_CONSUMER:   return "Cannot unsubscribe an active consumer";
  case MQ_RECEIVE_QUEUE_CLOSED:                 return "The receive queue is closed";
  case MQ_RECEIVE_QUEUE_ERROR:                  return "Receive queue error";
  case MQ_NO_CONNECTION:                        return "The Session is not associated with a connection";
  case MQ_CONNECTION_CLOSED:                    return "The Session's connection has been closed";
  case MQ_INVALID_ACKNOWLEDGE_MODE:             return "Invalid acknowledge mode";
  case MQ_INVALID_DESTINATION_TYPE:             return "Invalid destination type";
  case MQ_INVALID_RECEIVE_MODE:                 return "Invalid receive mode";
  case MQ_NOT_SYNC_RECEIVE_MODE:                return "Session not in sync receive mode";
  case MQ_NOT_ASYNC_RECEIVE_MODE:               return "Session not in async receive mode";
  case MQ_TRANSACTED_SESSION:                   return "Session is transacted";
  case MQ_NOT_TRANSACTED_SESSION:               return "Session is not transacted";
  case MQ_SESSION_NOT_CLIENT_ACK_MODE:          return "Session not in client acknowledge mode";
  case MQ_TRANSACTION_ID_IN_USE:                return "Transaction id in use";
  case MQ_INVALID_TRANSACTION_ID:               return "Invalid transaction id";
  case MQ_THREAD_OUTSIDE_XA_TRANSACTION:        return "The calling thread is not associated with a XA transaction";
  case MQ_XA_SESSION_NO_TRANSATION:             return "The XA session has no active transaction";
  case MQ_XA_SESSION_IN_PROGRESS:               return "XA session is in progress";
  case MQ_SHARED_SUBSCRIPTION_NOT_TOPIC:        return "Shared subscription must use Topic destination";

    
  case MQ_MESSAGE_NO_DESTINATION:               return "The message does not have a destination";
  case MQ_DESTINATION_NO_CLASS:                 return "The destination does not have a class";
  case MQ_DESTINATION_NO_NAME:                  return "The destination does not have a name";
  case MQ_NO_REPLY_TO_DESTINATION:              return "The message does not have a reply to destination";

  case MQ_PRODUCER_NO_DESTINATION:              return "The producer does not have a specified destination";
  case MQ_PRODUCER_HAS_DESTINATION:             return "The producer has a specified destination";
  case MQ_INVALID_DELIVERY_MODE:                return "Invalid delivery mode";
  case MQ_INVALID_PRIORITY:                     return "Invalid priority";
  case MQ_PRODUCER_CLOSED:                      return "Producer closed";
  case MQ_SEND_NOT_FOUND:                       return "The destination this message was sent to could not be found";
  case MQ_SEND_TOO_LARGE:                       return "Message exceeds the single message size limit for the server or destination";
  case MQ_SEND_RESOURCE_FULL:                   return "Destination is full and is rejecting new messages";

  case MQ_CONSUMER_NO_DURABLE_NAME:             return "There is no durable name specified";
  case MQ_CONSUMER_NOT_INITIALIZED:             return "The consumer has not been initialized";
  case MQ_CONSUMER_EXCEPTION:                   return "An exception occurred on the consumer";
  case MQ_CONSUMER_NO_SESSION:                  return "The consumer has no session";
  case MQ_MESSAGE_NOT_IN_SESSION:               return "The message was not delivered to the session";
  case MQ_NO_MESSAGE:                           return "There was no message to receive";
  case MQ_CONSUMER_CLOSED:                      return "The consumer was closed";
  case MQ_INVALID_MESSAGE_SELECTOR:             return "Invalid message selector";
  case MQ_CONSUMER_NOT_FOUND:                   return "Message consumer not found";
  case MQ_DESTINATION_CONSUMER_LIMIT_EXCEEDED:  return "The number of consumers on the destination exceeded limit";
  case MQ_CONSUMER_DESTINATION_NOT_FOUND:       return "Destination that this consumer was on no longer exists";
  case MQ_NOLOCAL_DURABLE_CONSUMER_NO_CLIENTID: return "Client ID must set when noLocal is true to create durable subscription";
  case MQ_CONSUMER_NO_SUBSCRIPTION_NAME:        return "There is no subscription name specified";


  case MQ_CONNECTION_START_ERROR:               return "Connection start failed";
  case MQ_CONNECTION_CREATE_SESSION_ERROR:      return "Connection failed to create a session";
  case MQ_CONNECTION_OPEN_ERROR:                return "Connection failed to open a connection";
  case MQ_CONNECTION_UNSUPPORTED_TRANSPORT:     return "The transport specified is not supported";

  case MQ_HANDLED_OBJECT_INVALID_HANDLE_ERROR:  return "The object is invalid (i.e. it has been deleted).";
  case MQ_HANDLED_OBJECT_IN_USE:                return "The object could not be deleted because there is another reference to it";
  case MQ_HANDLED_OBJECT_NO_MORE_HANDLES:       return "A handle could not be allocated because the supply of handles has been exhausted";

  case MQ_REFERENCED_FREED_OBJECT_ERROR:        return "A freed object was referenced";

  case MQ_DESTINATION_NOT_TEMPORARY:            return "The destination is not temporary";
  case MQ_TEMPORARY_DESTINATION_NOT_IN_CONNECTION: return "The temporary destination is not in the connection";

  case MQ_CALLBACK_RUNTIME_ERROR:               return "Callback runtime error occurred";

  // These are only returned by the cshim layer
  case MQ_STATUS_INVALID_HANDLE:                return "The handle is invalid";
  case MQ_NO_MESSAGE_PROPERTIES:                return "There are no message properties";
  case MQ_STATUS_NULL_LOGGER:                   return "The logger was NULL";
  case MQ_STATUS_CONNECTION_NOT_CLOSED:         return "The connection cannot be deleted because it was not closed";
  case MQ_NOT_XA_CONNECTION:                    return "The connection is not XA connection";
  case MQ_ILLEGAL_CLOSE_XA_CONNECTION:          return "Illegal close a XA connection";


  /**************************************************************************** 
   * NSPR prerr.h
   *
   * These errors are not listed in NSPR public doc, hence not included: 
   * PR_OPERATION_ABORTED_ERROR
   * PR_NETWORK_DOWN_ERROR
   * PR_SOCKET_SHUTDOWN_ERROR
   * PR_CONNECT_ABORTED_ERROR
   * PR_HOST_UNREACHABLE_ERROR
   ****************************************************************************/
case PR_OUT_OF_MEMORY_ERROR:                    return "PR_OUT_OF_MEMORY_ERROR";
case PR_BAD_DESCRIPTOR_ERROR:                   return "PR_BAD_DESCRIPTOR_ERROR";
case PR_WOULD_BLOCK_ERROR:                      return "PR_WOULD_BLOCK_ERROR";
case PR_ACCESS_FAULT_ERROR:                     return "PR_ACCESS_FAULT_ERROR";
case PR_INVALID_METHOD_ERROR:                   return "PR_INVALID_METHOD_ERROR";
case PR_ILLEGAL_ACCESS_ERROR:                   return "PR_ILLEGAL_ACCESS_ERROR";
case PR_UNKNOWN_ERROR:                          return "PR_UNKNOWN_ERROR";
case PR_PENDING_INTERRUPT_ERROR:                return "PR_PENDING_INTERRUPT_ERROR";
case PR_NOT_IMPLEMENTED_ERROR:                  return "PR_NOT_IMPLEMENTED_ERROR";
case PR_IO_ERROR:                               return "PR_IO_ERROR";
case PR_IO_TIMEOUT_ERROR:                       return  "PR_IO_TIMEOUT_ERROR";
case PR_IO_PENDING_ERROR:                       return "PR_IO_PENDING_ERROR"; 
case PR_DIRECTORY_OPEN_ERROR:                   return "PR_DIRECTORY_OPEN_ERROR";
case PR_INVALID_ARGUMENT_ERROR:                 return "PR_INVALID_ARGUMENT_ERROR";
case PR_ADDRESS_NOT_AVAILABLE_ERROR:            return "PR_ADDRESS_NOT_AVAILABLE_ERROR";
case PR_ADDRESS_NOT_SUPPORTED_ERROR:            return "PR_ADDRESS_NOT_SUPPORTED_ERROR";
case PR_IS_CONNECTED_ERROR:                     return "PR_IS_CONNECTED_ERROR";
case PR_BAD_ADDRESS_ERROR:                      return "PR_BAD_ADDRESS_ERROR";
case PR_ADDRESS_IN_USE_ERROR:                   return "PR_ADDRESS_IN_USE_ERROR";
case PR_CONNECT_REFUSED_ERROR:                  return "PR_CONNECT_REFUSED_ERROR";
case PR_NETWORK_UNREACHABLE_ERROR:              return "PR_NETWORK_UNREACHABLE_ERROR";
case PR_CONNECT_TIMEOUT_ERROR:                  return "PR_CONNECT_TIMEOUT_ERROR";
case PR_NOT_CONNECTED_ERROR:                    return "PR_NOT_CONNECTED_ERROR";
case PR_LOAD_LIBRARY_ERROR:                     return "PR_LOAD_LIBRARY_ERROR";
case PR_UNLOAD_LIBRARY_ERROR:                   return "PR_UNLOAD_LIBRARY_ERROR";
case PR_FIND_SYMBOL_ERROR:                      return "PR_FIND_SYMBOL_ERROR";
case PR_INSUFFICIENT_RESOURCES_ERROR:           return "PR_INSUFFICIENT_RESOURCES_ERROR";
case PR_DIRECTORY_LOOKUP_ERROR:                 return "PR_DIRECTORY_LOOKUP_ERROR";
case PR_TPD_RANGE_ERROR:                        return "PR_TPD_RANGE_ERROR";
case PR_PROC_DESC_TABLE_FULL_ERROR:             return "PR_PROC_DESC_TABLE_FULL_ERROR";
case PR_SYS_DESC_TABLE_FULL_ERROR:              return "PR_SYS_DESC_TABLE_FULL_ERROR";
case PR_NOT_SOCKET_ERROR:                       return "PR_NOT_SOCKET_ERROR";
case PR_NOT_TCP_SOCKET_ERROR:                   return "PR_NOT_TCP_SOCKET_ERROR";
case PR_SOCKET_ADDRESS_IS_BOUND_ERROR:          return "PR_SOCKET_ADDRESS_IS_BOUND_ERROR";
case PR_NO_ACCESS_RIGHTS_ERROR:                 return "PR_NO_ACCESS_RIGHTS_ERROR";
case PR_OPERATION_NOT_SUPPORTED_ERROR:          return "PR_OPERATION_NOT_SUPPORTED_ERROR";
case PR_PROTOCOL_NOT_SUPPORTED_ERROR:           return "PR_PROTOCOL_NOT_SUPPORTED_ERROR";
case PR_REMOTE_FILE_ERROR:                      return "PR_REMOTE_FILE_ERROR";
case PR_BUFFER_OVERFLOW_ERROR:                  return "PR_BUFFER_OVERFLOW_ERROR";
case PR_CONNECT_RESET_ERROR:                    return "PR_CONNECT_RESET_ERROR";
case PR_RANGE_ERROR:                            return "PR_RANGE_ERROR";
case PR_DEADLOCK_ERROR:                         return "PR_DEADLOCK_ERROR";
case PR_FILE_IS_LOCKED_ERROR:                   return "PR_FILE_IS_LOCKED_ERROR";
case PR_FILE_TOO_BIG_ERROR:                     return "PR_FILE_TOO_BIG_ERROR";
case PR_NO_DEVICE_SPACE_ERROR:                  return "PR_NO_DEVICE_SPACE_ERROR";
case PR_PIPE_ERROR:                             return "PR_PIPE_ERROR";
case PR_NO_SEEK_DEVICE_ERROR:                   return "PR_NO_SEEK_DEVICE_ERROR";
case PR_IS_DIRECTORY_ERROR:                     return "PR_IS_DIRECTORY_ERROR";
case PR_LOOP_ERROR:                             return "PR_LOOP_ERROR";
case PR_NAME_TOO_LONG_ERROR :                   return "PR_NAME_TOO_LONG_ERROR";
case PR_FILE_NOT_FOUND_ERROR:                   return "PR_FILE_NOT_FOUND_ERROR";
case PR_NOT_DIRECTORY_ERROR:                    return "PR_NOT_DIRECTORY_ERROR";
case PR_READ_ONLY_FILESYSTEM_ERROR:             return "PR_READ_ONLY_FILESYSTEM_ERROR";
case PR_DIRECTORY_NOT_EMPTY_ERROR:              return "PR_DIRECTORY_NOT_EMPTY_ERROR";
case PR_FILESYSTEM_MOUNTED_ERROR :              return "PR_FILESYSTEM_MOUNTED_ERROR";
case PR_NOT_SAME_DEVICE_ERROR:                  return "PR_NOT_SAME_DEVICE_ERROR";
case PR_DIRECTORY_CORRUPTED_ERROR:              return "PR_DIRECTORY_CORRUPTED_ERROR";
case PR_FILE_EXISTS_ERROR:                      return "PR_FILE_EXISTS_ERROR";
case PR_MAX_DIRECTORY_ENTRIES_ERROR:            return "PR_MAX_DIRECTORY_ENTRIES_ERROR";
case PR_INVALID_DEVICE_STATE_ERROR:             return "PR_INVALID_DEVICE_STATE_ERROR";
case PR_DEVICE_IS_LOCKED_ERROR:                 return "PR_DEVICE_IS_LOCKED_ERROR";
case PR_NO_MORE_FILES_ERROR:                    return "PR_NO_MORE_FILES_ERROR";
case PR_END_OF_FILE_ERROR:                      return "PR_END_OF_FILE_ERROR";
case PR_FILE_SEEK_ERROR :                       return "PR_FILE_SEEK_ERROR";
case PR_FILE_IS_BUSY_ERROR:                     return "PR_FILE_IS_BUSY_ERROR";
case PR_IN_PROGRESS_ERROR:                      return "PR_IN_PROGRESS_ERROR"; 
case PR_ALREADY_INITIATED_ERROR :               return "PR_ALREADY_INITIATED_ERROR";
case PR_GROUP_EMPTY_ERROR:                      return "PR_GROUP_EMPTY_ERROR";
case PR_INVALID_STATE_ERROR:                    return "PR_INVALID_STATE_ERROR";

  /**************************************************************************** 
   * NSS secerr.h
   *
   * These errors are listed in NSS public doc but not found in NSS headers,
   * hence not included:
   * SEC_ERROR_MODULE_STUCK 
   * SEC_ERROR_BAD_TEMPLATE
   * SEC_ERROR_CRL_NOT_FOUND
   * SEC_ERROR_REUSED_ISSUER_AND_SERIAL
   * SEC_ERROR_BUSY
   * SEC_ERROR_EXTRA_INPUT
   * SEC_ERROR_UNSUPPORTED_ELLIPTIC_CURVE
   * SEC_ERROR_UNSUPPORTED_EC_POINT_FORM
   * SEC_ERROR_UNRECONGNIZED_OID
   * SEC_ERROR_OCSP_INVALID_SIGNING_CERT
   ****************************************************************************/
case SEC_ERROR_IO: 				              return "SEC_ERROR_IO";
case SEC_ERROR_LIBRARY_FAILURE: 		      return "SEC_ERROR_LIBRARY_FAILURE";
case SEC_ERROR_BAD_DATA:			          return "SEC_ERROR_BAD_DATA";
case SEC_ERROR_OUTPUT_LEN: 			          return "SEC_ERROR_OUTPUT_LEN";
case SEC_ERROR_INPUT_LEN: 			          return "SEC_ERROR_INPUT_LEN";
case SEC_ERROR_INVALID_ARGS: 			      return "SEC_ERROR_INVALID_ARGS";
case SEC_ERROR_INVALID_ALGORITHM: 		      return "SEC_ERROR_INVALID_ALGORITHM";
case SEC_ERROR_INVALID_AVA:			          return "SEC_ERROR_INVALID_AVA"; 
case SEC_ERROR_INVALID_TIME: 			      return "SEC_ERROR_INVALID_TIME";
case SEC_ERROR_BAD_DER:			              return "SEC_ERROR_BAD_DER";
case SEC_ERROR_BAD_SIGNATURE: 		          return "SEC_ERROR_BAD_SIGNATURE";
case SEC_ERROR_EXPIRED_CERTIFICATE:		      return "SEC_ERROR_EXPIRED_CERTIFICATE";
case SEC_ERROR_REVOKED_CERTIFICATE:		      return "SEC_ERROR_REVOKED_CERTIFICATE";
case SEC_ERROR_UNKNOWN_ISSUER: 		          return "SEC_ERROR_UNKNOWN_ISSUER";
case SEC_ERROR_BAD_KEY:			              return "SEC_ERROR_BAD_KEY";
case SEC_ERROR_BAD_PASSWORD: 			      return "SEC_ERROR_BAD_PASSWORD";
case SEC_ERROR_RETRY_PASSWORD: 		          return "SEC_ERROR_RETRY_PASSWORD";
case SEC_ERROR_NO_NODELOCK:			          return "SEC_ERROR_NO_NODELOCK";
case SEC_ERROR_BAD_DATABASE:			      return "SEC_ERROR_BAD_DATABASE";
case SEC_ERROR_NO_MEMORY: 			          return "SEC_ERROR_NO_MEMORY";
case SEC_ERROR_UNTRUSTED_ISSUER: 		      return "SEC_ERROR_UNTRUSTED_ISSUER";
case SEC_ERROR_UNTRUSTED_CERT: 		          return "SEC_ERROR_UNTRUSTED_CERT";
case SEC_ERROR_DUPLICATE_CERT: 		          return "SEC_ERROR_DUPLICATE_CERT";
case SEC_ERROR_DUPLICATE_CERT_NAME: 	      return "SEC_ERROR_DUPLICATE_CERT_NAME";
case SEC_ERROR_ADDING_CERT:			          return "SEC_ERROR_ADDING_CERT";
case SEC_ERROR_FILING_KEY: 			          return "SEC_ERROR_FILING_KEY";
case SEC_ERROR_NO_KEY: 			              return "SEC_ERROR_NO_KEY";
case SEC_ERROR_CERT_VALID: 			          return "SEC_ERROR_CERT_VALID";
case SEC_ERROR_CERT_NOT_VALID: 		          return "SEC_ERROR_CERT_NOT_VALID";
case SEC_ERROR_CERT_NO_RESPONSE: 		      return "SEC_ERROR_CERT_NO_RESPONSE";
case SEC_ERROR_EXPIRED_ISSUER_CERTIFICATE: 	  return "SEC_ERROR_EXPIRED_ISSUER_CERTIFICATE";
case SEC_ERROR_CRL_EXPIRED:			          return "SEC_ERROR_CRL_EXPIRED";
case SEC_ERROR_CRL_BAD_SIGNATURE: 		      return "SEC_ERROR_CRL_BAD_SIGNATURE";
case SEC_ERROR_CRL_INVALID:			          return "SEC_ERROR_CRL_INVALID";
case SEC_ERROR_EXTENSION_VALUE_INVALID:	      return "SEC_ERROR_EXTENSION_VALUE_INVALID";
case SEC_ERROR_EXTENSION_NOT_FOUND:		      return "SEC_ERROR_EXTENSION_NOT_FOUND";
case SEC_ERROR_CA_CERT_INVALID:		          return "SEC_ERROR_CA_CERT_INVALID";
case SEC_ERROR_PATH_LEN_CONSTRAINT_INVALID:	  return "SEC_ERROR_PATH_LEN_CONSTRAINT_INVALID";
case SEC_ERROR_CERT_USAGES_INVALID:		      return "SEC_ERROR_CERT_USAGES_INVALID";
case SEC_INTERNAL_ONLY:			              return "SEC_INTERNAL_ONLY";
case SEC_ERROR_INVALID_KEY:			          return "SEC_ERROR_INVALID_KEY";
case SEC_ERROR_UNKNOWN_CRITICAL_EXTENSION: 	  return "SEC_ERROR_UNKNOWN_CRITICAL_EXTENSION";
case SEC_ERROR_OLD_CRL:			              return "SEC_ERROR_OLD_CRL";
case SEC_ERROR_NO_EMAIL_CERT: 		          return "SEC_ERROR_NO_EMAIL_CERT";
case SEC_ERROR_NO_RECIPIENT_CERTS_QUERY: 	  return "SEC_ERROR_NO_RECIPIENT_CERTS_QUERY";
case SEC_ERROR_NOT_A_RECIPIENT:		          return "SEC_ERROR_NOT_A_RECIPIENT";
case SEC_ERROR_PKCS7_KEYALG_MISMATCH: 	      return "SEC_ERROR_PKCS7_KEYALG_MISMATCH";
case SEC_ERROR_PKCS7_BAD_SIGNATURE:		      return "SEC_ERROR_PKCS7_BAD_SIGNATURE";
case SEC_ERROR_UNSUPPORTED_KEYALG: 		      return "SEC_ERROR_UNSUPPORTED_KEYALG";
case SEC_ERROR_DECRYPTION_DISALLOWED: 	      return "SEC_ERROR_DECRYPTION_DISALLOWED";
/* Fortezza Alerts */
case XP_SEC_FORTEZZA_BAD_CARD: 		          return "XP_SEC_FORTEZZA_BAD_CARD";
case XP_SEC_FORTEZZA_NO_CARD: 		          return "XP_SEC_FORTEZZA_NO_CARD";
case XP_SEC_FORTEZZA_NONE_SELECTED:		      return "XP_SEC_FORTEZZA_NONE_SELECTED";
case XP_SEC_FORTEZZA_MORE_INFO:		          return "XP_SEC_FORTEZZA_MORE_INFO";
case XP_SEC_FORTEZZA_PERSON_NOT_FOUND: 	      return "XP_SEC_FORTEZZA_PERSON_NOT_FOUND";
case XP_SEC_FORTEZZA_NO_MORE_INFO: 		      return "XP_SEC_FORTEZZA_NO_MORE_INFO";
case XP_SEC_FORTEZZA_BAD_PIN: 		          return "XP_SEC_FORTEZZA_BAD_PIN";
case XP_SEC_FORTEZZA_PERSON_ERROR: 		      return "XP_SEC_FORTEZZA_PERSON_ERROR";
case SEC_ERROR_NO_KRL: 			              return "SEC_ERROR_NO_KRL";
case SEC_ERROR_KRL_EXPIRED:			          return "SEC_ERROR_KRL_EXPIRED";
case SEC_ERROR_KRL_BAD_SIGNATURE: 		      return "SEC_ERROR_KRL_BAD_SIGNATURE";
case SEC_ERROR_REVOKED_KEY:			          return "SEC_ERROR_REVOKED_KEY";
case SEC_ERROR_KRL_INVALID:			          return "SEC_ERROR_KRL_INVALID";
case SEC_ERROR_NEED_RANDOM:			          return "SEC_ERROR_NEED_RANDOM";
case SEC_ERROR_NO_MODULE: 			          return "SEC_ERROR_NO_MODULE";
case SEC_ERROR_NO_TOKEN: 			          return "SEC_ERROR_NO_TOKEN";
case SEC_ERROR_READ_ONLY: 			          return "SEC_ERROR_READ_ONLY";
case SEC_ERROR_NO_SLOT_SELECTED: 		      return "SEC_ERROR_NO_SLOT_SELECTED";
case SEC_ERROR_CERT_NICKNAME_COLLISION:	      return "SEC_ERROR_CERT_NICKNAME_COLLISION";
case SEC_ERROR_KEY_NICKNAME_COLLISION: 	      return "SEC_ERROR_KEY_NICKNAME_COLLISION";
case SEC_ERROR_SAFE_NOT_CREATED: 		      return "SEC_ERROR_SAFE_NOT_CREATED";
case SEC_ERROR_BAGGAGE_NOT_CREATED:		      return "SEC_ERROR_BAGGAGE_NOT_CREATED";
case XP_JAVA_REMOVE_PRINCIPAL_ERROR: 		  return "XP_JAVA_REMOVE_PRINCIPAL_ERROR";
case XP_JAVA_DELETE_PRIVILEGE_ERROR: 		  return "XP_JAVA_DELETE_PRIVILEGE_ERROR";
case XP_JAVA_CERT_NOT_EXISTS_ERROR:		      return "XP_JAVA_CERT_NOT_EXISTS_ERROR";
case SEC_ERROR_BAD_EXPORT_ALGORITHM: 		  return "SEC_ERROR_BAD_EXPORT_ALGORITHM";
case SEC_ERROR_EXPORTING_CERTIFICATES: 	      return "SEC_ERROR_EXPORTING_CERTIFICATES";
case SEC_ERROR_IMPORTING_CERTIFICATES: 	      return "SEC_ERROR_IMPORTING_CERTIFICATES";
case SEC_ERROR_PKCS12_DECODING_PFX:		      return "SEC_ERROR_PKCS12_DECODING_PFX";
case SEC_ERROR_PKCS12_INVALID_MAC: 		      return "SEC_ERROR_PKCS12_INVALID_MAC";
case SEC_ERROR_PKCS12_UNSUPPORTED_MAC_ALGORITHM:  return "SEC_ERROR_PKCS12_UNSUPPORTED_MAC_ALGORITHM"; 
case SEC_ERROR_PKCS12_UNSUPPORTED_TRANSPORT_MODE: return "SEC_ERROR_PKCS12_UNSUPPORTED_TRANSPORT_MODE";
case SEC_ERROR_PKCS12_CORRUPT_PFX_STRUCTURE:   	  return "SEC_ERROR_PKCS12_CORRUPT_PFX_STRUCTURE";
case SEC_ERROR_PKCS12_UNSUPPORTED_PBE_ALGORITHM:  return "SEC_ERROR_PKCS12_UNSUPPORTED_PBE_ALGORITHM";
case SEC_ERROR_PKCS12_UNSUPPORTED_VERSION:        return "SEC_ERROR_PKCS12_UNSUPPORTED_VERSION";
case SEC_ERROR_PKCS12_PRIVACY_PASSWORD_INCORRECT: return "SEC_ERROR_PKCS12_PRIVACY_PASSWORD_INCORRECT";
case SEC_ERROR_PKCS12_CERT_COLLISION:             return "SEC_ERROR_PKCS12_CERT_COLLISION";
case SEC_ERROR_USER_CANCELLED:                    return "SEC_ERROR_USER_CANCELLED";
case SEC_ERROR_PKCS12_DUPLICATE_DATA:             return "SEC_ERROR_PKCS12_DUPLICATE_DATA";
case SEC_ERROR_MESSAGE_SEND_ABORTED:              return "SEC_ERROR_MESSAGE_SEND_ABORTED";
case SEC_ERROR_INADEQUATE_KEY_USAGE:              return "SEC_ERROR_INADEQUATE_KEY_USAGE";
case SEC_ERROR_INADEQUATE_CERT_TYPE: 		      return "SEC_ERROR_INADEQUATE_CERT_TYPE";
case SEC_ERROR_CERT_ADDR_MISMATCH:                return "SEC_ERROR_CERT_ADDR_MISMATCH";
case SEC_ERROR_PKCS12_UNABLE_TO_IMPORT_KEY:       return "SEC_ERROR_PKCS12_UNABLE_TO_IMPORT_KEY";
case SEC_ERROR_PKCS12_IMPORTING_CERT_CHAIN:	      return "SEC_ERROR_PKCS12_IMPORTING_CERT_CHAIN";
case SEC_ERROR_PKCS12_UNABLE_TO_LOCATE_OBJECT_BY_NAME: return "SEC_ERROR_PKCS12_UNABLE_TO_LOCATE_OBJECT_BY_NAME";
case SEC_ERROR_PKCS12_UNABLE_TO_EXPORT_KEY:       return "SEC_ERROR_PKCS12_UNABLE_TO_EXPORT_KEY";
case SEC_ERROR_PKCS12_UNABLE_TO_WRITE:            return "SEC_ERROR_PKCS12_UNABLE_TO_WRITE";
case SEC_ERROR_PKCS12_UNABLE_TO_READ:             return "SEC_ERROR_PKCS12_UNABLE_TO_READ";
case SEC_ERROR_PKCS12_KEY_DATABASE_NOT_INITIALIZED:  return "SEC_ERROR_PKCS12_KEY_DATABASE_NOT_INITIALIZED";
case SEC_ERROR_KEYGEN_FAIL:                     return "SEC_ERROR_KEYGEN_FAIL";
case SEC_ERROR_INVALID_PASSWORD:                return "SEC_ERROR_INVALID_PASSWORD";
case SEC_ERROR_RETRY_OLD_PASSWORD:              return "SEC_ERROR_RETRY_OLD_PASSWORD";
case SEC_ERROR_BAD_NICKNAME:                    return "SEC_ERROR_BAD_NICKNAME";
case SEC_ERROR_NOT_FORTEZZA_ISSUER:             return "SEC_ERROR_NOT_FORTEZZA_ISSUER";
case SEC_ERROR_CANNOT_MOVE_SENSITIVE_KEY:       return "SEC_ERROR_CANNOT_MOVE_SENSITIVE_KEY";
case SEC_ERROR_JS_INVALID_MODULE_NAME:          return "SEC_ERROR_JS_INVALID_MODULE_NAME";
case SEC_ERROR_JS_INVALID_DLL:		            return "SEC_ERROR_JS_INVALID_DLL";
case SEC_ERROR_JS_ADD_MOD_FAILURE: 		        return "SEC_ERROR_JS_ADD_MOD_FAILURE";
case SEC_ERROR_JS_DEL_MOD_FAILURE: 		        return "SEC_ERROR_JS_DEL_MOD_FAILURE";
case SEC_ERROR_OLD_KRL:			                return "SEC_ERROR_OLD_KRL";
case SEC_ERROR_CKL_CONFLICT: 			        return "SEC_ERROR_CKL_CONFLICT";
case SEC_ERROR_CERT_NOT_IN_NAME_SPACE: 	        return "SEC_ERROR_CERT_NOT_IN_NAME_SPACE";
case SEC_ERROR_KRL_NOT_YET_VALID: 		        return "SEC_ERROR_KRL_NOT_YET_VALID";
case SEC_ERROR_CRL_NOT_YET_VALID: 		        return "SEC_ERROR_CRL_NOT_YET_VALID";
case SEC_ERROR_UNKNOWN_CERT: 			        return "SEC_ERROR_UNKNOWN_CERT";
case SEC_ERROR_UNKNOWN_SIGNER: 		            return "SEC_ERROR_UNKNOWN_SIGNER";
case SEC_ERROR_CERT_BAD_ACCESS_LOCATION:	    return "SEC_ERROR_CERT_BAD_ACCESS_LOCATION";
case SEC_ERROR_OCSP_UNKNOWN_RESPONSE_TYPE: 	    return "SEC_ERROR_OCSP_UNKNOWN_RESPONSE_TYPE";
case SEC_ERROR_OCSP_BAD_HTTP_RESPONSE: 	        return "SEC_ERROR_OCSP_BAD_HTTP_RESPONSE";
case SEC_ERROR_OCSP_MALFORMED_REQUEST: 	        return "SEC_ERROR_OCSP_MALFORMED_REQUEST";
case SEC_ERROR_OCSP_SERVER_ERROR: 		        return "SEC_ERROR_OCSP_SERVER_ERROR";
case SEC_ERROR_OCSP_TRY_SERVER_LATER: 	        return "SEC_ERROR_OCSP_TRY_SERVER_LATER";
case SEC_ERROR_OCSP_REQUEST_NEEDS_SIG: 	        return "SEC_ERROR_OCSP_REQUEST_NEEDS_SIG";
case SEC_ERROR_OCSP_UNAUTHORIZED_REQUEST: 	    return "SEC_ERROR_OCSP_UNAUTHORIZED_REQUEST";
case SEC_ERROR_OCSP_UNKNOWN_RESPONSE_STATUS: 	return "SEC_ERROR_OCSP_UNKNOWN_RESPONSE_STATUS";
case SEC_ERROR_OCSP_UNKNOWN_CERT: 		        return "SEC_ERROR_OCSP_UNKNOWN_CERT";
case SEC_ERROR_OCSP_NOT_ENABLED: 		        return "SEC_ERROR_OCSP_NOT_ENABLED";
case SEC_ERROR_OCSP_NO_DEFAULT_RESPONDER: 	    return "SEC_ERROR_OCSP_NO_DEFAULT_RESPONDER";
case SEC_ERROR_OCSP_MALFORMED_RESPONSE:	        return "SEC_ERROR_OCSP_MALFORMED_RESPONSE";
case SEC_ERROR_OCSP_UNAUTHORIZED_RESPONSE: 	    return "SEC_ERROR_OCSP_UNAUTHORIZED_RESPONSE";
case SEC_ERROR_OCSP_FUTURE_RESPONSE: 		    return "SEC_ERROR_OCSP_FUTURE_RESPONSE";
case SEC_ERROR_OCSP_OLD_RESPONSE: 		        return "SEC_ERROR_OCSP_OLD_RESPONSE";
/* smime stuff */
case SEC_ERROR_DIGEST_NOT_FOUND:                return "SEC_ERROR_DIGEST_NOT_FOUND";
case SEC_ERROR_UNSUPPORTED_MESSAGE_TYPE:        return "SEC_ERROR_UNSUPPORTED_MESSAGE_TYPE";


  /**************************************************************************** 
   * NSS sslerr.h
   *
   ****************************************************************************/
case SSL_ERROR_EXPORT_ONLY_SERVER: 		        return "SSL_ERROR_EXPORT_ONLY_SERVER";
case SSL_ERROR_US_ONLY_SERVER: 		            return "SSL_ERROR_US_ONLY_SERVER";
case SSL_ERROR_NO_CYPHER_OVERLAP: 		        return "SSL_ERROR_NO_CYPHER_OVERLAP";
case SSL_ERROR_NO_CERTIFICATE:                  return "SSL_ERROR_NO_CERTIFICATE";
case SSL_ERROR_BAD_CERTIFICATE:            	    return "SSL_ERROR_BAD_CERTIFICATE";
case SSL_ERROR_BAD_CLIENT: 			            return "SSL_ERROR_BAD_CLIENT";
case SSL_ERROR_BAD_SERVER: 			            return "SSL_ERROR_BAD_SERVER";
case SSL_ERROR_UNSUPPORTED_CERTIFICATE_TYPE:    return "SSL_ERROR_UNSUPPORTED_CERTIFICATE_TYPE";
case SSL_ERROR_UNSUPPORTED_VERSION:             return "SSL_ERROR_UNSUPPORTED_VERSION";
case SSL_ERROR_WRONG_CERTIFICATE:		        return "SSL_ERROR_WRONG_CERTIFICATE";
case SSL_ERROR_BAD_CERT_DOMAIN:                 return "SSL_ERROR_BAD_CERT_DOMAIN";
case SSL_ERROR_POST_WARNING: 			        return "SSL_ERROR_POST_WARNING";
case SSL_ERROR_SSL2_DISABLED: 		            return "SSL_ERROR_SSL2_DISABLED";
case SSL_ERROR_BAD_MAC_READ: 			        return "SSL_ERROR_BAD_MAC_READ";
case SSL_ERROR_BAD_MAC_ALERT: 		            return "SSL_ERROR_BAD_MAC_ALERT";
case SSL_ERROR_BAD_CERT_ALERT:                   return "SSL_ERROR_BAD_CERT_ALERT";
case SSL_ERROR_REVOKED_CERT_ALERT: 		        return "SSL_ERROR_REVOKED_CERT_ALERT";
case SSL_ERROR_EXPIRED_CERT_ALERT: 		        return "SSL_ERROR_EXPIRED_CERT_ALERT";

case SSL_ERROR_SSL_DISABLED: 			        return "SSL_ERROR_SSL_DISABLED";
case SSL_ERROR_FORTEZZA_PQG: 			        return "SSL_ERROR_FORTEZZA_PQG";
case SSL_ERROR_UNKNOWN_CIPHER_SUITE:            return "SSL_ERROR_UNKNOWN_CIPHER_SUITE";
case SSL_ERROR_NO_CIPHERS_SUPPORTED:	        return "SSL_ERROR_NO_CIPHERS_SUPPORTED";
case SSL_ERROR_BAD_BLOCK_PADDING:		        return "SSL_ERROR_BAD_BLOCK_PADDING";
case SSL_ERROR_RX_RECORD_TOO_LONG:		        return "SSL_ERROR_RX_RECORD_TOO_LONG";
case SSL_ERROR_TX_RECORD_TOO_LONG:		        return "SSL_ERROR_TX_RECORD_TOO_LONG";

case SSL_ERROR_RX_MALFORMED_HELLO_REQUEST:	    return "SSL_ERROR_RX_MALFORMED_HELLO_REQUEST";
case SSL_ERROR_RX_MALFORMED_CLIENT_HELLO:       return "SSL_ERROR_RX_MALFORMED_CLIENT_HELLO";
case SSL_ERROR_RX_MALFORMED_SERVER_HELLO:	    return "SSL_ERROR_RX_MALFORMED_SERVER_HELLO";
case SSL_ERROR_RX_MALFORMED_CERTIFICATE:        return "SSL_ERROR_RX_MALFORMED_CERTIFICATE";
case SSL_ERROR_RX_MALFORMED_SERVER_KEY_EXCH:    return "SSL_ERROR_RX_MALFORMED_SERVER_KEY_EXCH";
case SSL_ERROR_RX_MALFORMED_CERT_REQUEST:	    return "SSL_ERROR_RX_MALFORMED_CERT_REQUEST";
case SSL_ERROR_RX_MALFORMED_HELLO_DONE:	        return "SSL_ERROR_RX_MALFORMED_HELLO_DONE";
case SSL_ERROR_RX_MALFORMED_CERT_VERIFY:        return "SSL_ERROR_RX_MALFORMED_CERT_VERIFY";
case SSL_ERROR_RX_MALFORMED_CLIENT_KEY_EXCH:    return "SSL_ERROR_RX_MALFORMED_CLIENT_KEY_EXCH";
case SSL_ERROR_RX_MALFORMED_FINISHED: 	        return "SSL_ERROR_RX_MALFORMED_FINISHED";

case SSL_ERROR_RX_MALFORMED_CHANGE_CIPHER: 	    return "SSL_ERROR_RX_MALFORMED_CHANGE_CIPHER";
case SSL_ERROR_RX_MALFORMED_ALERT:	 	        return "SSL_ERROR_RX_MALFORMED_ALERT";
case SSL_ERROR_RX_MALFORMED_HANDSHAKE: 	        return "SSL_ERROR_RX_MALFORMED_HANDSHAKE";
case SSL_ERROR_RX_MALFORMED_APPLICATION_DATA:	return "SSL_ERROR_RX_MALFORMED_APPLICATION_DATA";

case SSL_ERROR_RX_UNEXPECTED_HELLO_REQUEST:	    return "SSL_ERROR_RX_UNEXPECTED_HELLO_REQUEST";
case SSL_ERROR_RX_UNEXPECTED_CLIENT_HELLO:	    return "SSL_ERROR_RX_UNEXPECTED_CLIENT_HELLO";
case SSL_ERROR_RX_UNEXPECTED_SERVER_HELLO:	    return "SSL_ERROR_RX_UNEXPECTED_SERVER_HELLO";
case SSL_ERROR_RX_UNEXPECTED_CERTIFICATE:	    return "SSL_ERROR_RX_UNEXPECTED_CERTIFICATE";
case SSL_ERROR_RX_UNEXPECTED_SERVER_KEY_EXCH:   return "SSL_ERROR_RX_UNEXPECTED_SERVER_KEY_EXCH";
case SSL_ERROR_RX_UNEXPECTED_CERT_REQUEST:	    return "SSL_ERROR_RX_UNEXPECTED_CERT_REQUEST";
case SSL_ERROR_RX_UNEXPECTED_HELLO_DONE:        return "SSL_ERROR_RX_UNEXPECTED_HELLO_DONE";
case SSL_ERROR_RX_UNEXPECTED_CERT_VERIFY:	    return "SSL_ERROR_RX_UNEXPECTED_CERT_VERIFY";
case SSL_ERROR_RX_UNEXPECTED_CLIENT_KEY_EXCH:   return "SSL_ERROR_RX_UNEXPECTED_CLIENT_KEY_EXCH";	
case SSL_ERROR_RX_UNEXPECTED_FINISHED: 	        return "SSL_ERROR_RX_UNEXPECTED_FINISHED";

case SSL_ERROR_RX_UNEXPECTED_CHANGE_CIPHER:	    return "SSL_ERROR_RX_UNEXPECTED_CHANGE_CIPHER";
case SSL_ERROR_RX_UNEXPECTED_ALERT:	 	        return "SSL_ERROR_RX_UNEXPECTED_ALERT";
case SSL_ERROR_RX_UNEXPECTED_HANDSHAKE:         return "SSL_ERROR_RX_UNEXPECTED_HANDSHAKE";
case SSL_ERROR_RX_UNEXPECTED_APPLICATION_DATA:  return "SSL_ERROR_RX_UNEXPECTED_APPLICATION_DATA";

case SSL_ERROR_RX_UNKNOWN_RECORD_TYPE:	        return "SSL_ERROR_RX_UNKNOWN_RECORD_TYPE"; 
case SSL_ERROR_RX_UNKNOWN_HANDSHAKE: 		    return "SSL_ERROR_RX_UNKNOWN_HANDSHAKE";
case SSL_ERROR_RX_UNKNOWN_ALERT: 	            return "SSL_ERROR_RX_UNKNOWN_ALERT";	

case SSL_ERROR_CLOSE_NOTIFY_ALERT: 		        return "SSL_ERROR_CLOSE_NOTIFY_ALERT";
case SSL_ERROR_HANDSHAKE_UNEXPECTED_ALERT: 	    return "SSL_ERROR_HANDSHAKE_UNEXPECTED_ALERT";
case SSL_ERROR_DECOMPRESSION_FAILURE_ALERT:	    return "SSL_ERROR_DECOMPRESSION_FAILURE_ALERT";
case SSL_ERROR_HANDSHAKE_FAILURE_ALERT:	        return "SSL_ERROR_HANDSHAKE_FAILURE_ALERT";
case SSL_ERROR_ILLEGAL_PARAMETER_ALERT:	        return "SSL_ERROR_ILLEGAL_PARAMETER_ALERT";
case SSL_ERROR_UNSUPPORTED_CERT_ALERT: 	        return "SSL_ERROR_UNSUPPORTED_CERT_ALERT";
case SSL_ERROR_CERTIFICATE_UNKNOWN_ALERT: 	    return "SSL_ERROR_CERTIFICATE_UNKNOWN_ALERT";

case SSL_ERROR_GENERATE_RANDOM_FAILURE:	        return "SSL_ERROR_GENERATE_RANDOM_FAILURE";
case SSL_ERROR_SIGN_HASHES_FAILURE:	            return "SSL_ERROR_SIGN_HASHES_FAILURE";
case SSL_ERROR_EXTRACT_PUBLIC_KEY_FAILURE:	    return "SSL_ERROR_EXTRACT_PUBLIC_KEY_FAILURE";
case SSL_ERROR_SERVER_KEY_EXCHANGE_FAILURE:	    return "SSL_ERROR_SERVER_KEY_EXCHANGE_FAILURE";
case SSL_ERROR_CLIENT_KEY_EXCHANGE_FAILURE:	    return "SSL_ERROR_CLIENT_KEY_EXCHANGE_FAILURE";

case SSL_ERROR_ENCRYPTION_FAILURE:		        return "SSL_ERROR_ENCRYPTION_FAILURE";
case SSL_ERROR_DECRYPTION_FAILURE:		        return "SSL_ERROR_DECRYPTION_FAILURE";
case SSL_ERROR_SOCKET_WRITE_FAILURE:	        return "SSL_ERROR_SOCKET_WRITE_FAILURE";

case SSL_ERROR_MD5_DIGEST_FAILURE:		        return "SSL_ERROR_MD5_DIGEST_FAILURE";
case SSL_ERROR_SHA_DIGEST_FAILURE:		        return "SSL_ERROR_SHA_DIGEST_FAILURE";
case SSL_ERROR_MAC_COMPUTATION_FAILURE:	        return "SSL_ERROR_MAC_COMPUTATION_FAILURE";
case SSL_ERROR_SYM_KEY_CONTEXT_FAILURE:	        return "SSL_ERROR_SYM_KEY_CONTEXT_FAILURE";
case SSL_ERROR_SYM_KEY_UNWRAP_FAILURE:	        return "SSL_ERROR_SYM_KEY_UNWRAP_FAILURE";
case SSL_ERROR_PUB_KEY_SIZE_LIMIT_EXCEEDED:	    return "SSL_ERROR_PUB_KEY_SIZE_LIMIT_EXCEEDED";
case SSL_ERROR_IV_PARAM_FAILURE:	            return "SSL_ERROR_IV_PARAM_FAILURE";
case SSL_ERROR_INIT_CIPHER_SUITE_FAILURE:	    return "SSL_ERROR_INIT_CIPHER_SUITE_FAILURE";
case SSL_ERROR_SESSION_KEY_GEN_FAILURE:	        return "SSL_ERROR_SESSION_KEY_GEN_FAILURE";
case SSL_ERROR_NO_SERVER_KEY_FOR_ALG:	        return "SSL_ERROR_NO_SERVER_KEY_FOR_ALG";
case SSL_ERROR_TOKEN_INSERTION_REMOVAL:	        return "SSL_ERROR_TOKEN_INSERTION_REMOVAL";
case SSL_ERROR_TOKEN_SLOT_NOT_FOUND:	        return "SSL_ERROR_TOKEN_SLOT_NOT_FOUND";
case SSL_ERROR_NO_COMPRESSION_OVERLAP:	        return "SSL_ERROR_NO_COMPRESSION_OVERLAP";
case SSL_ERROR_HANDSHAKE_NOT_COMPLETED:	        return "SSL_ERROR_HANDSHAKE_NOT_COMPLETED";
case SSL_ERROR_BAD_HANDSHAKE_HASH_VALUE:        return "SSL_ERROR_BAD_HANDSHAKE_HASH_VALUE";
case SSL_ERROR_CERT_KEA_MISMATCH:		        return "SSL_ERROR_CERT_KEA_MISMATCH";
case SSL_ERROR_NO_TRUSTED_SSL_CLIENT_CA:        return "SSL_ERROR_NO_TRUSTED_SSL_CLIENT_CA";
case SSL_ERROR_SESSION_NOT_FOUND:		        return "SSL_ERROR_SESSION_NOT_FOUND";

case SSL_ERROR_DECRYPTION_FAILED_ALERT:	        return "SSL_ERROR_DECRYPTION_FAILED_ALERT";
case SSL_ERROR_RECORD_OVERFLOW_ALERT:		    return "SSL_ERROR_RECORD_OVERFLOW_ALERT";
case SSL_ERROR_UNKNOWN_CA_ALERT:	            return "SSL_ERROR_UNKNOWN_CA_ALERT";
case SSL_ERROR_ACCESS_DENIED_ALERT:		        return "SSL_ERROR_ACCESS_DENIED_ALERT";
case SSL_ERROR_DECODE_ERROR_ALERT:		        return "SSL_ERROR_DECODE_ERROR_ALERT";
case SSL_ERROR_DECRYPT_ERROR_ALERT:		        return "SSL_ERROR_DECRYPT_ERROR_ALERT";
case SSL_ERROR_EXPORT_RESTRICTION_ALERT:        return "SSL_ERROR_EXPORT_RESTRICTION_ALERT";
case SSL_ERROR_PROTOCOL_VERSION_ALERT:	        return "SSL_ERROR_PROTOCOL_VERSION_ALERT";
case SSL_ERROR_INSUFFICIENT_SECURITY_ALERT:	    return "SSL_ERROR_INSUFFICIENT_SECURITY_ALERT";
case SSL_ERROR_INTERNAL_ERROR_ALERT:	        return "SSL_ERROR_INTERNAL_ERROR_ALERT";
case SSL_ERROR_USER_CANCELED_ALERT:		        return "SSL_ERROR_USER_CANCELED_ALERT";
case SSL_ERROR_NO_RENEGOTIATION_ALERT:	        return "SSL_ERROR_NO_RENEGOTIATION_ALERT";


  default:
#ifdef DEBUG
    fprintf(stderr, "!! No mapping for error code %d\n", errorCode); 
    /*BREAKPOINT();*/  // See what error this is
#endif
    return "Unknown Error";
  };
}
