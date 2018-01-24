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
 * @(#)jmqbrokersvc.c	1.39 07/20/07
 */ 

#include <windows.h>
#include <io.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <process.h>
#include <direct.h>
#include <tchar.h>
#include <jni.h>

#include "registry.h"
#include "mqapp.h"

#define MAX_OPTIONS 20

#define BROKER_MAIN                 "com/sun/messaging/jmq/jmsserver/Broker"
#define BROKER_SCM_EVENT_MANAGER    "com/sun/messaging/jmq/jmsserver/util/ntsvc/SCMEventManager"

// Main broker jar.
#define MAIN_JAR                    "imqbroker.jar"

// Class path entries. Relative paths are assumed to be relative to 
// imqhome/lib
char *classpath_entries[] = {
        "imqbroker.jar",
        "imqutil.jar",
        "jsse.jar",
        "jnet.jar",
        "jcert.jar",
        "..\\..\\share\\lib\\jdmkrt.jar",
        "..\\..\\share\\lib\\mfwk_instrum_tk.jar"
        };
int nclasspath_entries = sizeof (classpath_entries) / sizeof(char *);

int   application_argc = 0;
char *application_argv[128];
int   jvm_argc = 0;

// Argv list for jvm args. We skip first slot to reserv for "-server" if needed
char *jvm_argv_buffer[128];
char **jvm_argv = &(jvm_argv_buffer[1]);
char *broker_port = NULL;

char adminkey[80];

/* Information about the forked child process */
STARTUPINFO jsi;
PROCESS_INFORMATION jpi;

SERVICE_STATUS          ssStatus;
SERVICE_STATUS_HANDLE   sshStatusHandle;
BOOL bConsole = FALSE;
BOOL bVerbose = FALSE;
BOOL bManaged = FALSE;
char jmqhome[MAX_PATH];
char jmqbinhome[MAX_PATH];
char jmqbinhome[MAX_PATH];
char jmqvarhome[MAX_PATH];
char jmqetchome[MAX_PATH];
char jmqlibhome[MAX_PATH];
char jmqlibexthome[MAX_PATH];
char jrehome[MAX_PATH];
char classpath[65536];
DWORD dwErr = 0;
HANDLE hServerStopEvent = NULL;
int jmqBrokerExitCode = 0;

void WINAPI ServiceMain(DWORD argc, LPTSTR *argv);
void parseArgs (int argc, char *argv[]);
int stringToArgv(char *string, int argc, char *argv[], int maxn);
void dumpArgv(char *m, int argc, char *argv[]);
VOID WINAPI ControlHandler(DWORD ctrlCode);
BOOL WINAPI ConsoleCtrlHandler(DWORD dwCtrlType);
int Initialize(void);
VOID AddLibJars(const char *varhome, char *classpath);
VOID ServiceStart (DWORD dwArgc, LPTSTR *lpszArgv);
VOID ServiceStop(void);
BOOL ReportStatus(DWORD dwCurrentState,
                  DWORD dwWin32ExitCode,
                  DWORD dwWaitHint);
VOID AddToMessageLog(WORD wType, LPTSTR msg);
VOID LogError(LPTSTR msg);
VOID LogWarning(LPTSTR msg);
VOID LogInformation(LPTSTR msg);
int StartJavaApplication(
    DWORD argc,   LPSTR *argv,  /* Arguments to pass to JVM */
    LPSTR mainClass,            /* Main class to invoke.*/
    DWORD appArgc, LPSTR *appArgv   /* Arguments to pass to Main class */
    );
void getParamsFromRegistry();
BOOL createKeyFile(char *keyfile, const char *key);
char *GetNumericPropertyValue(const char *s);
char *FindLine(const char *file, const char *s);
char *FindBrokerPort( int argc, char *argv[], char *jmqvarhome, char *jmqlibhome);
BOOL argInArgv(const char *s, const char **argv, int argc);
BOOL hotspotServerAvailable(const char *jhome);

extern int FindJavaRuntime(const char*home, const char *varhome, char *path);
extern int mymkdir(const *path);

VOID main(int argc, char** argv)
{
    SERVICE_TABLE_ENTRY DispatchTable[] = {
        { TEXT(SERVICE_NAME), (LPSERVICE_MAIN_FUNCTION) ServiceMain } ,
        { NULL, NULL}
    };

    jmqhome[0] = '\0';
    jmqbinhome[0] = '\0';
    jmqvarhome[0] = '\0';
    jmqetchome[0] = '\0';
    jmqlibhome[0] = '\0';
    jmqlibexthome[0] = '\0';
    jrehome[0] = '\0';
    classpath[0] = '\0';
    adminkey[0] = '\0';

    parseArgs(argc, argv);

    if (bConsole) {
        if (Initialize() < 0) {
            exit (1);
        };
        ServiceStart(application_argc, application_argv);
        exit (jmqBrokerExitCode);
    } else {
        // NTService
        if (!StartServiceCtrlDispatcher(DispatchTable)) {
            LogError("StartServiceCtrlDispatcher failed");
        }
    }
}

// Handles console events that are generate on shutdown,
// logoff, Ctrl-C, Ctrl-Break and console close.`
BOOL WINAPI ConsoleCtrlHandler(DWORD dwCtrlType) {
    switch(dwCtrlType)
    {
        case CTRL_SHUTDOWN_EVENT:
            // Called when the system is shutdown. We want to shutdown the svc
            ServiceStop();
            return TRUE;

        // For other events let the default handler take care of them.
        // Even though the documentation says the default handler exits
        // the process, it appears as though a different handler is used
        // for services in which case LOGOFF_EVENT does not exit
        case CTRL_LOGOFF_EVENT:
        case CTRL_C_EVENT:
        case CTRL_BREAK_EVENT:
        case CTRL_CLOSE_EVENT:
        default:
            return FALSE;
    }
}

void WINAPI ServiceMain(DWORD argc, LPTSTR *argv)
{
    // Get parameters from the registry. This will set
    // jrehome, jvm_argc/argv and application_argc/argv to whatever
    // was installed in the registry (if any). These may then be
    // overwritten in parseArgs() by what the user entered in the SCM panel.
    getParamsFromRegistry();
    parseArgs(argc, argv);

    // Call RegisterServiceCtrlHandler immediately to register a service
    // control handler function. The returned SERVICE_STATUS_HANDLE is
    // saved with global scope, and used as a service id in calls to
    // SetServiceStatus.
    sshStatusHandle = RegisterServiceCtrlHandler( TEXT(SERVICE_NAME),
                                                  ControlHandler);

    if (!sshStatusHandle)
        goto finally;

    // The global ssStatus SERVICE_STATUS structure contains information
    // about the service, and is used throughout the program in calls made
    // to SetStatus through the ReportStatus function.
    ssStatus.dwServiceType = SERVICE_WIN32_OWN_PROCESS;
    ssStatus.dwServiceSpecificExitCode = 0;


    // If we could guarantee that all initialization would occur in less
    // than one second, we would not have to report our status to the
    // service control manager. For good measure, we will assign
    // SERVICE_START_PENDING to the current service state and inform the
    // service control manager through our ReportStatus function.
    if (!ReportStatus(SERVICE_START_PENDING, NO_ERROR, 3000))
        goto finally;

    // Do it! In ServiceStart, we'll send additional status reports to the
    // service control manager, especially the SERVICE_RUNNING report once 
    // our JVM is initiallized and ready to be invoked.
    if (Initialize() == 0) {
        ServiceStart(application_argc, application_argv);
    }

finally:

    // Report the stopped status to the service control manager, if we have
    // a valid server status handle.
     if (sshStatusHandle)
        (VOID)ReportStatus( SERVICE_STOPPED, dwErr, 0);
}


// Each Win32 service must have a control handler to respond to
// control requests from the dispatcher.

VOID WINAPI ControlHandler(DWORD ctrlCode)
{

    switch(ctrlCode)
    {
        case SERVICE_CONTROL_STOP:
            // Request to stop the service. Report SERVICE_STOP_PENDING
            // to the service control manager before calling ServiceStop()
            // to avoid a "Service did not respond" error.
            ReportStatus(SERVICE_STOP_PENDING, NO_ERROR, 0);
            ServiceStop();
            return;

        case SERVICE_CONTROL_SHUTDOWN:
            ReportStatus(SERVICE_STOP_PENDING, NO_ERROR, 0);
            ServiceStop();
            return;

        case SERVICE_CONTROL_PAUSE:
            //ReportStatus(SERVICE_PAUSE_PENDING, NO_ERROR, 0);
            return;

        case SERVICE_CONTROL_CONTINUE:
            //ReportStatus(SERVICE_CONTINUE_PENDING, NO_ERROR, 0);
            return;

        case SERVICE_CONTROL_INTERROGATE:
            // This case MUST be processed, even though we are not
            // obligated to do anything substantial in the process.
            break;

         default:
            // Any other cases...
            break;

    }

    // After invocation of this function, we MUST call the SetServiceStatus
    // function, which is accomplished through our ReportStatus function. We
    // must do this even if the current status has not changed.
    ReportStatus(ssStatus.dwCurrentState, NO_ERROR, 0);
}


/*
 * Do some initialization. This basically locates JMQ, the JDK, determines
 * our CLASSPATH, and dynamically loads jvm.dll
 */
int Initialize()
{
    int n;
    char buffer[512];
    HANDLE hLib = NULL;
    MqEnv me;

    MqAppInitMqEnv(&me, "");
    MqAppInitializeNoJava(&me);

    strcpy(jmqhome, me.imqhome);

    if (jmqlibhome[0] == '\0') {
        strcpy(jmqlibhome, me.imqlibhome);
    }
    if (jmqlibexthome[0] == '\0') {
        strcpy(jmqlibexthome, jmqlibhome);
        strcat(jmqlibexthome, "\\ext");
    }

    // Determine jmqvarhome. This can be set via an environment variable
    if (jmqvarhome[0] == '\0') {
        strcpy(jmqvarhome, me.imqvarhome);
    }

    if (_access(jmqvarhome, 00) < 0) {
        if (NULL != jmqvarhome) {
            if (0 != mymkdir(jmqvarhome)){
                sprintf(buffer, TEXT("Unable to make directory to %s"),
                                jmqvarhome);
                LogError(buffer);
		return -1;
	    }
	}
    }

    if (jmqetchome[0] == '\0' && me.imqetchome != '\0') {
        strcpy(jmqetchome, me.imqetchome);
    }
    if (jmqetchome[0] != '\0' && _access(jmqetchome, 00) < 0) {
        sprintf(buffer, "Couldn't find IMQ_ETCHOME '%s'", jmqetchome);
        LogError(buffer);
        return -1;
    }

    // Make sure IMQ_HOME is set in our environment. This will be
    // needed by any commands we execute in IMQ_HOME\bin
    if (!SetEnvironmentVariable("IMQ_HOME", jmqhome)) {
        sprintf(buffer, "Could not set IMQ_HOME to '%s'", jmqhome);
        LogError(buffer);
    }

    if (*jrehome == '\0') {
        /* Locate a java runtime. */
        FindJavaRuntime(jmqhome, jmqvarhome, jrehome);
    }

    if (*jrehome == '\0') {
        sprintf(buffer, "Please specify a Java runtime using the IMQ_JAVAHOME\nenvironment variable, or -javahome command line option\n");
        LogInformation(buffer);
        return -1;
    }

    if (_access(jrehome, 00) < 0) {
        sprintf(buffer, "Invalid Java Runtime '%s'", jrehome);
        LogError(buffer);
        return -1;
    }

    // Change working directory to jmqhome
    if (NULL != jmqhome){
	strcpy(jmqbinhome, jmqhome);
	strcat(jmqbinhome, "\\bin");
        if (0 != _chdir(jmqbinhome)){
            sprintf(buffer, TEXT("Unable to change working directory to %s"),
                         jmqbinhome);
            LogError(buffer);
	}
    }

    // Initialize classpath
    *classpath = '\0';
    for (n = 0; n < nclasspath_entries; n++) {
        if (n != 0) strcat(classpath, ";");
        if (*classpath_entries[n] != '\\') {
            strcat(classpath, jmqhome);
            strcat(classpath, "\\lib\\");
        }
        strcat(classpath, classpath_entries[n]);
    }

    /*
     * If IMQ_DEFAULT_EXT_JARS is set, append it to the value of 'classpath'
     */
    if (me.imqextjars[0] != '\0') {
        strcat(classpath, ";");
        strcat(classpath, me.imqextjars);
    }

    AddLibJars(jmqlibexthome, classpath);

    if (bVerbose && bConsole) {
        printf("Starting " DISPLAY_NAME "\n");
        printf("   imqhome=%s\n", jmqhome);
        printf("imqvarhome=%s\n", jmqvarhome);
        printf("imqetchome=%s\n", jmqetchome);
        printf("   jrehome=%s\n", jrehome);
    }

    broker_port = FindBrokerPort(application_argc, application_argv,
                            jmqvarhome, jmqlibhome);

    return 0;
}

/**
 * Add all the jar files in the $IMQ_VARHOME/lib directory to classpath.
 * These are user defined jar files needed for things like JDBC connectors
 */
VOID AddLibJars(const char *exthome, char *classpath) {

    WIN32_FIND_DATA dir;
    char lib[MAX_PATH];
    HANDLE handle;

    /* Added ./lib/ext/classes directory */
    strcat(classpath, ";");
    strcat(classpath, exthome);

    sprintf(lib, "%s\\*", exthome);

    /* Add jars if any */
    handle = FindFirstFile(lib, &dir);
    if (handle == INVALID_HANDLE_VALUE) {
	return;
    }
    do {
	int len = strlen(dir.cFileName);

	if (len <= 4) {
            // not a .jar file
	} else if (strcmp(dir.cFileName + len - 4, ".jar") != 0 &&
                   strcmp(dir.cFileName + len - 4, ".zip") != 0) {
            // File doesn't end in .jar or .zip
	} else {
            strcat(classpath, ";");
            strcat(classpath, exthome);
            strcat(classpath, "\\");
            strcat(classpath, dir.cFileName);
	}
    } while (FindNextFile(handle, &dir));
}

// This method is called from ServiceMain() when NT starts the service

VOID ServiceStart (DWORD service_argc, LPTSTR *service_argv)
{
    int  status;
    int  n;
    char keyfile[MAX_PATH + 1];

    // Create a Stop Event
    hServerStopEvent = CreateEvent(
        NULL,    
        TRUE,    
        FALSE,   
        NULL);

    if ( hServerStopEvent == NULL)
        goto cleanup;

    if (!ReportStatus(SERVICE_RUNNING,NO_ERROR,0)){
        goto cleanup;
    }

    jvm_argv[jvm_argc] = malloc(sizeof(classpath) + 80);
    sprintf(jvm_argv[jvm_argc], "-cp");
    jvm_argc++;

    jvm_argv[jvm_argc] = malloc(sizeof(classpath) + 80);
    sprintf(jvm_argv[jvm_argc], "%s", classpath);
    jvm_argc++;

    jvm_argv[jvm_argc] = malloc(sizeof(jmqhome) + 80);
    sprintf(jvm_argv[jvm_argc], "-Dimq.home=%s", jmqhome);
    jvm_argc++;

    jvm_argv[jvm_argc] = malloc(sizeof(jmqvarhome) + 80);
    sprintf(jvm_argv[jvm_argc], "-Dimq.varhome=%s", jmqvarhome);
    jvm_argc++;

    if (jmqetchome[0] != '\0') {
        jvm_argv[jvm_argc] = malloc(sizeof(jmqetchome) + 80);
        sprintf(jvm_argv[jvm_argc], "-Dimq.etchome=%s", jmqetchome);
        jvm_argc++;
    }

    // If -Xms has not been specified yet, then specify it.
    if (!argInArgv("-Xms", jvm_argv, jvm_argc)) {
        jvm_argv[jvm_argc] = _strdup("-Xms16m");
        jvm_argc++;
    }

    // If -Xmx has not been specified yet, then specify it.
    if (!argInArgv("-Xmx", jvm_argv, jvm_argc)) {
        jvm_argv[jvm_argc] = _strdup("-Xmx192m");
        jvm_argc++;
    }

    if (!bConsole) {
        // this prevents this process from getting whacked upon the logoff event
        // We need this to cleanly terminate service on shutdown
        SetConsoleCtrlHandler(ConsoleCtrlHandler, TRUE);
        // this prevents the VM from getting whacked upon the logoff event
        jvm_argv[jvm_argc] = _strdup("-Xrs");
        jvm_argc++;
    }

    // If there is a hotspot server vm available, use it
    if (!argInArgv("-server", jvm_argv, jvm_argc) &&
         hotspotServerAvailable(jrehome)) {
        // Point to start of argv buffer (we left an empty slot there)
        // and insert -server at the start
        jvm_argv = jvm_argv_buffer;
        jvm_argv[0] = _strdup("-server");
        jvm_argc++;
    }

    if (bVerbose && bConsole) {
        dumpArgv("Java VM Arguments:", jvm_argc, jvm_argv);
        dumpArgv("Broker Arguments", application_argc, application_argv);
    }

    srand(time(0));

    do {
        if (!bConsole) {
            // Generate a random admin session key. We need this
            // when running as a service to shutdown the broker
            // using jmqcmd
            sprintf(adminkey, "%d%d%d%d%d%d",
                rand(), rand(), rand(), rand(), rand(), rand());
            createKeyFile(keyfile, adminkey);
            service_argv[service_argc] = _strdup("-adminkeyfile");
            service_argc++;
            service_argv[service_argc] = _strdup(keyfile);
            service_argc++;
        } else {
            keyfile[0] = '\0';
            adminkey[0] = '\0';
        }


        // After the initialization is complete (we've checked for
        // arguments) and the service control manager has been told
        // the service is running, invoke the Java application.
        // This call will block until the broker exits`
        status = StartJavaApplication(jvm_argc, jvm_argv,
            "com.sun.messaging.jmq.jmsserver.Broker",
            service_argc, service_argv);

        jmqBrokerExitCode = status;

        // Broker may have exited due to a restart command from JMQ
        // admin. If this is the case it would have set its exit
        // code to 255 which means we need to restart it. We sleep
        // for a bit to avoid spinning in a tight loop if we get
        // here unintentionally 
        if (status == 255 && !bManaged) {
            Sleep(1000 * 1);
        } else {
            // Report the stopped status in ServiceMain
            //if (sshStatusHandle)
            //   (VOID)ReportStatus( SERVICE_STOPPED, NO_ERROR, 0);
        }

        if (!bConsole) {
            // Remove -adminkeyfile options.
            for (n = 0; n < 2; n++) {
                service_argc--;
                if (service_argv[service_argc] != NULL) {
                    free(service_argv[service_argc]);
                    service_argv[service_argc] = NULL;
                }
            }
        }

        // If -managed was supplied, the restart will be performed by Glassfish
        // If -managed was not supplied, then loop round and restart

    } while (status == 255 && !bManaged);


cleanup:

    if (hServerStopEvent) {
        CloseHandle(hServerStopEvent);
    }
    LogInformation(TEXT("Broker process is stopped"));
}

/*
 *  Create a key file and write the specified key string to it.
 *  char *keyfile   Buffer to hold name of key file. Allocated by caller.
 *  char *key       Key  string to write to file
 */
BOOL createKeyFile(char *keyfile, const char *key) {

    char buf[MAX_PATH];
    int  nbytes = 0;
    HANDLE hFile;

    *keyfile = '\0';

    // Generate a temp file  in IMQ_VARHOME
    if (GetTempFileName(jmqvarhome, "imqkey", 0, keyfile) == 0) {
        LogError("Could not get a temp file");
        sprintf(keyfile, "%s\\%s", jmqvarhome, "imqkey.txt");
    }

    // Create file
    hFile = CreateFile(keyfile,
                    GENERIC_WRITE,              // open for writing
                    0,                          // do not share
                    NULL,                       // no security
                    CREATE_ALWAYS,              // overwirte existing
                    FILE_ATTRIBUTE_NORMAL,      // normal file
                    NULL);                      // no attr. template
    if (hFile == INVALID_HANDLE_VALUE) {
        sprintf(buf, "Could not open admin key file %s", keyfile);
        LogError(buf);
        return 0;
    }

    // Write admin key to temp file
    if (!WriteFile(
                hFile,              // File handle to write to
                adminkey,           // buffer to write
                strlen(adminkey),   // number of bytes to write
                &nbytes,            // [out] number of written
                NULL                // Overlap not needed (not async)
                )) { 
        sprintf(buf, "Could not write to admin key file %s", keyfile);
        LogError(buf);
        return 0;
    }
    CloseHandle(hFile);
    return 1;
}

/*
 * Stop the service means we need to stop the broker.
 * We do this by running imqcmd
 */
VOID ServiceStop()
{
    char cmdLine[1024];
    char  buf[512];
    DWORD exitCode = 0;
    STARTUPINFO si;
    PROCESS_INFORMATION pi;

    /* Signal the stop event. */
    if ( hServerStopEvent ){
        SetEvent(hServerStopEvent);
    }

    /* Run imqcmd to stop broker */
    sprintf(cmdLine, "\"%s\\bin\\imqcmd\"", jmqhome);
    //strcat(cmdLine, " shutdown bkr -f -s -u admin -p admin");
    strcat(cmdLine, " shutdown bkr -f -s -adminkey -u admin -pw ");
    strcat(cmdLine, adminkey);
    if (broker_port != NULL) {
        strcat(cmdLine, " -b localhost:");
        strcat(cmdLine, broker_port);
    }

    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));

    if (bVerbose) {
        sprintf(buf, "Shutting down service by executing %s", cmdLine);
        LogInformation(buf);
    }

    /* Execute the child process */
    if ( !CreateProcess(
        NULL,               // No module name. Use command line.
        cmdLine,            // Command line
        NULL,               // Prcess handle not inheritable.
        NULL,               // Thread handle not inheritable.
        FALSE,              // Set handle inheritance to FALSE.
        0,                  // No creation flags, use parent's console.
        NULL,               // Use parent's environment block
        NULL,               // Use parent's current directory
        &si,                // Pointer to STARTUPINFO struct [in]
        &pi                 // Pointer to PROCESS_INFORMATION struct [out]
        )
       ) {
        sprintf(buf, "Stopping service failed. Could not execute %s", cmdLine);
        LogError(buf);
        return;
    }

    if (sshStatusHandle) {
        // Make sure service control manager doesn't time us out
        ReportStatus(SERVICE_STOP_PENDING, NO_ERROR, 0);
    }

    // Wait for child to exit
    WaitForSingleObject (pi.hProcess, INFINITE);

    // Get exit code from child process
    if (!GetExitCodeProcess(pi.hProcess, &exitCode)) {
        LogError(TEXT("Unable to get child's exit code."));
    }

    /*
     * Can't rely on imqcmd exit status yet. 
    if (exitCode != 0) {
        sprintf(buf, "Error stopping service. %s returned %d", cmdLine, exitCode);
        LogError(buf);
    }
    */

    // Close process and thread handles
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);

    /**
     * Let the ServiceMain thread to set STOPPED status
     */
    //(VOID)ReportStatus( SERVICE_STOPPED, NO_ERROR, 0);

    return;
}

