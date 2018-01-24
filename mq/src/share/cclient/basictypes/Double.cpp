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
 * @(#)Double.cpp	1.5 06/26/07
 */ 

#include "Double.hpp"
#include "../debug/DebugUtils.h"
#include "../util/UtilityMacros.h"
#include <memory.h>

/*
 * Default constructor.
 */
Double::Double()
{
  CHECK_OBJECT_VALIDITY();

  this->value    = DOUBLE_DEFAULT_VALUE;
  this->valueStr = NULL;
}

/*
 * 
 */
Double::Double(const PRFloat64 valueArg)
{
  CHECK_OBJECT_VALIDITY();

  this->value    = valueArg;
  this->valueStr = NULL;
}

/*
 * 
 */
Double::~Double()
{
  CHECK_OBJECT_VALIDITY();

  DELETE_ARR( this->valueStr );
}


/*
 * Return a pointer to a deep copy of this object.
 */
BasicType *
Double::clone() const
{
  CHECK_OBJECT_VALIDITY();

  return new Double(this->value);
}

/*
 * Set the value of this object to the value parameter.
 */
void
Double::setValue(const PRFloat64 valueArg)
{
  CHECK_OBJECT_VALIDITY();

  DELETE_ARR( this->valueStr );

  this->value = valueArg;
}

/*
 * Return the value of this object.
 */
PRFloat64
Double::getValue() const
{
  CHECK_OBJECT_VALIDITY();

  return this->value;
}

/*
 * Return the type of this object.
 */
TypeEnum
Double::getType() const
{
  CHECK_OBJECT_VALIDITY();

  return DOUBLE_TYPE;
}

/*
 * Read the value of the object from the input stream.
 *
 * Return an error if the read fails.
 */
iMQError 
Double::read(IMQDataInputStream * const in)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( in );

  DELETE_ARR( this->valueStr );

  RETURN_IF_ERROR( in->readFloat64(&this->value) );
  
  return IMQ_SUCCESS;
}

/*
 * Write the value of the object to the output stream.
 *
 * Return an error if the write fails.
 */
iMQError 
Double::write(IMQDataOutputStream * const out) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );

  RETURN_IF_ERROR( out->writeFloat64(this->value) );
  
  return IMQ_SUCCESS;
}

/*
 * Print the value of the object to the file.
 *
 * Return an error if the print fails
 */
iMQError 
Double::print(FILE * const file) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( file );

  PRInt64 bytesWritten = fprintf(file, "%g", (PRFloat64)this->value);
  RETURN_ERROR_IF( bytesWritten <= 0, IMQ_FILE_OUTPUT_ERROR );
  
  return IMQ_SUCCESS;
}

/*
 *
 */
PRBool       
Double::equals(const BasicType * const object) const
{
  CHECK_OBJECT_VALIDITY();

  return ((object != NULL)                           &&
          (object->getType() == this->getType())     &&
          (((Double*)object)->getValue() == this->value));
}

/*
 * Returns a 32-bit hash code for this number.
 */
PLHashNumber
Double::hashCode() const
{
  CHECK_OBJECT_VALIDITY();

  PLHashNumber hashCode = 0;
  memcpy((void*)&hashCode, 
         (void*)&this->value, 
         MIN( sizeof(hashCode), sizeof(this->value) ));

  return hashCode;
}

/*
 * Return a char* representation of this object.
 */
const char *
Double::toString()
{
  CHECK_OBJECT_VALIDITY();

  if (this->valueStr != NULL) {
    return this->valueStr;
  } 
  this->valueStr = new char[DOUBLE_MAX_STR_SIZE];
  if (this->valueStr == NULL) {
    return "<out-of-memory>";
  }

  SNPRINTF(this->valueStr, DOUBLE_MAX_STR_SIZE, "%g", (PRFloat64)this->value);
  // Just to be safe.  snprintf won't automatically null terminate for us.
  this->valueStr[DOUBLE_MAX_STR_SIZE-1] = '\0'; 

  return this->valueStr;
}



/*
 *
 */
iMQError
Double::getFloat64Value(PRFloat64 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = this->value;

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
Double::getStringValue(const char ** const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = ((Double*)this)->toString();

  return IMQ_SUCCESS;
}


