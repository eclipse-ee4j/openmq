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
 * @(#)mqapp.h	1.7 07/19/07
 */ 

/*
 * Header file for MQ C applications
 */

#include <windows.h>

/*
 * Structure containing environment settings needed to run the MQ App.
 * The information here will be used to construct the command line.
 */
typedef struct {
    char *main_class;
    char *application_argv[128];
    int application_argc;
    int jvm_argc;
    char **jvm_argv;
    char imqhome[MAX_PATH];
    char imqvarhome[MAX_PATH];
    char imqlibhome[MAX_PATH];
    char imqetchome[MAX_PATH];
    char imqextjars[4096];
    char jrehome[MAX_PATH];
    char classpath[65536];
} MqEnv;

/*
 * Declarations for some functions used my MQ Apps. These are defined
 * in util.c
 */

/*
 * Do some initialization. This basically locates IMQ, the JDK, determines
 * the run classpath (for -cp later).
 *
 * The following fields in the MqEnv are updated.
 *  imqhome
 *  imqlibhome
 *  imqvarhome
 *  imqetchome
 *  imqextjars
 *  jrehome
 *  classpath
 *
 * All the fields above need to point to pre-allocated space.
 *
 * Params:
 *  me			ptr to MqEnv structure, must not be NULL.
 *  classpath_entries	Array containing entries relative to IMQ_HOME/lib that
 *			need to be added to the run classpath. This must not
 *			be NULL.
 *  nclasspath_entries  Number of classpath entries in classpath_entries.
 *  append_libjars	Boolean flag - if true the jars/zips in IMQ_HOME/lib/ext
 *			will be appended to the run classpath.
 *  append_classpath	Boolean flag - if true the value of CLASSPATH will
 *			be appended to the run classpath.
 */
extern int MqAppInitialize(MqEnv *me, char *classpath_entries[], int nclasspath_entries,
			BOOL append_libjars, BOOL append_classpath);

/*
 * Do only MQ related initialization - no java initialization. This basically locates IMQ
 * and sets the relevant pieces of info in the MqEnv structure.
 *
 * The following fields in the MqEnv are updated.
 *  imqhome
 *  imqlibhome
 *  imqvarhome
 *  imqetchome
 *  imqextjars
 *
 * All the fields above need to point to pre-allocated space.
 *
 * Params:
 *  me			ptr to MqEnv structure, must not be NULL.
 */
extern int MqAppInitializeNoJava(MqEnv *me);

/*
 * Parse generic application arguments, setting the relevant
 * fields in the MqEnv structure.
 *
 * The following fields in the MqEnv are updated.
 *  jrehome
 *  application_argv
 *  application_argc
 *
 * The application_argv field needs to point to pre-allocated
 * array of (char *) pointers.
 *
 * Params:
 *  me	 ptr to MqEnv structure, must not be NULL.
 *  argv argument vector
 *  argc number of elements in argv
 */
extern void MqAppParseArgs (MqEnv *me, char *argv[], int argc);

/*
 * Create the java command line based on information
 * in the MqEnv structure.
 *
 * No fields in the MqEnv are updated.
 *
 * Params:
 *  me		ptr to MqEnv structure, must not be NULL.
 *  set_varhome	Boolean flag that indicates whether "-Dimq.varhome"
 *		should be appended to the command line or not.
 *  cmdLine	buffer for storing command line. This
 *		points to pre allocated space.
 */
extern void MqAppCreateJavaCmdLine(MqEnv *me, BOOL set_varhome, char *cmdLine);

/*
 * Run the command, wait for it to exit,
 * and return the exit code.
 * A process is forked to run the command via
 * CreateProcess()
 *
 * Params:
 *  cmdLine	buffer for storing command line. This
 *		points to pre allocated space.
 */
extern DWORD MqAppRunCmd(char *cmdLine);

/*
 * Initialize MqEnv structure
 *
 * The following fields in the MqEnv are updated.
 *  imqhome
 *  imqlibhome
 *  imqvarhome
 *  imqetchome
 *  imqextjars
 *  jrehome
 *  classpath
 *  application_argc
 *  main_class
 *
 * All the string fields above need to point to pre-allocated space.
 *
 * Params:
 *  me		ptr to MqEnv structure, must not be NULL.
 *  main_class	string containing main class of application
 *	e.g. 
 *	"com.sun.messaging.jmq.admin.apps.console.AdminConsole"
 */
extern void MqAppInitMqEnv(MqEnv *me, char *main_class);

/*
 * Run the java command, wait for it to exit,
 * and return the exit code. This function uses
 * the VM invocation APIs in JNI.
 *
 * Params:
 *  me	ptr to MqEnv structure, must not be NULL.
 */
extern DWORD MqAppJNIRunJavaCmd(MqEnv *me);
