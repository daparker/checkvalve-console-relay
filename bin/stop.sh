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

##
#
# PROGRAM:
# stop.sh
#
# DESCRIPTION:
# Stop an instance of CheckValve Console Relay.
#
# AUTHOR:
# Dave Parker
#
# CHANGE LOG:
#
# April 7, 2015
# - Initial release.
#
# July 25, 2015
# - Rewritten based on stop script for CheckValve Chat Relay.
# - Determine value of $BASEDIR automatically.
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
# PID and JAR files for CheckValve Console Relay
#
JARFILE="${BASEDIR}/lib/checkvalveconsolerelay.jar"
PIDFILE="${BASEDIR}/checkvalveconsolerelay.pid"

# Make sure the PID file exists
if [ ! -f $PIDFILE ] ; then
    echo >&2
    echo "ERROR: PID file $PIDFILE does not exist." >&2
    echo >&2
    exit 1
fi

TARGET_PID=$(cat $PIDFILE)

# Make sure the PID file is not empty
if [ -z $TARGET_PID ] ; then
    echo >&2
    echo "ERROR: PID file $PIDFILE is empty." >&2
    echo >&2
    exit 1
fi

# Make sure the PID file is writable
if (! touch ${PIDFILE} >/dev/null 2>&1) ; then
    echo >&2
    echo "ERROR: PID file ${PIDFILE} is not writable." >&2
    echo >&2
    exit 1
fi

# Make sure CheckValve Console Relay is running on the PID obtained from the PID file
if (ps -ef | grep -w $TARGET_PID | grep -m1 -v grep | grep $JARFILE >/dev/null) ; then
    kill -TERM $TARGET_PID

    if [ $? -eq 0 ] ; then
        echo "Stopped CheckValve Console Relay."
        >$PIDFILE
        exit 0
    else
        echo "Failed to stop CheckValve Console Relay (PID $TARGET_PID)."
        exit 1
    fi
else
    echo >&2
    echo "ERROR: Value in PID file is $TARGET_PID, but CheckValve Console Relay" >&2
    echo "       is not running on this PID." >&2
    echo >&2
fi

exit 0
