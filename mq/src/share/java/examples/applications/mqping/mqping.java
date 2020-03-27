/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

/*
 * @(#)mqping.java	1.4 07/02/07
 */ 

import jakarta.jms.*;
import java.util.*;

/**
 *
 * Version 1.0
 *
 * The mqping utility is similar to the Unix ping utility in some regards.  
 * With mqping, messages are sent to and received from a running broker.
 * The utility measures the round trip time.  The utility allows the user
 * to control the size of the message, the destination type, delivery mode
 * and send interval.
 *
 * This utility takes the following arguments:
 *   -t dest_type      Specify the optional destination type.  Valid values
 *                     are 't' or 'q'.  Default: 'q'
 *   -r                Optionally indicate the message is persistent.  
 *                     Not specifying this option indicates the message 
 *                     should not be persisted.
 *   -s size           Specify the optional size of the messages in bytes.
 *                     Default: 1024 
 *   -i delay          The interval (in seconds) between successive 
 *                     transmissions.  Default: 0 (no delay)
 *
 * By default mqping will connect to the broker running on localhost:7676.
 * You can use -DimqAddressList attribute to change the host, port and 
 * transport:
 *
 *	java mqping -DimqAddressList=mq://<host>:<port>/jms
 *
 */

public class mqping {
    ConnectionFactory   connectionFactory;
    Connection		connection;
    Session		session;
    MessageConsumer	receiver;
    String		destType;

    Destination		dest;
    boolean		interrupt = false;
    int			max = 0, min = 1000000, totalms = 0, sent = 0, recv = 0;

