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
# chat_logger.pl
#
# DESCRIPTION:
# Connect to a CheckValve Chat Relay and print messges from the specified game server.
#
# AUTHOR:
# Dave Parker
#
# CHANGE LOG:
#
# November 14, 2013
# - Initial release.
#
# July 3, 2014:
# - Version 1.1.
# - Removed short options from usage summary for brevity.
#

$|++;

use strict;
use POSIX;
use IO::Socket;
use Getopt::Long;

my $PROGRAM_VERSION = "1.1";

use constant {
	PACKET_HEADER            => "\xff\xff\xff\xff",
	PTYPE_IDENTITY_STRING    => 0x00,
	PTYPE_HEARTBEAT          => 0x01,
	PTYPE_CONNECTION_REQUEST => 0x02,
	PTYPE_CONNECTION_FAILURE => 0x03,
	PTYPE_CONNECTION_SUCCESS => 0x04,
	PTYPE_MESSAGE_DATA       => 0x05
};

use vars qw[ $help $prog $socket $cr $gs $cr_host $cr_port $gs_host $gs_port $pass $limit $quiet @tmp ];

$prog = $0;
$help = undef;
$cr = undef;
$gs = undef;
$pass = "";
$limit = 0;
$quiet = 0;
@tmp = undef;

# Get the command-line arguments
GetOptions(
	"chat-relay=s" => \$cr,
	"game-server=s" => \$gs,
	"password=s" => \$pass,
	"limit=i" => \$limit,
	"quiet" => \$quiet,
	"help" => \$help
);

# Show usage text and exit if the 'help' option was specified
if( defined($help) ) {
	usage();
	exit(1);
}

# Validate the Chat Relay information
if( ! defined($cr) ) {
	print "\nERROR: You must specify a Chat Relay server.\n";
	usage();
	exit(1);
}
elsif( index($cr, ':') == -1 ) {
	print "\nERROR: The Chat Relay value must be an <ip>:<port> pair.\n";
	usage();
	exit(1);
}
else {
	@tmp = split(/:/, $cr);
	$cr_host = $tmp[0];
	$cr_port = $tmp[1];

	if( ($cr_port !~ m/^\d{1,5}$/) or ($cr_port > 65535) ) {
		print "\nERROR: The Chat Relay port must be a number (1-65535).\n";
		usage();
		exit(1);
	}
}

# Validate the game server information
if( ! defined($gs) ) {
	print "\nERROR: You must specify a game server.\n";
	usage();
	exit(1);
}
elsif( index($gs, ':') == -1 ) {
	print "\nERROR: The game server value must be an <ip>:<port> pair.\n";
	usage();
	exit(1);
}
else {
	@tmp = split(/:/, $gs);
	$gs_host = $tmp[0];
	$gs_port = $tmp[1];

	if( ($gs_port !~ m/^\d{1,5}$/) or ($gs_port > 65535) ) {
		print "\nERROR: The game server port must be a number (1-65535).\n";
		usage();
		exit(1);
	}
}

connectToChatRelay();
getChatMessages();
exit(0);

sub connectToChatRelay {
	# Create the TCP socket or exit if it fails
	$socket = IO::Socket::INET->new(
		PeerAddr  => $cr_host,
		PeerPort  => $cr_port,
		Proto     => "tcp",
		Type      => SOCK_STREAM,
		Blocking  => 1
	) or die "ERROR: Failed to connect to the CheckValve Chat Relay server.\n($!)\n";
}

sub getChatMessages {
	my ($recv, $byte, $protocol, $response, $header, $message, $server_tstamp, $msg_tstamp, $st_flag, $say_team, $team, $name, $ip, $port, $tstamp);
	my @bytes;
	my @fields;

	# Assemble the connection request
	my $type = "\x02";
	my $body = "P " . $pass . "\x00" . $gs_host . "\x00" . $gs_port . "\x00";
        my $length = pack('v', length($body));
	my $buffersize = 1024;
	my $data  = PACKET_HEADER . $type . $length . $body;

	# Keep track of how many messages were received
        my $received = 0;

	while( $socket ) {
                last if( $limit > 0 && $received >= $limit );

		$socket->recv($recv, $buffersize);

		$header = substr($recv, 0, 4, '');
		$type = unpack "c", substr($recv, 0, 1, '');

		# Skip this packet if it's a heartbeat or has an invalid header
		next if( $type == PTYPE_HEARTBEAT || $header ne PACKET_HEADER );

		# Discard the content length bytes (not needed)
		substr($recv, 0, 2, '');

		if( $type == PTYPE_IDENTITY_STRING ) {
			print "--> Sending connection request.\n" if( ! $quiet );
			$socket->send($data);
		}
		elsif( $type == PTYPE_CONNECTION_FAILURE ) {
			$response = $recv;
			$response =~ s/^E //;
			print "ERROR: Connection request was rejected by the server ($response).\n";
			close($socket);
			exit(1);
		}
		elsif( $type == PTYPE_CONNECTION_SUCCESS ) {
			print "--> Connected successfully to " . $cr_host . ":" . $cr_port . ".\n" if ( ! $quiet );
		}
		elsif( $type == PTYPE_MESSAGE_DATA ) {
			# Get the protocol version
			$protocol = unpack "c", substr($recv, 0, 1, '');

			# Skip this message if the protocol version is not 1
			next if( $protocol != 0x01 );

			# Get the timestamp
			$server_tstamp = unpack "l", substr($recv, 0, 4, '');

			# Get the say_team flag
			$st_flag = unpack "c", substr($recv, 0, 1, '');

			# Parse the string fields of the packet data
			@fields = split(/\x00/, $recv);
			$ip         = $fields[0];
			$port       = $fields[1];
			$msg_tstamp = $fields[2];
			$name       = $fields[3];
			$team       = $fields[4];
			$message    = $fields[5];

			# Format the server timestamp 
			$tstamp = strftime("%m/%d/%Y %H:%M:%S", localtime($server_tstamp));

			# Format the say_team indicator
			$say_team = ($st_flag == 0x00)?"say":"say_team";

			# Print the message to standard output
			printf("[%s][%s][%s][%s][%s][%s][%s][%s]\n", $ip, $port, $tstamp, $msg_tstamp, $name, $team, $say_team, $message);

			$received++;
		}
		else {
			print "--> Unknown response type.  Re-sending request.\n" if( ! $quiet );
			$socket->send($data);
		}
	}
}

sub usage {
	print <<EOT
chat_logger.pl version $PROGRAM_VERSION

Usage: $prog [--chat-relay <ip>:<port>] [--game-server <ip>:<port>] [--limit <num>] [--password <password>] [--quiet]
       $prog [--help]

Command line options:
    -c|--chat-relay <ip>:<port>   Connect to the CheckValve Chat Relay at the specified IP and port (required).
    -g|--game-server <ip>:<port>  Request chat messages from the game server at the specified IP and port (required).
    -l|--limit <num>              Stop the chat logger after receiving <num> messages.
    -p|--password <password>      Specify the CheckValve Chat Relay password.
    -q|--quiet                    Operate in quiet mode, suppress most output.
    -h|--help                     Show this help text and exit.

EOT
}
