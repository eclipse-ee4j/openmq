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
 * @(#)SimpleChat.java	1.13 07/02/07
 */ 

import java.awt.*;
import java.awt.event.*;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.Vector;
import java.util.Enumeration;

import jakarta.jms.*;

/**
 * The SimpleChat example is a basic 'chat' application that uses
 * the JMS APIs. It uses JMS Topics to represent chat rooms or 
 * chat topics.
 *
 * When the application is launched, use the 'Chat' menu to
 * start or connect to a chat session.
 *
 * The command line option '-DimqAddressList='can be used to affect 
 * how the application connects to the message service provided by 
 * the Oracle GlassFish(tm) Server Message Queue software.
 *
 * It should be pointed out that the bulk of the application is
 * AWT - code for the GUI. The code that implements the messages
 * sent/received by the chat application is small in size.
 *
 * The SimpleChat example consists of the following classes, all
 * contained in one file:
 *
 *	SimpleChat		- contains main() entry point, GUI/JMS
 *				  initialization code.
 *	SimpleChatPanel		- GUI for message textareas.
 *	SimpleChatDialog	- GUI for "Connect" popup dialog.
 *	ChatObjMessage	        - chat message class.
 *
 * Description of the ChatObjMessage class and how it is used
 * ==========================================================
 * The ChatObjMessage class is used to broadcast messages in
 * the JMS simplechat example. 
 * The interface SimpleChatMessageTypes (defined in this file)
 * has several message 'types':
 *  
 * From the interface definition:
 *     public static int   JOIN    = 0;
 *     public static int   MSG     = 1;
 *     public static int   LEAVE   = 2;
 *  
 * JOIN    - for applications to announce that they just joined the chat
 * MSG     - for normal text messages
 * LEAVE   - for applications to announce that are leaving the chat
 *  
 * Each ChatObjMessage also has fields to indicate who the sender is
 * - a simple String identifier.
 *  
 * When the chat application enters a chat session, it broadcasts a JOIN
 * message. Everybody currently in the chat session will get this and
 * the chat GUI will recognize the message of type JOIN and will print
 * something like this in the 'Messages in chat:' textarea:
 *  
 *         *** johndoe has joined chat session
 *  
 * Once an application has entered a chat session, messages sent as part of
 * a normal 'chat' are sent as ChatObjMessage's of type MSG. Upon seeing
 * these messages, the chat GUI simply displays the sender and the message
 * text as follows:
 *  
 *         johndoe: Hello World !
 *  
 * When a chat disconnect is done, prior to doing the various JMS cleanup
 * operations, a LEAVE message is sent. The chat GUI sees this and prints
 * something like:
 *  
 *         *** johndoe has left chat session
 *  
 * 
 */
public class SimpleChat implements ActionListener,
				WindowListener,
				MessageListener  {

    ConnectionFactory            connectionFactory;
    Connection                   connection;
    Session                      session;
    MessageProducer              msgProducer;
    MessageConsumer              msgConsumer;
    Topic                        topic;

    boolean                      connected = false;
  
    String                       name, hostName, topicName, outgoingMsgTypeString;
    int                          outgoingMsgType;

    Frame                        frame;
    SimpleChatPanel              scp;
    SimpleChatDialog             scd = null;
    MenuItem                     connectItem, disconnectItem, clearItem, exitItem;
    Button                       sendB, connectB, cancelB;

    SimpleChatMessageCreator     outgoingMsgCreator;

    SimpleChatMessageCreator     txtMsgCreator,	objMsgCreator, mapMsgCreator, bytesMsgCreator, streamMsgCreator;

    /**
     * @param args	Arguments passed via the command line. These are
     *			used to create the ConnectionFactory.
     */
    public static void main(String[] args) {
	SimpleChat	sc = new SimpleChat();

	sc.initGui();
	sc.initJms(args);
    }

    /**
     * SimpleChat constructor.
     * Initializes the chat user name, topic, hostname.
     */
    public SimpleChat()  {
        name = System.getProperty("user.name", "johndoe");
        topicName = "defaulttopic";

	try  {
	    hostName = InetAddress.getLocalHost().getHostName();
	} catch (Exception e)  {
	    hostName = "localhost";
	}
    }

    public SimpleChatMessageCreator getMessageCreator(int type)  {
	switch (type)  {
	case SimpleChatDialog.MSG_TYPE_TEXT:
	    if (txtMsgCreator == null)  {
		txtMsgCreator = new SimpleChatTextMessageCreator();
	    }
	    return (txtMsgCreator);

	case SimpleChatDialog.MSG_TYPE_OBJECT:
	    if (objMsgCreator == null)  {
		objMsgCreator = new SimpleChatObjMessageCreator();
	    }
	    return (objMsgCreator);

	case SimpleChatDialog.MSG_TYPE_MAP:
	    if (mapMsgCreator == null)  {
		mapMsgCreator = new SimpleChatMapMessageCreator();
	    }
	    return (mapMsgCreator);

	case SimpleChatDialog.MSG_TYPE_BYTES:
	    if (bytesMsgCreator == null)  {
		bytesMsgCreator = new SimpleChatBytesMessageCreator();
	    }
	    return (bytesMsgCreator);

	case SimpleChatDialog.MSG_TYPE_STREAM:
	    if (streamMsgCreator == null)  {
		streamMsgCreator = new SimpleChatStreamMessageCreator();
	    }
	    return (streamMsgCreator);
	}

	return (null);
    }

    public SimpleChatMessageCreator getMessageCreator(Message msg)  {
	if (msg instanceof TextMessage)  {
	    if (txtMsgCreator == null)  {
		txtMsgCreator = new SimpleChatTextMessageCreator();
	    }
	    return (txtMsgCreator);
	} else if (msg instanceof ObjectMessage)  {
	    if (objMsgCreator == null)  {
		objMsgCreator = new SimpleChatObjMessageCreator();
	    }
	    return (objMsgCreator);
	} else if (msg instanceof MapMessage)  {
	    if (mapMsgCreator == null)  {
		mapMsgCreator = new SimpleChatMapMessageCreator();
	    }
	    return (mapMsgCreator);
	} else if (msg instanceof BytesMessage)  {
	    if (bytesMsgCreator == null)  {
		bytesMsgCreator = new SimpleChatBytesMessageCreator();
	    }
	    return (bytesMsgCreator);
	} else if (msg instanceof StreamMessage)  {
	    if (streamMsgCreator == null)  {
		streamMsgCreator = new SimpleChatStreamMessageCreator();
	    }
	    return (streamMsgCreator);
	}
	
	return (null);
    }

    /* 
     * BEGIN INTERFACE ActionListener
     */
    /**
     * Detects the various UI actions and performs the
     * relevant action:
     *	Connect menu item (on Chat menu):	Show Connect dialog
     *	Disconnect menu item (on Chat menu):	Disconnect from chat
     *	Connect button (on Connect dialog):	Connect to specified
     *						chat
     *	Cancel button (on Connect dialog):	Hide Connect dialog
     *	Send button:				Send message to chat
     *	Clear menu item (on Chat menu):		Clear chat textarea
     *	Exit menu item (on Chat menu):		Exit application
     *
     * @param ActionEvent UI event
     */
    public void actionPerformed(ActionEvent e)  {
	Object		obj = e.getSource();

	if (obj == connectItem)  {
	    queryForChatNames();
	} else if (obj == disconnectItem)  {
	    doDisconnect();
	} else if (obj == connectB)  {
	    scd.setVisible(false);

	    topicName = scd.getChatTopicName();
	    name = scd.getChatUserName();
	    outgoingMsgTypeString = scd.getMsgTypeString();
	    outgoingMsgType = scd.getMsgType();
	    doConnect();
	} else if (obj == cancelB)  {
	    scd.setVisible(false);
	} else if (obj == sendB)  {
	    sendNormalMessage();
	} else if (obj == clearItem)  {
	    scp.clear();
	} else if (obj == exitItem)  {
	    exit();
	}
    }
    /* 
     * END INTERFACE ActionListener
     */

    /* 
     * BEGIN INTERFACE WindowListener
     */
    public void windowClosing(WindowEvent e)  {
        e.getWindow().dispose();
    }
    public void windowClosed(WindowEvent e)  {
	exit();
    }
    public void windowActivated(WindowEvent e)  { }
    public void windowDeactivated(WindowEvent e)  { }
    public void windowDeiconified(WindowEvent e)  { }
    public void windowIconified(WindowEvent e)  { }
    public void windowOpened(WindowEvent e)  { }
    /*
     * END INTERFACE WindowListener
     */

    /*
     * BEGIN INTERFACE MessageListener
     */
    /**
     * Display chat message on gui.
     *
     * @param msg message received
     */
    public void onMessage(Message msg)  {
	String		sender, msgText;
	int		type;
	SimpleChatMessageCreator	inboundMsgCreator;

	inboundMsgCreator = getMessageCreator(msg);

	if (inboundMsgCreator == null)  {
            errorMessage("Message received is not supported ! ");
	    return;
	}

	/*
	 *  Need to fetch msg values in this order.
	 */
	type = inboundMsgCreator.getChatMessageType(msg);
	sender = inboundMsgCreator.getChatMessageSender(msg);
	msgText = inboundMsgCreator.getChatMessageText(msg);

	if (type == SimpleChatMessageTypes.BADTYPE)  {
            errorMessage("Message received in wrong format ! ");
	    return;
	}

	scp.newMessage(sender, type, msgText);
    }
    /*
     * END INTERFACE MessageListener
     */


    /*
     * Popup the SimpleChatDialog to query the user for the chat user
     * name and chat topic.
     */
    private void queryForChatNames()  {
	if (scd == null)  {
	    scd = new SimpleChatDialog(frame);
	    connectB = scd.getConnectButton();
	    connectB.addActionListener(this);
	    cancelB = scd.getCancelButton();
	    cancelB.addActionListener(this);
	}

	scd.setChatUserName(name);
	scd.setChatTopicName(topicName);
	scd.show();
    }

    /*
     * Performs the actual chat connect.
     * The createChatSession() method does the real work
     * here, creating:
     *		Connection
     *		Session
     *		Topic
     *		MessageConsumer
     *		MessageProducer
     */
    private void doConnect()  {
	if (connectedToChatSession())
	    return;

	outgoingMsgCreator = getMessageCreator(outgoingMsgType);

	if (createChatSession(topicName) == false) {
	    errorMessage("Unable to create Chat session.  " +
			 "Please verify a broker is running");
	    return;
	}
	setConnectedToChatSession(true);

	connectItem.setEnabled(false);
	disconnectItem.setEnabled(true);

	scp.setUserName(name);
	scp.setDestName(topicName);
	scp.setMsgType(outgoingMsgTypeString);
	scp.setHostName(hostName);
	scp.setEnabled(true);
    }

    /*
     * Disconnects from chat session.
     * destroyChatSession() performs the JMS cleanup.
     */
    private void doDisconnect()  {
	if (!connectedToChatSession())
	    return;

	destroyChatSession();

	setConnectedToChatSession(false);

	connectItem.setEnabled(true);
	disconnectItem.setEnabled(false);
	scp.setEnabled(false);
    }

    /*
     * These methods set/return a flag that indicates
     * whether the application is currently involved in
     * a chat session.
     */
    private void setConnectedToChatSession(boolean b)  {
	connected = b;
    }
    private boolean connectedToChatSession()  {
	return (connected);
    }

    /*
     * Exit application. Does some cleanup if
     * necessary.
     */
    private void exit()  {
	doDisconnect();
        System.exit(0);
    }

    /*
     * Create the application GUI.
     */
    private void initGui() {

	frame = new Frame("Simple Chat");

	frame.addWindowListener(this);

	MenuBar	menubar = createMenuBar();

        frame.setMenuBar(menubar);

	scp = new SimpleChatPanel();
	scp.setUserName(name);
	scp.setDestName(topicName);
	scp.setHostName(hostName);
	sendB = scp.getSendButton();
	sendB.addActionListener(this);

	frame.add(scp);
	frame.pack();
	frame.setVisible(true);

	scp.setEnabled(false);
    }

    /*
     * Create menubar for application.
     */
    private MenuBar createMenuBar() {
	MenuBar	mb = new MenuBar();
        Menu chatMenu;

	chatMenu = (Menu) mb.add(new Menu("Chat"));
	connectItem = (MenuItem) chatMenu.add(new MenuItem("Connect ..."));
	disconnectItem = (MenuItem) chatMenu.add(new MenuItem("Disconnect"));
	clearItem = (MenuItem) chatMenu.add(new MenuItem("Clear Messages"));
	exitItem = (MenuItem) chatMenu.add(new MenuItem("Exit"));

	disconnectItem.setEnabled(false);

        connectItem.addActionListener(this);
        disconnectItem.addActionListener(this);
        clearItem.addActionListener(this);
	exitItem.addActionListener(this);

	return (mb);
    }

    /*
     * Send message using text that is currently in the SimpleChatPanel
     * object. The text message is obtained via scp.getMessage()
     *
     * An object of type ChatObjMessage is created containing the typed
     * text. A JMS ObjectMessage is used to encapsulate this ChatObjMessage
     * object.
     */
    private void sendNormalMessage()  {
	Message		msg;

	if (!connectedToChatSession())  {
	    errorMessage("Cannot send message: Not connected to chat session!");

	    return;
	}

	try  {
	    msg = outgoingMsgCreator.createChatMessage(session, 
					name, 
					SimpleChatMessageTypes.NORMAL, 
					scp.getMessage());

            msgProducer.send(msg);
	    scp.setMessage("");
	    scp.requestFocus();
	} catch (Exception ex)  {
	    errorMessage("Caught exception while sending NORMAL message: " + ex);
	}
    }

    /*
     * Send a message to the chat session to inform people
     * we just joined the chat.
     */
    private void sendJoinMessage()  {
	Message		msg;

	try  {
	    msg = outgoingMsgCreator.createChatMessage(session, 
					name, 
					SimpleChatMessageTypes.JOIN, 
					null);

            msgProducer.send(msg);
	} catch (Exception ex)  {
	    errorMessage("Caught exception while sending JOIN message: " + ex);
	}
    }
    /*
     * Send a message to the chat session to inform people
     * we are leaving the chat.
     */
    private void sendLeaveMessage()  {
	Message		msg;

	try  {
	    msg = outgoingMsgCreator.createChatMessage(session, 
					name, 
					SimpleChatMessageTypes.LEAVE, 
					null);

            msgProducer.send(msg);
	} catch (Exception ex)  {
	    errorMessage("Caught exception while sending LEAVE message: " + ex);
	}
    }

    /*
     * JMS initialization.
     * This is simply creating the ConnectionFactory.
     */
    private void initJms(String args[]) {
	/* XXX: chg for JMS1.1 to use BasicConnectionFactory for non-JNDI useage
	 * remove --- Use BasicConnectionFactory directly - no JNDI
	*/
	try  {
            connectionFactory
		= new com.sun.messaging.ConnectionFactory();
	} catch (Exception e)  {
	    errorMessage("Caught Exception: " + e);
	}
    }

    /*
     * Create 'chat session'. This involves creating:
     *		Connection
     *		Session
     *		Topic
     *		MessageConsumer
     *		MessageProducer
     */
    private boolean createChatSession(String topicStr) {
	try  {
	    /*
	     * Create the connection...
	     *
	    */
            connection = connectionFactory.createConnection();

	    /*
	     * Not transacted
	     * Auto acknowledegement
	     */
            session = connection.createSession(false,
						Session.AUTO_ACKNOWLEDGE);

	    topic = session.createTopic(topicStr);
	    msgProducer = session.createProducer(topic);
	    /*
	     * Non persistent delivery
	     */
	    msgProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
	    msgConsumer = session.createConsumer(topic);
	    msgConsumer.setMessageListener(this);

            connection.start();

	    sendJoinMessage();

	    return true;

	} catch (Exception e)  {
	    errorMessage("Caught Exception: " + e);
            e.printStackTrace();
            return false; 
	}
    }
    /*
     * Destroy/close 'chat session'.
     */
    private void destroyChatSession()  {
	try  {
	    sendLeaveMessage();

	    msgConsumer.close();
	    msgProducer.close();
	    session.close();
	    connection.close();

	    topic = null;
	    msgConsumer = null;
	    msgProducer = null;
	    session = null;
	    connection = null;

	} catch (Exception e)  {
	    errorMessage("Caught Exception: " + e);
	}

    }

    /*
     * Display error. Right now all we do is dump to
     * stderr.
     */
    private void errorMessage(String s)  {
        System.err.println(s);
    }


}

/**
 * This class provides the bulk of the UI:
 *	sendMsgTA	TextArea for typing messages to send
 *	msgsTA		TextArea for displaying messages in chat
 *	sendB		Send button for activating a message 'Send'.
 *
 *	...and various labels to indicate the chat topic name,
 *	the user name, and host name.
 *
 */
class SimpleChatPanel extends Panel implements SimpleChatMessageTypes  {
    private String	destName,
			userName,
			msgType,
			hostName;
    
    private Button	sendB;
    
    private Label	destLabel, userLabel, msgTypeLabel, msgsLabel, 
			sendMsgLabel;

    private TextArea	msgsTA;

    private TextArea	sendMsgTA;

    /**
     * SimpleChatPanel constructor
     */
    public SimpleChatPanel()  {
	init();
    }

    /**
     * Set the chat username
     * @param userName Chat userName
     */
    public void setUserName(String userName)  {
	this.userName = userName;
	userLabel.setText("User Id: " + userName);
	sendB.setLabel("Send Message as " + userName);
    }
    /**
     * Set the chat hostname. This is pretty much
     * the host that the router is running on.
     * @param hostName Chat hostName
     */
    public void setHostName(String hostName)  {
	this.hostName = hostName;
    }
    /**
     * Sets the topic name.
     * @param destName Chat topic name
     */
    public void setDestName(String destName)  {
	this.destName = destName;
	destLabel.setText("Topic: " + destName);
    }

    public void setMsgType(String msgType)  {
	this.msgType = msgType;
	msgTypeLabel.setText("Outgoing Msg Type: " + msgType);
    }

    /**
     * Returns the 'Send' button.
     */
    public Button getSendButton()  {
	return(sendB);
    }

    /**
     * Clears the chat message text area.
     */
    public void clear()  {
	msgsTA.setText("");
    }

    /**
     * Appends the passed message to the chat message text area.
     * @param msg Message to display
     */
    public void newMessage(String sender, int type, String text)  {
	switch (type)  {
	case NORMAL:
	    msgsTA.append(sender +  ": " + text + "\n");
	break;

	case JOIN:
	    msgsTA.append("*** " +  sender +  " has joined chat session\n");
	break;

	case LEAVE:
	    msgsTA.append("*** " +  sender +  " has left chat session\n");
	break;

	default:
	}
    }

    /**
     * Sets the string to display on the chat message textarea
     * @param s String to display
     */
    public void setMessage(String s)  {
	sendMsgTA.setText(s);
    }
    /**
     * Returns the contents of the chat message textarea
     */
    public String getMessage()  {
	return (sendMsgTA.getText());
    }

    /*
     * Init chat panel GUI elements.
     */
    private void init()  {

	Panel	dummyPanel;

	setLayout(new BorderLayout(0, 0));

	destLabel = new Label("Topic:");
	
	userLabel = new Label("User Id: ");

	msgTypeLabel = new Label("Outgoing Msg Type:");



	dummyPanel = new Panel();
	dummyPanel.setLayout(new BorderLayout(0, 0));
	dummyPanel.add("North", destLabel);
	dummyPanel.add("Center", userLabel);
	dummyPanel.add("South", msgTypeLabel);
	add("North", dummyPanel);

	dummyPanel = new Panel();
	dummyPanel.setLayout(new BorderLayout(0, 0));
	msgsLabel = new Label("Messages in chat:");
	msgsTA = new TextArea(15, 40);
	msgsTA.setEditable(false);

	dummyPanel.add("North", msgsLabel);
	dummyPanel.add("Center", msgsTA);
	add("Center", dummyPanel);

	dummyPanel = new Panel();
	dummyPanel.setLayout(new BorderLayout(0, 0));
	sendMsgLabel = new Label("Type Message:");
	sendMsgTA = new TextArea(5, 40);
	sendB = new Button("Send Message");
	dummyPanel.add("North", sendMsgLabel);
	dummyPanel.add("Center", sendMsgTA);
	dummyPanel.add("South", sendB);
	add("South", dummyPanel);
    }
}

/**
 * Dialog for querying the chat user name and chat topic.
 *
 */
class SimpleChatDialog extends Dialog  {
    
    public final static int	MSG_TYPE_UNDEFINED	= -1;
    public final static int	MSG_TYPE_OBJECT		= 0;
    public final static int	MSG_TYPE_TEXT		= 1;
    public final static int	MSG_TYPE_MAP		= 2;
    public final static int	MSG_TYPE_BYTES		= 3;
    public final static int	MSG_TYPE_STREAM		= 4;

    private TextField	nameF, topicF;
    private Choice	msgTypeChoice;
    private Button	connectB, cancelB;

    /**
     * SimpleChatDialog constructor.
     * @param f Parent frame.
     */
    public SimpleChatDialog(Frame f)  {
	super(f, "Simple Chat: Connect information", true);
	init();
	setResizable(false);
    }

    /**
     * Return 'Connect' button
     */
    public Button getConnectButton()  {
	return (connectB);
    }
    /**
     * Return 'Cancel' button
     */
    public Button getCancelButton()  {
	return (cancelB);
    }

    /**
     * Return chat user name entered.
     */
    public String getChatUserName()  {
	if (nameF == null)
	    return (null);
	return (nameF.getText());
    }
    /**
     * Set chat user name.
     * @param s chat user name
     */
    public void setChatUserName(String s)  {
	if (nameF == null)
	    return;
	nameF.setText(s);
    }

    /**
     * Set chat topic
     * @param s chat topic
     */
    public void setChatTopicName(String s)  {
	if (topicF == null)
	    return;
	topicF.setText(s);
    }
    /**
     * Return chat topic
     */
    public String getChatTopicName()  {
	if (topicF == null)
	    return (null);
	return (topicF.getText());
    }

    /*
     * Get message type
     */
    public int getMsgType()  {
	if (msgTypeChoice == null)
	    return (MSG_TYPE_UNDEFINED);
	return (msgTypeChoice.getSelectedIndex());
    }

    public String getMsgTypeString()  {
	if (msgTypeChoice == null)
	    return (null);
	return (msgTypeChoice.getSelectedItem());
    }

    /*
     * Init GUI elements.
     */
    private void init()  {
	Panel			p, dummyPanel, labelPanel, valuePanel;
	GridBagLayout		labelGbag, valueGbag;
	GridBagConstraints      labelConstraints, valueConstraints;
	Label			chatNameLabel, chatTopicLabel,
				msgTypeLabel;
	int			i, j;

	p = new Panel();
	p.setLayout(new BorderLayout());

	dummyPanel = new Panel();
	dummyPanel.setLayout(new BorderLayout());

	/***/
	labelPanel = new Panel();
	labelGbag = new GridBagLayout();
	labelConstraints = new GridBagConstraints();
	labelPanel.setLayout(labelGbag);
	j = 0;

	valuePanel = new Panel();
	valueGbag = new GridBagLayout();
	valueConstraints = new GridBagConstraints();
	valuePanel.setLayout(valueGbag);
	i = 0;

	chatNameLabel = new Label("Chat User Name:", Label.RIGHT);
	chatTopicLabel = new Label("Chat Topic:", Label.RIGHT);
	msgTypeLabel = new Label("Outgoing Msg Type:", Label.RIGHT);

	labelConstraints.gridx = 0;
	labelConstraints.gridy = j++;
	labelConstraints.weightx = 1.0;
	labelConstraints.weighty = 1.0;
	labelConstraints.anchor = GridBagConstraints.EAST;
	labelGbag.setConstraints(chatNameLabel, labelConstraints);
	labelPanel.add(chatNameLabel);

	labelConstraints.gridy = j++;
	labelGbag.setConstraints(chatTopicLabel, labelConstraints);
	labelPanel.add(chatTopicLabel);

	labelConstraints.gridy = j++;
	labelGbag.setConstraints(msgTypeLabel, labelConstraints);
	labelPanel.add(msgTypeLabel);

	nameF = new TextField(20);
	topicF = new TextField(20);
	msgTypeChoice = new Choice();
	msgTypeChoice.insert("ObjectMessage", MSG_TYPE_OBJECT);
	msgTypeChoice.insert("TextMessage", MSG_TYPE_TEXT);
	msgTypeChoice.insert("MapMessage", MSG_TYPE_MAP);
	msgTypeChoice.insert("BytesMessage", MSG_TYPE_BYTES);
	msgTypeChoice.insert("StreamMessage", MSG_TYPE_STREAM);
	msgTypeChoice.select(MSG_TYPE_STREAM);

	valueConstraints.gridx = 0;
	valueConstraints.gridy = i++;
	valueConstraints.weightx = 1.0;
	valueConstraints.weighty = 1.0;
	valueConstraints.anchor = GridBagConstraints.WEST;
	valueGbag.setConstraints(nameF, valueConstraints);
	valuePanel.add(nameF);

	valueConstraints.gridy = i++;
	valueGbag.setConstraints(topicF, valueConstraints);
	valuePanel.add(topicF);

	valueConstraints.gridy = i++;
	valueGbag.setConstraints(msgTypeChoice, valueConstraints);
	valuePanel.add(msgTypeChoice);

	dummyPanel.add("West", labelPanel);
	dummyPanel.add("Center", valuePanel);
	/***/

	p.add("North", dummyPanel);

	dummyPanel = new Panel();
	connectB = new Button("Connect");
	cancelB = new Button("Cancel");
	dummyPanel.add(connectB);
	dummyPanel.add(cancelB);

	p.add("South", dummyPanel);

	add(p);
	pack();
    }
}

interface SimpleChatMessageTypes  {
    public static int	JOIN	= 0;
    public static int	NORMAL	= 1;
    public static int	LEAVE	= 2;
    public static int	BADTYPE	= -1;
}

interface SimpleChatMessageCreator  {
    public Message createChatMessage(Session session, String sender, 
					int type, String text);
    public boolean isUsable(Message msg);
    public int getChatMessageType(Message msg);
    public String getChatMessageSender(Message msg);
    public String getChatMessageText(Message msg);
}

class SimpleChatTextMessageCreator implements 
			SimpleChatMessageCreator, SimpleChatMessageTypes  {
    private static String MSG_SENDER_PROPNAME =	"SIMPLECHAT_MSG_SENDER";
    private static String MSG_TYPE_PROPNAME =	"SIMPLECHAT_MSG_TYPE";

    public Message createChatMessage(Session session, String sender, 
					int type, String text)  {
	TextMessage		txtMsg = null;

        try  {
            txtMsg = session.createTextMessage();
	    txtMsg.setStringProperty(MSG_SENDER_PROPNAME, sender);
	    txtMsg.setIntProperty(MSG_TYPE_PROPNAME, type);
	    txtMsg.setText(text);
        } catch (Exception ex)  {
	    System.err.println("Caught exception while creating message: " + ex);
        }

	return (txtMsg);
    }

    public boolean isUsable(Message msg)  {
	if (msg instanceof TextMessage)  {
	    return (true);
	}

	return (false);
    }

    public int getChatMessageType(Message msg)  {
	int	type = BADTYPE;

	try  {
	    TextMessage	txtMsg = (TextMessage)msg;
	    type = txtMsg.getIntProperty(MSG_TYPE_PROPNAME);
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (type);
    }

    public String getChatMessageSender(Message msg)  {
	String	sender = null;

	try  {
	    TextMessage	txtMsg = (TextMessage)msg;
	    sender = txtMsg.getStringProperty(MSG_SENDER_PROPNAME);
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (sender);
    }

    public String getChatMessageText(Message msg)  {
	String	text = null;

	try  {
	    TextMessage	txtMsg = (TextMessage)msg;
	    text = txtMsg.getText();
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (text);
    }
}

class SimpleChatObjMessageCreator implements 
		SimpleChatMessageCreator, SimpleChatMessageTypes  {
    public Message createChatMessage(Session session, String sender, 
					int type, String text)  {
	ObjectMessage		objMsg = null;
	ChatObjMessage	sMsg;

        try  {
            objMsg = session.createObjectMessage();
            sMsg = new ChatObjMessage(sender, type, text);
            objMsg.setObject(sMsg);
        } catch (Exception ex)  {
	    System.err.println("Caught exception while creating message: " + ex);
        }

	return (objMsg);
    }

    public boolean isUsable(Message msg)  {
	try  {
	    ChatObjMessage	sMsg = getSimpleChatMessage(msg);
	    if (sMsg == null)  {
	        return (false);
	    }
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (true);
    }

    public int getChatMessageType(Message msg)  {
	int	type = BADTYPE;

	try  {
	    ChatObjMessage	sMsg = getSimpleChatMessage(msg);
	    if (sMsg != null)  {
	        type = sMsg.getType();
	    }
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (type);
    }

    public String getChatMessageSender(Message msg)  {
	String		sender = null;

	try  {
	    ChatObjMessage	sMsg = getSimpleChatMessage(msg);
	    if (sMsg != null)  {
	        sender = sMsg.getSender();
	    }
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (sender);
    }

    public String getChatMessageText(Message msg)  {
	String			text = null;

	try  {
	    ChatObjMessage	sMsg = getSimpleChatMessage(msg);
	    if (sMsg != null)  {
	        text = sMsg.getMessage();
	    }
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (text);
    }

    private ChatObjMessage getSimpleChatMessage(Message msg)  {
	ObjectMessage		objMsg;
	ChatObjMessage	sMsg = null;

	if (!(msg instanceof ObjectMessage))  {
	    System.err.println("SimpleChatObjMessageCreator: Message received not of type ObjectMessage!");
	    return (null);
	}

	objMsg = (ObjectMessage)msg;

	try  {
	    sMsg = (ChatObjMessage)objMsg.getObject();
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (sMsg);
    }
}

class SimpleChatMapMessageCreator implements 
			SimpleChatMessageCreator, SimpleChatMessageTypes  {
    private static String MAPMSG_SENDER_PROPNAME =	"SIMPLECHAT_MAPMSG_SENDER";
    private static String MAPMSG_TYPE_PROPNAME =	"SIMPLECHAT_MAPMSG_TYPE";
    private static String MAPMSG_TEXT_PROPNAME =	"SIMPLECHAT_MAPMSG_TEXT";

    public Message createChatMessage(Session session, String sender, 
					int type, String text)  {
	MapMessage		mapMsg = null;

        try  {
            mapMsg = session.createMapMessage();
	    mapMsg.setInt(MAPMSG_TYPE_PROPNAME, type);
	    mapMsg.setString(MAPMSG_SENDER_PROPNAME, sender);
	    mapMsg.setString(MAPMSG_TEXT_PROPNAME, text);
        } catch (Exception ex)  {
	    System.err.println("Caught exception while creating message: " + ex);
        }

	return (mapMsg);
    }

    public boolean isUsable(Message msg)  {
	if (msg instanceof MapMessage)  {
	    return (true);
	}

	return (false);
    }

    public int getChatMessageType(Message msg)  {
	int	type = BADTYPE;

	try  {
	    MapMessage	mapMsg = (MapMessage)msg;
	    type = mapMsg.getInt(MAPMSG_TYPE_PROPNAME);
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (type);
    }

    public String getChatMessageSender(Message msg)  {
	String	sender = null;

	try  {
	    MapMessage	mapMsg = (MapMessage)msg;
	    sender = mapMsg.getString(MAPMSG_SENDER_PROPNAME);
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (sender);
    }

    public String getChatMessageText(Message msg)  {
	String	text = null;

	try  {
	    MapMessage	mapMsg = (MapMessage)msg;
	    text = mapMsg.getString(MAPMSG_TEXT_PROPNAME);
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (text);
    }
}

class SimpleChatBytesMessageCreator implements 
			SimpleChatMessageCreator, SimpleChatMessageTypes  {

    public Message createChatMessage(Session session, String sender, 
					int type, String text)  {
	BytesMessage		bytesMsg = null;

        try  {
	    byte	b[];

            bytesMsg = session.createBytesMessage();
	    bytesMsg.writeInt(type);
	    /*
	     * Write length of sender and text strings
	     */
	    b = sender.getBytes();
	    bytesMsg.writeInt(b.length);
	    bytesMsg.writeBytes(b);

	    if (text != null)  {
	        b = text.getBytes();
	        bytesMsg.writeInt(b.length);
	        bytesMsg.writeBytes(b);
	    } else  {
	        bytesMsg.writeInt(0);
	    }
        } catch (Exception ex)  {
	    System.err.println("Caught exception while creating message: " + ex);
        }

	return (bytesMsg);
    }

    public boolean isUsable(Message msg)  {
	if (msg instanceof BytesMessage)  {
	    return (true);
	}

	return (false);
    }

    public int getChatMessageType(Message msg)  {
	int	type = BADTYPE;

	try  {
	    BytesMessage	bytesMsg = (BytesMessage)msg;
	    type = bytesMsg.readInt();
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (type);
    }

    public String getChatMessageSender(Message msg)  {
	String	sender = null;

	sender = readSizeFetchString(msg);

	return (sender);
    }

    public String getChatMessageText(Message msg)  {
	String	text = null;

	text = readSizeFetchString(msg);

	return (text);
    }

    private String readSizeFetchString(Message msg)  {
	String	stringData = null;

	try  {
	    BytesMessage	bytesMsg = (BytesMessage)msg;
	    int			length, needToRead;
	    byte		b[];

	    length = bytesMsg.readInt();

	    if (length == 0)  {
		return ("");
	    }

	    b = new byte [length];

	    /*
	     * Loop to keep reading until all the bytes are read in
	     */
	    needToRead = length;
	    while (needToRead > 0)  {
	        byte tmpBuf[] = new byte [needToRead];
	        int ret = bytesMsg.readBytes(tmpBuf);
	        if (ret > 0)  {
	            for (int i=0; i < ret; ++i)  {
	                b[b.length - needToRead +i] = tmpBuf[i];
	            }
	            needToRead -= ret;
	        }
	    }

	    stringData = new String(b);
	} catch (Exception ex)  {
	    System.err.println("Caught exception: " + ex);
	}

	return (stringData);
    }
}

class SimpleChatStreamMessageCreator implements 
			SimpleChatMessageCreator, SimpleChatMessageTypes  {

    public Message createChatMessage(Session session, String sender, 
					int type, String text)  {
	StreamMessage		streamMsg = null;

        try  {
	    byte	b[];

            streamMsg = session.createStreamMessage();
	    streamMsg.writeInt(type);
	    streamMsg.writeString(sender);

	    if (text == null)  {
		text = "";
	    }
	    streamMsg.writeString(text);
        } catch (Exception ex)  {
	    System.err.println("Caught exception while creating message: " + ex);
        }

	return (streamMsg);
    }

    public boolean isUsable(Message msg)  {
	if (msg instanceof StreamMessage)  {
	    return (true);
	}

	return (false);
    }

    public int getChatMessageType(Message msg)  {
	int	type = BADTYPE;

	try  {
	    StreamMessage	streamMsg = (StreamMessage)msg;
	    type = streamMsg.readInt();
	} catch (Exception ex)  {
	    System.err.println("getChatMessageType(): Caught exception: " + ex);
	}

	return (type);
    }

    public String getChatMessageSender(Message msg)  {
	String	sender = null;

	try  {
	    StreamMessage	streamMsg = (StreamMessage)msg;
	    sender = streamMsg.readString();
	} catch (Exception ex)  {
	    System.err.println("getChatMessageSender(): Caught exception: " + ex);
	}

	return (sender);
    }

    public String getChatMessageText(Message msg)  {
	String	text = null;

	try  {
	    StreamMessage	streamMsg = (StreamMessage)msg;
	    text = streamMsg.readString();
	} catch (Exception ex)  {
	    System.err.println("getChatMessageText(): Caught exception: " + ex);
	}

	return (text);
    }

}




/**
 * Object representing a message sent by chat application.
 * We use this class and wrap a jakarta.jms.ObjectMessage
 * around it instead of using a jakarta.jms.TextMessage
 * because a simple string is not sufficient. We want
 * be able to to indicate that a message is one of these 
 * types:
 *	join message	('Hi, I just joined')
 *	regular message	(For regular chat messages)
 *	leave message	('Bye, I'm leaving')
 *
 */
class ChatObjMessage implements java.io.Serializable, SimpleChatMessageTypes  {
    private int		type = NORMAL;
    private String	sender,
			message;
    
    /**
     * ChatObjMessage constructor. Construct a message with the given
     * sender and message.
     * @param sender Message sender
     * @param type Message type
     * @param message The message to send
     */
    public ChatObjMessage(String sender, int type, String message)  {
	this.sender = sender;
	this.type = type;
	this.message = message;
    }

    /**
     * Returns message sender.
     */
    public String getSender()  {
	return (sender);
    }

    /**
     * Returns message type
     */
    public int getType()  {
	return (type);
    }

    /**
     * Sets the message string
     * @param message The message string
     */
    public void setMessage(String message)  {
	this.message = message;
    }
    /**
     * Returns the message string
     */
    public String getMessage()  {
	return (message);
    }
}
