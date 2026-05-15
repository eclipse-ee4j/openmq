@echo off
REM
REM  Copyright (c) 2026 Contributors to the Eclipse Foundation
REM
REM  This program and the accompanying materials are made available under the
REM  terms of the Eclipse Public License v. 2.0, which is available at
REM  http://www.eclipse.org/legal/epl-2.0.
REM
REM  This Source Code may also be made available under the following Secondary
REM  Licenses when the conditions for such availability set forth in the
REM  Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
REM  version 2 with the GNU Classpath Exception, which is available at
REM  https://www.gnu.org/software/classpath/license.html.
REM
REM  SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
REM

REM
REM Broker startup script
REM Script specific properties:
REM   -autorestart  Restart broker on abnormal exit
REM   -managed      Broker is managed by an external process (suppresses autorestart)
REM   -verbose      Print the launch command before starting
REM

setLocal ENABLEDELAYEDEXPANSION
set _mainclass=com.sun.messaging.jmq.jmsserver.Broker

call "%~dp0..\lib\imqinit.bat" %*
if ERRORLEVEL 1 goto end

set IMQ_EXTERNAL=%IMQ_HOME%\lib\ext
set _def_jvm_args=-Xms192m -Xmx192m -Xss256k
set JVM_ARGS=
set BKR_ARGS=
set _AUTORESTART=
set _MANAGED=
set _BGND=
set args_list=%*

:processArgs
FOR /f "tokens=1,2* delims= " %%a IN ("%args_list%") DO (
  if "%%a" == "-vmargs"      ( set JVM_ARGS=%JVM_ARGS% %%b&set args_list=%%c&goto :processArgs )
  if "%%a" == "-autorestart" ( set _AUTORESTART=true&set args_list=%%b %%c&goto :processArgs )
  if "%%a" == "-managed"     ( set _MANAGED=true&set args_list=%%b %%c&goto :processArgs )
  if "%%a" == "-verbose"     ( set IMQ_VERBOSE=true&set args_list=%%b %%c&goto :processArgs )
  if "%%a" == "-bgnd"        ( set _BGND=true&set BKR_ARGS=%BKR_ARGS% -bgnd&set args_list=%%b %%c&goto :processArgs )
  if "%%a" == "-clientvm"    ( set args_list=%%b %%c&goto :processArgs )
  set BKR_ARGS=%BKR_ARGS% %%a
  set args_list=%%b %%c
  goto :processArgs
)
:exitProcessArgs

REM Create instances directory if it does not exist
if not exist "%IMQ_VARHOME%\instances" mkdir "%IMQ_VARHOME%\instances"

set JVM_ARGS=%_def_jvm_args% %JVM_ARGS% -XX:MaxGCPauseMillis=5000 "-Dimq.home=%IMQ_HOME%" "-Dimq.varhome=%IMQ_VARHOME%" "-Dimq.etchome=%IMQ_ETCHOME%" "-Dimq.libhome=%IMQ_LIBHOME%"
set _classes=%IMQ_HOME%\lib\imqbroker.jar;%IMQ_HOME%\lib\imqutil.jar;%IMQ_EXTERNAL%\*
for %%f in ("%IMQ_EXTERNAL%\*.zip") do set _classes=!_classes!;%%f
if not "%IMQ_EXT_JARS%" == "" set _classes=%_classes%;%IMQ_EXT_JARS%

if "%IMQ_VERBOSE%" == "true" (
    echo   IMQ_HOME    : %IMQ_HOME%
    echo   IMQ_VARHOME : %IMQ_VARHOME%
    echo Command:
    echo   "%JAVA_HOME%\bin\java" -cp "%_classes%" %JVM_ARGS% %_mainclass% %BKR_ARGS%
)

:StartBroker
"%JAVA_HOME%\bin\java" -cp "%_classes%" %JVM_ARGS% %_mainclass% %BKR_ARGS%
set _status=%ERRORLEVEL%

REM Exit code 255 means restart
if %_status% == 255 (
    if "%_MANAGED%" == "true" goto end
    timeout /t 1 /nobreak > nul
    goto StartBroker
)

REM Normal termination
if %_status% == 0 goto end
if %_status% == 1 goto end

REM Abnormal termination - restart if -autorestart was specified
if "%_AUTORESTART%" == "true" (
    if not "%_MANAGED%" == "true" (
        timeout /t 2 /nobreak > nul
        goto StartBroker
    )
)

:end
endlocal & EXIT /b %_status%
