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
 * @(#)Status.java	1.9 06/27/07
 */ 

package com.sun.messaging.jmq.io;

/**
 * This class enumerates the JMQ status codes. It 
 * roughly follows the HTTP model of dividing the
 * status codes into categories.
 */
public class Status {

    /**
     * 100-199 Informational.
     * We don't have any of these yet.
     */

    /**
     * 200-299 Success
     */
    public static final int OK               = 200;	// Success
    public static final int CREATED          = 201;	// Created
    public static final int ACCEPTED         = 202;	// Created

    /**
     * 300-399 Redirection
     */
    public static final int MULTIPLE_CHOICES = 300;	// Multiple Choices
    public static final int MOVED_PERMANENTLY= 301;	// Moved Permanently
    public static final int MOVED_TEMPORARILY= 302;	// Moved Temporarily
    public static final int SEE_OTHER        = 303;	// See Other        
    public static final int NOT_MODIFIED     = 304;	// Not Modified

    /**
     * 400-499 Request error
     */
    public static final int BAD_REQUEST  = 400; // Request was invalid
    public static final int UNAUTHORIZED = 401; // Resource requires authentication
    public static final int PAYMENT_REQUIRED = 402; // TBD
    public static final int FORBIDDEN    = 403;	// User does not have access
    public static final int NOT_FOUND    = 404;	// Resource was not found
    public static final int NOT_ALLOWED  = 405;	// Method not allowed on resrc
    public static final int NOT_ACCEPTABLE=406; // Not Acceptable
    public static final int PROXY_AUTH_REQUIRED = 407; // TBD
    public static final int TIMEOUT      = 408; // Server has timed out
    public static final int CONFLICT     = 409; // Resource in conflict
    public static final int GONE         = 410; // Resource is not available
    public static final int LENGTH_REQUIRED     = 411; // TBD
    public static final int PRECONDITION_FAILED = 412;	// Precondition not met
    public static final int INVALID_LOGIN    = 413; // invalid login
    public static final int RESOURCE_FULL    = 414; // Resource is full
    public static final int UNSUPPORTED_TYPE = 415; // Unsupported type
    public static final int EXPECTATION_FAILED = 417; // expectation failed
    public static final int ENTITY_TOO_LARGE = 423; // Request entity too large

    public static final int RETRY = 449; // Retry the request

    /**
     * 500-599 Server error
     */
    public static final int ERROR            = 500; // Internal server error
    public static final int NOT_IMPLEMENTED  = 501; // Not implemented
    public static final int UNAVAILABLE      = 503; // Server is temporarily
    						    // unavailable
    public static final int BAD_VERSION      = 505; // Version not supported

    /**
     * Return a string description of the specified status code
     *
     * @param    n    Type to return description for
     */
    public static String getString(int n) {
        switch (n) {
	case 200: return "OK(" + n + ")";
	case 201: return "CREATED(" + n + ")";
	case 202: return "ACCEPTED(" + n + ")";
        case 300: return "MULTIPLE_CHOICES(" + n + ")";
        case 301: return "MOVED_PERMANENTLY(" + n + ")";
        case 302: return "MOVED_TEMPORARILY(" + n + ")";
        case 303: return "SEE_OTHER(" + n + ")";
        case 304: return "NOT_MODIFIED(" + n + ")";
	case 400: return "BAD_REQUEST(" + n + ")";
	case 401: return "UNAUTHORIZED(" + n + ")";
	case 402: return "PAYMENT_REQUIRED(" + n + ")";
	case 403: return "FORBIDDEN(" + n + ")";
	case 404: return "NOT_FOUND(" + n + ")";
	case 405: return "NOT_ALLOWED(" + n + ")";
	case 406: return "NOT_ACCEPTABLE(" + n + ")";
	case 407: return "PROXY_AUTH_REQUIRED(" + n + ")";
	case 408: return "TIMEOUT(" + n + ")";
	case 409: return "CONFLICT(" + n + ")";
	case 410: return "GONE(" + n + ")";
	case 411: return "LENGTH_REQUIRED(" + n + ")";
	case 412: return "PRECONDITION_FAILED(" + n + ")";
	case 413: return "INVALID_LOGIN(" + n + ")";
	case 414: return "RESOURCE_FULL(" + n + ")";
        case 415: return "UNSUPPORTED_TYPE(" + n + ")";
        case 417: return "EXPECTATION_FAILED(" + n + ")";
        case 423: return "ENTITY_TOO_LARGE(" + n + ")";
	case 500: return "ERROR(" + n + ")";
	case 501: return "NOT_IMPLEMENTED(" + n + ")";
	case 503: return "UNAVAILABLE(" + n + ")";
	case 504: return "BAD_VERSION(" + n + ")";

	default:  return "UNKNOWN(" + n + ")";
	}
    }
}

