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
 * @(#)Status.hpp	1.4 06/26/07
 */ 

#ifndef STATUS_HPP
#define STATUS_HPP

#include <nspr.h>
#include "../error/ErrorCodes.h"

static const PRInt32 STATUS_UNKNOWN      = 0;        // Initial value

/**
* 100-199 Informational.
* We don't have any of these yet.
*/

/**
* 200-299 Success
*/
static const PRInt32 STATUS_OK           = 200;      // Success

/**
* 300-399 Redirection
* We don't have any of these yet.
*/

/**
* 400-499 Request error
*/
static const PRInt32 STATUS_BAD_REQUEST  = 400;      // Request was invalid
static const PRInt32 STATUS_UNAUTHORIZED = 401;      // Resource requires authentication
static const PRInt32 STATUS_FORBIDDEN    = 403;	// User does not have access
static const PRInt32 STATUS_NOT_FOUND    = 404;	// Resource was not found
static const PRInt32 STATUS_NOT_ALLOWED  = 405;	// Method not allowed on resrc
static const PRInt32 STATUS_TIMEOUT      = 408;	// Server has timed out
static const PRInt32 STATUS_CONFLICT     = 409;	// Resource in conflict
static const PRInt32 STATUS_GONE         = 410;	// Resource is not available
static const PRInt32 STATUS_PRECONDITION_FAILED = 412;    // A precondition not met
static const PRInt32 STATUS_INVALID_LOGIN       = 413;    // invalid login
static const PRInt32 STATUS_RESOURCE_FULL       = 414;    // Resource is full 
static const PRInt32 STATUS_ENTITY_TOO_LARGE    = 423;    // Request entity too large

/**
* 500-599 Server error
*/
static const PRInt32 STATUS_ERROR            = 500; // Internal server error
static const PRInt32 STATUS_NOT_IMPLEMENTED  = 501; // Not implemented
static const PRInt32 STATUS_UNAVAILABLE      = 503; // Server is temporarily
// unavailable
static const PRInt32 STATUS_BAD_VERSION      = 505; // Version not supported


/**
 * This class enumerates the JMQ status codes. It roughly follows the
 * HTTP model of dividing the status codes into categories.
 *
 */
class Status {
public:

  /**
   * Converts the status to the corresponding iMQError
   */
  static iMQError toIMQError(const PRInt32 statusCode);
  
};

#endif






