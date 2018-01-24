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
 * @(#)DebugUtils.h	1.9 06/26/07
 */ 

/*
 * This file defines debugging utilities for the iMQ C client.
 */

#ifndef DEBUGUTILS_H
#define DEBUGUTILS_H

#ifdef __cplusplus
extern "C" {
#endif

#if defined(WIN32) 
# pragma warning(disable: 4514) /* warning C4514: 'delete' : unreferenced inline function has been removed */ 
# pragma warning(disable: 4711) /* warning C4711: function 'XXX' selected for automatic inline expansion */ 
#endif 

#ifdef __cplusplus
}
#endif

#if defined(WIN32) 
# include <crtdbg.h>

/* #define _CRTDBG_MAP_ALLOC */
# ifdef _DEBUG
#  define DEBUG_CLIENTBLOCK   new( _CLIENT_BLOCK, __FILE__, __LINE__)
# else
#  define DEBUG_CLIENTBLOCK
# endif /* _DEBUG */
#endif /* defined(WIN32) */


#include "../util/MemAllocTest.h"
//#define TEST_MEMORY_ALLOC_FAILURES
#if defined(WIN32) 
#ifdef _DEBUG 
#  ifdef TEST_MEMORY_ALLOC_FAILURES
/* Log the memory failure for iMQ */
#   ifdef MQ_LIBRARY
#    define new (!mallocSucceeds()) ?  \
       (LOG_FINE_NEW(( CODELOC, MEMORY_LOG_MASK, NULL_CONN_ID, IMQ_OUT_OF_MEMORY, "new failed" )),  \
         NULL) : \
       DEBUG_CLIENTBLOCK
#   else
#    define new (!mallocSucceeds()) ?  NULL: DEBUG_CLIENTBLOCK
#   endif /* IMQ_EXPORT_DLL_SYMBOLS */
#  else
#    define new DEBUG_CLIENTBLOCK
#  endif /* TEST_MEMORY_ALLOC_FAILURES */
#endif /* _DEBUG */
#endif /* defined(WIN32) */

#ifdef __cplusplus
extern "C" {
#endif

#include <stdlib.h>


#include <assert.h>
#define ASSERT assert


#ifdef __cplusplus
}
#endif

#endif /* DEBUGUTILS_H */


