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
 * @(#)IMQDataInputStream.cpp	1.3 06/26/07
 */ 

#include "../debug/DebugUtils.h"
#include "IMQDataInputStream.hpp"
#include "../util/UtilityMacros.h"

/*
 * 
 */
iMQError 
IMQDataInputStream::readBoolean(PRBool * const value)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( value );
  *value = PR_FALSE;

  // The boolean is only one byte in the file
  PRPackedBool packedBool = PR_FALSE;
  RETURN_IF_ERROR( readUint8((PRUint8*)&packedBool) );

  *value = packedBool;

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
IMQDataInputStream::readUint8Array(PRUint8 * const values, 
                                   const PRUint32 numToRead)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( values );

  for (PRUint32 i = 0; i < numToRead; i++) {
    RETURN_IF_ERROR( readUint8(&(values[i])) );
  }

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
IMQDataInputStream::readInt8(PRInt8 * const value)
{
  CHECK_OBJECT_VALIDITY();

  return readUint8((PRUint8*)value);
}

/*
 *
 */
iMQError
IMQDataInputStream::readInt16(PRInt16 * const value)
{
  CHECK_OBJECT_VALIDITY();

  return readUint16((PRUint16*)value);
}

/*
 *
 */
iMQError
IMQDataInputStream::readInt32(PRInt32 * const value)
{
  CHECK_OBJECT_VALIDITY();

  return readUint32((PRUint32*)value);
}

/*
 *
 */
iMQError 
IMQDataInputStream::readInt64(PRInt64 * const value)
{
  CHECK_OBJECT_VALIDITY();

  return readUint64((PRUint64*)value);
}

/*
 *
 */
iMQError 
IMQDataInputStream::readFloat32(PRFloat32 * const value)
{
  CHECK_OBJECT_VALIDITY();

  return readUint32((PRUint32*)value);
}

/*
 *
 */
iMQError 
IMQDataInputStream::readFloat64(PRFloat64 * const value)
{
  CHECK_OBJECT_VALIDITY();

  return readUint64((PRUint64*)value);
}
