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

:: Name of this utility
set MQMIGRATE=mqmigrate
:: Default MQ Src Install Dir to copy data FROM
set DEFAULT_MQSRCDIR=c:\Sun\Message_Queue
:: Default MQ Dst Install Dir to copy data TO
:: XXX
set DEFAULT_MQDSTDIR="c:\Program Files\Sun\JES5\Message_Queue"

::
:: Command line options
::
set SRC_FLAG_SHORT=-srcdir
set SRC_FLAG_LONG=--srcdir
set DST_FLAG_SHORT=-dstdir
set DST_FLAG_LONG=--dstdir

:: Initialize MQ src dir to Message_Queue
set MQSRCDIR=c:\Sun\Message_Queue
:: Initialize MQ dst dir to Message_Queue
set MQDSTDIR="c:\Program Files\Sun\JES5\Message_Queue"

:: Build the MQSRCDIR and MQDSTDIR variables
:: on the command line
goto getArgs
:postGetArgs

echo debug: in postgetargs
echo debug: MQSRCDIR is %MQSRCDIR%
echo debug: MQDSTDIR is %MQDSTDIR%

echo %MQMIGRATE%: Source MQ InstallDir: %MQSRCDIR%
echo %MQMIGRATE%:   Dest MQ InstallDir: %MQDSTDIR%
:: Check srcdir and dstdir actually exist along with a
:: var\instances directory.
goto checkSrcDir
goto checkDstDir

goto end

:::::::::::::::::
:: subroutines ::
:::::::::::::::::

::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:: getArgs
:: Fill the MQSRCDIR and MQDSTDIR variables with the command line args
:: If the -srcdir or the -dstdir options are detected save the next 
:: argument in the MQSRCDIR variable or MQDSTDIR variable. 
:: This goto loop keeps shifiting until the %1 variable is empty
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

:getArgs
echo debug: in getargs percent1 is %1
if %1x==x goto postGetArgs
if %1==%SRC_FLAG_SHORT% goto getSrcDir
if %1==%SRC_FLAG_LONG%  goto getSrcDir
if %1==%DST_FLAG_SHORT% goto getDstDir
if %1==%DST_FLAG_LONG%  goto getDstDir
if not %1x==x (echo %MQMIGRATE%: illegal option -- %1& goto usage)
shift
goto getArgs


::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:: getSrcDir
:: Set the MQSRCDIR variable to the value after -srcdir.
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

:getSrcDir

:: instance flag
:: index to argument after instance flag
shift
echo debug: in getSrcDir
:: set instance name to the next argument or the default instance 
set MQSRCDIR=%1
if %MQSRCDIR%x==x (echo %MQMIGRATE%: No value specified for -srcdir.&  echo %MQMIGRATE%: Using %DEFAULT_MQSRCDIR% for the -srcdir value.& set MQSRCDIR=%DEFAULT_MQSRCDIR%)
shift
echo debug: MQSRCDIR is %MQSRCDIR%
goto getArgs


::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:: getDstDir
:: Set the MQDSTDIR variable to the value after -dstdir
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

:getDstDir

:: instance flag
:: index to argument after instance flag
shift
echo debug: in getDstDir
:: set instance name to the next argument or the default instance 
set MQDSTDIR=%1
if %MQDSTDIR%x==x (echo %MQMIGRATE%: No value specified for -dstdir.& echo %MQMIGRATE%: Using %DEFAULT_MQDSTDIR% for the -dstdir value.& set MQDSTDIR=%DEFAULT_MQDSTDIR%)
shift
echo debug: MQDSTDIR is %MQDSTDIR%
goto getArgs


::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:: checkSrcDir
:: check that MQSRCDIR is set
:: check that the value defined in MQSRCDIR exists on the filesystem
:: check that the value defined in MQSRCDIR\var\instances exists on the filesystem
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:checkSrcDir

echo debug: in checkSrcDir

:: Check MQSRCDIR defined
if %MQSRCDIR%x==x (echo %MQMIGRATE%: Please specify a Message Queue installation to migrate the data from using the -srcdir option.& goto end)

:: Check MQSRDIR exists
if not exist %MQSRCDIR% (echo %MQMIGRATE%: Cannot locate a Message Queue installation in %MQSRCDIR% to migrate the data FROM.& goto end)

:: Check MQSRDIR\var\instances exists
if not exist %MQSRCDIR%\var\instances (echo %MQMIGRATE%: Cannot locate Message Queue data in %MQSRCDIR%\var\instances to migrate data FROM.& goto end)

::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:: checkDstDir
:: check that MQDSTDIR is set
:: check that the value defined in MQDSTDIR exists on the filesystem
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:checkDstDir

echo debug: in checkDstDir

:: Check MQDSTDIR defined
if %MQDSTDIR%x==x (echo %MQMIGRATE%: Please specify a Message Queue installation to migrate the data from using the -dstdir option.& goto end)

:: Check MQDSTDIR exists
if not exist %MQDSTDIR% (echo %MQMIGRATE%: Cannot locate a Message Queue installation in %MQDSTDIR% to migrate the dat TO.& goto end)

rem :: Check MQDSTDIR\var\instances exists
rem if not exist %MQDSTDIR%\var\instances (echo %MQMIGRATE%: Cannot locate Message Queue data to migrate to in %MQDSTDIR%\var\instances.& goto end)

goto end

::::::::::::
:: Errors ::
::::::::::::

:notAdmin
echo %MQMIGRATE%: You must be a member of the Administrator group to administer the console
goto end


:usage
echo Usage: mqmigrate [-srcdir MQ_Src_InstallDir] [-dstdir MQ_Dst_InstallDir]
echo        (Double quote InstallDirs with spaces.)
goto end


:end
echo debug: in end

