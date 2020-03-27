/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import java.util.Enumeration;
import java.util.Properties;
import jakarta.jms.*;

/**
 * The DestMetrics example is a JMS application that monitors a
 * destination on a Oracle GlassFish(tm) Server Message Queue broker. It does so by 
 * subscribing to a topic named:
 *	mq.metrics.destination.queue.<dest_name>	OR
 *	mq.metrics.destination.topic.<dest_name>
 * Messages that arrive contain information describing the 
 * destination such as:
 *  - number of messages that flowed into this destination
 *  - number of messages that flowed out of this destination
 *  - size of message bytes that flowed into this destination
 *  - size of message bytes that flowed out of this destination
 *  - etc.
 *
 * Note that this example does not display all the information
 * available in the destination metric message.
 *
 * This application takes the following arguments:
 *	-t dest_type	Specify required destination type. Valid values
 *			are 't' or 'q'.
 *	-n dest_name	Specify required destination name.
 *
 * By default DestMetrics will connect to the broker running on localhost:7676.
 * You can use -DimqAddressList attribute to change the host, port and 
 * transport:
 *
 *	java -DimqAddressList=mq://<host>:<port>/jms DestMetrics
 */
public class DestMetrics implements MessageListener  {
    ConnectionFactory        metricConnectionFactory;
    Connection               metricConnection;
    Session                  metricSession;
    MessageConsumer          metricConsumer;
    Topic                    metricTopic;
    MetricsPrinter           mp;
    String                   metricTopicName = null;
    int                      rowsPrinted = 0;
  
    public static void main(String args[])  {
	String		destName = null, destType = null;

	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals("-n"))  {
		destName = args[i+1];
	    } else if (args[i].equals("-t"))  {
		destType = args[i+1];
	    }
	}

	if (destName == null)  {
	    System.err.println("Need to specify destination name with -n");
	    System.exit(1);
	}

	if (destType == null)  {
	    System.err.println("Need to specify destination type (t or q) with -t");
	    System.exit(1);
	}

        DestMetrics bm = new DestMetrics();

        bm.initPrinter(destType, destName);
        bm.initJMS();
        bm.subscribeToMetric(destType, destName);
    }

    public DestMetrics() {
    }

    /*
     * Initializes the class that does the printing, MetricsPrinter.
     * See the MetricsPrinter class for details.
     */
    private void initPrinter(String destType, String destName) {
	String oneRow[] = new String[ 11 ], tmp;
	int    span[] = new int[ 11 ];
	int i = 0;

	mp = new MetricsPrinter(11, 2, "-", MetricsPrinter.CENTER);
	mp.setTitleAlign(MetricsPrinter.CENTER);

	i = 0;
	span[i++] = 2;
	span[i++] = 0;
	span[i++] = 2;
	span[i++] = 0;
	span[i++] = 3;
	span[i++] = 0;
	span[i++] = 0;
	span[i++] = 3;
	span[i++] = 0;
	span[i++] = 0;
	span[i++] = 1;

	i = 0;
	oneRow[i++] = "Msgs";
	oneRow[i++] = "";
	oneRow[i++] = "Msg Bytes";
	oneRow[i++] = "";
	oneRow[i++] = "Msg Count";
	oneRow[i++] = "";
	oneRow[i++] = "";
	oneRow[i++] = "Total Msg Bytes (k)";
	oneRow[i++] = "";
	oneRow[i++] = "";
	oneRow[i++] = "Largest";
	mp.addTitle(oneRow, span);

	i = 0;
        oneRow[i++] = "In";
	oneRow[i++] = "Out";
	oneRow[i++] = "In";
	oneRow[i++] = "Out";
	oneRow[i++] = "Current";
	oneRow[i++] = "Peak";
	oneRow[i++] = "Avg";
	oneRow[i++] = "Current";
	oneRow[i++] = "Peak";
	oneRow[i++] = "Avg";
	oneRow[i++] = "Msg (k)";
	mp.addTitle(oneRow);
    }

    /** 
     * Create the Connection and Session etc.
     */
    public void initJMS() {
        try {
            metricConnectionFactory = new com.sun.messaging.ConnectionFactory();
            metricConnection = metricConnectionFactory.createConnection();
            metricConnection.start();

            //  creating Session
            //	Transaction Mode: None
            //	Acknowledge Mode: Automatic
            metricSession = metricConnection.createSession(false,
				Session.AUTO_ACKNOWLEDGE);
        } catch(Exception e) {
            System.err.println("Cannot create metric connection or session: "
			+ e.getMessage());
            e.printStackTrace();
	    System.exit(1);
        }
    }
  
    public void subscribeToMetric(String destType, String destName) {
        try {

	    if (destType.equals("q"))  {
		metricTopicName = "mq.metrics.destination.queue." + destName;
	    } else  {
		metricTopicName = "mq.metrics.destination.topic." + destName;
	    }

            metricTopic = metricSession.createTopic(metricTopicName);

            metricConsumer = metricSession.createConsumer(metricTopic);
            metricConsumer.setMessageListener(this);
        } catch(JMSException e) {
            System.err.println("Cannot subscribe to metric topic: "
			+ e.getMessage());
            e.printStackTrace();
	    System.exit(1);
        }
    }

    /*
     * When a metric message arrives
     *	- verify it's type
     *	- extract it's fields
     *  - print one row of output
     */
    public void onMessage(Message m)  {
	try  {
	    MapMessage mapMsg = (MapMessage)m;
	    String type = mapMsg.getStringProperty("type");

	    if (type.equals(metricTopicName))  {
	        String oneRow[] = new String[ 11 ];
		int i = 0;

	        /*
	         * Extract destination metrics
	         */
		oneRow[i++] = Long.toString(mapMsg.getLong("numMsgsIn"));
		oneRow[i++] = Long.toString(mapMsg.getLong("numMsgsOut"));
		oneRow[i++] = Long.toString(mapMsg.getLong("msgBytesIn"));
		oneRow[i++] = Long.toString(mapMsg.getLong("msgBytesOut"));

		oneRow[i++] = Long.toString(mapMsg.getLong("numMsgs"));
		oneRow[i++] = Long.toString(mapMsg.getLong("peakNumMsgs"));
		oneRow[i++] = Long.toString(mapMsg.getLong("avgNumMsgs"));

		oneRow[i++] = Long.toString(mapMsg.getLong("totalMsgBytes")/1024);
		oneRow[i++] = Long.toString(mapMsg.getLong("peakTotalMsgBytes")/1024);
		oneRow[i++] = Long.toString(mapMsg.getLong("avgTotalMsgBytes")/1024);

		oneRow[i++] = Long.toString(mapMsg.getLong("peakMsgBytes")/1024);

		mp.add(oneRow);

		if ((rowsPrinted % 20) == 0)  {
		    mp.print();
		} else  {
		    mp.print(false);
		}

		rowsPrinted++;

		mp.clear();
	    } else  {
	        System.err.println("Msg received: not broker metric type");
	    }
	} catch (Exception e)  {
	    System.err.println("onMessage: Exception caught: " + e);
	}
    }
}
