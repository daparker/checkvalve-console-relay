@echo off
REM
REM Copyright 2010-2015 by David A. Parker <parker.david.a@gmail.com>
REM
REM This file is part of CheckValve, an HLDS/SRCDS query app for Android.
REM
REM CheckValve is free software: you can redistribute it and/or modify
REM it under the terms of the GNU General Public License as published by
REM the Free Software Foundation.
REM
REM CheckValve is distributed in the hope that it will be useful,
REM but WITHOUT ANY WARRANTY; without even the implied warranty of
REM MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
REM GNU General Public License for more details.
REM
REM You should have received a copy of the GNU General Public License
REM along with the CheckValve source code.  If not, see
REM <http://www.gnu.org/licenses/>.
REM

REM
REM  PROGRAM:
REM  status.bat
REM
REM  DESCRIPTION:
REM  Get current status information from the CheckValve Console Relay
REM
REM  AUTHOR:
REM  Dave Parker
REM
REM  DATE:  
REM  July 25, 2015
REM

REM --- Default configuration file
set DEF_CONFIG_FILE=..\checkvalveconsolerelay.properties
set CONFIG_FILE=%DEF_CONFIG_FILE%

if "%1"=="" goto check_lib_dir
if "%1"=="--help" goto usage
if "%1"=="--config" (
    if "%2"=="" (
        echo.
        echo ERROR: The option --config requires a value.
        goto usage
    ) else (
        set CONFIG_FILE=%2
        goto check_lib_dir
    )
) else (
    echo.
    echo ERROR: Invalid option %1
    goto usage
)

REM --- Make sure the directory ..\lib exists
:check_lib_dir
if not exist ..\lib (
    goto no_lib_dir
) else (
    goto check_bundled_java
)

REM --- Check for a bundled JRE
:check_bundled_java
if exist ..\jre\bin\java.exe (
    set JAVA_BIN=..\jre\bin\java.exe
    goto run_program
) else (
    goto check_system_java
)

REM --- Check for Java in the system PATH
:check_system_java
java.exe -version >NUL 2>NUL

if errorlevel 1 (
    goto no_java
)
if errorlevel 0 (
    set JAVA_BIN=java.exe
    goto run_program
)

REM --- Display and error and exit if ..\lib does not exist
:no_lib_dir
echo.
echo ERROR: Could not find the 'lib' directory in the parent folder.
echo.
echo Please ensure that this script is being executed from the
echo <install_dir>\bin folder, where <install_dir> is the base
echo installation folder of the CheckValve Console Relay.
echo.
goto exit

REM --- Display an error and exit if Java could not be found
:no_java
echo.
echo ERROR: Unable to locate the 'java' executable.
echo.
echo Please ensure the Java Runtime Environment (JRE) is installed and
echo the 'java' executable can be found in your PATH.
echo.
goto exit

REM --- Run consolerelayctl to start the Console Relay 
:run_program
cd ..\lib
%JAVA_BIN% -jar consolerelayctl.jar status
cd ..\bin
goto exit

:usage
echo.
echo Usage: %0
echo        [--config ^<file^>]
echo        [--help]
echo.
echo Command-line options:
echo.
echo   --config ^<file^>  Read config from ^<file^>
echo                    [default = %DEF_CONFIG_FILE%]
echo.
echo   --help           Show this help text and exit
echo.
goto exit

:exit
pause
