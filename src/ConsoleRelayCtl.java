/*
 * Copyright 2010-2015 by David A. Parker <parker.david.a@gmail.com>
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
 * ConsoleRelayCtl.java
 *
 * DESCRIPTION:
 * Control program for the CheckValve Console Relay.
 *
 * AUTHOR:
 * Dave Parker
 *
 * CHANGE LOG:
 *
 * July 27, 2015
 * - Version 1.0.0.
 * - Initial release.
 */

package com.github.daparker.checkvalve.consolerelayctl;

import java.io.*;
import java.net.*;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.util.Properties;

public class ConsoleRelayCtl
{
    private static final int CTL_PACKET_HEADER = 0xFFFFFFFE;
    private static final byte CTL_PROTOCOL_VERSION = (byte) 0x01;
    private static final byte CTL_PTYPE_STATUS = (byte) 0x06;
    private static final byte CTL_PTYPE_SHUTDOWN = (byte) 0x07;
    private static final byte CTL_PTYPE_STATUS_RESPONSE = (byte) 0x08;
    private static final byte CTL_PTYPE_SHUTDOWN_RESPONSE = (byte) 0x09;
    private static final String PROGRAM_VERSION = "1.0.0";
    private static final String IDENTITY_STRING = "CheckValve Console Relay Control " + PROGRAM_VERSION;

    private static String fs;
    private static String configFile;
    private static String jarFile;
    private static String minHeap;
    private static String maxHeap;
    private static String debugHost;
    private static String debugPort;
    private static boolean debug;
    private static boolean specifyConfig;
    private static int controlListenPort;
    private static File parentDir;

    public static void main(String args[]) throws InterruptedException
    {
        if( args.length == 0 )
        {
            usage();
            System.exit(1);
        }

        // Defaults
        try
        {
            fs = System.getProperty("file.separator");
            parentDir = new File("..").getCanonicalFile();
            jarFile = parentDir.getCanonicalPath() + fs + "lib" + fs + "checkvalveconsolerelay.jar";
            configFile = parentDir.getCanonicalPath() + fs + "checkvalveconsolerelay.properties";
            minHeap = "8m";
            maxHeap = "16m";
            debugHost = "localhost";
            debugPort = "1044";
            debug = false;
            specifyConfig = false;
            controlListenPort = 34568;
        }
        catch( Exception e )
        {
            System.out.println( "ERROR: Caught an exception: " + e.toString() );
            e.printStackTrace();
            System.exit(1);
        }

        String opt = new String();
        String val = new String();

        for( int i = 0; i < args.length; i++ )
        {
            opt = args[i];

            if( opt.equals("--jar") )
            {
                try
                {
                    val = args[++i];
                    jarFile = val;
                }
                catch( ArrayIndexOutOfBoundsException e )
                {
                    System.out.println();
                    System.out.println( "ERROR: The option " + opt + " requires a value." );
                    usage();
                    System.exit(1);
                }
            }
            else if( opt.equals("--config") )
            {
                try
                {
                    val = args[++i];
                    configFile = val;
                    specifyConfig = true;
                }
                catch( ArrayIndexOutOfBoundsException e )
                {
                    System.out.println();
                    System.out.println( "ERROR: The option " + opt + " requires a value." );
                    usage();
                    System.exit(1);
                }
            }
            else if( opt.equals("--minheap") )
            {
                try
                {
                    val = args[++i];
                    minHeap = val;
                }
                catch( ArrayIndexOutOfBoundsException e )
                {
                    System.out.println();
                    System.out.println( "ERROR: The option " + opt + " requires a value." );
                    usage();
                    System.exit(1);
                }
            }
            else if( opt.equals("--maxheap") )
            {
                try
                {
                    val = args[++i];
                    maxHeap = val;
                }
                catch( ArrayIndexOutOfBoundsException e )
                {
                    System.out.println();
                    System.out.println( "ERROR: The option " + opt + " requires a value." );
                    usage();
                    System.exit(1);
                }
            }
            else if( opt.equals("--debug") )
            {
                debug = true;
            }
            else if( opt.equals("--debughost") )
            {
                try
                {
                    val = args[++i];
                    debugHost = val;
                }
                catch( ArrayIndexOutOfBoundsException e )
                {
                    System.out.println();
                    System.out.println( "ERROR: The option " + opt + " requires a value." );
                    usage();
                    System.exit(1);
                }
            }
            else if( opt.equals("--debugport") )
            {
                try
                {
                    val = args[++i];
                    debugPort = val;
                }
                catch( ArrayIndexOutOfBoundsException e )
                {
                    System.out.println();
                    System.out.println( "ERROR: The option " + opt + " requires a value." );
                    usage();
                    System.exit(1);
                }
            }
            else if( opt.equals("-h") || opt.equals("--help") )
            {
                usage();
                System.exit(0);
            }
            else
            {
                if( opt.equals("start") )
                {
                    startConsoleRelay();
                }
                else if( opt.equals("stop") )
                {
                    sendCtl(CTL_PTYPE_SHUTDOWN);
                }
                else if( opt.equals("status") )
                {
                    sendCtl(CTL_PTYPE_STATUS);
                }
                else
                {
                    System.out.println();
                    System.out.println( "Invalid option: " + opt );
                    usage();
                    System.exit(1);
                }
            }
        }

        opt = "";
        val = "";

        parseConfig();
    }

