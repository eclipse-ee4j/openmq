/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

/*
 * @(#)Debug.java	1.16 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.io.*;
import java.util.StringTokenizer;
import java.util.Date;
import java.util.logging.Level;
import com.sun.messaging.jmq.io.*;

/**
 * This class is used to debug JMQ client API implementation.  To turn on
 * debugging mode, you can do one of the following options:
 *
 * 1. Define system property 'imq.debug'. For example, java -Dimq.debug TestClass ...
 * This will print out Exceptions that are 'absorbed' by the system (us).  These
 * are the exceptions that do not concern with the developer.  Such as when
 * Socket is closed, the ReadChannel will be interrupted.  We catch the
 * SocketException and exit.  Te exception will be printed out to the
 * PrintStream if 'imq.debug' property is set.
 *
 * 2. Define system property 'debug.verbose'.  For example,
 * java -Dimq.debug.verbose TestClass ...
 * This will printout #1 debugging messages plus some verbose messages in
 * the code.
 *
 * 3. Define system property 'ClassName'.  To debug a specific class, you
 * define 'imq.debug' system property and the 'className' you wish to debug.
 * For example, to debug TopicConnectionImpl,
 * java -Dimq.debug -DTopicConnectionImpl TestClass ...
 * This will dump the connection's set up information.
 * NOTE: To debug a super class, you have to define the class name of the
 * currentClass/subclass.
 *
 * 4. To debug packet, you have the following options:
 *    4.1 Define system property 'ReadWritePacket' or 'ReadOnlyPacket'
 *    or both.  If 'ReadWritePacket' is defined, the outgoing packet
 *    will be dumped to the print stream. If 'ReadOnlyPacket' is defined
 *    the incoming packet will be dumped to the print stream.
 *
 *    For example,
 *    java -Dimq.debug -DReadOnlyPacket -DReadWritePacket testProg
 *
 *    4.2 Define system property 'imq.packetType'.
 *    If a specific packet type is defined and 4.1 properties are not,
 *    the packet type defined will be dumpped to the print stream.
 *    For example,
 *    java -Dimq.debug -Dimq.packetType="28|29" TestProg
 *    will dump GOODBYE (28) and GOODBYE_REPLY (29) packets.
 *
 *
 * 4. Define 'imq.debug.all' system property.  This will dump debugging messages
 * include #1, #2, and #3.
 *
 * 5. You can also call Traceable.dump() anytime you wish.  Any class that
 * implements Traceable has a method dump() that prints information about
 * the class.
 *
 * 6. Define 'imq.debug.file=fileName' if the output is desired to go to a file.
 * For example,
 * java -Dimq.debug.file=debug.out -DReadWritePacket TestProg
 * This will dump all outgoing packets to debug.out file.
 * 
 * 7. Define 'imq.debug.transaction' syatem property will dump all pkts related to
 * transactions.
 */
public class Debug {

    public static final boolean debug;
    private static boolean debugAll = false;
    private static boolean debugVerbose = false;

    private static boolean silentMode = false;

    //default print stream
    private static PrintStream ps = System.out;


    //although all packets read are also ReadWritePacket type, this is chosen
    //so that the usage pattern is the same as all other Traceable classes.
    private static final String READ_ONLY_PACKET =
                                   "ReadOnlyPacket";
    private static final String READ_WRITE_PACKET =
                                   "ReadWritePacket";

    private static final String PACKET_TYPE = "imq.packetType";
    
    public static final String WRITING_PACKET = " -------writing packet----->";
    public static final String READING_PACKET = " <------reading packet------"; 
    
    private static boolean debugTransaction = false;

    private static boolean useLogger = false;
    
    static {
        boolean tmpdebug = false;
        if ( System.getProperty("imq.debug") != null ) {
            tmpdebug = true;
        } 

        //debug all classes - every Debug.printpl will be printed.
        if ( System.getProperty ("imq.debug.all") != null ) {
            tmpdebug = true;
            debugVerbose = true;
            debugAll = true;
        }

        if ( System.getProperty( "imq.debug.verbose") != null ) {
            tmpdebug = true;
            debugVerbose = true;
        }

        String debugFile = System.getProperty( "imq.debug.file" );
        if (debugFile != null) {
            tmpdebug = true;
            try {
                //set append mode to false
                FileOutputStream fos = new FileOutputStream ( debugFile, false );
                // set auto flush to true
                ps = new PrintStream ( fos, true );
            } catch ( Exception e ) {
                //System.out is used by default
                e.printStackTrace();
            }
        }

        if ( System.getProperty("imq.silent") != null ) {
            silentMode = true;
        }
        
        if ( System.getProperty("imq.debug.transaction") != null ) {
            tmpdebug = true;
            debugTransaction = true;
        }
        debug = tmpdebug;
        
    }

    public Debug() {
    }

    public static void println ( String msg ) {
        if ( debugVerbose ) {
            synchronized (ps) {
                ps.println(msg);
                ps.flush();
            }
        }
    }

    /**
     * print info message.
     * @param msg
     */
    public static void info (String msg ) {

        if ( silentMode == false ) {
            synchronized (ps) {
                ps.println(msg);
                ps.flush();
            }
        }
    }

