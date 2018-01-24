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
 * @(#)IMQDataOutputStream.cpp	1.3 06/26/07
 */ 

#include "IMQDataOutputStream.hpp"
#include "../util/UtilityMacros.h"
#include "../error/ErrorCodes.h"

/*
 *
 */
iMQError 
IMQDataOutputStream::writeBoolean(const PRBool value)
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( (value == PR_TRUE) || (value == PR_FALSE) );

  // The boolean is only one byte in the file
  PRPackedBool packedBool = (PRPackedBool)value;
  
  return writeUint8((PRUint8)packedBool);
}


/*
 *
 */
iMQError 
IMQDataOutputStream::writeInt8(const PRInt8 value)
{
  CHECK_OBJECT_VALIDITY();

  return writeUint8((PRUint8)value);
}

/*
 *
 */
iMQError 
IMQDataOutputStream::writeUint8Array(const PRUint8 values[], 
                                     const PRUint32 numToWrite)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( values );
  for (PRUint32 i = 0; i < numToWrite; i++) {
    RETURN_IF_ERROR( writeUint8(values[i]) );
  }
  
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
IMQDataOutputStream::writeInt16(const PRInt16 value)
{
  CHECK_OBJECT_VALIDITY();

  return writeUint16((PRUint16)value);
}

/*
 *
 */
iMQError
IMQDataOutputStream::writeInt32(const PRInt32 value)
{
  CHECK_OBJECT_VALIDITY();

  return writeUint32((PRUint32)value);
}

/*
 *
 */
iMQError
IMQDataOutputStream::writeInt64(const PRInt64 value)
{
  CHECK_OBJECT_VALIDITY();

  return writeUint64((PRUint64)value);
}

/*
 *
 */
iMQError
IMQDataOutputStream::writeFloat32(const PRFloat32 value)
{
  CHECK_OBJECT_VALIDITY();

  return writeUint32(*((PRUint32*)&value));
}

/*
 *
 */
iMQError
IMQDataOutputStream::writeFloat64(const PRFloat64 value)
{
  CHECK_OBJECT_VALIDITY();

  return writeUint64(*((PRUint64*)&value));
}












