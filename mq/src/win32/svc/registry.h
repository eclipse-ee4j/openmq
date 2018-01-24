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
 * @(#)registry.h	1.10 10/17/07
 */ 

#ifndef _REGISTRY_H
#define _REGISTRY_H

#define JREHOME_KEY    "JREHome"
#define JVMARGS_KEY     "JVMArgs"
#define SERVICEARGS_KEY "ServiceArgs"
/* ####jes-windev#### start ## changing the SERVICE_NAME from iMQ_Broker to MQ_Broker and DISPLAY_NAME from iMQ Broker to Message Queue Broker */ 
#define SERVICE_NAME                "MQ5.1_Broker"
#define DISPLAY_NAME                "Message Queue 5.1 Broker"
/* ####jes-windev#### end */
#define PARAM_KEY_PATH  "SYSTEM\\CurrentControlSet\\Services\\" SERVICE_NAME "\\Parameters"

#ifdef __cplusplus
extern "C" {
#endif

extern char *vm_libs[];
extern int nvm_libs;

/************************************************************************
 *
 * saveStringInRegistry()
 *
 * value - Char array holding '\0' terminated string to save
 * value_size - size of 'value' parameter including terminating '\0'
 * key - subKey to hold value in. E.g. "VMArgs"
 *
 * Returns
 *     ERROR_SUCCESS on success
 *     Winerror.h error on failure
 ************************************************************************/
extern int saveStringInRegistry(const char *value, long value_size, const char* key);

/************************************************************************
 *
 * getStringFromRegistry()
 *
 * value - Char array to place string value in
 * value_size - size of 'value' parameter including terminating '\0'. 
 *              Upon return this will contain the number of bytes of data
 *              retrieved.
 * key - subKey to get value from. E.g. "VMArgs"
 *
 * Returns
 *     ERROR_SUCCESS on success
 *     Winerror.h error on failure
 ************************************************************************/
extern int getStringFromRegistry(char *value, long *value_size, const char* key);
extern int getAnyStringFromRegistry(const char path, char *value, long *value_size, const char* key);

#ifdef __cplusplus
}
#endif

#endif
