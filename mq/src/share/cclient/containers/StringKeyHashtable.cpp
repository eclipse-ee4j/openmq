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
 * @(#)StringKeyHashtable.cpp	1.1 10/17/07
 */ 

#include "StringKeyHashtable.hpp"
#include "../basictypes/UTF8String.hpp"
#include "../util/UtilityMacros.h"
#include "../util/LogUtils.hpp"

/*
 *
 */
StringKeyHashtable::StringKeyHashtable()
{
  CHECK_OBJECT_VALIDITY();

  PRBool autoDeleteKey, autoDeleteValue;
  this->table = new BasicTypeHashtable(autoDeleteKey=PR_TRUE, 
                                       autoDeleteValue=PR_TRUE); 
}


/*
 *
 */
StringKeyHashtable::~StringKeyHashtable()
{
  CHECK_OBJECT_VALIDITY();

  DELETE( this->table );
}

/*
 *
 */
MQError
StringKeyHashtable::remove(const char *key)
{
  CHECK_OBJECT_VALIDITY();
  
  MQError errorCode = MQ_SUCCESS;

  RETURN_ERROR_IF_NULL(key);  
  RETURN_ERROR_IF(this->table == NULL, MQ_OUT_OF_MEMORY);  

  UTF8String keyu(key);

  monitor.enter();
  errorCode = this->table->removeEntry(&keyu);
  monitor.exit();

  if (errorCode != MQ_SUCCESS) {
    if (errorCode != MQ_NOT_FOUND)  {
    LOG_WARNING(( CODELOC, HASHTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
        "Failed to remove key=%s from the StringKeyHashtable 0x%p because '%s' (%d)", 
         key, this, errorStr(errorCode), errorCode ));
    } else {
    LOG_FINE(( CODELOC, HASHTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
        "Can't remove key=%s from the StringKeyHashtable 0x%p because '%s' (%d)", 
         key, this, errorStr(errorCode), errorCode ));
    }
  }

  return errorCode;
}

/*
 *
 */
MQError 
StringKeyHashtable::add(const char * key, Object * const value)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  Object * prev = NULL;

  RETURN_ERROR_IF_NULL(key);
  RETURN_ERROR_IF_NULL(value);
  RETURN_ERROR_IF(this->table == NULL, MQ_OUT_OF_MEMORY);

  UTF8String * keyu = new UTF8String(key);

  RETURN_ERROR_IF(keyu == NULL, MQ_OUT_OF_MEMORY);

  monitor.enter();
  errorCode = this->table->getValueFromKey(keyu, (const Object** const)&prev);
  if (errorCode == MQ_SUCCESS) {
    LOG_INFO(( CODELOC, HASHTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
        "key=%s already exists in StringKeyHashtable 0x%p, removing ...",
         key, this ));
    errorCode = remove(key);
    if (errorCode == MQ_SUCCESS) {
      errorCode = this->table->addEntry(keyu, value);
    }
  } else if (errorCode == MQ_NOT_FOUND) {
    errorCode = this->table->addEntry(keyu, value);
  }
  monitor.exit();

  if (errorCode != MQ_SUCCESS) {
    DELETE( keyu );
    LOG_SEVERE(( CODELOC, HASHTABLE_LOG_MASK, NULL_CONN_ID, errorCode, 
        "Failed to add key=%s to StringKeyHashtable 0x%p because '%s' (%d)",
         key, this, errorStr(errorCode), errorCode ));
  }

  return errorCode;
}


/*
 *
 */
MQError
StringKeyHashtable::get(const char * key, Object ** const value)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;

  RETURN_ERROR_IF_NULL( key );
  RETURN_ERROR_IF_NULL( value );
  RETURN_ERROR_IF(this->table == NULL, MQ_OUT_OF_MEMORY);

  UTF8String keyu(key);


  monitor.enter();
  errorCode = this->table->getValueFromKey(&keyu, (const Object** const)value);
  monitor.exit();

  if (errorCode != MQ_SUCCESS) {
    if (errorCode == MQ_NOT_FOUND) {
      LOG_FINE(( CODELOC, HASHTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
          "key=%s not found in StringKeyHashtable 0x%p", key, this ));
    } else {
      LOG_SEVERE(( CODELOC, HASHTABLE_LOG_MASK, NULL_CONN_ID, errorCode,
          "Failed to get key=%s from StringKeyHashtable 0x%p because '%s' (%d)",
           key, this, errorStr(errorCode), errorCode ));
    }
  }

  return errorCode;
}

