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
 * @(#)imqkeytool.c	1.9 07/02/07
 */ 

/*
 * Front-end program to MQ key tool administration for generating SSL
 * key pairs. It calls JDK's keytool.
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

char keystore[MAX_PATH];
char aliasname[MAX_PATH];
BOOL do_broker = TRUE;

/*
 * Class path entries. Relative paths are assumed to be relative to
 * $imqhome/lib
 */
char *classpath_entries[] = {
        "dummy.jar"
        };
int nclasspath_entries = 0;


void printUsage ()
{
    printf(
	"\nusage:\n"
        "imqkeytool [-broker]\n"
        "   generates a keystore and self-signed certificate for the broker\n\n"
        "imqkeytool -servlet <keystore_location>\n"
        "   generates a keystore and self-signed certificate for the HTTPS\n"
        "   tunnel servlet, keystore_location specifies the name and location\n"
        "   of the keystore file\n\n");
}

void keyToolParseArgs (MqEnv *me, char *argv[], int argc)
{
    argv++; argc--;
    while (argc > 0) {
        if (strcmp(*argv, "-javahome") == 0) {
            argv++; argc--;
            if (argc > 0) {
                strncpy(me->jrehome, *argv, sizeof(me->jrehome) - 32);
                strcat(me->jrehome, "\\jre");
            }
        } else if (strcmp(*argv, "-jrehome") == 0) {
            argv++; argc--;
            if (argc > 0) {
                strncpy(me->jrehome, *argv, sizeof(me->jrehome));
            }
        } else if (strcmp(*argv, "-varhome") == 0) {
            argv++; argc--;
            if (argc > 0) {
                strncpy(me->imqvarhome, *argv, sizeof(me->imqvarhome));
            }
        } else if (strcmp(*argv, "-broker") == 0) {
	    do_broker = TRUE;
        } else if (strcmp(*argv, "-servlet") == 0) {
	    do_broker = FALSE;

            argv++; argc--;
	    if (argc > 0)  {
                strncpy(keystore, *argv, sizeof(keystore));
	    } else  {
		printf("Please specify keystore location for the -servlet option\n");
	        printUsage();
	        exit(1);
	    }
        } else {
	    printUsage();
	    exit(1);
        }
        argv++; argc--;
    }
}

void main(int argc, char** argv)
{
    char cmdLine[1024];
    char keytoolCmd[512];
    char *p;
    DWORD exitCode = 0;
    MqEnv	me;

    p = getenv("OS");
    if ((p == NULL) || (strcmp(p, "Windows_NT") != 0)) {
	printf("The MQ keytool requires Windows NT or Windows 2000\n");
	exit(1);
    }

    aliasname[0] = '\0';
    keystore[0] = '\0';

    MqAppInitMqEnv(&me, "");

    keyToolParseArgs (&me, argv, argc);

    if (MqAppInitialize(&me, classpath_entries, nclasspath_entries, FALSE, FALSE) < 0) {
	exit (1);
    }

    if (do_broker)  {
	strcpy(aliasname, "imq");
    } else  {
	strcpy(aliasname, "imqservlet");
    }

    /*
     * keystore is set only if -servlet is used (-servlet usage
     * is detected in keyToolParseArgs()), set the keystore to 
     * imqhome/etc/keystore
     */
    if (keystore[0] == '\0') {
        if (me.imqetchome[0] == '\0') {
            strcpy(keystore, me.imqhome);
            strcat(keystore, "\\etc");
        } else {
            strcpy(keystore, me.imqetchome);
        }
        strcat(keystore, "\\keystore");

    }

    sprintf(keytoolCmd, "\"%s\\bin\\keytool\"", me.jrehome);

    /* Copy Java command and command line arguments into command line */
    strcpy(cmdLine, keytoolCmd);

    /*
     * Append rest of keytool options including alias name and keystore
     * location.
     */
    strcat(cmdLine, " -v -genkey -keyalg \"RSA\" -alias ");
    strcat(cmdLine, aliasname);
    strcat(cmdLine, " -keystore ");
    strcat(cmdLine, "\"");
    strcat(cmdLine, keystore);
    strcat(cmdLine, "\"");

    printf("Keystore: %s\n", keystore);
    if (do_broker)  {
	printf("Generating keystore for the broker ...\n");
    } else  {
	printf("Generating keystore for the HTTPS tunnel servlet ...\n");
    }

    exitCode = MqAppRunCmd(cmdLine);

    if (!do_broker && (exitCode == 0))  {
	printf("Make sure the keystore is accessible and readable by the HTTPS tunnel servlet.\n");
    }

    exit(exitCode);
}
