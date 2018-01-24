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
 * @(#)PortMapperEntry.cpp	1.4 06/26/07
 */ 

#include "PortMapperEntry.hpp"
#include "../util/UtilityMacros.h"
#include "PortMapperTable.hpp"

/*
 *
 */
PortMapperEntry::PortMapperEntry()
{
  CHECK_OBJECT_VALIDITY();

  port     = 0;
  protocol = NULL;
  type     = NULL;
  name     = NULL;
}

/*
 *
 */
PortMapperEntry::~PortMapperEntry()
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( ((protocol == NULL) && (type == NULL) && (name == NULL) && (port == 0)) ||
          ((protocol != NULL) && (type != NULL) && (name != NULL)) );

  reset();
}

/*
 *
 */
void
PortMapperEntry::reset()
{
  CHECK_OBJECT_VALIDITY();

  port = 0;
  DELETE( protocol );
  DELETE( type );
  DELETE( name );
}

/*
 *
 */
iMQError 
PortMapperEntry::parse(const UTF8String * const serviceLine)
{
  CHECK_OBJECT_VALIDITY();

  ObjectVector *  fieldVector = NULL;
  UTF8String *    portStr     = NULL;
  iMQError        errorCode   = IMQ_SUCCESS;
  RETURN_ERROR_IF_NULL( serviceLine );

  // serviceLine should look like
  // <service name><SP><protocol><SP><type><SP><port>
  ERRCHK( serviceLine->tokenize(PORTMAPPER_FIELD_SEPARATOR, &fieldVector) );  
  /*
   * Check that serviceLine contains at least 4 elements.
   * Each line is of the form:
   *  portmapper tcp PORTMAPPER 7676 [key1=value1,key2=value2,...]
   * The C API only needs the first 4 elements. As of now, it will not parse the 
   * optional properties section encapsulated with "[" and "]".
   * Note: The keys/values may contain spaces.
   */
  CNDCHK( (fieldVector->size() < PORTMAPPER_SERVICE_NUM_FIELDS),
          IMQ_PORTMAPPER_INVALID_INPUT );  
  ERRCHK( fieldVector->remove(0, (void**)&(this->name)) );
  ERRCHK( fieldVector->remove(0, (void**)&(this->protocol)) );
  ERRCHK( fieldVector->remove(0, (void**)&(this->type)) );
  ERRCHK( fieldVector->remove(0, (void**)&portStr) );
  ERRCHK( portStr->getUint16Value(&(this->port)) );

  DELETE( fieldVector );
  DELETE( portStr );

  return IMQ_SUCCESS;

 Cleanup:
  DELETE( fieldVector );
  DELETE( portStr );
  reset();
  
  return errorCode;
}

/*
 *
 */
const UTF8String *
PortMapperEntry::getName() const
{
  CHECK_OBJECT_VALIDITY();

  return name;
}

/*
 *
 */
PRUint16 
PortMapperEntry::getPort() const
{
  CHECK_OBJECT_VALIDITY();

  return port;
}


