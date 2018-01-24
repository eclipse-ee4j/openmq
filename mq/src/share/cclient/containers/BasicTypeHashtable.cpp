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
 * @(#)BasicTypeHashtable.cpp	1.6 06/26/07
 */ 

#include "BasicTypeHashtable.hpp"
#include "../basictypes/AllBasicTypes.hpp"
#include "../debug/DebugUtils.h"
#include "../util/UtilityMacros.h"
#include <float.h>
#include <limits.h>

// HASHTABLE_SERIALIZE_LOADFACTOR is the default load factor to
// output.  getLoadFactor, getThreshold, and getCapacity depend on
// this being 1.0.  This should be a member of SerialHashtable, but
// only const static integer values can be initialized in the class
// declaration.
static const PRFloat32 HASHTABLE_SERIALIZE_LOADFACTOR = 1.0;


#if !defined(WIN32) 
extern "C" {
#endif // !defined(WIN32) 
  static PRintn PR_CALLBACK basicTypeComparator(const void * v1, const void * v2);
  static PLHashNumber PR_CALLBACK basicTypeHashFunc(const void * key);
  
  // (De-)Allocation functions
  static void * PR_CALLBACK allocTableOp(void * pool, PRSize size);
  static void PR_CALLBACK freeTableOp(void * pool, void * item);
  static PLHashEntry * PR_CALLBACK allocEntryOp(void * pool, const void *key);
  static void PR_CALLBACK freeEntryOp(void *pool, 
                                      PLHashEntry *hashEntry, 
                                      PRUintn flag);
  
  // Callback to enumerate the keys in the Hash
  static PRIntn PR_CALLBACK keyEnumerator(PLHashEntry *hashEntry, 
                                          PRIntn index, 
                                          void *arg);
#if !defined(WIN32) 
}
#endif // !defined(WIN32) 

/*
 *
 */
BasicTypeHashtable::BasicTypeHashtable() : keys(PR_FALSE)
{
  CHECK_OBJECT_VALIDITY();

  init(PR_TRUE, PR_TRUE);
}

/*
 *
 */
BasicTypeHashtable::BasicTypeHashtable(const PRBool autoDeleteKeysArg, 
                                       const PRBool autoDeleteValuesArg)
  : keys(PR_FALSE)
{
  CHECK_OBJECT_VALIDITY();

  init(autoDeleteKeysArg, autoDeleteValuesArg);
}

/*
 *
 */
void
BasicTypeHashtable::init(const PRBool autoDeleteKeysArg, 
                         const PRBool autoDeleteValuesArg)
{
  CHECK_OBJECT_VALIDITY();

  this->autoDeleteKeys      = autoDeleteKeysArg;
  this->autoDeleteValues    = autoDeleteValuesArg;
  this->numEntries          = 0;
  this->iteratorIndex       = 0;

  this->allocOps.allocTable = allocTableOp;
  this->allocOps.freeTable  = freeTableOp;  
  this->allocOps.allocEntry = allocEntryOp;
  this->allocOps.freeEntry  = freeEntryOp;

  this->plhash = PL_NewHashTable(0,                       // default number of buckets
                                 basicTypeHashFunc,
                                 basicTypeComparator, 
                                 basicTypeComparator,
                                 &(this->allocOps),
                                 // so freeEntryOp can access autoDelete[Keys|Values]
                                 this                 
                                 );

  this->hashtableStr = NULL;
}

/*
 *
 */
BasicTypeHashtable::~BasicTypeHashtable()
{
  CHECK_OBJECT_VALIDITY();

  if (plhash != NULL) {
    PL_HashTableDestroy(plhash);
  }
  DELETE_ARR( hashtableStr );
}

/*
 *
 */
void
BasicTypeHashtable::reset()
{
  CHECK_OBJECT_VALIDITY();

  if (plhash != NULL) {
    PL_HashTableDestroy(plhash);
  }
  keys.reset();
  init(this->autoDeleteKeys, this->autoDeleteValues);
}




/*
 * If this method is successful, Hashtable is responsible for freeing
 * keyObject and valueObject.  Otherwise, the caller is responsible
 * for freeing keyObject and valueObject.
 */
iMQError
BasicTypeHashtable::addEntry(BasicType * const key, 
                             Object    * const value)
{
  CHECK_OBJECT_VALIDITY();
  
  RETURN_ERROR_IF_NULL( key );
  RETURN_ERROR_IF( this->plhash == NULL, IMQ_HASH_TABLE_ALLOCATION_FAILED );
  ASSERT( key != NULL );

  // Stop the iteration if there is one
  keyIterationStop();

  // If the entry already exists, then it's an error
  Object * currentValue = NULL;
  currentValue = (Object*)PL_HashTableLookup(this->plhash, (void*)key);
  RETURN_ERROR_IF( currentValue != NULL, IMQ_HASH_VALUE_ALREADY_EXISTS );

  // Add entry's key to the list of keys.
  RETURN_IF_ERROR( keys.add(key) );

  // Add the element to the hash
  PLHashEntry * entry = NULL;
  entry = PL_HashTableAdd(this->plhash, key, value);
  if (entry == NULL) {
    keys.removeBasicType(key);
    return IMQ_OUT_OF_MEMORY;
  }

  numEntries++;

#ifndef NDEBUG
  Object * valueBack = NULL;
  // Test that we were able to add it
  valueBack = (Object*)PL_HashTableLookup(this->plhash, (void*)key);
  ASSERT( value == valueBack );
#endif

  return IMQ_SUCCESS;
} 


/*
 *
 */
iMQError  
BasicTypeHashtable::removeEntry(const BasicType * const key)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->plhash == NULL, IMQ_HASH_TABLE_ALLOCATION_FAILED );
  ASSERT( key != NULL );

  // ReadQTable depends on this returning an error if key is not found
  if (PL_HashTableLookup(plhash, (void*)key) == NULL) {
    return IMQ_NOT_FOUND;
  }

  // Remove the key from the list of keys
  RETURN_IF_ERROR( keys.removeBasicType(key) );

  PRBool success = PL_HashTableRemove(this->plhash, key);
  if (!success) {
    return IMQ_NOT_FOUND;
  }

  // Decrement the number of entries
  this->numEntries--;

  return IMQ_SUCCESS;
}


