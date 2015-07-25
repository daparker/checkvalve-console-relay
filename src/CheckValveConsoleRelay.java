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
 * CheckValveConsoleRelay.java
 *
 * DESCRIPTION:
 * Relay HLDS/SRCDS console messages to the clients who want them.
 *
 * AUTHOR:
 * Dave Parker
 *
 * CHANGE LOG:
 *
 * April 7, 2015
 * - Version 1.0.0.
 * - Initial release.
 */

package com.dparker.apps.checkvalve;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.StackTraceElement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

public class CheckValveConsoleRelay
{
    //
    // Class global constants
    //

    final static int PACKET_HEADER = 0xFFFFFFFF;
    final static byte BYTE_ZERO = (byte) 0;
    final static byte BYTE_ONE = (byte) 1;
    final static byte PROTOCOL_VERSION = (byte) 1;
    final static byte PTYPE_IDENTITY_STRING = (byte) 0x00;
    final static byte PTYPE_HEARTBEAT = (byte) 0x01;
    final static byte PTYPE_CONNECTION_REQUEST = (byte) 0x02;
    final static byte PTYPE_CONNECTION_FAILURE = (byte) 0x03;
    final static byte PTYPE_CONNECTION_SUCCESS = (byte) 0x04;
    final static byte PTYPE_MESSAGE_DATA = (byte) 0x05;
    final static long START_TIME = System.currentTimeMillis();
    final static String PROGRAM_VERSION = "1.0.0";
    final static String IDENTITY_STRING = "CheckValve Console Relay " + PROGRAM_VERSION;

    //
    // Class global variables
    //

    static int messageListenPort = 0;
    static int clientListenPort = 0;
    static int numClients = 0;
    static int maxClients = 0;
    static int logStatsEnabled = 0;
    static int logRotateEnabled = 0;
    static int logRotateKeepFiles = 0;
    static int autoBanEnabled = 0;
    static int autoBanThreshold = 0;
    static int acceptedConnections = 0;
    static int rejectedConnections = 0;
    static int debugLevel = 0;

    static long clientCheckInterval = 0;
    static long logStatsInterval = 0;
    static long logRotateInterval = 0;
    static long autoBanTimeLimit = 0;
    static long autoBanDuration = 0;
    static long totalPackets = 0;
    static long relayedPackets = 0;

    static boolean newMessage = false;
    static boolean shuttingDown = false;

    static String messageListenAddress = new String();
    static String clientListenAddress = new String();
    static String logFile = new String();
    static String password = new String();
    static String configFile = new String();
    static String[] messageInfo = new String[2];

    static Logger logger = new Logger();
    static Map<String, Long> bannedClients = new HashMap<String, Long>();
    static ServerSocket clientListenerSocket;
    static DatagramSocket messageListenerSocket;
    static Connection[] connections;
    static ByteBuffer messageData = ByteBuffer.allocate(4096);

