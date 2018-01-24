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
 * @(#)TCPProtocolHandler.cpp	1.9 06/26/07
 */ 

#include "TCPProtocolHandler.hpp"
#include "../../util/UtilityMacros.h"
#include "../../util/LogUtils.hpp"
#include "../PortMapperClient.hpp"

/*
 *
 */
TCPProtocolHandler::TCPProtocolHandler()
{
  CHECK_OBJECT_VALIDITY();

  init();
}



/*
 *
 */
TCPProtocolHandler::~TCPProtocolHandler()
{
  CHECK_OBJECT_VALIDITY();

  reset();
}

/*
 *
 */
void
TCPProtocolHandler::init()
{
  CHECK_OBJECT_VALIDITY();
}

/*
 *
 */
void
TCPProtocolHandler::reset()
{
  CHECK_OBJECT_VALIDITY();

  brokerSocket.reset();
}


/*
 *
 */
MQError 
TCPProtocolHandler::connect(const Properties * const connectionProperties)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = MQ_SUCCESS;

  PortMapperClient portMapperClient;
  UTF8String tcpProtocol(TCP_PROTOCOL_STR);
  UTF8String normalConnection(CONNECTION_TYPE_NORMAL_STR);
  PRInt32 directPort = 0;
  PRUint16 brokerPort = 0;
  const char * brokerName = NULL;
  PRBool useIPV6 = PR_FALSE;
  PRUint32 connectTimeout = 0;

  // Make sure we are not already connected, and that
  // connectionProperties is valid
  CNDCHK( brokerSocket.status() != TCPSocket::NOT_CONNECTED, 
          MQ_TCP_ALREADY_CONNECTED );
  NULLCHK( connectionProperties );

  // Get the host name property first 
  ERRCHK( connectionProperties->getStringProperty(MQ_BROKER_HOST_PROPERTY,
                                                  &brokerName) );
  NULLCHK(brokerName);
  if ( connectionProperties->getIntegerProperty(MQ_SERVICE_PORT_PROPERTY,
                                               &directPort) == MQ_SUCCESS) {
    CNDCHK( (directPort <= 0 || 
             directPort > PORT_MAPPER_CLIENT_MAX_PORT_NUMBER), MQ_TCP_INVALID_PORT );
    brokerPort = directPort;
  }
  errorCode = connectionProperties->getBooleanProperty(
                MQ_ENABLE_IPV6_PROPERTY, &useIPV6); 
  if (errorCode != MQ_SUCCESS && errorCode != MQ_NOT_FOUND) {
      ERRCHK( errorCode);
  }
  if (errorCode == MQ_NOT_FOUND) {
    useIPV6 = PR_FALSE;
  }

  if (brokerPort == 0) {
    // Use the portmapper to find out what port to connect to
    ERRCHK( portMapperClient.readBrokerPorts(connectionProperties) );
    ERRCHK( portMapperClient.getPortForProtocol(&tcpProtocol,
                                                &normalConnection,
                                                &brokerPort) );
  }
  
  // We should probably get this from a property
  connectTimeout = DEFAULT_CONNECT_TIMEOUT;

  // Now connect to the broker on the JMS port
  ERRCHK( this->brokerSocket.connect(brokerName, 
                                     brokerPort, useIPV6,
                                     connectTimeout) );

  LOG_INFO(( CODELOC, TCP_HANDLER_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
             "Opened TCP connection to broker %s:%d.", brokerName, brokerPort ));
  
  return MQ_SUCCESS;

Cleanup:
  LOG_SEVERE(( CODELOC, TCP_HANDLER_LOG_MASK, NULL_CONN_ID, 
               MQ_COULD_NOT_CONNECT_TO_BROKER,
               "Could not open TCP connection to broker %s:%d because '%s' (%d)", 
               (brokerName == NULL ? "NULL":brokerName), 
               brokerPort, errorStr(errorCode), errorCode ));
  
  MQ_ERROR_TRACE("connect", errorCode);
  return errorCode;
}


/*
 *
 */
MQError 
TCPProtocolHandler::getLocalPort(PRUint16 * const port) const
{
  CHECK_OBJECT_VALIDITY();

  return brokerSocket.getLocalPort(port);
}

/*
 *
 */
MQError 
TCPProtocolHandler::getLocalIP(const IPAddress ** const ipAddr) const
{
  CHECK_OBJECT_VALIDITY();

  return brokerSocket.getLocalIP(ipAddr);
}


/*
 *
 */
MQError 
TCPProtocolHandler::read(const PRInt32          numBytesToRead,
                         const PRUint32         timeoutMicroSeconds, 
                               PRUint8 * const  bytesRead, 
                               PRInt32 * const  numBytesRead)
{
  CHECK_OBJECT_VALIDITY();

  return brokerSocket.read(numBytesToRead,
                           timeoutMicroSeconds,
                           bytesRead,
                           numBytesRead);
}


/*
 *
 */
MQError 
TCPProtocolHandler::write(const PRInt32          numBytesToWrite,
                          const PRUint8 * const  bytesToWrite,
                          const PRUint32         timeoutMicroSeconds, 
                                PRInt32 * const  numBytesWritten)
{
  CHECK_OBJECT_VALIDITY();

  return brokerSocket.write(numBytesToWrite,
                            bytesToWrite,
                            timeoutMicroSeconds,
                            numBytesWritten);
}

/*
 *
 */
MQError 
TCPProtocolHandler::close()
{
  CHECK_OBJECT_VALIDITY();

  return brokerSocket.close();
}


/*
 *
 */
MQError 
TCPProtocolHandler::shutdown()
{
  CHECK_OBJECT_VALIDITY();

  return brokerSocket.shutdown();
}

/*
 *
 */
PRBool
TCPProtocolHandler::isClosed()
{
  CHECK_OBJECT_VALIDITY();

  return brokerSocket.isClosed();
}

