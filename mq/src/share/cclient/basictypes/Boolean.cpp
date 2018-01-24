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
 * @(#)Boolean.cpp	1.4 06/26/07
 */ 

#include "Boolean.hpp"
#include "../debug/DebugUtils.h"
#include "../util/UtilityMacros.h"

/*
 * Default constructor.
 */
Boolean::Boolean()
{
  CHECK_OBJECT_VALIDITY();

  this->value = BOOLEAN_DEFAULT_VALUE;
}

/*
 * 
 */
Boolean::Boolean(const PRBool valueArg)
{
  CHECK_OBJECT_VALIDITY();

  this->value = valueArg;
}

/*
 * Return a pointer to a deep copy of this object.
 */ 
BasicType *
Boolean::clone() const
{
  CHECK_OBJECT_VALIDITY();

  return new Boolean(this->value);
}

/*
 * Set the value of this object to the value parameter.
 */
void
Boolean::setValue(const PRBool valueArg)
{
  CHECK_OBJECT_VALIDITY();

  this->value = valueArg;
}

/*
 * Return the value of this object.
 */
PRBool
Boolean::getValue() const
{
  CHECK_OBJECT_VALIDITY();

  return this->value;
}

/*
 * Return the type of this object.
 */
TypeEnum
Boolean::getType() const
{
  CHECK_OBJECT_VALIDITY();

  return BOOLEAN_TYPE;
}

/*
 * Read the value of the object from the input stream.
 *
 * Return an error if the read fails.
 */
iMQError 
Boolean::read(IMQDataInputStream * const in)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( in );

  RETURN_IF_ERROR( in->readBoolean(&this->value) );
  
  return IMQ_SUCCESS;
}

/*
 * Write the value of the object to the output stream.
 *
 * Return an error if the write fails.
 */
iMQError 
Boolean::write(IMQDataOutputStream * const out) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );

  RETURN_IF_ERROR( out->writeBoolean(this->value) );
  
  return IMQ_SUCCESS;
}

/*
 * Print a string representation the value of the object to the file.
 */
iMQError 
Boolean::print(FILE * const file) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( file );

  PRInt64 bytesWritten = fprintf(file, "%s", this->value ? "true" : "false");
  RETURN_ERROR_IF( bytesWritten <= 0, IMQ_FILE_OUTPUT_ERROR );
  
  return IMQ_SUCCESS;
}

/*
 *
 */
PRBool       
Boolean::equals(const BasicType * const object) const
{
  CHECK_OBJECT_VALIDITY();

  return ((object != NULL)                             &&
          (object->getType() == this->getType())       &&
          (((Boolean*)object)->getValue() == this->value));
}

/*
 * Returns a 32-bit hash code for this number.  
 */
PLHashNumber
Boolean::hashCode() const
{
  CHECK_OBJECT_VALIDITY();

  return this->value;
}


/*
 *
 */
const char *
Boolean::toString()
{
  CHECK_OBJECT_VALIDITY();

  if (this->value) {
    return "TRUE";
  } else {
    return "FALSE";
  }
}


/*
 *
 */
iMQError
Boolean::getBoolValue(PRBool * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = this->value;

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
Boolean::getStringValue(const char ** const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = ((Boolean*)this)->toString();

  return IMQ_SUCCESS;
}
