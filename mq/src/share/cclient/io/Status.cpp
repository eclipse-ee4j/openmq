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
 * @(#)Status.cpp	1.4 06/26/07
 */ 

#include "Status.hpp"
#include "../util/UtilityMacros.h"

/*
 *
 */
iMQError
Status::toIMQError(const PRInt32 statusCode)
{
  switch(statusCode) {
  case STATUS_OK:                  return IMQ_SUCCESS;
  case STATUS_BAD_REQUEST:         return IMQ_BROKER_BAD_REQUEST ;
  case STATUS_UNAUTHORIZED:        return IMQ_BROKER_UNAUTHORIZED;
  case STATUS_FORBIDDEN:           return IMQ_BROKER_FORBIDDEN;
  case STATUS_NOT_FOUND:           return IMQ_BROKER_NOT_FOUND;
  case STATUS_TIMEOUT:             return IMQ_BROKER_TIMEOUT;
  case STATUS_CONFLICT:            return IMQ_BROKER_CONFLICT;
  case STATUS_GONE:                return IMQ_BROKER_GONE;
  case STATUS_PRECONDITION_FAILED: return IMQ_BROKER_PRECONDITION_FAILED;
  case STATUS_INVALID_LOGIN:       return IMQ_BROKER_INVALID_LOGIN;
  case STATUS_RESOURCE_FULL:       return MQ_BROKER_RESOURCE_FULL;
  case STATUS_ENTITY_TOO_LARGE:    return MQ_BROKER_ENTITY_TOO_LARGE;
  case STATUS_ERROR:               return IMQ_BROKER_ERROR;
  case STATUS_NOT_IMPLEMENTED:     return IMQ_BROKER_NOT_IMPLEMENTED;
  case STATUS_UNAVAILABLE:         return IMQ_BROKER_UNAVAILABLE;
  case STATUS_BAD_VERSION:         return IMQ_BROKER_BAD_VERSION;
  default:                         return IMQ_BROKER_ERROR;
  }
}

