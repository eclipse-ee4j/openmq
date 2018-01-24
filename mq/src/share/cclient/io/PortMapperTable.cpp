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
 * @(#)PortMapperTable.cpp	1.3 06/26/07
 */ 

#include "PortMapperTable.hpp"
#include "../util/UtilityMacros.h"

/*
 *
 */
PortMapperTable::PortMapperTable()
{
  CHECK_OBJECT_VALIDITY();

  init();
}


/*
 *
 */
PortMapperTable::~PortMapperTable()
{
  CHECK_OBJECT_VALIDITY();

  reset();
}

/*
 *
 */
void
PortMapperTable::init()
{
  CHECK_OBJECT_VALIDITY();

  brokerInstance = NULL;
  brokerVersion  = NULL;
  version        = NULL;
}

/*
 *
 */
void
PortMapperTable::reset()
{
  CHECK_OBJECT_VALIDITY();

  DELETE( brokerInstance );
  DELETE( brokerVersion );
  DELETE( version );
  serviceTable.reset();
}

/*
 *
 */
iMQError
PortMapperTable::add(PortMapperEntry * portMapperEntry)
{
  CHECK_OBJECT_VALIDITY();
  iMQError errorCode = IMQ_SUCCESS;
  UTF8String * entryName = NULL;

  NULLCHK( portMapperEntry );
  NULLCHK( portMapperEntry->getName() );

  MEMCHK( entryName = (UTF8String*)portMapperEntry->getName()->clone() );

  ERRCHK( serviceTable.addEntry(entryName, portMapperEntry) );
  entryName = NULL; // owned by the serviceTable now

  return IMQ_SUCCESS;
Cleanup:
  DELETE( entryName );

  return errorCode;
}

/**
 * This reads the portmapper data from the specified string.  (These
 * comments were copied from PortMapperTable.java")
 * The format of the data is:
 *
 *  <PRE>
 *  <portmapper version><SP><broker instance name><SP>packet version><NL>
 *  <service name><SP><protocol><SP><type><SP><port><NL>
 *  <.><NL>
 *
 *  Where:
 *
 *  <portmapper version>Portmapper numeric version string (ie "100").
 *  <packet version>    Packet version string (e.g. "2.0").
 *  <NL>                Newline character (octal 012)
 *  <service name>      Alphanumeric string. No embedded whitespace.
 *  <space>             A single space character
 *  <protocol>          Transport protocol. Typically "tcp" or "ssl"
 *  <service>           Service type. Typically "NORMAL", "ADMIN" or
 *                      "PORTMAPPER"
 *  <port>              Numeric string. Service port number
 *  <.>                 The '.' (dot) character
 *
 *  An example would be:
 *
 *  101 jmqbroker 2.0
 *  portmapper tcp PORTMAPPER 7575
 *  jms tcp NORMAL 59510
 *  admin tcp ADMIN 59997
 *  ssljms ssl NORMAL 42322
 *  .
 *
 *   </PRE>
 *
 */
iMQError 
PortMapperTable::parse(const UTF8String * const portServerOutput)
{
  CHECK_OBJECT_VALIDITY();

  ObjectVector *     lineVector           = NULL;
  ObjectVector *     fieldVector          = NULL;
  UTF8String *       firstLine            = NULL;
  UTF8String *       serviceLine          = NULL;
  UTF8String *       portMapperVersionStr = NULL;
  UTF8String *       brokerInstanceName   = NULL;
  UTF8String *       packetVersionStr     = NULL;
  PortMapperEntry *  portmapperEntry      = NULL;
  iMQError           errorCode            = IMQ_SUCCESS;

  reset();
  RETURN_ERROR_IF_NULL( portServerOutput );
    
  // Split portServerOutput into lines
  ERRCHK( portServerOutput->tokenize(PORTMAPPER_LINE_SEPARATOR, &lineVector) );
  CNDCHK( lineVector->size() == 0, IMQ_PORTMAPPER_INVALID_INPUT );

  // Split off the first line which contains versioning information 
  ERRCHK( lineVector->remove(0, (void**)&firstLine) );
 
  // Now tokenize into
  // <portmapper version><SP><broker instance name><SP>broker version><NL>
  // And make sure that we got the number of fields that we expected.
  ERRCHK( firstLine->tokenize(PORTMAPPER_FIELD_SEPARATOR, &fieldVector) );
  CNDCHK( fieldVector->size() < PORTMAPPER_VERSION_NUM_FIELDS,
          IMQ_PORTMAPPER_INVALID_INPUT );  

  // Now get the portmapper version, broker instance name, broker version
  ERRCHK( fieldVector->remove(0, (void**)&portMapperVersionStr) );
  ERRCHK( fieldVector->remove(0, (void**)&brokerInstanceName) );
  ERRCHK( fieldVector->remove(0, (void**)&packetVersionStr) );

  // Return if the portMapperVersion isn't what we expect
  CNDCHK( STRCMP( portMapperVersionStr->getCharStr(), PORTMAPPER_VERSION ) != 0,
          IMQ_PORTMAPPER_WRONG_VERSION );
  
  // Process each service name/port number value pair
  while (lineVector->size() > 0) {
    ERRCHK( lineVector->remove(0, (void**)&serviceLine) );
    
    // We're done if we've reached the service description terminator
    if (STRCMP( serviceLine->getCharStr(), PORTMAPPER_SERVICE_TERMINATOR ) == 0 ) {
      DELETE( serviceLine );
      break;
    }

    // Add this service description to the table 
    MEMCHK( portmapperEntry = new PortMapperEntry() );
    ERRCHK( portmapperEntry->parse(serviceLine) );
    ERRCHK( this->add(portmapperEntry) );

    DELETE( serviceLine );
  }

  // Cleanup
  portmapperEntry = NULL;
  DELETE( lineVector );
  DELETE( fieldVector );
  DELETE( firstLine );
  
  // Set member variables
  this->brokerVersion  = portMapperVersionStr;
  this->brokerInstance = brokerInstanceName;
  this->version        = packetVersionStr;

  return IMQ_SUCCESS;


// Cleanup everything in case there was an error
 Cleanup:
  DELETE( lineVector );
  DELETE( fieldVector );
  DELETE( firstLine );
  DELETE( serviceLine );
  DELETE( portMapperVersionStr );
  DELETE( brokerInstanceName );
  DELETE( packetVersionStr );
  DELETE( portmapperEntry );

  reset();

  return errorCode; 
}

/*
 *
 */
iMQError 
PortMapperTable::get(const char * serviceName, 
                     const PortMapperEntry ** const portMapperEntry) const
{
  CHECK_OBJECT_VALIDITY();

  UTF8String serviceStr(serviceName);

  return get(&serviceStr, portMapperEntry);
}


/*
 *
 */
iMQError
PortMapperTable::get(const UTF8String * serviceName,
                     const PortMapperEntry ** const portMapperEntry) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( serviceName );
  RETURN_ERROR_IF_NULL( portMapperEntry );
  *portMapperEntry = NULL;
  
  RETURN_IF_ERROR( serviceTable.getValueFromKey( serviceName,
                                                 (const Object** const)&(*portMapperEntry)) );

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
PortMapperTable::getPortForProtocol(const UTF8String * protocol,
                                    const UTF8String * type,
                                    const PortMapperEntry ** const portMapperEntry)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( protocol );
  RETURN_ERROR_IF_NULL( type );
  RETURN_ERROR_IF_NULL( portMapperEntry );
  *portMapperEntry = NULL;
  
  // Iterate through all of the entries looking for the entry that
  // matches the given protocol and type.
  RETURN_IF_ERROR( serviceTable.keyIterationStart() );
  while (serviceTable.keyIterationHasNext()) {
    const BasicType * serviceName        = NULL;
    const PortMapperEntry  * entry = NULL;
    RETURN_IF_ERROR( serviceTable.keyIterationGetNext(&serviceName) );
    RETURN_IF_ERROR( serviceTable.getValueFromKey(serviceName,
                                                  (const Object**const)&entry) );
    ASSERT( entry != NULL );
    ASSERT( entry->name->equals(serviceName) );
    if (entry->protocol->equals(protocol) &&
        entry->type->equals(type))
    {
      *portMapperEntry = entry;
      return IMQ_SUCCESS;
    }
    
  }
  
  return IMQ_NOT_FOUND;
}

