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
 * @(#)PortMapperEntry.hpp	1.4 06/28/07
 */ 

#ifndef PORTMAPPERENTRY_H
#define PORTMAPPERENTRY_H

#include "../debug/DebugUtils.h"
#include "../error/ErrorCodes.h"
#include "../basictypes/AllBasicTypes.hpp"
#include "../basictypes/Object.hpp"

#include <nspr.h>

/**
 * This class stores a single mapping from iMQ protocol name to
 * protocol, type, and port.  E.g.  jms tcp NORMAL 59510
 *
 * @see PortMapperTable
 * @see PortMapperClient
 */
class PortMapperEntry : public Object {
public:
  // These fields are declared public so they can be easily read.  They should
  // not be modified.

  /** The port of the service (e.g. 59510) */
  PRUint16     port;
  /** The protocol used for the service (e.g. tcp) */
  UTF8String * protocol;
  /** The type of the service (e.g. NORMAL) */
  UTF8String * type;
  /** The name of the service (e.g. jms) */
  UTF8String * name;

private:
  void reset();
public:
  PortMapperEntry();
  ~PortMapperEntry();

  /**
   * This method initializes this PortMapperEntry by parsing the service
   * described in serviceLine.
   *
   * @param serviceLine is a string representing the service line with the \n
   * already stripped.  (e.g. "jms tcp NORMAL 59510")
   *
   * @returns IMQ_SUCCESS if successful and an error otherwise 
   */
  iMQError parse(const UTF8String * const serviceLine);
  
  /**
   * This method returns the name of the service.  The caller should not attempt
   * to modify or free the string.
   *
   * @returns the name of the service e.g. "jms".  If the PortMapperEntry has
   * not been initialized, then NULL is returned.  
   */
  const UTF8String * getName() const;

  /**
   * This method returns the port associated with this service.
   *
   * @returns  the port associated with this service.
   */
  PRUint16 getPort() const;
};

#endif // PORTMAPPERENTRY_H






