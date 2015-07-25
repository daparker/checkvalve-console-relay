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
 * UDPMessageEmitter.java
 *
 * DESCRIPTION:
 * Emit a simulated SRCDS log message via UDP for the purpose of testing the
 * CheckValve Chat Relay.
 *
 * AUTHOR:
 * Dave Parker
 *
 * CHANGE LOG:
 *
 * November 14, 2013
 * - Initial release.
 *
 * July 1, 2014
 * - Version 2.0.
 * - Added option and code to send a simulated SRCDS console message.
 * - Removed short options from usage summary for brevity.
 */

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class UDPMessageEmitter
{
    final static String PROGRAM_VERSION = "2.0";

    static InetAddress peerHost;
    static InetAddress localHost;
    static boolean sayTeam;
    static boolean console;
    static String text;
    static String say;
    static int localPort;
    static int peerPort;
    static int delay;
    static int limit;

    public static void main(String args[])
    {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy - HH:mm:ss");

        try
        {
            // Defaults
            localHost = InetAddress.getByName("127.0.0.1");
            localPort = 2345;
            peerHost = InetAddress.getByName("127.0.0.1");
            peerPort = 12345;
            sayTeam = false;
            console = false;
            limit = 0;
            delay = 1;
            text = "This is a test!";

            if( args.length > 0 )
                parseOptions(args);

            String data = new String();

            //data = data.concat("\u00FF\u00FF\u00FF\u00FF\u0052");
            data = data.concat("L " + sdf.format(System.currentTimeMillis()) + ": ");

            if( console )
            {
                data = data.concat("\"Console<0><Console><Console>\" ");
            }
            else
            {
                data = data.concat("\"SomePlayer<99><STEAM_1:1:01234567>");
                data = data.concat("<Survivor><Biker><ALIVE><80+0><setpos_exact 5033.42 -13677.53 -1.97; setang -0.53 175.02 0.00><Area 76076>\" ");
            }

            data = data.concat(say  + " \"" + text + "\"");

            sendMessage(data);
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
    }

    private static void parseOptions( String args[] )
    {
        String opt = new String();
        String val = new String();

        for( int i = 0; i < args.length; i++ )
        {
            opt = args[i];

            if( opt.equals("-t") || opt.equals("--to") )
            {
                try
                {
                    val = args[++i];

                    String to[] = val.split(":");

                    if( to.length != 2 )
                    {
                        System.out.println("\nERROR: The 'to' address must be an <ip>:<port> pair.");
                        usage();
                        System.exit(1);
                    }

                    peerHost = InetAddress.getByName(to[0]);
                    peerPort = Integer.parseInt(to[1]);

                    if( peerPort < 1 || peerPort > 65535 )
                    {
                        System.out.println("\nERROR: The 'to' port must be a number (1-65535).");
                        usage();
                        System.exit(1);
                    }
                }
                catch( ArrayIndexOutOfBoundsException e )
                {
                    System.out.println("\nERROR: The option " + opt + " requires a value.");
                    usage();
                    System.exit(1);
                }
                catch( NumberFormatException e )
                {
                    System.out.println("\nERROR: The option " + opt + " requires a value.");
                    usage();
                    System.exit(1);
                }
                catch( UnknownHostException e )
                {
                    String host = val.substring(0, val.indexOf(":"));
                    System.out.println("\nERROR: Unknown host " + host + ".");
                    usage();
                    System.exit(1);
                }
            }
            else if( args[i].equals("-f") || args[i].equals("--from") )
            {
                try
                {
                    val = args[++i];

                    String from[] = val.split(":");

                    if( from.length != 2 )
                    {
                        System.out.println("\nERROR: The 'from' address must be an <ip>:<port> pair.");
                        usage();
                        System.exit(1);
                    }

                    localHost = InetAddress.getByName(from[0]);
                    localPort = Integer.parseInt(from[1]);

                    if( localPort < 1 || localPort > 65535 )
                    {
                        System.out.println("\nERROR: The 'from' port must be a number (1-65535).");
                        usage();
                        System.exit(1);
                    }
                }
                catch( ArrayIndexOutOfBoundsException e )
                {
                    System.out.println("\nERROR: The option " + opt + " requires a value.");
                    usage();
                    System.exit(1);
                }
                catch( NumberFormatException e )
                {
                    System.out.println("\nERROR: The option " + opt + " requires a value.");
                    usage();
                    System.exit(1);
                }
                catch( UnknownHostException e )
                {
                    String host = val.substring(0, val.indexOf(":"));
                    System.out.println("\nERROR: Unknown host " + host + ".");
                    usage();
                    System.exit(1);
                }
            }
            else if( args[i].equals("-l") || args[i].equals("--limit") )
            {
                try
                {
                    limit = Integer.parseInt(args[++i]);
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
            else if( args[i].equals("-d") || args[i].equals("--delay") )
            {
                try
                {
                    delay = Integer.parseInt(args[++i]);
                }
                catch( ArrayIndexOutOfBoundsException e )
                {
                    System.out.println("\nERROR: The option " + opt + " requires a value.");
                    usage();
                    System.exit(1);
                }
                catch( NumberFormatException nfe )
                {
                    System.out.println("\nERROR: The value of " + opt + " must be a number.");
                    usage();
                    System.exit(1);
                }
            }
            else if( args[i].equals("-m") || args[i].equals("--message") )
            {
                try
                {
                    text = args[++i];
                }
                catch( ArrayIndexOutOfBoundsException e )
                {
                    System.out.println("\nERROR: The option " + opt + " requires a value.");
                    usage();
                    System.exit(1);
                }
            }
            else if( args[i].equals("-s") || args[i].equals("--sayteam") )
            {
                sayTeam = true;
            }
            else if( args[i].equals("-c") || args[i].equals("--console") )
            {
                console = true;
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

        // Format the say_team string
        say = (sayTeam)?"say_team":"say";
    }

    private static void sendMessage( String data )
    {
        try
        {
            byte[] buffer = new byte[256];
            ByteBuffer bb = ByteBuffer.wrap(buffer);

            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(0xFFFFFFFF);
            bb.put((byte)0x52);
            bb.put(data.getBytes("UTF-8"));
            bb.flip();

            DatagramSocket s = new DatagramSocket(localPort, localHost);
            DatagramPacket d = new DatagramPacket(bb.array(), bb.position(), bb.limit(), peerHost, peerPort);

            s.connect(peerHost, peerPort);

            int sent = 0;

            for(;;)
            {
                if( limit > 0 && sent >= limit )
                    break;

                s.send(d);
                Thread.sleep(delay*1000);

                sent++;
            }

            s.close();
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }

        System.exit(0);
    }

    private static void usage()
    {
        String eol = System.getProperty( "line.separator" );
        String usage = new String();

        usage = usage.concat("Usage: java UDPMessageEmitter ");
        usage = usage.concat("[--to <ip>:<port>] ");
        usage = usage.concat("[--from <ip>:<port>] ");
        usage = usage.concat("[--delay <num>] ");
        usage = usage.concat("[--limit <num>] ");
        usage = usage.concat("[--message <string>] ");
        usage = usage.concat("[--sayteam] ");
        usage = usage.concat("[--console]" + eol);
        usage = usage.concat("       java UDPMessageEmitter [--help]");

        System.out.println("UDPMessageEmitter version " + PROGRAM_VERSION);
        System.out.println();
        System.out.println(usage);
        System.out.println();
        System.out.println("Command line options:");
        System.out.println("    -t|--to <ip>:<port>    Send messages to the listener at the specified IP and port (default = 127.0.0.1:12345)");
        System.out.println("    -f|--from <ip>:<port>  Send messages from the specified IP and port (default = 127.0.0.1:2345)");
        System.out.println("    -d|--delay <num>       Send a message every <num> seconds (default = 1)");
        System.out.println("    -l|--limit <num>       Stop after sending <num> messages (default = no limit)");
        System.out.println("    -m|--message <string>  Send <string> as the message text (default = \"This is a test!\")");
        System.out.println("    -s|--sayteam           Make this a say_team message (default = say)");
        System.out.println("    -c|--console           Make this a SRCDS console message");
        System.out.println("    -h|--help              Show this help text and exit");
        System.out.println();
        System.out.println("NOTE: When using the --from option, the address must be assigned to an available network interface.");
        System.out.println();
    }
}
