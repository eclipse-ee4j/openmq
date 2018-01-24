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
 * @(#)registry.c	1.7 07/02/07
 */ 

#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <process.h>
#include <winreg.h>
#include "registry.h"

// VMs to use. Order is most desireable to least desireable.
// Relative paths are assumed to be relative to jrehome/bin
char *vm_libs[] = {
        "server\\jvm.dll",
        "hotspot\\jvm.dll",
        "client\\jvm.dll",
        "classic\\jvm.dll",
        };
int nvm_libs = sizeof (vm_libs) / sizeof(char *);

int saveStringInRegistry(const char *value, long value_size, const char* name)
{
    long result;
    HKEY hKey;
    DWORD disposition;

    /*
     * Get a handle to the service parameters key. It will be something like
     * HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\IMQBroker\Parameters
     */
    result = RegCreateKeyEx(
        HKEY_LOCAL_MACHINE,
        PARAM_KEY_PATH,
        0,
        "LocalSystem",
        REG_OPTION_NON_VOLATILE,
        KEY_ALL_ACCESS,
        NULL,
        (PHKEY)&hKey,
        (LPDWORD)&disposition);

    if (result != ERROR_SUCCESS) {
        return result;
    }

    /* Set the subkey and value */
    result = RegSetValueEx(
        hKey,
        name,
        0,
        REG_SZ,
        value,
        value_size);

    if (result != ERROR_SUCCESS) {
        return result;
    }

    RegCloseKey(hKey);
}

int getStringFromRegistry(char *value, long *value_size_p, const char* name)
{
    long result;
    HKEY hKey;
    long type;

    /*
     * Get a handle to the service parameters key. It will be something like
     * HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\IMQBroker\Parameters
     */
    result = RegOpenKeyEx(
        HKEY_LOCAL_MACHINE,
        PARAM_KEY_PATH,
        0,
        KEY_READ,
        (PHKEY)&hKey
        );

    if (result != ERROR_SUCCESS) {
        return result;
    }

    /* Get the subkey and value */
    result = RegQueryValueEx(
        hKey,
        name,
        0,
        &type,
        value,
        value_size_p);

    if (result != ERROR_SUCCESS) {
        return result;
    }
}

int getAnyStringFromRegistry(const char * startpath, char *value, long *value_size_p, const char* name)
{
    long result;
    HKEY hKey;
    long type;

    /*
     * Get a handle to the service parameters key. It will be something like
     * HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\IMQBroker\Parameters
     */
    result = RegOpenKeyEx(
        HKEY_LOCAL_MACHINE,
        startpath,
        0,
        KEY_READ,
        (PHKEY)&hKey
        );

    if (result != ERROR_SUCCESS) {
        return result;
    }

    /* Get the subkey and value */
    result = RegQueryValueEx(
        hKey,
        name,
        0,
        &type,
        value,
        value_size_p);

    if (result != ERROR_SUCCESS) {
        return result;
    }

    RegCloseKey(hKey);
}