// Throughout the program, calls to SetServiceStatus are required
// which are handled by calling this function. Here, the non-constant
// members of the SERVICE_STATUS struct are assigned and SetServiceStatus
// is called with the struct. Note that we will not report to the service
// control manager if we are running as  console application.

BOOL ReportStatus(DWORD dwCurrentState,
                  DWORD dwWin32ExitCode,
                  DWORD dwWaitHint)
{
    static DWORD dwCheckPoint = 1;
    BOOL bResult = TRUE;

    if ( !bConsole ) 
    {
        if (dwCurrentState == SERVICE_START_PENDING)
            ssStatus.dwControlsAccepted = 0;
        else {
            ssStatus.dwControlsAccepted =
                SERVICE_ACCEPT_STOP;
        }

        ssStatus.dwCurrentState = dwCurrentState;
        ssStatus.dwWin32ExitCode = dwWin32ExitCode;
        ssStatus.dwWaitHint = dwWaitHint;

        if ( ( dwCurrentState == SERVICE_RUNNING ) ||
             ( dwCurrentState == SERVICE_STOPPED ) )
            ssStatus.dwCheckPoint = 0;
        else
            ssStatus.dwCheckPoint = dwCheckPoint++;

        if (!(bResult = SetServiceStatus( sshStatusHandle, &ssStatus))) {
            LogError(TEXT("SetServiceStatus"));
        }
    }

    return bResult;
}

