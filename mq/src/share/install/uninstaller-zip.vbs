'
' Copyright (c) 2000-2017 Oracle and/or its affiliates. All rights reserved.
'
' This program and the accompanying materials are made available under the
' terms of the Eclipse Public License v. 2.0, which is available at
' http://www.eclipse.org/legal/epl-2.0.
'
' This Source Code may also be made available under the following Secondary
' Licenses when the conditions for such availability set forth in the
' Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
' version 2 with the GNU Classpath Exception, which is available at
' https://www.gnu.org/software/classpath/license.html.
'
' SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
'

PRODUCTNAME="mq"

Set wShell = CreateObject("WScript.Shell")
gReturnValue = wshell.Run("regsvr32 /s scrrun.dll", 0 ,True)
set gFileSystem = CreateObject("Scripting.FileSystemObject")
Set gWshEnv = wshell.Environment("SYSTEM")

JAVA_HOME=""
CMDLINEJAVA_HOME=""
SILENT=""

MYDIR=trim(Replace(Wscript.scriptFullName, Wscript.scriptName, ""))

' Relative paths to directories from top of umbrella directory
META_DATA_REL_PATH="var\install\contents\"+PRODUCTNAME
INIT_CONFIG_REL_PATH="\"+PRODUCTNAME+"\lib\install"
CONFIG_DIR_REL_PATH="var\install\config\"+PRODUCTNAME

