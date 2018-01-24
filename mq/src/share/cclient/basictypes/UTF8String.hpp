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
 * @(#)UTF8String.hpp	1.4 06/26/07
 */ 

#ifndef UTF8STRING_HPP
#define UTF8STRING_HPP

#include "BasicType.hpp"
#include "../containers/ObjectVector.hpp"

/** Similar to a Java string.  The string is UTF8 encoded. */
// The size of the shortest string that must be encoded as a
// UTF8_LONG_STRING.
static const PRUint32 UTF8STRING_MIN_LONG_STRING_LENGTH = 0x00010000;
class UTF8String : public BasicType
{
private:

  PRUint8 * value;           // the UTF8 string
  PRUint32  bytesAllocated;  // charsInValue + 1 (assuming value != NULL)
  PRUint32  bytesInValue;    // the number of bytes in string
  PRBool    isLongString;    // true iff the length of value >= 2^16 (i.e. 
                             // it's length doesn't fit in an unsigned short)
  void init();
  void setString(const char * const value, const PRUint32 valueLength);

public:
  UTF8String();
  UTF8String(PRBool isLongString);
  UTF8String(const char * const value);
  UTF8String(const char * const value, const PRUint32 valueLength);
  ~UTF8String();

  /** Resets this string */
  void       reset();
 
  /** */
  iMQError setValue(const char * const value);

  // Currently, all this method does is return a pointer to value,
  // which we NULL terminated when we read it in.  Use it only for
  // debugging purposes.
  const PRUint8 *  toUCharStr() const;

  // getCharStr is basically just an alias for toUCharStr
  const char *  getCharStr() const;

  // calls getCharStr()
  const char * toString();

  const PRUint8 * getBytes() const;
  PRInt32 getBytesSize() const;

  /** Interprets this string as a unsigned 16-bit integer, and returns
   *  the result in uint16Value */
  iMQError getUint16Value(PRUint16 * const uint16Value);
  iMQError readLengthBytes(IMQDataInputStream * const in, PRBool checknull);

  // virtual methods from BasicType that must be implemented
  virtual TypeEnum      getType() const;
  virtual BasicType *   clone() const;
  virtual PRBool        equals(const BasicType * const object) const;
  virtual PLHashNumber  hashCode() const;
  virtual iMQError      read(IMQDataInputStream * const in);
  virtual iMQError      write(IMQDataOutputStream * const out) const;
  virtual iMQError      print(FILE * const file) const;

  

  // Returns the length of the string
  virtual PRInt32       length() const;

  // Returns a Vector of UTF8Strings that are the result of this
  // string being split on the delimeter string delim.  The strings do
  // not include the delimeter.
  iMQError tokenize(const char * const delimStr, ObjectVector ** const strVector ) const;

  virtual iMQError getBoolValue(PRBool * const valueArg) const;
  virtual iMQError getInt8Value(PRInt8 * const value) const;
  virtual iMQError getInt16Value(PRInt16 * const value) const;
  virtual iMQError getInt32Value(PRInt32 * const value) const;
  virtual iMQError getInt64Value(PRInt64 * const value) const;
  virtual iMQError getFloat32Value(PRFloat32 * const value) const;
  virtual iMQError getFloat64Value(PRFloat64 * const value) const;
  virtual iMQError getStringValue(const char ** const value) const;
  
//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  UTF8String(const UTF8String& string);
  UTF8String& operator=(const UTF8String& string);
};
 

#endif // UTF8STRING_HPP
