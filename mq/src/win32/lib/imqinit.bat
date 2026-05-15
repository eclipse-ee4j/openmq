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
REM Common initialization script used by all imq wrapper scripts.
REM Must be invoked with CALL and all caller args: call imqinit.bat %*
REM Must NOT use setlocal so that variables persist to the calling script.
REM
REM After this script runs the following variables are defined:
REM
REM   JAVA_HOME       Location of Java to use
REM   IMQ_JAVAREASON  Where JAVA_HOME was resolved from
REM   IMQ_HOME        Root of Message Queue installation
REM   IMQ_ETCHOME     Root of etc subtree
REM   IMQ_LIBHOME     Root of lib subtree
REM   IMQ_VARHOME     Root of var subtree
REM   IMQ_EXT_JARS    External JARs to include on the classpath
REM   IMQ_VERBOSE     Set to "true" if -verbose was found in the args
REM

REM Determine IMQ_HOME from this script's location (lib\imqinit.bat -> parent = IMQ_HOME)
set IMQ_HOME=%~dp0..

REM Set directory defaults relative to IMQ_HOME
if "%IMQ_ETCHOME%" == "" set IMQ_ETCHOME=%IMQ_HOME%\etc
if "%IMQ_LIBHOME%" == "" set IMQ_LIBHOME=%IMQ_HOME%\lib
if "%IMQ_VARHOME%" == "" set IMQ_VARHOME=%IMQ_HOME%\var

REM Load imqenv.conf. Only lines beginning with "set IMQ_DEFAULT_" are processed,
REM which are the active (uncommented) settings. REM-prefixed lines are ignored.
if exist "%IMQ_ETCHOME%\imqenv.conf" (
    for /f "usebackq tokens=*" %%a in (`findstr /b /i "set IMQ_DEFAULT_" "%IMQ_ETCHOME%\imqenv.conf" 2^>nul`) do %%a
)

REM Apply IMQ_DEFAULT_* variables loaded from imqenv.conf
if not "%IMQ_DEFAULT_JAVAHOME%" == "" (
    set JAVA_HOME=%IMQ_DEFAULT_JAVAHOME%
    set IMQ_JAVAREASON=imqenv.conf
    set IMQ_DEFAULT_JAVAHOME=
)
if not "%IMQ_DEFAULT_VARHOME%" == "" (
    set IMQ_VARHOME=%IMQ_DEFAULT_VARHOME%
    set IMQ_DEFAULT_VARHOME=
)
if not "%IMQ_DEFAULT_ETCHOME%" == "" (
    set IMQ_ETCHOME=%IMQ_DEFAULT_ETCHOME%
    set IMQ_DEFAULT_ETCHOME=
)
if not "%IMQ_DEFAULT_EXT_JARS%" == "" (
    set IMQ_EXT_JARS=%IMQ_DEFAULT_EXT_JARS%
    set IMQ_DEFAULT_EXT_JARS=
)

REM Override JAVA_HOME with IMQ_JAVAHOME environment variable
if not "%IMQ_JAVAHOME%" == "" (
    set JAVA_HOME=%IMQ_JAVAHOME%
    set IMQ_JAVAREASON=IMQ_JAVAHOME
)

REM Scan caller args for -javahome, -jrehome, -varhome, -verbose.
REM This mirrors imqinit reading $@ without consuming from it:
REM the caller's %* is unchanged and will be passed through to Java as-is.
set _imq_args=%*
:_imqinit_scan
for /f "tokens=1,2* delims= " %%a in ("%_imq_args%") do (
    if /i "%%a" == "-javahome" ( set JAVA_HOME=%%b&set IMQ_JAVAREASON=-javahome&set _imq_args=%%c&goto :_imqinit_scan )
    if /i "%%a" == "-jrehome"  ( set JAVA_HOME=%%b&set IMQ_JAVAREASON=-jrehome&set _imq_args=%%c&goto :_imqinit_scan )
    if /i "%%a" == "-varhome"  ( set IMQ_VARHOME=%%b&set _imq_args=%%c&goto :_imqinit_scan )
    if /i "%%a" == "-verbose"  ( set IMQ_VERBOSE=true&set _imq_args=%%b %%c&goto :_imqinit_scan )
    if not "%%a" == ""         ( set _imq_args=%%b %%c&goto :_imqinit_scan )
)
set _imq_args=

REM Validate Java location
if "%JAVA_HOME%" == "" (
    echo Error: Java location not set.
    echo Set JAVA_HOME, IMQ_JAVAHOME, or pass -javahome to specify the Java runtime.
    EXIT /b 1
)
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Error: Invalid Java location: %JAVA_HOME%
    echo Java location was specified using: %IMQ_JAVAREASON%
    EXIT /b 1
)

REM Verbose output (mirrors imqinit verbose block)
if "%IMQ_VERBOSE%" == "true" (
    echo.
    echo Environment is:
    echo     Java location     : %JAVA_HOME%
    echo     Java specified by : %IMQ_JAVAREASON%
    echo     IMQ_HOME          : %IMQ_HOME%
    echo     IMQ_VARHOME       : %IMQ_VARHOME%
    echo     IMQ_ETCHOME       : %IMQ_ETCHOME%
    echo     IMQ_LIBHOME       : %IMQ_LIBHOME%
    echo     IMQ_EXT_JARS      : %IMQ_EXT_JARS%
)

EXIT /b 0
