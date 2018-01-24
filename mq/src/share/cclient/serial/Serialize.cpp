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
 * @(#)Serialize.cpp	1.3 06/26/07
 */ 

#include "Serialize.hpp"
#include "../util/UtilityMacros.h"
#include "../basictypes/AllBasicTypes.hpp"

/**
 *
 */
iMQError 
Serialize::serialIDToType(const PRUint64 serialID, 
                          TypeEnum * const classType)
{
  RETURN_ERROR_IF_NULL(classType);  

  if (LL_EQ( serialID, SERIALIZE_HASHTABLE_SERIAL_ID )) {
    *classType = HASHTABLE_TYPE;
  }
  else if (LL_EQ( serialID, SERIALIZE_BOOLEAN_SERIAL_ID )) {
    *classType = BOOLEAN_TYPE;
  }
  else if (LL_EQ( serialID, SERIALIZE_BYTE_SERIAL_ID )) {
    *classType = BYTE_TYPE;
  }
  else if (LL_EQ( serialID, SERIALIZE_SHORT_SERIAL_ID )) {
    *classType = SHORT_TYPE;
  }
  else if (LL_EQ( serialID, SERIALIZE_INTEGER_SERIAL_ID )) {
    *classType = INTEGER_TYPE;
  }
  else if (LL_EQ( serialID, SERIALIZE_LONG_SERIAL_ID )) {
    *classType = LONG_TYPE;
  }
  else if (LL_EQ( serialID, SERIALIZE_FLOAT_SERIAL_ID )) {
    *classType = FLOAT_TYPE;
  }
  else if (LL_EQ( serialID, SERIALIZE_DOUBLE_SERIAL_ID )) {
    *classType = DOUBLE_TYPE;
  }
  else if (LL_EQ( serialID, SERIALIZE_NUMBER_SERIAL_ID )) {
    *classType = NUMBER_TYPE;
  }
  else {
	*classType = UNKNOWN_TYPE;
    return IMQ_SERIALIZE_BAD_CLASS_UID;
  }

  return IMQ_SUCCESS;
}

/**
 *
 */
iMQError 
Serialize::typeToSerialID(const TypeEnum   classType,
                          PRUint64 * const serialID)
{
  RETURN_ERROR_IF_NULL( serialID );  

  ASSERT( (classType >= BOOLEAN_TYPE) && (classType <= NULL_TYPE) );
  *serialID = SERIAL_ID_BY_TYPE[classType];

  return IMQ_SUCCESS;
}


/**
 *
 */
iMQError
Serialize::classTypeToClassDescBytes(const TypeEnum classType, 
                                     PRUint8 const ** const classDesc, 
                                     PRUint32 * const classDescLen)
{
  RETURN_ERROR_IF_NULL( classDesc );  
  RETURN_ERROR_IF_NULL( classDescLen );  
  ASSERT( (classType >= BOOLEAN_TYPE) && (classType <= NULL_TYPE) );

  if (CLASS_DESC_BY_TYPE[classType] == NULL) {
	*classDesc = NULL;
	*classDescLen = 0;
    return IMQ_SERIALIZE_UNRECOGNIZED_CLASS; 
  }

  *classDesc = CLASS_DESC_BY_TYPE[classType];
  *classDescLen = CLASS_DESC_SIZE_BY_TYPE[classType];

  return IMQ_SUCCESS;
}



/**
 * Factory for new objects
 */
iMQError
Serialize::createObject(const TypeEnum classType, 
                        BasicType ** const object)
{
  RETURN_ERROR_IF_NULL( object );
  *object = NULL;
  
  // Allocate a new object of classType
  BasicType * newObject = NULL;
  switch (classType) {

  case BOOLEAN_TYPE:
    newObject = new Boolean();
    break;

  case BYTE_TYPE:
    newObject = new Byte();
    break;

  case SHORT_TYPE:
    newObject = new Short();
    break;

  case INTEGER_TYPE:
    newObject = new Integer();
    break;

  case LONG_TYPE:
    newObject = new Long();
    break;

  case FLOAT_TYPE:
    newObject = new Float();
    break;

  case DOUBLE_TYPE:
    newObject = new Double();
    break;

  case UTF8_STRING_TYPE:
    newObject = new UTF8String();
    break;

  case UTF8_LONG_STRING_TYPE:
    PRBool isLongString;
    isLongString = PR_TRUE;
    newObject = new UTF8String(isLongString);
    break;

  default:
    return IMQ_SERIALIZE_UNRECOGNIZED_CLASS;
  }

  RETURN_IF_OUT_OF_MEMORY( newObject );

  *object = newObject;
  return IMQ_SUCCESS;
}