    public static void main(String args[])  {
        String          host = "localhost";
        int		size = 1024,
			deliveryMode = DeliveryMode.NON_PERSISTENT,
			sleepTime = 0;
	mqping		ping;

	ping = new mqping();
	ping.destType = new String("q");

	// Process the args
        for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals("-t"))  {
	        ping.destType = args[i+1];
		i++;
		if (!ping.destType.equals("q") && !ping.destType.equals("t")) {
                    System.err.println("Problems processing -t <dest_type>" +
				       " string.");
                    Usage();
		}
	    } else if (args[i].equals("-r"))  {
		deliveryMode = DeliveryMode.PERSISTENT;
	    } else if (args[i].equals("-s"))  {
                try {
                    size = Integer.parseInt(args[i+1]);
		    if (size < 0) {
                       System.err.println("Value of -s <size> less than 0: ");
                       Usage();
		    }
		    i++;
                } catch (Exception e) {
                    System.err.println("Problems processing -s <size> string.");
                    Usage();
                }
	    } else if (args[i].equals("-i"))  {
                try {
                    sleepTime = Integer.parseInt(args[i+1]);
		    if (sleepTime < 0) {
                       System.err.println("Value of -i <delay> less than 0: ");
                       Usage();
		    }
		    i++;
                } catch (Exception e) {
                    System.err.println("Problems processing -i <delay> string.");
                    Usage();
                }
	    } else
		Usage();
	}

	// Initialize the connection, session and destination.
	ping.initJMS();

	String address = System.getProperty("imqAddressList");
        if (address != null)  {
            int i = address.indexOf('/');
	    int j = address.lastIndexOf(':');
            if (j >= i+2)  {
                host = address.substring(i+2, j);
	    }
        }
        // Establish a shutdown hook to print ping stats.
	Thread hook = new PingShutdownHook(host, ping);

	try {
	    Runtime.getRuntime().addShutdownHook(hook);
	} catch (IllegalArgumentException e) {
	    System.err.println("Cannot establish a shutdown hook: "
	        + e.getMessage());
	    System.exit(1);
	} catch (java.lang.IllegalStateException e) {
	    // Do nothing.
	} catch (SecurityException e) {
	    System.err.println("Cannot establish a shutdown hook: "
	        + e.getMessage());
	    System.exit(1);
        }
	
	// Ping the broker.
	ping.ping(host, deliveryMode, size, sleepTime);
	try {
	   if (ping.interrupt != true)
	       ping.connection.close();
	} catch (JMSException e) {
	    System.err.println("Cannot close connection: "
	        + e.getMessage());
	    System.exit(1);
	}
    }

    public static void Usage () {
	System.out.print(
	      "Usage: java mqping [-t t/q] [-r] [-s size]");
	System.out.println(" [-i delay]");
	System.out.print("   -t: t = send to topic, q = send to queue");
	System.out.println("  Default: q");
	System.out.println(
		"   -r: send persistent messages.  Default: non-persistent");
	System.out.println(
		"   -s: size of messages to send in bytes.  Default: 1024");
	System.out.print(
		"   -i: the interval (in seconds) between successive");
	System.out.println(
		" transmissions.  Default: 0");
        System.exit(1);
    }

    public mqping() {
    }

    private void initJMS() {
	Random rand;

	try {

	    rand = new Random(Calendar.getInstance().getTimeInMillis());

            connectionFactory = new com.sun.messaging.ConnectionFactory();

            connection = connectionFactory.createConnection();
            
 	    // For Durable Subs we need to set the client ID but for this
	    // application we really don't need to use the same client ID
	    // from invocation to invocation.
	    if (destType.equals("t"))
	       connection.setClientID("MQPing" + rand.nextInt(1000000));

            // We don't really care about the Ack mode as that is not
	    // part of the measurement.
            session = connection.createSession(false, 
					       Session.DUPS_OK_ACKNOWLEDGE);

	    if (destType.equals("q"))
		dest = session.createQueue("MQPing" + rand.nextInt(1000000));
	   else
		dest = session.createTopic("MQPing" + rand.nextInt(1000000));

	   connection.start();

	} catch (Exception e) {
	    System.err.println("Problems creating JMS resources: "
	        + e.getMessage());
	    System.exit(1);
	}
    }

    private void ping(String host, int deliveryMode, int size, int sleepTime) {
	MessageProducer sender;
	TopicSubscriber sub;
	Message 	echo;
	long		currentTime;
	int 		diffms = 0;
	
	try {
	    sender = session.createProducer(dest);

	    if (destType.equals("q"))
	        receiver = session.createConsumer(dest);
	    else {
		sub = session.createDurableSubscriber((Topic)dest, "MQPing");
		receiver = (MessageConsumer) sub;
	    }

	    BytesMessage msg = session.createBytesMessage();
            byte[] data = new byte[size];
            msg.writeBytes(data, 0, data.length);


	    System.out.println("PING " + host + ": " + size + " data bytes");

            // Ping until we receive ^C	
  	    for (int i = 1; interrupt == false; i++) {
		msg.setLongProperty("sendTime", 
				    Calendar.getInstance().getTimeInMillis());
		msg.setLongProperty("sequence", i); 

		synchronized(this) {
		    // Send and Receive the message
	            sender.send(msg, deliveryMode, 1, 20000);	
		    echo = receiver.receive();

		    currentTime = Calendar.getInstance().getTimeInMillis();
		    diffms = 
			(int)(currentTime - echo.getLongProperty("sendTime"));
		}

		System.out.println(
			size + " bytes from " + host + 
			": sequence=" + echo.getLongProperty("sequence") +
			". time=" + diffms + "ms.");

		// Process stats
		sent++; recv++;
		totalms += diffms;
		if (diffms < min)
		    min = diffms;
		if (diffms > max)
		    max = diffms;
		if (sleepTime != 0)
		   sleep(sleepTime * 1000);
	    }
	} catch (InvalidDestinationException e) {
	    System.err.println("Invalid Destination: " + e.getMessage());
	    System.exit(1);
	} catch (JMSException e) {
	    if (interrupt != true) {
	        System.err.println("Error managing JMS resources: "
	            + e.getMessage());
	        System.exit(1);
	    }
	} catch (UnsupportedOperationException e) {
	    System.err.println("Destination not properly specified: " 
	        + e.getMessage());
	    System.exit(1);
	} catch (Exception e) {
	    System.err.println("Error: "
	        + e.getMessage());
	    e.printStackTrace();
	    System.exit(1);
	}
    }

    /**
      * Sleep for a specified time.
      * @param time Time in milliseconds to wait.
      */ 
    public void sleep (int time) {
        try {
            Thread.sleep(time);
        }
        catch (Exception e) {
        }
    }    
}

/**
 * A shutdown hook is called before the VM is going to exit.
 * Display stats here.
 */
class PingShutdownHook extends Thread {
    mqping ping;

    PingShutdownHook(String host, mqping ping) {
        super(host);
	this.ping = ping;
    }

    public void run() {
	int loss = 0, avg = 0;

	synchronized(ping) {
	    ping.interrupt = true;

	    try {
	        ping.receiver.close();

	        // We never need to keep the durable around beyond the life of
	        // this application.
	        if (ping.destType.equals("t"))
	            ping.session.unsubscribe("MQPing");

	       ping.connection.close();
	    } catch (JMSException e) {
	        System.err.println("Cannot close JMS resources: "
	            + e.getMessage());
	        System.exit(1);
	    }
	}

	loss = (int) (((ping.sent - ping.recv)/ping.sent) * 100); 
	avg = (int) (ping.totalms/ping.recv);

	System.out.println("----" + getName() + " PING Statistics----");
	System.out.println(ping.sent + " messages transmitted, " + ping.recv +
			" messages received, " + loss + "% message loss");
	System.out.println("round-trip (ms)  min/avg/max = " + ping.min +
			"/" + avg + "/" + ping.max);

    }    
}
