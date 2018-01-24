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
 * @(#)mqbasictypes.h	1.10 06/26/07
 */ 

#ifndef MQ_BASICTYPES_H
#define MQ_BASICTYPES_H

/*
 * defines MQ basic types
 */

#if ((defined(__SUNPRO_CC) && (__SUNPRO_CC_COMPAT == 5)) \
         || defined(__SUNPRO_C)) \
    && defined(__sun) && (defined(__sparc) || (defined(__i386) || (defined(__amd64) || (defined(__x86_64)))))
#ifndef SOLARIS
#define SOLARIS
#endif
#endif

#if (defined(__GNUC__) || defined (__GNUG__)) && defined(__linux__)
#ifndef LINUX
#define LINUX
#endif
#endif

//######hpux-dev######
#if (defined(__hpux))
#ifndef HPUX
#define HPUX
#endif
#endif

#if (defined(__IBMC__) || defined (__IBMCPP__)) && defined(__unix__)
#ifndef AIX 
#define AIX 
#endif
#endif


#if defined(_MSC_VER) && defined(_WIN32)
#ifndef WIN32
#define WIN32
#endif
#endif

#ifdef SOLARIS
#include <inttypes.h>
#endif
#ifdef LINUX
#include <stdint.h>
#endif
//#####hpux-dev#####
#ifdef HPUX
#include <inttypes.h>
#endif

#ifdef AIX 
#include <inttypes.h>
#endif


#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

//#####hpux-dev#####
#if defined(SOLARIS) || defined(LINUX) || defined(HPUX) || defined(AIX)
typedef int32_t   MQBool;
typedef int8_t    MQInt8;
typedef int16_t   MQInt16;
typedef int32_t   MQInt32;
typedef int64_t   MQInt64;
typedef uint32_t  MQUint32;
#elif defined(WIN32)
typedef __int32           MQBool;
typedef __int8            MQInt8;
typedef __int16           MQInt16;
typedef __int32           MQInt32;
typedef __int64           MQInt64;
typedef unsigned __int32  MQUint32;
#else
#error unknown platform
#endif

//#####hpux-dev#####
#if defined(SOLARIS) || defined(LINUX) || defined(WIN32) || defined(HPUX) || defined(AIX)
typedef float   MQFloat32;
typedef double  MQFloat64;
typedef char    MQChar;

#define MQ_TRUE  1
#define MQ_FALSE 0
#else
#error unknown platform
#endif

//#####hpux-dev#####
/** internal use only */ 
#if defined(WIN32)
#if defined(MQ_EXPORT_DLL_SYMBOLS)
#define EXPORTED_SYMBOL __declspec(dllexport)
#else
#define EXPORTED_SYMBOL __declspec(dllimport)
#endif /* defined(MQ_EXPORT_DLL_SYMBOLS) */
#elif defined(SOLARIS) || defined(LINUX) || defined(HPUX) || defined(AIX)
#define EXPORTED_SYMBOL 
#else
#error unknown platform
#endif  /* defined(WIN32) */


#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_BASICTYPES_H */
