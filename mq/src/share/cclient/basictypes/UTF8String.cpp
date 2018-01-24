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
 * @(#)UTF8String.cpp	1.4 06/26/07
 */ 

#include "../debug/DebugUtils.h" // must be first in the file

#include "UTF8String.hpp"
#include "Byte.hpp"
#include "Boolean.hpp"
#include "Short.hpp"
#include "Integer.hpp"
#include "Long.hpp"
#include "Float.hpp"
#include "Double.hpp"
#include "../debug/DebugUtils.h"
#include "../util/UtilityMacros.h"
#include "../util/PRTypesUtils.h"
#include <string.h>

#include <nspr.h>
#include <memory.h> // for memcpy


/*
 *
 */
UTF8String::UTF8String()
{
  CHECK_OBJECT_VALIDITY();

  init();
  this->isLongString = PR_FALSE;
}

/*
 *
 */
UTF8String::UTF8String(PRBool isLongStringArg)
{
  CHECK_OBJECT_VALIDITY();

  init();
  this->isLongString = isLongStringArg;
}

/*
 *
 */
UTF8String::UTF8String(const char * const valueArg)
{
  CHECK_OBJECT_VALIDITY();

  init();
  setString(valueArg, (valueArg == NULL) ? 0 : (PRUint32)STRLEN(valueArg));
}


/*
 *
 */
UTF8String::UTF8String(const char * const valueArg, const PRUint32 valueLength)
{
  CHECK_OBJECT_VALIDITY();

  init();
  setString(valueArg, valueLength);
}

/*
 *
 */
void
UTF8String::setString(const char * const valueArg, const PRUint32 valueLength)
{
  CHECK_OBJECT_VALIDITY();

  if (valueArg == NULL) {
    return;
  }
  
  // allocate room for a new string and copy value into it
  this->value = new PRUint8[valueLength+1];
  if (this->value == NULL) {
    return;
  }
  STRNCPY( this->value, valueArg, valueLength );
  this->value[valueLength] = '\0';
  this->bytesInValue = valueLength;
  this->bytesAllocated = this->bytesInValue + 1; 

  this->isLongString = (valueLength >= UTF8STRING_MIN_LONG_STRING_LENGTH);
}


/*
 *
 */
UTF8String::~UTF8String()
{
  CHECK_OBJECT_VALIDITY();

  reset();
}


/*
 *
 */
void 
UTF8String::init()
{
  CHECK_OBJECT_VALIDITY();

  value          = NULL;
  bytesAllocated = 0;
  bytesInValue   = 0;
  isLongString   = PR_FALSE;
}

/*
 *
 */
void 
UTF8String::reset()
{
  CHECK_OBJECT_VALIDITY();

  DELETE_ARR( value );
  init();
}

/*
 *
 */
iMQError
UTF8String::setValue(const char * const valueStr)
{
  CHECK_OBJECT_VALIDITY();

  this->reset();
  
  RETURN_ERROR_IF_NULL( valueStr );
  
  setString(valueStr, (PRUint32)STRLEN(valueStr));

  return IMQ_SUCCESS;
}

/*
 *
 */
TypeEnum
UTF8String::getType() const
{
  CHECK_OBJECT_VALIDITY();

  if (isLongString) {
    return UTF8_LONG_STRING_TYPE;
  } else {
    return UTF8_STRING_TYPE;
  }
}

/*
 *
 */
BasicType *
UTF8String::clone() const
{
  CHECK_OBJECT_VALIDITY();

  UTF8String * newString = new UTF8String();
  if (newString == NULL) {
    return NULL;
  }
  
  // deep copy the value field into the new string
  if (this->value != NULL) {
    newString->value = new PRUint8[this->bytesAllocated];
    if (newString->value == NULL) {
      delete newString;
      return NULL;
    }
    
	// This copy includes the terminating '\0'
    memcpy(newString->value, this->value, this->bytesAllocated);
  }

  // copy the remaining fields
  newString->bytesAllocated = this->bytesAllocated;
  newString->bytesInValue   = this->bytesInValue;
  newString->isLongString   = this->isLongString;

  return newString;
}

/*
 *
 */
const PRUint8 *
UTF8String::toUCharStr() const
{
  CHECK_OBJECT_VALIDITY();

  if (value == NULL) {
    return (PRUint8*)"";
  }
  return value;
}


/*
 *
 */
const char *
UTF8String::getCharStr() const
{
  CHECK_OBJECT_VALIDITY();

  return (char*)toUCharStr();
}

/*
 *
 */
const PRUint8 *
UTF8String::getBytes() const
{
  CHECK_OBJECT_VALIDITY();

  return value;
}

/*
 *
 */
PRInt32
UTF8String::getBytesSize() const
{
  CHECK_OBJECT_VALIDITY();

  return bytesInValue;
}

/*
 *
 */
const char * 
UTF8String::toString()
{
  CHECK_OBJECT_VALIDITY();

  return this->getCharStr();
}


/*
 *
 */
iMQError 
UTF8String::getUint16Value(PRUint16 * const uint16Value)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( uint16Value );
  RETURN_ERROR_IF( value == NULL, IMQ_STRING_NOT_NUMBER );
  *uint16Value = 0;
  
  PRInt32 intValue = ATOI32( (char*)value );

  if ((intValue < 0) || (intValue > MAX_PR_UINT16)) {
    return IMQ_NUMBER_NOT_UINT16;
  }

  *uint16Value = (PRUint16)intValue;

  return IMQ_SUCCESS;
}

