#!/usr/bin/perl -w
##
#
# Copyright 2010-2013 by David A. Parker <parker.david.a@gmail.com>
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
# udp_message_emitter.pl
#
# DESCRIPTION:
# Emit a simulated SRCDS log message via UDP for the purpose of testing the
# CheckValve Chat Relay.
#
# AUTHOR:
# Dave Parker
#
# CHANGE LOG:
#
# November 14, 2013
# - Initial release.
#
# July 1, 2014
# - Version 2.0.
# - Added option and code to send a simulated SRCDS console message.
# - Removed short options from usage summary for brevity.
#

$|++;

use strict;
use POSIX;
use IO::Socket;
use Getopt::Long;

my $PROGRAM_VERSION = "2.0";

my $prog = $0;
my $local_host = "127.0.0.1";
my $local_port = 2345;
my $peer_host = "127.0.0.1";
my $peer_port = 12345;
my $text = "This is a test!";
my $delay = 1;
my $limit = 0;
my $say_team = 0;
my $console = 0;
my $help = undef;
my $to = undef;
my $from = undef;
my @tmp = undef;

GetOptions(
	"to=s" => \$to,
	"from=s" => \$from,
	"delay=i" => \$delay,
	"limit=i" => \$limit,
	"message=s" => \$text,
	"sayteam" => \$say_team,
	"console" => \$console,
	"help" => \$help
) or ($help = 1);

if( defined($help) ) {
	usage();
	exit(1);
}

if( defined($to) ) {
	if( index($to, ':') == -1 ) {
		print "\nERROR: The 'to' address must be an <ip>:<port> pair.\n";
		usage();
		exit(1);
	}

	@tmp = split(/:/, $to);
	$peer_host = $tmp[0];
	$peer_port = $tmp[1];

	if( ($peer_port !~ m/^\d{1,5}$/) or ($peer_port > 65535) ) {
		print "\nERROR: The 'to' port must be a number (1-65535).\n";
		usage();
		exit(1);
	}
}

if( defined($from) ) {
	if( index($from, ':') == -1 ) {
		print "\nERROR: The 'from' address must be an <ip>:<port> pair.\n";
		usage();
		exit(1);
	}

	@tmp = split(/:/, $from);
	$local_host = $tmp[0];
	$local_port = $tmp[1];

	if( ($local_port !~ m/^\d{1,5}$/) or ($local_port > 65535) ) {
		print "\nERROR: The 'from' port must be a number (1-65535).\n";
		usage();
		exit(1);
	}
}

my $say = ($say_team > 0)?"say_team":"say";
my $time = strftime("%m/%d/%Y - %H:%M:%S", localtime);
my $data = qq|\xff\xff\xff\xff\x52L $time: |;

if( $console > 0 ) {
	$data .= qq|"Console<0><Console><Console>" |;
}
else {
	$data .= qq|"SomePlayer<99><STEAM_1:1:01234567><Survivor><Biker><ALIVE><80+0><setpos_exact 5033.42 -13677.53 -1.97; setang -0.53 175.02 0.00><Area 76076>" |;
}

$data .= qq|$say "$text"|;

my $sock = new IO::Socket::INET (
	PeerHost => $peer_host,
	PeerPort => $peer_port,
        LocalHost => $local_host,
        LocalPort => $local_port,
	Proto => 'udp'
) or die "Could not create socket: $!\n";

if( $limit > 0 ) {
	for( my $i = 0; $i < $limit; $i++ ) {
		$sock->autoflush;
		$sock->send($data);
		sleep($delay);
	}
}
else {
	while( 1 ) {
		$sock->autoflush;
		$sock->send($data);
		sleep($delay);
	}
}

close($sock);
exit(0);

sub usage {
	print <<EOT
udp_message_emitter.pl version $PROGRAM_VERSION

Usage: $prog [--to <ip>:<port>] [--from <ip>:<port>] [--delay <num>] [--limit <num>] [--message <string>] [--sayteam] [--console]
       $prog [--help]

Command line options:
    -t|--to <ip>:<port>    Send messages to the listener at the specified IP and port (default = 127.0.0.1:12345)
    -f|--from <ip>:<port>  Send messages from the specified IP and port (default = 127.0.0.1:2345)
    -d|--delay <num>       Send a message every <num> seconds (default = 1)
    -l|--limit <num>       Stop after sending <num> messages (default = no limit)
    -m|--message <string>  Send <string> as the message text (default = "This is a test!")
    -s|--sayteam           Make this a say_team message (default = say)
    -c|--console           Make this a SRCDS console message
    -h|--help              Show this help text and exit

NOTE: When using the --from option, the address must be assigned to an available network interface.

EOT
}
