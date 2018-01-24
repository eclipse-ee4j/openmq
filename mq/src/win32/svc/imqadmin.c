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
 * @(#)imqadmin.c	1.6 07/02/07
 */ 

/*
 * Front-end program to MQ Administration Console
 */

#include <windows.h>
#include <io.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <process.h>
#include <direct.h>
#include <errno.h>
#include "mqapp.h"

/*
 * Class path entries. Relative paths are assumed to be relative to
 * $imqhome/lib
 */
/* ###jes-windev### start ## bug 6267418 # adding ..\\..\\share\\lib\\jhall.jar to classpath  */
char *classpath_entries[] = {
        "imqadmin.jar",
        "fscontext.jar",
        "..\\..\\share\\lib\\jhall.jar",
        "jhall.jar",
        "help"
        };
/* ###jes-windev### end  */
int nclasspath_entries = sizeof (classpath_entries) / sizeof(char *);

char *main_class = "com.sun.messaging.jmq.admin.apps.console.AdminConsole";

void main(int argc, char** argv)
{
    char cmdLine[1024];
    DWORD exitCode = 0;
    MqEnv	me;

    MqAppInitMqEnv(&me, main_class);

    MqAppParseArgs(&me, argv, argc);

    if (MqAppInitialize(&me, classpath_entries, nclasspath_entries, FALSE, TRUE) < 0) {
	exit (1);
    }

    MqAppCreateJavaCmdLine(&me, TRUE, cmdLine);

    exitCode = MqAppRunCmd(cmdLine);

    exit(exitCode);
}
