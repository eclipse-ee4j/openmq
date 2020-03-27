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
 * @(#)QBrowser.java	1.10 07/02/07
 */ 

import java.awt.*;
import java.util.*;
import java.text.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Connection;
import jakarta.jms.Session;
import jakarta.jms.Destination;
import jakarta.jms.Queue;
import jakarta.jms.Topic;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Message;
import jakarta.jms.QueueBrowser;
import jakarta.jms.DeliveryMode;
import jakarta.jms.StreamMessage;
import jakarta.jms.MapMessage;
import jakarta.jms.ObjectMessage;
import jakarta.jms.BytesMessage;
import jakarta.jms.TextMessage;
import jakarta.jms.JMSException;


/**
 * The QBrowser example is a GUI application that lets you visually
 * examine the contents of a JMS Queue. It is written using javax.swing.
 *
 * By default QBrowser will connect to the imqbrokerd running
 * on localhost:7676. You can use -DimqAddressList attribute to change
 * the host, port and transport:
 *
 *     java -DimqAddressList=mq://<host>:<port>/jms QBrowser
 *
 * Once QBrowser is up, enter the name of a queue and click Browse.
 * A list of messages on the queue will appear in the main window.
 * Select a message and click "Details" to see the contents of the message.
 *
 * QBrowser consists of the following classes:
 *
 * QBrowser      main(), the base GUI frame, and the JMS code
 * MsgTable      A TableModel for handling the display of messages on a queue
 * PropertyPanel A JPanel with a scrolling text area for displaying 
 *               simple text, or the contents of a HashMap.
 * A number of minor event handling classes.
 *
 */
public class QBrowser extends JPanel implements jakarta.jms.MessageListener {

    JMenuItem	    exit_item = null;
    JLabel          qLabel = null;
    JComboBox       qBox = null;
    JButton         qBrowse = null;
    JTable          msgTable = null;
    JLabel          footerLabel = null;
    JPanel          footerPanel = null;
    QueueBrowser    qb = null;
    Session         session  = null;
    Connection      connection  = null;
    Topic metricTopic = null;
    MessageConsumer metricSubscriber = null;
    JFrame          detailsFrame = null;
    PropertyPanel headerPanel = null, propertyPanel = null, bodyPanel = null;

    static final String DEST_LIST_TOPIC_NAME = "mq.metrics.destination_list";
    public static String version = "1.0";
    public static String title = "QBrowser " + version;

    public static String[] pad = {"", "0", "00", "000", "0000"};
    public static String serverHost = "localhost";
    public static int    serverPort = 7676;

    QBrowser() {
	super(true);

	setBorder(BorderFactory.createEtchedBorder());
	setLayout(new BorderLayout());

        // Create menu bar
	JMenuBar  menubar = new JMenuBar();
	JMenu menu = new JMenu("File");
	exit_item = new JMenuItem("Exit");
	exit_item.addActionListener(new ExitListener());
	menu.add(exit_item);
	menubar.add(menu);

        // Create panel to hold input area for Q name and Browse button
        JPanel qPanel = new JPanel();
        qPanel.setLayout(new BorderLayout());
	qPanel.add(BorderLayout.NORTH, menubar);

        qLabel = new JLabel("Queue Name: ");
        qPanel.add(BorderLayout.WEST, qLabel);

        qBox = new JComboBox();
        Dimension d = qBox.getPreferredSize();
        d.setSize(10 * d.getWidth(), d.getHeight());
        qBox.setPreferredSize(d);
        qBox.setEditable(true);
        //qBox.addActionListener(new BrowseListener());
        qPanel.add(BorderLayout.CENTER, qBox);
        qBrowse = new JButton("Browse");
        qBrowse.addActionListener(new BrowseListener() );
        qPanel.add(BorderLayout.EAST, qBrowse);

        qPanel.updateUI();
        //qPanel.setBackground(Color.YELLOW);

        add(BorderLayout.NORTH, qPanel);

        // Create panel to hold table of messages
        JPanel tPanel = new JPanel();
        tPanel.setLayout(new BorderLayout());

        msgTable = new JTable(new MsgTable());
        msgTable.addMouseListener(new TableMouseListener());

        TableColumn column = msgTable.getColumnModel().getColumn(1);
        column.setPreferredWidth(190);
        column = msgTable.getColumnModel().getColumn(2);
        column.setPreferredWidth(130);

        JScrollPane tablePane = new JScrollPane(msgTable);
        tablePane.setPreferredSize(new Dimension(100, 300));
        //tablePane.setMinimumSize(new Dimension(100, 100));
        tPanel.add(BorderLayout.CENTER, tablePane);

        add(BorderLayout.CENTER, tPanel);

        // Create footer
        footerPanel = new JPanel();
        footerPanel.setLayout(new BorderLayout());
        footerLabel = new JLabel("");
        footerPanel.add(BorderLayout.WEST, footerLabel);

        JButton details = new JButton("Details...");
        details.addActionListener(new DetailsListener() );
        footerPanel.add(BorderLayout.EAST, details);

        add(BorderLayout.SOUTH, footerPanel);

        setFooter("Enter a Queue Name and click Browse");

        try {
            connect();
        } catch (JMSException ex) {
            System.err.println("Could not initialize JMS: " + ex);
            System.err.println(
                "Are you sure there is an imqbrokerd running on " +
                serverHost + ":" + serverPort + "?" );
            usage();
        }

    }

    private void shutdownJMS() {
        try {
            connection.close();
        } catch (JMSException e) {
            System.out.println("Exception closing JMS connection: " + e);
        }
    }

    /**
     * Initialize JMS by creating Connection and Session.
     */
    private void initJMS() throws JMSException {
        ConnectionFactory  cf = null;

        cf = new com.sun.messaging.ConnectionFactory();

        connection = cf.createConnection();
        
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    /**
     * Setup a consumer that listens on the Message Queue monitoring topic
     * that sends out lists of destinations.
     */
    private void initDestListConsumer() throws JMSException {
        metricTopic = session.createTopic(DEST_LIST_TOPIC_NAME);
        metricSubscriber = session.createConsumer(metricTopic);
        metricSubscriber.setMessageListener(this);
    }



    /**
     * Set text on footer
     */
    private void setFooter(String s) {
        footerLabel.setText(s);
        footerLabel.paintImmediately(footerLabel.getBounds());
    }

    /**
     * Show the contents of a message in a seperate popup window
     */
    private void showDetails(Message msg, int msgno) {
        if (detailsFrame == null) {
            // Create popup
            detailsFrame = new JFrame();
            detailsFrame.setTitle(QBrowser.title + " - Message Details");
            detailsFrame.setBackground(Color.white);
            detailsFrame.getContentPane().setLayout(new BorderLayout());

            headerPanel = new PropertyPanel();
            headerPanel.setTitle("JMS Headers");
            detailsFrame.getContentPane().add(BorderLayout.NORTH, headerPanel);

            propertyPanel = new PropertyPanel();
            propertyPanel.setTitle("Message Properties");
            detailsFrame.getContentPane().add(BorderLayout.CENTER, propertyPanel);

            bodyPanel = new PropertyPanel();
            bodyPanel.setTitle("Message body");
            detailsFrame.getContentPane().add(BorderLayout.SOUTH, bodyPanel);
            detailsFrame.pack();
        }

        // Load JMS headers from message
        try {
            HashMap hdrs = jmsHeadersToHashMap(msg);
            headerPanel.setTitle("JMS Headers: Message #" + msgno);
            headerPanel.load(hdrs);
        } catch (JMSException ex) {
            setFooter("Error: " + ex.getMessage());
        }

        // Load message properties
        HashMap props = new HashMap();
        // Get all message properties and stuff into a hash table
        try {
            for (Enumeration enu = msg.getPropertyNames();
                enu.hasMoreElements();) {

                String name = (enu.nextElement()).toString();
                props.put(name, (msg.getObjectProperty(name)).toString());
            }
        } catch (JMSException ex) {
            setFooter("Error: " + ex.getMessage());
        }
        propertyPanel.load(props);

        // Load message body
        bodyPanel.setTitle("Message Body: (" + QBrowser.messageType(msg) + ")");
        bodyPanel.load(jmsMsgBodyAsString(msg));

        detailsFrame.show();
    }

    private void connect() throws JMSException {
        if (connection == null) {
            setFooter("Connecting to " + serverHost + ":" +
                       serverPort + "...");
            initJMS();

            try {
                initDestListConsumer();
            } catch (JMSException e) {
                // If we can't subscribe to the mq.metrics topic then we
                // are probably not running against an EE broker. That's
                // OK. It just means we can't populate the Destination
                // combo-box on the GUI.
                //System.out.println("Could not subscribe to " +
                //    DEST_LIST_TOPIC_NAME);
            }
            connection.start();
            setFooter("Connected to " + serverHost + ":" + serverPort);
        }
    }


    /**
     * Browse the queue
     */
    private void doBrowse() {

        ComboBoxEditor editor = qBox.getEditor();
        String name = (String)editor.getItem();
        setFooter("Browsing " + name + "...");

        // Browse queue
        try {
            String selector = null;
            Queue q = session.createQueue(name);
            QueueBrowser qb;
	    if (selector == null) {
                qb = session.createBrowser(q);
	    } else {
                qb = session.createBrowser(q, selector);
	    }
            // Load messages into table
            MsgTable mt = (MsgTable)msgTable.getModel();
            int n = mt.load(qb.getEnumeration());
            setFooter(name + ": " + String.valueOf(n));
            qb.close();
        } catch (JMSException ex) {
            setFooter(ex.getMessage());
        }
    }

    /**
     * Add a name to the "Queue Name" combo box menu
     */
    private void addDestToMenu(String name) {
        DefaultComboBoxModel  model  = (DefaultComboBoxModel)qBox.getModel();

        if (model.getIndexOf(name) < 0) {
            // Name is not in menu. Add it.
            model.addElement(name);
        } 
    }

    /**
     * Main
     */
    public static void main (String args[]) {

        if (args.length > 0) {
            usage();
        }

 	String address = System.getProperty("imqAddressList");
        if (address != null)  {
            int i = address.indexOf('/');
	    int j = address.lastIndexOf(':');
            int k = address.indexOf('/', j);
            if (j > i+2)  {
                serverHost = address.substring(i+2, j);
	    }
           if (k > j) {
                serverPort = Integer.parseInt(address.substring(j+1, k));
            }
        }

       JFrame frame = new JFrame();
       frame.setTitle(QBrowser.title + " - " + serverHost + ":" + serverPort);
       frame.setBackground(Color.white);
       frame.getContentPane().setLayout(new BorderLayout());
       frame.getContentPane().add("Center", new QBrowser());
       frame.pack();
       frame.show();
    }

    private static void usage() {
        System.out.println(
            "usage: java QBrowser \n" );
        System.exit(1);
    }

    public static void dumpException(Exception e) {
        Exception linked = null;
	if (e instanceof JMSException) {
            linked = ((JMSException)e).getLinkedException();
        }

        if (linked == null) {
            e.printStackTrace();
        } else {
            System.err.println(e.toString());
            linked.printStackTrace();
        }
    }

    /**
     * Return a string description of the type of JMS message
     */
    static String messageType(Message m) {

        if (m instanceof TextMessage) {
            return "TextMessage";
        } else if (m instanceof BytesMessage) {
            return "BytesMessage";
        } else if (m instanceof MapMessage) {
            return "MapMessage";
        } else if (m instanceof ObjectMessage) {
            return "ObjectMessage";
        } else if (m instanceof StreamMessage) {
            return "StreamMessage";
        } else if (m instanceof Message) {
            return "Message";
        } else {
            // Unknown Message type
            String type = m.getClass().getName();
            StringTokenizer st = new StringTokenizer(type, ".");
            String s = null;
            while (st.hasMoreElements()) {
                s = st.nextToken();
            }
            return s;
        }
    }

    /**
     * Return a string representation of the body of a JMS
     * bytes message. This is basically a hex dump of the body.
     * Note, this only looks at the first 1K of the message body.
     */
    private static String jmsBytesBodyAsString(Message m) {
        byte[] body = new byte[1024];
        int n = 0;

        if (m instanceof BytesMessage) {
            try {
                ((BytesMessage)m).reset();
                n = ((BytesMessage)m).readBytes(body);
            } catch (JMSException ex) {
                return (ex.toString());
            }
        } else if (m instanceof StreamMessage) {
            try {
                ((StreamMessage)m).reset();
                n = ((StreamMessage)m).readBytes(body);
            } catch (JMSException ex) {
                return (ex.toString());
            }
        }

        if (n <= 0) {
            return "<empty body>";
        } else {
            return(toHexDump(body, n) +
                   ((n >= body.length ) ? "\n. . ." : "") );
        }
    }

    /**
     * Return a string representation of a JMS message body
     */
    private static String jmsMsgBodyAsString(Message m) {

        if (m instanceof TextMessage) {
            try {
                return ((TextMessage) m).getText();
            } catch (JMSException ex) {
                return ex.toString();
            }
        } else if (m instanceof BytesMessage) {
            return jmsBytesBodyAsString(m);
        } else if (m instanceof MapMessage) {
            MapMessage msg = (MapMessage)m;
            HashMap props = new HashMap();
            // Get all MapMessage properties and stuff into a hash table
            try {
                for (Enumeration enu = msg.getMapNames();
                    enu.hasMoreElements();) {
                    String name = (enu.nextElement()).toString();
                    props.put(name, (msg.getObject(name)).toString());
                }
                return props.toString();
            } catch (JMSException ex) {
                return (ex.toString());
            }
        } else if (m instanceof ObjectMessage) {
            ObjectMessage msg = (ObjectMessage)m;
            Object obj = null;
            try {
                obj = msg.getObject();
                if (obj != null) {
                    return obj.toString();
                } else {
                    return "null";
                }
            } catch (Exception ex) {
                return (ex.toString());
            }
        } else if (m instanceof StreamMessage) {
            return jmsBytesBodyAsString(m);
        } else if (m instanceof Message) {
            return "Can't get body for message of type Message";
        } 
        return "Unknown message type " + m;
    }

    /**
     * Takes the JMS header fields of a JMS message and puts them in 
     * a HashMap
     */
    private static HashMap jmsHeadersToHashMap(Message m) throws JMSException {
        HashMap hdrs = new HashMap();
        String s = null;

        s = m.getJMSCorrelationID();
        hdrs.put("JMSCorrelationID", s);

        s = String.valueOf(m.getJMSDeliveryMode());
        hdrs.put("JMSDeliverMode", s);

        Destination d = m.getJMSDestination();
        if (d != null) {
            if (d instanceof Queue) {
                s = ((Queue)d).getQueueName();
            } else {
                s = ((Topic)d).getTopicName();
            }
        } else {
            s = "";
        }
        hdrs.put("JMSDestination", s);

        s = String.valueOf(m.getJMSExpiration());
        hdrs.put("JMSExpiration", s);

        s = m.getJMSMessageID();
        hdrs.put("JMSMessageID", s);

        s = String.valueOf(m.getJMSPriority());
        hdrs.put("JMSPriority", s);

        s = String.valueOf(m.getJMSRedelivered());
        hdrs.put("JMSRedelivered", s);

        d = m.getJMSDestination();
        if (d != null) {
            if (d instanceof Queue) {
                s = ((Queue)d).getQueueName();
            } else {
                s = ((Topic)d).getTopicName();
            }
        } else {
            s = "";
        }
        hdrs.put("JMSReplyTo", s);

        s = String.valueOf(m.getJMSTimestamp());
        hdrs.put("JMSTimestamp", s);

        s = m.getJMSType();
        hdrs.put("JMSType", s);

        return hdrs;
    }

    /**
     * Takes a buffer of bytes and returns a hex dump. Each hex digit
     * represents 4 bits. The hex digits are formatted into groups of
     * 4 (2 bytes, 16 bits). Each line has 8 groups, so each line represents
     * 128 bits.
     */
    private static String toHexDump(byte[] buf, int length) {

        // Buffer must be an even length
        if (buf.length % 2 != 0) {
            throw new IllegalArgumentException();
        }

	int value;
	StringBuffer sb = new StringBuffer(buf.length * 2);

	/* Assume buf is in network byte order (most significant byte
	 * is buf[0]). Convert two byte pairs to a short, then
	 * display as a hex string.
	 */
	int n = 0;
	while (n < buf.length && n < length) {
	    value = buf[n + 1] & 0xFF;		// Lower byte
	    value |= (buf[n] << 8) & 0xFF00;	// Upper byte
            String s = Integer.toHexString(value);
            // Left bad with 0's
	    sb.append(pad[4 - s.length()]);
            sb.append(s);
	    n += 2;

            if (n % 16 == 0) {
                sb.append("\n");
            }  else {
                sb.append(" ");
            }
         }
	 return sb.toString();
    }

    /**
     * Consumer that listens on the MQ monitoring topic that sends
     * out lists of destination names. We use this to update the
     * combo-box menu.
     */
    public void onMessage(Message msg) {

	try  {
            MapMessage mapMsg = (MapMessage)msg;
            String type = mapMsg.getStringProperty("type");

            if (type.equals(DEST_LIST_TOPIC_NAME))  {
		String oneRow[] = new String[ 3 ];

                TreeSet names = new TreeSet();

		/*
                 * Extract list of destinations
		 */
		for (Enumeration e = mapMsg.getMapNames();
                     e.hasMoreElements();) {
                    String name = (String)e.nextElement();
                    Hashtable values = (Hashtable)mapMsg.getObject(name);

                    // Sort names by putting them into TreeSet
                    if (values.get("type").toString().equals("queue")) {
                        names.add((String)values.get("name"));
                    }
		}

                // Add sorted names to combo box menu
                for (Iterator iter = names.iterator(); iter.hasNext();) {
                    addDestToMenu((String)iter.next());
                }
            } else {
                System.err.println(
                    "Msg received: not destination list metric type");
            }
	} catch (Exception e)  {
	    System.err.println("onMessage: Exception caught: " + e);
        }
    }

    class OptionListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
	    System.out.println("ItemEvent");
	}
    }

    class ExitListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
	    shutdownJMS();
            System.exit(0);
	}
    }

    class BrowseListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            doBrowse();
	}
    }

    class TableMouseListener extends MouseAdapter {
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                int row = msgTable.getSelectedRow();
                MsgTable mt = (MsgTable)msgTable.getModel();
                Message msg = mt.getMessageAtRow(row);
                showDetails(msg, row);
            }
        }
    }

    class DetailsListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            int row = msgTable.getSelectedRow();
            if (row < 0) {
                setFooter("Please select a message");
                return;
            }
            MsgTable mt = (MsgTable)msgTable.getModel();
            Message msg = mt.getMessageAtRow(row);
            showDetails(msg, row);
        }
    }


