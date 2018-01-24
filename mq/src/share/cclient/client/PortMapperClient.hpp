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
 * @(#)PortMapperClient.hpp	1.4 06/26/07
 */ 

#ifndef PORTMAPPERCLIENT_HPP
#define PORTMAPPERCLIENT_HPP

#include "../debug/DebugUtils.h"
#include "../error/ErrorCodes.h"
#include "../basictypes/AllBasicTypes.hpp"
#include "../io/PortMapperTable.hpp"
#include "../containers/Properties.hpp"
#include "../util/PRTypesUtils.h"
#include "../basictypes/Object.hpp"

// The largest valid port number.
static const PRInt32  PORT_MAPPER_CLIENT_MAX_PORT_NUMBER         = MAX_PR_UINT16;

static const PRUint32 PORT_MAPPER_CLIENT_MAX_PORT_MAPPINGS_SIZE  = 2000;  // this was chosen arbitrarily

// Wait for 30 seconds to connect to the server
static const PRUint32 PORT_MAPPER_CLIENT_CONNECT_MICROSEC_TIMEOUT = 30 * 1000 * 1000;

// Wait for 3 min to receive information from the port server
static const PRUint32 PORT_MAPPER_CLIENT_RECEIVE_MICROSEC_TIMEOUT = 180 * 1000 * 1000;

/**
 * This class reads the MQ port mappings from the port mapper server
 * running on the broker.  
 */
class PortMapperClient : public Object {
private:
  
  PortMapperTable portMapperTable;

public:
  PortMapperClient();
  MQError readBrokerPorts(const Properties * const connectionProperties);
  MQError getPortForProtocol (const UTF8String * const protocol, 
                               const UTF8String * const type,
                                     PRUint16 *   const port);

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  PortMapperClient(const PortMapperClient& pmc);
  PortMapperClient& operator=(const PortMapperClient& pmc);
};

#endif // PORTMAPPERCLIENT_HPP