VOID LogError(LPTSTR msg) {
    AddToMessageLog(EVENTLOG_ERROR_TYPE, msg);
}

VOID LogWarning(LPTSTR msg) {
    AddToMessageLog(EVENTLOG_WARNING_TYPE, msg);
}

VOID LogInformation(LPTSTR msg) {
    AddToMessageLog(EVENTLOG_INFORMATION_TYPE, msg);
}

// If running as a service, use event logging to post a message
// If not, display the message on the console.
// wType should be one of:
//  EVENTLOG_ERROR_TYPE  
//  EVENTLOG_WARNING_TYPE
//  EVENTLOG_INFORMATION_TYPE

VOID AddToMessageLog(WORD wType, LPTSTR msg )
{
    TCHAR   *szMsg = NULL;
    HANDLE  hEventSource;
    LPTSTR  lpszStrings[2];
    LPVOID lpMsgBuf = NULL;
    int len = 24;

    if (wType == EVENTLOG_ERROR_TYPE) {
        dwErr = GetLastError();
        FormatMessage(
            FORMAT_MESSAGE_ALLOCATE_BUFFER |
            FORMAT_MESSAGE_FROM_SYSTEM |
            FORMAT_MESSAGE_IGNORE_INSERTS ,
            NULL,
            dwErr,
            MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
            (LPTSTR)&lpMsgBuf,
            0,
            NULL
        );
        len += strlen(TEXT(": :  ")) + strlen(TEXT(DISPLAY_NAME));
        len += strlen(msg) + 24 + strlen(lpMsgBuf);
        szMsg = (TCHAR *)malloc(len*sizeof(TCHAR));
        _stprintf(szMsg, TEXT("%s: %s: %d %s"), TEXT(DISPLAY_NAME), msg, dwErr,
            lpMsgBuf);
        LocalFree(lpMsgBuf);
    } else {
        len += strlen(TEXT(": ")) + strlen(TEXT(DISPLAY_NAME)) + strlen(msg);
        szMsg = (TCHAR *)malloc(len*sizeof(TCHAR));
        _stprintf(szMsg, TEXT("%s: %s"), TEXT(DISPLAY_NAME), msg);
    }

    if (!bConsole) {
        hEventSource = RegisterEventSource(NULL, TEXT(SERVICE_NAME));

        lpszStrings[0] = szMsg;

        if (hEventSource != NULL) {
            ReportEvent(hEventSource, 
                wType,  
                0,                    
                0,                    
                NULL,                 
                1,                    
                0,                    
                lpszStrings,          
                NULL);                

            DeregisterEventSource(hEventSource);
        }
    } else{
        _tprintf(TEXT("%s\n"), szMsg);
    }
    if (szMsg != NULL) free(szMsg);
}

