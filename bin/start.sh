#!/bin/bash
##
#
# Copyright 2010-2015 by David A. Parker <parker.david.a@gmail.com>
# 
# This file is part of CheckValve, an HLDS/SRCDS query app for Android.
# 
# CheckValve is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation.
# 
# CheckValve is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with the CheckValve source code.  If not, see
# <http://www.gnu.org/licenses/>.
#

##
#
# PROGRAM:
# start.sh
#
# DESCRIPTION:
# Start an instance of CheckValve Console Relay as a background daemon.
#
# AUTHOR:
# Dave Parker
#
# CHANGE LOG:
#
# November 14, 2013
# - Initial release.
#
# July 18, 2014
# - Added $CONFIG_FILE and the '--config' option for specifying the
#   configuration file.
#
# October 30, 2014
# - Added static default values for variables. 
# - Added a check for the existance of $BASEDIR.
#
# November 3, 2014
# - Fixed improper PATH order when using a bundled JRE.
#
# May 4, 2015
# - Moved constants and variables to the top of the script.
# - Set the value of $BASEDIR automatically.
#
# June 14, 2015
# - Replaced 'basename' with 'dirname' so setting $BASEDIR works correctly.
# - Fixed typos which caused syntax errors
#

##
#
# Store the current working directory
#
OLD_PWD=$(pwd)

##
#
# Set the CheckValve Console Relay base directory
#
THISDIR=$(dirname $0)
cd ${THISDIR}/../
BASEDIR=$(pwd)
cd ${OLD_PWD}

##
#
# Default configuration file
#
DEF_CONFIG_FILE="checkvalveconsolerelay.properties"
CONFIG_FILE=${DEF_CONFIG_FILE}

##
#
# Default debugging options
#
DEF_DEBUG_HOST="localhost"
DEF_DEBUG_PORT="1044"
DEBUG="0"
DEBUG_OPTS=""
DEBUG_HOST=${DEF_DEBUG_HOST}
DEBUG_PORT=${DEF_DEBUG_PORT}

##
#
# Default minimum heap size for CheckValve Console Relay
#
DEF_JVM_MIN_MEM="8m"
JVM_MIN_MEM=${DEF_JVM_MIN_MEM}

##
#
# Default maximum heap size for CheckValve Console Relay
#
DEF_JVM_MAX_MEM="16m"
JVM_MAX_MEM=${DEF_JVM_MAX_MEM}

##
#
# JAR and PID files for CheckValve Console Relay
#
JARFILE="${BASEDIR}/lib/checkvalveconsolerelay.jar"
PIDFILE="${BASEDIR}/checkvalveconsolerelay.pid"

# If a bundled JRE is present then use it
if [ -d ${BASEDIR}/jre ] ; then
    PATH="${BASEDIR}/jre/bin:${PATH}"
    export PATH

    # Set JAVA_HOME if it is not already set
    if [ -z JAVA_HOME ] ; then
        JAVA_HOME="${BASEDIR}/jre"
        export JAVA_HOME
    fi
fi

##
#
# Java executable
#
JAVA_BIN=$(which java)

short_usage()
{
    echo "Usage: $0 [--config <file>] [--minheap <size>] [--maxheap <size>] [--debug [--debughost <ip>] [--debugport <port>]]"
    echo "       $0 [--help|-h|-?]"
}

long_usage()
{
    echo
    short_usage
    echo
    echo "Command-line options:"
    echo
    echo "  --config <file>     Read config from <file> [default = ${DEF_CONFIG_FILE}]"
    echo "  --minheap <size>    Set the JVM's minimum heap size to <size> [default = ${DEF_JVM_MIN_MEM}]"
    echo "  --maxheap <size>    Set the JVM's maximum heap size to <size> [default = ${DEF_JVM_MAX_MEM}]"
    echo "  --debug             Enable the Java debugging listener (for use with jdb)"
    echo "  --debughost <ip>    IP for jdb connections if debugging is enabled [default = ${DEF_DEBUG_HOST}]"
    echo "  --debugport <port>  Port for jdb connections if debugging is enabled [default = ${DEF_DEBUG_PORT}]"
    echo "  --help | -h | -?    Show this help text and exit"
    echo
    echo "Note: For --minheap and --maxheap, the <size> value should be a number followed by k, m, or g"
    echo "      kilobytes, megabytes, or gigabytes, respectively.  (Ex: 1048576k, 1024m, 1g)."
    echo
}