    public static void main(String args[]) throws InterruptedException
    {
        // Default configuration file
        configFile = "checkvalveconsolerelay.properties";

        // Parse command line options
        if( args.length > 0 )
        {
            String opt = new String();
            String val = new String();

            for( int i = 0; i < args.length; i++ )
            {
                opt = args[i];

                if( opt.equals("-c") || opt.equals("--config") )
                {
                    try
                    {
                        val = args[++i];
                        configFile = val;
                    }
                    catch( ArrayIndexOutOfBoundsException e )
                    {
                        System.out.println();
                        System.out.println("ERROR: The option " + opt + " requires a value.");
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
                    System.out.println();
                    System.out.println("Invalid option: " + opt);
                    usage();
                    System.exit(1);
                }
            }

            opt = "";
            val = "";
        }

        // Environment
        String javaVersion = System.getProperty("java.version");
        String osVersion = System.getProperty("os.version");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");

        parseConfig();
        connections = new Connection[maxClients];

        if( logFile.length() > 0 )
        {
            if( logger.open(logFile) == -1 )
            {
                System.out.println();
                System.out.println( "WARNING: Failed to open logfile " + logFile + " for writing.  Logging is disabled." );
                System.out.println();
            }
        }
        else
        {
            System.out.println();
            System.out.println( "WARNING: No log file is defined in the configuration file.  Logging is disabled." );
            System.out.println();
        }

        // The loopback interface should only be used for testing.
        if( clientListenAddress.equals("127.0.0.1") || clientListenAddress.equals("localhost") )
        {
            System.out.println();
            System.out.println( "WARNING: The client listener is using a loopback address (" + clientListenAddress + ")." );
        }
        if( messageListenAddress.equals("127.0.0.1") || messageListenAddress.equals("localhost") )
        {
            System.out.println();
            System.out.println( "WARNING: The message listener is using a loopback address (" + messageListenAddress + ")." );
        }


        // Write startup messages to the log file
        logger.writeln( "[STARTUP] CheckValve Console Relay started (version " + PROGRAM_VERSION + ")" );
        logger.writeln( "[STARTUP] Using Java " + javaVersion + " on " + osName + " " + osVersion + " (" + osArch + ")" );
        logger.writeln( "[STARTUP] Debug level = " + debugLevel );
        logger.writeln( "[STARTUP] Client Listener Address = " + clientListenAddress );
        logger.writeln( "[STARTUP] Client Listener Port = " + clientListenPort );
        logger.writeln( "[STARTUP] Message Listener Address = " + messageListenAddress );
        logger.writeln( "[STARTUP] Message Listener Port = " + messageListenPort );

        if( password.length() > 0 )
            logger.writeln( "[STARTUP] A password is required for client connections." );
        else
            logger.writeln( "[STARTUP] A password is NOT required for client connections." );

        if( logStatsEnabled == 1 )
            logger.writeln( "[STARTUP] Statistics logging is enabled." );
        else
            logger.writeln( "[STARTUP] Statistics logging is NOT enabled." );

        if( logRotateEnabled == 1 )
            logger.writeln( "[STARTUP] Log rotation is enabled." );
        else
            logger.writeln( "[STARTUP] Log rotation is NOT enabled." );

        if( autoBanEnabled == 1 )
            logger.writeln( "[STARTUP] Auto-ban is enabled." );
        else
            logger.writeln( "[STARTUP] Auto-ban is NOT enabled." );

        // Initialize the Connection objects for client slots
        for( int i = 0; i < maxClients; i++ )
            connections[i] = new Connection();

        logger.writeln( "[STARTUP] Initialized " + maxClients + " client slots." );

        // Create threads
        final Thread tcpListenerThread = new Thread(new ClientListener());
        final Thread udpListenerThread = new Thread(new MessageListener());
        final Thread sendConsoleMessageThread = new Thread(new SendConsoleMessage());
        final Thread checkConnectionThread = new Thread(new CheckConnection());
        final Thread checkBansThread = new Thread(new CheckBans());
        final Thread logStatsThread = new Thread(new LogStats());
        final Thread logRotateThread = new Thread(new LogRotate());

        // Set thread names
        tcpListenerThread.setName("ClientListener");
        udpListenerThread.setName("MessageListener");
        sendConsoleMessageThread.setName("SendConsoleMessage");
        checkConnectionThread.setName("CheckConnection");
        checkBansThread.setName("CheckBans");
        logStatsThread.setName("LogStats");
        logRotateThread.setName("LogRotate");

        // Start threads
        tcpListenerThread.start();
        udpListenerThread.start();
        sendConsoleMessageThread.start();
        checkConnectionThread.start();

        // Only start the CheckBans thread if bans are enabled and they will expire
        if( autoBanEnabled > 0 && autoBanDuration > 0 )
            checkBansThread.start();

        // Only start the LogStats thread if stats logging is enabled
        if( logStatsEnabled == 1 )
            logStatsThread.start();

        // Only start the LogRotate thread if stats logging is enabled
        if( logRotateEnabled == 1 )
            logRotateThread.start();

        // Add a shutdown hook to clean up before shutting down
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                if( debugLevel >= 3 )
                    logger.debug(3, "Starting shutdown hook.");

                long id = Thread.currentThread().getId();
                String name = Thread.currentThread().getName();

                if( debugLevel >= 3 )
                    logger.debug(3, "Thread started (name=" + name + ", id=" + id + ").");

                shuttingDown = true;

                if( debugLevel >= 2 )
                    logger.debug(2, "Shutdown flag has been set.");

                try
                {
                    logger.writeln( "[SHUTDOWN] Closing all client connections." );

                    // Close and kill all active client connections
                    for( int i = 0; i < maxClients; i++ )
                    {
                        if( connections[i].isAlive() )
                        {
                            connections[i].closeSocket();
                            connections[i].kill();

                            if( debugLevel >= 2 )
                                logger.debug(2, "Connection " + i + " has been shut down.");
                        }
                    }

                    // Close the listen sockets
                    logger.writeln( "[SHUTDOWN] Closing all sockets." );
                    if( clientListenerSocket != null ) { clientListenerSocket.close(); }
                    if( messageListenerSocket != null ) { messageListenerSocket.close(); }

                    // Stop all threads
                    logger.writeln( "[SHUTDOWN] Stopping threads." );
                    tcpListenerThread.interrupt();
                    udpListenerThread.interrupt();
                    sendConsoleMessageThread.interrupt();
                    checkConnectionThread.interrupt();

                    if( checkBansThread.isAlive() )
                        checkBansThread.interrupt();

                    if( logStatsThread.isAlive() )
                        logStatsThread.interrupt();

                    if( logRotateThread.isAlive() )
                        logRotateThread.interrupt();

                    logger.writeln( "[SHUTDOWN] Shutting down the CheckValve Console Relay." );
                    logger.close();

                    // Shutdown
                    return;
                }
                catch( Exception e )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, name + " [ID=" + id + "] caught an exception.");

                    logger.writeln( "[SHUTDOWN] [ERROR] Shutdown hook caught an exception." );
                    logger.writeln( "[SHUTDOWN] [ERROR] " + e.toString() );

                    if( debugLevel >= 2 )
                    {
                        StackTraceElement[] ste = e.getStackTrace();

                        for(int x = 0; x < ste.length; x++)
                            logger.debug(2, ste[x].toString() );
                    }

                    // Shutdown
                    return;
                }
            }
        });
    }

    private static void usage()
    {
        System.out.println();
        System.out.println("Usage: java -jar checkvalveconsolerelay.jar [-c <file>] [-h]");
        System.out.println();
        System.out.println("Command line options:");
        System.out.println("    -c|--config <file>  Get configuration from <file> instead of the default (checkvalveconsolerelay.properties)");
        System.out.println("    -h|--help           Show this help and exit.");
        System.out.println();
    }

    private static void parseConfig()
    {
        // Defaults
        final String DEFAULT_AUTOBAN_ENABLED = "1";
        final String DEFAULT_AUTOBAN_THRESHOLD = "5";
        final String DEFAULT_AUTOBAN_TIMELIMIT = "0";
        final String DEFAULT_AUTOBAN_DURATION = "86400";
        final String DEFAULT_CHECK_INTERVAL = "10";
        final String DEFAULT_CLIENT_ADDRESS = "0.0.0.0";
        final String DEFAULT_CLIENT_PORT = "23456";
        final String DEFAULT_DEBUG_LEVEL = "0";
        final String DEFAULT_LOG_FILE = "checkvalveconsolerelay.log";
        final String DEFAULT_LOGROTATE_ENABLED = "1";
        final String DEFAULT_LOGROTATE_INTERVAL = "168";
        final String DEFAULT_LOGROTATE_KEEP_FILES = "10";
        final String DEFAULT_LOGSTATS_ENABLED = "1";
        final String DEFAULT_LOGSTATS_INTERVAL = "86400";
        final String DEFAULT_MAX_CLIENTS = "10";
        final String DEFAULT_MESSAGE_ADDRESS = "0.0.0.0";
        final String DEFAULT_MESSAGE_PORT = "12345";
        final String DEFAULT_PASSWORD = "";

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

        //
        // Integer options
        //

        try
        {
            messageListenPort = Integer.parseInt(config.getProperty("messageListenPort",DEFAULT_MESSAGE_PORT).trim());
            if( messageListenPort < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            messageListenPort = Integer.parseInt(DEFAULT_MESSAGE_PORT);
            System.out.println();
            System.out.println( "WARNING: Specified value for messageListenPort is invalid, using default (" + DEFAULT_MESSAGE_PORT + ")." );
        }

        try
        {
            clientListenPort = Integer.parseInt(config.getProperty("clientListenPort",DEFAULT_CLIENT_PORT).trim());
            if( clientListenPort < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            clientListenPort = Integer.parseInt(DEFAULT_CLIENT_PORT);
            System.out.println();
            System.out.println( "WARNING: Specified value for clientListenPort is invalid, using default (" + DEFAULT_CLIENT_PORT + ")." );
        }

        try
        {
            maxClients = Integer.parseInt(config.getProperty("maxClients",DEFAULT_MAX_CLIENTS).trim());
            if( maxClients < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            maxClients = Integer.parseInt(DEFAULT_MAX_CLIENTS);
            System.out.println();
            System.out.println( "WARNING: Specified value for maxClients is invalid, using default (" + DEFAULT_MAX_CLIENTS + ")." );
        }

        try
        {
            logStatsEnabled = Integer.parseInt(config.getProperty("logStatsEnabled",DEFAULT_LOGSTATS_ENABLED).trim());
            if( logStatsEnabled < 0 || logStatsEnabled > 1 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            logStatsEnabled = Integer.parseInt(DEFAULT_LOGSTATS_ENABLED);
            System.out.println();
            System.out.println( "WARNING: Specified value for logStatsEnabled is invalid, using default (" + DEFAULT_LOGSTATS_ENABLED + ")." );
        }

        try
        {
            logRotateEnabled = Integer.parseInt(config.getProperty("logRotateEnabled",DEFAULT_LOGROTATE_ENABLED).trim());
            if( logRotateEnabled < 0 || logRotateEnabled > 1 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            logRotateEnabled = Integer.parseInt(DEFAULT_LOGROTATE_ENABLED);
            System.out.println();
            System.out.println( "WARNING: Specified value for logRotateEnabled is invalid, using default (" + DEFAULT_LOGROTATE_ENABLED + ")." );
        }

        try
        {
            logRotateKeepFiles = Integer.parseInt(config.getProperty("logRotateKeepFiles",DEFAULT_LOGROTATE_KEEP_FILES).trim());
            if( logRotateKeepFiles < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            logRotateKeepFiles = Integer.parseInt(DEFAULT_LOGROTATE_KEEP_FILES);
            System.out.println();
            System.out.println( "WARNING: Specified value for logRotateKeepFiles is invalid, using default (" + DEFAULT_LOGROTATE_KEEP_FILES + ")." );
        }

        try
        {
            autoBanEnabled = Integer.parseInt(config.getProperty("autoBanEnabled",DEFAULT_AUTOBAN_ENABLED).trim());
            if( autoBanEnabled < 0 || autoBanEnabled > 1 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            autoBanEnabled = Integer.parseInt(DEFAULT_AUTOBAN_ENABLED);
            System.out.println();
            System.out.println( "WARNING: Specified value for autoBanEnabled is invalid, using default (" + DEFAULT_AUTOBAN_ENABLED + ")." );
        }

        try
        {
            autoBanThreshold = Integer.parseInt(config.getProperty("autoBanThreshold",DEFAULT_AUTOBAN_THRESHOLD).trim());
            if( autoBanThreshold < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            autoBanThreshold = Integer.parseInt(DEFAULT_AUTOBAN_THRESHOLD);
            System.out.println();
            System.out.println( "WARNING: Specified value for autoBanThreshold is invalid, using default (" + DEFAULT_AUTOBAN_THRESHOLD + ")." );
        }

        try
        {
            debugLevel = Integer.parseInt(config.getProperty("debugLevel",DEFAULT_DEBUG_LEVEL).trim());
            if( debugLevel < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            debugLevel = Integer.parseInt(DEFAULT_DEBUG_LEVEL);
            System.out.println();
            System.out.println( "WARNING: Specified value for debugLevel is invalid, using default (" + DEFAULT_DEBUG_LEVEL + ")." );
        }

        //
        // Long options
        //

        try
        {
            clientCheckInterval = Long.parseLong(config.getProperty("clientCheckInterval",DEFAULT_CHECK_INTERVAL).trim())*1000;
            if( clientCheckInterval < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            clientCheckInterval = Long.parseLong(DEFAULT_CHECK_INTERVAL)*1000;
            System.out.println();
            System.out.println( "WARNING: Specified value for clientCheckInterval is invalid, using default (" + DEFAULT_CHECK_INTERVAL + ")." );
        }

        try
        {
            logStatsInterval = Long.parseLong(config.getProperty("logStatsInterval",DEFAULT_LOGSTATS_INTERVAL).trim())*1000;
            if( logStatsInterval < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            logStatsInterval = Long.parseLong(DEFAULT_LOGSTATS_INTERVAL)*1000;
            System.out.println();
            System.out.println( "WARNING: Specified value for logStatsInterval is invalid, using default (" + DEFAULT_LOGSTATS_INTERVAL + ")." );
        }

        try
        {
            logRotateInterval = Long.parseLong(config.getProperty("logRotateInterval",DEFAULT_LOGROTATE_INTERVAL).trim())*60*60*1000;
            if( logRotateInterval < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            logRotateInterval = Long.parseLong(DEFAULT_LOGROTATE_INTERVAL)*60*60*1000;
            System.out.println();
            System.out.println( "WARNING: Specified value for logRotateInterval is invalid, using default (" + DEFAULT_LOGROTATE_INTERVAL + ")." );
        }

        try
        {
            autoBanTimeLimit = Long.parseLong(config.getProperty("autoBanTimeLimit",DEFAULT_AUTOBAN_TIMELIMIT).trim())*1000;
            if( autoBanTimeLimit < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            autoBanTimeLimit = Long.parseLong(DEFAULT_AUTOBAN_TIMELIMIT)*1000;
            System.out.println();
            System.out.println( "WARNING: Specified value for autoBanTimeLimit is invalid, using default (" + DEFAULT_AUTOBAN_TIMELIMIT + ")." );
        }

        try
        {
            autoBanDuration = Long.parseLong(config.getProperty("autoBanDuration",DEFAULT_AUTOBAN_DURATION).trim())*1000;
            if( autoBanDuration < 0 ) throw new NumberFormatException();
        }
        catch( NumberFormatException n )
        {
            autoBanDuration = Long.parseLong(DEFAULT_AUTOBAN_DURATION)*1000;
            System.out.println();
            System.out.println( "WARNING: Specified value for autoBanDuration is invalid, using default (" + DEFAULT_AUTOBAN_DURATION + ")." );
        }

        //
        // String options
        //

        clientListenAddress = config.getProperty("clientListenAddress",DEFAULT_CLIENT_ADDRESS).trim();
        messageListenAddress = config.getProperty("messageListenAddress",DEFAULT_MESSAGE_ADDRESS).trim();
        logFile = config.getProperty("logFile",DEFAULT_LOG_FILE).trim();
        password = config.getProperty("password",DEFAULT_PASSWORD).trim();
    }

    /*
     * TCP listener for client connections
     */
    private static class ClientListener implements Runnable
    {
        private InetAddress addr;
        private InputStream in;
        private String [] fields;
        private String data = new String();
        private String clientIp = new String();
        private String clientPass = new String();
        private String clientString = new String();
        private byte reqType = BYTE_ZERO;
        private int reqHeader = 0;
        private int nextSlot = 0;
        private int clientPort = 0;
        private int ready = 0;
        private int connectTimeout = 2000;
        private long connectTimeMillis = 0;
        private short contentLength = 0;
        private boolean listening = false;
        private boolean waiting = false;
        private Map<String, Integer> badConnectionAttempts = new HashMap<String, Integer>();
        private Map<String, Long> badAttemptTimes = new HashMap<String, Long>();

        private long id = 0;
        private String name = new String();

        public void run()
        {
            id = Thread.currentThread().getId();
            name = Thread.currentThread().getName();

            if( debugLevel >= 3 )
                logger.debug(3, "Thread started (name=" + name + ", id=" + id + ").");

            for(;;)
            {
                try
                {
                    runClientListener();
                }
                catch( InterruptedException ie )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, name + " [ID=" + id + "] received an interrupt.");

                    return;
                }
                catch( SocketException se )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, name + " [ID=" + id + "] caught a SocketException.");

                    if( shuttingDown )
                    {
                        if( debugLevel >= 3 )
                            logger.debug(3, name + " [ID=" + id + "] ignored the SocketException (shutdown flag is set).");

                        return;
                    }

                    //
                    // If the listening flag is not set then the socket could not be opened
                    //
                    if( ! listening )
                    {
                        System.err.println();

                        logger.writeln( "[ERROR] Unable to create TCP listening socket." );
                        System.err.println( "[ERROR] Unable to create TCP listening socket." );
                        logger.writeln( "[ERROR] " + se.toString() );
                        System.err.println( "[ERROR] " + se.toString() );

                        if( debugLevel >= 2 )
                        {
                            StackTraceElement[] ste = se.getStackTrace();

                            for(int x = 0; x < ste.length; x++)
                                logger.debug(2, ste[x].toString() );
                        }

                        if( ! shuttingDown )
                        {
                            if( debugLevel >= 2 )
                                logger.debug(2, "Client listener is calling for the program to shut down.");

                            System.exit(1);
                        }

                        return;
                    }
                    else
                    {
                        logger.writeln( "[ERROR] Client listener thread caught an exception:" );
                        logger.writeln( "[ERROR] " + se.toString() );

                        if( debugLevel >= 2 )
                        {
                            StackTraceElement[] ste = se.getStackTrace();

                            for(int x = 0; x < ste.length; x++)
                                logger.debug(2, ste[x].toString() );
                        }

                        if( clientListenerSocket.isClosed() )
                        {
                            listening = false;
                            logger.writeln( "[ERROR] The client listener socket closed unexpectedly." );
                            logger.writeln( "[ERROR] Attempting to restart the client listener." );
                        }
                        else
                        {
                            logger.writeln( "This exception appears to be non-fatal." );
                        }
                    }
                }
                catch( Exception e )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, name + " [ID=" + id + "] caught an exception.");

                    if( shuttingDown )
                    {
                        if( debugLevel >= 3 )
                            logger.debug(3, name + " [ID=" + id + "] ignored the exception (shutdown flag is set).");

                        return;
                    }

                    logger.writeln( "[ERROR] Client listener thread caught an exception:" );
                    logger.writeln( "[ERROR] " + e.toString() );

                    if( debugLevel >= 2 )
                    {
                        StackTraceElement[] ste = e.getStackTrace();

                        for(int x = 0; x < ste.length; x++)
                            logger.debug(2, ste[x].toString() );
                    }

                    if( clientListenerSocket.isClosed() )
                    {
                        listening = false;
                        logger.writeln( "[ERROR] The client listener socket closed unexpectedly." );
                        logger.writeln( "[ERROR] Attempting to restart the client listener." );
                    }
                    else
                    {
                        logger.writeln( "This exception appears to be non-fatal." );
                    }
                }
            }
        }

        private void runClientListener() throws Exception
        {
            if( ! listening )
            {
                // Create the TCP listen socket
                addr = InetAddress.getByName(clientListenAddress);
                clientListenerSocket = new ServerSocket(clientListenPort, 1, addr);

                // Set the listening flag
                listening = true;

                logger.writeln( "Client listener started; listening for clients on " + clientListenAddress + ":" + clientListenPort + " (TCP)." );
            }

            byte[] dataBytes = new byte[2048];
            ByteBuffer dataBuffer = ByteBuffer.wrap(dataBytes);
            dataBuffer.order(ByteOrder.LITTLE_ENDIAN);

            for(;;)
            {
                dataBuffer.clear();

                // Create a new open socket for the next connection
                Socket clientSocket = clientListenerSocket.accept();

                // Set the I/O timeout for the client socket
                clientSocket.setSoTimeout(connectTimeout);

                // Get the client's IP address
                clientIp = clientSocket.getInetAddress().getHostAddress();

                // Close this connection immediately if the client IP is banned
                if( bannedClients.containsKey(clientIp) )
                {
                    clientSocket.close();

                    if( debugLevel >= 3 )
                        logger.debug(3, "Ignored request from banned IP address " + clientIp + ".");

                    continue;
                }

                // Get the current time
                connectTimeMillis = System.currentTimeMillis();

                // Get the client's port number
                clientPort = clientSocket.getPort();
                clientString = clientIp + ":" + clientPort;

                // Send our identity string to the client
                sendMessageToClient(clientSocket, PTYPE_IDENTITY_STRING, IDENTITY_STRING);

                if( debugLevel >= 3 )
                    logger.debug(3, "Identity string has been sent to " + clientString + ".");

                // Get the input buffer of the socket
                in = clientSocket.getInputStream(); 

                try
                {
                    in.read(dataBytes, 0, 7);
                }
                catch( SocketTimeoutException ste )
                {
                    logger.writeln( "Rejecting client " + clientString + " : No connection request." );
                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    continue;
                }
                catch( Exception e )
                {
                    logger.writeln( "Rejecting client " + clientString + " : Error reading request header (" + e.toString() + ")." );

                    if( debugLevel >= 2 )
                    {
                        StackTraceElement[] ste = e.getStackTrace();

                        for(int x = 0; x < ste.length; x++)
                            logger.debug(2, ste[x].toString() );
                    }

                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    continue;
                }

                if( debugLevel >= 2 )
                    logger.debug(2, "Validating connection request from " + clientString + ".");

                if( (reqHeader = dataBuffer.getInt()) != PACKET_HEADER )
                {
                    if( debugLevel >= 3 )
                    {
                        String exp = "0x" + Integer.toHexString(PACKET_HEADER).toUpperCase();
                        String rcv = "0x" + String.format("%8s", Integer.toHexString(reqHeader)).replace(' ','0').toUpperCase();
                        logger.debug(3, "Request contains an invalid header (expected " + exp + ", received " + rcv + ").");
                    }

                    logger.writeln( "Rejecting client " + clientString + " : Invalid packet (bad header)." );
                    sendMessageToClient(clientSocket, PTYPE_CONNECTION_FAILURE, "E Invalid packet");
                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    rejectedConnections++;
                    continue;
                }

                if( (reqType = dataBuffer.get()) != PTYPE_CONNECTION_REQUEST )
                {
                    if( debugLevel >= 3 )
                    {
                        String exp = "0x" + String.format("%2s", Byte.toString(PTYPE_CONNECTION_REQUEST)).replace(' ','0').toUpperCase();
                        String rcv = "0x" + String.format("%2s", Byte.toString(reqType)).replace(' ','0').toUpperCase();
                        logger.debug(3, "Request contains an invalid packet type (expected " + exp + ", received " + rcv + ").");
                    }

                    logger.writeln( "Rejecting client " + clientString + " : Invalid packet (bad packet type)." );
                    sendMessageToClient(clientSocket, PTYPE_CONNECTION_FAILURE, "E Invalid packet");
                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    rejectedConnections++;
                    continue;
                }

                contentLength = dataBuffer.getShort();

                if( contentLength < 1 || contentLength > 4096 )
                {
                    if( debugLevel >= 3 )
                    {
                        String rcv = "0x" + String.format("%4s", Integer.toHexString((int)contentLength)).replace(' ','0').toUpperCase();
                        logger.debug(3, "Request contains an invalid content length (" + rcv + ").");
                    }

                    logger.writeln( "Rejecting client " + clientString + " : Invalid content length." );
                    sendMessageToClient(clientSocket, PTYPE_CONNECTION_FAILURE, "E Invalid content length");
                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    rejectedConnections++;
                    continue;
                }

                //
                // Read the rest of the packet data
                //

                try
                {
                    in.read(dataBytes, dataBuffer.position(), contentLength);
                }
                catch( SocketTimeoutException ste )
                {
                    logger.writeln( "Rejecting client " + clientString + " : Socket timeout while waiting for request data." );
                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    continue;
                }
                catch( Exception e )
                {
                    logger.writeln( "Rejecting client " + clientString + " : Error reading request data (" + e.toString() + ")." );

                    if( debugLevel >= 2 )
                    {
                        StackTraceElement[] ste = e.getStackTrace();

                        for(int x = 0; x < ste.length; x++)
                            logger.debug(2, ste[x].toString() );
                    }

                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    continue;
                }

                data = new String(dataBytes, dataBuffer.position(), contentLength, "UTF-8");

                // Make sure the packet has data
                if( (data == null) || (data.length() < 2) )
                {
                    logger.writeln( "Rejecting client " + clientString + " : Empty packet." );
                    sendMessageToClient(clientSocket, PTYPE_CONNECTION_FAILURE, "E Empty packet");
                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    rejectedConnections++;
                    continue;
                }

                // Make sure the packet data conforms to the protocol
                if( ! data.startsWith( "P ") )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, "Incorrect start of packet data (expected 'P ', found '" + data.substring(0,2) + "').");

                    logger.writeln( "Rejecting client " + clientString + " : Invalid packet (bad connection request)." );
                    sendMessageToClient(clientSocket, PTYPE_CONNECTION_FAILURE, "E Invalid packet");
                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    rejectedConnections++;
                    continue;
                }

                // Split the remaining packet data into strings delimited by 0x00
                fields = data.split( "\u0000" );

                //
                // FIELDS:
                //    [0]: Password (ignored if no password is required)
                //    [1]: IP of the game server from which this client wants console messages
                //    [2]: Port of the game server from which this client wants console messages
                //

                // Make sure the packet has 3 fields
                if( fields.length != 3 )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, "Incorrect number of fields in packet data (expected 3, found " + fields.length + ").");

                    logger.writeln( "Rejecting client " + clientString + " : Invalid packet (unable to parse)." );
                    sendMessageToClient(clientSocket, PTYPE_CONNECTION_FAILURE, "E Invalid packet");
                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    rejectedConnections++;
                    continue;
                }

                // Validate the password in this packet if one is required
                if( password.length() > 0 )
                {
                    clientPass = fields[0].substring(2, fields[0].length());

                    if( ! clientPass.equals(password) )
                    {
                        logger.writeln( "Rejecting client " + clientString + " : Bad password." );
                        sendMessageToClient(clientSocket, PTYPE_CONNECTION_FAILURE, "E Bad password");
                        updateBanList(clientIp);
                        in.close();
                        clientSocket.close();
                        rejectedConnections++;
                        continue;
                    }
                }

                // Validate requested IP address
                if( ! isValidIPv4Address(fields[1]) && ! isValidIPv6Address(fields[1]) )
                {
                    logger.writeln( "Rejecting client " + clientString + " : Bad IP address in request." );
                    sendMessageToClient(clientSocket, PTYPE_CONNECTION_FAILURE, "E Bad IP address");
                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    rejectedConnections++;
                    continue;
                }

                // Validate requested port
                if( ! isValidPortNumber(fields[2]) )
                {
                    logger.writeln( "Rejecting client " + clientString + " : Bad port number in request." );
                    sendMessageToClient(clientSocket, PTYPE_CONNECTION_FAILURE, "E Bad port number");
                    updateBanList(clientIp);
                    in.close();
                    clientSocket.close();
                    rejectedConnections++;
                    continue;
                }

                //
                // Connection request is valid
                //

                // Remove the bad connection counter for this IP if one exists
                if( badConnectionAttempts.containsKey(clientIp) )
                {
                    badConnectionAttempts.remove(clientIp);
                    
                    if( debugLevel >= 2 )
                        logger.debug(2, "Removed bad connection counter for " + clientIp + ".");
                }

                // Assign this client to the next available slot or reject the connection if no slots are available
                if( (nextSlot = getNextSlot()) != -1 )
                {
                    logger.writeln( "New client connection from " + clientString + "." );
                    sendMessageToClient(clientSocket, PTYPE_CONNECTION_SUCCESS, "OK");

                    // Use the Connection class to handle everything
                    connections[nextSlot] = new Connection( clientSocket, fields[1], fields[2] );

                    if( debugLevel >= 2 )
                        logger.debug(2, "Created a new Connection object for " + clientString + " in slot " + nextSlot + ".");

                    logger.writeln( "Assigned client " + clientString + " to slot " + nextSlot );
                    logger.writeln( "Client " + clientString + " wants messages from " + fields[1] + ":" + fields[2] );
                }
                else
                {
                    logger.writeln( "Refusing connection from " + clientString + " : Too many connections." );
                    sendMessageToClient(clientSocket, PTYPE_CONNECTION_FAILURE, "E Too many connections");
                    clientSocket.close();
                    rejectedConnections++;
                }
            }
        }

        private void updateBanList(String ip)
        {
            if( autoBanEnabled == 0 )
                return;

            if( badConnectionAttempts.containsKey(ip) )
            {
                int num = badConnectionAttempts.get(ip).intValue();
                long now = System.currentTimeMillis();
                long last = badAttemptTimes.get(ip).longValue();
                long diff = now - last;

                if( debugLevel >= 2 )
                    logger.debug(2, ip + " has previously had " + num + " bad connection attempt(s).");

                if( debugLevel >= 2 )
                    logger.debug(2, "Last bad connection attempt from " + ip + " was " + diff + " milliseconds ago.");

                if( autoBanTimeLimit == 0 || diff < autoBanTimeLimit )
                {
                    if( (num+1) == autoBanThreshold )
                    {
                        bannedClients.put(ip, Long.valueOf(now));

                        if( debugLevel >= 2 )
                            logger.debug(2, "Created a new ban entry for " + ip + ".");

                        badConnectionAttempts.remove(ip);
                        badAttemptTimes.remove(ip);
                    
                        if( debugLevel >= 2 )
                            logger.debug(2, "Removed bad connection counter for " + ip + ".");

                        logger.writeln( "[AUTO-BAN] Banning IP " + clientIp + " after " + (num+1) + " failed connection attempts." );
                        logger.writeln( "[AUTO-BAN] Future connection attempts from " + ip + " will be ignored." );
                    }
                    else
                    {
                        num++;
                        badConnectionAttempts.put(ip, num);

                        if( debugLevel >= 2 )
                            logger.debug(2, "Updated bad connection counter for " + ip + " to " + num + ".");
                    }

                    return;
                }
            }

            badConnectionAttempts.put(ip, 1);
            badAttemptTimes.put(ip, Long.valueOf(System.currentTimeMillis()));

            if( debugLevel >= 2 )
                logger.debug(2, "Started a bad connection counter for " + ip + ".");

            return;
        }

        private int getNextSlot()
        {
            if( debugLevel >= 3 )
                logger.debug(3, "Looking for an available connection slot.");

            for( int i = 0; i < maxClients; i++ )
            {
                if( ! connections[i].isAlive() )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, "Slot " + i + " is available.");

                    return i;
                }
            }

            if( debugLevel >= 3 )
                logger.debug(3, "Failed to find an available connection slot.");

            return -1;
        }

        private boolean isValidIPv6Address(String address)
        {
            try
            {
                java.net.Inet6Address.getByName(address);
                return true;
            }
            catch( Exception e )
            {
                if( debugLevel >= 3 )
                    logger.debug(3, address + " is not a valid IPv6 address.");

                return false;
            }
        }

        private boolean isValidIPv4Address(String address)
        {
            try
            {
                java.net.Inet4Address.getByName(address);
                return true;
            }
            catch( Exception e )
            {
                if( debugLevel >= 3 )
                    logger.debug(3, address + " is not a valid IPv4 address.");

                return false;
            }
        }

        private boolean isValidPortNumber(String s)
        {
            try
            {
                int i = Integer.parseInt(s);

                if( i > 0 && i < 65536 )
                    return true;
                else
                    return false;
            }
            catch( Exception e )
            {
                if( debugLevel >= 3 )
                    logger.debug(3, s + " is not a valid port number.");

                return false;
            }
        }

        private void sendMessageToClient(Socket sock, byte ptype, String message)
        {
            try
            {
                byte[] messageData = new byte[4096];
                byte[] messageBytes = message.getBytes("UTF-8");

                ByteBuffer buffer = ByteBuffer.wrap(messageData);

                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putInt(PACKET_HEADER);
                buffer.put(ptype);
                buffer.putShort((short)(messageBytes.length + 1));
                buffer.put(messageBytes);
                buffer.put(BYTE_ZERO);
                buffer.flip();

                OutputStream out = sock.getOutputStream();
                out.write(buffer.array(), 0, buffer.limit());
                out.flush();
            }
            catch( Exception e )
            {
                logger.writeln( "[ERROR] Caught an exception while sending message to " + clientString + " (message=" + message + ")." );
                logger.writeln( "[ERROR] " + e.toString() );

                if( debugLevel >= 2 )
                {
                    StackTraceElement[] ste = e.getStackTrace();

                    for(int x = 0; x < ste.length; x++)
                        logger.debug(2, ste[x].toString() );
                }

                return;
            }
        }
    }

    /*
     * UDP listener for incoming messages from the game server(s)
     */
    private static class MessageListener implements Runnable
    {
        private InetAddress addr;
        private DatagramPacket packet;
        private int idx = 0;
        private int port = 0;
        private int serverTimestamp = 0;
        private String from = new String();
        private String data = new String();
        private String message = new String();
        private String messageTimestamp = new String();
        private String playerName = new String();
        private String playerTeam = new String();
        private String playerSays = new String();
        private String [] tokens = new String[2];
        private byte isSayTeam = BYTE_ZERO;
        private byte[] buffer = new byte[4096];
        private boolean listening = false;

        private int messageSize = 0;
        private ByteBuffer messageBody = ByteBuffer.allocate(4096);

        private long id = 0;
        private String name = new String();

        public void run()
        {
            id = Thread.currentThread().getId();
            name = Thread.currentThread().getName();

            if( debugLevel >= 3 )
                logger.debug(3, "Thread started (name=" + name + ", id=" + id + ").");

            messageBody.order(ByteOrder.LITTLE_ENDIAN);
            messageData.order(ByteOrder.LITTLE_ENDIAN);

            for(;;)
            {
                try
                {
                    runMessageListener();
                }
                catch( InterruptedException ie )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, name + " [ID=" + id + "] received an interrupt.");

                    return;
                }
                catch( SocketException se )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, name + " [ID=" + id + "] caught a SocketException.");

                    if( shuttingDown )
                    {
                        if( debugLevel >= 3 )
                            logger.debug(3, name + " [ID=" + id + "] ignored the SocketException (shutdown flag is set).");

                        return;
                    }

                    //
                    // If the listening flag is not set then the socket could not be opened
                    //
                    if( ! listening )
                    {
                        System.err.println();

                        logger.writeln( "[ERROR] Unable to create UDP listening socket." );
                        System.err.println( "[ERROR] Unable to create UDP listening socket." );
                        logger.writeln( "[ERROR] " + se.toString() );
                        System.err.println( "[ERROR] " + se.toString() );

                        if( debugLevel >= 2 )
                        {
                            StackTraceElement[] ste = se.getStackTrace();

                            for(int x = 0; x < ste.length; x++)
                                logger.debug(2, ste[x].toString() );
                        }

                        if( ! shuttingDown )
                        {
                            if( debugLevel >= 2 )
                                logger.debug(2, "Message listener is calling for the program to shut down.");

                            System.exit(1);
                        }

                        return;
                    }
                    else
                    {
                        logger.writeln( "[ERROR] Message listener thread caught an exception:" );
                        logger.writeln( "[ERROR] " + se.toString() );

                        if( debugLevel >= 2 )
                        {
                            StackTraceElement[] ste = se.getStackTrace();

                            for(int x = 0; x < ste.length; x++)
                                logger.debug(2, ste[x].toString() );
                        }

                        if( messageListenerSocket.isClosed() )
                        {
                            listening = false;
                            logger.writeln( "[ERROR] The message listener socket closed unexpectedly." );
                            logger.writeln( "[ERROR] Attempting to restart the message listener." );
                        }
                        else
                        {
                            logger.writeln( "This exception appears to be non-fatal." );
                        }
                    }
                }
                catch( Exception e )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, name + " [ID=" + id + "] caught an exception.");

                    if( shuttingDown )
                    {
                        if( debugLevel >= 3 )
                            logger.debug(3, name + " [ID=" + id + "] ignored the exception (shutdown flag is set).");

                        return;
                    }

                    logger.writeln( "[ERROR] Message listener thread caught an exception:" );
                    logger.writeln( "[ERROR] " + e.toString() );

                    if( debugLevel >= 2 )
                    {
                        StackTraceElement[] ste = e.getStackTrace();

                        for(int x = 0; x < ste.length; x++)
                            logger.debug(2, ste[x].toString() );
                    }

                    if( clientListenerSocket.isClosed() )
                    {
                        listening = false;
                        logger.writeln( "[ERROR] The client listener socket closed unexpectedly." );
                        logger.writeln( "[ERROR] Attempting to restart the client listener." );
                    }
                    else
                    {
                        logger.writeln( "This exception appears to be non-fatal." );
                    }
                }
            }
        }

        private void runMessageListener() throws Exception
        {
            if( ! listening )
            {
                // Create the UDP listen socket
                addr = InetAddress.getByName(messageListenAddress);
                messageListenerSocket = new DatagramSocket(messageListenPort, addr);

                // Set the listening flag
                listening = true;

                // Create the datagram packet to handle incoming data
                packet = new DatagramPacket(buffer,buffer.length);

                logger.writeln( "Message listener started; receiving log messages on " + messageListenAddress + ":" + messageListenPort + " (UDP)." );
            }

            for(;;)
            {
                // Get the next packet from the socket
                messageListenerSocket.receive(packet);

                //Increment the total packets counter
                totalPackets++;

                if( numClients > 0 )
                {
                    // Get the data from the packet
                    data = new String(buffer, 0, packet.getLength(), "UTF-8");

                    // Get the remote address and remote port from the packet
                    messageInfo[0] = packet.getAddress().getHostAddress();
                    messageInfo[1] = Integer.valueOf(packet.getPort()).toString();

                    // Only continue processing if a client wants messages from this game server
                    if( ! isWanted(messageInfo[0], messageInfo[1]) )
                    {
                        if( debugLevel >= 1 )
                            logger.debug(1, "[id=" + totalPackets + "] No client wants this message.");

                        continue;
                    }

                    if( debugLevel >= 1 )
                        logger.debug(1, "[id=" + totalPackets + "] At least one client wants this message.");

                    while( newMessage ) Thread.sleep(1);

                    // Clear the byte buffer
                    messageBody.clear();
                    messageData.clear();

                    // Extract the log message from the packet
                    message = data.substring(data.indexOf("L "), data.length()-1);

                    // Include the current timestamp in case the one in the message is mangled
                    serverTimestamp = (int) (System.currentTimeMillis()/1000);

                    messageBody.putInt(serverTimestamp);
                    messageBody.put(messageInfo[0].getBytes("UTF-8")).put(BYTE_ZERO);
                    messageBody.put(messageInfo[1].getBytes("UTF-8")).put(BYTE_ZERO);
                    messageBody.put(message.getBytes("UTF-8")).put(BYTE_ZERO);
                    messageBody.flip();

                    // Assemble the packet data
                    messageData.putInt(PACKET_HEADER);
                    messageData.put(PTYPE_MESSAGE_DATA);
                    messageData.putShort((short)(messageBody.limit()+1));
                    messageData.put(PROTOCOL_VERSION);
                    messageData.put(messageBody);
                    messageData.flip();

                    // Set the new message flag
                    newMessage = true;

                    if( debugLevel >= 3 )
                        logger.debug(3, "Set the new message flag.");
                }
            }
        }

        private boolean isWanted(String i, String p)
        {
            for( int x = 0; x < maxClients; x++ )
            {
                if( connections[x].isAlive() )
                    if( connections[x].getWantsIP().equals(i) )
                        if( connections[x].getWantsPort().equals(p) )
                            return true;
            }

            return false;
        } 
    }

    /*
     * Send a message to all connected clients who want it
     */
    private static class SendConsoleMessage implements Runnable
    {
        private int i = 0;
        private long id = 0;
        private String name = new String();

        public void run()
        {
            id = Thread.currentThread().getId();
            name = Thread.currentThread().getName();

            if( debugLevel >= 3 )
                logger.debug(3, "Thread started (name=" + name + ", id=" + id + ").");

            try
            {
                for(;;)
                {
                    while( ! newMessage ) Thread.sleep(10);

                    if( debugLevel >= 3 )
                        logger.debug(3, "New message flag is set, processing new outgoing message.");

                    // Send the message to the clients
                    for( i = 0; i < maxClients; i++ )
                    {
                        if( connections[i].isAlive() )
                        {
                            if( connections[i].getWantsIP().equals(messageInfo[0]) )
                            {
                                if( connections[i].getWantsPort().equals(messageInfo[1]) )
                                {
                                    connections[i].send(messageData.array(), 0, messageData.limit());

                                    if( debugLevel >= 2 )
                                        logger.debug(2, "Sent this message to " + connections[i].getClientString() + ".");
                                }
                            }
                        }
                    }

                    // Increment the relayed packets counter
                    relayedPackets++;

                    // Clear the new message flag
                    newMessage = false;

                    if( debugLevel >= 3 )
                        logger.debug(3, "Cleared the new message flag.");
                }
            }
            catch( InterruptedException ie )
            {
                if( debugLevel >= 3 )
                    logger.debug(3, name + " [ID=" + id + "] received an interrupt.");

                return;
            }
        }
    }

    /*
     * Check for disconnected clients
     */
    private static class CheckConnection implements Runnable
    {
        private int i = 0;
        private String clientString;

        private long id = 0;
        private String name = new String();

        public void run()
        {
            id = Thread.currentThread().getId();
            name = Thread.currentThread().getName();

            if( debugLevel >= 3 )
                logger.debug(3, "Thread started (name=" + name + ", id=" + id + ").");

            try
            {
                for(;;)
                {
                    Thread.sleep(clientCheckInterval);

                    // Send a packet to each client and then clean up clients which do not respond
                    for( i = 0; i < maxClients; i++ )
                    {
                        if( connections[i].isAlive() )
                        {
                            if( connections[i].checkSocket() != 0 )
                            {
                                clientString = connections[i].getClientString();
                                logger.writeln( "Removing client " + clientString + " : No response to socket check." );
                                connections[i].closeSocket();
                                connections[i].kill();
                            }
                        }
                    }
                }
            }
            catch( InterruptedException ie )
            {
                if( debugLevel >= 3 )
                    logger.debug(3, name + " [ID=" + id + "] received an interrupt.");

                return;
            }
        }
    }

    /*
     * Periodically log some statsistics
     */
    private static class LogStats implements Runnable
    {
        long uptime = 0;
        long upDays = 0;
        long upHours = 0;
        long upMinutes = 0;
        long usedMem = 0;
        long freeMem = 0;
        long totalMem = 0;
        long maxMem = Runtime.getRuntime().maxMemory()/1024;

        private String uptimeMessage = new String();
        private String memoryMessage = new String();

        private long id = 0;
        private String name = new String();

        public void run()
        {
            id = Thread.currentThread().getId();
            name = Thread.currentThread().getName();

            if( debugLevel >= 3 )
                logger.debug(3, "Thread started (name=" + name + ", id=" + id + ").");

            for(;;)
            {
                try
                {
                    Thread.sleep(logStatsInterval);

                    totalMem = Runtime.getRuntime().totalMemory()/1024;
                    freeMem = Runtime.getRuntime().freeMemory()/1024;
                    usedMem = totalMem - freeMem;

                    uptime = ((System.currentTimeMillis() - START_TIME)/1000)/60;
                    upDays = uptime/1440;
                    upHours = (uptime - (upDays*1440))/60;
                    upMinutes = (uptime - (upDays*1440) - (upHours*60));
                    uptimeMessage = "";

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

                    logger.writeln( "[STATS] Uptime: " + uptimeMessage );
                    logger.writeln( "[STATS] Memory: " + memoryMessage );
                    logger.writeln( "[STATS] Total packets received: " + totalPackets );
                    logger.writeln( "[STATS] Total packets relayed: " + relayedPackets );
                    logger.writeln( "[STATS] Accepted client connections: " + acceptedConnections );
                    logger.writeln( "[STATS] Rejected client connections: " + rejectedConnections );
                    logger.writeln( "[STATS] Clients currently connected: " + numClients );

                    if( autoBanEnabled == 1 )
                        logger.writeln( "[STATS] Clients currently banned: " + bannedClients.size() );
                }
                catch( InterruptedException ie )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, name + " [ID=" + id + "] received an interrupt.");

                    return;
                }
                catch( Exception e )
                {
                    if( debugLevel >= 3 )
                        logger.debug(3, name + " [ID=" + id + "] caught an exception.");

                    logger.writeln( "[ERROR] LogStats thread caught an exception:" );
                    logger.writeln( "[ERROR] " + e.toString() );

                    if( debugLevel >= 2 )
                    {
                        StackTraceElement[] ste = e.getStackTrace();

                        for(int x = 0; x < ste.length; x++)
                            logger.debug(2, ste[x].toString() );
                    }
                }
            }
        }
    }

    /*
     * Check for expired bans
     */
    private static class CheckBans implements Runnable
    {
        private Map.Entry set;
        private Iterator i;
        private String key;
        private long exp;
	private long now;

        private long id = 0;
        private String name = new String();

        public void run()
        {
            id = Thread.currentThread().getId();
            name = Thread.currentThread().getName();

            if( debugLevel >= 3 )
                logger.debug(3, "Thread started (name=" + name + ", id=" + id + ").");

            try
            {
                for(;;)
                {
                    if( ! bannedClients.isEmpty() )
                    {
                        i = bannedClients.entrySet().iterator();
                        now = System.currentTimeMillis();

                        while( i.hasNext() )
                        {
                            set = (Map.Entry)i.next();
                            key = (String)set.getKey();
                            exp = bannedClients.get(key).longValue();

                            if( (now-exp) >= autoBanDuration )
                            {
                                logger.writeln( "[AUTO-BAN] Removing expired ban for " + key + "." );
                                i.remove();
                                bannedClients.remove(key);
                            }
                        }
                    }

                    Thread.sleep(60000);
                }
            }
            catch( InterruptedException ie )
            {
                if( debugLevel >= 3 )
                    logger.debug(3, name + " [ID=" + id + "] received an interrupt.");

                return;
            }
        }
    }

    /*
     * Rotate log files
     */
    private static class LogRotate implements Runnable
    {
        private static File baseFile = new File(logFile);
	private static File[] logFiles = new File[logRotateKeepFiles];

        private long id = 0;
        private String name = new String();

        public void run()
        {
            id = Thread.currentThread().getId();
            name = Thread.currentThread().getName();

            if( debugLevel >= 3 )
                logger.debug(3, "Thread started (name=" + name + ", id=" + id + ").");

            try
            {
                int i = 0;

                for( i = 0; i < logFiles.length; i++ )
                    logFiles[i] = new File(logFile + "." + (i+1));

                for(;;)
                {
                    Thread.sleep(logRotateInterval);

                    logger.close();

                    for( i = logFiles.length-2; i >= 0; i-- )
                        if( logFiles[i].exists() )
                            logFiles[i].renameTo(logFiles[i+1]);

                    baseFile.renameTo(logFiles[0]);

                    logger.open(logFile);
                }
            }
            catch( InterruptedException ie )
            {
                if( debugLevel >= 3 )
                    logger.debug(3, name + " [ID=" + id + "] received an interrupt.");

                return;
            }
        }
    }

    /*
     * Logger
     */
    private static class Logger
    {
        private static FileWriter out;
        private static SimpleDateFormat sdf;
        private static String eol;
        private static boolean open;

        public Logger()
        {
            open = false;
        }

        public static int open(String file)
        {
            try
            {
                // Get the platform-specific line separator
                eol = System.getProperty( "line.separator" );

                // Get an output stream to the log file
                out = new FileWriter(file, true);

                // Set the format mask for timestamps on log messages
                sdf = new SimpleDateFormat( "[EEE MMM dd HH:mm:ss yyyy]: " );

                // Set the open flag
                open = true;

                writeln( "Logger started." );

                return 0;
            }
            catch( IOException ioe )
            {
                open = false;
                return -1;
            }
        }

        public static int close()
        {
            try
            {
                // Make sure the log file is open
                if( ! open )
                    return -1;

                writeln( "Shutting down logger." );

                // Flush pending writes and close the log file
                out.flush();
                out.close();

                return 0;
            }
            catch( IOException ioe )
            {
                return -1;
            }
        }

        public static void debug(int level, String msg)
        {
            try
            {
                // Make sure the log file is open
                if( ! open )
                    return;

                // Write a timestamped debug message to the log file
                out.write(sdf.format(System.currentTimeMillis()) + "[DEBUG(" + level + ")] " + msg + eol);
                out.flush();
            }
            catch( IOException ioe )
            {
                return;
            }
        }

        public static void writeln(String msg)
        {
            try
            {
                // Make sure the log file is open
                if( ! open )
                    return;

                // Write a timestamped message to the log file
                out.write(sdf.format(System.currentTimeMillis()) + msg + eol);
                out.flush();
            }
            catch( IOException ioe )
            {
                return;
            }
        }
    }

    /*
     * Class for client connections
     */
    private static class Connection extends Thread
    {
        private Socket sock;
        private OutputStream out;
        private InputStream in;
        private String clientString;
        private String wantsIP;
        private String wantsPort;
        private int sendBufferSize;
        private int recvBufferSize;

        private static byte[] heartbeatBytes = new byte[5];
        private static ByteBuffer heartbeatBuffer = ByteBuffer.wrap(heartbeatBytes);

        // Empty constructor for initialization
        public Connection()
        {
            heartbeatBuffer.order(ByteOrder.LITTLE_ENDIAN);
            heartbeatBuffer.putInt(PACKET_HEADER);
            heartbeatBuffer.put(PTYPE_HEARTBEAT);
            heartbeatBuffer.flip();
        }

        // Full constructor for client connections
        public Connection(Socket s, String i, String p)
        {
            try
            {
                sock = s;
                wantsIP = i;
                wantsPort = p;
                in = sock.getInputStream();
                out = sock.getOutputStream();
                recvBufferSize = sock.getReceiveBufferSize();
                sendBufferSize = sock.getSendBufferSize();
                clientString = sock.getInetAddress().getHostAddress() + ":" + sock.getPort();
                start();
            }
            catch( Exception e )
            {
                logger.writeln( "[ERROR] Failed to create connection object for client." );
                logger.writeln( "[ERROR] " + e.toString() );
                this.interrupt();
            }
        }

        public void run()
        {
            numClients++;
            acceptedConnections++;

            try
            {
                // Discard any remaining bytes sent from the client
                this.flushInputBuffer();

                // Just keep sleeping...
                for(;;) { Thread.sleep(Long.MAX_VALUE); }
            }
            catch( InterruptedException ie )
            {
                numClients--;
                return;
            }
        }

        public Socket getSocket()
        {
            return sock;
        }

        public String getClientString()
        {
            return clientString;
        }

        public InputStream getSocketInputStream()
        {
            return in;
        }

        public OutputStream getSocketOutputStream()
        {
            return out;
        }

        public String getWantsIP()
        {
            return wantsIP;
        }

        public String getWantsPort()
        {
            return wantsPort;
        }

        public int send(byte[] b, int offset, int len)
        {
            try
            {
                out.write(b, offset, len);
                out.flush();
                return 0;
            }
            catch( IOException ioe )
            {
                return 1;
            }
        }

        public void flushInputBuffer()
        {
            try
            {
                in.skip((long)recvBufferSize);
                return;
            }
            catch( SocketTimeoutException ste )
            {
                return;
            }
            catch( Exception e )
            {
                logger.writeln( "[ERROR] Failed to flush socket input buffer of client " + clientString + "." );
                logger.writeln( "[ERROR] " + e.toString() );
                return;
            }
        }

        public int checkSocket()
        {
            // Send a packet with only the header and a PTYPE_HEARTBEAT
            // byte to the client as a heartbeat

            try
            {
                out.write(heartbeatBuffer.array());
                out.flush();
                flushInputBuffer();
                return 0;
            }
            catch( Exception e )
            {
                return 1;
            }
        }

        public void closeSocket()
        {
            try
            {
                in.close();
                out.close();
                sock.close();
            }
            catch( IOException ioe )
            {
                logger.writeln( "Client " + getClientString() + " has disconnected." );
            }
        }

        public void kill()
        {
            this.interrupt();
        }
    }
}
