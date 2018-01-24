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
 * @(#)SerialDataInputStream.cpp	1.3 06/26/07
 */ 

#include "../debug/DebugUtils.h" // must be first include in the file


#include "SerialDataInputStream.hpp"
#include "../util/PRTypesUtils.h"
#include "../serial/Serialize.hpp"
#include "../util/UtilityMacros.h"
#include "../basictypes/AllBasicTypes.hpp"
#include "../containers/BasicTypeHashtable.hpp"

#include <memory.h>  // for memcpy
#include <stdio.h>
#include <nspr.h>


/* 
 * See the header file SerialDataInputStream.hpp for a description of
 * what each of these methods does.  
 */




// OPTIMIZATION:  instead of copying the entire stream into
// streamBytes we could just store a pointer to it.

/*
 *
 */
SerialDataInputStream::SerialDataInputStream()
{
  CHECK_OBJECT_VALIDITY();

  init();
}


/*
 *
 */
SerialDataInputStream::~SerialDataInputStream()
{
  CHECK_OBJECT_VALIDITY();

  reset();
}


/*
 *
 */
void
SerialDataInputStream::init()
{
  CHECK_OBJECT_VALIDITY();

  isValid = false;
  streamBytes = NULL;
  streamLength = 0;
  streamLoc = 0;
}


/*
 *
 */
void
SerialDataInputStream::reset()
{
  CHECK_OBJECT_VALIDITY();

  //
  // Free the stream if it was allocated
  //
  DELETE_ARR( streamBytes );

  //
  // Reset the handle manager
  //
  handleManager.reset();

  init();
}

/*
 *
 */
iMQError 
SerialDataInputStream::setNetOrderStream(const PRUint8 stream[], 
                                         const PRUint32 streamSize)
{
  CHECK_OBJECT_VALIDITY();

  //
  // Validate the input
  //
  RETURN_ERROR_IF_NULL( stream );

  reset();
  ASSERT( streamBytes == NULL );

  //
  // Allocate the local copy of the stream
  //
  streamBytes = new PRUint8[streamSize];
  if (streamBytes == NULL) {
    reset();
    return IMQ_OUT_OF_MEMORY;
  }

  //
  // Copy the stream into the local stream buffer
  //
  memcpy(streamBytes, stream, streamSize);

  //
  // Store the length of the stream, and reset the reading location
  // to the beginning of the stream.
  //
  streamLength = streamSize;
  streamLoc = 0;
  isValid = PR_TRUE;

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError 
SerialDataInputStream::readUint8(PRUint8 * const value)
{
  CHECK_OBJECT_VALIDITY();

  // Validate the input
  RETURN_ERROR_IF_NULL( value );
  *value = 0;
  
  // Make sure it's okay to read the next byte
  if (!isValid) {
    RETURN_UNEXPECTED_ERROR( IMQ_UNINITIALIZED_STREAM );
  }
  if (endOfStream()) {
    return IMQ_END_OF_STREAM;
  }

  // Read the byte
  ASSERT( streamBytes != NULL );
  *value = streamBytes[streamLoc];
  streamLoc++;


  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
SerialDataInputStream::readUint16(PRUint16 * const value)
{
  CHECK_OBJECT_VALIDITY();

  // Validate the input
  RETURN_ERROR_IF_NULL( value );
  *value = 0;

  // Read in the next 2 bytes
  PRUint16 netOrderShort;
  RETURN_IF_ERROR( readUint8Array((PRUint8*)&netOrderShort, sizeof(PRUint16)) );

  // Convert the net order short to a host order short
  *value = PR_ntohs(netOrderShort);
  
  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
SerialDataInputStream::readUint32(PRUint32 * const value)
{
  CHECK_OBJECT_VALIDITY();

  // Validate the input
  RETURN_ERROR_IF_NULL( value );
  *value = 0;

  // Read in the next 4 bytes
  PRUint32 netOrderInt;
  RETURN_IF_ERROR( readUint8Array((PRUint8*)&netOrderInt, sizeof(PRUint32)) );

  // Convert the net order int to a host order int
  *value = PR_ntohl(netOrderInt);
  
  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError 
SerialDataInputStream::readUint64(PRUint64 * const value)
{
  CHECK_OBJECT_VALIDITY();

  // Validate the input
  RETURN_ERROR_IF_NULL( value );

  PRUint32 hi, lo;
  
  RETURN_IF_ERROR( readUint32(&hi) );
  RETURN_IF_ERROR( readUint32(&lo) );

  *value = LL_ULLFromHiLo(hi, lo);

  return IMQ_SUCCESS;
}


/*
 *
 */
PRBool 
SerialDataInputStream::endOfStream() const
{
  CHECK_OBJECT_VALIDITY();

  if (streamLoc < streamLength) {
    return PR_FALSE;
  }

  ASSERT( streamLoc == streamLength );  // we should never go past the end
  return PR_TRUE;
}



// --------------------------------------------------------------------
//
// Serialization functions
//
// --------------------------------------------------------------------

/*
 *
 */
iMQError
SerialDataInputStream::readHashtable(BasicTypeHashtable * const hashtable)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( hashtable );

  ASSERT( isValid );
  ASSERT( streamBytes != NULL );

  // Read the magic number and the version.  Make sure they match
  // what we expect.
  PRUint16 magic, version;
  RETURN_IF_ERROR( readUint16(&magic) );
  RETURN_IF_ERROR( readUint16(&version) );
  RETURN_ERROR_IF( magic != SERIALIZE_STREAM_MAGIC, 
                   IMQ_SERIALIZE_BAD_MAGIC_NUMBER );
  RETURN_ERROR_IF( version != SERIALIZE_STREAM_VERSION, 
                   IMQ_SERIALIZE_BAD_VERSION );

  // Now we should get the TC_OBJECT_BYTES character
  RETURN_IF_ERROR( consumeExpectedBytes(TC_OBJECT_BYTES, 
                                        sizeof(TC_OBJECT_BYTES)) );
  
  // Consume the hash table class description
  TypeEnum classType;
  RETURN_IF_ERROR( readClassDesc(&classType) );
  RETURN_ERROR_IF( classType != HASHTABLE_TYPE, IMQ_SERIALIZE_NOT_HASHTABLE );

  // allocate a handle for this hash table
  // use NULL because we will never need to reference the 
  // hashtable
  RETURN_IF_ERROR( handleManager.setNextHandleToObject(NULL) );

  // now read in the hashtable data
  RETURN_IF_ERROR( readHashtableData(hashtable) );

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
SerialDataInputStream::readClassDesc(TypeEnum * const classType)
{
  CHECK_OBJECT_VALIDITY();

  //
  // Validate the input
  //
  RETURN_ERROR_IF_NULL( classType );
  *classType = UNKNOWN_TYPE;

  RETURN_ERROR_IF( !isValid, IMQ_UNINITIALIZED_STREAM );

  //
  // If the class is new, then we will have to read in the class
  // description.  Otherwise it will be a reference to a class 
  // description that we will have to look up.
  //
  PRUint8 classEncoding;
  RETURN_IF_ERROR( readUint8(&classEncoding) );  

  switch (classEncoding) {

  case SERIALIZE_TC_CLASSDESC:
    return readNewClassDesc(classType);

  case SERIALIZE_TC_REFERENCE:
    return readReferenceClassDesc(classType);

  case SERIALIZE_TC_NULL:
    *classType = NULL_TYPE;
    return IMQ_SUCCESS;

  default:
    return IMQ_SERIALIZE_NOT_CLASS_DEF;
  };
}


/*
 *
 */
iMQError 
SerialDataInputStream::readNewClassDesc(TypeEnum * const classType)
{
  CHECK_OBJECT_VALIDITY();

  // To be portable one must declare classes at the start of the
  // function.
  UTF8String className;
  PRUint64 serialID;

  RETURN_ERROR_IF_NULL( classType );
  *classType = UNKNOWN_TYPE;

  //
  // Read in the class name and serialization id
  //
  RETURN_IF_ERROR( className.read(this) );
  RETURN_IF_ERROR( readUint64(&serialID) );

  //
  // Get the type of the class based on the serial id
  //
  TypeEnum typeOfClass;
  RETURN_IF_ERROR( Serialize::serialIDToType(serialID, &typeOfClass) );

  // assign the next handle to this class
  RETURN_IF_ERROR( handleManager.setNextHandleToClass(typeOfClass) );
  
  // read in the class description, which we ignore because we know what
  // it will be based on the serial id of the class
  const PRUint8 * classDesc;
  PRUint32 classDescLen;
  RETURN_IF_ERROR( Serialize::classTypeToClassDescBytes(typeOfClass, 
                                                        &classDesc, 
                                                        &classDescLen) );
  RETURN_IF_ERROR( consumeExpectedBytes(classDesc, classDescLen) );

  // now read in the super class (which might be null)
  // and make sure that it's what we expect based on the subclass
  TypeEnum superClassType;
  RETURN_IF_ERROR( readClassDesc(&superClassType) );
  RETURN_ERROR_IF( (superClassType != NULL_TYPE) &&
                     (superClassType != NUMBER_TYPE), 
                   IMQ_SERIALIZE_BAD_SUPER_CLASS );
  
  RETURN_ERROR_IF( SUPER_CLASS_BY_TYPE[typeOfClass] != superClassType,
                   IMQ_SERIALIZE_BAD_SUPER_CLASS );
  
  *classType = typeOfClass;

  return IMQ_SUCCESS;
}



/*
 * Read the next numExpectedBytes from the stream.  If they differ
 * from the bytes in expectedBytes then return IMQ_SERIALIZE_UNEXPECTED_BYTES.
 */
// OPTIMIZATION: reading all of the bytes and then doing the 
// comparison would be faster.
iMQError 
SerialDataInputStream::consumeExpectedBytes(const PRUint8 expectedBytes[], 
                                            const PRUint32 numExpectedBytes)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( expectedBytes );  

  for (PRUint32 i = 0; i < numExpectedBytes; i++) {
    PRUint8 value;
    RETURN_IF_ERROR( readUint8(&value) );
    if (value != expectedBytes[i]) {
      return IMQ_SERIALIZE_UNEXPECTED_BYTES;
    }
  }

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError
SerialDataInputStream::readHashtableData(BasicTypeHashtable * const hashtable)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  BasicType * keyObject = NULL;
  BasicType * valueObject = NULL;
  PRInt32 capacity = 0;  
  PRInt32 numEntries = 0;
  PRInt32 entryIndex = 0;
  PRFloat32 loadFactor = 0.0;
  PRInt32 threshold = 0;

  NULLCHK( hashtable ); 
  hashtable->reset();

  // Read in the loadFactor and threshold
  ERRCHK( readFloat32(&loadFactor) );
  ERRCHK( readInt32(&threshold) );
  hashtable->setLoadFactor(loadFactor);
  hashtable->setThreshold(threshold);
  
  // Skip over TC_BLOCKDATA and the size of the block data
  ERRCHK( consumeExpectedBytes(TC_BLOCKDATA_BYTES, 
                               sizeof(TC_BLOCKDATA_BYTES)) );  
  ERRCHK( consumeExpectedBytes(HASHTABLE_BLOCKDATA_SIZE_BYTES, 
                               sizeof(HASHTABLE_BLOCKDATA_SIZE_BYTES)) );

  // Read in the size of the hash and the number of elements
  ERRCHK( readInt32(&capacity) );
  ERRCHK( readInt32(&numEntries) );
  hashtable->setCapacity(capacity);

  // now read in each key and value
  for (entryIndex = 0; entryIndex < numEntries; entryIndex++) {
    // read in the key
    ERRCHK( readSerializedBasicObject(&keyObject) );

    // read in the value
    // we don't call RETURN_IF_ERROR here because we are obligated
    // to free keyObject
    ERRCHK( readSerializedBasicObject(&valueObject) );

    // add the key/value to the hash
    ERRCHK( hashtable->addEntry(keyObject, valueObject) );

    // The hashtable owns these now
    keyObject = NULL;
    valueObject = NULL;
  }

  // TC_ENDBLOCKDATA should be the last byte of the serialized hashtable
  RETURN_IF_ERROR( consumeExpectedBytes(TC_ENDBLOCKDATA_BYTES, 
                                        sizeof(TC_ENDBLOCKDATA_BYTES)) );

  return IMQ_SUCCESS;
Cleanup:
  DELETE( keyObject );
  DELETE( valueObject );
  return errorCode;
}

/*
 *
 */
iMQError
SerialDataInputStream::readReferenceClassDesc(TypeEnum * const classType)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( classType );  
  *classType = UNKNOWN_TYPE;

  // read in the handle for this class 
  PRUint32 classHandleID;
  RETURN_IF_ERROR( readUint32(&classHandleID) );

  // look-up the type of this class based on the handle
  RETURN_IF_ERROR( handleManager.getClassFromHandle(classHandleID, classType) );

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
SerialDataInputStream::readReferenceObject(BasicType ** const object)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( object );  
  *object = NULL;
  
  // read in the reference handle
  PRUint32 objectHandleID = 0;
  RETURN_IF_ERROR( readUint32(&objectHandleID) );

  // look-up the reference handle
  RETURN_IF_ERROR( handleManager.getObjectCloneFromHandle(objectHandleID, object) );

  return IMQ_SUCCESS;
}



/*
 * The caller is responsible for freeing *valueObject.
 */
iMQError
SerialDataInputStream::readSerializedBasicObject(BasicType ** const object)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  BasicType * newObject = NULL;
  PRBool addedToHandleManager = PR_FALSE;

  NULLCHK( object );
  *object = NULL;

  // read in the type of the object
  TypeEnum classType;
  PRUint8 objectType;
  ERRCHK( readUint8(&objectType) );

  // read in the class description
  switch (objectType) {

  // if we've seen this object before just return it
  case SERIALIZE_TC_REFERENCE:
    return readReferenceObject(object);
    break;

  // strings don't have a class description
  case SERIALIZE_TC_STRING:
    classType = UTF8_STRING_TYPE;
    break;
  case SERIALIZE_TC_LONGSTRING:
    classType = UTF8_LONG_STRING_TYPE;
    break;

  // if it's an object, we have to read in it's type
  case SERIALIZE_TC_OBJECT:
    ERRCHK( readClassDesc(&classType) );
    break;
  
  default:
    return IMQ_SERIALIZE_UNEXPECTED_BYTES;
  };

  // allocate a class of the specified type
  ERRCHK( Serialize::createObject(classType, &newObject) );

  // set the next handle to this object
  addedToHandleManager = PR_TRUE; // don't delete newObject if there is an error
  ERRCHK( handleManager.setNextHandleToObject(newObject) );
    
  // read in the object
  ERRCHK( newObject->read(this) );

  // The handle manager owns the original object, so here we just make a clone
  MEMCHK( *object = newObject->clone() );

  return IMQ_SUCCESS;
Cleanup:
  if (!addedToHandleManager) {
    DELETE( newObject );
  }
  return errorCode;
}

/*
 *
 */
iMQError 
SerialDataInputStream::test(const char * inputTestFile, const PRBool doExhaustiveCorruptionTest)
{
  iMQError errorCode = IMQ_SUCCESS;

  SerialDataInputStream inStream;
  BasicTypeHashtable hashtable;
  PRUint8 buffer[100000];
  FILE * in = NULL;
  PRInt32 size = 0;

  NULLCHK( inputTestFile );

  // Read in the input file
  in = fopen(inputTestFile, "rb");
  CNDCHK( in == NULL, IMQ_SERIALIZE_TEST_ERROR );
  size = (PRInt32)fread(buffer, 1, sizeof(buffer), in);
  fclose(in);

  if (doExhaustiveCorruptionTest) {
    // Go through each byte of the serialized hashtable
    for (int byteIndex = 0; byteIndex < size; byteIndex++) {
      char prevValue = buffer[byteIndex];
      
      // Set the current byte to each possible value
      for (int newValue = 0; newValue <= 255; newValue++) {
        buffer[byteIndex] = (char)newValue;

        // read in the corrupted hashtable that results
        ERRCHK( inStream.setNetOrderStream(buffer, (PRUint32)size) );
        errorCode = inStream.readHashtable(&hashtable);
      }
      buffer[byteIndex] = prevValue;
    }
  } else {
    // read in the corrupted hashtable that results
    ERRCHK( inStream.setNetOrderStream(buffer, (PRUint32)size) );
    ERRCHK( inStream.readHashtable(&hashtable) );
  }
  return IMQ_SUCCESS;
Cleanup:
  if (in != NULL) {
    fclose(in);
  }
  return errorCode;
}








