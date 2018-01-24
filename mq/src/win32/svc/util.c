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
 * @(#)util.c	1.17 07/19/07
 */ 

#include <windows.h>
#include <io.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include "mqapp.h"
#include "registry.h"

int SourceFile(const char *file);
int myisspace(const char c);
int mymkdir(const char *path);

#define IMQ_JAVAHOME "IMQ_JAVAHOME"
#define JAVA_HOME "JAVA_HOME"
#define IMQ_DEFAULT_JAVAHOME "IMQ_DEFAULT_JAVAHOME"
#define IMQ_DEFAULT_VARHOME  "IMQ_DEFAULT_VARHOME"
#define IMQ_DEFAULT_EXT_JARS "IMQ_DEFAULT_EXT_JARS"
#define IMQENV1  "\\etc\\imqenv.conf"
#define IMQENV2  "\\..\\etc\\mq\\imqenv.conf"
#define SET "set "
#define REM "REM "
#define POUND "#"
#define JDK_JRE_START "SOFTWARE\\JavaSoft\\Java Runtime Environment"
#define JDK_CURVER "CurrentVersion"
#define JDK_HOME_STR "JavaHome"

#define SKIP_WHITE(p) while (myisspace(*p)) p++

/*
 * VMs to use for JNI java clients. Order is most desireable to 
 * least desireable.
 * Relative paths are assumed to be relative to jrehome/bin
 */
char *client_vm_libs[] = {
    "client\\jvm.dll",
    "server\\jvm.dll",
    "classic\\jvm.dll",
    "hotspot\\jvm.dll"
};
int nclient_vm_libs = sizeof (client_vm_libs) / sizeof(char *);



/**
 * Find a suitable Java runtime. Here is where we look.
 *
 * 1. IMQ_JAVAHOME
 * 2. What's set for IMQ_JAVAHOME in %IMQ_HOME%\var\jdk-env.bat
 * 3. IMQ_DEFAULT_JAVAHOME (may be set via imqenv.conf)
 * 4. %IMQ_HOME%\jre (bundled)
 *
 * home     Value of IMQ_HOME
 * varhome  Value of IMQ_VARHOME
 * path     Memory location to copy path to runtime into. It must be
 *          at least size MAX_PATH. If it is a JDK then the path
 *          will point to the "jre" directory.
 *
 * returns 0 if we find a runtime, else < 0
 */
int FindJavaRuntime(const char *home, const char *varhome, char *path) {

    char tmpbuf[MAX_PATH];
    char pathbuf[MAX_PATH];
    char regbuf[MAX_PATH];
    char versionbuf[256];
    int vn = 256;
    int tb = MAX_PATH;

    char *p = NULL;
    HANDLE hfile = 0;

    *path = '\0';

    regbuf[0] = '\0';

    memset(tmpbuf,    '\0', sizeof(tmpbuf));
    memset(pathbuf, '\0', sizeof(pathbuf));
    memset(versionbuf, '\0', sizeof(versionbuf));

    if ((p = getenv(IMQ_JAVAHOME)) == NULL) {
        /* IMQ_JAVAHOME not set. Try and get it from jdk-env.bat file */
        strcpy(tmpbuf, varhome);
        strcat(tmpbuf, "\\jdk-env.bat");
        if (_access(tmpbuf, 00) == 0) {
            /* This should set IMQ_JAVAHOME */
            SourceFile(tmpbuf);
            p = getenv(IMQ_JAVAHOME);
        }
    }

    if (p == NULL) {
        /* Still no IMQ_JAVAHOME. Check for IMQ_DEFAULT_JAVAHOME */
        p = getenv(IMQ_DEFAULT_JAVAHOME);
    }

    if (p == NULL) {
        /* Still no IMQ_JAVAHOME. Look for bundled */
        strcpy(tmpbuf, home);
        strcat(tmpbuf, "\\jre");
        if (_access(tmpbuf, 00) == 0) {
            p = tmpbuf;
        }
    }
    if (p == NULL) {
        // Still no IMQ_JAVAHOME. Look for JAVA_HOME variable 
        p = getenv("JAVA_HOME");
    }

    if (p == NULL) {
        /* Still no JAVA_HOME, search the registry */
        /* Get the version from JDK_JRE_START */
        versionbuf[0]='\0';
        getAnyStringFromRegistry(JDK_JRE_START, versionbuf,&vn,JDK_CURVER);
        /* Get the jdk from JDK_START + version + JDK_HOME_STR */
        regbuf[0]= '\0';
        strcpy(regbuf, JDK_JRE_START);
        strcat(regbuf, "\\");
        strcat(regbuf, versionbuf);
        tmpbuf[0] = '\0';
        getAnyStringFromRegistry(regbuf, tmpbuf, &tb, JDK_HOME_STR);
        if (tmpbuf[0] != '\0' ) {
           //OK - make sure the registry isn't messages up
           if (_access(tmpbuf, 00) == 0) {
                p = tmpbuf;
           }
        }
    }
    if (p == NULL) {
        /* Still no JAVA_HOME, fallback to the path ?? */
        return 0; 
    }

    if (_access(p, 00) == 0) {
        /*
         * Valid IMQ_JAVAHOME. either it is pointing to a JDK or a JRE.
         * We always return a path to the JRE directory.
         */
        strcpy(pathbuf, p);
        strcat(pathbuf, "\\jre");
        if (_access(pathbuf, 00) == 0) {
            /* IMQ_JAVAHOME was pointing to a JDK */
            strcpy(path, pathbuf);
        } else {
            /* IMQ_JAVAHOME was pointing to a JRE */
            strcpy(path, p);
        }
        /* printf("===== Found java runtime %s\n", path); */
        return 0;
    } else {
        //Handle new logic
        //First check the registry
        //Then check for JAVA_HOME
        //finally we don't know what to do
         
        /* Return invalid path so we can give a good error message */
        strcpy(path, p);
        return -1;
    }
}

/*
 * Our version of isspace(). Why? Because I'm getting link errors
 * on windows if I use isspace() and I don't have time to figure out
 * why.
 */
int myisspace(const char c) {
    return (c != '\0' && c <= ' ');
}

/*
 * Parse path and mkdir each directory one at a time
 * since _mkdir will only work on one path.
 */
int mymkdir(const char *path) {

  char *onePath;
  char buildPath[MAX_PATH];
  char p[MAX_PATH];
 
  /* printf("mymkdir path is %s\n", path); */

  buildPath[0] = '\0';
  p[0] = '\0';

  // Make a copy of path so we don't modify.
  strcpy(p, path);

  onePath = strtok(p, "/\\");

  while (onePath != NULL) {
     /* printf("onePath is %s\n", onePath); */

     strcat(buildPath, onePath);
     strcat(buildPath, "\\");
     /* printf("buildPath is %s\n", buildPath);*/

     // If this is drive letter, then just try to access it.
     if (strlen(buildPath) == 3 && buildPath[1] == ':') {
	/* printf("this is a drive, try accessing it\n"); */
	if (_access(onePath, 00) != 0) {
           fprintf(stderr, "Cannot access drive %s from %s.\n", buildPath, path);
	   return -1;
	}
     } else {

	/* printf("Try accessing buildPath %s\n", buildPath); */

	// Try accessing buildPath.  
	// If cannot access it, then try to mkdir it.
	if (_access(buildPath, 00) != 0) {
	    /* printf("couldn't access %s so try to mkdir it \n", buildPath); */
 	    if (_mkdir(buildPath) != 0) {
               fprintf(stderr, "Cannot create directory %s in path %s\n", buildPath, path);
	       return -1;
   	    } 
	}
     }

     onePath = strtok(NULL, "/\\");
  } 
  return 0;
}

/*
 * Set environment variables based on "set" commands in a file.
 * For example if the file contains
 *     set COLOR=blue
 *     set SIZE=large
 * The COLOR and SIZE would be set in the current process' environment.
 * All other lines in the file are ignored.
 */
int SourceFile(const char *file) {

    char buf[MAX_PATH];
    char env[MAX_PATH];
    FILE *fp;
    char *p;
    char *start = NULL;

    if ((fp = fopen(file, "r")) == NULL) {
        sprintf(buf, "MQ service couldn't read configuration file %s", file);
	perror(buf);
	return -1;
    }

    memset(buf, '\0', sizeof(buf));

    while (fgets(buf, sizeof(buf), fp) != NULL) {
	p = buf;
        *env = '\0';
	/* Skip leading white space */
        SKIP_WHITE(p);

        if (strncmp(REM, p, strlen(REM)) == 0 ||
	    strncmp(POUND, p, strlen(POUND)) == 0) {
            // Skip comments REM or #
            ;
        } else if (strncmp(SET, p, strlen(SET)) == 0) {
            /* Skip "set "*/
            p += strlen(SET);

            SKIP_WHITE(p);

            /* Start of variable */
            start = p;

            /* Find end of varible */
            while (!myisspace(*p) && *p != '=') {
                p++;
            }

            if (*p == '\0') {
                continue;
            }

            *p = '\0';
            strcpy(env, start);
            strcat(env, "=");
            p++;
            SKIP_WHITE(p);

            if (*p == '=') {
                p++;
                SKIP_WHITE(p);
            }

            /* Get value */
            start = p;

            while (*p != '\0' && *p != '\n') {
                p++;
            }
            *p = '\0';
            strcat(env, start);

            if (*env != '\0') {
                 /* printf("===== Setting %s\n", env);  */
                _putenv(_strdup(env));
            }

        } else if (strncmp(IMQ_DEFAULT_VARHOME, p, strlen(IMQ_DEFAULT_VARHOME)) == 0 ||
		   strncmp(IMQ_DEFAULT_JAVAHOME, p, strlen(IMQ_DEFAULT_JAVAHOME)) == 0 ||
		   strncmp(IMQ_DEFAULT_EXT_JARS, p, strlen(IMQ_DEFAULT_EXT_JARS)) == 0) {

	    start = p;

            /* Find end of variable */
            while (!myisspace(*p) && *p != '=') {
                p++;
            }

            if (*p == '\0') {
                continue;
            }

            *p = '\0';
            strcpy(env, start);
            strcat(env, "=");
            p++;
            SKIP_WHITE(p);

            if (*p == '=') {
                p++;
                SKIP_WHITE(p);
            }

            /* Get value */
            start = p;

            while (*p != '\0' && *p != '\n') {
                p++;
            }
            *p = '\0';
            strcat(env, start);

            if (*env != '\0') {
                /* printf("===== Setting unix style %s\n", env); */
                _putenv(_strdup(env));
            }

        }
    }

    fclose(fp);

    return 0;
}


/**
 * Add all the jar files in the $IMQ_VARHOME/lib/ext directory to classpath.
 * These are user defined jar files needed for things like JDBC connectors.
 *
 * Note: External JARs can also be specified with IMQ_DEFAULT_EXT_JARS
 *       and may be set via imqenv.conf.
 */
VOID MqAppAddLibJars(const char *libhome, char *classpath) {

    WIN32_FIND_DATA dir;
    char lib[MAX_PATH];
    HANDLE handle;

    /* Added $IMQ_HOME/lib/ext directory */
    strcat(classpath, ";");
    strcat(classpath, libhome);
    strcat(classpath, "\\ext");

    sprintf(lib, "%s\\ext\\*", libhome);

    /* Add jars if any */
    handle = FindFirstFile(lib, &dir);
    if (handle == INVALID_HANDLE_VALUE) {
	return;
    }
    do {
	int len = strlen(dir.cFileName);

	if (len <= 4) {
            /* not a .jar file */
	} else if (strcmp(dir.cFileName + len - 4, ".jar") != 0 &&
                   strcmp(dir.cFileName + len - 4, ".zip") != 0) {
            /* File doesn't end in .jar or .zip */
	} else {
            strcat(classpath, ";");
            strcat(classpath, libhome);
            strcat(classpath, "\\ext\\");
            strcat(classpath, dir.cFileName);
	}
    } while (FindNextFile(handle, &dir));
}

/*
 * Do only MQ related initialization - no java initialization. This basically locates IMQ
 * and sets the relevant pieces of info in the MqEnv structure.
 *
 * The following fields in the MqEnv are updated.
 *  imqhome
 *  imqlibhome
 *  imqvarhome
 *  imqextjars
 *
 * All the fields above need to point to pre-allocated space.
 *
 * Params:
 *  me			ptr to MqEnv structure, must not be NULL.
 */
int MqAppInitializeNoJava(MqEnv *me)
{
    char *slash_loc;
    char *p;
    char tmpbuf[MAX_PATH];
    int newfs = 0;

    /* Determine IMQ_HOME */
    GetModuleFileName(0, me->imqhome, sizeof(me->imqhome));

    /* Take off file name and bin directory */
    slash_loc = strrchr(me->imqhome, '\\');
    *slash_loc = '\0';
    slash_loc = strrchr(me->imqhome, '\\');
    *slash_loc = '\0';

    if (me->imqlibhome[0] == '\0') {
        strcpy(me->imqlibhome, me->imqhome);
        strcat(me->imqlibhome, "\\lib");
    }

    /* Source imqenv.conf file to set default values
     * For example this may set IMQ_DEFAULT_JAVAHOME, IMQ_DEFAULT_VARHOME,
     * and IMQ_DEFAULT_EXT_JARS
     */

    /* First check the new filesystem layout location */
    memset(tmpbuf, 0, MAX_PATH);
    strncpy(tmpbuf, me->imqhome, MAX_PATH - strlen(IMQENV2) - 2 );
    strcat(tmpbuf, IMQENV2);
    if (_access(tmpbuf, 00) == 0) {
        SourceFile(tmpbuf);
        /* Note: this also means a different imqetchome */
        strcpy(me->imqetchome, me->imqhome);
        strcat(me->imqetchome, "\\..\\etc\\mq");
        newfs = 1;
    } else { /* use the original imqconf.env */
        memset(tmpbuf, 0, MAX_PATH);
        strncpy(tmpbuf, me->imqhome, MAX_PATH - strlen(IMQENV1) - 2 );
        strcat(tmpbuf, IMQENV1);
        if (_access(tmpbuf, 00) == 0) {
            SourceFile(tmpbuf);
            strcpy(me->imqetchome, me->imqhome);
            strcat(me->imqetchome, "\\etc");
        } else {
            /*
            printf("NO ENV.CONF : %s\n", me->imqhome);
            */
        }
    }


    /* Determine imqvarhome. This can be set via an environment variable */
    if (me->imqvarhome[0] == '\0') {
        if ((p = getenv("IMQ_VARHOME")) == NULL) {
            if ((p = getenv("IMQ_DEFAULT_VARHOME")) != NULL) {
                /* IMQ_DEFAULT_VARHOME may be set by imqenv.conf */
                strcpy(me->imqvarhome, p);
            } else if (newfs == 1) {
                strcpy(me->imqvarhome, me->imqhome);
                strcat(me->imqvarhome, "\\..\\var\\mq");
            } else {
                strcpy(me->imqvarhome, me->imqhome);
                strcat(me->imqvarhome, "\\var");
            }
        } else {
            strcpy(me->imqvarhome, p);
        }
    }

    /* Determine imqextjars. This can be set via an environment variable */
    if (me->imqextjars[0] == '\0') {
        if ((p = getenv("IMQ_DEFAULT_EXT_JARS")) != NULL) {
            /* IMQ_DEFAULT_EXT_JARS may be set by imqenv.conf */
            strcpy(me->imqextjars, p);
        }
    }

    // Change working directory to imqhome/bin
    memset(tmpbuf, 0, MAX_PATH);
    if (me->imqhome[0] != '\0') {
	strcpy(tmpbuf, me->imqhome);
	strcat(tmpbuf, "\\bin");
        if (0 != _chdir(tmpbuf)){
            fprintf(stderr, "Unable to change working directory to %s", tmpbuf);
	}
    }

/*
    printf("IMQ_HOME: %s\n", me->imqhome);
    printf("IMQ_VARHOME: %s\n", me->imqvarhome);
    printf("IMQ_ETCHOME: %s\n", me->imqetchome);
    printf("IMQ_EXTJARS: %s\n", me->imqextjars);
*/

    return 0;
}


/*
 * Do some initialization. This basically locates IMQ, the JDK, determines
 * the run classpath (for -cp later).
 *
 * The following fields in the MqEnv are updated.
 *  imqhome
 *  imqlibhome
 *  imqvarhome
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
int MqAppInitialize(MqEnv *me, char *classpath_entries[], int nclasspath_entries,
			BOOL append_libjars, BOOL append_classpath)
{
    char *p;
    int n;
    char tmpbuf[MAX_PATH];

    /*
     * Initialize the MQ related fields
     */
    MqAppInitializeNoJava(me);

    if (*(me->jrehome) == '\0') {
        /* Locate a java runtime. */
        FindJavaRuntime(me->imqhome, me->imqvarhome, me->jrehome);
    }

/** LKS-XXX
  * Removing for glassfish v3, we need to default to the path nothing is set.
  *  Note:  the error message will not be as clean if it fails (will do post FF)

    if (*(me->jrehome) == '\0') {
        fprintf(stderr, "Please specify a Java runtime using the IMQ_JAVAHOME\nenvironment variable, or -javahome command line option\n");
        return -1;
    }

    if (_access(me->jrehome, 00) < 0) {
        fprintf(stderr, "Invalid Java Runtime '%s'", me->jrehome);
        return -1;
    }

   End of REMOVE (LKS-XXX) *
*/

    /* Initialize classpath */
    *(me->classpath) = '\0';
    for (n = 0; n < nclasspath_entries; n++) {
        if (n != 0) strcat(me->classpath, ";");
        if (*classpath_entries[n] != '\\') {
            strcat(me->classpath, me->imqhome);
            strcat(me->classpath, "\\lib\\");
        }
        strcat(me->classpath, classpath_entries[n]);
    }

    /*
     * If IMQ_DEFAULT_EXT_JARS is set, append it to the value of 'classpath'
     */
    if (me->imqextjars[0] != '\0') {
        strcat(me->classpath, ";");
        strcat(me->classpath, me->imqextjars);
    }

    if (append_libjars)  {
        MqAppAddLibJars(me->imqlibhome, me->classpath);
    }

    if (append_classpath)  {
        /*
         * If CLASSPATH is set, append it to the value of 'classpath'
         */
        if ((p = getenv("CLASSPATH")) != NULL) {
            strcat(me->classpath, ";");
            strcat(me->classpath, p);
        }
    }

    // Change working directory to imqhome/bin
    memset(tmpbuf, 0, MAX_PATH);
    if (me->imqhome[0] != '\0') {
	strcpy(tmpbuf, me->imqhome);
	strcat(tmpbuf, "\\bin");
        if (0 != _chdir(tmpbuf)){
            fprintf(stderr, "Unable to change working directory to %s", tmpbuf);
	}
    }
    
/*
    printf("IMQ_HOME: %s\n", me->imqhome);
    printf("IMQ_VARHOME: %s\n", me->imqvarhome);
    printf("IMQ_EXTJARS: %s\n", me->imqextjars);
    printf("jrehome: %s\n", me->jrehome);
*/

    return 0;
}

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
 *  me	ptr to MqEnv structure, must not be NULL.
 *  argv argument vector
 *  argc number of elements in argv
 */
