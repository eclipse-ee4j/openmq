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
 * @(#)iMQPropertiesShim.cpp	1.15 06/26/07
 */ 

#include "mqproperties.h"
#include "mqproperties-priv.h"
#include "shimUtils.hpp"
#include "../containers/Properties.hpp"


const MQStatus MQ_STATUS_SUCCESS = {MQ_SUCCESS};

static MQType objectTypeToMQType(const TypeEnum objectType);
static MQError checkBasicTypes();

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQCreateProperties(MQPropertiesHandle * propertiesHandle)
{
  static const char FUNCNAME[] = "MQCreateProperties";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);
  
  ERRCHK( checkBasicTypes() );

  // Make sure propertiesHandle is not NULL and then initialize it
  CNDCHK( propertiesHandle == NULL, MQ_NULL_PTR_ARG );
  propertiesHandle->handle = (MQInt32)HANDLED_OBJECT_INVALID_HANDLE;

  // Allocate a new properties object
  //MEMCHK( props = new Properties() );
  props = new Properties();
  CNDCHK( props == NULL, MQ_OUT_OF_MEMORY );
  ERRCHK( props->getInitializationError() );

  // This properties handle is valid outside of the library
  props->setIsExported(PR_TRUE);

  // Set the output handle
  propertiesHandle->handle = props->getHandle();

  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  DELETE( props );
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus 
MQFreeProperties(MQPropertiesHandle propertiesHandle)
{
  CLEAR_ERROR_TRACE(PR_FALSE);
  return freeHandledObject(propertiesHandle.handle, PROPERTIES_OBJECT);
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetPropertyType(const MQPropertiesHandle propertiesHandle,
                  ConstMQString key,
                  MQType*       propertyType)
{
  static const char FUNCNAME[] = "MQGetPropertyType";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  TypeEnum objectType;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( key == NULL, MQ_NULL_PTR_ARG );
  CNDCHK( propertyType == NULL, MQ_NULL_PTR_ARG );

  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  // Get the type of the object and convert it to an MQtype
  ERRCHK( props->getPropertyType(key, &objectType) );
  *propertyType = objectTypeToMQType(objectType);

  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}  


/*
 *
 */
MQType 
objectTypeToMQType(const TypeEnum objectType)
{
  switch(objectType) {
  case BOOLEAN_TYPE:           return MQ_BOOL_TYPE;
  case BYTE_TYPE:              return MQ_INT8_TYPE;
  case SHORT_TYPE:             return MQ_INT16_TYPE;
  case INTEGER_TYPE:           return MQ_INT32_TYPE;
  case LONG_TYPE:              return MQ_INT64_TYPE;
  case FLOAT_TYPE:             return MQ_FLOAT32_TYPE;
  case DOUBLE_TYPE:            return MQ_FLOAT64_TYPE;
  case UTF8_STRING_TYPE:       return MQ_STRING_TYPE;
  case UTF8_LONG_STRING_TYPE:  return MQ_STRING_TYPE;
  default:                     return MQ_INVALID_TYPE;
  }
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQPropertiesKeyIterationStart(const MQPropertiesHandle propertiesHandle)
{
  static const char FUNCNAME[] = "MQPropertiesKeyIterationStart";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->keyIterationStart() );

  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
  

/*
 *
 */
EXPORTED_SYMBOL MQBool
MQPropertiesKeyIterationHasNext(const MQPropertiesHandle propertiesHandle)
{
  static const char FUNCNAME[] = "MQPropertiesKeyIterationHasNext";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  MQBool hasNext = false;

  CLEAR_ERROR_TRACE(PR_FALSE);
  
  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  hasNext = props->keyIterationHasNext();

  releaseHandledObject(props);
  return hasNext;
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  return MQ_FALSE;
}
  

/*
 * Caller should not modify or free key
 */
EXPORTED_SYMBOL MQStatus
MQPropertiesKeyIterationGetNext(const MQPropertiesHandle propertiesHandle,
                                ConstMQString * key)
{
  static const char FUNCNAME[] = "MQPropertiesKeyIterationGetNext";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE(PR_FALSE); 

  CNDCHK( key == NULL, MQ_NULL_PTR_ARG );
  *key = NULL;
  
  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->keyIterationGetNext(key) );
  
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}



//
// Get/Set individual properties
//

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetStringProperty(const MQPropertiesHandle propertiesHandle,
                    ConstMQString key,
                    ConstMQString value)
{
  static const char FUNCNAME[] = "MQSetStringProperty";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->setStringProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

             
/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetStringProperty(const MQPropertiesHandle propertiesHandle,
                    ConstMQString key,
                    ConstMQString * value)
{
  static const char FUNCNAME[] = "MQGetStringProperty";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);
  
  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->getStringProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetBoolProperty(const MQPropertiesHandle propertiesHandle,
                  ConstMQString key,
                  MQBool   value)
{
  static const char FUNCNAME[] = "MQSetBoolProperty";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE( PR_FALSE );
  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->setBooleanProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
             
/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetBoolProperty(const MQPropertiesHandle propertiesHandle,
                  ConstMQString key,
                  MQBool *      value)
{
  static const char FUNCNAME[] = "MQGetBoolProperty";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;

  CLEAR_ERROR_TRACE( PR_FALSE );
  
  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->getBooleanProperty(key, (PRBool *)value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetInt8Property(const MQPropertiesHandle propertiesHandle,
                  ConstMQString key,
                  MQInt8   value)
{
  static const char FUNCNAME[] = "MQSetInt8Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE( PR_FALSE );

  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->setByteProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
             
/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetInt8Property(const MQPropertiesHandle propertiesHandle,
                  ConstMQString key,
                  MQInt8 *      value)
{
  static const char FUNCNAME[] = "MQGetInt8Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE( PR_FALSE );

  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->getByteProperty(key, (PRInt8 *)value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
         

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetInt16Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString key,
                   MQInt16   value)
{
  static const char FUNCNAME[] = "MQSetInt16Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE( PR_FALSE );

  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->setShortProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
             
/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetInt16Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString key,
                   MQInt16 *     value)
{
  static const char FUNCNAME[] = "MQGetInt16Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;

  CLEAR_ERROR_TRACE( PR_FALSE );
  
  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->getShortProperty(key, (PRInt16 *)value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
         
            

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetInt32Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString key,
                   MQInt32   value)
{
  static const char FUNCNAME[] = "MQSetInt32Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;

  CLEAR_ERROR_TRACE( PR_FALSE );
  
  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->setIntegerProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
             
/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetInt32Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString key,
                   MQInt32 *     value)
{
  static const char FUNCNAME[] = "MQGetInt32Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE( PR_FALSE );

  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->getIntegerProperty(key, (PRInt32 *)value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
         
            

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetInt64Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString key,
                   MQInt64   value)
{
  static const char FUNCNAME[] = "MQSetInt64Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE( PR_FALSE );
  
  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->setLongProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
             
/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetInt64Property(const MQPropertiesHandle propertiesHandle,
                   ConstMQString key,
                   MQInt64 * value)
{
  static const char FUNCNAME[] = "MQGetInt64Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE( PR_FALSE );

  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->getLongProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
         
            

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetFloat32Property(const MQPropertiesHandle propertiesHandle,
                     ConstMQString key,
                     MQFloat32   value)
{
  static const char FUNCNAME[] = "MQSetFloat32Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE( PR_FALSE );

  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->setFloatProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
             
/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetFloat32Property(const MQPropertiesHandle propertiesHandle,
                     ConstMQString key,
                     MQFloat32 *   value)
{
   static const char FUNCNAME[] = "MQGetFloat32Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;

  CLEAR_ERROR_TRACE( PR_FALSE );
  
  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->getFloatProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
         
/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetFloat64Property(const MQPropertiesHandle propertiesHandle,
                     ConstMQString key,
                     MQFloat64   value)
{
  static const char FUNCNAME[] = "MQSetFloat64Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE( PR_FALSE );
  
  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->setDoubleProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
             
/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetFloat64Property(const MQPropertiesHandle propertiesHandle,
                     ConstMQString key,
                     MQFloat64 *   value)
{
  static const char FUNCNAME[] = "MQGetFloat64Property";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;
  
  CLEAR_ERROR_TRACE( PR_FALSE );

  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->getDoubleProperty(key, value) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
         
            

/*
 * This function should not be exported.
 */
EXPORTED_SYMBOL MQStatus
MQReadPropertiesFromFile(const MQPropertiesHandle propertiesHandle,
                         const char * fileName)
{
  static const char FUNCNAME[] = "MQReadPropertiesFromFile";
  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;

  CLEAR_ERROR_TRACE( PR_FALSE );
  
  // Convert the handle to a properties pointer
  props = (Properties*)getHandledObject(propertiesHandle.handle, 
                                        PROPERTIES_OBJECT);
  CNDCHK( props == NULL, MQ_STATUS_INVALID_HANDLE );

  ERRCHK( props->readFromFile(fileName) );
    
  releaseHandledObject(props);
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(props);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
MQError
checkBasicTypes()
{
  if (sizeof(MQBool) != 4) return MQ_BASIC_TYPE_SIZE_MISMATCH;
  if (sizeof(MQInt8) != 1)  return MQ_BASIC_TYPE_SIZE_MISMATCH;
  if (sizeof(MQInt16) != 2)  return MQ_BASIC_TYPE_SIZE_MISMATCH;
  if (sizeof(MQInt32) != 4)  return MQ_BASIC_TYPE_SIZE_MISMATCH;
  if (sizeof(MQUint32) != 4)  return MQ_BASIC_TYPE_SIZE_MISMATCH;
  if (sizeof(MQInt64) != 8)  return MQ_BASIC_TYPE_SIZE_MISMATCH;
  if (sizeof(MQFloat32) != 4)  return MQ_BASIC_TYPE_SIZE_MISMATCH;
  if (sizeof(MQFloat64) != 8)  return MQ_BASIC_TYPE_SIZE_MISMATCH;
  return MQ_SUCCESS;
}

