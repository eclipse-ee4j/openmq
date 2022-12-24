@echo off
REM
REM  Copyright (c) 2000-2017 Oracle and/or its affiliates. All rights reserved.
REM  Copyright (c) 2020 Payara Services Ltd.
REM  Copyright (c) 2022 Contributors to the Eclipse Foundation. All rights reserved.
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

setLocal ENABLEDELAYEDEXPANSION
set _mainclass=com.sun.messaging.jmq.jmsserver.Broker

for /f %%i in ("%0") do set curdir=%%~dpi
set IMQ_HOME=%curdir%\..\
set DEPENDLIBS=..\..\..\share\opt\depend
set IMQ_EXTERNAL=%IMQ_HOME%\lib\ext
set JVM_ARGS=
set BKR_ARGS=
set args_list=%*

:processArgs
FOR /f "tokens=1,2* delims= " %%a IN ("%args_list%") DO (

  set arg=%%a
  if "%%a" == "-jmqhome"	( set IMQ_HOME=%%b&set args_list=%%c&goto :processArgs )
  if "%%a" == "-imqhome"	( set IMQ_HOME=%%b&set args_list=%%c&goto :processArgs )
  if "%%a" == "-javahome"	( set JAVA_HOME=%%b&set args_list=%%c&goto :processArgs )
  if "%%a" == "-jrehome"	( set JAVA_HOME=%%b&set args_list=%%c&goto :processArgs )
  if "%%a" == "-varhome"	( set JVM_ARGS=%JVM_ARGS% -Dimq.varhome=%%b&set args_list=%%c&goto :processArgs )
  if "%%a" == "-imqvarhome"	( set JVM_ARGS=%JVM_ARGS% -Dimq.varhome=%%b&set args_list=%%c&goto :processArgs )
  if "%%a" == "-vmargs"		( set JVM_ARGS=%JVM_ARGS% %%b&set args_list=%%c&goto :processArgs )
  if "%%a" == "-managed"	( set args_list=%%b&goto :processArgs )
  if "!arg:~0,2!" == "-D"	( set JVM_ARGS=%JVM_ARGS% %%a&set args_list=%%b %%c&goto :processArgs )

  set BKR_ARGS=%BKR_ARGS% %%a
  set args_list=%%b %%c
  goto :processArgs

)
:exitProcessArgs

if "%JAVA_HOME%" == "" (echo "Please set the JAVA_HOME environment variable or use -javahome" & goto end)

set JVM_ARGS=%JVM_ARGS% -Dimq.home=%IMQ_HOME%
set _classes=%DEPENDLIBS%\javax.jms-api.jar;%IMQ_HOME%\..\..\share\opt\classes;%DEPENDLIBS%\grizzly-framework.jar;%DEPENDLIBS%\grizzly-portunif.jar;%DEPENDLIBS%\hk2-runlevel.jar;%DEPENDLIBS%\hk2-api.jar;%DEPENDLIBS%\grizzly-http.jar;%DEPENDLIBS%\grizzly-http-server.jar;%DEPENDLIBS%\jakarta.servlet-api.jar;%DEPENDLIBS%\grizzly-websockets.jar;%DEPENDLIBS%\jakarta.json.jar;%DEPENDLIBS%\jhall.jar;%DEPENDLIBS%\jakarta.transaction-api.jar;%DEPENDLIBS%\fscontext.jar;%DEPENDLIBS%\audit.jar;%DEPENDLIBS%\bdb_je.jar;%IMQ_EXTERNAL%\*

echo JAVA_HOME is %JAVA_HOME%
echo JVM_ARGS is %JVM_ARGS%
echo IMQ_HOME is %IMQ_HOME%
echo BKR_ARGS is %BKR_ARGS%
echo IMQ_EXTERNAL is %IMQ_EXTERNAL%
echo CLASSPATH is %_classes%

:StartBroker

"%JAVA_HOME%\bin\java" -cp %_classes% %JVM_ARGS% %_mainclass% %BKR_ARGS%

REM # If Broker exits with 255 then we restart it
if ERRORLEVEL 255 ( sleep 1 & goto StartBroker )

endlocal

EXIT /b %ERRORLEVEL%
