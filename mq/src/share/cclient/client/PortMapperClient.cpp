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
 * @(#)PortMapperClient.cpp	1.7 06/26/07
 */ 

#include "PortMapperClient.hpp"
#include "../util/UtilityMacros.h"
#include "TransportProtocolHandler.hpp"
#include "../io/TCPSocket.hpp"


PortMapperClient::PortMapperClient()
{
  CHECK_OBJECT_VALIDITY();

}

MQError
PortMapperClient::readBrokerPorts(const Properties * const connectionProperties)
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS;

  portMapperTable.reset();

  // Get the host name property
  const char * brokerName = NULL;
  errorCode = connectionProperties->getStringProperty(
                MQ_BROKER_HOST_PROPERTY, &brokerName);
  if (errorCode != MQ_SUCCESS) {
    MQ_ERROR_TRACE( "readBrokerPort", errorCode );
    return errorCode;
  }

  // Get the host port property
  PRInt32 brokerPort = 0;
  errorCode = connectionProperties->getIntegerProperty(
                MQ_BROKER_PORT_PROPERTY, &brokerPort);
  if (errorCode != MQ_SUCCESS) {
    MQ_ERROR_TRACE( "readBrokerPort", errorCode );
    return errorCode;
  }
  if ((brokerPort < 0) || (brokerPort > PORT_MAPPER_CLIENT_MAX_PORT_NUMBER)) {
    return MQ_TCP_INVALID_PORT;
  }

  PRBool useIPV6 = PR_FALSE;
  errorCode = connectionProperties->getBooleanProperty(
                MQ_ENABLE_IPV6_PROPERTY, &useIPV6);
  if (errorCode != MQ_SUCCESS && errorCode != MQ_NOT_FOUND) {
    MQ_ERROR_TRACE( "readBrokerPort", errorCode );
    return errorCode;
  }
  if (errorCode == MQ_NOT_FOUND) {
    useIPV6 = PR_FALSE;
  }

  PRInt32 readTimeout = PORT_MAPPER_CLIENT_RECEIVE_MICROSEC_TIMEOUT;
  errorCode = connectionProperties->getIntegerProperty(
                MQ_READ_PORTMAPPER_TIMEOUT_PROPERTY, &readTimeout);
  if (errorCode != MQ_SUCCESS && errorCode != MQ_NOT_FOUND) {
    MQ_ERROR_TRACE( "readBrokerPort", errorCode );
    return errorCode;
  }
  if (errorCode == MQ_NOT_FOUND) {
    readTimeout = PORT_MAPPER_CLIENT_RECEIVE_MICROSEC_TIMEOUT;
  } else {
    readTimeout = (readTimeout == 0 ? TRANSPORT_NO_TIMEOUT : readTimeout*1000);
  }

  PRInt32 writeTimeout = TRANSPORT_NO_TIMEOUT;
  errorCode = connectionProperties->getIntegerProperty(
                MQ_WRITE_TIMEOUT_PROPERTY, &writeTimeout);
  if (errorCode != MQ_SUCCESS && errorCode != MQ_NOT_FOUND) {
    MQ_ERROR_TRACE( "readBrokerPort", errorCode );
    return errorCode;
  }
  if (errorCode == MQ_NOT_FOUND) {
    writeTimeout = TRANSPORT_NO_TIMEOUT;
  } else {
    writeTimeout = (writeTimeout == 0 ? TRANSPORT_NO_TIMEOUT : writeTimeout*1000);
  }

  // Open up a new connection to the broker
  TCPSocket brokerSocket;
  RETURN_IF_ERROR_TRACE( brokerSocket.connect(brokerName,
                                        (PRUint16)brokerPort, useIPV6,
                                        PORT_MAPPER_CLIENT_CONNECT_MICROSEC_TIMEOUT), "readBrokerPorts", "mq");

  PRInt32 numBytesWritten = 0;
  PRInt32 numBytesToWrite = STRLEN(PORTMAPPER_VERSION_LINE);
  errorCode = brokerSocket.write(numBytesToWrite,
                                 (const PRUint8 *)PORTMAPPER_VERSION_LINE,
                                 writeTimeout,
                                 &numBytesWritten);
  /* This can sometimes fail if the server already wrote
   * the port table and closed the connection */
  if (errorCode != MQ_SUCCESS) { 
    LOG_FINE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID,
               MQ_PORTMAPPER_ERROR,
               "Failed to write port mapper version to broker because '%s' (%d)",
               errorStr(errorCode), errorCode ));
    MQ_ERROR_TRACE( "readBrokerPort", errorCode );
  } else if (numBytesWritten != numBytesToWrite) {
    LOG_SEVERE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID,
                 MQ_PORTMAPPER_ERROR,
                 "Unexpected bytes %d written to broker for port mapper version but expected %d",
                 numBytesWritten, numBytesToWrite ));
    MQ_ERROR_TRACE( "readBrokerPort", MQ_PORTMAPPER_ERROR );
    return  MQ_PORTMAPPER_ERROR;
  }

  // Read the port server output into a buffer
  PRUint8 portMappings[PORT_MAPPER_CLIENT_MAX_PORT_MAPPINGS_SIZE];
  PRInt32 numBytesRead = 0;

  errorCode = brokerSocket.read(sizeof(portMappings),
                                readTimeout,
                                portMappings,
                                &numBytesRead);

  // The broker sends the port mappings and then closes the socket, so
  // we may get an error back even though we successfully read the port
  // mappings
  if ((errorCode != MQ_SUCCESS) && (numBytesRead <= 0)) {
    MQ_ERROR_TRACE( "readBrokerPort", errorCode );
    return errorCode;
  }

  // Put the buffer in a string
  UTF8String portMapStr((char*)portMappings, numBytesRead);

  // Parse the string and put the results in a lookup table
  RETURN_IF_ERROR_TRACE( portMapperTable.parse(&portMapStr), "readBrokerPorts", "mq" );

  return MQ_SUCCESS;
}



MQError
PortMapperClient::getPortForProtocol(const UTF8String * const protocol, 
                                     const UTF8String * const type,
                                           PRUint16   * const port) 

{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;

  // Get the port entry
  const PortMapperEntry * portMapperEntry = NULL;
  errorCode = portMapperTable.getPortForProtocol(
                protocol, type, &portMapperEntry);
  if (errorCode != MQ_SUCCESS) {
    LOG_SEVERE(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, errorCode,
                 "Failed to get port for protocol %s", protocol->getCharStr() ));
    return errorCode;
  }

  ASSERT( portMapperEntry != NULL );
  *port = portMapperEntry->getPort();
  
  return MQ_SUCCESS;
}


