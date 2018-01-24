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
 * @(#)mqproperties.h	1.13 06/26/07
 */ 

#ifndef MQ_PROPERTIES_H
#define MQ_PROPERTIES_H

/*
 * declarations of C interface for properties
 */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"


/**
 * Creates a new properties object.
 *
 * @param propertiesHandle the output handle parameter that holds the
 *        newly created properties
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQCreateProperties(MQPropertiesHandle * propertiesHandle);

/**
 * Frees the properties object specified by propertiesHandle.
 *
 * @param propertiesHandle the properties object to free.
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQFreeProperties(MQPropertiesHandle propertiesHandle);

/**
 * Starts an iteration through the property keys.  You cannot have
 * multiple active iterations on the same properties object.  Adding
 * or removing properties to/from the properties object invalidates
 * the iteration.  This is used with MQPropertiesKeyIterationHasNext
 * and MQPropertiesKeyIterationGetNext to iterate through all of the
 * property keys.  Here is an example usage (without error checking):
 * <pre>
 *   MQPropertiesKeyIterationStart(propsHandle);
 *   while (MQPropertiesKeyIterationHasNext(propsHandle)) {
 *     ConstMQString * key = NULL;
 *     MQType propType;
 *     MQPropertiesKeyIterationGetNext(&key, propsHandle);
 *     MQGetPropertyType(key, &propType, propsHandle);
 *     if (propType == MQStringType) {
 *       ConstMQString * value = NULL;
 *       MQGetStringProperty(key, &value, propsHandle);
 *       // do something with the key value pair 
 *     }
 *   }
 * </pre>
 * @param propertiesHandle the properties object to start the
 *        iteration on
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.
 * @see MQPropertiesKeyIterationHasNext
 * @see MQPropertiesKeyIterationGetNext */
EXPORTED_SYMBOL MQStatus 
MQPropertiesKeyIterationStart(const MQPropertiesHandle propertiesHandle);

/**
 * Returns MQ_TRUE if there are additional property keys in the
 * iteration, and MQ_FALSE if there are no more property keys in the
 * iteration.  MQPropertiesKeyIterationStart must be called before
 * this function.
 *
 * @param propertiesHandle the properties object to check for remaining
 *        keys in the iteration.
 * @return MQ_TRUE iff there are additional property keys in the iteration
 * @see MQPropertiesKeyIterationStart
 * @see MQPropertiesKeyIterationGetNext */
EXPORTED_SYMBOL MQBool   
MQPropertiesKeyIterationHasNext(const MQPropertiesHandle propertiesHandle);

/**
 * Retrieves the next key from the key iteration.  See
 * MQPropertiesKeyIterationStart for more information.
 *
 * @param propertiesHandle the properties object to return the next
 *        key in the iteration for
 * @param key the output parameter for next properties key in the
 *        iteration.  The caller should not modify or attempt to free
 *        this string.  The string is a NULL terminated UTF-8 encoded
 *        string.
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.
 * @see MQPropertiesKeyIterationStart
 * @see MQPropertiesKeyIterationHasNext */
EXPORTED_SYMBOL MQStatus 
MQPropertiesKeyIterationGetNext(const MQPropertiesHandle propertiesHandle,
                                ConstMQString *          key);

/**
 * Returns the type of the property value with the specified key.
 *
 * @param propertiesHandle the properties object to return the property
 *        value type for
 * @param key the property key to retrieve the type for
 * @param propertyType the output parameter for the type of the property value
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.
 * @see MQPropertiesKeyIterationStart
 * @see MQPropertiesKeyIterationHasNext */
EXPORTED_SYMBOL MQStatus 
MQGetPropertyType(const MQPropertiesHandle propertiesHandle,
                  ConstMQString            key,
                  MQType *                 propertyType);

/**
 * Property Type Conversions.
 *
 * MQSet<TYPE>Property  MQGet<TYPE>Property  
 * ---------------------------------------
 * Bool                   Bool, String
 * Int8                   Int8, Int16, Int32, Int64, String
 * Int16                  Int16, Int32, Int64, String
 * Int32                  Int32, Int64, String
 * Int64                  Int64, String
 * Float32                Float32, Float64, String
 * Float64                Float64, String
 * String                 String, Int8, Int16, Int32, Int64, Float32, Float64
 */

/**
 * Sets the value of the property specified by key to value.
 *
 * @param propertiesHandle the properties object to set the value for
 * @param key the key of the property to set
 * @param value the value of the property to set
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSetStringProperty(const MQPropertiesHandle propertiesHandle,
                    ConstMQString            key,
                    ConstMQString            value);

/**
 * Gets the value of the property specified by key.
 *
 * @param propertiesHandle the properties object to get the value for
 * @param key the key of the property to get
 * @param value the output parameter of for the value of the property to get
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */  
EXPORTED_SYMBOL MQStatus 
MQGetStringProperty(const MQPropertiesHandle propertiesHandle,
                    ConstMQString            key,
                    ConstMQString *          value);

/**
 * Sets the value of the property specified by key to value.
 *
 * @param propertiesHandle the properties object to set the value for
 * @param key the key of the property to set
 * @param value the value of the property to set
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSetBoolProperty(const MQPropertiesHandle propertiesHandle,
                  ConstMQString            key,
                  MQBool                   value);

/**
 * Gets the value of the property specified by key.
 *
 * @param propertiesHandle the properties object to get the value for
 * @param key the key of the property to get
 * @param value the output parameter of for the value of the property to get
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */  
EXPORTED_SYMBOL MQStatus 
MQGetBoolProperty(const MQPropertiesHandle propertiesHandle,
                  ConstMQString            key,
                  MQBool *                 value);

/**
 * Sets the value of the property specified by key to value.
 *
 * @param propertiesHandle the properties object to set the value for
 * @param key the key of the property to set
 * @param value the value of the property to set
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSetInt8Property(const MQPropertiesHandle propertiesHandle,
                  ConstMQString            key,
                  MQInt8                   value);

/**
 * Gets the value of the property specified by key.
 *
 * @param propertiesHandle the properties object to get the value for
 * @param key the key of the property to get
 * @param value the output parameter of for the value of the property to get
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */  
EXPORTED_SYMBOL MQStatus 
MQGetInt8Property(const MQPropertiesHandle propertiesHandle,
                  ConstMQString            key,
                  MQInt8 *                 value);

/**
 * Sets the value of the property specified by key to value.
 *
 * @param propertiesHandle the properties object to set the value for
 * @param key the key of the property to set
 * @param value the value of the property to set
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSetInt16Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString            key,
                   MQInt16                  value);

/**
 * Gets the value of the property specified by key.
 *
 * @param propertiesHandle the properties object to get the value for
 * @param key the key of the property to get
 * @param value the output parameter of for the value of the property to get
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */  
EXPORTED_SYMBOL MQStatus 
MQGetInt16Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString            key,
                   MQInt16 *                value);

