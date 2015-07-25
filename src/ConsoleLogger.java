/*
 * Copyright 2010-2013 by David A. Parker <parker.david.a@gmail.com>
 *
 * This file is part of CheckValve, an HLDS/SRCDS query app for Android.
 *
 * CheckValve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 *
 * CheckValve is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the CheckValve source code.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

/*
 * PROGRAM:
 * ConsoleLogger.java
 *
 * DESCRIPTION:
 * Connect to a CheckValve Console Relay and print messges from the
 * specified game server.
 *
 * AUTHOR:
 * Dave Parker
 *
 * CHANGE LOG:
 *
 * April 7, 2015
 * - Initial release.
 */

import java.io.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

public class ConsoleLogger
{
    final static String PROGRAM_VERSION = "1.0";

    final static int PACKET_HEADER = 0xFFFFFFFF;
    final static byte PTYPE_IDENTITY_STRING = (byte) 0x00;
    final static byte PTYPE_HEARTBEAT = (byte) 0x01;
    final static byte PTYPE_CONNECTION_REQUEST = (byte) 0x02;
    final static byte PTYPE_CONNECTION_FAILURE = (byte) 0x03;
    final static byte PTYPE_CONNECTION_SUCCESS = (byte) 0x04;
    final static byte PTYPE_MESSAGE_DATA = (byte) 0x05;

    static boolean quietMode;
    static byte responseType;
    static byte protocolVersion;
    static int consoleRelayPort;
    static int serverTimestamp;
    static int packetLimit;
    static short contentLength;
    static String gameServerIP;
    static String gameServerPort;
    static String consoleRelayPassword;
    static String passwordString;
    static String input;
    static String output;
    static StringBuilder responseMessage;
    static Socket s;
    static InetAddress consoleRelayIP;
    static String[] fields = new String[6];

    // Make sure the data and request buffers have backing arrays
    static byte[] dataBytes = new byte[1024];
    static byte[] requestBytes = new byte[256];
    static ByteBuffer dataBuffer = ByteBuffer.wrap(dataBytes);
    static ByteBuffer requestBuffer = ByteBuffer.wrap(requestBytes);