' Construct umbrella directory based on where this script lives - in the Metadata directory
UMBRELLA_DIR=Left(Wscript.scriptFullName, Len(Wscript.scriptFullName)-Len(META_DATA_REL_PATH+"\")-Len(Wscript.scriptName))

' Construct absolute directory paths based on umbrella dir + relative paths
ENGINE_DIR=UMBRELLA_DIR+"install"
META_DATA_DIR=UMBRELLA_DIR+META_DATA_REL_PATH
INIT_CONFIG_DIR=UMBRELLA_DIR+INIT_CONFIG_REL_PATH
CONFIG_DIR=UMBRELLA_DIR+CONFIG_DIR_REL_PATH

UNINSTALL_PROP=MYDIR+"uninstaller.properties"

'LOGDIR=gFileSystem.GetSpecialFolder(2)

'-------------------------------------------------------------------------------
' perfor actual operation for the script: install/uninstall
' input(s):  none
' output(s): instCode
'-------------------------------------------------------------------------------
Function perform

ENGINE_OPS="-m ""file:///"+META_DATA_DIR+""""
ENGINE_OPS=ENGINE_OPS+" -p Default-Product-ID="+PRODUCTNAME
ENGINE_OPS=ENGINE_OPS+" -s """+CONFIG_DIR+""""
ENGINE_OPS=ENGINE_OPS+" -p ""Init-Config-Locations="+PRODUCTNAME+":"+INIT_CONFIG_DIR + """"
ENGINE_OPS=ENGINE_OPS+" -p UI-Options=internalBrowserOnly"

if DRYRUN <> "" Then
    ENGINE_OPS=ENGINE_OPS+" -n """+DRYRUN+""""
end if

if ANSWERFILE <> "" Then
    ENGINE_OPS=ENGINE_OPS+" -a """+ANSWERFILE+""""
end if

if SILENT <> "" Then
    ENGINE_OPS=ENGINE_OPS+" -p Display-Mode=SILENT"
end if

if ALTROOT <> "" Then
    ENGINE_OPS=ENGINE_OPS + " -R " + ALTROOT
end if

if LOGLEVEL <> "" Then
    ENGINE_OPS=ENGINE_OPS+" -l "+LOGLEVEL
end if

if LOGDIR <> "" Then
    ENGINE_OPS=ENGINE_OPS+" -p ""Logs-Location="+LOGDIR+""""
end if

if JAVA_HOME <> ""  Then
    ENGINE_OPS=ENGINE_OPS+" -j """+JAVA_HOME+""""
end if

if JAVA_OPTIONS <> "" Then
    ENGINE_OPS=ENGINE_OPS+" -J "+JAVA_OPTIONS
end if

if INSTALLPROPS <> "" Then
    ENGINE_OPS=ENGINE_OPS+INSTALLPROPS
end if

if INSTALLABLES <> "" Then
    ENGINE_OPS=ENGINE_OPS+" -i "+INSTALLABLES
end if

'WScript.echo "wscript //nologo " & chr(34) & ENGINE_DIR & "\bin\engine-wrapper.vbs" & chr(34) & " " &  ENGINE_OPS
mycmd = "wscript //nologo " & chr(34) & ENGINE_DIR & "\bin\engine-wrapper.vbs" & chr(34) & " " &  ENGINE_OPS
set oExec=wShell.exec(mycmd)

Do while oExec.Status = 0
	WScript.Sleep 500
Loop

End Function


'-------------------------------------------------------------------------------
' usage only: define what parameters are available here
' input(s):  exitCode
'-------------------------------------------------------------------------------
Function usage
'note: no way to make this silent because we don't know the setting yet
WScript.echo "Usage: " + Wscript.scriptName + " [OPTION]" & VBCr _
	& "" & VBCr _
	& "Options:" & VBCr _
	& "  -a <file>" & VBCr _
	& "  Specify an answer file to be used by installer in non interactive mode." & VBCr _
	& "" & VBCr _
	& "  -h" & VBCr _
	& "  Show usage help" & VBCr _
	& "" & VBCr _
	& "  -j <path>" & VBCr _
	& "  Specify java runtime to use to run the installer" & VBCr _
	& "" & VBCr _
	& "  -s" & VBCr _
	& "  Silent mode. No output will be displayed. Typically used with an answer file" & VBCr _
	& "  specified with -a" & VBCr _
	& "" & VBCr _
	& "  -n <file>" & VBCr _
	& "  Dry run mode. Does not uninstall anything. Uninstall responses are saved to <file> and " & VBCr _
	& "  can be used with -a to replicate this uninstall. " & VBCr _
	& "" & VBCr _
	& "  Exit status" & VBCr _
	& "  0	Success" & VBCr _
	& "  >0	An error occurred" & VBCr _
	& "" & VBCr _
	& "  Usage examples:" & VBCr _
	& "   Generate answer file" & VBCr _
	& "    uninstaller -n file1" & VBCr _
	& "" & VBCr _
	& "   Using an answer file in silent mode" & VBCr _
	& "    uninstaller -a file1 -s" & VBCr _

WScript.Quit(1)

End Function

' Set JAVA_HOME. This is done by looking for the JDK location in a file named jdklocation or 
' by using what the user specified via -j
' Some fallback is needed in case none of the above was specified
Function setJavaHome

' overwrite check if user specify javahome to use
if CMDLINEJAVA_HOME = "" Then
    ' Determine JDK location by looking for file named jdklocation
    Set filesys = CreateObject("Scripting.FileSystemObject")
    If filesys.FileExists(UNINSTALL_PROP)=True Then
        Set fIn = filesys.OpenTextFile(UNINSTALL_PROP)
        Do Until fIn.AtEndOfStream
            sLine = fIn.ReadLine
	    If (sLine <> "") then
	        JAVA_HOME=trim(sLine)
	        Exit Do
            End If
        Loop

	' Close file
	fIn.close
    Else
	' Need to determine VM location some other way
        JAVA_HOME="C:\Program Files\Java\jdk1.5.0_11"
    End If
else
  JAVA_HOME=CMDLINEJAVA_HOME
  if SILENT = "" Then
      WScript.Echo "Using the user specified JAVA_HOME "+CMDLINEJAVA_HOME
  end if
end if

End Function


'-------------------------------------------------------------------------------
' ****************************** MAIN THREAD ***********************************
'-------------------------------------------------------------------------------

' check arguments

Set args = WScript.Arguments
argumentCounter=0

do while argumentCounter < args.Length
argName=args.Item(argumentCounter)

select case argName

case "-a"
  if argumentCounter + 1 < args.Length Then
    ANSWERFILE=trim(args.Item(argumentCounter+1))
    argumentCounter=argumentCounter+2

	    if Not gFileSystem.FileExists(ANSWERFILE) Then
        ' we don't know yet if we are silent so echo anyway
		WScript.Echo "Cannot read the answer file"+ANSWERFILE
                WScript.Quit(1)
            end if
  Else
    usage
  End if

case "-R"
  if argumentCounter + 1 < args.Length Then
    ALTROOT=trim(args.Item(argumentCounter+1))
    argumentCounter=argumentCounter+2

	    if Not gFileSystem.FolderExists(ALTROOT) Then
        ' we don't know yet if we are silent to echo anyway
		WScript.Echo ALTROOT+" is not a valid alternate root"
                WScript.Quit(1)
            end if
  Else
    usage
  End if

case "-l"
  if argumentCounter + 1 < args.Length Then
    LOGDIR=trim(args.Item(argumentCounter+1))
    argumentCounter=argumentCounter+2

	    if Not gFileSystem.FolderExists(LOGDIR) Then
        'we don't know yet if we are silent so echo anyway
		WScript.Echo LOGDIR+" is not a directory or is not writable"
                WScript.Quit(1)
	    end if
  Else
    usage
  End if
case "-q"
  LOGLEVEL="WARNING"
  argumentCounter=argumentCounter+1

case "-v"
  LOGLEVEL="FINEST"
  argumentCounter=argumentCounter+1

case "-s"
  SILENT="true"
  argumentCounter=argumentCounter+1

case "-t"
  'we dont know yet if we are silent so echo anyway
  WScript.Echo "TextUI is not supported for Windows."
  argumentCounter=argumentCounter+1

case "-n"
  if argumentCounter + 1 < args.Length Then
    DRYRUN=trim(args.Item(argumentCounter+1))
    argumentCounter=argumentCounter+2
  Else
    usage
  End if

case "-j"

  if argumentCounter + 1 < args.Length Then
    CMDLINEJAVA_HOME=trim(args.Item(argumentCounter+1))
    argumentCounter=argumentCounter+2

    if Not gFileSystem.FolderExists(CMDLINEJAVA_HOME) Then
    'we don't know yet if we are silent so echo anyway
	WScript.Echo CMDLINEJAVA_HOME+" must be the root directory of a valid JVM installation"
               WScript.Quit(1)
    end if
  Else
    usage
  End if

case "-J"
  if argumentCounter + 1 < args.Length Then
    JAVAOPTIONS=trim(args.Item(argumentCounter+1))
    argumentCounter=argumentCounter+2
  Else
    usage
  End if

case "-p"
 if argumentCounter + 1 < args.Length Then
   INSTALLPROPS=INSTALLPROPS+" -p "+trim(args.Item(argumentCounter+1))
   argumentCounter=argumentCounter+2
  Else
    usage
  End if

case "-h"
 usage
 argumentCounter=argumentCounter+1

case Else
 usage

end select

Loop

setJavaHome

perform

if DRYRUN <> "" Then
  if SILENT = "" Then
      WScript.Echo "Dry run, not cleaning up additional data"
  end if
else
  if SILENT = "" Then
      WScript.Echo "Cleaning up remaining files and folders under " + UMBRELLA_DIR + ". Some files used which are used during uninstallation will remain. They can be manually discarded once the uninstall completes."
   end if
  set InstallDir = gFileSystem.getFolder(UMBRELLA_DIR+"install")
  InstallDir.Delete(true)
  if SILENT = "" Then
      WScript.Echo "Uninstallation complete"
  end if
end if