iMQError 
UTF8String::read(IMQDataInputStream * const in)
{
  return readLengthBytes(in, PR_TRUE);
}
/*
 * A PRUint16 begins the stream and stores the number of bytes in
 * the string.  Each character after the length is stored in UTF8
 * format.
 */
iMQError 
UTF8String::readLengthBytes(IMQDataInputStream * const in, PRBool checknull)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( in );
  reset();

  // Read in the length of the string in bytes.  The length is a 64 bit
  // value for long strings, and a 16 bit value for short strings.  We
  // currently assume that the length of the string will fit in a 32 bit
  // integer.
  PRUint32 bytesInString = 0;
  if (isLongString) {
    PRUint64 bytesInString64 = 0;
    RETURN_IF_ERROR( in->readUint64(&bytesInString64) );
    
    // we currently don't support strings > 4G.  I don't think this
    // will be a problem because iMQ packets cannot be larger than 2G.
    // We use < here instead of <= because below we add an additional byte
    // on the end for a \0
    RETURN_ERROR_IF(  LL_UCMP( bytesInString64, >=, LL_MAX_UINT32 ),
                      IMQ_SERIALIZE_STRING_TOO_BIG );
                           
    LL_L2UI( bytesInString, bytesInString64 );
  } else {
    PRUint16 bytesInString16;
    RETURN_IF_ERROR( in->readUint16(&bytesInString16) );
    bytesInString = bytesInString16;
  }

  // Allocate room for the bytes including one byte to null-terminate
  RETURN_IF_OUT_OF_MEMORY( value = new PRUint8[bytesInString + 1] );
  bytesAllocated = bytesInString + 1;
  bytesInValue = bytesInString;

  // Read in the characters
  iMQError error;
  error = in->readUint8Array(value, bytesInString);
  if (error != IMQ_SUCCESS) {
    reset();
    return error;
  }

  // Null terminate the utf8 string.  It isn't necessary to do this, 
  // but it is convenient for debugging because we can print out
  // the utf8 string, which usually only contains ascii characters.
  value[bytesInString] = '\0';

  if (checknull == PR_TRUE) {
    // Make sure that none of the bytes preceding the terminating '\0' is '\0'
    for (PRUint32 i = 0; i < bytesInString; i++) {
      if (value[i] == '\0') {
        reset();
        return IMQ_SERIALIZE_STRING_CONTAINS_NULL;
      }
    }
  }

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
UTF8String::write(IMQDataOutputStream * const out) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );

  // Write the length of the string in bytes.  The length is a 64 bit
  // value for long strings, and a 16 bit value for short strings.
  if (isLongString) {
    PRUint64 bytesInString64 = LL_ULLFromHiLo(0, bytesInValue);
    RETURN_IF_ERROR( out->writeUint64(bytesInString64) );
  } else {
    ASSERT( bytesInValue <= (PRUint32)MAX_PR_UINT16 );
    PRUint16 bytesInString16 = (PRUint16)bytesInValue;
    RETURN_IF_ERROR( out->writeUint16(bytesInString16) );
  }

  // Now write the bytes of the string
  RETURN_IF_ERROR( out->writeUint8Array(value, bytesInValue) );

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
UTF8String::print(FILE * const file) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( file );

  if (value != NULL) {
    fprintf(file, "%s", value);
  }

  return IMQ_SUCCESS;
}


