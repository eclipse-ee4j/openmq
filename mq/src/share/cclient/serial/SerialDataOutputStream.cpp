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
 * @(#)SerialDataOutputStream.cpp	1.3 06/26/07
 */ 

#include "SerialDataOutputStream.hpp"
#include "../util/PRTypesUtils.h"
#include "../serial/Serialize.hpp"
#include "../util/UtilityMacros.h"
#include "../basictypes/AllBasicTypes.hpp"

#include <memory.h>  // for memcpy
#include <stdio.h>
#include <nspr.h>


/*
 *
 */
SerialDataOutputStream::SerialDataOutputStream()
{
  CHECK_OBJECT_VALIDITY();

  init();
}

/*
 *
 */
SerialDataOutputStream::~SerialDataOutputStream()
{
  CHECK_OBJECT_VALIDITY();

  reset();
}

/*
 *
 */
void
SerialDataOutputStream::init()
{
  CHECK_OBJECT_VALIDITY();

  streamBytes  = NULL;
  streamLength = 0;
  streamLoc    = 0;
}


/*
 *
 */
void
SerialDataOutputStream::reset()
{
  CHECK_OBJECT_VALIDITY();

  DELETE_ARR( streamBytes );
  handleManager.reset();
  init();
}

/*
 *
 */
PRInt32
SerialDataOutputStream::numBytesWritten() const
{
  CHECK_OBJECT_VALIDITY();

  return streamLoc;
}


/**
 * Returns a pointer to the array of bytes.  Efficiency is
 * emphasized instead of safety.  The caller should not alter what
 * is returned.
 *
 * @return a pointer to the array of bytes that represents this stream.
 * @see numBytesWritten 
 */
const PRUint8 * 
SerialDataOutputStream::getStreamBytes() const
{
  CHECK_OBJECT_VALIDITY();

  return streamBytes;
}

/*
 *
 */
iMQError 
SerialDataOutputStream::exportStreamBytes(PRUint8 ** const bytes, PRInt32 * const bytesSize)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( bytes );
  RETURN_ERROR_IF_NULL( bytesSize );

  *bytes = streamBytes;
  *bytesSize = streamLoc;

  // Reset the parameters
  streamBytes = NULL; // caller is responsible for freeing
  reset();

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
SerialDataOutputStream::writeHashtable(
                          BasicTypeHashtable * const hashtable)
{
  CHECK_OBJECT_VALIDITY();

  // Don't write anything if hashtable is NULL
  if (hashtable == NULL) {
    return IMQ_SUCCESS;
  }

  // Write the magic number, version, and Hashtable class description
  RETURN_IF_ERROR( writeBytes(SERIALIZE_HASHTABLE_PREFIX, 
                              sizeof(SERIALIZE_HASHTABLE_PREFIX)) );

  // Allocate a new handle for the Hashtable description.  
  RETURN_IF_ERROR( handleManager.setNextHandleToClass(HASHTABLE_TYPE) );
  
  // Allocate a new handle for the Hashtable object that we are about
  // to write.  This handle will never be referenced so we just store
  // NULL.
  RETURN_IF_ERROR( handleManager.setNextHandleToObject(NULL) );
  
  // Write the hashTable data
  RETURN_IF_ERROR( writeHashtableData(hashtable) );
  
  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
SerialDataOutputStream::writeHashtableData(
                          BasicTypeHashtable * const hashtable)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( hashtable );

  // These four fields are the only Hashtable fields besides
  // the <key,value> pairs that are serialized
  PRFloat32 loadFactor = 0.0;
  PRInt32   threshold  = 0;
  PRInt32   capacity   = 0;
  PRInt32   numKeys    = 0;

  // Get values for each of the four fields
  RETURN_IF_ERROR( hashtable->getLoadFactor(&loadFactor) );
  RETURN_IF_ERROR( hashtable->getThreshold(&threshold) );
  RETURN_IF_ERROR( hashtable->getCapacity(&capacity) );
  RETURN_IF_ERROR( hashtable->getNumKeys((PRUint32*)&numKeys) );
  
  // Write the loadFactor and threshold
  RETURN_IF_ERROR( writeFloat32(loadFactor) );
  RETURN_IF_ERROR( writeInt32(threshold) );

  // Write TC_BLOCKDATA followed by the total size of the next two fields
  RETURN_IF_ERROR( writeBytes(TC_BLOCKDATA_BYTES, sizeof(TC_BLOCKDATA_BYTES)) );
  RETURN_IF_ERROR( writeBytes(HASHTABLE_BLOCKDATA_SIZE_BYTES, 
                              sizeof(HASHTABLE_BLOCKDATA_SIZE_BYTES)) );  
  
  // Write the capacity and the number of keys
  RETURN_IF_ERROR( writeInt32(capacity) );
  RETURN_IF_ERROR( writeInt32(numKeys) );

  //
  // Write each key and value
  //
  RETURN_IF_ERROR( hashtable->keyIterationStart() );
  PRInt32 numElements = 0;
  while (hashtable->keyIterationHasNext()) {
    const BasicType * key;
    const BasicType * value;
    key   = NULL;
    value = NULL;

    // Get the next key and value
    RETURN_IF_UNEXPECTED_ERROR( hashtable->keyIterationGetNext(&key) );


    RETURN_IF_UNEXPECTED_ERROR( hashtable->getValueFromKey(key, (const Object**)&value) ); // error here
    ASSERT( key != NULL );
    ASSERT( value != NULL );
    RETURN_IF_ERROR( writeSerializedBasicObject(key) );
    RETURN_IF_ERROR( writeSerializedBasicObject(value) );

    // Count to make sure that we wrote the right number of elements
    numElements++;  
  }
  RETURN_UNEXPECTED_ERROR_IF( numElements != numKeys, 
                              IMQ_SERIALIZE_CORRUPTED_HASHTABLE );

  // Write TC_ENDBLOCKDATA
  RETURN_IF_ERROR( writeBytes(TC_ENDBLOCKDATA_BYTES, 
                              sizeof(TC_ENDBLOCKDATA_BYTES)) );

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
SerialDataOutputStream::writeSerializedBasicObject(const BasicType * const object)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( object );

  // Check if we have already written this object.  If so, then we
  // just write a reference to it.
  PRUint32 handle;
  RETURN_IF_ERROR( handleManager.getHandleFromBasicType(object, &handle) );
  if (handle != SERIAL_HANDLE_MANAGER_INVALID_HANDLE) {
    return writeReference(handle);
  }
  
  // Otherwise we have to write a new object
  return writeSerializedNewBasicObject(object);
}


/*
 *
 */
iMQError 
SerialDataOutputStream::writeSerializedNewBasicObject(const BasicType * const object)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( object );

  // Write the type of the object (TC_OBJECT, TC_STRING, TC_LONGSTRING)
  // And for TC_OBJECT write the class description.
  TypeEnum classType = object->getType();
  switch (classType) {
  case UTF8_STRING_TYPE:
    RETURN_IF_ERROR( writeByte(SERIALIZE_TC_STRING) );
    break;

  case UTF8_LONG_STRING_TYPE:
    RETURN_IF_ERROR( writeByte(SERIALIZE_TC_LONGSTRING) );
    break;

  case BOOLEAN_TYPE:
  case BYTE_TYPE:
  case SHORT_TYPE:
  case INTEGER_TYPE:
  case LONG_TYPE:
  case FLOAT_TYPE:
  case DOUBLE_TYPE:
    RETURN_IF_ERROR( writeByte(SERIALIZE_TC_OBJECT) );
    RETURN_IF_ERROR( writeClassDesc(classType) );
    break;
      
  default:
    return IMQ_SERIALIZE_UNRECOGNIZED_CLASS;
  }
  
  // Set the next handle to a clone of thie object.  We use a clone
  // because the handleManager will free the object when it is
  // destructed.
  RETURN_IF_ERROR( handleManager.setNextHandleToObject(object->clone()) );

  // Now write the object
  RETURN_IF_ERROR( object->write(this) );
  
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
SerialDataOutputStream::writeClassDesc(const TypeEnum classType)
{
  CHECK_OBJECT_VALIDITY();

  // If classType is NULL_TYPE, then we only write TC_NULL.  classType
  // will be NULL_TYPE for classes that do not have a superclass.
  if (classType == NULL_TYPE) {
    return writeByte(SERIALIZE_TC_NULL);
  }

  // it must be a type that we can write a class description for
  RETURN_ERROR_IF( FULL_CLASS_DESC_BY_TYPE[classType] == NULL,
                   IMQ_SERIALIZE_NO_CLASS_DESC );

  // Check if we have already written this class description.  If so
  // then we just write a reference to it.
  PRUint32 handle;
  RETURN_IF_ERROR( handleManager.getHandleFromClass(classType, &handle) );
  if (handle != SERIAL_HANDLE_MANAGER_INVALID_HANDLE) {
    return writeReference(handle);
  }
  
  // This new class description gets the next handle.
  RETURN_IF_ERROR( handleManager.setNextHandleToClass(classType) );

  // It's a new class description, so write out the canned
  // description.
  RETURN_IF_ERROR( writeByte(SERIALIZE_TC_CLASSDESC) );
  RETURN_IF_ERROR( writeBytes(FULL_CLASS_DESC_BY_TYPE[classType],
                              FULL_CLASS_DESC_SIZE_BY_TYPE[classType]) );

  // Now write the superclass.
  RETURN_IF_ERROR( writeClassDesc(SUPER_CLASS_BY_TYPE[classType]) );

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
SerialDataOutputStream::writeReference(const PRUint32 handle)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_IF_ERROR( writeByte(SERIALIZE_TC_REFERENCE) );
  RETURN_IF_ERROR( writeUint32(handle) );
  
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
SerialDataOutputStream::increaseBufferIfNeeded()
{
  CHECK_OBJECT_VALIDITY();

  // If streamLoc >= streamLength, then the buffer is full
  if (streamLoc >= streamLength) {
    ASSERT( streamLoc == streamLength );
    RETURN_IF_ERROR( increaseBuffer() );
  }
  ASSERT( streamLoc < streamLength );

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
SerialDataOutputStream::increaseBuffer()
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( streamLoc == streamLength );

  // allocate a bigger buffer
  PRInt32   newStreamLength = MAX( SERIAL_DATA_OUTPUT_STREAM_INITIAL_STREAM_SIZE, 
	                               streamLength * 2 );
  PRUint8 * newStreamBytes = new PRUint8[newStreamLength];
  RETURN_IF_OUT_OF_MEMORY( newStreamBytes );

  // copy the old stream into the new stream
  memcpy(newStreamBytes, streamBytes, streamLength * sizeof(PRUint8));

  // delete the old stream
  DELETE_ARR( streamBytes );

  // update the member variables
  streamBytes = newStreamBytes;
  streamLength = newStreamLength;
  
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
SerialDataOutputStream::writeBytes(const PRUint8 * const bytesToWrite,
                                   const PRInt32 bytesToWriteLength)
{
  CHECK_OBJECT_VALIDITY();

  return writeUint8Array(bytesToWrite, bytesToWriteLength);
}

/*
 *
 */
iMQError
SerialDataOutputStream::writeByte(const PRUint8 value)
{
  CHECK_OBJECT_VALIDITY();

  return writeUint8(value);
}


/*
 *
 */
iMQError
SerialDataOutputStream::writeUint8(const PRUint8 value)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_IF_ERROR( increaseBufferIfNeeded() );

  // Assign value to the next element of the output array, and
  // increment the location
  streamBytes[streamLoc] = value;
  streamLoc++;

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
SerialDataOutputStream::writeUint16(const PRUint16 value)
{
  CHECK_OBJECT_VALIDITY();

  // convert value to be in network byte order
  PRUint16 netOrderValue = PR_htons(value);
  RETURN_IF_ERROR( writeUint8Array((PRUint8*)&netOrderValue, sizeof(value)) );
  
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
SerialDataOutputStream::writeUint32(const PRUint32 value)
{
  CHECK_OBJECT_VALIDITY();

  // convert value to be in network byte order
  PRUint32 netOrderValue = PR_htonl(value);
  RETURN_IF_ERROR( writeUint8Array((PRUint8*)&netOrderValue, sizeof(value)) );

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
SerialDataOutputStream::writeUint64(const PRUint64 value)
{
  CHECK_OBJECT_VALIDITY();

  // split value into high and low 32-bit parts
  PRUint32 hi, lo;
  LL_HiLoFromULL(&hi, &lo, value);

  // write out each 32-bit part
  RETURN_IF_ERROR( writeUint32(hi) );
  RETURN_IF_ERROR( writeUint32(lo) );

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
SerialDataOutputStream::writeToFile(FILE * out)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );

  size_t bytesWritten;
  bytesWritten = fwrite(streamBytes, sizeof(PRUint8), streamLoc, out);
  RETURN_ERROR_IF( (PRInt32)bytesWritten != streamLoc, IMQ_FILE_OUTPUT_ERROR );
   
  return IMQ_SUCCESS;
}
