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
 * @(#)Serialize.hpp	1.3 06/26/07
 */ 

#ifndef SERIALIZE_HPP
#define SERIALIZE_HPP

#include <prlong.h>
#include "../util/PRTypesUtils.h"
#include "../basictypes/TypeEnum.hpp"
#include "../basictypes/BasicType.hpp"
#include "../error/ErrorCodes.h"

/** 
 * This class is used to maniuplate the constants associated with
 * (de-)serializing the Java hashtable which is the properties field
 * of the iMQ message.  It also has a few methods that can be used to
 * convert a Java serialization ID to the ID of another class. */
class Serialize 
{
public:
  /**
   * Converts the Java class serialization ID, serialID,
   * (e.g. 0x12E2A0A4F7818738) to the corresponding TypeEnum
   * (e.g. INTEGER_TYPE) and places the result in the output parameter
   * classType.  An error is returned if serialID is not valid or if
   * classType is NULL.  This function is primarily used by
   * SerialDataInputStream. 
   * @param serialID the serial ID (e.g. BOOLEAN_SERIAL_ID) to translate
   *  to a type 
   * @param classType the type that serialID corresponds to
   * @return IMQ_SUCCESS if successful and an error otherwise
   */
  static iMQError serialIDToType(const PRUint64   serialID, 
                                 TypeEnum * const classType);

  /**
   * Converts the TypeEnum (e.g. INTEGER_TYPE) to the corresponding
   * Java class serialization ID, serialID, (e.g. 0x12E2A0A4F7818738)
   * and places the result in the output parameter classType.  An
   * error is returned if classType is not valid or if serialID is
   * NULL.  This function is primarily used by SerialDataOutputStream.
   * @param classType the type of the class to convert to a serialID
   * @param serialID the serialID of classType
   * @return IMQ_SUCCESS if successful and an error otherwise
   */
   static iMQError typeToSerialID(const TypeEnum classType, 
                                  PRUint64 * const serialID);

  /** 
   * Returns the serialized class description for classType
   * (e.g. INTEGER_TYPE) in the output parameter classDesc.  The
   * length of the class description is stored in the output parameter
   * classDescLen.  It returns an error if classType is invalid, or if
   * classDesc or classDescLen is NULL.  
   * @param classType the class to get the description for
   * @param classDesc the serialized description of classType
   * @param classDescLen the length of the serialized description 
   *  of classType
   * @return IMQ_SUCCESS if successful and an error otherwise */
  static iMQError classTypeToClassDescBytes(
                    const TypeEnum classType, 
                    PRUint8 const ** const classDesc, 
                    PRUint32 * const classDescLen);

  /** 
   * Creates a new object of the type specified in classType and
   * returns the object in the output parameter object.  It returns an
   * error if classType is invalid or if object is NULL. 
   * @param classType the type of the class to create an object for
   * @param object the newly created object
   * @return IMQ_SUCCESS if successful and an error otherwise */
  static iMQError createObject(const TypeEnum classType, 
                               BasicType ** const object);

//
// This class only has static methods, so don't even allow an instance
// to be constructed.  Also, avoid all implicit shallow copies.
// Without these, the compiler will automatically define
// implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  Serialize();
  Serialize(const Serialize& serialize);
  Serialize& operator=(const Serialize& serialize);

}; // class Serialize


// These are copied directly from section "6.4.2 Terminal Symbols
// and Constatns" of "Java Object Serialization Specification--Java
// 2 SDK, Standard Edition, v1.3 Beta"
static const PRUint16 SERIALIZE_STREAM_MAGIC        = 0xACED;
static const PRUint16 SERIALIZE_STREAM_VERSION      = 5;
static const PRUint8  SERIALIZE_TC_NULL             = 0x70;
static const PRUint8  SERIALIZE_TC_REFERENCE        = 0x71;
static const PRUint8  SERIALIZE_TC_CLASSDESC        = 0x72;
static const PRUint8  SERIALIZE_TC_OBJECT           = 0x73;
static const PRUint8  SERIALIZE_TC_STRING           = 0x74;
static const PRUint8  SERIALIZE_TC_ARRAY            = 0x75;
static const PRUint8  SERIALIZE_TC_CLASS            = 0x76;
static const PRUint8  SERIALIZE_TC_BLOCKDATA        = 0x77;
static const PRUint8  SERIALIZE_TC_ENDBLOCKDATA     = 0x78;
static const PRUint8  SERIALIZE_TC_RESET            = 0x79;
static const PRUint8  SERIALIZE_TC_BLOCKDATALONG    = 0x7A;
static const PRUint8  SERIALIZE_TC_EXCEPTION        = 0x7B;
static const PRUint8  SERIALIZE_TC_LONGSTRING       = 0x7C;
static const PRUint8  SERIALIZE_TC_PROXYCLASSDESC   = 0x7D;
static const PRUint32 SERIALIZE_BASE_WIRE_HANDLE    = 0x007E0000;
static const PRUint8  SERIALIZE_SC_WRITE_METHOD     = 0x01;
static const PRUint8  SERIALIZE_SC_BLOCK_DATA       = 0x08;
static const PRUint8  SERIALIZE_SC_SERIALIZABLE     = 0x02;
static const PRUint8  SERIALIZE_SC_EXTERNALIZABLE   = 0x04;

// These are the 64-bit class IDs for each of the classes that we
// must be able to (de-)serialize.  They were determined by
// serializing an object of the given type from Java, and then
// examining the ID that resulted.  These are for JVM 1.3.  They
// shouldn't change between versions of the JVM, but they might.
//
// PORTABILITY: for platforms where 64 bit integers are actually
// structs, these initializations might fail, and we'll have to move
// these out of the class.
static const PRUint64 SERIALIZE_BOOLEAN_SERIAL_ID   = (PRUint64)LL_INIT( 0xCD207280, 0xD59CFAEE );
static const PRUint64 SERIALIZE_BYTE_SERIAL_ID      = (PRUint64)LL_INIT( 0x9C4E6084, 0xEE50F51C );
static const PRUint64 SERIALIZE_SHORT_SERIAL_ID     = (PRUint64)LL_INIT( 0x684D3713, 0x3460DA52 );
static const PRUint64 SERIALIZE_INTEGER_SERIAL_ID   = (PRUint64)LL_INIT( 0x12E2A0A4, 0xF7818738 );
static const PRUint64 SERIALIZE_LONG_SERIAL_ID      = (PRUint64)LL_INIT( 0x3B8BE490, 0xCC8F23DF );
static const PRUint64 SERIALIZE_FLOAT_SERIAL_ID     = (PRUint64)LL_INIT( 0xDAEDC9A2, 0xDB3CF0EC );
static const PRUint64 SERIALIZE_DOUBLE_SERIAL_ID    = (PRUint64)LL_INIT( 0x80B3C24A, 0x296BFB04 );
static const PRUint64 SERIALIZE_NUMBER_SERIAL_ID    = (PRUint64)LL_INIT( 0x86AC951D, 0x0B94E08B );
static const PRUint64 SERIALIZE_HASHTABLE_SERIAL_ID = (PRUint64)LL_INIT( 0x13BB0F25, 0x214AE4B8 );
static const PRUint64 SERIALIZE_INVALID_SERIAL_ID   = (PRUint64)LL_INIT( 0xDEADBEEF, 0xDEADBEEF );

const static PRUint8 TC_OBJECT_BYTES[] = {SERIALIZE_TC_OBJECT}; 
const static PRUint8 TC_BLOCKDATA_BYTES[] = {SERIALIZE_TC_BLOCKDATA}; 
const static PRUint8 TC_ENDBLOCKDATA_BYTES[] = {SERIALIZE_TC_ENDBLOCKDATA}; 
const static PRUint8 HASHTABLE_BLOCKDATA_SIZE_BYTES[] = {0x08}; 

// These are the bytes that describe the class which occur after the
// serialVersionID of the class.  They were determined by serializing
// an object of the given type from Java, and then examining the byte
// descriptions that resulted.  These are for JVM 1.3.  They shouldn't
// change between versions of the JVM, but they might.

const static PRUint8 HASHTABLE_CLASS_DESC[] =
{ 0x03, 0x00, 0x02, 0x46, 0x00, 0x0A, 0x6C, 0x6F, 0x61, 0x64, 
  0x46, 0x61, 0x63, 0x74, 0x6F, 0x72, 0x49, 0x00, 0x09, 0x74, 
  0x68, 0x72, 0x65, 0x73, 0x68, 0x6F, 0x6C, 0x64, 0x78 };

const static PRUint8 BOOLEAN_CLASS_DESC[] = 
{ 0x02, 0x00, 0x01, 0x5A, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 BYTE_CLASS_DESC[] = 
{ 0x02, 0x00, 0x01, 0x42, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 SHORT_CLASS_DESC[] = 
{ 0x02, 0x00, 0x01, 0x53, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 INTEGER_CLASS_DESC[] =
{ 0x02, 0x00, 0x01, 0x49, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75,
  0x65, 0x78 };

const static PRUint8 LONG_CLASS_DESC[] = 
{ 0x02, 0x00, 0x01, 0x4A, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 FLOAT_CLASS_DESC[] = 
{ 0x02, 0x00, 0x01, 0x46, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 DOUBLE_CLASS_DESC[] = 
{ 0x02, 0x00, 0x01, 0x44, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 NUMBER_CLASS_DESC[] =
{ 0x02, 0x00, 0x00, 0x78 };


// Lookup table that returns the serialized class desc for the
// indexed type.
const static PRUint8 * CLASS_DESC_BY_TYPE[] = 
{
  BOOLEAN_CLASS_DESC,    // BOOLEAN_TYPE          = 0,
  BYTE_CLASS_DESC,       // BYTE_TYPE             = 1,
  SHORT_CLASS_DESC,      // SHORT_TYPE            = 2,
  INTEGER_CLASS_DESC,    // INTEGER_TYPE          = 3,
  LONG_CLASS_DESC,       // LONG_TYPE             = 4,
  FLOAT_CLASS_DESC,      // FLOAT_TYPE            = 5
  DOUBLE_CLASS_DESC,     // DOUBLE_TYPE           = 6,
  NULL,                  // UTF8_STRING_TYPE      = 7,
  NULL,                  // UTF8_LONG_STRING_TYPE = 8,
  HASHTABLE_CLASS_DESC,  // HASHTABLE_TYPE        = 9,
  NUMBER_CLASS_DESC,     // NUMBER_TYPE           = 10,
  NULL,                  // UNKNOWN_TYPE          = 11,
  NULL,                  // NULL_TYPE             = 12
};

// Lookup table that returns the size of the serialized class desc
// for the indexed type.
const static PRUint32 CLASS_DESC_SIZE_BY_TYPE[] = 
{
  sizeof(BOOLEAN_CLASS_DESC),    // BOOLEAN_TYPE          = 0,
  sizeof(BYTE_CLASS_DESC),       // BYTE_TYPE             = 1,
  sizeof(SHORT_CLASS_DESC),      // SHORT_TYPE            = 2,
  sizeof(INTEGER_CLASS_DESC),    // INTEGER_TYPE          = 3,
  sizeof(LONG_CLASS_DESC),       // LONG_TYPE             = 4,
  sizeof(FLOAT_CLASS_DESC),      // FLOAT_TYPE            = 5
  sizeof(DOUBLE_CLASS_DESC),     // DOUBLE_TYPE           = 6,
  0,                             // UTF8_STRING_TYPE      = 7,
  0,                             // UTF8_LONG_STRING_TYPE = 8,
  sizeof(HASHTABLE_CLASS_DESC),  // HASHTABLE_TYPE        = 9,
  sizeof(NUMBER_CLASS_DESC),     // NUMBER_TYPE           = 10,
  0,                             // UNKNOWN_TYPE          = 11,
  0,                             // NULL_TYPE             = 12
};



// These are the bytes that describe the class including the class
// name and the serialVersionID of the class.  They were determined by
// serializing an object of the given type from Java, and then
// examining the byte descriptions that resulted.  These are for JVM
// 1.3.  They shouldn't change between versions of the JVM, but they
// might.
const static PRUint8 BOOLEAN_FULL_CLASS_DESC[] = 
{ // 00 11 java.util.Boolean
  0x00, 0x11, 0x6A, 0x61, 0x76, 0x61, 0x2E, 0x6C, 0x61, 0x6E, 
  0x67, 0x2E, 0x42, 0x6F, 0x6F, 0x6C, 0x65, 0x61, 0x6E,
  // same as SERIALIZE_BOOLEAN_SERIAL_ID
  0xCD, 0x20, 0x72, 0x80, 0xD5, 0x9C, 0xFA, 0xEE,
  // same as BOOLEAN_CLASS_DESC
  0x02, 0x00, 0x01, 0x5A, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 BYTE_FULL_CLASS_DESC[] = 
{ // 00 0E java.util.Byte
  0x00, 0x0E, 0x6A, 0x61, 0x76, 0x61, 0x2E, 0x6C, 0x61, 0x6E, 
  0x67, 0x2E, 0x42, 0x79, 0x74, 0x65, 
  // same as SERIALIZE_BYTE_SERIAL_ID
  0x9C, 0x4E, 0x60, 0x84, 0xEE, 0x50, 0xF5, 0x1C,
  // same as BYTE_CLASS_DESC
  0x02, 0x00, 0x01, 0x42, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 SHORT_FULL_CLASS_DESC[] = 
{ // 00 0F java.util.Short
  0x00, 0x0F, 0x6A, 0x61, 0x76, 0x61, 0x2E, 0x6C, 0x61, 0x6E, 
  0x67, 0x2E, 0x53, 0x68, 0x6F, 0x72, 0x74,
  // same as SERIALIZE_SHORT_SERIAL_ID
  0x68, 0x4D, 0x37, 0x13, 0x34, 0x60, 0xDA, 0x52,
  // same as SHORT_CLASS_DESC
  0x02, 0x00, 0x01, 0x53, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 INTEGER_FULL_CLASS_DESC[] =
{ // 00 11 java.util.Integer
  0x00, 0x11, 0x6A, 0x61, 0x76, 0x61, 0x2E, 0x6C, 0x61, 0x6E, 
  0x67, 0x2E, 0x49, 0x6E, 0x74, 0x65, 0x67, 0x65, 0x72,
  // same as SERIALIZE_INTEGER_SERIAL_ID
  0x12, 0xE2, 0xA0, 0xA4, 0xF7, 0x81, 0x87, 0x38,
  // same as INTEGER_CLASS_DESC
  0x02, 0x00, 0x01, 0x49, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75,
  0x65, 0x78 };

const static PRUint8 LONG_FULL_CLASS_DESC[] = 
{ // 00 0E java.util.Long
  0x00, 0x0E, 0x6A, 0x61, 0x76, 0x61, 0x2E, 0x6C, 0x61, 0x6E, 
  0x67, 0x2E, 0x4C, 0x6F, 0x6E, 0x67,
  // same as SERIALIZE_LONG_SERIAL_ID
  0x3B, 0x8B, 0xE4, 0x90, 0xCC, 0x8F, 0x23, 0xDF,
  // same as LONG_CLASS_DESC
  0x02, 0x00, 0x01, 0x4A, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 FLOAT_FULL_CLASS_DESC[] = 
{ // 00 0F java.util.Float
  0x00, 0x0F, 0x6A, 0x61, 0x76, 0x61, 0x2E, 0x6C, 0x61, 0x6E, 
  0x67, 0x2E, 0x46, 0x6C, 0x6F, 0x61, 0x74,
  // same as SERIALIZE_FLOAT_SERIAL_ID
  0xDA, 0xED, 0xC9, 0xA2, 0xDB, 0x3C, 0xF0, 0xEC,
  // same as FLOAT_CLASS_DESC
  0x02, 0x00, 0x01, 0x46, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 DOUBLE_FULL_CLASS_DESC[] = 
{ // 00 10 java.util.Double
  0x00, 0x10, 0x6A, 0x61, 0x76, 0x61, 0x2E, 0x6C, 0x61, 0x6E, 
  0x67, 0x2E, 0x44, 0x6F, 0x75, 0x62, 0x6C, 0x65,
  // same as SERIALIZE_DOUBLE_SERIAL_ID
  0x80, 0xB3, 0xC2, 0x4A, 0x29, 0x6B, 0xFB, 0x04,
  // same as DOUBLE_CLASS_DESC
  0x02, 0x00, 0x01, 0x44, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 
  0x65, 0x78 };

const static PRUint8 NUMBER_FULL_CLASS_DESC[] =
{ // 00 10 java.util.Number
  0x00, 0x10, 0x6A, 0x61, 0x76, 0x61, 0x2E, 0x6C, 0x61, 0x6E, 
  0x67, 0x2E, 0x4E, 0x75, 0x6D, 0x62, 0x65, 0x72,
  // same as SERIALIZE_NUMBER_SERIAL_ID
  0x86, 0xAC, 0x95, 0x1D, 0x0B, 0x94, 0xE0, 0x8B,
  // same as NUMBER_CLASS_DESC
  0x02, 0x00, 0x00, 0x78 };


// Lookup table that returns the full serialized class desc for the
// indexed type.
const static PRUint8 * FULL_CLASS_DESC_BY_TYPE[] = 
{
  BOOLEAN_FULL_CLASS_DESC,  // BOOLEAN_TYPE          = 0,
  BYTE_FULL_CLASS_DESC,     // BYTE_TYPE             = 1,
  SHORT_FULL_CLASS_DESC,    // SHORT_TYPE            = 2,
  INTEGER_FULL_CLASS_DESC,  // INTEGER_TYPE          = 3,
  LONG_FULL_CLASS_DESC,     // LONG_TYPE             = 4,
  FLOAT_FULL_CLASS_DESC,    // FLOAT_TYPE            = 5
  DOUBLE_FULL_CLASS_DESC,   // DOUBLE_TYPE           = 6,
  NULL,                     // UTF8_STRING_TYPE      = 7,
  NULL,                     // UTF8_LONG_STRING_TYPE = 8,
  NULL,                     // HASHTABLE_TYPE        = 9,
  NUMBER_FULL_CLASS_DESC,   // NUMBER_TYPE           = 10,
  NULL,                     // UNKNOWN_TYPE          = 11,
  NULL,                     // NULL_TYPE             = 12
};

// Lookup table that returns the size of the full serialized class
// desc for the indexed type.
const static PRUint32 FULL_CLASS_DESC_SIZE_BY_TYPE[] = 
{
  sizeof(BOOLEAN_FULL_CLASS_DESC),  // BOOLEAN_TYPE          = 0,
  sizeof(BYTE_FULL_CLASS_DESC),     // BYTE_TYPE             = 1,
  sizeof(SHORT_FULL_CLASS_DESC),    // SHORT_TYPE            = 2,
  sizeof(INTEGER_FULL_CLASS_DESC),  // INTEGER_TYPE          = 3,
  sizeof(LONG_FULL_CLASS_DESC),     // LONG_TYPE             = 4,
  sizeof(FLOAT_FULL_CLASS_DESC),    // FLOAT_TYPE            = 5
  sizeof(DOUBLE_FULL_CLASS_DESC),   // DOUBLE_TYPE           = 6,
  0,                                // UTF8_STRING_TYPE      = 7,
  0,                                // UTF8_LONG_STRING_TYPE = 8,
  0,                                // HASHTABLE_TYPE        = 9,
  sizeof(NUMBER_FULL_CLASS_DESC),   // NUMBER_TYPE           = 10,
  0,                                // UNKNOWN_TYPE          = 11,
  0,                                // NULL_TYPE             = 12
};

// Lookup table that returns the super type of the indexed type.
const static TypeEnum SUPER_CLASS_BY_TYPE[] = 
{
  NULL_TYPE,       // BOOLEAN_TYPE          = 0,
  NUMBER_TYPE,     // BYTE_TYPE             = 1,
  NUMBER_TYPE,     // SHORT_TYPE            = 2,
  NUMBER_TYPE,     // INTEGER_TYPE          = 3,
  NUMBER_TYPE,     // LONG_TYPE             = 4,
  NUMBER_TYPE,     // FLOAT_TYPE            = 5
  NUMBER_TYPE,     // DOUBLE_TYPE           = 6,
  UNKNOWN_TYPE,    // UTF8_STRING_TYPE      = 7,
  UNKNOWN_TYPE,    // UTF8_LONG_STRING_TYPE = 8,
  NULL_TYPE,       // HASHTABLE_TYPE        = 9,
  NULL_TYPE,       // NUMBER_TYPE           = 10,
  UNKNOWN_TYPE,    // UNKNOWN_TYPE          = 11,
  UNKNOWN_TYPE,    // NULL_TYPE             = 12
};


// Lookup table that returns the serial id of the indexed type.
const static PRUint64 SERIAL_ID_BY_TYPE[] = 
{
  SERIALIZE_BOOLEAN_SERIAL_ID,    // BOOLEAN_TYPE          = 0,
  SERIALIZE_BYTE_SERIAL_ID,       // BYTE_TYPE             = 1,
  SERIALIZE_SHORT_SERIAL_ID,      // SHORT_TYPE            = 2,
  SERIALIZE_INTEGER_SERIAL_ID,    // INTEGER_TYPE          = 3,
  SERIALIZE_LONG_SERIAL_ID,       // LONG_TYPE             = 4,
  SERIALIZE_FLOAT_SERIAL_ID,      // FLOAT_TYPE            = 5
  SERIALIZE_DOUBLE_SERIAL_ID,     // DOUBLE_TYPE           = 6,
  SERIALIZE_INVALID_SERIAL_ID,    // UTF8_STRING_TYPE      = 7,
  SERIALIZE_INVALID_SERIAL_ID,    // UTF8_LONG_STRING_TYPE = 8,
  SERIALIZE_HASHTABLE_SERIAL_ID,  // HASHTABLE_TYPE        = 9,
  SERIALIZE_NUMBER_SERIAL_ID,     // NUMBER_TYPE           = 10,
  SERIALIZE_INVALID_SERIAL_ID,    // UNKNOWN_TYPE          = 11,
  SERIALIZE_INVALID_SERIAL_ID,    // NULL_TYPE             = 12
};



// These bytes will appear at the beginning of every Java serialized
// Hashtable.
const static PRUint8 SERIALIZE_HASHTABLE_PREFIX[] =
{ 0xAC, 0xED,                       // magic number
  0x00, 0x05,                       // stream version
  0x73,                             // TC_OBJECT
  0x72,                             // TC_CLASSDESC
    0x00, 0x13,                     // chars in java.util.Hashtable = 19
      0x6A, 0x61, 0x76, 0x61, 0x2E, // "java."-
      0x75, 0x74, 0x69, 0x6C, 0x2E, // "util."-
      0x48, 0x61, 0x73, 0x68, 0x74, 0x61, 0x62, 0x6C, 0x65, // "Hashtable"
  0x13, 0xBB, 0x0F, 0x25, 0x21, 0x4A, 0xE4, 0xB8, // serial Version ID
  0x03,                             // SC_WRITE_METHOD | SC_SERIALIZABLE
  0x00, 0x02,                       // number of fields = 2
    0x46,                           // 'F' for float
      0x00, 0x0A,                   // chars in "loadFactor" = 10
      0x6C, 0x6F, 0x61, 0x64,              // "load"-
      0x46, 0x61, 0x63, 0x74, 0x6F, 0x72,  // "factor" 
    0x49,                           // 'I' for integer
      0x00, 0x09,                   // chars in "threshold" = 9
      0x74, 0x68, 0x72, 0x65, 0x73, 0x68, 0x6F, 0x6C, 0x64,  // "threshold"
  0x78,                             // TC_ENDBLOCKDATA
  0x70                              // TC_NULL (=> no superclass)
};
#endif // SERIALIZATION_HPP