start_debug()
{
    cd ${BASEDIR}

    # Set the JVM debugging options
    DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DEBUG_HOST}:${DEBUG_PORT}"

    # Start CheckValve Console Relay with debug options and save its PID to the PID file
    ${JAVA_BIN} ${DEBUG_OPTS} -Xms${JVM_MIN_MEM} -Xmx${JVM_MAX_MEM} -jar ${JARFILE} -c ${CONFIG_FILE} >/dev/null &
    echo "$!" > ${PIDFILE}
    echo "Started CheckValve Console Relay."
    echo "(Debugging mode is enabled, connect jdb to ${DEBUG_HOST}:${DEBUG_PORT} for debugging)."

    cd ${OLD_PWD}
}

start_no_debug()
{
    cd ${BASEDIR}

    # Start CheckValve Console Relay and save its PID to the PID file
    ${JAVA_BIN} -Xms${JVM_MIN_MEM} -Xmx${JVM_MAX_MEM} -jar ${JARFILE} -c ${CONFIG_FILE} &
    echo "$!" > ${PIDFILE}
    echo "Started CheckValve Console Relay."

    cd ${OLD_PWD}
}

# Set options from the command line
while [ "$1" ] ; do
    case $(echo "$1" | tr A-Z a-z) in
        '--config')
            if [ ! $2 ] ; then
                echo "You must supply a value for --config"
                short_usage
                exit 1
            fi
            CONFIG_FILE="$2"
            shift ; shift
            ;;
        '--debug')
            DEBUG="1"
            shift
            ;;
        '--debughost')
            if [ ! $2 ] ; then
                echo "You must supply a value for --debughost"
                short_usage
                exit 1
            fi
            DEBUG_HOST="$2"
            shift ; shift
            ;;
        '--debugport')
            if [ ! $2 ] ; then
                echo "You must supply a value for --debugport"
                short_usage
                exit 1
            fi
            DEBUG_PORT="$2"
            shift ; shift
            ;;
        '--minheap')
            if [ ! $2 ] ; then
                echo "You must supply a value for --minheap"
                short_usage
                exit 1
            fi
            JVM_MIN_MEM="$2"
            shift ; shift
            ;;
        '--maxheap')
            if [ ! $2 ] ; then
                echo "You must supply a value for --maxheap"
                short_usage
                exit 1
            fi
            JVM_MAX_MEM="$2"
            shift ; shift
            ;;
        '--help'|'-h'|'-?')
            long_usage
            exit 1
            ;;
        *)
            echo "Invalid option: $1"
            short_usage
            exit 1
            ;;
    esac
done

# Make sure the BASEDIR exists
if [ ! -d ${BASEDIR} ] ; then
    echo >&2
    echo "ERROR: The directory ${BASEDIR} does not exist."
    echo >&2
    echo "Please edit the BASEDIR variable in $0 and try again." >&2
    echo >&2
    exit 1
fi

# Make sure the $BASEDIR/lib directory exists
if [ ! -d ${BASEDIR}/lib ] ; then
    echo >&2
    echo "ERROR: The directory ${BASEDIR}/lib does not exist."
    echo >&2
    echo "Please edit the BASEDIR variable in $0 and try again." >&2
    echo >&2
    exit 1
fi

# Make sure the JAR file exists
if [ ! -f ${JARFILE} ] ; then
    echo >&2
    echo "ERROR: The file ${JARFILE} does not exist." >&2
    echo >&2
    exit 1
fi

# Make sure the PID file is writable
if (! touch ${PIDFILE} >/dev/null 2>&1) ; then
    echo >&2
    echo "ERROR: The file ${PIDFILE} is not writable." >&2
    echo >&2
    exit 1
fi

# Make sure the 'java' command is in the PATH
if [ "$JAVA_BIN" = "" ] ; then
    echo >&2
    echo "ERROR: Unable to locate the 'java' executable."
    echo >&2
    echo "Please ensure the Java Runtime Environment is installed" >&2
    echo "and the 'java' executable can be found in your PATH." >&2
    echo >&2
    exit 1
fi

# Make sure another instance is not already running
if [ -s ${PIDFILE} ] ; then
    TARGET_PID=$(cat ${PIDFILE})

    if (ps -ef | grep -w ${TARGET_PID} | grep -m1 -v grep | grep ${JARFILE} >/dev/null) ; then
        echo >&2
        echo "ERROR: CheckValve Console Relay is already running on PID ${TARGET_PID}." >&2
        echo >&2
        exit 1
    else
        echo >&2
        echo "WARNING: Replacing stale PID file (perhaps left over from an unclean shutdown)." >&2
        echo >&2
    fi
fi

if [ "$DEBUG" = "1" ] ; then
    start_debug
else
    start_no_debug
fi

exit 0
