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
 * @(#)UniversalClient.java	1.14 07/02/07
 */ 
//package examples.applications.uclient;

import com.sun.messaging.jms.notification.ConnectionReconnectedEvent;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.text.Format;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.ResourceAllocationException;
import jakarta.jms.Session;
import jakarta.jms.StreamMessage;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.jms.TransactionRolledBackException;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EtchedBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;


/**
 * The UniversalClient example is a basic 'client' application that uses the JMS 1.1 APIs.
 * It uses JMS Message Producer and Consumer to send and receive message.
 *
 * When the application is launched, use the 'Universal Client'Actions menu to start or connect to
 * a boker.
 *
 * The broker host port can be provided using the GUI. Once the client is connected one can send
 * jms messages to any number of destination Topic or Queue.
 *
 * Once the Producer send messages to a destination of the given boker,
 * one can create a consumer for the same destination which receives the messages
 * and display them in a Message Table.
 *
 * For destination of type Queue one can simply receive message either by using transacted session
 * or by using non-transacted session
 *
 * For topic type destination the one can create a durable Topic subscriber for a given
 * topic with a given selector. One can receive message using the topic subscriber created during the
 * first receive message button click, but if at later stage one changes the topic selector or the
 * transaction type a new durable topic selector is created
 *
 *
 * One can see the details of the message received, by using the 'Message Details' button.
 *
 */
public class UniversalClient implements UniversalClientConstants,
    ExceptionListener {

    static final String ADDRESSLIST_PROP = "imqAddressList";

    // Connection, factory and session..
    private ConnectionFactory myConnFactory;
    private Connection myConn;
    private Session myProducerSession;
    private Session myConsumerSession;
    private MessageProducer myProducer;
    private MessageConsumer myConsumer;
    private Destination myDestination;
    private boolean connected = false;

    //data structures
    private HashMap mySessionMap = new HashMap();
    private HashMap myProducerMap = new HashMap();

    //username, hostname
    private String userName;
    private String password;
    private String brokerAddress = "localhost:7676";
    private String clientID;

    // main frame
    private JFrame frame;

    //MenuBar and MenuItems
    private JMenuBar menuBar;
    private JMenuItem connectItem;
    private JMenuItem disconnectItem;
    private JMenuItem clearItem;
    private JMenuItem exitItem;
    private JMenuItem sendMsgItem;
    private JMenuItem sendMsgStopItem;
    private JMenuItem rcvMsgItem;
    private JMenuItem rcvMsgStopItem;

    //connection dialog box
    private ConnectionDialogBox connDialogB;
    private JButton connectB;
    private JButton connCancelB;

    //send message dialog box
    private SendMessageDialogBox sendMsgDialogB;
    private JButton sendMsgB;
    private JButton sendMsgCancelB;

    //Footer  panel
    private JPanel footerPanel;
    private JLabel footerLabel;
    private boolean footerInUse;

    //message table to show the received message
    private JTable msgTable;
    private JScrollPane msgTableScrollPane;
    private boolean scrollingON;

    //top panel
    private double msgProductionRate;
    private JPanel topPanel;
    private JLabel msgProductionRateLabel;
    private JLabel hostPortLabel;
    private JLabel clientIDLabel;
    private JProgressBar sendMsgProgressBar = new JProgressBar();

    //Message details frame
    private JFrame detailsFrame;
    private PropertyPanel msgDetailsHeaderPanel;
    private PropertyPanel msgDetailsPropertyPanel;
    private PropertyPanel msgDetailsBodyPanel;

    //receive messages dialog box
    private ReceiveMessageDialogBox receiveMsgDialogB;
    private JButton receiveMsgB;
    private JButton receiveMsgCancelB;

    //status area
    StatusArea statusArea;

    //stop msg sender
    private boolean msgSenderStopped;

    //  stop msg receiver
    private boolean msgReceiverStopped;

    public UniversalClient() {
        userName = "guest";
        password = "guest";

        clientID = System.getProperty("universalclient.cid", null);

        if (clientID == null) {
            clientID = System.getProperty("user.name", "UClient-ID");
        }

        createAndShowGUI();

        Thread statusReporter = new StatusReportingThread();
        statusReporter.setPriority(Thread.MIN_PRIORITY);
        statusReporter.setDaemon(true);
        statusReporter.start();
    }

    /**
     * Create the GUI and show it. For thread safety, this method should be
     * invoked from the event-dispatching thread.
     */
    private void createAndShowGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Make sure we have nice window decorations.
        //JFrame.setDefaultLookAndFeelDecorated(true);
        //Create and set up the window.
        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());

        //create Menubar
        menuBar = createMenuBar();
        frame.setJMenuBar(menuBar);

        //Add the top panel
        topPanel = createTopPanel();
        frame.getContentPane().add(topPanel, BorderLayout.NORTH);

        //create message table panel
        JPanel tablePanel = createTablePanel();

        //create footer Panel
        createFooterPanel();

        //add footer to message table
        tablePanel.add(footerPanel, BorderLayout.SOUTH);

        //create status area
        statusArea = new StatusArea();
        statusArea.appendText(
            "# Message Queue Client Runtime Connection Notification And Connection related log");

        /*
         * Create another split pane containing the table panel above
         * and the status area.
         */
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setOneTouchExpandable(true);
        splitPane.setTopComponent(tablePanel);
        splitPane.setBottomComponent(statusArea);
        splitPane.setResizeWeight(0.9);

        frame.getContentPane().add(splitPane, BorderLayout.CENTER);

        //set size of mainframe
        setSize();

        //Display the window.
        frame.setVisible(true);
    }

    /**
     * sets the size of the main frame
     */
    private void setSize() {
        int relativeSize = 10; //with base = 15
        String osName = System.getProperty("os.name", "");

        if (osName.indexOf("Windows") >= 0) {
            relativeSize = 12;
        }

        Toolkit tk = frame.getToolkit();
        Dimension d = tk.getScreenSize();
        frame.setSize((d.width * relativeSize) / 15,
            (d.height * relativeSize) / 15);
        frame.setLocation(d.width / 8, d.height / 16);
    }

    /**
     * Create menubar for application.
     */
    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu consoleMenu = (JMenu) mb.add(new JMenu("Universal Client"));
        consoleMenu.setMnemonic('C');

        JMenu actionsMenu = (JMenu) mb.add(new JMenu("Actions"));
        actionsMenu.setMnemonic('A');

        connectItem = addMenuItem(actionsMenu, "Connect ...");
        connectItem.setMnemonic('C');
        disconnectItem = addMenuItem(actionsMenu, "Disconnect");
        disconnectItem.setMnemonic('D');
        actionsMenu.add(new JSeparator());

        sendMsgItem = addMenuItem(actionsMenu, "Send Message ...");
        sendMsgItem.setMnemonic('M');
        sendMsgStopItem = addMenuItem(actionsMenu, "Stop Msg Sender");
        sendMsgStopItem.setMnemonic('S');
        actionsMenu.add(new JSeparator());

        rcvMsgItem = addMenuItem(actionsMenu, "Receive Message ...");
        rcvMsgItem.setMnemonic('R');
        rcvMsgStopItem = addMenuItem(actionsMenu, "Stop Msg Receiver");
        rcvMsgStopItem.setMnemonic('t');
        actionsMenu.add(new JSeparator());

        clearItem = addMenuItem(actionsMenu, "Clear Messages");
        clearItem.setMnemonic('e');

        exitItem = addMenuItem(consoleMenu, "Exit");
        exitItem.setMnemonic('x');
        exitItem.setAccelerator(KeyStroke.getKeyStroke('Q', 2));

        disconnectItem.setEnabled(false);

        connectItem.addActionListener(new ConnectionPopUpListener());
        disconnectItem.addActionListener(new DisConnectionListener());

        sendMsgItem.addActionListener(new SendMessagePopUpListener());
        sendMsgItem.setEnabled(false);

        sendMsgStopItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setMsgSenderStopped(true);
                }
            });
        sendMsgStopItem.setEnabled(false);

        rcvMsgItem.addActionListener(new ReceiveMessagePopUpListener());
        rcvMsgItem.setEnabled(false);

        rcvMsgStopItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    stopMsgReceiver();
                }
            });
        rcvMsgStopItem.setEnabled(false);

        clearItem.addActionListener(new ClearMessageListener());

        exitItem.addActionListener(new ExitListener());

        return (mb);
    }

    /**
     * adds menu item to menu
     */
    private JMenuItem addMenuItem(JMenu menu, String itemStr) {
        JMenuItem item = (JMenuItem) menu.add(newJMenuItem(itemStr));

        return item;
    }

    /**
     * returns a new JMenuItem
     */
    private JMenuItem newJMenuItem(String s) {
        JMenuItem jmi = new JMenuItem(s);

        return jmi;
    }

    /**
     * create table Panel  for message received
     */
    private JPanel createTablePanel() {
        JPanel tPanel = new JPanel();
        tPanel.setLayout(new BorderLayout());

        msgTable = new JTable(new MsgTable());
        msgTable.addMouseListener(new TableMouseListener());

        TableColumn column = msgTable.getColumnModel().getColumn(1);
        column.setPreferredWidth(125);
        column = msgTable.getColumnModel().getColumn(2);
        column.setPreferredWidth(85);
        column = msgTable.getColumnModel().getColumn(3);
        column.setPreferredWidth(95);

        column = msgTable.getColumnModel().getColumn(4);
        column.setPreferredWidth(15);
        column = msgTable.getColumnModel().getColumn(5);
        column.setPreferredWidth(15);
        column = msgTable.getColumnModel().getColumn(6);
        column.setPreferredWidth(40);

        msgTableScrollPane = new JScrollPane(msgTable);
        msgTableScrollPane.setAutoscrolls(true);

        MouseMotionListener doScrollRectToVisible = new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    Rectangle r = new Rectangle(e.getX(), e.getY(), 1, 1);
                    ((JScrollBar) e.getSource()).scrollRectToVisible(r);

                    int maxHeight = msgTable.getVisibleRect().height;

                    if (e.getY() > (maxHeight - 30)) {
                        setScrollingON(true);
                    } else {
                        setScrollingON(false);
                    }
                }
            };

        msgTableScrollPane.getVerticalScrollBar().addMouseMotionListener(doScrollRectToVisible);
        msgTableScrollPane.getVerticalScrollBar().setToolTipText("Drag to Bottom to Start auto scroll, to Stop autoscroll Drag else where");

        tPanel.add(new JLabel("Received Message Table", SwingConstants.CENTER),
            BorderLayout.NORTH);
        tPanel.add(msgTableScrollPane, BorderLayout.CENTER);
        tPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));

        return tPanel;
    }

    /**
     * create top Panel  for production related statistics
     */
    private JPanel createTopPanel() {
        msgProductionRate = Double.NaN;
        topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));

        JLabel l = new JLabel("Connection & Message Production",
                SwingConstants.CENTER);
        l.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

        topPanel.add(BorderLayout.NORTH, l);

        JPanel dummyP = new JPanel(new BorderLayout());

        clientIDLabel = new JLabel();
        msgProductionRateLabel = new JLabel();
        hostPortLabel = new JLabel();

        dummyP.add(BorderLayout.NORTH, clientIDLabel);
        dummyP.add(BorderLayout.CENTER, hostPortLabel);
        dummyP.add(BorderLayout.SOUTH, msgProductionRateLabel);

        topPanel.add(BorderLayout.WEST, dummyP);

        setClientID();
        setMsgProductionRate("Not Available");
        setHostPortLabelText("Client Not Connected");

        dummyP = new JPanel(new BorderLayout());

        setSendMsgProgressBar(0, "");

        dummyP.add(BorderLayout.CENTER, sendMsgProgressBar);
        topPanel.add(BorderLayout.SOUTH, dummyP);
        topPanel.setPreferredSize(new Dimension(0, 95));

        return topPanel;
    }

    /**
     * Main
     */
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    UniversalClient uc = new UniversalClient();
                }
            });
    }

    /**
     * shows connection dialog box to connect to a broker
     */
    private void popUpConnDialogBox() {
        if (connDialogB == null) {
            connDialogB = new ConnectionDialogBox(frame);
            connectB = connDialogB.getConnectButton();

            connectB.addActionListener(new ConnectionListener());
            connCancelB = connDialogB.getCancelButton();

            connCancelB.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        connDialogB.setVisible(false);
                    }
                });
        }

        connDialogB.setUserName(userName);
        connDialogB.setPassword(password);
        connDialogB.setClientID(clientID);
        connDialogB.setVisible(true);
    }

    /**
     * shows Send Message dialog box for sending one or more message
     */
    private void popUpSendMessageDialogBox() {
        if (sendMsgDialogB == null) {
            sendMsgDialogB = new SendMessageDialogBox(frame);

            sendMsgB = sendMsgDialogB.getSendButton();
            sendMsgB.addActionListener(new SendMessageListener());

            sendMsgCancelB = sendMsgDialogB.getCancelButton();
            sendMsgCancelB.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        sendMsgDialogB.setVisible(false);
                    }
                });
        }

        sendMsgDialogB.setVisible(true);
    }

    /**
     * shows Receive Message dialog box for receiving one or more message
     */
    private void popUpReceiveMessageDialogBox() {
        if (receiveMsgDialogB == null) {
            receiveMsgDialogB = new ReceiveMessageDialogBox(frame);

            receiveMsgB = receiveMsgDialogB.getReceiveButton();
            receiveMsgB.addActionListener(new ReceiveMessageListener());

            receiveMsgCancelB = receiveMsgDialogB.getCancelButton();
            receiveMsgCancelB.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        receiveMsgDialogB.setVisible(false);
                    }
                });
        }

        receiveMsgDialogB.setVisible(true);
    }

    /**
     * Connect to a broker given a host port, createConnection method
     * Performs the actual connect.
     */
    private void doConnect() {
        if (connected()) {
            return;
        }

        if (createConnection() == false) {
            errorMessage("Unable to create A session.  " +
                "Please verify a broker is running");

            return;
        }

        setUpUIAfterConnection();
        printToStatusArea("Successfully Connected to " + getBrokerAddress());
    }

    /**
     * setup UI and other flags once a connection is either
     * by user or MQ client runtime auto reconnect
     *
     */
    private void setUpUIAfterConnection() {
        setConnected(true);
        connectItem.setEnabled(false);
        disconnectItem.setEnabled(true);
        sendMsgItem.setEnabled(true);
        rcvMsgItem.setEnabled(true);
        connDialogB.setVisible(false);
        setClientID();

        setFooter("Connected to " + getBrokerAddress());
        setHostPortLabelText(getBrokerAddress());
    }

    /**
     * creates the actual connection a broker
     */
    private boolean createConnection() {
        try {

            Properties props = getConnFactoryConfig();
            String addr = props.getProperty(UniversalClient.ADDRESSLIST_PROP);
            if (addr != null && addr.trim().equals("")) {
                props.remove(ADDRESSLIST_PROP);
                addr = null;
            }
            
            String brokerh = null, brokerp = null;
            if (addr == null) {
                brokerh = connDialogB.getHost();
                brokerp = String.valueOf(connDialogB.getPort());
                setBrokerAddress(brokerh+":"+brokerp);
            } else { 
                setBrokerAddress(addr);
            }

            //this is required in case the MQ client runtime is trying to do a auto reconnect
            if (myConn != null) {
                myConn.close();
            }

            myConnFactory = new com.sun.messaging.ConnectionFactory();

            UniversalClientUtility.setConnFactoryProperties(
                (com.sun.messaging.ConnectionFactory) myConnFactory, props);

            if (addr == null) {
                ((com.sun.messaging.ConnectionFactory)myConnFactory).setProperty(
                    com.sun.messaging.ConnectionConfiguration.imqBrokerHostName, brokerh);
                ((com.sun.messaging.ConnectionFactory) myConnFactory).setProperty(
                    com.sun.messaging.ConnectionConfiguration.imqBrokerHostPort, brokerp);
            }
            ((com.sun.messaging.ConnectionFactory) myConnFactory).setProperty(
                com.sun.messaging.ConnectionConfiguration.imqConfiguredClientID,
                String.valueOf(getClientID()));

            myConn = myConnFactory.createConnection(getUserName(), getPassword());

            //construct a MQ event listener, the listener implements com.sun.messaging.jms.notification.EventListener interface.
            com.sun.messaging.jms.notification.EventListener eListener = new ConnectionEventListener();

            //set event listener to the MQ connection.
            ((com.sun.messaging.jms.Connection) myConn).setEventListener(eListener);
            myConn.setExceptionListener(this);
            myConn.start();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage("Caught Exception: " + e);
            showErrorDialog(e, "Connection Error");

            //e.printStackTrace();
            return false;
        }
    }

    /**
     * responsible for creating and sending message when one clicks on the
     * send message button of Send Message dialog box. If a Message Producer does not
     * exist for a given destination creates a new one for sending messages.
     */
    private void doSendMessage() {
        if (!connected()) {
            errorMessage("Unable to send Message.  " +
                "Please verify a broker is running");

            return;
        }

        sendMsgDialogB.setVisible(false);

        try {
            boolean isTransacted = sendMsgDialogB.isTransacted();
            int destType = sendMsgDialogB.getDestinationType();
            String destName = sendMsgDialogB.getDestinationName();
            myProducerSession = getProducerSession(isTransacted);

            myDestination = createDestination(destType, destName,
                    myProducerSession);
            myProducer = getProducer(myDestination, isTransacted);

            if (sendMsgDialogB.getDeliveryMode() == DELIVERY_MODE_NON_PERSISTENT) {
                myProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            }

            //start a message sender thread
            Thread msgSender = new MessageSenderThread();
            msgSender.start();
        } catch (Exception e) {
            errorMessage("Unable to send message:" + e.getMessage());
            showErrorDialog(e, "Create Destination Error");
            doDisconnect();
        }
    }

    /**
     *  get Message Consumer for a given destination topic or queue
     */
    private MessageConsumer getConsumer(Destination dest, int destType,
        boolean isTransacted, Session s, String selector)
        throws JMSException {
        MessageConsumer c = null;

        if (destType == DESTINATION_TYPE_TOPIC_DURABLE) {
            myConn.stop();
            c = s.createDurableSubscriber((Topic) dest, getClientID(),
                    selector, false);
            myConn.start();
        } else {
            c = s.createConsumer(dest, selector);
        }

        return c;
    }

    /**
     *  get Message Producer for a given destination if one exist or create a new Producer
     */
    private MessageProducer getProducer(Destination dest, boolean isTransacted)
        throws JMSException {
        String key = dest.toString() + isTransacted;
        MessageProducer p = (MessageProducer) myProducerMap.get(key);

        if (p == null) {
            p = myProducerSession.createProducer(dest);
            myProducerMap.put(key, p);
        }

        return p;
    }

    /**
     *  get JMS Session for producer depending on whether it is transacted or not
     */
    private Session getProducerSession(boolean isTransacted)
        throws JMSException {
        String key = new String("session" + isTransacted) + "Producer";
        Session s = (Session) mySessionMap.get(key);

        if (s == null) {
            s = myConn.createSession(isTransacted, Session.AUTO_ACKNOWLEDGE);
            mySessionMap.put(key, s);
        }

        return s;
    }

    /**
     *  get JMS Session for consumer depending on whether it is transacted or not
     */
    private Session getConsumerSession(boolean isTransacted, int ackMode)
        throws JMSException {
        String key = new String("session" + isTransacted) + "Consumer" +
            ackMode;
        Session s = (Session) mySessionMap.get(key);

        if (s == null) {
            if (ackMode == AUTO_ACKNOWLEDGE) {
                ackMode = Session.AUTO_ACKNOWLEDGE;
            } else if (ackMode == CLIENT_ACKNOWLEDGE) {
                ackMode = Session.CLIENT_ACKNOWLEDGE;
            } else if (ackMode == DUPS_OK_ACKNOWLEDGE) {
                ackMode = Session.DUPS_OK_ACKNOWLEDGE;
            }

            //note: If a Session is transacted, the acknowledgement mode is ignored.
            s = myConn.createSession(isTransacted, ackMode);
            mySessionMap.put(key, s);
        }

        return s;
    }

    /**
     * sends n # of messages
     * this method is called from a message sender thread
     */
    private void sendMessage() {
        boolean isTransacted = sendMsgDialogB.isTransacted();
        int numOfMsg = sendMsgDialogB.getNumOfMsg();
        int delayBetweenMsg = sendMsgDialogB.getDelayBetweenMsg();

        sendMsgItem.setEnabled(false);
        sendMsgStopItem.setEnabled(true);
        setMsgSenderStopped(false);

        sendMsgProgressBar.setMaximum(numOfMsg);

        String msgStr = null;

        long t1 = System.currentTimeMillis();
        int msgIndex = 0;

        for (; msgIndex < numOfMsg; msgIndex++) {
            Message msg = null;

            if (isMsgSenderStopped()) {
                break;
            }

            try {
                try {
                    msg = createMessage(msgIndex + 1, numOfMsg);

                    myProducer.send(msg);

                    if (isTransacted) {
                        myProducerSession.commit();
                    }
                } catch (TransactionRolledBackException e) {
                    msgIndex--;
                } catch (JMSException e) {
                    if (isTransacted) {
                        myProducerSession.rollback();
                        msgIndex--;

                        continue;
                    }
                }
            } catch (Exception e) {
                showErrorDialog(e, "Send Message Error");
                e.printStackTrace();

                if (isValidConnection()) {
                    sendMsgItem.setEnabled(true);
                }

                sendMsgStopItem.setEnabled(false);
                setMsgSenderStopped(false);
                setSendMsgProgressBar(0, "");

                return;
            }

            try {
                msgStr = "Sending Message " + (msgIndex + 1) + " of " +
                    numOfMsg + "  To  " +
                    UniversalClientUtility.getDestination(msg);
            } catch (JMSException e1) {
                e1.printStackTrace();
            }

            setSendMsgProgressBar(msgIndex, msgStr);

            if (!(delayBetweenMsg == 0)) {
                try {
                    Thread.sleep(delayBetweenMsg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        long t2 = System.currentTimeMillis();

        msgProductionRate = (msgIndex * 1000.00) / (t2 - t1);
        setMsgProductionRate(new Double(msgProductionRate));

        if (isMsgSenderStopped()) {
            setSendMsgProgressBar(msgIndex, "Sending Message Stopped ....");
        } else {
            setSendMsgProgressBar(msgIndex, "Sending Message Completed ....");
        }

        if (connected()) {
            sendMsgItem.setEnabled(true);
        } else {
            setMsgProductionRate(new String("N/A"));
        }

        sendMsgStopItem.setEnabled(false);
        setMsgSenderStopped(false);

        //let the user see the status for 2 sec
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        setSendMsgProgressBar(0, "");
    }

    /**
     * constructs message of given type, size and ttl
     */
    public Message createMessage(int msgNum, int totalNumMsgs)
        throws Exception {
        int ttl = sendMsgDialogB.getMsgTTL();
        int type = sendMsgDialogB.getMsgType();
        int size = sendMsgDialogB.getMsgSize();
        boolean compressed = (sendMsgDialogB.getCompression() == MSG_COMPRESSED);

        Message msg = null;
        byte b = 's';
        byte[] byteArr = new byte[size];
        Arrays.fill(byteArr, b);

        switch (type) {
        case MSG_TYPE_TEXT:
            msg = myProducerSession.createTextMessage();
            ((TextMessage) msg).setText(new String(byteArr));

            break;

        case MSG_TYPE_OBJECT:
            msg = myProducerSession.createObjectMessage();
            ((ObjectMessage) msg).setObject(new String(byteArr));

            break;

        case MSG_TYPE_MAP:
            msg = myProducerSession.createMapMessage();
            ((MapMessage) msg).setString("hello", new String(byteArr));

            break;

        case MSG_TYPE_BYTES:
            msg = myProducerSession.createBytesMessage();
            ((BytesMessage) msg).writeBytes(byteArr);

            break;

        case MSG_TYPE_STREAM:
            msg = myProducerSession.createStreamMessage();
            ((StreamMessage) msg).writeBytes(byteArr);

            break;
        }

        msg.setJMSExpiration(ttl);
        msg.setJMSType("Universal Client");

        msg.setIntProperty("totalNumMsgs", totalNumMsgs);
        msg.setIntProperty("msgNum", msgNum);
        msg.setStringProperty("msgNumStr", String.valueOf(msgNum));
        msg.setStringProperty("msgSource", "I am coming from universal client");

        msg.setBooleanProperty("JMS_SUN_COMPRESS", compressed);

        return (msg);
    }

    /**
     * create destination Topic or Queue for a given session
     */
    private Destination createDestination(int type, String name, Session s)
        throws JMSException {
        Destination dest = null;

        if (type == DESTINATION_TYPE_QUEUE) {
            dest = s.createQueue(name);
        } else {
            dest = s.createTopic(name);
        }

        return dest;
    }

    /**
     * @return Returns the password.
     */
    public String getPassword() {
        return connDialogB.getPassword();
    }

    /**
     * @return Returns connection factory configuration
     */
    public Properties getConnFactoryConfig() {
        return connDialogB.getConfiguration();
    }

    /**
     * @return Returns the userName.
     */
    public String getUserName() {
        return connDialogB.getUserName();
    }

    /**
     * @return Returns the hostName.
     */
    public String getBrokerAddress() {
        //connDialogB.getHost()+":"conDialogB.getPort();
        //or imqAddressList
        return brokerAddress; 
    }

    public void setBrokerAddress(String addr) {
        this.brokerAddress = addr;
    }

    /**
     * @return Returns the clientID.
     */
    public String getClientID() {
        if (connDialogB == null) {
            return clientID;
        } else {
            return connDialogB.getClientID();
        }
    }

    /**
     * creates a consumer and start the message receiver thread
     * for receiving messages for a given destination
     */
    private void doReceiveMessage() {
        if (!connected()) {
            errorMessage("Unable to send Message.  " +
                "Please verify a broker is running");

            return;
        }

        receiveMsgDialogB.setVisible(false);

        try {
            boolean isTransacted = receiveMsgDialogB.isTransacted();
            String destName = receiveMsgDialogB.getDestinationName();
            int destType = receiveMsgDialogB.getDestinationType();
            String selector = receiveMsgDialogB.getSelector();
            int ackMode = receiveMsgDialogB.getAcknowledgeMode();

            myConsumerSession = getConsumerSession(isTransacted, ackMode);

            myDestination = createDestination(destType, destName,
                    myConsumerSession);
            myConsumer = getConsumer(myDestination, destType, isTransacted,
                    myConsumerSession, selector);

            //start message receiver thread
            Thread msgReceiver = new MessageReceiverThread();
            msgReceiver.start();
        } catch (ResourceAllocationException rae) {
            showErrorDialog(rae, "Receive Message Error");
        } catch (Exception e) {
            showErrorDialog(e, "Receive Message Error");
            doDisconnect();
        }
    }

    private boolean isValidConnection() {
        boolean result = false;

        if (myConn == null) {
            return false;
        } else {
            try {
                myConn.start();
                result = true;
            } catch (JMSException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    /**
     * receives message for a consumer, blocks on a receive call, if
     * consumer is closed , the method returns
     * this method is called from a message receiver thread
     */
    private void receiveMessage() {
        int counter = 0;
        boolean isTransacted = receiveMsgDialogB.isTransacted();
        int ackMode = receiveMsgDialogB.getAcknowledgeMode();
        int delayBetweenMsg = receiveMsgDialogB.getDelayBetweenMsg();

        rcvMsgItem.setEnabled(false);
        rcvMsgStopItem.setEnabled(true);

        setFooterInUse(true);
        setFooter("Waiting on a message Receive() call");

        try {
            while (true) {
                Message msg = null;

                try {
                    msg = myConsumer.receive();

                    if (msg == null) {
                        //reset the stop flag for msg receiver
                        setMsgReceiverStopped(false);

                        break;
                    }

                    if (isTransacted) {
                        myConsumerSession.commit();
                    } else {
                        if (ackMode == CLIENT_ACKNOWLEDGE) {
                            msg.acknowledge();
                        }
                    }
                } catch (TransactionRolledBackException ex) {
                    if (isMsgReceiverStopped()) {
                        //reset the stop flag for msg receiver
                        setMsgReceiverStopped(false);

                        break;
                    }

                    if (isValidConnection()) {
                        continue;
                    } else {
                        break;
                    }
                } catch (JMSException ex) {
                    if (isTransacted) {
                        myConsumerSession.rollback();
                    }

                    if (isMsgReceiverStopped()) {
                        //reset the stop flag for msg receiver
                        setMsgReceiverStopped(false);

                        break;
                    }

                    if (isValidConnection()) {
                        continue;
                    } else {
                        break;
                    }
                }

                counter++;

                addMessageToTable(msg);

                try {
                    String msgStr = "Last Received Message # " +
                        msg.getIntProperty("msgNum") + "/" +
                        msg.getIntProperty("totalNumMsgs") + "  From  " +
                        UniversalClientUtility.getDestination(msg);
                    setFooter(msgStr);
                } catch (JMSException e) {
                    e.printStackTrace();
                }

                if (!(delayBetweenMsg == 0)) {
                    try {
                        Thread.sleep(delayBetweenMsg);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            myConsumer.close();
        } catch (Exception e) {
            showErrorDialog(e, "Receive Message Error");
            setFooterInUse(false);

            return;
        }

        rcvMsgItem.setEnabled(true);
        rcvMsgStopItem.setEnabled(false);

        String counterStr = (counter == 0) ? "NONE"
                                           : (new Integer(counter).toString());
        setFooter("Received messages for this transaction: " + counterStr);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        setFooterInUse(false);
    }

    /**
     * shows status of  message table
     * this method is called from status reporting thread
     */
    public void showCurrentStatus() {
        MsgTable mt = (MsgTable) msgTable.getModel();

        int n = mt.getRowCount();

        if (isFooterInUse()) {
            frame.repaint();
        }

        if ((n != 0) && !isFooterInUse()) {
            setFooter("Message Table Current Size " + ": " + String.valueOf(n));
        }
    }

    /**
     * when auto-scrolling is enabled this is used to
     * scroll to last row of message table
     */
    public void scrollToLastRowOfTable() {
        Rectangle rect = UniversalClientUtility.getRowBounds(msgTable,
                msgTable.getRowCount() - 1);
        msgTable.scrollRectToVisible(rect);
    }

    /**
     * add received message on to message table
     */
    public void addMessageToTable(Message msg) {
        MsgTable mt = (MsgTable) msgTable.getModel();
        int n = 0;

        try {
            n = mt.addMessage(msg);

            if (n > MAX_TABLE_SIZE) {
                throw new ArrayIndexOutOfBoundsException();
            }

            if (isScrollingON()) {
                scrollToLastRowOfTable();
            }

            mt.updateUI();
        } catch (Exception e) {
            showErrorDialog("Exceeded MAX Table Capacity: " +
                "All Messages will be deleted", "Message Loading Error");

            mt.clearData();
        }
    }

    /**
     * Disconnects from session. destroyCurrentSession() performs the JMS
     * cleanup.
     */
    private void doDisconnect() {
        if (!connected()) {
            return;
        }

        destroyCurrentSession();

        setConnected(false);

        connectItem.setEnabled(true);
        disconnectItem.setEnabled(false);
        sendMsgItem.setEnabled(false);
        sendMsgStopItem.setEnabled(false);
        rcvMsgItem.setEnabled(false);
        rcvMsgStopItem.setEnabled(false);

        setFooter("Not Connected");
        setClientID();
        setHostPortLabelText("Not Connected");
        setMsgProductionRate("Not Available");
    }

    /**
     * Destroy/close ' Current session'
     * and clean all data structures
     */
    private void destroyCurrentSession() {
        try {
            //close consumer
            if (myConsumer != null) {
                myConsumer.close();
            }

            //close all producers
            Iterator iterator = myProducerMap.values().iterator();

            while (iterator.hasNext()) {
                MessageProducer element = (MessageProducer) iterator.next();
                element.close();
            }

            myProducerMap.clear();

            //close all producer & consumer session
            iterator = mySessionMap.values().iterator();

            while (iterator.hasNext()) {
                Session element = (Session) iterator.next();
                element.close();
            }

            mySessionMap.clear();
            clearMessageList();

            //close connection
            //myConn.close();
            //myConsumer = null;myProducer = null;myProducerSession = null;myConsumerSession = null;myConn = null;
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage("Caught Exception: " + e);
            myProducerMap.clear();
            mySessionMap.clear();
            clearMessageList();

            //e.printStackTrace();
        }
    }

    /**
     * clears all existing messages from message table
     */
    private void clearMessageList() {
        MsgTable mt = (MsgTable) msgTable.getModel();
        mt.clearData();
        setFooter("Message List Cleared");
    }

    /**
     * Display error:display it in the footer panel
     */
    private void errorMessage(String s) {
        setFooter(s);
    }

    /**
    * shows information message in a dialog box for String
    */
    private void showInformationDialog(String s, String reason) {
        JOptionPane.showMessageDialog(frame, s, reason,
            JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * shows error message in a dialog box for String
     */
    private void showErrorDialog(String s, String reason) {
        JOptionPane.showMessageDialog(frame, s, reason,
            JOptionPane.ERROR_MESSAGE);
    }

    /**
     * shows error message in a dialog box for Exception
     */
    private void showErrorDialog(Exception e, String reason) {
        JOptionPane.showMessageDialog(frame, e, reason,
            JOptionPane.ERROR_MESSAGE);
    }

    /**
     * These methods set a flag that indicates whether the application is
     * currently involved in a jms session.
     */
    private void setConnected(boolean b) {
        connected = b;
    }

    /**
     * These methods return a flag that indicates whether the application is
     * currently involved in a jms session.
     */
    private boolean connected() {
        return (connected);
    }

    /**
     * Exit application. Does some cleanup if necessary.
     */
    private void exit() {
        doDisconnect();
        System.exit(0);
    }

    /**
     * create footer panel
     */
    private JPanel createFooterPanel() {
        footerPanel = new JPanel();
        footerPanel.setLayout(new BorderLayout());
        footerLabel = new JLabel("");
        footerPanel.add(BorderLayout.WEST, footerLabel);

        JPanel dummyP = new JPanel();

        JButton details = new JButton("Message Details");
        details.setToolTipText("Show Message Details ");
        details.addActionListener(new DetailsListener());

        footerPanel.setBorder(BorderFactory.createEtchedBorder(
                EtchedBorder.RAISED));

        //can add other items if required
        dummyP.add(details);
        footerPanel.add(BorderLayout.EAST, dummyP);

        return footerPanel;
    }

    /**
     * @return Returns the scrollingON.
     */
    public boolean isScrollingON() {
        return scrollingON;
    }

    /**
     * @param scrollingON The scrollingON to set.
     */
    public void setScrollingON(boolean scrollingON) {
        this.scrollingON = scrollingON;
    }

    /**
     * @return Returns the footerInUse.
     */
    public boolean isFooterInUse() {
        return footerInUse;
    }

    /**
     * @param footerInUse The footerInUse to set.
     */
    public void setFooterInUse(boolean footerInUse) {
        this.footerInUse = footerInUse;
    }

    /**
     * Set text on footer
     */
    private void setFooter(String s) {
        footerLabel.setText(s);
        footerLabel.repaint();
    }

    /**
     * set text Client ID label
     */
    private void setClientID() {
        if (connected()) {
            clientIDLabel.setText("Client ID:  " + getClientID());
        } else {
            clientIDLabel.setText("Client ID:  " + clientID);
        }

        clientIDLabel.paintImmediately(clientIDLabel.getBounds());
    }

    /**
     *  Set text for host:port
     */
    private void setHostPortLabelText(String hostPort) {
        hostPortLabel.setText("Hostname & Port:  " + hostPort);
        hostPortLabel.paintImmediately(hostPortLabel.getBounds());
    }

    /**
     * Set value and string for SendMsgProgressBar
     */
    private void setSendMsgProgressBar(int value, String msgStr) {
        sendMsgProgressBar.setValue(value);
        sendMsgProgressBar.setString(msgStr);
        sendMsgProgressBar.setStringPainted(true);
    }

    /**
     *  Set text for msg production rate
     */
    private void setMsgProductionRate(Object rate) {
        String r = null;

        if (rate instanceof Double) {
            r = rate + " (msg/sec)";
        } else {
            r = rate.toString();
        }

        msgProductionRateLabel.setText("Msg Production Rate:  " + r);

        msgProductionRateLabel.paintImmediately(msgProductionRateLabel.getBounds());
    }

    /**
     * Show the contents of a message in a seperate popup window
     */
    private void showDetails(Message msg, int msgno) {
        try {
            msgno = msg.getIntProperty("msgNum");
        } catch (JMSException e) {
            e.printStackTrace();
        }

        if (detailsFrame == null) {
            // Create popup
            detailsFrame = new JFrame();
            detailsFrame.setTitle(title + " - Message Details");
            detailsFrame.setBackground(Color.white);
            detailsFrame.getContentPane().setLayout(new BorderLayout());

            msgDetailsHeaderPanel = new PropertyPanel();
            msgDetailsHeaderPanel.setTitle("JMS Headers");
            detailsFrame.getContentPane().add(BorderLayout.NORTH,
                msgDetailsHeaderPanel);

            msgDetailsPropertyPanel = new PropertyPanel();
            msgDetailsPropertyPanel.setTitle("Message Properties");
            detailsFrame.getContentPane().add(BorderLayout.CENTER,
                msgDetailsPropertyPanel);

            msgDetailsBodyPanel = new PropertyPanel();
            msgDetailsBodyPanel.setTitle("Message body");
            detailsFrame.getContentPane().add(BorderLayout.SOUTH,
                msgDetailsBodyPanel);
            detailsFrame.pack();
        }

        // Load JMS headers from message
        try {
            HashMap hdrs = UniversalClientUtility.jmsHeadersToHashMap(msg);
            msgDetailsHeaderPanel.setTitle("JMS Headers: Message #" + msgno);
            msgDetailsHeaderPanel.load(hdrs);
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

        msgDetailsPropertyPanel.load(props);

        // Load message body
        msgDetailsBodyPanel.setTitle("Message Body: (" +
            UniversalClientUtility.messageType(msg) + ")");
        msgDetailsBodyPanel.load(UniversalClientUtility.jmsMsgBodyAsString(msg));

        detailsFrame.setVisible(true);
    }

    /**
     * handles notification events related to connection
     * which are emitted by MQ client run time
     */
    public void onException(JMSException ex) {
        // handle ConnectionClosedEvent or ConectionClosingEvent or ConnectionExitEvent
        setMsgSenderStopped(true);

        // note the message receiver thread exits when the connection is closed/exit
        // so no need to close the message consumer
        doDisconnect();
        showErrorDialog(ex.toString(), "Connection Closing Event");
    }

    /**
     * @return Returns the msgSenderStopped.
     */
    public boolean isMsgSenderStopped() {
        return msgSenderStopped;
    }

    /**
     * @param msgSenderStopped The msgSenderStopped to set.
     */
    public void setMsgSenderStopped(boolean msgSenderStopped) {
        this.msgSenderStopped = msgSenderStopped;
    }

    /**
     * stop the message consmer
     */
    public void stopMsgReceiver() {
        if (myConsumer != null) {
            try {
                setMsgReceiverStopped(true);
                myConsumer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * appends to status area
     */
    public void printToStatusArea(String message) {
        statusArea.appendText("[" + new Date() + "]  " + message);
    }

    public boolean isMsgReceiverStopped() {
        return msgReceiverStopped;
    }

    public void setMsgReceiverStopped(boolean msgReceiverStopped) {
        this.msgReceiverStopped = msgReceiverStopped;
    }

    /**
     * the uclient application exits
     */
    class ExitListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            exit();
        }
    }

    /**
     * clears all existing message of the message table
     */
    class ClearMessageListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            clearMessageList();
        }
    }

    /**
     * pops up Connection dialog box
     */
    class ConnectionPopUpListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            popUpConnDialogBox();
        }
    }

    /**
     * pops up Send message dialog box
     */
    class SendMessagePopUpListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            popUpSendMessageDialogBox();
        }
    }

    /**
     * pops up Receive message dialog box
     */
    class ReceiveMessagePopUpListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            popUpReceiveMessageDialogBox();
        }
    }

    /**
     * connects to broker when the "Connect" button of
     * connection dialog box is clicked
     */
    class ConnectionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            doConnect();
        }
    }

    /**
     * Handles the notification events emitted by MQ client runtime
     * This events could be due to BROKER shutdown, restart, error, crash
     * or MQ client runtime reconnect succesful or reconnect failed
     */
    class ConnectionEventListener
        implements com.sun.messaging.jms.notification.EventListener {
        public void onEvent(com.sun.messaging.jms.notification.Event connEvent) {
            log(connEvent);

            if (connEvent instanceof ConnectionReconnectedEvent) {
                String brokerAddr = ((ConnectionReconnectedEvent) connEvent).getBrokerAddress();

                // Broker address is in the format IP_address:port(actual_port);
                // we just want the IP_address:port part of the address.
                int index = brokerAddr.indexOf('(');

                if (index > 0) {
                    brokerAddr = brokerAddr.substring(0, index);
                }

                setHostPortLabelText(brokerAddr);
            }
        }

        private void log(com.sun.messaging.jms.notification.Event connEvent) {
            //String eventCode = connEvent.getEventCode();
            String eventMessage = connEvent.getEventMessage();
            printToStatusArea(eventMessage);
        }
    }

    /**
     * dis connects client connection when user clicks on
     * "DisConnect" Menu Item
     */
    class DisConnectionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            doDisconnect();

            try {
                //close connection only when the user wants to close
                myConn.close();
            } catch (JMSException e1) {
                e1.printStackTrace();
            }

            printToStatusArea("Successfully Disconnected from " +
                getBrokerAddress());
        }
    }

    /**
     * starts sending message once the "send" button of
     * send msg dialog box is clicked
     */
    class SendMessageListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            doSendMessage();
        }
    }

    /**
     * starts receiving message once "receive" button of
     * receive msg dialog box is clicked
     */
    class ReceiveMessageListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            doReceiveMessage();
        }
    }

    /**
     * shows message details when message table row is double clicked
     */
    class TableMouseListener extends MouseAdapter {
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                int row = msgTable.getSelectedRow();
                MsgTable mt = (MsgTable) msgTable.getModel();
                Message msg = mt.getMessageAtRow(row);
                showDetails(msg, row);
            }
        }
    }

    /**
     * shows message details when message details button is clicked
     */
    class DetailsListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            int row = msgTable.getSelectedRow();

            if (row < 0) {
                setFooter("Please select a message");

                return;
            }

            MsgTable mt = (MsgTable) msgTable.getModel();
            Message msg = mt.getMessageAtRow(row);
            showDetails(msg, row);
        }
    }

    /**
     *  this thread is responsible for sending messages
     */
    class MessageSenderThread extends Thread {
        public void run() {
            sendMessage();
        }
    }

    /**
     *  this thread is responsible for receiving messages
     */
    class MessageReceiverThread extends Thread {
        public void run() {
            receiveMessage();
        }
    }

    /**
     * StatusReporting thread is responsible for showing the message table status
     * also refreshes the main frame when the client receives messages
     */
    class StatusReportingThread extends Thread {
        public void run() {
            while (true) {
                if (connected()) {
                    showCurrentStatus();
                }

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}


/**
 * Dialog for connecting to broker, user name and password.
 *
 */
class ConnectionDialogBox extends JDialog {
    private static final long serialVersionUID = 3544395815952070704L;
    private JTextField userNameF;
    private JPasswordField passwordF;
    private JButton connectB;
    private JButton cancelB;
    private JComboBox hostPortComboBox;
    private JTextField clientIDF;
    private String host;
    private int port;
    private JTextArea cfTextArea;

    //
    JPanel p = new JPanel(new BorderLayout());
    JPanel dummyPanel = new JPanel();
    JPanel valuePanel = new JPanel();
    GridBagLayout valueGbag = new GridBagLayout();
    GridBagConstraints valueConstraints = new GridBagConstraints();

    /**
     * @param f
     */
    public ConnectionDialogBox(JFrame f) {
        super(f, "Universal Client: Connection information", true);
        init();
        setResizable(false);
        setLocationRelativeTo(f);
    }

    /**
     * Init GUI elements.
     */
    private void init() {
        int y = 0;

        valuePanel.setLayout(valueGbag);
        hostPortComboBox = new JComboBox();

        Dimension d = hostPortComboBox.getPreferredSize();
        d.setSize(8 * d.getWidth(), d.getHeight());
        hostPortComboBox.setPreferredSize(d);
        hostPortComboBox.setEditable(true);
        addItemToComboBox("localhost:7676", hostPortComboBox);
        addLabelAndValueComponent("Host:Port: ", hostPortComboBox, y++);

        userNameF = new JTextField(24);
        addLabelAndValueComponent("User Name: ", userNameF, y++);

        passwordF = new JPasswordField(24);
        addLabelAndValueComponent("Password: ", passwordF, y++);

        clientIDF = new JTextField(24);
        addLabelAndValueComponent("Client ID: ", clientIDF, y++);

        dummyPanel.add("Center", valuePanel);
        p.add("North", dummyPanel);

        //p.setBackground(Color.GRAY);
        p.setBorder(BorderFactory.createEtchedBorder());

        dummyPanel = new JPanel();
        connectB = new JButton("Connect");
        cancelB = new JButton("Cancel");
        dummyPanel.add(connectB);
        dummyPanel.add(cancelB);

        p.add("South", dummyPanel);

        cfTextArea = new JTextArea();
        cfTextArea.setFont(new Font("DialogInput", Font.BOLD, 11));

        //cfTextArea.setLineWrap(true);
        cfTextArea.setWrapStyleWord(true);
        cfTextArea.setEditable(true);

        JScrollPane areaScrollPane = new JScrollPane(cfTextArea);
        areaScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        areaScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        areaScrollPane.setPreferredSize(new Dimension(350, 150));

        String connFactoryAttributes = "" +
            "# Edit/Change default attribute values if required \n\n" +
            "# Connection Handling \n" + UniversalClient.ADDRESSLIST_PROP+"=\n" +
            "imqAddressListBehavior=PRIORITY \n" +
            "imqAddressListIterations=1 \n" + "imqReconnectEnabled = false \n" +
            "imqReconnectAttempts = 0    \n" + "imqReconnectInterval=3000 \n" +
            " \n" + "# Connection handling attribute \n" +
            "imqConnectionType=TCP \n" + "imqSSLIsHostTrusted=true \n" +
            "imqConnectionURL=http://localhost/imq/tunnel \n" +
            "imqBrokerServicePort=0 \n" + " \n" + "# Client Identification \n" +
            "imqDefaultUsername=guest \n" + "imqDefaultPassword=guest \n" +
            "imqDisableSetClientID=false \n" + " \n" +
            "# Message Header Overrides  \n" +
            "imqOverrideJMSDeliveryMode=false \n" + "imqJMSDeliveryMode=2 \n" +
            "imqOverrideJMSExpiration=false \n" + "imqJMSExpiration=0 \n" +
            "imqOverrideJMSPriority=false \n" + "imqJMSPriority=4 \n" +
            "imqOverrideJMSHeadersToTemporaryDestinations=false \n" + " \n" +
            "# Reliability and Flow Control \n" + "imqAckTimeout=0 \n" +
            "imqAckOnProduce= \n" + "imqAckOnAcknowledge= \n" +
            "imqConnectionFlowCount=100 \n" +
            "imqConnectionFlowLimitEnabled=false \n" +
            "imqConnectionFlowLimit=1000 \n" + "imqConsumerFlowLimit=100 \n" +
            "imqConsumerFlowThreshold=50 \n" + " \n" +
            "# Queue Browser Behavior  \n" +
            "imqQueueBrowserMaxMessagesPerRetrieve=1000 \n" +
            "imqQueueBrowserRetrieveTimeout=60000 \n" +
            "imqLoadMaxToServerSession=true \n" + " \n" +
            "# JMS-defined Properties Support \n" +
            "imqSetJMSXUserID=false \n" + "imqSetJMSXAppID=false \n" +
            "imqSetJMSXProducerTXID=false \n" +
            "imqSetJMSXConsumerTXID=false \n" +
            "imqSetJMSXRcvTimestamp=false \n" + " \n" + "";

        cfTextArea.setText(connFactoryAttributes);
        cfTextArea.setCaretPosition(0);

        dummyPanel = new JPanel(new BorderLayout());
        dummyPanel.add("North", new JLabel("Connection Factory Attributes"));
        dummyPanel.add("South", areaScrollPane);
        dummyPanel.setBorder(BorderFactory.createEtchedBorder());
        p.add("Center", dummyPanel);

        getContentPane().add(p);
        pack();
    }

    private void addLabelAndValueComponent(String labelStr, Component value,
        int yAxis) {
        JLabel label = new JLabel(labelStr, Label.RIGHT);

        valueConstraints.gridx = 0;
        valueConstraints.gridy = yAxis;
        valueConstraints.weightx = 1.0;
        valueConstraints.weighty = 1.0;
        valueConstraints.anchor = GridBagConstraints.WEST;
        valueGbag.setConstraints(label, valueConstraints);
        valuePanel.add(label);

        valueConstraints.gridx = 1;
        valueConstraints.gridy = yAxis;
        valueConstraints.weightx = 1.0;
        valueConstraints.weighty = 1.0;
        valueConstraints.anchor = GridBagConstraints.WEST;
        valueGbag.setConstraints(value, valueConstraints);
        valuePanel.add(value);
    }

    /**
     * Add a name to the "Queue Name" combo box menu
     */
    private void addItemToComboBox(String name, JComboBox comb) {
        DefaultComboBoxModel model = (DefaultComboBoxModel)comb.getModel();

        if (model.getIndexOf(name) < 0) {
            // Name is not in menu. Add it.
            model.addElement(name);
        }
    }

    /**
     * Return 'Connect' button
     */
    public JButton getConnectButton() {
        return (connectB);
    }

    /**
     * Return 'Cancel' button
     */
    public JButton getCancelButton() {
        return (cancelB);
    }

    /**
     * Return user name entered.
     */
    public String getUserName() {
        if (userNameF == null) {
            return (null);
        }

        return (userNameF.getText());
    }

    /**
     * Set chat user name.
     */
    public void setUserName(String s) {
        if (userNameF == null) {
            return;
        }

        userNameF.setText(s);
    }

    /**
     * Return user password entered.
     */
    public String getPassword() {
        if (passwordF == null) {
            return (null);
        }

        return (new String(passwordF.getPassword()));
    }

    /**
     * Set password.
     */
    public void setPassword(String s) {
        if (passwordF == null) {
            return;
        }

        passwordF.setText(s);
    }

    /**
     * @return Returns the host.
     */
    public String getHost() {
        String hostPort = (String) hostPortComboBox.getEditor().getItem();
        host = hostPort.substring(0, hostPort.indexOf(':')).trim();

        return host;
    }

    /**
     * @param host The host to set.
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return Returns the port.
     */
    public int getPort() {
        String hostPort = (String) hostPortComboBox.getEditor().getItem();
        String portStr = hostPort.substring(hostPort.indexOf(':') + 1);
        port = new Integer(portStr.trim()).intValue();

        return port;
    }

    /**
     * @param port The port to set.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return Returns the clientID.
     */
    public String getClientID() {
        if (clientIDF != null) {
            return clientIDF.getText();
        }

        return null;
    }

    /**
     * @param clientID The clientID to set.
     */
    public void setClientID(String clientID) {
        if (clientIDF != null) {
            clientIDF.setText(clientID);
        }
    }

    /**
     * get configuration for connection factory
     */
    public Properties getConfiguration() {
        Properties props = new Properties();

        try {
            props.load(new ByteArrayInputStream(cfTextArea.getText().getBytes()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return props;
    }
}


/**
 * Dialog for sending one or more message, for a given destination Topic or Queue
 */
class SendMessageDialogBox extends JDialog implements UniversalClientConstants {
    private static final long serialVersionUID = 3258132444711302966L;
    private JTextField destinationNameF = new JTextField("defaultDest", 15);
    private JComboBox destinationType = new JComboBox();
    private JComboBox msgType = new JComboBox();
    private IntegerField msgSize = new IntegerField(10, 15);
    private IntegerField msgTTL = new IntegerField(10, 15);
    private JComboBox transactionType = new JComboBox();
    private JComboBox deliveryMode = new JComboBox();
    private JComboBox compression = new JComboBox();
    private IntegerField numOfMsg = new IntegerField(300, 15);
    private IntegerField delayBetweenMsg = new IntegerField(1000, 15);

    // buttons
    private JButton sendButton = new JButton("Send Message");
    private JButton cancelButton = new JButton("Cancel");

    //
    JPanel p = new JPanel(new BorderLayout());
    JPanel dummyPanel = new JPanel();
    JPanel valuePanel = new JPanel();
    GridBagLayout valueGbag = new GridBagLayout();
    GridBagConstraints valueConstraints = new GridBagConstraints();

    /**
     * @param owner
     */
    public SendMessageDialogBox(Frame owner) {
        super(owner, "Universal Client: Send Message", true);
        init();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void init() {
        int y = 0;

        valuePanel.setLayout(valueGbag);

        addLabelAndValueComponent("Destination Name", destinationNameF, y++);

        destinationType.insertItemAt("Queue         ", DESTINATION_TYPE_QUEUE);
        destinationType.insertItemAt("Topic", DESTINATION_TYPE_TOPIC);
        destinationType.setSelectedIndex(DESTINATION_TYPE_QUEUE);
        addLabelAndValueComponent("Destination Type", destinationType, y++);

        addLabelAndValueComponent("Message Size (bytes)", msgSize, y++);
        addLabelAndValueComponent("Message TTL (sec)", msgTTL, y++);

        deliveryMode.insertItemAt("PERSISTENT", DELIVERY_MODE_PERSISTENT);
        deliveryMode.insertItemAt("NON PERSISTENT", DELIVERY_MODE_NON_PERSISTENT);
        deliveryMode.setSelectedIndex(DELIVERY_MODE_PERSISTENT);
        addLabelAndValueComponent("Delivery Mode", deliveryMode, y++);

        transactionType.insertItemAt("Session Transacted", SESSION_TRANSACTED);
        transactionType.insertItemAt("Session Non Transacted",
            SESSION_NON_TRANSACTED);
        transactionType.setSelectedIndex(SESSION_TRANSACTED);
        addLabelAndValueComponent("Transaction Type", transactionType, y++);

        compression.insertItemAt("Compressed", MSG_COMPRESSED);
        compression.insertItemAt("Non Compressed", MSG_NON_COMPRESSED);
        compression.setSelectedIndex(MSG_NON_COMPRESSED);
        addLabelAndValueComponent("Compression Type", compression, y++);

        msgType.insertItemAt("ObjectMessage", MSG_TYPE_OBJECT);
        msgType.insertItemAt("TextMessage", MSG_TYPE_TEXT);
        msgType.insertItemAt("MapMessage", MSG_TYPE_MAP);
        msgType.insertItemAt("BytesMessage", MSG_TYPE_BYTES);
        msgType.insertItemAt("StreamMessage", MSG_TYPE_STREAM);
        msgType.setSelectedIndex(MSG_TYPE_TEXT);
        addLabelAndValueComponent("Message Type:", msgType, y++);

        addLabelAndValueComponent("Number of Message", numOfMsg, y++);

        addLabelAndValueComponent("Delay Between Msg(ms)", delayBetweenMsg, y++);

        //use a dummy panel
        dummyPanel.add("Center", valuePanel);
        p.add("North", dummyPanel);

        //p.setBackground(Color.GRAY);
        p.setBorder(BorderFactory.createEtchedBorder());

        dummyPanel = new JPanel();
        dummyPanel.add(sendButton);
        dummyPanel.add(cancelButton);

        p.add("South", dummyPanel);

        getContentPane().add(p);
        pack();
    }

    private void addLabelAndValueComponent(String labelStr, Component value,
        int yAxis) {
        JLabel label = new JLabel(labelStr, Label.RIGHT);

        valueConstraints.gridx = 0;
        valueConstraints.gridy = yAxis;
        valueConstraints.weightx = 1.0;
        valueConstraints.weighty = 1.0;
        valueConstraints.anchor = GridBagConstraints.WEST;
        valueGbag.setConstraints(label, valueConstraints);
        valuePanel.add(label);

        valueConstraints.gridx = 1;
        valueConstraints.gridy = yAxis;
        valueConstraints.weightx = 1.0;
        valueConstraints.weighty = 1.0;
        valueConstraints.anchor = GridBagConstraints.WEST;
        valueGbag.setConstraints(value, valueConstraints);
        valuePanel.add(value);
    }

    /**
     * @return Returns the cancelButton.
     */
    public JButton getCancelButton() {
        return cancelButton;
    }

    /**
     * @return Returns the compression.
     */
    public int getCompression() {
        return compression.getSelectedIndex();
    }

    /**
     * @return Returns the deliveryMode.
     */
    public int getDeliveryMode() {
        return deliveryMode.getSelectedIndex();
    }

    /**
     * @return Returns the destinationName.
     */
    public String getDestinationName() {
        return destinationNameF.getText();
    }

    /**
     * @return Returns the destinationType.
     */
    public int getDestinationType() {
        return destinationType.getSelectedIndex();
    }

    /**
     * @return Returns the msgSize.
     */
    public int getMsgSize() {
        return (int) msgSize.getValue();
    }

    /**
     * @return Returns the msgTTL.
     */
    public int getMsgTTL() {
        return (int) msgTTL.getValue();
    }

    /**
     * @return Returns the msgType.
     */
    public int getMsgType() {
        return msgType.getSelectedIndex();
    }

    /**
     * @return Returns the numOfMsg.
     */
    public int getNumOfMsg() {
        return (int) numOfMsg.getValue();
    }

    /**
     * @return Returns the sendButton.
     */
    public JButton getSendButton() {
        return sendButton;
    }

    /**
     * @return Returns the transactionType.
     */
    public boolean isTransacted() {
        return transactionType.getSelectedIndex() == 0;
    }

    /**
     * @return Returns the delayBetweenMsg.
     */
    public int getDelayBetweenMsg() {
        return (int) delayBetweenMsg.getValue();
    }
}


/**
 * Dialog for receiving message for given destination topic or Queue
 */
class ReceiveMessageDialogBox extends JDialog
    implements UniversalClientConstants {
    private static final long serialVersionUID = 3689630285423652918L;
    private JTextField destinationNameF = new JTextField("defaultDest", 15);
    private JComboBox msgConsumerType = new JComboBox();
    private JComboBox acknowledgeMode = new JComboBox();
    private JTextField selectorF = new JTextField("", 15);
    private IntegerField delayBetweenMsg = new IntegerField(2000, 15);

    // buttons
    private JButton receiveButton = new JButton("Receive Message");
    private JButton cancelButton = new JButton("Cancel");

    //
    JPanel p = new JPanel(new BorderLayout());
    JPanel dummyPanel = new JPanel();
    JPanel valuePanel = new JPanel();
    GridBagLayout valueGbag = new GridBagLayout();
    GridBagConstraints valueConstraints = new GridBagConstraints();

    /**
     * @param owner
     */
    public ReceiveMessageDialogBox(Frame owner) {
        super(owner, "Universal Client: Receive Message", true);
        init();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /**
     * init GUI
     */
    private void init() {
        int y = 0;

        valuePanel.setLayout(valueGbag);

        addLabelAndValueComponent("Destination Name", destinationNameF, y++);

        msgConsumerType.insertItemAt("Queue         ", DESTINATION_TYPE_QUEUE);
        msgConsumerType.insertItemAt("Topic", DESTINATION_TYPE_TOPIC);
        msgConsumerType.insertItemAt("Topic Durable",
            DESTINATION_TYPE_TOPIC_DURABLE);
        msgConsumerType.setSelectedIndex(DESTINATION_TYPE_QUEUE);
        addLabelAndValueComponent("Msg Consumer Type", msgConsumerType, y++);

        acknowledgeMode.insertItemAt("AUTO_ACKNOWLEDGE", AUTO_ACKNOWLEDGE);
        acknowledgeMode.insertItemAt("CLIENT_ACKNOWLEDGE", CLIENT_ACKNOWLEDGE);
        acknowledgeMode.insertItemAt("DUPS_OK_ACKNOWLEDGE", DUPS_OK_ACKNOWLEDGE);
        acknowledgeMode.insertItemAt("SESSION TRANSACTED",
            SESSION_TRANSACTED_MODE);
        acknowledgeMode.setSelectedIndex(SESSION_TRANSACTED_MODE);

        addLabelAndValueComponent("Acknowledge Mode", acknowledgeMode, y++);

        addLabelAndValueComponent("Message Selector", selectorF, y++);

        addLabelAndValueComponent("Delay Between Msg(ms)", delayBetweenMsg, y++);

        //use a dummy panel
        dummyPanel.add("Center", valuePanel);
        p.add("North", dummyPanel);

        //p.setBackground(Color.GRAY);
        p.setBorder(BorderFactory.createEtchedBorder());

        dummyPanel = new JPanel();
        dummyPanel.add(receiveButton);
        dummyPanel.add(cancelButton);

        p.add("Center", dummyPanel);

        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font("DialogInput", Font.BOLD, 11));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);

        JScrollPane areaScrollPane = new JScrollPane(textArea);
        areaScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        areaScrollPane.setPreferredSize(new Dimension(350, 150));

        String noteStr =
            "Note: The first receive message for a Durable Topic will create " +
            "a durable subscriber for that topic, One can keep receiving " +
            "message using the durable subsrcripton, however given the same " +
            "destination if the message selector is changed new durable " +
            "subcription is created and the previous subscription is deleted \n \n" +
            "Only one session at a time can have a TopicSubscriber for a particular durable " +
            "subscription. An inactive durable subscriber is one that exists but does not " +
            "currently have a message consumer associated with it. \n\n" +
            "A client can change an existing durable subscription by creating a durable" +
            "TopicSubscriber with the same name and a new topic and/or message selector. " +
            "Changing a durable subscriber is equivalent to unsubscribing (deleting) the old " +
            "one and creating a new one. ";
        textArea.setText(noteStr);
        textArea.setCaretPosition(0);

        p.add("South", areaScrollPane);

        getContentPane().add(p);
        pack();
    }

    private void addLabelAndValueComponent(String labelStr, Component value,
        int yAxis) {
        JLabel label = new JLabel(labelStr, Label.RIGHT);

        valueConstraints.gridx = 0;
        valueConstraints.gridy = yAxis;
        valueConstraints.weightx = 1.0;
        valueConstraints.weighty = 1.0;
        valueConstraints.anchor = GridBagConstraints.WEST;
        valueGbag.setConstraints(label, valueConstraints);
        valuePanel.add(label);

        valueConstraints.gridx = 1;
        valueConstraints.gridy = yAxis;
        valueConstraints.weightx = 1.0;
        valueConstraints.weighty = 1.0;
        valueConstraints.anchor = GridBagConstraints.WEST;
        valueGbag.setConstraints(value, valueConstraints);
        valuePanel.add(value);
    }

    /**
     * @return Returns the cancelButton.
     */
    public JButton getCancelButton() {
        return cancelButton;
    }

    /**
     * @return Returns the destinationName.
     */
    public String getDestinationName() {
        return destinationNameF.getText();
    }

    /**
     * @return Returns the destinationType.
     */
    public int getDestinationType() {
        return msgConsumerType.getSelectedIndex();
    }

    /**
     * @return Returns the receiveButton.
     */
    public JButton getReceiveButton() {
        return receiveButton;
    }

    /**
     * @return Returns the transactionType.
     */
    public boolean isTransacted() {
        return acknowledgeMode.getSelectedIndex() == SESSION_TRANSACTED_MODE;
    }

    /**
     * @return Returns the selector.
     */
    public String getSelector() {
        return selectorF.getText();
    }

    /**
     * @return Returns the acknowledgeMode.
     */
    public int getAcknowledgeMode() {
        return acknowledgeMode.getSelectedIndex();
    }

    /**
     * @return Returns the delayBetweenMsg.
     */
    public int getDelayBetweenMsg() {
        return (int) delayBetweenMsg.getValue();
    }
}


/**
 * UniversalClient Constants
 */
interface UniversalClientConstants {
    int MSG_TYPE_OBJECT = 0;
    int MSG_TYPE_TEXT = 1;
    int MSG_TYPE_MAP = 2;
    int MSG_TYPE_BYTES = 3;
    int MSG_TYPE_STREAM = 4;

    //dest type
    int DESTINATION_TYPE_QUEUE = 0;
    int DESTINATION_TYPE_TOPIC = 1;
    int DESTINATION_TYPE_TOPIC_DURABLE = 2;

    //msg delivery mode
    int DELIVERY_MODE_PERSISTENT = 0;
    int DELIVERY_MODE_NON_PERSISTENT = 1;

    //msg compression
    int MSG_COMPRESSED = 0;
    int MSG_NON_COMPRESSED = 1;

    //session transacted
    int SESSION_TRANSACTED = 0;
    int SESSION_NON_TRANSACTED = 1;

    //acknowledgeMode
    int AUTO_ACKNOWLEDGE = 0;
    int CLIENT_ACKNOWLEDGE = 1;
    int DUPS_OK_ACKNOWLEDGE = 2;
    int SESSION_TRANSACTED_MODE = 3;

    //
    int MAX_TABLE_SIZE = 30000;
    String title = "Oracle GlassFish(tm) Server Message Queue Universal Client";
}


class IntegerField extends JTextField {
    private static final long serialVersionUID = 4050206357708159280L;
    private NumberFormat format = NumberFormat.getNumberInstance();

    public IntegerField(double value, int columns) {
        super(columns);
        format.setParseIntegerOnly(true);

        Document doc = new FormattedDocument(format);
        setDocument(doc);
        doc.addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) {
                    IntegerField.this.getValue();
                }

                public void removeUpdate(DocumentEvent e) {
                    IntegerField.this.getValue();
                }

                public void changedUpdate(DocumentEvent e) {
                }
            });
        setValue(value);
    }

    public double getValue() {
        double retVal = 0.0;

        try {
            retVal = format.parse(getText()).doubleValue();
        } catch (ParseException e) {
            // This should never happen because insertString allows
            // only properly formatted data to get in the field.
            Toolkit.getDefaultToolkit().beep();

            //System.err.println("getValue: could not parse: " + getText());
        }

        return retVal;
    }

    public void setValue(double value) {
        setText(format.format(value));
    }
}


class FormattedDocument extends PlainDocument {
    private static final long serialVersionUID = 3258134673816433462L;
    private Format format;

    public FormattedDocument(Format f) {
        format = f;
    }

    public Format getFormat() {
        return format;
    }

    public void insertString(int offs, String str, AttributeSet a)
        throws BadLocationException {
        String currentText = getText(0, getLength());
        String beforeOffset = currentText.substring(0, offs);
        String afterOffset = currentText.substring(offs, currentText.length());
        String proposedResult = beforeOffset + str + afterOffset;

        try {
            format.parseObject(proposedResult);
            super.insertString(offs, str, a);
        } catch (ParseException e) {
            Toolkit.getDefaultToolkit().beep();
            System.err.println("insertString: could not parse: " +
                proposedResult);
        }
    }

    public void remove(int offs, int len) throws BadLocationException {
        String currentText = getText(0, getLength());
        String beforeOffset = currentText.substring(0, offs);
        String afterOffset = currentText.substring(len + offs,
                currentText.length());
        String proposedResult = beforeOffset + afterOffset;

        try {
            if (proposedResult.length() != 0) {
                format.parseObject(proposedResult);
            }

            super.remove(offs, len);
        } catch (ParseException e) {
            Toolkit.getDefaultToolkit().beep();
            System.err.println("remove: could not parse: " + proposedResult);
        }
    }
}


/**
 * A table of JMS Messages
 */
class MsgTable extends AbstractTableModel {
    private static final long serialVersionUID = 3689630306999286069L;
    final String[] columnNames = {
            "#", "Timestamp", "Type", "Destination", "Mode", "Priority",
            "Redelivered"
        };
    SimpleDateFormat df = new SimpleDateFormat("dd/MMM/yyyy:kk:mm:ss z");
    LinkedList list = new LinkedList();

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
        if ((list == null) || list.isEmpty()) {
            return null;
        }

        Message m = (Message) list.get(row);

        if (m == null) {
            return "null";
        }

        try {
            switch (column) {
            case 0:

                if (UniversalClientUtility.getMessageNumber(m) != null) {
                    return UniversalClientUtility.getMessageNumber(m);
                } else { // Message number is the same as the row number

                    return new Integer(row);
                }

            case 1:

                // Need to format into date/time
                return df.format(new Date(m.getJMSTimestamp()));

            case 2:
                return (UniversalClientUtility.messageType(m));

            case 3:
                return UniversalClientUtility.getDestination(m);

            case 4:

                // Delivery mode
                int mode = m.getJMSDeliveryMode();

                if (mode == DeliveryMode.PERSISTENT) {
                    return "P";
                } else if (mode == DeliveryMode.NON_PERSISTENT) {
                    return "NP";
                } else {
                    return String.valueOf(mode) + "?";
                }

            case 5:

                // Priority
                return new Integer(m.getJMSPriority());

            case 6:
                return new Boolean(m.getJMSRedelivered());

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
    int load(Enumeration e) {
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

    /**
     * adss message to table
     */
    int addMessage(Message msg) {
        list.add(msg);

        return list.size();
    }

    public void updateUI() {
        fireTableRowsInserted(list.size() - 1, list.size());
    }

    public void clearData() {
        list.clear();
        fireTableDataChanged();
    }

    int load(List l) {
        if (l == null) {
            return 0;
        }

        if (l.size() == list.size()) {
            return list.size();
        }

        list = new LinkedList(l);

        fireTableDataChanged();

        return list.size();
    }

    Message getMessageAtRow(int row) {
        if (list == null) {
            return null;
        }

        return ((Message) list.get(row));
    }
}


/**
 * A panel with a text area that knows how to format and display a HashMap
 * of values.
 */
class PropertyPanel extends JPanel {
    private static final long serialVersionUID = 3257288045550974257L;
    JLabel label = null;
    JTextArea textArea = null;
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
        textArea.setEditable(false);

        areaScrollPane = new JScrollPane(textArea);
        areaScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
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
            entry = (Map.Entry) iter.next();

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
        textArea.setCaretPosition(0);

        areaScrollPane.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
    }

    /**
     * Display text in the text window
     */
    void load(String s) {
        textArea.setText(s);
    }

    /**
     * Pad a string to the specified width, right justified. If the string
     * is longer than the width you get back the original string.
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


class UniversalClientUtility {
    public static String[] pad = { "", "0", "00", "000", "0000" };

    public static void dumpException(Exception e) {
        Exception linked = null;

        if (e instanceof JMSException) {
            linked = ((JMSException) e).getLinkedException();
        }

        if (linked == null) {
            e.printStackTrace();
        } else {
            System.err.println(e.toString());
            linked.printStackTrace();
        }
    }

    public static String getMessageNumber(Message m) throws JMSException {
        if (m.propertyExists("msgNum") && m.propertyExists("totalNumMsgs")) {
            int msgNum = m.getIntProperty("msgNum");
            int totalNumMsgs = m.getIntProperty("totalNumMsgs");

            return msgNum + " of " + totalNumMsgs;
        }

        return null;
    }

    public static String getDestination(Message m) throws JMSException {
        Destination d = m.getJMSDestination();
        String s = null;

        if (d != null) {
            if (d instanceof Queue) {
                s = "Queue: " + ((Queue) d).getQueueName();
            } else {
                s = "Topic: " + ((Topic) d).getTopicName();
            }
        } else {
            s = "";
        }

        return s;
    }

    /**
     * Return a string description of the type of JMS message
     */
    public static String messageType(Message m) {
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
     * Return a string representation of the body of a JMS bytes message. This
     * is basically a hex dump of the body. Note, this only looks at the first
     * 1K of the message body.
     */
    public static String jmsBytesBodyAsString(Message m) {
        byte[] body = new byte[1024];
        int n = 0;

        if (m instanceof BytesMessage) {
            try {
                ((BytesMessage) m).reset();
                n = ((BytesMessage) m).readBytes(body);
            } catch (JMSException ex) {
                return (ex.toString());
            }
        } else if (m instanceof StreamMessage) {
            try {
                ((StreamMessage) m).reset();
                n = ((StreamMessage) m).readBytes(body);
            } catch (JMSException ex) {
                return (ex.toString());
            }
        }

        if (n <= 0) {
            return "<empty body>";
        } else {
            return (toHexDump(body, n) + ((n >= body.length) ? "\n. . ." : ""));
        }
    }

    /**
     * Return a string representation of a JMS message body
     */
    public static String jmsMsgBodyAsString(Message m) {
        if (m instanceof TextMessage) {
            try {
                return ((TextMessage) m).getText();
            } catch (JMSException ex) {
                return ex.toString();
            }
        } else if (m instanceof BytesMessage) {
            return jmsBytesBodyAsString(m);
        } else if (m instanceof MapMessage) {
            MapMessage msg = (MapMessage) m;
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
            ObjectMessage msg = (ObjectMessage) m;
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
     * Takes the JMS header fields of a JMS message and puts them in a HashMap
     */
    public static HashMap jmsHeadersToHashMap(Message m)
        throws JMSException {
        HashMap hdrs = new HashMap();
        String s = null;

        s = m.getJMSCorrelationID();
        hdrs.put("JMSCorrelationID", s);

        s = String.valueOf(m.getJMSDeliveryMode());
        hdrs.put("JMSDeliverMode", s);

        Destination d = m.getJMSDestination();

        if (d != null) {
            if (d instanceof Queue) {
                s = ((Queue) d).getQueueName() + " : Queue";
            } else {
                s = ((Topic) d).getTopicName() + " : Topic";
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
                s = ((Queue) d).getQueueName();
            } else {
                s = ((Topic) d).getTopicName();
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
     * Takes a buffer of bytes and returns a hex dump. Each hex digit represents
     * 4 bits. The hex digits are formatted into groups of 4 (2 bytes, 16 bits).
     * Each line has 8 groups, so each line represents 128 bits.
     */
    public static String toHexDump(byte[] buf, int length) {
        // Buffer must be an even length
        if ((buf.length % 2) != 0) {
            throw new IllegalArgumentException();
        }

        int value;
        StringBuffer sb = new StringBuffer(buf.length * 2);

        /*
         * Assume buf is in network byte order (most significant byte is
         * buf[0]). Convert two byte pairs to a short, then display as a hex
         * string.
         */
        int n = 0;

        while ((n < buf.length) && (n < length)) {
            value = buf[n + 1] & 0xFF; // Lower byte
            value |= ((buf[n] << 8) & 0xFF00); // Upper byte

            String s = Integer.toHexString(value);

            // Left bad with 0's
            sb.append(pad[4 - s.length()]);
            sb.append(s);
            n += 2;

            if ((n % 16) == 0) {
                sb.append("\n");
            } else {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    public static Rectangle getRowBounds(JTable table, int row) {
        checkRow(table, row);

        Rectangle result = table.getCellRect(row, -1, true);
        Insets i = table.getInsets();

        result.x = i.left;
        result.width = table.getWidth() - i.left - i.right;

        return result;
    }

    private static void checkRow(JTable table, int row) {
        if (row < 0) {
            throw new IndexOutOfBoundsException(row + " < 0");
        }

        if (row >= table.getRowCount()) {
            throw new IndexOutOfBoundsException(row + " >= " +
                table.getRowCount());
        }
    }

    /*
     * Set the properties on this object.
     */
    public static void setConnFactoryProperties(
        com.sun.messaging.AdministeredObject obj, Properties objProps)
        throws JMSException {
        /*
         * Set the specified properties on the new object.
         */
        for (Enumeration e = objProps.propertyNames(); e.hasMoreElements();) {
            String propName = (String) e.nextElement();
            String value = objProps.getProperty(propName);

            if (value != null) {
                try {
                    obj.setProperty(propName, value.trim());
                } catch (JMSException je) {
                    throw je;
                }
            }
        }
    }
}


class StatusArea extends JPanel {
    private static final long serialVersionUID = 3618421531575793975L;
    private JTextArea statusTextArea;

    /**
     * Create status bar for uclient console application.
     */
    public StatusArea() {
        super(true);
        setLayout(new BorderLayout());
        statusTextArea = new JTextArea(4, 60);

        //statusTextArea = new JTextArea();
        statusTextArea.setLineWrap(true);
        statusTextArea.setForeground(Color.BLUE);
        statusTextArea.setEditable(false);

        JScrollPane statusTextPane = new JScrollPane(statusTextArea);
        add(statusTextPane, BorderLayout.CENTER);
    }

    /**
     * Append status text to the text area.
     *
     * @param statusText the status text
     */
    public void appendText(String statusText) {
        statusText = statusText + "\n";
        statusTextArea.append(statusText);
        statusTextArea.setCaretPosition(statusTextArea.getText().length());
    }

    /*
     * Clears the text shown in the Status Area.
     */
    public void clearText() {
        statusTextArea.setText("");
    }
}