/*
 * These are different port mapper outputs used for testing.
 */
const char * PORT_MAPPER_OUTPUT = 
"101 jmqbroker 2.0\n"
"cluster tcp CLUSTER 59135\n"
"admin tcp ADMIN 59134\n"
"portmapper tcp PORTMAPPER 7676\n"
"jms tcp NORMAL 59133\n"
"httpjms http NORMAL 0\n"
".\n";

const char * PORT_MAPPER_OUTPUT_BOGUS_1 = 
"101 jmqbroker2.0\n"
"cluster tcp CLUSTER 59135\n"
"admin tcp ADMIN 59134\n"
"portmapper tcp PORTMAPPER 7676\n"
"jms tcp NORMAL 59133\n"
"httpjms http NORMAL 0\n"
".\n";

const char * PORT_MAPPER_OUTPUT_BOGUS_2 = 
"101 jmqbroker 2.0\n"
"cluster tcp CLUSTER 59135\n"
"admin tcp ADMIN59134\n"
"portmapper tcp PORTMAPPER 7676\n"
"jms tcp NORMAL 59133\n"
"httpjms http NORMAL 0\n"
".\n";

const char * PORT_MAPPER_OUTPUT_BOGUS_3 = 
"101 jmqbroker 2.0\n"
"cluster tcp CLUSTER 59135\n"
"admin tcp ADMIN 59134 HELLO\n"
"portmapper tcp PORTMAPPER 7676\n"
"jms tcp NORMAL 59133\n"
"httpjms http NORMAL 0\n"
".\n";