/*
 * Run a Java application and block until it exits.
 * Return the exit status from the Java application
 */
int StartJavaApplication(
    DWORD argc,   LPSTR *argv,  /* Arguments to pass to JVM */
    LPSTR mainClass,            /* Main class to invoke. */
    DWORD appArgc, LPSTR *appArgv   /* Arguments to pass to Main class */
    )
{
    char *buf = NULL;
    char *cmdLine = NULL;
    char javaCmd[512];
    DWORD exitCode = 0;
    UINT i;
    int len = 24;

    sprintf(javaCmd, "\"%s\\bin\\java\"", jrehome);
    len += strlen(javaCmd);
    
    for (i = 0; i < argc; i++) {
        len += strlen(argv[i]) + 8;
    }

    len += strlen(mainClass);

    for (i = 0; i < appArgc; i++) {
        len += strlen(appArgv[i]) + 8;
    }

    cmdLine = (char *)malloc(len * sizeof(char));
    memset(cmdLine, '\0', len);

    /* Copy Java command and command line arguments into command line */
    strcpy(cmdLine, javaCmd);
    for (i = 0; i < argc; i++) {
        strcat(cmdLine, " ");
        strcat(cmdLine, "\"");
        strcat(cmdLine, argv[i]);
        strcat(cmdLine, "\"");
    }

    /* Copy main class into command line */
    strcat(cmdLine, " ");
    strcat(cmdLine, mainClass);
    strcat(cmdLine, " ");

    /* Copy main class arguments into command line */
    for (i = 0; i < appArgc; i++) {
        strcat(cmdLine, " ");
        strcat(cmdLine, "\"");
        strcat(cmdLine, appArgv[i]);
        strcat(cmdLine, "\"");
    }

    ZeroMemory(&jsi, sizeof(jsi));
    jsi.cb = sizeof(jsi);
    ZeroMemory(&jpi, sizeof(jpi));

    len = strlen(cmdLine) + 24;
    buf = (char *)malloc(len * sizeof(char));
    memset(buf, '\0', len);
    if (bVerbose || !bConsole) {
        sprintf(buf, "Starting %s", cmdLine);
        LogInformation(buf);
    }
;
    // Execute the child process
    if ( !CreateProcess(
        NULL,               // Use cmdLine
        cmdLine,            // Command line
        NULL,               // Prcess handle not inheritable.
        NULL,               // Thread handle not inheritable.
        TRUE,               // Set handle inheritance to TRUE.
        0,                  // No creation flags, use parent's console.
        NULL,               // Use parent's environment block
        NULL,               // Use parent's current directory
        &jsi,                // Pointer to STARTUPINFO struct [in]
        &jpi                 // Pointer to PROCESS_INFORMATION struct [out]
        )
       ) {
        sprintf(buf, "Starting service failed. Could not execute %s", cmdLine);
        LogError(buf);
        free(buf);
        return 1;
    }
    free(buf);

    // Wait for child to exit
    WaitForSingleObject (jpi.hProcess, INFINITE);

    // Get exit code from child process
    if (!GetExitCodeProcess(jpi.hProcess, &exitCode)) {
        LogError(TEXT("Unable to get child's exit code."));
        exitCode = 1;
    }

    // Close process and thread handles
    CloseHandle(jpi.hProcess);
    CloseHandle(jpi.hThread);

    /*
    sprintf(buf, "Application exited with code %d\n", exitCode);
    LogInformation(buf);
    */

    return exitCode;
}