    private static void usage()
    {
        System.out.println();
        System.out.println( "Usage: java -jar consolerelayctl.jar [--config <file>] {start|stop|status}" );
        System.out.println( "       java -jar consolerelayctl.jar --help" );
        System.out.println();
        System.out.println( "Command line options:" );
        System.out.println( "    --config <file>  Get configuration from <file> instead of the default (checkvalveconsolerelay.properties)" );
        System.out.println( "    --help           Show this help and exit." );
        System.out.println();
    }

    private static void parseConfig()
    {
        // Defaults
        final String DEFAULT_CONTROL_PORT = "34568";

        Properties config = new Properties();

        try
        {
            BufferedReader input = new BufferedReader(new FileReader(configFile));
            config.load(input);
            input.close();
        }
        catch( FileNotFoundException e )
        {
            System.err.println( "[ERROR] Configuration file " + configFile + " does not exist." );
            System.exit(1);
        }
        catch( IOException ioe )
        {
            System.err.println( "[ERROR] Failed to read configuration file " + configFile + "." );
            System.exit(1);
        }

        try
        {
            controlListenPort = Integer.parseInt(config.getProperty("controlListenPort",DEFAULT_CONTROL_PORT).trim());
            if( controlListenPort < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            controlListenPort = Integer.parseInt(DEFAULT_CONTROL_PORT);
            System.out.println();
            System.out.println( "WARNING: Specified value for controlListenPort is invalid, using default (" + DEFAULT_CONTROL_PORT + ")." );
        }
    }

    private static void startConsoleRelay()
    {
        File f;
        Runtime r = Runtime.getRuntime();

        try
        {
            String os = System.getProperty("os.name").toLowerCase();
            String javaExe = (os.toLowerCase().contains("win"))?"java.exe":"java";

            String bundledJava = parentDir.getCanonicalPath() + fs + "jre" + fs + "bin" + fs + javaExe;
            String debugOpts = new String();
            String java = new String();

            f = new File(jarFile);

            if( ! f.exists() )
            {
                System.err.println();
                System.err.println( "ERROR: Unable to locate " + jarFile + "." );
                System.err.println();
                System.exit(1);
            }

            f = new File(bundledJava);

            if( f.exists() )
            {
                java = bundledJava;
            }
            else
            {
                Process p = r.exec(javaExe + " -version");

                if( p.waitFor() == 0 )
                {
                    java = javaExe;
                }
                else
                {
                    System.err.println();
                    System.err.println( "ERROR: Unable to locate the 'java' executable.  Please ensure" );
                    System.err.println( "       the Java Runtime Environment (JRE) is installed and" );
                    System.err.println( "       the 'java' executable can be found in your PATH." );
                    System.err.println();
                    System.exit(1);
                }
            }

            String cmd = java;
            cmd += (debug)?" -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + debugHost + ":" + debugPort:"";
            cmd += " -Xms" + minHeap;
            cmd += " -Xmx" + maxHeap;
            cmd += " -jar " + jarFile;

            if( specifyConfig )
            {
                String conf = new String();

                if( configFile.startsWith("..") )
                    conf = configFile.substring(3);
                else
                    conf = configFile;

                cmd += " -c " + conf;
            }

            r.exec(cmd, null, parentDir);

            System.out.println( "Started CheckValve Console Relay." );

            if( debug )
                System.out.println( "(Debugging mode is enabled, connect jdb to " + debugHost + ":" + debugPort + " for debugging)." );
        }
        catch( Exception e )
        {
            System.out.println( "ERROR: Caught an exception: " + e.toString() );
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void sendCtl( byte ctl )
    {
        int reqHeader;
        byte reqType;
        byte reqProtocol;
        long reqTimestamp;
        long connectTimeMillis;

        byte[] outArray = new byte[128];
        ByteBuffer outBuffer = ByteBuffer.wrap(outArray);
        outBuffer.order(ByteOrder.BIG_ENDIAN);
        
        byte[] inArray = new byte[128];
        ByteBuffer inBuffer = ByteBuffer.wrap(inArray);
        inBuffer.order(ByteOrder.BIG_ENDIAN);

        DatagramSocket s = null;
        DatagramPacket out = null;
        DatagramPacket in = null;

        // Assemble the packet data
        outBuffer.putInt(CTL_PACKET_HEADER);           //  4
        outBuffer.put(CTL_PROTOCOL_VERSION);           // +1 =  5
        outBuffer.putLong(System.currentTimeMillis()); // +8 = 13
        outBuffer.put(ctl);                            // +1 = 14
        outBuffer.flip();

        try
        {
            InetAddress localhost = InetAddress.getByName("127.0.0.1");

            s = new DatagramSocket();
            out = new DatagramPacket(outBuffer.array(), outBuffer.position(), outBuffer.limit());
            in = new DatagramPacket(inArray, inArray.length);

            s.connect(localhost,controlListenPort);
            s.setSoTimeout(1000);
            s.send(out);
            s.receive(in);

            connectTimeMillis = System.currentTimeMillis();

            reqHeader = inBuffer.getInt();

            if( reqHeader != CTL_PACKET_HEADER )
            {
                String exp = "0x" + Integer.toHexString(CTL_PACKET_HEADER).toUpperCase();
                String rcv = "0x" + String.format("%8s", Integer.toHexString(reqHeader)).replace(' ','0').toUpperCase();
                System.out.println( "Control request contains an invalid header (expected " + exp + ", received " + rcv + ")." );
                System.out.println( "Rejecting control request : Invalid packet (bad header)." );
                return;
            }

            reqProtocol = inBuffer.get();
            reqTimestamp = inBuffer.getLong();

            if( reqTimestamp > connectTimeMillis || reqTimestamp < (connectTimeMillis - 100) )
            {
                System.out.println( "Rejecting control response : Invalid timestamp." );
                return;
            }

            reqType = inBuffer.get();

            if( reqType == CTL_PTYPE_STATUS_RESPONSE )
            {
                int uptime = inBuffer.getInt();
                int upDays = inBuffer.getInt();
                int upHours = inBuffer.getInt();
                int upMinutes = inBuffer.getInt();
                int totalMem = inBuffer.getInt();
                int freeMem = inBuffer.getInt();
                int maxMem = inBuffer.getInt();
                int usedMem = inBuffer.getInt();
                long totalPackets = inBuffer.getLong();
                long relayedPackets = inBuffer.getLong();
                int acceptedConnections = inBuffer.getInt();
                int rejectedConnections = inBuffer.getInt();
                int numBanned = inBuffer.getInt();

                String uptimeMessage = "";
                String memoryMessage = "";

                if( upDays == 1 )
                    uptimeMessage += upDays + " day ";
                else
                    uptimeMessage += upDays + " days ";

                if( upHours == 1 )
                    uptimeMessage += upHours + " hour ";
                else
                    uptimeMessage += upHours + " hours ";

                if( upMinutes == 1 )
                    uptimeMessage += upMinutes + " minute";
                else
                    uptimeMessage += upMinutes + " minutes";

                memoryMessage  = "max=" + maxMem + "k, ";
                memoryMessage += "allocated=" + totalMem + "k, ";
                memoryMessage += "used=" + usedMem + "k, ";
                memoryMessage += "free=" + freeMem + "k";

                System.out.println();
                System.out.println( "CheckValve Console Relay is running." );
                System.out.println();
                System.out.println( "  Uptime: " + uptimeMessage );
                System.out.println( "  Memory: " + memoryMessage );
                System.out.println( "  Packets received: " + totalPackets );
                System.out.println( "  Packets relayed: " + relayedPackets );
                System.out.println( "  Accepted connections: " + acceptedConnections );
                System.out.println( "  Rejected connections: " + rejectedConnections );
                System.out.println( "  Clients currently banned: " + numBanned );
                System.out.println();
            }
            else if( reqType == CTL_PTYPE_SHUTDOWN_RESPONSE )
            {
                byte response = inBuffer.get();

                if( response == (byte)0x00 )
                    System.out.println( "CheckValve Console Relay is shutting down.");
                else
                    System.out.println( "Rejecting control response : Invalid value." );
            }
            else
            {
                return;
            }
        }
        catch( PortUnreachableException e )
        {
            System.out.println( "Could not connect to the CheckValve Console Relay." );
            System.exit(1);
        }
        catch( Exception e )
        {
            System.out.println( "Failed to send control packet to the CheckValve Console Relay." );
            System.out.println( "Caught an exception: " + e.toString() );
            e.printStackTrace();
            System.exit(1);
        }
        finally
        {
            if( s != null )
                if( ! s.isClosed() )
                    s.close();
        }
    }
}