const char * PORT_MAPPER_OUTPUT_BOGUS_4 = 
"";

/*
 *
 */
iMQError
PortMapperTable::test()
{
  PortMapperTable pmt;
  const PortMapperEntry * pme = NULL;
  iMQError errorCode = IMQ_SUCCESS;

  // Parse the valid input
  UTF8String strToParse(PORT_MAPPER_OUTPUT);
  RETURN_IF_ERROR( pmt.parse(&strToParse) );

  // These are valid port names
  RETURN_IF_ERROR( pmt.get("cluster", &pme) );
  RETURN_IF_ERROR( pmt.get("admin", &pme) );
  RETURN_IF_ERROR( pmt.get("portmapper", &pme) );
  RETURN_IF_ERROR( pmt.get("jms", &pme) );
  RETURN_IF_ERROR( pmt.get("httpjms", &pme) );

  // These are invalid port names, so they should fail
  RETURN_ERROR_IF( pmt.get("", &pme) == IMQ_SUCCESS, IMQ_PORTMAPPER_ERROR );
  RETURN_ERROR_IF( pmt.get("jmsbogus", &pme) == IMQ_SUCCESS, IMQ_PORTMAPPER_ERROR );

  // Now try parsing invalid input, each of the parses should fail
  PortMapperTable pmt1,pmt2,pmt3,pmt4;
  UTF8String strToParse1(PORT_MAPPER_OUTPUT_BOGUS_1);
  UTF8String strToParse2(PORT_MAPPER_OUTPUT_BOGUS_2);
  UTF8String strToParse3(PORT_MAPPER_OUTPUT_BOGUS_3);
  UTF8String strToParse4(PORT_MAPPER_OUTPUT_BOGUS_4);

  RETURN_ERROR_IF( pmt1.parse(&strToParse1) == IMQ_SUCCESS, IMQ_PORTMAPPER_ERROR );
  RETURN_ERROR_IF( pmt2.parse(&strToParse2) == IMQ_SUCCESS, IMQ_PORTMAPPER_ERROR );
  RETURN_ERROR_IF( pmt3.parse(&strToParse3) == IMQ_SUCCESS, IMQ_PORTMAPPER_ERROR );
  RETURN_ERROR_IF( pmt4.parse(&strToParse4) == IMQ_SUCCESS, IMQ_PORTMAPPER_ERROR );
  
  return IMQ_SUCCESS;
}



