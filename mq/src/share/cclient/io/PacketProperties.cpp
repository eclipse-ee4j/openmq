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
 * @(#)PacketProperties.cpp	1.4 06/26/07
 */ 

#include "PacketProperties.hpp"
#include "../basictypes/TypeEnum.hpp"
#include "../util/UtilityMacros.h"

static const PRUint16  BOOLEAN = 1;
static const PRUint16  BYTE = 2;
static const PRUint16  SHORT = 3;
static const PRUint16  INTEGER = 4;
static const PRUint16  LONG = 5;
static const PRUint16  FLOAT = 6;
static const PRUint16  DOUBLE = 7;
static const PRUint16  STRING = 8;
static const PRUint16  OBJECT = 9;  //not supported

static const PRUint32  VERSION = 1;

iMQError
PacketProperties::writeProperties(SerialDataOutputStream * const out,
                                  Properties * const properties) 
{
  iMQError errorCode;
  PRUint32 numProperties = 0;
  TypeEnum type;
  char * key = NULL;
  UTF8String *utf8Str = NULL;

  RETURN_ERROR_IF_NULL(out);
  RETURN_ERROR_IF_NULL(properties);

  ERRCHK( properties->getNumKeys(&numProperties) );
  if (numProperties > 0) {

  ERRCHK( out->writeUint32(VERSION) );
  ERRCHK( out->writeUint32(numProperties) );

  MEMCHK( utf8Str =  new UTF8String() );
  ERRCHK( properties->keyIterationStart() );
  while (properties->keyIterationHasNext() == PR_TRUE) {
    key = NULL;
    ERRCHK( properties->keyIterationGetNext((const char **)&key) );
    ERRCHK( properties->getPropertyType(key, &type) );
    utf8Str->reset();
    ERRCHK( utf8Str->setValue(key) );
    ERRCHK( utf8Str->write(out) );

    switch(type) {
    case BOOLEAN_TYPE:
       PRBool booleanValue;
       ERRCHK( properties->getBooleanProperty(key, &booleanValue) );
       ERRCHK( out->writeUint16(BOOLEAN) );
       ERRCHK( out->writeBoolean(booleanValue) );
       break;
    case BYTE_TYPE:
       PRInt8 byteValue;
       ERRCHK( properties->getByteProperty(key, &byteValue) );
       ERRCHK( out->writeUint16(BYTE) );
       ERRCHK( out->writeInt8(byteValue) );
       break;
    case SHORT_TYPE:
       PRInt16 shortValue;
       ERRCHK( properties->getShortProperty(key, &shortValue) );
       ERRCHK( out->writeUint16(SHORT) );
       ERRCHK( out->writeInt16(shortValue) );
       break;
    case INTEGER_TYPE:
       PRInt32 integerValue;
       ERRCHK( properties->getIntegerProperty(key, &integerValue) );
       ERRCHK( out->writeUint16(INTEGER) );
       ERRCHK( out->writeInt32(integerValue) );
       break;
    case LONG_TYPE:
       PRInt64 longValue;
       ERRCHK( properties->getLongProperty(key, &longValue) );
       ERRCHK( out->writeUint16(LONG) );
       ERRCHK( out->writeInt64(longValue) );
       break;
    case FLOAT_TYPE:
       PRFloat32 floatValue;
       ERRCHK( properties->getFloatProperty(key, &floatValue) );
       ERRCHK( out->writeUint16(FLOAT) );
       ERRCHK( out->writeFloat32(floatValue) );
       break;
    case DOUBLE_TYPE:
       PRFloat64 doubleValue;
       ERRCHK( properties->getDoubleProperty(key, &doubleValue) );
       ERRCHK( out->writeUint16(DOUBLE) );
       ERRCHK( out->writeFloat64(doubleValue) );
       break;
    case UTF8_STRING_TYPE:
    case UTF8_LONG_STRING_TYPE:
       {
       char *value = NULL;
       ERRCHK( properties->getStringProperty(key, (const char **)&value) );
       ERRCHK( out->writeUint16(STRING) );
       utf8Str->reset();
       ERRCHK( utf8Str->setValue(value) );
       ERRCHK( utf8Str->write(out) );
       }
       break;
    default:
       errorCode = IMQ_INVALID_PACKET_FIELD;
       goto Cleanup;
    }

  }

  DELETE (utf8Str);
  }
  return IMQ_SUCCESS;

  Cleanup:
   if (utf8Str != NULL) DELETE (utf8Str);
   return errorCode;
}

/*
 * caller need ensure there is properties to read  
 */
iMQError
PacketProperties::readProperties(SerialDataInputStream * const out,
                                 Properties * const properties)
{
  iMQError errorCode;
  PRUint32 version; 
  PRUint16 type; 
  UTF8String *key = NULL;
  UTF8String *utf8Str = NULL;

  RETURN_ERROR_IF_NULL(out);
  RETURN_ERROR_IF_NULL(properties);

  RETURN_IF_ERROR( out->readUint32(&version) );   
  if (version != VERSION) {
    return IMQ_INVALID_PACKET;
  }

  PRUint32 numProperties;
  RETURN_IF_ERROR( out->readUint32(&numProperties) );   

  PRUint32 count = 0;
  RETURN_IF_OUT_OF_MEMORY ( key = new UTF8String() );
  MEMCHK( utf8Str =  new UTF8String() );

  while (count < numProperties) {
    key->reset();
    ERRCHK( key->read(out) );
    if (key->length() <= 0) break;

    ERRCHK( out->readUint16(&type) );
    switch (type) {
    case BOOLEAN:
       PRBool booleanValue;
       ERRCHK( out->readBoolean(&booleanValue) );
       ERRCHK( properties->setBooleanProperty(key->getCharStr(), booleanValue) );
       break;
    case BYTE:
       PRInt8 byteValue;
       ERRCHK( out->readInt8(&byteValue) );
       ERRCHK( properties->setByteProperty(key->getCharStr(), byteValue) );
       break;
    case SHORT:
       PRInt16 shortValue;
       ERRCHK( out->readInt16(&shortValue) );
       ERRCHK( properties->setShortProperty(key->getCharStr(), shortValue) );
       break;
    case INTEGER:
       PRInt32 integerValue;
       ERRCHK( out->readInt32(&integerValue) );
       ERRCHK( properties->setIntegerProperty(key->getCharStr(), integerValue) );
       break;
    case LONG:
       PRInt64 longValue;
       ERRCHK( out->readInt64(&longValue) );
       ERRCHK( properties->setLongProperty(key->getCharStr(), longValue) );
       break;
    case FLOAT:
       PRFloat32 floatValue;
       ERRCHK( out->readFloat32(&floatValue) );
       ERRCHK( properties->setFloatProperty(key->getCharStr(), floatValue) );
       break;
    case DOUBLE:
       PRFloat64 doubleValue;
       ERRCHK( out->readFloat64(&doubleValue) );
       ERRCHK( properties->setDoubleProperty(key->getCharStr(), doubleValue) );
       break;
    case STRING:
       utf8Str->reset();
       ERRCHK( utf8Str->read(out) );
       ERRCHK( properties->setStringProperty(key->getCharStr(), utf8Str->getCharStr()) );
       break;
    default:
       errorCode = IMQ_INVALID_PACKET_FIELD;      
       goto Cleanup;
    }
    count++;

  } 

  DELETE (key); 
  DELETE (utf8Str);
  return IMQ_SUCCESS;

  Cleanup:
   if (key != NULL) DELETE (key); 
   if (utf8Str != NULL) DELETE (utf8Str);
   return errorCode;
}

