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
 * @(#)UtilityMacros.h	1.14 10/17/07
 */ 

/**
 * This file defines various utility macros for the iMQ C client.  Many
 * of them deal with handling errors. 
 */

#ifndef UTILITYMACROS_H
#define UTILITYMACROS_H

#include "../debug/DebugUtils.h"
#include "../error/ErrorTrace.h"

#ifdef __cplusplus
extern "C" {
#endif
    
/* used by internal testing only */
#define INPUT_FILE_DIR "./"

#include "../error/ErrorCodes.h"
#include <nspr.h>
#include <stdio.h>
#include <string.h>
#include <memory.h>
#include <stdlib.h>

/**
 * These define how a macro block begins and ends.  This allows us to declare
 * local variables.  These actually should be defined to be "do {" and "}
 * while(0)", but that generates a compiler warning.  As long as we use {} in
 * all of our if's, then we will be fine.  
 */
#define IMQ_BEGIN_MACRO  {
#define IMQ_END_MACRO    } 

/** Returns the minimum of a and b */
#define MIN(a,b) (((a)<(b))?(a):(b))

/** Returns the maximum of a and b */
#define MAX(a,b) (((a)>(b))?(a):(b))

/* We define these string functions as macros in case we eventually
 * have to replace them because they can't handle UTF-8 */
#define STRLEN(x)             strlen((const char*)(x))
#define STRCPY(to,from)       strcpy((char*)(to),(const char*)(from))
#define STRNCPY(to,from,len)  strncpy((char*)(to),(const char*)(from),(len))
#define STRNCAT(to,from,len)  strncat((char*)(to),(const char*)(from),(len))
#define STRCAT(to,from)       strcat((char*)(to),(const char*)(from))
#define STRRCHR(str,chr)      strrchr((const char*)(str),(chr))
#define STRSTR(str,strDelim)  strstr((char*)(str),(const char*)(strDelim))
#define STRCMP(str1,str2)     strcmp((const char*)(str1),(const char*)(str2))
#define STRNCMP(str1,len,str2) strncmp((const char*)(str1), (const char*)(str2),(len))


#if defined(WIN32) 
# define SNPRINTF  _snprintf
# define VSNPRINTF _vsnprintf
# define STRCMPI _strcmpi
# define GETPID _getpid
#else
# define SNPRINTF  snprintf
# define VSNPRINTF vsnprintf
# define STRCMPI strcasecmp
# define GETPID  getpid 
#endif

/** This macro clears the variable x. */
#define MEMZERO(x)       memset((void*)&(x),0,sizeof(x))

/** Converts the string representation of an integer into a 32 bit integer.
    E.g. ATOI32("3838") returns 3838. */
/* PORTABILITY: this probably won't work on 64-bit platforms. */
#define ATOI32(x)        atoi(x)

/** If ptr is not NULL, deletes it and sets it to NULL. 
 *  Warning: the ptr arg must not have any side effects.
 *  For example, see how DELETE(stack->pop()) expands
 */
#undef DELETE
#define DELETE(ptr)         \
  if ((ptr) == NULL) {      \
  } else {                  \
    LOG_FINEST(( CODELOC, MEMORY_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS, \
                "deleting 0x%p",                                      \
                ptr ));                                               \
    delete (ptr);           \
    (ptr) = NULL;           \
  }

/** If ptr is not NULL, deletes it as an array and sets it to NULL. 
 *  Warning: the ptr arg must not have any side effects. See warning above.
 */
#define DELETE_ARR(arrPtr)  \
  if ((arrPtr) == NULL) {   \
  } else {                  \
    delete[] (arrPtr);      \
    (arrPtr) = NULL;        \
  }


/** If ptr is not NULL, deletes it as an array and sets it to NULL. 
 *  Warning: the ptr arg must not have any side effects. See warning above.
 */
#define HANDLED_DELETE(handledObject)                                 \
  if ((handledObject) == NULL)  {                                     \
  } else {                                                            \
    LOG_FINEST(( CODELOC, MEMORY_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS, \
                 "calling HandledObject::internallyDelete(0x%p)",     \
                 handledObject ));                                    \
    HandledObject::internallyDelete(handledObject);                   \
    (handledObject) = NULL;                                           \
  }

#define HANDLED_DELETE_IF_NOT_DELETED(handledObject)                  \
  if ((handledObject) == NULL)  {                                     \
  } else {                                                            \
    LOG_FINEST(( CODELOC, MEMORY_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS, \
                 "calling HandledObject::internallyDeleteWithCheck(0x%p)",  \
                 handledObject ));                                    \
    HandledObject::internallyDeleteWithCheck(handledObject, PR_TRUE); \
    (handledObject) = NULL;                                           \
  }


/* WARNING:  The macros RETURN_ERROR_IF_NULL, RETURN_IF_ERROR, ...
 * will generate an error in the following cases
 * if(XXX)
 *   RETURN_ERROR_IF_NULL(XXX);
 *
 * Use braces:
 * if(XXX) {
 *    RETURN_ERROR_IF_NULL(XXX);
 * }
 */

/** 
 * On Windows, the BREAKPOINT() macro defines a breakpoint assembly
 * instruction. It is primarily used to force a break when an error occurs so
 * the program state can be examined. On other platforms, it is a noop.  
 */
#define BREAK_ON_ERROR

#if defined(NDEBUG) || defined(MQ_NDEBUG_BREAKPOINT)
# define BREAKPOINT() ((void)0)
#elif defined(BREAK_ON_ERROR) 
# if defined(WIN32)
#  define BREAKPOINT() __asm{ int 3 }
# else
#  define BREAKPOINT() *((int*)NULL) = 0 /* core dump */
# endif /* defined(WIN32) */
#else
# define BREAKPOINT() ((void)0)
#endif /* MQ_NDEBUG_BREAKPOINT */

# define MEM_BREAKPOINT() ((void)0)

/**
 * The ERRCHK macro allows a function to cleanup (e.g. deallocate locally
 * allocated memory) if a function call returns an error.  The caller must
 * locally declare "iMQError errorCode", and a label Cleanup which will
 * be jumped to if an error occurs.
 */
#define ERRCHK(funcCall)                          \
  if ((errorCode = (funcCall)) == IMQ_SUCCESS) {  \
  } else {                                        \
    goto Cleanup;                                 \
  }

#ifdef MQ_NO_ERROR_TRACE
#define ERRCHK_TRACE(funcCall, m, t) ERRCHK(funcCall)
#define MQ_ERRCHK_TRACE(funcCall, m) ERRCHK(funcCall)
#else
#define ERRCHK_TRACE(funcCall, m, t)  \
  if ((errorCode = (funcCall)) == IMQ_SUCCESS) {  \
  } else {                                        \
    ERROR_TRACE( m, t, errorCode );               \
    goto Cleanup;                                 \
  }
#define MQ_ERRCHK_TRACE(funcCall, m) ERRCHK_TRACE(funcCall, m, "mq")
#endif

/** 
 * The NSPRCHK macro 
 *
 */
#define NSPRCHK(funcCall)                                \
  if ((funcCall) == PR_SUCCESS) {                        \
  } else {                                               \
    ERRCHK( NSPR_ERROR_TO_IMQ_ERROR( PR_GetError() ) );  \
  }

#ifdef MQ_NO_ERROR_TRACE
#define NSPRCHK_TRACE(funcCall, m, t)  NSPRCHK(funcCall)
#else
#define NSPRCHK_TRACE(funcCall, m, t)                    \
  if ((funcCall) == PR_SUCCESS) {                        \
  } else {                                               \
    ERRCHK_TRACE(NSPR_ERROR_TO_IMQ_ERROR( PR_GetError()), m, t);  \
  }
#endif 

/**
 * The MEMCHK macro is similar to the ERRCHK macro.  It allows a function to
 * cleanup (e.g. deallocate locally allocated memory) if a memory allocation
 * failed (i.e. NULL is returned).  The caller must locally declare "iMQError
 * errorCode", and a label Cleanup which will be jumped to if an error occurs.  
 */
#define MEMCHK(memAllocation)                     \
  if ((memAllocation) != NULL) {                  \
  } else {                                        \
    fprintf(stderr, "new() failed at %s:%d\n", __FILE__, __LINE__);  \
    ERRCHK(IMQ_OUT_OF_MEMORY);                    \
  }

#define PRINT_ERROR_IF_OOM(memAllocation)         \
  if ((memAllocation) == NULL) {                  \
    fprintf(stderr, "Memory allocation failed at %s:%d\n", __FILE__, __LINE__);  \
  }

#define RETURN_ERROR_IF_OOM(memAllocation, errorCode)         \
  if ((memAllocation) == NULL) {                  \
    fprintf(stderr, "Memory allocation failed at %s:%d\n", __FILE__, __LINE__);  \
    return (errorCode); \
  }

/**
 * The CNDCHK macro is similar to the ERRCHK macro.  It allows a function to
 * cleanup (e.g. deallocate locally allocated memory) if some condition is true.
 * If conditionToCheck evaluates to TRUE, then errorToReturn is placed in
 * errorCode.  The caller must locally declare "iMQError errorCode", and a label
 * Cleanup which will be jumped to if an error occurs.  
 */
#define CNDCHK(conditionToCheck,errorToReturn)    \
  if (!(conditionToCheck)) {                      \
  } else {                                        \
    ERRCHK( errorToReturn );                      \
  }

#ifdef MQ_NO_ERROR_TRACE
#define CNDCHK_TRACE(conditionToCheck,errorToReturn, m, t)  CNDCHK(conditionToCheck,errorToReturn)
#define MQ_CNDCHK_TRACE(conditionToCheck,errorToReturn, m)  CNDCHK(conditionToCheck,errorToReturn)
#else
#define CNDCHK_TRACE(conditionToCheck,errorToReturn, m, t)    \
  if (!(conditionToCheck)) {                      \
  } else {                                        \
    ERRCHK_TRACE( errorToReturn, m, t );          \
  }
#define MQ_CNDCHK_TRACE(conditionToCheck,errorToReturn, m)  CNDCHK_TRACE(conditionToCheck,errorToReturn, m, "mq")
#endif

/**
 * The NULLCHK macro is similar to the ERRCHK and MEMCHK macros.  If ptr is
 * NULL, then IMQ_NULL_PTR_ARG is 
 */
#define NULLCHK(ptr)                                 \
  IMQ_BEGIN_MACRO                                    \
    if ((ptr) == NULL) {                             \
      BREAKPOINT();                                  \
      ERRCHK( IMQ_NULL_PTR_ARG );                    \
    }                                                \
  IMQ_END_MACRO


/** 
 * RETURN_UNEXPECTED_ERROR is used to return an unexpected error that occurs.
 * If debugging is enabled, then it prints an error message before returning the
 * error.  And on Windows the program will hit a breakpoint so the program state
 * can be examined.  
 *
 * Warning errorCode cannot have any side effects.  See warning above.
 */
#ifdef NDEBUG
# define RETURN_UNEXPECTED_ERROR(errorcode)   \
    return (errorcode)
#else
# define RETURN_UNEXPECTED_ERROR(errorcode)              \
    IMQ_BEGIN_MACRO                                      \
      fprintf(stderr, "Unexpected error %d at %s:%d\n",  \
                      (errorcode), __FILE__, __LINE__);  \
      return (errorcode);                                \
    IMQ_END_MACRO                           
#endif

/**
 * If ptr is NULL, then this macro returns an error.
 */
#define RETURN_ERROR_IF_NULL(ptr)                    \
  IMQ_BEGIN_MACRO                                    \
    if ((ptr) == NULL) {                             \
      MEM_BREAKPOINT();                              \
      return IMQ_NULL_PTR_ARG;                       \
    }                                                \
  IMQ_END_MACRO


/**
 * If expr generates an iMQError, then return that error value.  This assumes
 * that i_m_q_E_r_r_o_r__ is not used in the outside scope.
 */
#define RETURN_IF_ERROR(expr)                               \
  IMQ_BEGIN_MACRO                                           \
    iMQError i_m_q_E_r_r_o_r__;                             \
    if ((i_m_q_E_r_r_o_r__ = (expr)) != IMQ_SUCCESS) {      \
      return i_m_q_E_r_r_o_r__;                             \
    }                                                       \
  IMQ_END_MACRO

#ifdef MQ_NO_ERROR_TRACE
#define RETURN_IF_ERROR_TRACE(expr, m, t)  RETURN_IF_ERROR(expr) 
#else
#define RETURN_IF_ERROR_TRACE(expr, m, t)  \
  IMQ_BEGIN_MACRO                                           \
    iMQError i_m_q_E_r_r_o_r__;                             \
    if ((i_m_q_E_r_r_o_r__ = (expr)) != IMQ_SUCCESS) {      \
      ERROR_TRACE(m, t, i_m_q_E_r_r_o_r__);                 \
      return i_m_q_E_r_r_o_r__;                             \
    }                                                       \
  IMQ_END_MACRO
#endif

/**
 * If expr generates an iMQError, then return that error value.  If debugging is
 * enabled, then display an error message.  This assumes that i_m_q_E_r_r_o_r__
 * is not used in the outside scope.  
 */
#define RETURN_IF_UNEXPECTED_ERROR(expr)                    \
  IMQ_BEGIN_MACRO                                           \
    iMQError i_m_q_E_r_r_o_r__;                             \
    if ((i_m_q_E_r_r_o_r__ = (expr)) != IMQ_SUCCESS) {      \
      RETURN_UNEXPECTED_ERROR(i_m_q_E_r_r_o_r__);           \
    }                                                       \
  IMQ_END_MACRO


/**
 * If ptr (e.g. the result of a memory allocation) is NULL, this macro returns
 * an error.  
 */
#define RETURN_IF_OUT_OF_MEMORY(ptr)                     \
  IMQ_BEGIN_MACRO                                        \
    if ((ptr) == NULL) {                                 \
      MEM_BREAKPOINT();                                  \
      return IMQ_NULL_PTR_ARG;                           \
    }                                                    \
  IMQ_END_MACRO

/**
 * If expr is true, this macro returns errorCode.  Otherwise it does nothing.
 */
#define RETURN_ERROR_IF(expr,errorCode)   \
  IMQ_BEGIN_MACRO                         \
    if (expr) {                           \
      return (errorCode);                 \
    }                                     \
  IMQ_END_MACRO

/** 
 * If expr is true, this macro returns errorCode.  But first it prints out the
 * error code with the current file and line number.
 */
#define RETURN_UNEXPECTED_ERROR_IF(expr,errorCode)   \
  IMQ_BEGIN_MACRO                                    \
    if (expr) {                                      \
      RETURN_UNEXPECTED_ERROR( errorCode );          \
    }                                                \
  IMQ_END_MACRO

/**
 * This macro converts an NSPR error into an iMQError.
 */
#define NSPR_ERROR_TO_IMQ_ERROR(error) (error)

/** 
 * This macro is used to get and return the NSPR error code. It is called after
 * an NSPR function has returned an error.
 */
#define RETURN_NSPR_ERROR_CODE()                              \
  IMQ_BEGIN_MACRO                                             \
    return NSPR_ERROR_TO_IMQ_ERROR( PR_GetError() );          \
  IMQ_END_MACRO

/**
 * This macro returns an iMQError code if the NSPR function call represented by
 * 'expr' returns an error.  
 */
#define RETURN_IF_NSPR_ERROR(expr)                                \
  IMQ_BEGIN_MACRO                                                 \
    PRInt32 n_s_p_r_S_u_c_c_e_s_s__;                              \
    if ((n_s_p_r_S_u_c_c_e_s_s__ = (expr)) == PR_FAILURE) {       \
      RETURN_NSPR_ERROR_CODE();                                   \
    }                                                             \
  IMQ_END_MACRO


/**
 * This macro ensures that the object whose method is being called is
 * valid, i.e.  it hasn't been deleted. */

#if !defined(NDEBUG) && !defined(MQ_NDEBUG_OBJECT_VALIDITY)
# define DO_OBJECT_VALIDITY_TEST
#else
# undef DO_OBJECT_VALIDITY_TEST
#endif
#ifdef DO_OBJECT_VALIDITY_TEST
#  define CHECK_OBJECT_VALIDITY()                                      \
    IMQ_BEGIN_MACRO                                                    \
      if (this->isValidObject()) {                                     \
      } else {                                                         \
        LOG_SEVERE(( CODELOC, CODE_ERROR_LOG_MASK, NULL_CONN_ID,       \
                     IMQ_REFERENCED_FREED_OBJECT_ERROR,                \
                     "The freed object 0x%p was referenced", this));   \
        ASSERT( this->isValidObject() );                               \
        BREAKPOINT();                                                  \
      }                                                                \
    IMQ_END_MACRO
#else
#  define CHECK_OBJECT_VALIDITY()  ((void)0)
#endif /* DO_OBJECT_VALIDITY_TEST */

#ifndef NDEBUG
# define MQ_ERROR_TRACE_DEBUG(m, e)  MQ_ERROR_TRACE(m, e) 
# define ERROR_TRACE_DEBUG(m, t, e)  ERROR_TRACE(m, t, e) 
#else
# define MQ_ERROR_TRACE_DEBUG(m, e) ((void)0) 
# define ERROR_TRACE_DEBUG(m, t, e) ((void)0) 
#endif

#ifdef MQ_NO_ERROR_TRACE
#define ERROR_TRACE(m, t, e) ((void)0)
#define ERROR_VTRACE(args)   ((void)0)
#define MQ_ERROR_TRACE(m, e) ((void)0)
#else
#define ERROR_TRACE(m, t, e) \
   IMQ_BEGIN_MACRO                                             \
   if (setErrorTraceElement(m, CODELOC, t, e) != PR_SUCCESS) { \
     LOG_WARNING(( CODELOC, ERROR_TRACE_LOG_MASK, NULL_CONN_ID,\
                   NSPR_ERROR_TO_IMQ_ERROR(PR_GetError()),     \
            "setErrorTraceElement failed %d", PR_GetError() ));\
   } \
   IMQ_END_MACRO
#define ERROR_VTRACE(args) \
   IMQ_BEGIN_MACRO                                             \
   if (((setVErrorTraceElement) args) != PR_SUCCESS) { \
     LOG_WARNING(( CODELOC, ERROR_TRACE_LOG_MASK, NULL_CONN_ID,\
                   NSPR_ERROR_TO_IMQ_ERROR(PR_GetError()),     \
            "setVErrorTraceElement failed %d", PR_GetError() ));\
   } \
   IMQ_END_MACRO
#define MQ_ERROR_TRACE(m, e) ERROR_TRACE(m, "mq", e)
#endif

#ifdef MQ_NO_ERROR_TRACE
#define CLEAR_ERROR_TRACE(all)  ((void)0)
#else
#define CLEAR_ERROR_TRACE(all) \
   IMQ_BEGIN_MACRO                                             \
   if (clearErrorTrace(all) != PR_SUCCESS) {                   \
     LOG_WARNING(( CODELOC, ERROR_TRACE_LOG_MASK, NULL_CONN_ID,\
                   NSPR_ERROR_TO_IMQ_ERROR(PR_GetError()),     \
               "clearErrorTrace() failed %d", PR_GetError() ));\
   } \
   IMQ_END_MACRO
#endif

/** This macro is placed in methods/functions that are not implemented. */
#define UNIMPLEMENTED(x) 

/** This macro is used for a variable that is intentionally not used.  It keeps
    the compiler from giving us a warning. */
#define UNUSED(x) (void)(x)

#ifdef __cplusplus
}
#endif

#endif /* UTILITYMACROS_H */


