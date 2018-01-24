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

REM # %1 is the INSTALL_ROOT_DIR

if %OS%x == Windows_NTx setlocal

set INSTALL_ROOT=%1
rem set JDK_HOME=%_IMQ_JAVAHOME%

rem echo INSTALL_ROOT is %INSTALL_ROOT%
if %INSTALL_ROOT%x==x echo "Usage: %0% <Install_Root_Dir>" && goto end

set IMQ_ROOT=%INSTALL_ROOT%\imq
rem echo imqroot is %IMQ_ROOT%

rem Write out JDK_HOME value to var\jdk.env if specified.
rem del /f/q %IMQ_ROOT%\var\jdk-env.bat 2> nul
rem if not "%JDK_HOME%x"=="x" echo set IMQ_JAVAHOME=%JDK_HOME%> %IMQ_ROOT%\var\jdk-env.bat

md %IMQ_ROOT%\var 2> nul
md %IMQ_ROOT%\var\security 2> nul
md %IMQ_ROOT%\var\instances 2> nul
md %IMQ_ROOT%\var\lib 2> nul
rem copy /b "%IMQ_ROOT%\var.init\lib\README.txt" "%IMQ_ROOT%\var\lib" 2> nul
rem if exist %IMQ_ROOT%\var.init\jdk-env.bat copy /b %IMQ_ROOT%\var.init\jdk-env.bat %IMQ_ROOT%\var 2> nul

rem Copy security files only if they do not already exist.
rem if not exist "%IMQ_ROOT%\var\security\accesscontrol.properties" echo "Copying accesscontrol.properties" && copy /b "%IMQ_ROOT%\var.init\security\accesscontrol.properties" "%IMQ_ROOT%\var\security"
rem if not exist "%IMQ_ROOT%\var\security\passwd" echo "Copying passwd" && copy /b "%IMQ_ROOT%\var.init\security\passwd" "%IMQ_ROOT%\var\security"

if "%OS%x" == "Windows_NTx" endlocal
