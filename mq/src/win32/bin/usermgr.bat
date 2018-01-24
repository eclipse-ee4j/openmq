@echo off
REM
REM  Copyright (c) 2000-2017 Oracle and/or its affiliates. All rights reserved.
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

setlocal ENABLEDELAYEDEXPANSION
set _mainclass=com.sun.messaging.jmq.jmsserver.auth.usermgr.UserMgr

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
  if "!arg:~0,2!" == "-D"	( set JVM_ARGS=%JVM_ARGS% %%a&set args_list=%%b %%c&goto :processArgs )

  set BKR_ARGS=%BKR_ARGS% %%a
  set args_list=%%b %%c
  goto :processArgs

)
:exitProcessArgs

if "%JAVA_HOME%" == "" (echo "Please set the JAVA_HOME environment variable or use -javahome" & goto end)

set JVM_ARGS=%JVM_ARGS% -Dimq.home=%IMQ_HOME%
set _classes=%IMQ_HOME%\..\..\share\opt\classes;%DEPENDLIBS%\grizzly-framework.jar;%DEPENDLIBS%\grizzly-portunif.jar;%DEPENDLIBS%\glassfish-api.jar;%DEPENDLIBS%\hk2-api.jar;%DEPENDLIBS%\jhall.jar;%DEPENDLIBS%\javax.transaction-api.jar;%DEPENDLIBS%\fscontext.jar;%DEPENDLIBS%\audit.jar;%DEPENDLIBS%\bdb_je.jar;%IMQ_EXTERNAL%\*

"%JAVA_HOME%\bin\java" -cp %_classes% %JVM_ARGS% %_mainclass% %BKR_ARGS%

endlocal

EXIT /b %ERRORLEVEL%