void parseArgs (int argc, char *argv[]) {

    argv++; argc--;
    while (argc > 0) {
        if (strcmp(*argv, "-console") == 0) {
            bConsole = TRUE;
        } else if (strcmp(*argv, "-verbose") == 0) {
            bVerbose = TRUE;
        } else if (strcmp(*argv, "-managed") == 0) {
            bManaged = TRUE;            
        } else if (strcmp(*argv, "-javahome") == 0) {
            argv++; argc--;
            if (argc > 0) {
                /* use it to set jrehome */
                strncpy(jrehome, *argv, sizeof(jrehome) - 32);
                if (!_stricmp((jrehome+(strlen(*argv)-4)),"\\jre") == 0) {
                   /* doesn't end in \jre, so append \jre on the end */     
                   strcat(jrehome, "\\jre");
                }
            }
        } else if (strcmp(*argv, "-jrehome") == 0) {
            argv++; argc--;
            if (argc > 0) {
                strncpy(jrehome, *argv, sizeof(jrehome));
            }
        } else if (strcmp(*argv, "-varhome") == 0) {
            argv++; argc--;
            if (argc > 0) {
                strncpy(jmqvarhome, *argv, sizeof(jmqvarhome));
            }
        } else if (strcmp(*argv, "-vmargs") == 0) {
            argv++; argc--;
            if (argc > 0) {
                jvm_argc = stringToArgv(*argv, jvm_argc, jvm_argv,  32);
            }
        } else {
            // We don't recognize the option, pass it on to application
            application_argv[application_argc] = _strdup(*argv);
            application_argc++;
        }

        argv++; argc--;
    }

    if (!bConsole) {
        // We are running as an NT service. Pass info to broker.
        application_argv[application_argc] = _strdup("-ntservice");
        application_argc++;
    }

}