// getValueFromKey returns the value associated with key in the
// output parameter value.  It returns IMQ_NOT_FOUND if key is not
// located in the hashtable.  It returns an error if key or value is
// NULL.
iMQError  
BasicTypeHashtable::getValueFromKey(const BasicType * const key, 
                                    const Object **   const value) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( key );
  RETURN_ERROR_IF_NULL( value );
  RETURN_ERROR_IF( this->plhash == NULL, IMQ_HASH_TABLE_ALLOCATION_FAILED );

  *value = NULL;
  
  // Lookup the value
  *value = (Object*)PL_HashTableLookup(plhash, (void*)key);
  
  if (*value == NULL) {
    return IMQ_NOT_FOUND;
  }

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError
BasicTypeHashtable::getNumKeys(PRUint32 * const numKeys) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( numKeys );
  RETURN_ERROR_IF( this->plhash == NULL, IMQ_HASH_TABLE_ALLOCATION_FAILED );

  *numKeys = numEntries;

  return IMQ_SUCCESS;
}


/*
 *
 */
const char *
BasicTypeHashtable::toString(const char * const linePrefix)
{
  CHECK_OBJECT_VALIDITY();

  if ((linePrefix == NULL) || (this->plhash == NULL)) {
    return "<HASHTABLE>";
  }
  // Delete the old string, we could be smart and modify add and remove to do this
  // whenever the hashtable changes.
  DELETE_ARR(hashtableStr);
  hashtableStr = new char[HASHTABLE_MAX_STR_SIZE];
  if (hashtableStr == NULL) {
    return "<HASHTABLE>";
  }
  STRCPY(hashtableStr, ""); 

  if (this->keyIterationStart() != IMQ_SUCCESS) {
    return hashtableStr;
  }
  while (this->keyIterationHasNext()) {
    BasicType * name  = NULL;
    BasicType * value = NULL;
    this->keyIterationGetNext((const BasicType**)&name);
    this->getValueFromKey(name, (const Object**const)&value);
    if ((name == NULL) || (value == NULL)) {
      continue;
    }
    
    // 
    STRNCAT(this->hashtableStr, linePrefix,        HASHTABLE_MAX_STR_SIZE);
    STRNCAT(this->hashtableStr, name->toString(),  HASHTABLE_MAX_STR_SIZE);
    STRNCAT(this->hashtableStr, " -> ",            HASHTABLE_MAX_STR_SIZE);
    STRNCAT(this->hashtableStr, value->toString(), HASHTABLE_MAX_STR_SIZE);
    STRNCAT(this->hashtableStr, "\n",              HASHTABLE_MAX_STR_SIZE);
  }
  this->hashtableStr[HASHTABLE_MAX_STR_SIZE-1] = '\0'; // null terminate to be safe

  return hashtableStr;
}



