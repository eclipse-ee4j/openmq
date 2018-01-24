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
 * @(#)IMQDataInputStream.hpp	1.3 06/26/07
 */ 

#ifndef IMQDATAINPUTSTREAM_HPP
#define IMQDATAINPUTSTREAM_HPP

#include <nspr.h>

#include "../error/ErrorCodes.h"
#include "../util/PRTypesUtils.h"
#include "../basictypes/Object.hpp"

/** 
 * This class is the the abstract base class that defines how to read
 * from a stream.  */
class IMQDataInputStream : public Object {
public:
// The following five methods are the only methods non-abstract subclasses
// must implement.  ReadUint[16|32|64] depend on the endianness of the stream
// so IMQDataInputStream cannot implement them by calling readUint8.

  /** Return true iff at the end of the stream. */ 
  virtual PRBool    endOfStream() const = 0;

  /** 
   * This method reads an 8-bit unsigned integer from the input stream and
   * returns the result in value.
   *
   * @param value is the output parameter where the result is placed.
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.
   */
  virtual iMQError  readUint8(PRUint8 * const value) = 0;

  /** 
   * This method reads a 16-bit unsigned integer from the input stream and
   * returns the result in value.
   *
   * @param value is the output parameter where the result is placed.
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.
   */
  virtual iMQError  readUint16(PRUint16 * const value) = 0;

  /** 
   * This method reads a 32-bit unsigned integer from the input stream and
   * returns the result in value.
   *
   * @param value is the output parameter where the result is placed.
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.
   */
  virtual iMQError  readUint32(PRUint32 * const value) = 0;

  /** 
   * This method reads a 64-bit unsigned integer from the input stream and
   * returns the result in value.
   *
   * @param value is the output parameter where the result is placed.
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.
   */
  virtual iMQError  readUint64(PRUint64 * const value) = 0;

// IMQDataInputStream provides default implementations of these eight methods
// by calling the unsigned counterparts and casting the result.

  /** 
   * This method reads an 8-bit boolean from the input stream and returns the
   * result in value.
   *
   * @param value is the output parameter where the result is placed.
   * @returns IMQ_SUCCESS if the read was successful and false otherwise. 
   */
  virtual iMQError  readBoolean(PRBool * const value);

  /** 
   * This method reads an array of 8-bit unsigned integers from the input stream
   * and returns the result in value.  The value array must have at least
   * numToRead elements.
   *
   * @param value is the output parameter where the result is placed.
   * @param numToRead is the number of 8-bit unsigned integers to read
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError  readUint8Array(PRUint8 * const values, 
                                   const PRUint32 numToRead);

  /** 
   * This method reads an 8-bit signed integer from the input stream and returns
   * the result in value.
   *
   * @param value is the output parameter where the result is placed.
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError  readInt8(PRInt8 * const value);

  /** 
   * This method reads a 16-bit signed integer from the input stream and returns
   * the result in value.
   *
   * @param value is the output parameter where the result is placed.
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError  readInt16(PRInt16 * const value);

  /** 
   * This method reads a 32-bit signed integer from the input stream and returns
   * the result in value.
   *
   * @param value is the output parameter where the result is placed.
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError  readInt32(PRInt32 * const value);

  /** 
   * This method reads a 64-bit signed integer from the input stream and returns
   * the result in value.
   *
   * @param value is the output parameter where the result is placed.
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError  readInt64(PRInt64 * const value);

  /** 
   * This method reads a 32-bit floating point number from the input stream and
   * returns the result in value.
   *
   * @param value is the output parameter where the result is placed.
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError  readFloat32(PRFloat32 * const value);

  /** 
   * This method reads a 64-bit floating point number from the input stream and
   * returns the result in value.
   *
   * @param value is the output parameter where the result is placed.
   * @returns IMQ_SUCCESS if the read was successful and false otherwise.  
   */
  virtual iMQError  readFloat64(PRFloat64 * const value);
};


#endif // IMQDATAINPUTSTREAM_HPP