void MqAppParseArgs (MqEnv *me, char *argv[], int argc)
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
        } else {
            /* We don't recognize the option, pass it on to application */
            me->application_argv[me->application_argc] = _strdup(*argv);
            me->application_argc++;
        }
        argv++; argc--;
    }
}

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
void MqAppCreateJavaCmdLine(MqEnv *me, BOOL set_varhome, char *cmdLine)
{
    char javaCmd[512];
    int   jvm_argc = 0, i;
    char *jvm_argv_buffer[128];
    char **jvm_argv = &(jvm_argv_buffer[1]);

    jvm_argv[jvm_argc] = malloc(sizeof(me->classpath) + 80);
    sprintf(jvm_argv[jvm_argc], "-cp");
    jvm_argc++;

    jvm_argv[jvm_argc] = malloc(sizeof(me->classpath) + 80);
    sprintf(jvm_argv[jvm_argc], "%s", me->classpath);
    jvm_argc++;

    jvm_argv[jvm_argc] = malloc(sizeof(me->imqhome) + 80);
    sprintf(jvm_argv[jvm_argc], "-Dimq.home=%s", me->imqhome);
    jvm_argc++;

    if (set_varhome)  {
        jvm_argv[jvm_argc] = malloc(sizeof(me->imqvarhome) + 80);
        sprintf(jvm_argv[jvm_argc], "-Dimq.varhome=%s", me->imqvarhome);
        jvm_argc++;
    }

    if (me->imqetchome != '\0' ) { /* we've set etchome */
        jvm_argv[jvm_argc] = malloc(sizeof(me->imqetchome) + 80);
        sprintf(jvm_argv[jvm_argc], "-Dimq.etchome=%s", me->imqetchome);
        jvm_argc++;
    }

    if (me->jrehome[0] == '\0') {
        strcat(javaCmd, "\"java\"");
    } else {
        sprintf(javaCmd, "\"%s\\bin\\java\"", me->jrehome);
    }

    /* Copy Java command and command line arguments into command line */
    strcpy(cmdLine, javaCmd);
    for (i = 0; i < jvm_argc; i++) {
        strcat(cmdLine, " ");
        strcat(cmdLine, "\"");
        strcat(cmdLine, jvm_argv[i]);
        strcat(cmdLine, "\"");
    }

    /* Copy main class into command line */
    strcat(cmdLine, " ");
    strcat(cmdLine, me->main_class);
    strcat(cmdLine, " ");

    /* Copy main class arguments into command line */
    for (i = 0; i < me->application_argc; i++) {
        strcat(cmdLine, " ");
        strcat(cmdLine, "\"");
        strcat(cmdLine, me->application_argv[i]);
        strcat(cmdLine, "\"");
    }
}

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
DWORD MqAppRunCmd(char *cmdLine)
{
    /* Information about the forked child process */
    STARTUPINFO jsi;
    PROCESS_INFORMATION jpi;
    /* exit code */
    DWORD exitCode = 0;

    ZeroMemory(&jsi, sizeof(jsi));
    jsi.cb = sizeof(jsi);
    ZeroMemory(&jpi, sizeof(jpi));

    /*
     * Run a Java application and block until it exits.
     */
    /* Execute the child process */
    if ( !CreateProcess(
        NULL,               /* Use cmdLine */
        cmdLine,            /* Command line */
        NULL,               /* Prcess handle not inheritable. */
        NULL,               /* Thread handle not inheritable. */
        TRUE,               /* Set handle inheritance to TRUE. */
        0,                  /* No creation flags, use parent's console. */
        NULL,               /* Use parent's environment block */
        NULL,               /* Use parent's current directory */
        &jsi,                /* Pointer to STARTUPINFO struct [in] */
        &jpi                 /* Pointer to PROCESS_INFORMATION struct [out] */
        )
       ) {

        printf("Starting process failed. Could not execute %s", cmdLine);
    }

    /* Wait for child to exit */
    WaitForSingleObject (jpi.hProcess, INFINITE);

    /* Get exit code from child process */
    if (!GetExitCodeProcess(jpi.hProcess, &exitCode)) {
        fprintf(stderr, "Unable to get child's exit code.");
    }

    /* Close process and thread handles */
    CloseHandle(jpi.hProcess);
    CloseHandle(jpi.hThread);

    return (exitCode);
}