/*
 *
 */
iMQError
BasicTypeHashtable::print(FILE * const out)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );

  fprintf(out, "%s", this->toString());

  return IMQ_SUCCESS;
}


/*
 *
 */
iMQError  
BasicTypeHashtable::keyIterationStart()
{
  CHECK_OBJECT_VALIDITY();
  RETURN_ERROR_IF( this->plhash == NULL, IMQ_HASH_TABLE_ALLOCATION_FAILED );

  // Stop the current iteration if there is one
  keyIterationStop();

  iteratorIndex = 0;
  
  return IMQ_SUCCESS;
}

/*
 *
 */
void
BasicTypeHashtable::keyIterationStop()
{
  CHECK_OBJECT_VALIDITY();

  iteratorIndex = numEntries;
}

/*
 *
 */
PRBool    
BasicTypeHashtable::keyIterationHasNext()
{
  CHECK_OBJECT_VALIDITY();

  return (iteratorIndex < numEntries);
}

/*
 *
 */
iMQError  
BasicTypeHashtable::keyIterationGetNext(const BasicType ** const key)
{
  CHECK_OBJECT_VALIDITY();
  RETURN_ERROR_IF( this->plhash == NULL, IMQ_HASH_TABLE_ALLOCATION_FAILED );

  if (!keyIterationHasNext()) {
    RETURN_UNEXPECTED_ERROR( IMQ_INVALID_ITERATOR );
  }
  
  iMQError errorCode = keys.get(iteratorIndex, (void**)key);
  iteratorIndex++;
  return errorCode;
}






/* -----------------------------------------------------------------------------
 * -----------------------------------------------------------------------------
 * These are only called  when (de-)serializing a Java Hashtable.
 *
 */


/*
 *
 */
void
BasicTypeHashtable::setLoadFactor(const PRFloat32 loadFactor)
{
  CHECK_OBJECT_VALIDITY();

  // CURRENTLY: don't do anything with the load factor
  UNIMPLEMENTED( "SerialHashTable::setLoadFactor" );
  UNUSED( loadFactor );
}


/*
 *
 */
void
BasicTypeHashtable::setThreshold(const PRInt32 threshold)
{
  CHECK_OBJECT_VALIDITY();

  // CURRENTLY: don't do anything with the threshold
  UNIMPLEMENTED( "SerialHashTable::setThreshold" );
  UNUSED( threshold );
}

/*
 *
 */
void
BasicTypeHashtable::setCapacity(const PRInt32 capacity)
{
  CHECK_OBJECT_VALIDITY();

  // CURRENTLY: don't do anything with the threshold
  UNIMPLEMENTED( "SerialHashTable::capacity" );
  UNUSED( capacity );
}

/*
 *
 */
iMQError  
BasicTypeHashtable::getLoadFactor(PRFloat32 * const loadFactor) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->plhash == NULL, IMQ_HASH_TABLE_ALLOCATION_FAILED );
  RETURN_ERROR_IF_NULL( loadFactor );

  *loadFactor = HASHTABLE_SERIALIZE_LOADFACTOR;
  
  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError  
