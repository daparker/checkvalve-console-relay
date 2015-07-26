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
# status.sh
#
# DESCRIPTION:
# Get status information from the CheckValve Console Relay.
#
# AUTHOR:
# Dave Parker
#
# CHANGE LOG:
#
# July 25, 2015
# - Initial release.
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
# JAR file for the CheckValve Console Relay Control
#
JARFILE="consolerelayctl.jar"

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
    echo "Usage: $0 [--config <file>] [--help|-h|-?]"
}

long_usage()
{
    echo
    short_usage
    echo
    echo "Command-line options:"
    echo
    echo "  --config <file>     Read config from <file> [default = ${DEF_CONFIG_FILE}]"
    echo "  --help | -h | -?    Show this help text and exit"
    echo
}

start_no_debug()
{
    cd ${BASEDIR}/lib

    # Start CheckValve Console Relay and save its PID to the PID file
    ${JAVA_BIN} -jar ${JARFILE} -c ${CONFIG_FILE} status

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

exit 0
