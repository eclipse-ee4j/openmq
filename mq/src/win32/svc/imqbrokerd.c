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
 * @(#)imqbrokerd.c	1.5 07/02/07
 */ 

/*
 * Front-end program to MQ broker. All this does is call
 * 	IMQ_HOME/bin/imqbrokersvc -console
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
        "dummy.jar"
        };
int nclasspath_entries = 0;

void brokerParseArgs (MqEnv *me, char *argv[], int argc)
{
    argv++; argc--;
    while (argc > 0) {
        /* We don't recognize the option, pass it on to application */
        me->application_argv[me->application_argc] = _strdup(*argv);
        me->application_argc++;
        argv++; argc--;
    }
}


void main(int argc, char** argv)
{
    char cmdLine[1024];
    char brokerCmd[512];
    char *p;
    DWORD exitCode = 0;
    MqEnv	me;
    int i;

    p = getenv("OS");
    if ((p == NULL) || (strcmp(p, "Windows_NT") != 0)) {
	printf("The MQ broker requires Windows NT or Windows 2000\n");
	exit(1);
    }

    MqAppInitMqEnv(&me, "");

    brokerParseArgs (&me, argv, argc);

    if (MqAppInitializeNoJava(&me) < 0) {
	exit (1);
    }

    sprintf(brokerCmd, "\"%s\\bin\\imqbrokersvc\"", me.imqhome);

    /* Copy Java command and command line arguments into command line */
    strcpy(cmdLine, brokerCmd);

    /*
     * Append -console
     */
    strcat(cmdLine, " -console");

    /*
     * Append any other options passed in
     */
    for (i = 0; i < me.application_argc; i++) {
        strcat(cmdLine, " ");
        strcat(cmdLine, "\"");
        strcat(cmdLine, me.application_argv[i]);
        strcat(cmdLine, "\"");
    }

    exitCode = MqAppRunCmd(cmdLine);

    exit(exitCode);
}