BasicTypeHashtable::getThreshold(PRInt32 * const threshold) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->plhash == NULL, IMQ_HASH_TABLE_ALLOCATION_FAILED );

  RETURN_ERROR_IF_NULL( threshold );
  RETURN_IF_ERROR( getNumKeys((PRUint32*)&(*threshold)) );

  return IMQ_SUCCESS;
}

/*
 *
 */
iMQError  
BasicTypeHashtable::getCapacity(PRInt32 * const capacity) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->plhash == NULL, IMQ_HASH_TABLE_ALLOCATION_FAILED );

  RETURN_ERROR_IF_NULL( capacity );
  RETURN_IF_ERROR( getNumKeys((PRUint32*)&(*capacity)) );

  return IMQ_SUCCESS;
}

PRBool
BasicTypeHashtable::getAutoDeleteKeys() const
{
  CHECK_OBJECT_VALIDITY();

  return this->autoDeleteKeys;
}

PRBool
BasicTypeHashtable::getAutoDeleteValues() const
{
  CHECK_OBJECT_VALIDITY();

  return this->autoDeleteValues;
}


/*
 *
 */
BasicTypeHashtable::BasicTypeHashtable(const BasicTypeHashtable& hashtable)
  : keys(PR_FALSE)
{
  CHECK_OBJECT_VALIDITY();

  // Auto-delete both the keys and the values because they will be copies
  // of the original keys and values.
  init(PR_TRUE, PR_TRUE);

  iMQError errorCode = IMQ_SUCCESS;
  const BasicType * key = NULL;
  const Object * value = NULL;
  BasicType * newKey = NULL;
  Object * newValue = NULL;

  ERRCHK( ((BasicTypeHashtable&)hashtable).keyIterationStart() );
  while(((BasicTypeHashtable&)hashtable).keyIterationHasNext()) {
    ERRCHK( ((BasicTypeHashtable&)hashtable).keyIterationGetNext(&key) );
    ERRCHK( ((BasicTypeHashtable&)hashtable).getValueFromKey(key, &value) );
    
    // We currently only support cloning basic types
    CNDCHK( !value->getIsBasicType(), IMQ_OBJECT_NOT_CLONEABLE );

    // Clone the key and value
    MEMCHK( newKey = key->clone() );
    MEMCHK( newValue = ((BasicType*)value)->clone() );

    // Put them in the hashtable
    ERRCHK( this->addEntry(newKey, newValue) );
    
    newKey = NULL;
    newValue = NULL;
  }

  return;
Cleanup:
  DELETE( newKey );
  DELETE( newValue );
  
  return;
}


/*
 *
 */
