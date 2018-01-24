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
 * @(#)imqusermgr.c	1.5 07/02/07
 */ 

/*
 * Front-end program to MQ file based user repository management tool
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
char *classpath_entries[] = {
        "imqbroker.jar"
        };
int nclasspath_entries = sizeof (classpath_entries) / sizeof(char *);

char *main_class = "com.sun.messaging.jmq.jmsserver.auth.usermgr.UserMgr";

void main(int argc, char** argv)
{
    char cmdLine[1024];
    char *p;
    DWORD exitCode = 0;
    MqEnv	me;

    p = getenv("OS");
    if ((p == NULL) || (strcmp(p, "Windows_NT") != 0)) {
	printf("The MQ User Manager requires Windows NT or Windows 2000\n");
	exit(1);
    }

    MqAppInitMqEnv(&me, main_class);

    MqAppParseArgs(&me, argv, argc);

    if (MqAppInitialize(&me, classpath_entries, nclasspath_entries, FALSE, FALSE) < 0) {
	exit (1);
    }

    MqAppCreateJavaCmdLine(&me, TRUE, cmdLine);

    exitCode = MqAppRunCmd(cmdLine);

    exit(exitCode);
}