/*
 * Convert a string to argc/argv format. If the passed argc is
 * greater than 0 then the arguments are appended starting at the
 * location indexed by argc.
 *
 * Returns the new argc.
 */
int stringToArgv(char *string, int argc, char *argv[], int maxn) {

    int  n;
    char **p;
    char *s;

    for (n = argc, p = argv, s = string; *s != '\0' && n < maxn; n++, p++) {
        argv[n] = s;
        while (*s != ' ' && *s != '\0') {
            s++;
        }

        while (*s == ' ') {
            *s = '\0';
            s++;
        }
    }
    argv[n] = NULL;

    return(n);
}

void dumpArgv(char *m, int argc, char *argv[]) {

    int  n;
    if (m != NULL) {
        printf("%s\n", m);
    }

    for (n = 0; n < argc; n++) {
        printf("argv[%d]=%s\n", n, argv[n]);
    }
}



/*
 * Set 'jrehome', 'jvm_argc/argv' and 'application_argc/argv' to
 * values stored in the registry at install time (if any)
 */
void getParamsFromRegistry() {

    int n = 0;
    char tmp_buf[1024];

    n = sizeof(tmp_buf);
    tmp_buf[0] = '\0';
    getStringFromRegistry(tmp_buf, &n, JREHOME_KEY);
    if (tmp_buf[0] != '\0') {
        strncpy(jrehome, tmp_buf, sizeof(jrehome) - 32);
    }

    n = sizeof(tmp_buf);
    tmp_buf[0] = '\0';
    getStringFromRegistry(tmp_buf, &n, JVMARGS_KEY);
    if (tmp_buf[0] != '\0') {
        jvm_argc = stringToArgv(_strdup(tmp_buf), jvm_argc, jvm_argv, 32);
    }

    n = sizeof(tmp_buf);
    tmp_buf[0] = '\0';
    getStringFromRegistry(tmp_buf, &n, SERVICEARGS_KEY);
    if (tmp_buf[0] != '\0') {
        application_argc = stringToArgv(_strdup(tmp_buf), application_argc, application_argv, 32);
    }
}

