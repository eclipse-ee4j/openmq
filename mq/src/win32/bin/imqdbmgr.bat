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
REM Message Queue Database Administration startup script
REM
REM Script specific properties:
REM   -javahome <path>  Use <path> as the location of the Java runtime
REM

setlocal ENABLEDELAYEDEXPANSION
set _mainclass=com.sun.messaging.jmq.jmsserver.persist.jdbc.DBTool

call "%~dp0..\lib\imqinit.bat" %*
if ERRORLEVEL 1 goto end

set IMQ_EXTERNAL=%IMQ_HOME%\lib\ext
set JVM_ARGS=
set BKR_ARGS=
set args_list=%*

:processArgs
FOR /f "tokens=1,2* delims= " %%a IN ("%args_list%") DO (
  if "%%a" == "-javahome"  ( set args_list=%%c&goto :processArgs )
  if "%%a" == "-vmargs"    ( set JVM_ARGS=%JVM_ARGS% %%b&set args_list=%%c&goto :processArgs )
  if "%%a" == "-verbose"   ( set args_list=%%b %%c&goto :processArgs )
  set BKR_ARGS=%BKR_ARGS% %%a
  set args_list=%%b %%c
  goto :processArgs
)
:exitProcessArgs

set JVM_ARGS=-Xmx128m "-Dimq.home=%IMQ_HOME%" "-Dimq.varhome=%IMQ_VARHOME%" "-Dimq.libhome=%IMQ_LIBHOME%" "-Dimq.etchome=%IMQ_ETCHOME%" %JVM_ARGS%
set _classes=%IMQ_HOME%\lib\imqbroker.jar;%IMQ_EXTERNAL%\*
for %%f in ("%IMQ_EXTERNAL%\*.zip") do set _classes=!_classes!;%%f
if not "%IMQ_EXT_JARS%" == "" set _classes=%_classes%;%IMQ_EXT_JARS%

"%JAVA_HOME%\bin\java" -cp "%_classes%" %JVM_ARGS% %_mainclass% %BKR_ARGS%

:end
endlocal
EXIT /b %ERRORLEVEL%