    public static void main(String args[])
    {
        int i;
        int pos;
        int end;
        int received;

        Date tstamp = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

        packetLimit = 0;
        quietMode = false;

        dataBuffer.order(ByteOrder.LITTLE_ENDIAN);
        requestBuffer.order(ByteOrder.LITTLE_ENDIAN);

        String opt = new String();
        String val = new String();

        try
        {
            for( i = 0; i < args.length; i++ )
            {
                opt = args[i];

                if( opt.equals("-c") || opt.equals("--console-relay") )
                {
                    try
                    {
                        val = args[++i];
                    }
                    catch( ArrayIndexOutOfBoundsException e )
                    {
                        System.out.println("\nERROR: The option " + opt + " requires a value.");
                        usage();
                        System.exit(1);
                    }

                    String cr[] = val.split(":");

                    if( cr.length != 2 )
                    {
                        System.out.println("\nERROR: The Console Relay value must be an <ip>:<port> pair.");
                        usage();
                        System.exit(1);
                    }

                    consoleRelayIP = InetAddress.getByName(cr[0]);
                    consoleRelayPort = Integer.parseInt(cr[1]);
                }
                else if( args[i].equals("-g") || args[i].equals("--game-server") )
                {
                    try
                    {
                        val = args[++i];
                    }
                    catch( ArrayIndexOutOfBoundsException e )
                    {
                        System.out.println("\nERROR: The option " + opt + " requires a value.");
                        usage();
                        System.exit(1);
                    }

                    String gs[] = val.split(":");

                    if( gs.length != 2 )
                    {
                        System.out.println("\nERROR: The game server value must be an <ip>:<port> pair.");
                        usage();
                        System.exit(1);
                    }

                    gameServerIP = new String(gs[0]);
                    gameServerPort = new String(gs[1]);
                }
                else if( args[i].equals("-l") || args[i].equals("--limit") )
                {
                    try
                    {
                        packetLimit = Integer.parseInt(args[++i]);
                    }
                    catch( NumberFormatException nfe )
                    {
                        System.out.println("\nERROR: The value of " + opt + " must be a number.");
                        usage();
                        System.exit(1);
                    }
                    catch( ArrayIndexOutOfBoundsException e )
                    {
                        System.out.println("\nERROR: The option " + opt + " requires a value.");
                        usage();
                        System.exit(1);
                    }
                }
                else if( args[i].equals("-p") || args[i].equals("--password") )
                {
                    try
                    {
                        consoleRelayPassword = args[++i];
                    }
                    catch( ArrayIndexOutOfBoundsException e )
                    {
                        System.out.println("\nERROR: The option " + opt + " requires a value.");
                        usage();
                        System.exit(1);
                    }
                }
                else if( args[i].equals("-q") || args[i].equals("--quiet") )
                {
                    quietMode = true;
                }
                else if( args[i].equals("-h") || args[i].equals("--help") )
                {
                    usage();
                    System.exit(0);
                }
                else
                {
                    System.out.println("\nERROR: Invalid option: '" + opt + "'");
                    usage();
                    System.exit(1);
                }
            }

            if( consoleRelayIP == null )
            {
                System.out.println("\nERROR: You must specify a Console Relay server.");
                usage();
                System.exit(1);
            }

            if( consoleRelayIP == null || gameServerIP == null )
            {
                System.out.println("\nERROR: You must specify a game server.");
                usage();
                System.exit(1);
            }

            passwordString = "P ";

            if( consoleRelayPassword.length() > 0 )
                passwordString += consoleRelayPassword;

            // Build the connection request packet
            requestBuffer.putInt(PACKET_HEADER);
            requestBuffer.put(PTYPE_CONNECTION_REQUEST);
            requestBuffer.putShort((short)(passwordString.length()+gameServerIP.length()+gameServerPort.length()+3));
            requestBuffer.put(passwordString.getBytes("UTF-8")).put((byte)0);
            requestBuffer.put(gameServerIP.getBytes("UTF-8")).put((byte)0);
            requestBuffer.put(gameServerPort.getBytes("UTF-8")).put((byte)0);
            requestBuffer.flip();

            try
            {
                s = new Socket(consoleRelayIP, consoleRelayPort);
            }
            catch( Exception e )
            {
                System.out.println("Failed to connect to the CheckValve Console Relay server.");
                System.out.println(e.toString());
                System.exit(1);
            }

            if( ! s.isConnected() )
            {
                System.out.println("Failed to connect to the CheckValve Console Relay server.");
                System.exit(1);
            }

            s.setSendBufferSize(1024);
            s.setReceiveBufferSize(1024);
 
            InputStream in   = s.getInputStream();
            OutputStream out = s.getOutputStream();

            received = 0;

            for(;;)
            {
                // Shut down if a packet limit was specified and has been reached
                if( packetLimit > 0 && received >= packetLimit )
                    break;

                dataBuffer.clear();

                try
                {
                    // Get the first 5 bytes of the packet data (header and packet type)
                    in.read(dataBytes, 0, 5);
                }
                catch( SocketTimeoutException ste )
                {
                    continue;
                }

                // Make sure the header is valid
                if( dataBuffer.getInt() != PACKET_HEADER )
                    continue;

                // Get the packet type
                responseType = dataBuffer.get();

                // No need to do anything if this is a heartbeat
                if( responseType == PTYPE_HEARTBEAT )
                    continue;

                responseMessage = new StringBuilder();

                try
                {
                    // Get the content length
                    in.read(dataBytes, dataBuffer.position(), 2);
                    contentLength = dataBuffer.getShort();
                }
                catch( SocketTimeoutException ste )
                {
                    continue;
                }

                // Make sure the content length is valid
                if( contentLength < 1 || contentLength > 1024 )
                    continue;

                try
                {
                    // Read the rest of the packet data
                    in.read(dataBytes, dataBuffer.position(), contentLength);
                }
                catch( SocketTimeoutException ste )
                {
                    continue;
                }

                switch( responseType )
                {
                    case PTYPE_IDENTITY_STRING:
                        pos = dataBuffer.position();
                        end = pos + contentLength;

                        for( i = pos; i < end; i++ )
                            responseMessage.append((char)dataBuffer.get());

                        // Send the connection request
                        if( ! quietMode )
                        {
                            System.out.println("--> Server identifies itself as " + responseMessage);
                            System.out.println("--> Sending connection request.");
                        }

                        out.write(requestBuffer.array(), requestBuffer.position(), requestBuffer.limit());
                        out.flush();
                        break;

                    case PTYPE_CONNECTION_SUCCESS:
                        if( ! quietMode )
                            System.out.println("--> Connected to " + consoleRelayIP.getHostAddress() + ":" + consoleRelayPort + ".");

                        break;

                    case PTYPE_CONNECTION_FAILURE:
                        pos = dataBuffer.position();
                        end = pos + contentLength;

                        for( i = pos; i < end; i++ )
                            responseMessage.append((char)dataBuffer.get());

                        System.out.println("ERROR: Connection request was rejected by the server.");
                        System.out.println("Reason: " + responseMessage.substring(2, responseMessage.length()));
                        break;

                    case PTYPE_MESSAGE_DATA:
                        received++;

                        protocolVersion = dataBuffer.get();
                        serverTimestamp = dataBuffer.getInt();
                        tstamp = new Date((long)serverTimestamp*1000);
                        pos = dataBuffer.position();
                        end = pos + contentLength - 6;

                        for( i = pos; i < end; i++ )
                            responseMessage.append((char)dataBuffer.get());

                        fields = new String(responseMessage.toString().getBytes("UTF-8")).split("\u0000");

                        output  = "[" + fields[0] + "]";
                        output += "[" + fields[1] + "]";
                        output += "[" + sdf.format(tstamp) + "]";
                        output += "[" + fields[2] + "]";

                        System.out.println(output);
                        break;

                    default:
                        if( ! quietMode )
                            System.out.println("--> Unknown response type.  Re-sending request.");

                        out.write(requestBuffer.array(), 0, requestBuffer.position());
                        out.flush();
                        break;
                }
            }
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }

        System.exit(0);
    }

    private static void usage()
    {
        System.out.println("ConsoleLogger version " + PROGRAM_VERSION);
        System.out.println();
        System.out.println("Usage: java ConsoleLogger [--console-relay <ip>:<port>] [--game-server <ip>:<port>] [--limit <num>] [--password <password>] [--quiet]");
        System.out.println("       java ConsoleLogger [--help]");
        System.out.println();
        System.out.println("Command line options:");
        System.out.println("    -c|--console-relay <ip>:<port>  Connect to the CheckValve Console Relay at the specified IP and port (required).");
        System.out.println("    -g|--game-server <ip>:<port>    Request console messages from the game server at the specified IP and port (required).");
        System.out.println("    -l|--limit <num>                Stop the console logger after receiving <num> messages.");
        System.out.println("    -p|--password <password>        Specify the CheckValve Console Relay password.");
        System.out.println("    -q|--quiet                      Operate in quiet mode, suppress most output.");
        System.out.println("    -h|--help                       Show this help text and exit.");
        System.out.println();
    }
}