/*
 * Run the java command using the information in the
 * MqEnv structure passed in.
 * This function uses the VM invocation APIs in JNI.
 *
 * Params:
 *  me	ptr to MqEnv structure, must not be NULL.
 */
DWORD MqAppJNIRunJavaCmd(MqEnv *me)
{
    FARPROC		MqApp_JNI_CreateJavaVM = NULL;
    HINSTANCE		hinstLib;
    JNIEnv		*env;
    JavaVM		*jvm;
    JavaVMInitArgs	vm_args;
    JavaVMOption	options[4];
    jint		res;
    jclass		cls;
    jmethodID		mid;
    jstring		jstr;
    jobjectArray	args;
    DWORD 		exitCode = 0;
    char 		dllpath[MAX_PATH];
    char		*main_class, *tmp;
    int			i;

    /*
     * Try to load the VM's in the client_vm_libs array.
     * Stop at the first successful load.
     */
    for (i = 0; i < nclient_vm_libs; i++)
    {
        /*
         * Construct path to one jvm.dll in client_vm_libs array
         */
        dllpath[0] = '\0';
        strcpy(dllpath, me->jrehome);
        strcat(dllpath, "\\bin\\");
        strcat(dllpath, client_vm_libs[i]);

	if ((hinstLib = LoadLibrary(dllpath)) == NULL) {
	    continue;
	} else {
	    break;
	}
    }

    /*
     * If can't load any VM libs, print error msg and return.
     */
    if (hinstLib == NULL)
    {
	/*
	 * Couldn't load a VM
	 */
        fprintf(stderr, "Couldn't load any of the following Java VMs from %s:\n",
	        me->jrehome);
        for (i = 0; i < nclient_vm_libs; i++)
	{
            fprintf(stderr, "\t%s\n", client_vm_libs[i]);
        }

        return (1);
    }

    /*
     * Get function ptr to JNI_CreateJavaVM()
     */
    MqApp_JNI_CreateJavaVM = GetProcAddress(hinstLib, "JNI_CreateJavaVM");
    if (NULL == MqApp_JNI_CreateJavaVM)
    {
	fprintf(stderr, "Failed to obtain func ptr to JNI_CreateJavaVM() !\n");
        return (1);
    }


    /*
     * Setup for creating VM.
     */

    /*
     * Construct VM args
     */
    i = 0;
    options[i].optionString = malloc(sizeof(me->classpath) + 80);
    sprintf(options[i].optionString, "-Djava.class.path=%s", me->classpath);
    i++;

    options[i].optionString = malloc(sizeof(me->imqhome) + 80);
    sprintf(options[i].optionString, "-Dimq.home=%s", me->imqhome);
    i++;

    vm_args.version = JNI_VERSION_1_4;
    vm_args.options = options;
    vm_args.nOptions = i;
    vm_args.ignoreUnrecognized = JNI_TRUE;

    res = (*MqApp_JNI_CreateJavaVM)(&jvm, (void **)&env, &vm_args);
    if (res < 0) {
        fprintf(stderr, "Can't create Java VM\n");

        return (1);
    }

    /*
     * Convert main_class string in MqEnv to class string
     * with slashes instead of periods.
     */
    main_class = _strdup(me->main_class);
    tmp = main_class;
    while ((tmp = strchr(tmp, '.')) != NULL)  {
        *tmp = '/';
    }

    /*
     * Load main class.
     */
    cls = (*env)->FindClass(env, main_class);
    if (cls == NULL)
    {
	fprintf(stderr, "Failed to load class: %s\n", main_class);

	return (1);
    }

    /*
     * Get method ID for main method.
     */
    mid = (*env)->GetStaticMethodID(env, cls, "main", "([Ljava/lang/String;)V");
    if (mid == NULL)
    {
	fprintf(stderr, "Cannot find main method.\n");

	return (1);
	
    }

    /*
     * Construct main method (application) args.
     */
    if (me->application_argc > 0)  {
	args = (*env)->NewObjectArray(env, me->application_argc,
		    (*env)->FindClass(env, "java/lang/String"), NULL);
	if (args == NULL)  
	{
	    fprintf(stderr, "Failed to allocate args array.\n");
	    return (1);
	}

        for (i = 0; i < me->application_argc; i++) {
	    jstr = (*env)->NewStringUTF(env, me->application_argv[i]);
	    if (jstr == NULL)
	    {
	        fprintf(stderr, 
		    "Failed to allocate arg: %s\n", me->application_argv[i]);
	        return (1);
	    }

	    (*env)->SetObjectArrayElement(env, args, i, jstr);
        }
    } else  {
	args = (*env)->NewObjectArray(env, 0,
		    (*env)->FindClass(env, "java/lang/String"), NULL);
    }

    /*
     * Invoke main method.
     */
    (*env)->CallStaticVoidMethod(env, cls, mid, args);

    if ((*env)->ExceptionOccurred(env))
    {
	(*env)->ExceptionDescribe(env);
    }

    /*
     * Cleanup:
     *  - Detach the current thread so that it appears to have exited when
     *    the application's main method exits.
     *  - Destroy VM.
     */
    if ((*jvm)->DetachCurrentThread(jvm) != 0) {
        fprintf(stderr, "Could not detach main thread.\n");
	exitCode = 1;
    }
    (*jvm)->DestroyJavaVM(jvm);

    return (exitCode);
}

/*
 * Initialize MqEnv structure
 *
 * The following fields in the MqEnv are updated.
 *  imqhome
 *  imqlibhome
 *  imqvarhome
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
void MqAppInitMqEnv(MqEnv *me, char *main_class)
{
    me->imqhome[0] = '\0';
    me->imqlibhome[0] = '\0';
    me->imqvarhome[0] = '\0';
    me->imqextjars[0] = '\0';
    me->jrehome[0] = '\0';
    me->classpath[0] = '\0';
    me->application_argc = 0;
    me->main_class = main_class;
}