iMQError
BasicTypeHashtable::test(const PRInt32 numIterations, const PRInt32 numKeyGroups)
{
  iMQError errorCode = IMQ_SUCCESS;

  BasicTypeHashtable hashtable;
  BasicType * addedKey   = NULL;
  BasicType * addedValue = NULL;
  char propname[1000];

  int i, j;
  for (j = 0; j < numIterations; j++) {
    // Populate the hashtable
    for (i = 0; i < numKeyGroups; i++) {
      sprintf(propname, "%s.%d", "true", i);   ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Boolean(PR_TRUE)) );
      sprintf(propname, "%s.%d", "false", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Boolean(PR_FALSE)) );

      sprintf(propname, "%s.%d", "zerobyte", i);    ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Byte(0x00)) );
      sprintf(propname, "%s.%d", "minposbyte", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Byte(0x01)) );
      sprintf(propname, "%s.%d", "maxposbyte", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Byte(SCHAR_MAX)) );
      sprintf(propname, "%s.%d", "minnegbyte", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Byte(-1)) );
      sprintf(propname, "%s.%d", "maxnegbyte", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Byte(SCHAR_MIN)) );
  
      sprintf(propname, "%s.%d", "zeroshort", i);    ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Short(0x0000)) );
      sprintf(propname, "%s.%d", "minposshort", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Short(0x0001)) );
      sprintf(propname, "%s.%d", "maxposshort", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Short(SHRT_MAX)) );
      sprintf(propname, "%s.%d", "minnegshort", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Short(-1)) );
      sprintf(propname, "%s.%d", "maxnegshort", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Short(SHRT_MIN)) );

      sprintf(propname, "%s.%d", "zerointeger", i);    ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Integer(0x00000000)) );
      sprintf(propname, "%s.%d", "minposinteger", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Integer(0x00000001)) );
      sprintf(propname, "%s.%d", "maxposinteger", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Integer(0x7FFFFFFF)) );
      sprintf(propname, "%s.%d", "minneginteger", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Integer(0xFFFFFFFF)) );
      sprintf(propname, "%s.%d", "maxneginteger", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Integer(0x80000000)) );

      sprintf(propname, "%s.%d", "zerolong", i);    ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Long(LL_ULLFromHiLo(0x00000000, 0x00000000))) );
      sprintf(propname, "%s.%d", "minposlong", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Long(LL_ULLFromHiLo(0x00000000, 0x00000001))) );
      sprintf(propname, "%s.%d", "maxposlong", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Long(LL_ULLFromHiLo(0x7FFFFFFF, 0xFFFFFFFF))) );
      sprintf(propname, "%s.%d", "minneglong", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Long(LL_ULLFromHiLo(0xFFFFFFFF, 0xFFFFFFFF))) );
      sprintf(propname, "%s.%d", "maxneglong", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Long(LL_ULLFromHiLo(0x80000000, 0x00000000))) );

      sprintf(propname, "%s.%d", "zerofloat", i);    ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Float(0)) );
      sprintf(propname, "%s.%d", "maxposfloat", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Float(FLT_MAX)) );
      sprintf(propname, "%s.%d", "maxnegfloat", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Float(FLT_MIN)) );

      sprintf(propname, "%s.%d", "zerodouble", i);    ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Double(0)) );
      sprintf(propname, "%s.%d", "maxposdouble", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Double(DBL_MAX)) );
      sprintf(propname, "%s.%d", "maxnegdouble", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new Double(DBL_MIN)) );

      sprintf(propname, "%s.%d", "emptystring", i);    ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new UTF8String("")) );
      sprintf(propname, "%s.%d", "longerstring", i);  ERRCHK( hashtable.addEntry(addedKey = new UTF8String(propname), addedValue = new UTF8String(propname)) );
    }
    addedKey = NULL;
    addedValue = NULL;

    // make sure element is there
    for (i = 0; i < numKeyGroups; i++) {
      const Object * value = NULL;
      UTF8String key;

      sprintf(propname, "%s.%d", "true", i); key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "false", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );

      sprintf(propname, "%s.%d", "zerobyte", i);    key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "minposbyte", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxposbyte", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "minnegbyte", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxnegbyte", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
  
      sprintf(propname, "%s.%d", "zeroshort", i);    key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "minposshort", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxposshort", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "minnegshort", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxnegshort", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );

      sprintf(propname, "%s.%d", "zerointeger", i);    key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "minposinteger", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxposinteger", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "minneginteger", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxneginteger", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );

      sprintf(propname, "%s.%d", "zerolong", i);    key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "minposlong", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxposlong", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "minneglong", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxneglong", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );

      sprintf(propname, "%s.%d", "zerofloat", i);    key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxposfloat", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxnegfloat", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );

      sprintf(propname, "%s.%d", "zerodouble", i);    key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxposdouble", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "maxnegdouble", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );

      sprintf(propname, "%s.%d", "emptystring", i);    key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
      sprintf(propname, "%s.%d", "longerstring", i);  key.setValue(propname); ERRCHK( hashtable.getValueFromKey(&key, &value) );
    }

    // delete every element
    for (i = 0; i < numKeyGroups; i++) {
      UTF8String key;

      sprintf(propname, "%s.%d", "true", i); key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "false", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );

      sprintf(propname, "%s.%d", "zerobyte", i);    key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "minposbyte", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxposbyte", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "minnegbyte", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxnegbyte", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
  
      sprintf(propname, "%s.%d", "zeroshort", i);    key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "minposshort", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxposshort", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "minnegshort", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxnegshort", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );

      sprintf(propname, "%s.%d", "zerointeger", i);    key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "minposinteger", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxposinteger", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "minneginteger", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxneginteger", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );

      sprintf(propname, "%s.%d", "zerolong", i);    key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "minposlong", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxposlong", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "minneglong", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxneglong", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );

      sprintf(propname, "%s.%d", "zerofloat", i);    key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxposfloat", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxnegfloat", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );

      sprintf(propname, "%s.%d", "zerodouble", i);    key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxposdouble", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "maxnegdouble", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );

      sprintf(propname, "%s.%d", "emptystring", i);    key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
      sprintf(propname, "%s.%d", "longerstring", i);  key.setValue(propname); ERRCHK( hashtable.removeEntry(&key) );
    }
  }  
  return IMQ_SUCCESS;