/**
 * Sets the value of the property specified by key to value.
 *
 * @param propertiesHandle the properties object to set the value for
 * @param key the key of the property to set
 * @param value the value of the property to set
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSetInt32Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString            key,
                   MQInt32                  value);

/**
 * Gets the value of the property specified by key.
 *
 * @param propertiesHandle the properties object to get the value for
 * @param key the key of the property to get
 * @param value the output parameter of for the value of the property to get
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */  
EXPORTED_SYMBOL MQStatus 
MQGetInt32Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString            key,
                   MQInt32 *                value);

/**
 * Sets the value of the property specified by key to value.
 *
 * @param propertiesHandle the properties object to set the value for
 * @param key the key of the property to set
 * @param value the value of the property to set
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSetInt64Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString            key,
                   MQInt64                  value);

/**
 * Gets the value of the property specified by key.
 *
 * @param propertiesHandle the properties object to get the value for
 * @param key the key of the property to get
 * @param value the output parameter of for the value of the property to get
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */  
EXPORTED_SYMBOL MQStatus 
MQGetInt64Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString            key,
                   MQInt64 *                value);

/**
 * Sets the value of the property specified by key to value.
 *
 * @param propertiesHandle the properties object to set the value for
 * @param key the key of the property to set
 * @param value the value of the property to set
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSetFloat32Property(const MQPropertiesHandle propertiesHandle,
                     ConstMQString            key,
                     MQFloat32                value);

/**
 * Gets the value of the property specified by key.
 *
 * @param propertiesHandle the properties object to get the value for
 * @param key the key of the property to get
 * @param value the output parameter of for the value of the property to get
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */  
EXPORTED_SYMBOL MQStatus 
MQGetFloat32Property(const MQPropertiesHandle propertiesHandle,
                     ConstMQString            key,
                     MQFloat32 *              value);

/**
 * Sets the value of the property specified by key to value.
 *
 * @param propertiesHandle the properties object to set the value for
 * @param key the key of the property to set
 * @param value the value of the property to set
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSetFloat64Property(const MQPropertiesHandle propertiesHandle,
                     ConstMQString            key,
                     MQFloat64                value);

/**
 * Gets the value of the property specified by key.
 *
 * @param propertiesHandle the properties object to get the value for
 * @param key the key of the property to get
 * @param value the output parameter of for the value of the property to get
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */  
EXPORTED_SYMBOL MQStatus 
MQGetFloat64Property(const MQPropertiesHandle propertiesHandle,
                     ConstMQString            key,
                     MQFloat64 *              value);

  
#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_PROPERTIES_H */
