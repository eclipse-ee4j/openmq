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
 * @(#)jmqsvcadmin.c	1.12 07/02/07
 */ 

#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <process.h>
#include <io.h>

#include "registry.h"
#define JMQ_BROKER_SERVICE_EXE_NAME	"imqbrokersvc"

int installService(LPCTSTR serviceName, LPCTSTR displayName,
					LPCTSTR serviceExe);
VOID removeService(LPCTSTR serviceName);
VOID queryService(LPCTSTR serviceName);
char * getStartTypeString(DWORD startType);
VOID printUsage(char* cmdName);
VOID getSysErrorText(char *buf, int bufSize, DWORD errorCode);
VOID printError(char *string, DWORD errorCode);
int validJRE(char *jre_home);

char jreHome[1024];
char vmArgs[1024];
char brokerArgs[1024];

/*
 * Main routine for service admin utility.
 */
VOID main(int argc, char** argv) {
    char *cmd = NULL;
    char *option = NULL;
    char buf[2048];
    int  installing = 0;

    memset(jreHome, '\0', sizeof(jreHome));
    memset(vmArgs, '\0', sizeof(vmArgs));
    memset(brokerArgs, '\0', sizeof(brokerArgs));

    cmd = argv[0];

    argv++; argc--;

    if (argc == 0) {
        printUsage(cmd);
    }

    while (argc > 0)  {
        option = *argv;

        if (!strcmp(option, "-help") ||
            !strcmp(option, "-h") ||
            !strcmp(option, "-H") ||
            !strcmp(option, "-?")) {
            printUsage(cmd);
        } else if (!strcmp(option, "-install") ||
                   !strcmp(option, "install")) {
            char *slash_loc;

            installing = 1;

            /*
             * Construct the path to the binary that will
             * be used for the broker service. The binary
             * will be named by the JMQ_BROKER_SERVICE_EXE_NAME
             * macro and needs to reside in the same directory
             * as this application.
             */
            GetModuleFileName(0, buf, sizeof(buf));
            slash_loc = strrchr(buf, '\\') + 1;
            strcpy(slash_loc, JMQ_BROKER_SERVICE_EXE_NAME);
    
        } else if (!strcmp(option, "-remove") ||
                   !strcmp(option, "remove")) {
            /*
             * Remove NT service
             */
            removeService(SERVICE_NAME);
        } else if (!strcmp(option, "-q") ||
                   !strcmp(option, "-query") ||
                   !strcmp(option, "query")) {
            /*
             * Query the NT service
             */
            queryService(SERVICE_NAME);
        } else if (!strcmp(option, "-javahome") ||
                   !strcmp(option, "-jrehome")) {
            /* Save jreHome */
            argv++; argc--;
            if (argc > 0) {
                strncpy(jreHome, *argv, sizeof(jreHome) - 32);
                if (strcmp(option, "-javahome") == 0) {
                    strcat(jreHome, "\\jre");
                }
                if (!validJRE(jreHome)) {
                    exit(1);
                }
            }
        } else if (!strcmp(option, "-vmargs")) {
            /* Save vmargs */
            argv++; argc--;
            if (argc > 0) {
                strncpy(vmArgs, *argv, sizeof(vmArgs) - 1);
            }
        } else if (!strcmp(option, "-args")) {
            /* Save broker arguments */
            argv++; argc--;
            if (argc > 0) {
                strncpy(brokerArgs, *argv, sizeof(brokerArgs) - 1);
            }
        } else  {
            printf("Unknown option %s\n", option);
            /* unknown option */
            printUsage(cmd);
        }
        argv++; argc--;
    }

    if (installing) {
        char tmp_buf[1024];
        int result;
        /*
         * Install NT service
         */
        if ( installService(SERVICE_NAME, DISPLAY_NAME, buf) < 0) exit(1);

        /* Save location of jrehome, vmargs and brokerargs in registry */
        if (*jreHome != '\0') {
            if ((result = saveStringInRegistry(jreHome, sizeof(jreHome),
                                        JREHOME_KEY)) != ERROR_SUCCESS) {
                sprintf(tmp_buf, "Could not set %s to %s in registry\n",
                                  JREHOME_KEY, jreHome);
	        printError(tmp_buf, result);
            }
        }
        if (*vmArgs != '\0') {
            if ((result = saveStringInRegistry(vmArgs, sizeof(vmArgs),
                                        JVMARGS_KEY)) != ERROR_SUCCESS) {
                sprintf(tmp_buf, "Could not set %s to %s in registry\n",
                                  JVMARGS_KEY, vmArgs);
	        printError(tmp_buf, result);
            }
        }
        if (*brokerArgs != '\0') {
            saveStringInRegistry(brokerArgs, sizeof(brokerArgs),
                SERVICEARGS_KEY);
            if ((result = saveStringInRegistry(brokerArgs, sizeof(brokerArgs),
                                        SERVICEARGS_KEY)) != ERROR_SUCCESS) {
                sprintf(tmp_buf, "Could not set %s to %s in registry\n",
                                  SERVICEARGS_KEY, brokerArgs);
	        printError(tmp_buf, result);
            }
        }
    }
}

/*
 * Installs the binary 'serviceExe' as a service named
 * by 'serviceName'.
 */
int installService(LPCTSTR serviceName, LPCTSTR displayName,
						LPCTSTR serviceExe) {
    SC_HANDLE	brokerService;
    SC_HANDLE	scm;
    char	tmp[512];

    /*
     * Open connection to Service Control Manager
     */
    scm = OpenSCManager(NULL, NULL, SC_MANAGER_ALL_ACCESS);

    if (scm != NULL)  {
	/*
	 * Create the service
	 * The service will be created having 'Manual' startup.
	 */
        brokerService = CreateService( 
				scm, 
				serviceName, 
				displayName, 
				SERVICE_ALL_ACCESS, 
				SERVICE_WIN32_OWN_PROCESS, 
				SERVICE_AUTO_START, 
				SERVICE_ERROR_NORMAL, 
				serviceExe, 
				NULL, 
				NULL, 
				NULL, 
				NULL, 
				NULL);

        if (brokerService != NULL) {
	    printf("Installation of service %s successful.\n",
			displayName);
        } else {
	    /*
	     * Error condition - print error
	     */
	    sprintf(tmp, "Failed to install service %s", displayName);
	    printError(tmp, GetLastError());

            CloseServiceHandle(scm);
	    return -1;
        }

	/*
	 * Close SCM handle
	 */
        CloseServiceHandle(brokerService);
        CloseServiceHandle(scm);
    } else  {
	/*
	 * Error condition - print error
	 */
	sprintf(tmp, "Failed to install service %s", displayName);
	printError(tmp, GetLastError());
	return -1;
    }

    return 0;
}

/*
 * Removes service specified by 'serviceName'.
 */
VOID removeService(LPCTSTR serviceName) {
    SC_HANDLE	brokerService;
    SC_HANDLE	scm;
    BOOL	ret;
    char	tmp[512];

    scm = OpenSCManager(NULL, NULL, SC_MANAGER_ALL_ACCESS);

    if (scm != NULL)  {
        brokerService = OpenService(scm, serviceName, SERVICE_ALL_ACCESS);

        if (brokerService == NULL) {
	    /*
	     * Error condition - print error
	     */
	    sprintf(tmp, "Failed to remove service %s", DISPLAY_NAME);
	    printError(tmp, GetLastError());

            CloseServiceHandle(scm);
	    return;
        }

        ret = DeleteService(brokerService);

        if (ret) {
	    printf("Service %s successfully removed.\n", DISPLAY_NAME);
        } else {
	    /*
	     * Error condition - print error
	     */
	    sprintf(tmp, "Failed to remove service %s", DISPLAY_NAME);
	    printError(tmp, GetLastError());
        }

        CloseServiceHandle(brokerService);
        CloseServiceHandle(scm);
    } else  {
	/*
	 * Error condition - print error
	 */
	sprintf(tmp, "Failed to remove service %s", DISPLAY_NAME);
	printError(tmp, GetLastError());
    }
}

/*
 * Queries the service specified by 'serviceName'.
 * This feature is currently not exposed to users.
 */
VOID queryService(LPCTSTR serviceName) {
    SC_HANDLE	brokerService;
    SC_HANDLE	scm;
    BOOL	ret;
    char	tmp[512];
    DWORD	bytesNeeded = 0;
    LPQUERY_SERVICE_CONFIG	svcCfgBuf = {0};

    scm = OpenSCManager(NULL, NULL, GENERIC_READ);

    if (scm != NULL)  {
        brokerService = OpenService(scm, serviceName, SERVICE_QUERY_CONFIG);

        if (brokerService == NULL) {
	    DWORD	err = GetLastError();

	    /*
	     * Error condition - print error
	     */
	    if (err == ERROR_SERVICE_DOES_NOT_EXIST)  {
	        printf("Service %s is not installed.\n", DISPLAY_NAME);
	    } else  {
	        sprintf(tmp, "Failed to query service %s", DISPLAY_NAME);
	        printError(tmp, err);
	    }

            CloseServiceHandle(scm);
	    return;
        }

	ret = QueryServiceConfig(brokerService, 0, 0, &bytesNeeded);
	svcCfgBuf = (LPQUERY_SERVICE_CONFIG)LocalAlloc(LPTR, bytesNeeded);

	ret = QueryServiceConfig(brokerService, svcCfgBuf, bytesNeeded, 
			&bytesNeeded);

        if (ret) {
            long n = 0;

	    printf("Service %s is installed.\n", DISPLAY_NAME);
	    printf("Display name: %s\n", svcCfgBuf->lpDisplayName);
	    printf("Start Type: %s\n", 
			getStartTypeString(svcCfgBuf->dwStartType));
	    printf("Binary location: %s\n", svcCfgBuf->lpBinaryPathName);

            n = sizeof(jreHome);
            getStringFromRegistry(jreHome, &n, JREHOME_KEY);
            n = sizeof(vmArgs);
            getStringFromRegistry(vmArgs, &n, JVMARGS_KEY);
            n = sizeof(brokerArgs);
            getStringFromRegistry(brokerArgs, &n, SERVICEARGS_KEY);

            if (*jreHome != '\0') {
	        printf("JREHome: %s\n", jreHome);
            }
            if (*vmArgs != '\0') {
	        printf("VM Args: %s\n", vmArgs);
            }
            if (*brokerArgs != '\0') {
	        printf("Broker Args: %s\n", brokerArgs);
            }

	    /*
	     * Free space allocated for buffer.
	     */
	    LocalFree(svcCfgBuf);
        } else {
	    /*
	     * Error condition - print error
	     */
	    sprintf(tmp, "Failed to query service %s", DISPLAY_NAME);
	    printError(tmp, GetLastError());
        }

        CloseServiceHandle(brokerService);
        CloseServiceHandle(scm);
    } else  {
	/*
	 * Error condition - print error
	 */
	sprintf(tmp, "Failed to query service %s", DISPLAY_NAME);
	printError(tmp, GetLastError());
    }
}

/*
 * Returns a string describing the service start type.
 */
char * getStartTypeString(DWORD startType)  {
    switch (startType)  {
    /*
     * SERVICE_BOOT_START/SERVICE_SYSTEM_START is for
     * device drivers. It is included here just for 
     * completeness.
     */
    case SERVICE_BOOT_START:
	return ("SERVICE_BOOT_START");
    break;
    case SERVICE_SYSTEM_START:
	return ("SERVICE_SYSTEM_START");
    break;


    case SERVICE_AUTO_START:
	return ("Automatic");
    break;

    case SERVICE_DEMAND_START:
	return ("Manual");
    break;

    case SERVICE_DISABLED:
	return ("Disabled");
    break;

    default:
	return ("Unknown start type");
    }
}


/*
 * Prints help usage text.
 */
VOID printUsage(char *cmdName) {
printf("Usage:\n");
printf("%s -h          Display help\n", cmdName);
printf("%s query       Query information about the service\n", cmdName);
printf("%s remove      Remove the service\n", cmdName);
printf("%s install [-javahome <javahome> | -jrehome <jrehome>]\n", cmdName);
printf("                    [-vmargs <vmargs>] [-args <brokerArgs>]\n");
printf("    Install the MQ Broker service. Optional arguments are:\n");
printf("        -javahome   Specify the location of the Java Runtime to use\n");
printf("                    when running the Broker service. <javahome> must\n");
printf("                    point to a Java 2 compatible JDK. By default the\n");
printf("                    service will run with the JRE bundled with MQ.\n");
printf("                    Example: -javahome d:\\jdk1.2.2\n");
printf("        -jrehome    Same as -javahome, but <jrehome> points to a Java 2\n");
printf("                    compatible JRE (instead of JDK).\n");
printf("                    Example: -jrehome d:\\jre\\1.3\n");
printf("        -vmargs     Specify additional arguments to pass to the Java VM\n");
printf("                    that is running the broker service.\n");
printf("                    Example: -vmargs \"-Xms16m -Xmx128m\"\n");
printf("        -args       Specify additional command line arguments to pass\n");
printf("                    to the Broker service. <brokerArgs> may be any\n");
printf("                    valid Broker command line option.\n");
printf("                    For example: -args \"-passfile d:\\imqpassfile\"\n");
    exit(1);
}

/*
 * Prints error message using the passed string and a system
 * message (based on the error code passed).
 */
VOID printError(char *string, DWORD errorCode) {
    char	buf[1024];

    getSysErrorText(buf, 1024, errorCode);
    printf("%s: %s", string, buf);
}

/*
 * Fills 'buf' with the system message related to the passed
 * error code.
 * 
 * The passed buffer needs to point to pre-allocated space
 * of size 'bufSize' characters.
 */
VOID getSysErrorText(char *buf, int bufSize, DWORD errorCode) {
    if (!FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM,
		0, errorCode, 0, buf, bufSize, 0))  {
	sprintf(buf, "Unknown error code: %x" + errorCode);
    }
}

/*
 * Return 1 if the JRE is valid, 0 if it is not.
 */
int validJRE(char *jre_home) {
    char buf[1024];
    int n;

    /* Make sure jre_home exists */
    if (_access(jre_home, 00) < 0) {
        printf("Couldn't find Java runtime '%s'", jre_home);
        return (0);
    }

    /* Make sure we can find a VM using it */
    for (n = 0; n < nvm_libs; n++) {
        strncpy(buf, jre_home, sizeof(buf) - 80);
        strcat(buf, "\\bin\\");
        strcat(buf, vm_libs[n]);

        /* Make sure we can find a VM using jre_home */
        if (_access(buf, 00) >= 0) {
            return (1);
        }
    }

    printf("'%s' does not appear to be a valid Java Runtime.\n", jre_home);
    printf("Couldn't find any of the following Java VMs:\n");
    for (n = 0; n < nvm_libs; n++) {
        printf("    %s\n", vm_libs[n]);
    }
    return (0);
}

