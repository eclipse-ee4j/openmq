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
REM Broker Administration (command line) startup script
REM

setlocal
set _mainclass=com.sun.messaging.jmq.admin.apps.broker.BrokerCmd

call "%~dp0..\lib\imqinit.bat" %*
if ERRORLEVEL 1 goto end

set JVM_ARGS=-Xmx128m "-Dimq.home=%IMQ_HOME%" "-Dimq.varhome=%IMQ_VARHOME%"
set _classes=%IMQ_HOME%\lib\imqadmin.jar;%IMQ_HOME%\lib\fscontext.jar
if not "%IMQ_EXT_JARS%" == "" set _classes=%_classes%;%IMQ_EXT_JARS%

"%JAVA_HOME%\bin\java" -cp "%_classes%" %JVM_ARGS% %_mainclass% %*

:end
endlocal
EXIT /b %ERRORLEVEL%