/*
 * This is a gross, gross hack to try to figure out what port the
 * broker (in particular the broker portmapper) is running on.
 *
 * Parameters:
 *    argc, *argv[]	The command line arguments that will be passed to broker
 *    jmqhome		The value of $IMQ_HOME
 *    jmqvarhome        The value of $IMQ_VARHOME
 *
 * Returns:
 *    NULL if no port property value was found
 *    Or a string representation of the port number
 */
char *FindBrokerPort(
	int argc, char *argv[],
	char *jmqvarhome,
	char *jmqlibhome
	) {

    const char *port_prop      = "-Dimq.portmapper.port=";
    const char *port_prop_name = "imq.portmapper.port=";
    char buf[MAX_PATH + 1];
    char *name = NULL;
    char *s = NULL;
    int  rcode = 0;

    /* First check the application command line arguments for
     * -port <port #> or -Dimq.portmapper.port=<port #>
     */
    while (argc > 0) {
        if (strcmp(*argv, "-port") == 0) {
            argv++; argc--;
	    if (argc > 0) {
		return _strdup(*argv);
	    }
         } else if (strncmp(*argv, port_prop, strlen(port_prop)) == 0) {
	    /* Found -Dimq.portmapper.port=" */
	    return GetNumericPropertyValue(*argv);
	 } else if (strcmp(*argv, "-name") == 0) {
	    /* We need instance name to check property files */
	    argv++; argc--;
	    if (argc > 0) {
		name = _strdup(*argv);
            }
	 }
	argv++; argc--;
    }

    if (name == NULL) {
	name = _strdup("imqbroker");
    }

    /* Next check instance property file if it exists */
    sprintf(buf, "%s\\instances\\%s\\props\\config.properties",
	jmqvarhome, name);
    free(name); name = NULL;
    if (_access(buf, 00) == 0) {
        if ((s = FindLine(buf, port_prop_name)) != NULL) {
	    return GetNumericPropertyValue(s);
        }
    }

    /* Finally check default configuration */
    sprintf(buf, "%s\\props\\broker\\default.properties",
	jmqlibhome);
    if ((s = FindLine(buf, port_prop_name)) != NULL) {
	return GetNumericPropertyValue(s);
    }

    return NULL;
}

