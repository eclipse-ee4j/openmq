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

#include "Short.hpp"
#include "../debug/DebugUtils.h"
#include "../util/UtilityMacros.h"

/*
 * Default constructor.
 */
Short::Short()
{
  CHECK_OBJECT_VALIDITY();

  this->value    = SHORT_DEFAULT_VALUE;
  this->valueStr = NULL;
}

/*
 * 
 */
Short::Short(const PRInt16 valueArg)
{
  CHECK_OBJECT_VALIDITY();

  this->value    = valueArg;
  this->valueStr = NULL;
}

/*
 * 
 */
Short::~Short()
{
  CHECK_OBJECT_VALIDITY();

  DELETE_ARR( this->valueStr );
}

/*
 * Return a pointer to a deep copy of this object.
 */
BasicType *
Short::clone() const
{
  CHECK_OBJECT_VALIDITY();

  return new Short(this->value);
}

/*
 * Set the value of this object to the value parameter.
 */
void
Short::setValue(const PRInt16 valueArg)
{
  CHECK_OBJECT_VALIDITY();

  this->value = valueArg;
}

/*
 * Return the value of this object.
 */
PRInt16
Short::getValue() const
{
  CHECK_OBJECT_VALIDITY();

  return this->value;
}

/*
 * Return the type of this object.
 */
TypeEnum
Short::getType() const
{
  CHECK_OBJECT_VALIDITY();

  return SHORT_TYPE;
}

/*
 * Read the value of the object from the input stream.
 *
 * Return an error if the read fails.
 */
iMQError 
Short::read(IMQDataInputStream * const in)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( in );

  RETURN_IF_ERROR( in->readInt16(&this->value) );
  
  return IMQ_SUCCESS;
}

/*
 * Write the value of the object to the output stream.
 *
 * Return an error if the write fails.
 */
iMQError 
Short::write(IMQDataOutputStream * const out) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );

  RETURN_IF_ERROR( out->writeInt16(this->value) );
  
  return IMQ_SUCCESS;
}

/*
 * Print the value of the object to the file.
 *
 * Return an error if the print fails
 */
iMQError 
Short::print(FILE * const file) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( file );

  PRInt32 bytesWritten = fprintf(file, "%d", (PRInt32)this->value);
  RETURN_ERROR_IF( bytesWritten <= 0, IMQ_FILE_OUTPUT_ERROR );
  
  return IMQ_SUCCESS;
}

/*
 *
 */
PRBool       
Short::equals(const BasicType * const object) const
{
  CHECK_OBJECT_VALIDITY();

  return ((object != NULL)                          &&
          (object->getType() == this->getType())    &&
          (((Short*)object)->getValue() == this->value));
}


/*
 * Returns a 32-bit hash code for this number.  
 */
PLHashNumber
Short::hashCode() const
{
  CHECK_OBJECT_VALIDITY();

  return this->value;
}


/*
 * Return a char* representation of this object.
 */
const char *
Short::toString()
{
  CHECK_OBJECT_VALIDITY();

  if (this->valueStr != NULL) {
    return this->valueStr;
  } 
  this->valueStr = new char[SHORT_MAX_STR_SIZE];
  if (this->valueStr == NULL) {
    return "<out-of-memory>";
  }

  SNPRINTF(this->valueStr, SHORT_MAX_STR_SIZE, "%d", (PRInt32)this->value);
  // Just to be safe.  snprintf won't automatically null terminate for us.
  this->valueStr[SHORT_MAX_STR_SIZE-1] = '\0'; 

  return this->valueStr;
}


/*
 *
 */
iMQError
Short::getInt16Value(PRInt16 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = this->value;

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
Short::getInt32Value(PRInt32 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = this->value;

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
Short::getInt64Value(PRInt64 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  LL_I2L( *valueArg, (PRInt32)this->value);

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
Short::getStringValue(const char ** const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = ((Short*)this)->toString();

  return IMQ_SUCCESS;
}
