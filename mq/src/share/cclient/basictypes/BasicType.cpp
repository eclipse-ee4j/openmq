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
 * @(#)BasicType.cpp	1.5 06/26/07
 */ 

#include "BasicType.hpp"
#include "../util/UtilityMacros.h"

/*
 *
 */
PRBool 
BasicType::getIsBasicType() const
{
  CHECK_OBJECT_VALIDITY();

  return PR_TRUE;
}


/*
 *
 */
iMQError
BasicType::getBoolValue(PRBool * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = PR_FALSE;

  return IMQ_INVALID_TYPE_CONVERSION;
}

/*
 *
 */
iMQError
BasicType::getInt8Value(PRInt8 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = 0;

  return IMQ_INVALID_TYPE_CONVERSION;
}

/*
 *
 */
iMQError
BasicType::getInt16Value(PRInt16 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = 0;

  return IMQ_INVALID_TYPE_CONVERSION;
}

/*
 *
 */
iMQError
BasicType::getInt32Value(PRInt32 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = 0;

  return IMQ_INVALID_TYPE_CONVERSION;
}

/*
 *
 */
iMQError
BasicType::getInt64Value(PRInt64 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = 0;

  return IMQ_INVALID_TYPE_CONVERSION;
}

/*
 *
 */
iMQError
BasicType::getFloat32Value(PRFloat32 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = 0.0;

  return IMQ_INVALID_TYPE_CONVERSION;
}

/*
 *
 */
iMQError
BasicType::getFloat64Value(PRFloat64 * const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = 0.0;

  return IMQ_INVALID_TYPE_CONVERSION;
}

/*
 *
 */
iMQError
BasicType::getStringValue(const char ** const valueArg) const
{
  RETURN_ERROR_IF_NULL( valueArg );
  *valueArg = "";

  return IMQ_INVALID_TYPE_CONVERSION;
}