/*
 * Takes something of the form:
 * blah-blah-blah=12345
 * and returns the "12345".
 * Caller is responsible for freeing returned buffer.
 */
char *GetNumericPropertyValue(const char *s)  {

    char *result;
    char *p;

    /* Skip to '=' (or ':') sign */
    while (*s != '\0' && *s != '=' && *s != ':') {
	s++;
    }

    if (*s == '\0') return NULL;

    /* Make s point to first byte in value */
    s++;
    result = _strdup(s);
    p = result;

    /* Find end of value */
    while (*p >= '0' && *p <= '9') {
	p++;
    }

    if (p == result) {
	free(result);
	return NULL;
    }

    *p = '\0';

    return result;
}


/*
 * Return the last line from a file that starts with the specified string.
 * We skip leading whitespace before doing comparision.
 */
char *FindLine(const char *file, const char *s) {

    char buf[MAX_PATH];
    FILE *fp;
    char *p;
    char *match_p = NULL;

    if ((fp = fopen(file, "r")) == NULL) {
        sprintf(buf, "MQ NT service couldn't search file %s", file);
	perror(buf);
	return NULL;
    }

    memset(buf, '\0', sizeof(buf));

    while (fgets(buf, sizeof(buf), fp) != NULL) {
	/* Skip leading white space */
	p = buf;
	while (*p != '\0' && (*p == ' ' || *p == '\t')) {
	    p++;
        }
	if (strncmp(s, p, strlen(s)) == 0) {
	    /* We have a match */
	    if (match_p != NULL) free(match_p);
	    match_p = _strdup(p);
        }
    }

    fclose(fp);
    return match_p;
}

/*
 * Check if an argument appears in the passed argument list. We
 * basically do this by checking if any string in argv starts with
 * the passed string.
 */
BOOL argInArgv(const char *s, const char **argv, int argc) {

    int n;
    for (n = 0; n < argc; n++) {
        if (strncmp(s, argv[n], sizeof(s)) == 0) {
            return 1;
        }
    }
    return 0;
}

/*
 * Check if the hotspot server VM is installed in the JRE we are running
 * against
 */
BOOL hotspotServerAvailable(const char *jhome) {

    char buf[MAX_PATH];

    sprintf(buf, "%s\\bin\\server", jhome);
    if (_access(buf, 00) < 0) {
        return 0;
    } else {
        return 1;
    }
}

