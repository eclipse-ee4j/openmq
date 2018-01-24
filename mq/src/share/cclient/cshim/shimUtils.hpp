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
 * @(#)shimUtils.hpp	1.8 06/26/07
 */ 

#ifndef SHIMUTILS_HPP
#define SHIMUTILS_HPP


#include "mqtypes.h"
#include "../util/UtilityMacros.h"
#include "../basictypes/HandledObject.hpp"


/**
 * Return a pointer to an object, with the given handle.  This
 * acquires an external reference to the object, which must be
 * released by calling releaseHandledObject.
 *
 * @param handle a handle to the object to retrieve.
 * @param objectType the expected type of the object.  See
 *        HandledObject.hpp for a list of valid types.
 * @return a pointer to the handled object.  If the handle is
 *         invalid or the type of the handled object does not
 *         match objectType, then NULL is returned.  */
HandledObject *
getHandledObject(const MQObjectHandle handle,
                 const HandledObjectType objectType);

/**
 * Releases an external reference to object.  This might actually
 * delete object.  This will occur if object was deleted internally by
 * the library (e.g. due to the connection being closed) but could not
 * actually be deleted because an external reference was held.
 *
 * @param object the object to release the handle to.
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
MQStatus
releaseHandledObject(HandledObject * object);

/**
 * Deletes the object with the given handle.
 *
 * @param handle the handle of the object to delete.
 * @param objectType the type of the object.  See
 *        HandledObject.hpp for a list of valid types.
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
MQStatus
freeHandledObject(const MQObjectHandle handle, 
                  const HandledObjectType objectType);


/**
 * This macro returns an MQStatus struct with the .errorCode field
 * set to error.  */
#define RETURN_STATUS(error)                \
  IMQ_BEGIN_MACRO                           \
    MQStatus i_M_Q_S_t_a_t_u_s;            \
    i_M_Q_S_t_a_t_u_s.errorCode = (error);  \
    return i_M_Q_S_t_a_t_u_s;               \
  IMQ_END_MACRO


/**
 * If expr is true, this macro returns an MQStatus struct with the
 * .errorCode field set to error.  Otherwise it does nothing.  
 */
#define RETURN_STATUS_IF(expr,error)          \
  IMQ_BEGIN_MACRO                             \
    if (expr) {                               \
      MQStatus i_M_Q_S_t_a_t_u_s;            \
      i_M_Q_S_t_a_t_u_s.errorCode = (error);  \
      return i_M_Q_S_t_a_t_u_s;               \
    }                                         \
  IMQ_END_MACRO


#endif /* SHIMUTILS_HPP */



