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
 * %W% %G%
 */ 

#include "Long.hpp"
#include "../debug/DebugUtils.h"
#include "../util/UtilityMacros.h"
#include "../util/PRTypesUtils.h"

/*
 * Default constructor.
 */
Long::Long()
{
  CHECK_OBJECT_VALIDITY();

  this->value    = LL_ULLFromHiLo(LONG_DEFAULT_HI_VALUE, LONG_DEFAULT_LO_VALUE);
  this->valueStr = NULL;
}

/*
 * 
 */
Long::Long(const PRInt64 valueArg)
{
  CHECK_OBJECT_VALIDITY();

  this->value    = valueArg;
  this->valueStr = NULL;
}

/*
 * 
 */
Long::~Long()
{
  CHECK_OBJECT_VALIDITY();

  DELETE_ARR( this->valueStr );
}

/*
 * Return a pointer to a deep copy of this object.
 */
BasicType *
Long::clone() const
{
  CHECK_OBJECT_VALIDITY();

  return new Long(this->value);
}

/*
 * Set the value of this object to the value parameter.
 */
void
Long::setValue(const PRInt64 valueArg)
{
  CHECK_OBJECT_VALIDITY();

  this->value = valueArg;
}

/*
 * Return the value of this object.
 */
PRInt64
Long::getValue() const
{
  CHECK_OBJECT_VALIDITY();

  return this->value;
}

/*
 * Return the type of this object.
 */
TypeEnum
Long::getType() const
{
  CHECK_OBJECT_VALIDITY();

  return LONG_TYPE;
}

/*
 * Read the value of the object from the input stream.
 *
 * Return an error if the read fails.
 */
iMQError 
Long::read(IMQDataInputStream * const in)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( in );

  RETURN_IF_ERROR( in->readInt64(&this->value) );
  
  return IMQ_SUCCESS;
}

/*
 * Write the value of the object to the output stream.
 *
 * Return an error if the write fails.
 */
iMQError 
Long::write(IMQDataOutputStream * const out) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );

  RETURN_IF_ERROR( out->writeInt64(this->value) );
  
  return IMQ_SUCCESS;
}

/*
 * Print the value of the object to the file.
 *
 * Return an error if the print fails
 */
iMQError 
Long::print(FILE * const file) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( file );

  PRInt32 bytesWritten = fprintf(file, "LONG LONG");
  RETURN_ERROR_IF( bytesWritten <= 0, IMQ_FILE_OUTPUT_ERROR );
  
  return IMQ_SUCCESS;
}

/*
 *
 */
PRBool       
Long::equals(const BasicType * const object) const
{
  CHECK_OBJECT_VALIDITY();

  return ((object != NULL)                               &&
          (object->getType() == this->getType())         &&
          LL_EQ( ((Long*)object)->getValue(), this->value ));
}

/*
 * Returns a 32-bit hash code for this number.  
 */
PLHashNumber
Long::hashCode() const
{
  CHECK_OBJECT_VALIDITY();

  PRUint32 hi = 0;
  PRUint32 lo = 0;

  // Get the hi and lo parts;
  LL_HiLoFromULL(&hi, &lo, this->value);

  // Bitwise xor the hi and lo parts
  PLHashNumber hashCode = hi ^ lo;

  return hashCode;
}


/*
 * Return a char* representation of this object.
 */
const char *
Long::toString()
{
  CHECK_OBJECT_VALIDITY();

  if (this->valueStr != NULL) {
    return this->valueStr;
  } 
  this->valueStr = new char[LONG_MAX_STR_SIZE];
  if (this->valueStr == NULL) {
    return "<out-of-memory>";
  }

  PR_snprintf(this->valueStr, LONG_MAX_STR_SIZE, "%lld", this->value);
  // Just to be safe.  snprintf won't automatically null terminate for us.
  this->valueStr[LONG_MAX_STR_SIZE-1] = '\0'; 

  return this->valueStr;
}

/*
 *
 */
iMQError
Long::getInt64Value(PRInt64 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = this->value;

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
Long::getStringValue(const char ** const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = ((Long*)this)->toString();

  return IMQ_SUCCESS;
}