/**
 * A table of JMS Messages
 */
class MsgTable extends AbstractTableModel {

    final String[] columnNames =
                {"#", "Timestamp", "Type", "Mode", "Priority"};

    SimpleDateFormat df =
        new SimpleDateFormat("dd/MMM/yyyy:kk:mm:ss z");

    LinkedList list = null;

    public int getRowCount() {
        if (list == null) {
            return 0;
        } else {
            return list.size();
        }
    }

    public int getColumnCount() {
        return columnNames.length;
    }

    public String getColumnName(int column) {
        return columnNames[column];
    }

    public Object getValueAt(int row, int column) {
        if (list == null) {
            return null;
        }

        Message m = (Message)list.get(row);

        if (m == null) {
            return "null";
        }

        try {
            switch (column) {
            case 0:
                // Message number is the same as the row number
                return new Integer(row);
            case 1:
                // Need to format into date/time
                return df.format(new Date(m.getJMSTimestamp()));
            case 2:
                return QBrowser.messageType(m);
            case 3:
                // Delivery mode
                int mode = m.getJMSDeliveryMode();
                if (mode == DeliveryMode.PERSISTENT) {
                    return "P";
                } else if (mode == DeliveryMode.NON_PERSISTENT) {
                    return "NP";
                } else {
                    return String.valueOf(mode) + "?";
                }
            case 4:
                // Priority
                return new Integer(m.getJMSPriority());
            default:
                return "Bad column value: " + column;
            }
        } catch (JMSException e) {
            return ("Error: " + e);
        }
    }



