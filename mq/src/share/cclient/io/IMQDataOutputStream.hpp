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
 * @(#)IMQDataOutputStream.hpp	1.3 06/26/07
 */ 

#ifndef IMQDATAOUTPUTSTREAM_HPP
#define IMQDATAOUTPUTSTREAM_HPP

#include <prtypes.h>
#include "../error/ErrorCodes.h"
#include "../util/PRTypesUtils.h"
#include "../basictypes/Object.hpp"

/** 
 * This class is the the abstract base class that defines how to write to a
 * stream.
 */
class IMQDataOutputStream : public Object {
public:
// These are the only methods non-abstract subclasses must implement
// WriteUint[16|32|64] depend on the endianness of the stream so
// IMQDataOutputStream cannot implement them by calling writeUint8.

  /** 
   * This method writes the 8-bit unsigned integer value to the output stream.
   *
   * @param value is number to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeUint8(const PRUint8 value) = 0;

  /** 
   * This method writes the 16-bit unsigned integer value to the output stream.
   *
   * @param value is number to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeUint16(const PRUint16 value) = 0;

  /** 
   * This method writes the 32-bit unsigned integer value to the output stream.
   *
   * @param value is number to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeUint32(const PRUint32 value) = 0;

  /** 
   * This method writes the 64-bit unsigned integer value to the output stream.
   *
   * @param value is number to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeUint64(const PRUint64 value) = 0;

// IMQDataOutputStream provides default implementations of these
// by calling the unsigned counterparts and casting the result.

  /** 
   * This method writes the boolean value to the output stream as an 8-bit
   * boolean.
   *
   * @param value is boolean to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeBoolean(const PRBool value);

  /** 
   * This method writes the 8-bit signed integer value to the output stream.
   *
   * @param value is number to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeInt8(const PRInt8 value);

  /** 
   * This method writes numToWrite 8-bit unsigned integers from the value array
   * to the output stream.
   *
   * @param value is array to write
   * @param numToWrite is the number of 8-bit unsigned integers to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeUint8Array(const PRUint8 values[], 
                                   const PRUint32 numToWrite);

  /** 
   * This method writes the 16-bit signed integer value to the output stream.
   *
   * @param value is number to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeInt16(const PRInt16 value);

  /** 
   * This method writes the 32-bit signed integer value to the output stream.
   *
   * @param value is number to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeInt32(const PRInt32 value);

  /** 
   * This method writes the 64-bit signed integer value to the output stream.
   *
   * @param value is number to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeInt64(const PRInt64 value);

  /** 
   * This method writes the 32-bit floating point number value to the output
   * stream.
   *
   * @param value is number to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeFloat32(const PRFloat32 value);

  /** 
   * This method writes the 64-bit floating point number value to the output
   * stream.
   *
   * @param value is number to write
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError writeFloat64(const PRFloat64 value);
};

#endif // IMQDATAINPUTSTREAM_HPP
