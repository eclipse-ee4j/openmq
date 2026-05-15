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
REM imqkeytool is a wrapper script for JDK Keytool for generation of a keystore
REM and a self signed certificate for use with SSL.
REM
REM To generate keystore and self signed certificate for the broker
REM usage: imqkeytool [-broker]
REM
REM To generate keystore and a self-signed certificate for the HTTPS tunnel
REM servlet
REM usage: imqkeytool -servlet <keystore location>
REM

setlocal
set _cmd=broker
set _keystore=
set args_list=%*

call "%~dp0..\lib\imqinit.bat" %*
if ERRORLEVEL 1 goto end

:processArgs
FOR /f "tokens=1,2* delims= " %%a IN ("%args_list%") DO (
  if "%%a" == "-javahome"  ( set args_list=%%c&goto :processArgs )
  if "%%a" == "-jrehome"   ( set args_list=%%c&goto :processArgs )
  if "%%a" == "-varhome"   ( set args_list=%%c&goto :processArgs )
  if "%%a" == "-verbose"   ( set args_list=%%b %%c&goto :processArgs )
  if "%%a" == "-broker"    ( set _cmd=broker&set args_list=%%b %%c&goto :processArgs )
  if "%%a" == "-servlet"   ( set _cmd=servlet&set _keystore=%%b&set args_list=%%c&goto :processArgs )
  if not "%%a" == "" goto usage
)
:exitProcessArgs

if "%_cmd%" == "broker"  goto broker
if "%_cmd%" == "servlet" goto servlet
goto usage

:broker
echo Generating keystore for the broker ...
set _KEYSTORE=%IMQ_ETCHOME%\keystore
echo Keystore=%_KEYSTORE%
"%JAVA_HOME%\bin\keytool" -v -genkey -keyalg "RSA" -alias imq -keystore "%_KEYSTORE%"
goto end

:servlet
if "%_keystore%" == "" goto nopath
echo Generating keystore for the HTTPS tunnel servlet ...
echo Keystore=%_keystore%
"%JAVA_HOME%\bin\keytool" -v -genkey -keyalg "RSA" -alias imqservlet -keystore "%_keystore%"
if %ERRORLEVEL% == 0 (echo Make sure the keystore is accessible and readable by the HTTPS tunnel servlet.)
goto end

:nopath
echo Please specify keystore location for the -servlet option
goto usage

:usage
echo usage:
echo imqkeytool [-broker]
echo    generates a keystore and self-signed certificate for the broker
echo imqkeytool -servlet keystore_location
echo    generates a keystore and self-signed certificate for the HTTPS
echo    tunnel servlet, keystore_location specifies the name and location
echo    of the keystore file

:end
endlocal
EXIT /b %ERRORLEVEL%