Cleanup:
  DELETE(addedKey);
  DELETE(addedValue);

  return errorCode;
}

/* -----------------------------------------------------------------------------
 * -----------------------------------------------------------------------------
 * These are the callbacks that NSPR's hash tables uses
 *
 */


/*
 * allocTableOp returns a pointer to a new allocation of size bytes
 */
static void * PR_CALLBACK allocTableOp(void * pool, PRSize size)
{
  UNUSED( pool ); 

  nextAllocSucceeds();
  return (void*)new PRUint8[size];
}



/*
 * freeTableOp frees item
 */
static void PR_CALLBACK freeTableOp(void * pool, void * item)
{
  UNUSED( pool );

#if defined(__linux__) || defined(LINUX)
  if (item != NULL) {
    delete[] ((PRUint8 *)item);
    item = NULL;
  }
#else
  DELETE_ARR( item );
#endif 
}



/*
 * allocEntryOp returns a pointer to a new PLHashEntry 
 */
static PLHashEntry * PR_CALLBACK allocEntryOp(void * pool, const void *key)
{
  UNUSED( pool );
  UNUSED( key );
  PLHashEntry * hashEntry = new PLHashEntry;

  return hashEntry;
}



/*
 * freeEntryOp frees hashEntry
 */
static void PR_CALLBACK freeEntryOp(void *pool, 
                                    PLHashEntry *hashEntry, 
                                    PRUintn flag)
{
  BasicTypeHashtable * hashtable = (BasicTypeHashtable*)pool;

  if (hashEntry == NULL) {
    return;
  }
  
  // we need to delete the key and the value.  Cast to BasicType* so that
  // the destructors are called
  if (hashtable->getAutoDeleteKeys()) {
    delete (BasicType*)hashEntry->key;
    hashEntry->key = NULL;
  }
  if (hashtable->getAutoDeleteValues()) {
    delete (Object*)hashEntry->value;
    hashEntry->value = NULL;
  }
  
  // if flag is HT_FREE_ENTRY, then we need to free the entry too
  if (flag == HT_FREE_ENTRY) {
    DELETE( hashEntry ); 
  }
}


/*
 * basicTypeComparator returns the two BasicTypes pointed to by v1 and
 * v2.  It returns a nonzero value if the two values are equal, and 0 if 
 * the two values are not equal. 
 */
static PRintn PR_CALLBACK basicTypeComparator(const void * v1, const void * v2)
{
  BasicType * basic1 = (BasicType*)v1;
  BasicType * basic2 = (BasicType*)v2;
  
  // It returns a nonzero value if the two values are equal, and 0 if the 
  // two values are not equal. 
  if ((basic1 == basic2) ||
      ((basic1 != NULL) && basic1->equals(basic2)))
  {
    return 1;
  }
  else {
    return 0;
  }
}


/*
 * Return a 32 bit hash code from key.
 */
static PLHashNumber PR_CALLBACK basicTypeHashFunc(const void * key)
{
  BasicType * basicType = (BasicType*)key;

  if (basicType != NULL) {
    return basicType->hashCode();
  }
  return 0;
}


