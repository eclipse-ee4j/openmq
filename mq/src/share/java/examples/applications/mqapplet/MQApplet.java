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
 * @(#)MQApplet.java	1.4 07/02/07
 */ 

import java.awt.*;
import java.awt.event.*;
import java.applet.*;

import javax.swing.*;
import javax.swing.event.*;

import jakarta.jms.*;

/**
 * This is a simple chat applet that uses JMS APIs. It uses publish
 * subscribe model. It can also be run as a standalone application.
 */
public class MQApplet extends JApplet
    implements ActionListener, ExceptionListener, MessageListener {
    private static boolean DEBUG = Boolean.getBoolean("mqapplet.debug");
    private static String TRANSPORT;
    static {
        TRANSPORT = System.getProperty("mqapplet.transport");
        if (TRANSPORT == null)
            TRANSPORT = "";
    }

    private JPanel mainPanel;
    private StatusPanel statusBar;
    private JTextField urlField;
    private JTextField hostField, portField;
    private JTextField addrField;

    private JTextArea txArea, rxArea;
    JButton connectButton, exitButton, clearButton, sendButton;

    private boolean createExitButton = false;

    public void init() {
        initGUI();
        initJMS();
    }

    public void destroy() {
        shutdownJMS();
        shutdownGUI();
    }

    private void enableExit() {
        createExitButton = true;
    }

    private void initGUI() {
        // The application window contains the 'mainPanel' container
        // and a status bar.
        Container content = getContentPane();

        // Create the mainPanel container. It holds all the UI
        // components...
        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        content.add("Center", mainPanel);

        // Create the status bar..
        statusBar = new StatusPanel();
        content.add("South", statusBar);

        //
        // Now start populating mainPanel...
        //

        // dialogPanel contains JMS configuration and the connect
        // button.
        JPanel dialogPanel = new JPanel();
        dialogPanel.setLayout(
            new BoxLayout(dialogPanel, BoxLayout.Y_AXIS));
        dialogPanel.setBorder(
            createMyBorder("JMS Connection Properties..."));

        JPanel dummyPanel;

        if (TRANSPORT.equalsIgnoreCase("http")) {
            dummyPanel = new JPanel();
            dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.X_AXIS));
            dummyPanel.add(new JLabel("imqConnectionURL : "));
            urlField = new JTextField("http://");
            dummyPanel.add(urlField);
            dialogPanel.add(dummyPanel);
        }
        else if (TRANSPORT.equalsIgnoreCase("tcp")) {
            dummyPanel = new JPanel();
            dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.X_AXIS));
            dummyPanel.add(new JLabel("imqBrokerHostName : "));
            hostField = new JTextField("localhost");
            dummyPanel.add(hostField);
            dialogPanel.add(dummyPanel);

            dummyPanel = new JPanel();
            dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.X_AXIS));
            dummyPanel.add(new JLabel("imqBrokerHostPort : "));
            portField = new JTextField("7676");
            dummyPanel.add(portField);
            dialogPanel.add(dummyPanel);
        }
        else {
            dummyPanel = new JPanel();
            dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.X_AXIS));
            dummyPanel.add(new JLabel("imqAddressList : "));
            addrField = new JTextField("mq://localhost:7676");
            dummyPanel.add(addrField);
            dialogPanel.add(dummyPanel);
        }

        dummyPanel = new JPanel();
        dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.X_AXIS));

        connectButton = new JButton();
        connectButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(3, 0, 3, 3),
            connectButton.getBorder()));
        connectButton.addActionListener(this);
        setConnectButton("Connect");
        dummyPanel.add(connectButton);

        if (createExitButton) {
            exitButton = new JButton("Exit");
            exitButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(3, 3, 3, 3),
                exitButton.getBorder()));
            exitButton.addActionListener(this);
            dummyPanel.add(exitButton);
        }

        dialogPanel.add(dummyPanel);

        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new GridLayout(2, 1));

        dummyPanel = new JPanel();
        dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.Y_AXIS));
        dummyPanel.setBorder(createMyBorder("Received messages "));

        rxArea = new JTextArea();
        rxArea.setEditable(false);
        JScrollPane spane = new JScrollPane(rxArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        dummyPanel.add(spane);

        clearButton = new JButton("Clear");
        clearButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(3, 3, 3, 3),
            clearButton.getBorder()));
        clearButton.addActionListener(this);
        dummyPanel.add(clearButton);
        messagePanel.add(dummyPanel);

        dummyPanel = new JPanel();
        dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.Y_AXIS));
        dummyPanel.setBorder(createMyBorder("Send message "));

        txArea = new JTextArea();
        txArea.setEditable(true);
        spane = new JScrollPane(txArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        dummyPanel.add(spane);

        sendButton = new JButton("Send");
        sendButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(3, 3, 3, 3),
            sendButton.getBorder()));
        sendButton.addActionListener(this);
        dummyPanel.add(sendButton);
        messagePanel.add(dummyPanel);

        mainPanel.add("North", dialogPanel);
        mainPanel.add("Center", messagePanel);
    }

    private void initJMS() {
    }

    private void shutdownGUI() {
        remove(mainPanel);
        mainPanel = null;
    }

    private void shutdownJMS() {
        doDisconnect();
    }

    public void processEvent(AWTEvent e) {
        if (e.getID() == Event.WINDOW_DESTROY) {
            System.exit(0);
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("Connect")) {
            if (TRANSPORT.equalsIgnoreCase("http")) {
                String us = urlField.getText();
                statusBar.setStatusLine("Connecting to " + us + "...");
                initHTTPConnectionFactory(us);
            }
            else if (TRANSPORT.equalsIgnoreCase("tcp")) {
                String h = hostField.getText();
                String p = portField.getText();
                statusBar.setStatusLine("Connecting to " + h + ":" + p + "...");
                initTCPConnectionFactory(h, p);
            }
            else {
                String addr = addrField.getText();
                statusBar.setStatusLine("Connecting to " + addr + "...");
                initConnectionFactory(addr);
            }

            doConnect();
        }

        if (e.getActionCommand().equals("Disconnect")) {
            statusBar.setStatusLine("Disconnecting...");
            doDisconnect();
            statusBar.setStatusLine("Connection closed.");
        }

        if (e.getActionCommand().equals("Send")) {
            String ss = txArea.getText();
            doSend(ss);
            txArea.setText(null);
        }

        if (e.getActionCommand().equals("Clear")) {
            rxArea.setText(null);
        }

        if (e.getActionCommand().equals("Exit")) {
            doDisconnect();
            System.exit(0);
        }
    }

    public void updateRxArea(String s) {
        rxArea.append(s);
    }

    public void enableConnectButton() {
        setConnectButton("Connect");
    }

    public void enableDisconnectButton() {
        setConnectButton("Disconnect");
    }

    ConnectionFactory connectionFactory = null;
    Connection connection = null;
    Session session = null;
    Topic topic = null;
    MessageConsumer msgConsumer = null;
    MessageProducer msgProducer = null;
    TextMessage textMessage = null;


    public void initHTTPConnectionFactory(String s) {
        try {
            if (connectionFactory == null) {
                connectionFactory = (ConnectionFactory)
                    new com.sun.messaging.ConnectionFactory();
            }

            // Provider specific code start.
            com.sun.messaging.ConnectionFactory cf =
                (com.sun.messaging.ConnectionFactory) connectionFactory;
            cf.setProperty(
                com.sun.messaging.ConnectionConfiguration.imqConnectionType,
                "HTTP");
            cf.setProperty(
                com.sun.messaging.ConnectionConfiguration.imqConnectionURL,
                s);

            // Provider specific code end.
        }
        catch (JMSException e) {
            updateRxArea("initHTTPConnectionFactory : " + e.toString() + "\n");
            e.printStackTrace();
            if (e.getLinkedException() != null)
                e.getLinkedException().printStackTrace();
        }
    }

    public void initTCPConnectionFactory(String h, String p) {
        try {
            if (connectionFactory == null) {
                connectionFactory = (ConnectionFactory)
                    new com.sun.messaging.ConnectionFactory();
            }

            // Provider specific code start.
            com.sun.messaging.ConnectionFactory cf =
                (com.sun.messaging.ConnectionFactory) connectionFactory;
	    // Set imqAddressList property.
            ((com.sun.messaging.ConnectionFactory)cf).setProperty(
                com.sun.messaging.ConnectionConfiguration.imqAddressList,
                new StringBuffer().append("mq://").append(h).append(
                    ":").append(p).append("/jms").toString());
            // Provider specific code end.
        }
        catch (JMSException e) {
            updateRxArea("initTCPConnectionFactory : " + e.toString() + "\n");
            e.printStackTrace();
            if (e.getLinkedException() != null)
                e.getLinkedException().printStackTrace();
        }
    }

    public void initConnectionFactory(String a) {
        if (connectionFactory == null) {
            connectionFactory = (ConnectionFactory)
                new com.sun.messaging.ConnectionFactory();
        }

        try {
            // Provider specific code start.
            com.sun.messaging.ConnectionFactory cf =
                (com.sun.messaging.ConnectionFactory) connectionFactory;
            cf.setProperty(
                com.sun.messaging.ConnectionConfiguration.imqAddressList,
                a);
            // Provider specific code end.
        }
        catch (JMSException e) {
            updateRxArea("initConnectionFactory : " + e.toString() + "\n");
            e.printStackTrace();
            if (e.getLinkedException() != null)
                e.getLinkedException().printStackTrace();
        }
    }

    public void doConnect() {
        try {
            connection = connectionFactory.createConnection();
            connection.setExceptionListener(this);
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            topic = session.createTopic("MQChatAppletTopic");
            msgConsumer = session.createConsumer(topic);
            msgConsumer.setMessageListener(this);
            msgProducer = session.createProducer(topic);
            textMessage = session.createTextMessage();

            statusBar.setStatusLine("Connected");
            enableDisconnectButton();
        }
        catch (JMSException e) {
            updateRxArea("doConnect : " + e.toString() + "\n");
            statusBar.setStatusLine("Unable to connect.");
            e.printStackTrace();
            if (e.getLinkedException() != null)
                e.getLinkedException().printStackTrace();
        }
    }

    public void doSend(String s) {
        if (msgProducer == null) {
            statusBar.setStatusLine("Not connected.");
            return;
        }

        try {
            textMessage.setText(s);
            msgProducer.send(textMessage);
        }
        catch (JMSException e) {
            updateRxArea("doSend : " + e.toString() + "\n");
            e.printStackTrace();
        }
    }

    public void doDisconnect() {
        try {
            if (connection != null)
                connection.close();
        }
        catch (Exception e) {}

        connection = null;
        session = null;
        topic = null;
        msgConsumer = null;
        msgProducer = null;
        textMessage = null;

        enableConnectButton();
    }

    public void onException(JMSException e) {
        statusBar.setStatusLine("Connection lost : " + e.toString());
        doDisconnect();
    }

    public void onMessage(Message m) {
        try {
            if (m instanceof TextMessage) {
                String s = ((TextMessage) m).getText();
                updateRxArea(s);
            }
        }
        catch (JMSException e) {
            e.printStackTrace();
            updateRxArea("onMessage : " + e.toString() + "\n");
        }
    }

    private void setConnectButton(String text) {
        connectButton.setText(text);
        connectButton.setActionCommand(text);
        connectButton.invalidate();
        connectButton.validate();
        mainPanel.repaint();
    }

    private javax.swing.border.Border createMyBorder(String title) {
        javax.swing.border.Border inner =
            BorderFactory.createLineBorder(Color.black);

        if (title != null)
            inner = BorderFactory.createTitledBorder(inner, title);

        javax.swing.border.Border outer =
            BorderFactory.createEmptyBorder(3, 3, 3, 3);

        return BorderFactory.createCompoundBorder(outer, inner);
    }

    public void cleanupAndExit() {
        destroy();
        System.exit(0);
    }

    public static MQApplet mq = null;
    public static void mainWindowClosed() {
        mq.cleanupAndExit();
    }

    public static void main(String []args) {
        JFrame f = new JFrame("MQApplet");
        f.setDefaultCloseOperation(f.DO_NOTHING_ON_CLOSE);
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                mainWindowClosed();
            }
        });

        mq = new MQApplet();
        mq.enableExit();

        mq.init();
        mq.start();

        f.getContentPane().add("Center", mq);
        f.setSize(600, 600);
        f.show();
    }

    class StatusPanel extends JPanel {
        private JLabel label = null;

        public StatusPanel() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createLoweredBevelBorder());
            label = new JLabel();

            int size = label.getFont().getSize();
            label.setFont(new Font("Serif", Font.PLAIN, size));
            add("West", label);

            setStatusLine("Ready");
        }

        public void setStatusLine(String statusLine) {
            if (statusLine == null)
                statusLine = "";

            label.setText(statusLine);
            invalidate();
            validate();
            repaint();
        }
    }
}