/*
 *
 */
PRBool       
UTF8String::equals(const BasicType * const object) const
{
  CHECK_OBJECT_VALIDITY();

  return ((object != NULL)                                            &&
          (object->getType() == this->getType())                      &&
          (((UTF8String*)object)->bytesInValue == this->bytesInValue) &&
          (memcmp(((UTF8String*)object)->value, 
                  this->value, 
                  this->bytesInValue) == 0));
}


/*
 * Returns a 32-bit hash code for this string.  
 */
PLHashNumber
UTF8String::hashCode() const
{
  CHECK_OBJECT_VALIDITY();

  if (value == NULL) {
    return 0;
  } else {
	// The string must be null-terminated.
	ASSERT( this->value[this->bytesInValue] == '\0' ); 
    return PL_HashString(value);
  }
}

/*
 *
 */
PRInt32
UTF8String::length() const
{
  CHECK_OBJECT_VALIDITY();

  if (value == NULL) {
    return 0;
  } else {
    ASSERT( bytesInValue == STRLEN(value) );
    return bytesInValue;
  }
}

/*
 *
 */
iMQError
UTF8String::tokenize(const char * const delimStr, ObjectVector ** const strVector ) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( delimStr );
  RETURN_ERROR_IF_NULL( strVector );
  *strVector = NULL;

  // Allocate a new vector to store the tokens
  ObjectVector * tokens = new ObjectVector();
 RETURN_IF_OUT_OF_MEMORY( tokens );

  // Split the string
  const char * remainingStr = (const char*)value;
  while ((remainingStr != NULL)      &&
         (STRLEN( remainingStr ) > 0)) 
  {
    // Find the next occurence of delim
    const char * nextDelim = NULL;
    nextDelim = STRSTR( remainingStr, delimStr );
    
    // Determine how many characters are in the token
    PRUint32 tokenLength = 0;
    PRUint32 delimLength = 0;
    if (nextDelim == NULL) {
      tokenLength = (PRUint32)STRLEN( remainingStr );
      delimLength = 0;
    } else {
      // Advance tokenLength along the remaining string until we reach
      // the location of nextDelim.  This code is ugly, but taking
      // differences between pointers was generating warnings and
      // wouldn't be much better despite the warnings.
      for (tokenLength = 0; 
           (char *)&(remainingStr[tokenLength]) != nextDelim; 
           tokenLength++) 
	  {
		// do nothing
	  }
      delimLength = (PRUint32)STRLEN( delimStr );
    }
    
    // Create a new UTF8String
    UTF8String * token = new UTF8String(remainingStr, tokenLength);
    if ((token == NULL) || ((PRUint32)token->getBytesSize() != tokenLength)) {
	  DELETE( token );
      DELETE( tokens );
      return IMQ_OUT_OF_MEMORY;
    }

    // Add it to the vector of tokens
    iMQError error = tokens->add(token);
    if (error != IMQ_SUCCESS) {
      DELETE( token );
      DELETE( tokens );
      return error;
    }
   
    // Advance remainingStr past the token and delimeter
    remainingStr = &remainingStr[tokenLength + delimLength];
  }

  // Return the vector of tokens
  *strVector = tokens;

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
UTF8String::getBoolValue(PRBool * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  RETURN_ERROR_IF( this->value == NULL, IMQ_NULL_STRING );

  if (STRCMPI((const char *)this->value, "true") == 0) {
    *valueArg = PR_TRUE;
  }
  else if (STRCMPI((const char *)this->value, "false") == 0) {
    *valueArg = PR_FALSE;
  } else {
    return IMQ_INVALID_TYPE_CONVERSION;
  }

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
UTF8String::getInt8Value(PRInt8 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  RETURN_ERROR_IF( this->value == NULL, IMQ_NULL_STRING );
  
  // Read in the number as an 32-bit integer, make sure the integer isn't
  // too small or too big, and then convert it to an 8-bit integer.
  PRInt32 int32Value = 0;
  PRInt32 numConversions;
  numConversions = PR_sscanf( (const char*)this->value, "%d", &int32Value );
  if (numConversions != 1) {
    return IMQ_INVALID_TYPE_CONVERSION;
  } else if ((int32Value < BYTE_MIN_VALUE) || (int32Value > BYTE_MAX_VALUE)) {
    return IMQ_TYPE_CONVERSION_OUT_OF_BOUNDS;
  }
  
  *valueArg = (PRInt8)int32Value;
  
  return IMQ_SUCCESS;
}



/*
 *
 */
iMQError
UTF8String::getInt16Value(PRInt16 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  RETURN_ERROR_IF( this->value == NULL, IMQ_NULL_STRING );
  
  // Read in the number as an 32-bit integer, make sure the integer isn't
  // too small or too big, and then convert it to a 16-bit integer.
  PRInt32 int32Value = 0;
  PRInt32 numConversions;
  numConversions = PR_sscanf( (const char*)this->value, "%d", &int32Value );
  if (numConversions != 1) {
    return IMQ_INVALID_TYPE_CONVERSION;
  } else if ((int32Value < SHORT_MIN_VALUE) || (int32Value > SHORT_MAX_VALUE)) {
    return IMQ_TYPE_CONVERSION_OUT_OF_BOUNDS;
  }
  
  *valueArg = (PRInt16)int32Value;
  
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
UTF8String::getInt32Value(PRInt32 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  RETURN_ERROR_IF( this->value == NULL, IMQ_NULL_STRING );
  
  PRInt32 numConversions;
  numConversions = PR_sscanf( (const char*)this->value, "%d", valueArg );
  if (numConversions != 1) {
    return IMQ_INVALID_TYPE_CONVERSION;
  } 

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
UTF8String::getInt64Value(PRInt64 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  RETURN_ERROR_IF( this->value == NULL, IMQ_NULL_STRING );
  
  PRInt32 numConversions;
  numConversions = PR_sscanf( (const char*)this->value, "%lld", valueArg );
  if (numConversions != 1) {
    return IMQ_INVALID_TYPE_CONVERSION;
  } 

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
UTF8String::getFloat32Value(PRFloat32 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  RETURN_ERROR_IF( this->value == NULL, IMQ_NULL_STRING );
  
  // Read in the number as an 64-bit double, make sure the double isn't
  // too small or too big, and then convert it to a 32-bit float.
  PRFloat64 float64Value = 0;
  PRInt32 numConversions;
  numConversions = PR_sscanf( (const char*)this->value, "%f", &float64Value );
  if (numConversions != 1) {
    return IMQ_INVALID_TYPE_CONVERSION;
  } else if ((float64Value < FLOAT_MIN_VALUE) || (float64Value > FLOAT_MAX_VALUE)) {
    return IMQ_TYPE_CONVERSION_OUT_OF_BOUNDS;
  }
  
  *valueArg = (PRFloat32)float64Value;
  
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
UTF8String::getFloat64Value(PRFloat64 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  RETURN_ERROR_IF( this->value == NULL, IMQ_NULL_STRING );
  
  PRInt32 numConversions;
  numConversions = PR_sscanf( (const char*)this->value, "%f", valueArg );
  if (numConversions != 1) {
    return IMQ_INVALID_TYPE_CONVERSION;
  }
  
  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
UTF8String::getStringValue(const char ** const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  RETURN_ERROR_IF( this->value == NULL, IMQ_NULL_STRING );

  *valueArg = this->getCharStr();

  return IMQ_SUCCESS;
}
