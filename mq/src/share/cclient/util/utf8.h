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
 * @(#)utf8.h	1.3 06/26/07
 */ 

#ifndef TEMP_UTF8_H
#define TEMP_UTF8_H

#ifdef __cplusplus
extern "C" {
#endif


#include <stdio.h>
#include <string.h>
#include <ctype.h>

/*
 * UTF-8 routines (should these move into libnls?)
 */
/* number of bytes in character */
int  ldap_utf8len( const char* );

/* find next character */
char*  ldap_utf8next( char* );

/* find previous character */
char*  ldap_utf8prev( char* );

/* copy one character */
int  ldap_utf8copy( char* dst, const char* src );

/* total number of characters */
size_t  ldap_utf8characters( const char* );

/* get one UCS-4 character, and move *src to the next character */
unsigned long  ldap_utf8getcc( const char** src );

/* UTF-8 aware strtok_r() */
  // char*  ldap_utf8strtok_r( char* src, const char* brk, char** next);

/* like isalnum(*s) in the C locale */
  // int  ldap_utf8isalnum( char* s );
/* like isalpha(*s) in the C locale */
  // int  ldap_utf8isalpha( char* s );
/* like isdigit(*s) in the C locale */
  // int  ldap_utf8isdigit( char* s );
/* like isxdigit(*s) in the C locale */
  // int  ldap_utf8isxdigit(char* s );
/* like isspace(*s) in the C locale */
  // int  ldap_utf8isspace( char* s );


/* UTF8 related prototypes: put in the header file of your choice (ldap.h)*/
  // int ldap_has8thBit(const unsigned char *s);
  // unsigned char *ldap_utf8StrToLower(const unsigned char *s);
  // void ldap_utf8ToLower(unsigned char *s, unsigned char *d, int *ssz, int *dsz);
  // int ldap_utf8isUpper(unsigned char *s);
  // unsigned char *ldap_utf8StrToUpper(unsigned char *s);
  // void ldap_utf8ToUpper(unsigned char *s, unsigned char *d, int *ssz, int *dsz);
  // int ldap_utf8isLower(unsigned char *s);
  //int ldap_utf8casecmp(const unsigned char *s0, const unsigned char *s1);
  //int ldap_utf8ncasecmp(const unsigned char *s0, const unsigned char *s1, int n);


#ifdef __cplusplus
}
#endif

#endif