    /**
     * Load and enumeration of messages into the table
     */
    int load (Enumeration e) {
        if (e == null) {
            return 0;
        }

        list = new LinkedList();

        while (e.hasMoreElements()) {
            list.add(e.nextElement());
        }

        fireTableDataChanged();

        return list.size();
    }

    Message getMessageAtRow(int row) {
        if (list == null) return null;
        return((Message)list.get(row));
    }
}


/**
 * A panel with a text area that knows how to format and display
 * a HashMap of values.
 */
class PropertyPanel extends JPanel {

    JLabel      label = null;
    JTextArea   textArea = null;
    JScrollPane areaScrollPane = null;

    PropertyPanel() {
        super(true);
        setBorder(BorderFactory.createEtchedBorder());
        setLayout(new BorderLayout());

        label = new JLabel();

        textArea = new JTextArea();
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        areaScrollPane = new JScrollPane(textArea);
        areaScrollPane.setVerticalScrollBarPolicy(
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        areaScrollPane.setPreferredSize(new Dimension(500, 150));

	add(BorderLayout.NORTH, label);
	add(BorderLayout.CENTER, areaScrollPane);
    }

    void setTitle(String title) {
        label.setText(title);
    }

    /**
     * Display a HashMap in the text window
     */
    void load(HashMap map) {

        StringBuffer buf = new StringBuffer();

        Set entries = map.entrySet();
        Map.Entry entry = null;
        Iterator iter = entries.iterator();
        while (iter.hasNext()) {
            entry = (Map.Entry)iter.next();
            String key = entry.getKey().toString();

            Object o = entry.getValue();
            String value = "";
            if (o != null) {
                value = o.toString();
            }

            buf.append(pad(key + ": ", 20));
            buf.append(value + "\n");
        }

        textArea.setText(buf.toString());

        areaScrollPane.scrollRectToVisible(new Rectangle(0, 0, 1, 1));

    }

    /**
     * Display text in the text window
     */
    void load(String s) {
        textArea.setText(s);
    }

    /**
     * Pad a string to the specified width, right justified.
     * If the string is longer than the width you get back the
     * original string.
     */
    String pad(String s, int width) {

        // Very inefficient, but we don't care
        StringBuffer sb = new StringBuffer();
        int padding = width - s.length();

        if (padding <= 0) {
            return s;
        }

        while (padding > 0) {
            sb.append(" ");
            padding--;
        }
        sb.append(s);
        return sb.toString();
    }
}
}
