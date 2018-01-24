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

REM # This is a developer edition for internal use.
REM # ikeytool is a wrapper script around JDK keytool and is used to
REM # generate the keypair for SSL
REM #
REM # To generate keystore and self signed certificate for the broker
REM # usage: imqkeytool [-broker]
REM #
REM # To generate keystore and a self-signed certificate for the HTTPS
REM # tunnel servlet
REM # usage: imqkeytool -servlet <keystore location>
REM #
REM #

if not "%OS%"=="Windows_NT" goto notNT
setlocal

REM Specify additional arguments to the JVM here
set JVM_ARGS=

if "%IMQ_HOME%" == "" set IMQ_HOME=..


if "%1" == "-javahome" goto setjavahome
:resume

if "%JAVA_HOME%" == "" (echo Please set the JAVA_HOME environment variable
or use -javahome. & goto end)


if "%1" == "-servlet" goto servlet
if "%1" == "-broker" goto broker
if "%1" == "" goto broker
goto usage
:broker
REM
REM generate keystore and certificate for the broker
REM
echo "Generating keystore for the broker ..."
set _KEYSTORE=%IMQ_HOME%\etc\keystore

echo Keystore=%_KEYSTORE%

"%JAVAHOME%\bin\keytool" -v -genkey -keyalg "RSA" -alias imq -keystore "%_KEYSTORE%"

goto end
:servlet
REM
REM generate keystore and certificate for the HTTPS tunntel servlet
REM
if "%2" == "" goto nopath
set _KEYSTORE=%2
echo "Generating keystore for the HTTPS tunnel servlet ..."
echo Keystore=%_KEYSTORE%
"%JAVAHOME%\bin\keytool" -v -genkey -keyalg "RSA" -alias imqservlet -keystore "%_KEYSTORE%"
if %ERRORLEVEL% == 0 (echo Make sure the keystore is accessible and readable by the HTTPS tunnel servlet.)
goto end

:setjavahome
set JAVA_HOME=%2
shift
shift
goto resume

:nopath
echo Please specify keystore location for the -servlet option
goto usage
:usage
(echo usage:)
(echo imqkeytool [-broker])
(echo    generates a keystore and self-signed certificate for the broker)
(echo imqkeytool -sevlet keystore_location)
(echo    generates a keystore and self-signed certificate for the HTTPS)
(echo    tunnel servlet, keystore_location specifies the name and location)
(echo    of the keystore file)
goto end
:notNT
echo The iMQ keytool requires Windows NT or Windows 2000

:end
if "%OS%"=="Windows_NT" endlocal