    /**
     * print all packets read from protocol handler.
     */
    public static void printReadPacket ( ReadOnlyPacket pkt ) {
        String debugClass = System.getProperty(READ_ONLY_PACKET);

        boolean debugPacket = matchPacketType (pkt);

        if ( debugAll || (debugClass != null) || debugPacket ) {
            printPacket (pkt, READING_PACKET);
        }
    }

    /**
     * prints all packets written from protocol handler.
     */
    public static void printWritePacket ( ReadOnlyPacket pkt ) {
        String debugClass = System.getProperty(READ_WRITE_PACKET);

        boolean debugPacket = matchPacketType (pkt);

        if ( debugAll || (debugClass != null) || debugPacket ) {
            printPacket (pkt, WRITING_PACKET);
        }
    }

    /**
     * Match if packet type matches any defined properties.
     */
    public static boolean matchPacketType (ReadOnlyPacket pkt) {

        String prop = System.getProperty(PACKET_TYPE);

        boolean isMatched = matchPacketType (pkt, prop);
        
        if ( isMatched == false ) {
        	//if not defined individually, try if imq.debug.transaction is deifned
        	if ( debugTransaction ) {
        		isMatched = isTransactedPacket (pkt);
        	}
        }

        return isMatched;
    }
    
    /**
     * Match if packet type matches any defined properties.
     */
    public static boolean matchPacketType (ReadOnlyPacket pkt, String prop) {

        if ( prop != null ) {
            StringTokenizer tokenizer = new StringTokenizer ( prop, "|" );
            while ( tokenizer.hasMoreTokens() ) {
                String packetType = tokenizer.nextToken();
                if ( pkt.getPacketType() == Integer.parseInt(packetType) ) {
                    return true;
                }
            }
        } 

        return false;
    }
    
    /**
     * prints all packets.
     */
    private static void printPacket ( ReadOnlyPacket pkt, String msg ) {
    	
        synchronized (ps) {
			ps.println(new Date().toString() + msg);
			pkt.dump(ps);
			ps.flush();
		}
    }
    
    /**
     * print pkt if pkt type matches one of the types specified in prop.
     * @param pkt the pkt to be dumped.
     * @param prop a list of pkt types separated by '|'.  For example, "21|22".
     * @param msg the message to be printed before the pkt dump.
     */
    public static void 
    matchAndPrintPacket (ReadOnlyPacket pkt, String pktFilter, String msg) {
    	
    	boolean shouldPrint = true;
    	
    	if ( pktFilter != null ) {
    		shouldPrint = matchPacketType (pkt, pktFilter);
    	}
    	
    	if ( shouldPrint ) {
    		printPacket (pkt, msg);
    	}
    }

    /**
	 * Dump class info if Traceable object is defined in the system property.
	 * For example, java -DSessionReader=t ...
	 */
    public static void println ( Traceable traceable ) {

    	try {
			String debugClassFullName = traceable.getClass().getName();
			String debugClassName = debugClassFullName;
			String debugClass = null;

			// The following block is trying to obtain the class name (without
			// package name).
			int index = debugClassFullName.lastIndexOf('.');

			if (index >= 0) {
				debugClassName = debugClassFullName.substring(index + 1);
			}

			debugClass = System.getProperty(debugClassName);

			if (debugAll || (debugClass != null)) {
				synchronized (ps) {
					ps.println("-------- begin dump class: "
							+ traceable.getClass().getName());
					traceable.dump(ps);
					ps.println("^^^^^^^^ end dump class: "
							+ traceable.getClass().getName());
					ps.flush();
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
    }

    public static void setUseLogger(boolean b) {
        useLogger = b;
    }

    /**
	 * Print stack trace.
	 */
    public static synchronized void printStackTrace( Throwable e ) {

        if ( silentMode ) {
            return;
        }
        if (useLogger) {
        ExceptionHandler.rootLogger.log(Level.INFO, e.getMessage(), e);
        } else {

        e.printStackTrace(ps);
        }
    }

    /**
     * Get debug print stream.
     */
    public static PrintStream getPrintStream() {
        return ps;
    }
    
    public static boolean isTransactedPacket(ReadOnlyPacket pkt) {
    	
    	int type = pkt.getPacketType();
    	
    	switch (type ) {
    		case PacketType.ACKNOWLEDGE:
    		case PacketType.ACKNOWLEDGE_REPLY:
    		case PacketType.COMMIT_TRANSACTION:
    		case PacketType.COMMIT_TRANSACTION_REPLY:	
    		case PacketType.END_TRANSACTION:
    		case PacketType.END_TRANSACTION_REPLY:
    		case PacketType.PREPARE_TRANSACTION:
    		case PacketType.PREPARE_TRANSACTION_REPLY:
    		case PacketType.REDELIVER:
    		case PacketType.ROLLBACK_TRANSACTION:
    		case PacketType.ROLLBACK_TRANSACTION_REPLY:
    		case PacketType.RECOVER_TRANSACTION:
    		case PacketType.RECOVER_TRANSACTION_REPLY:
    		case PacketType.START_TRANSACTION:
    		case PacketType.START_TRANSACTION_REPLY:
    		case PacketType.START:
    		case PacketType.STOP:
    		case PacketType.STOP_REPLY:
    		case PacketType.VERIFY_TRANSACTION:
    		case PacketType.VERIFY_TRANSACTION_REPLY:
    			
    			return true;
    			
    		default:
    			
    			return false;
    	}
    }
    
}
    
/*
 * EOF
 */
